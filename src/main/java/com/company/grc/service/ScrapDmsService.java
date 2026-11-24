package com.company.grc.service;

import com.company.grc.dto.ScrapDmsDto;
import com.company.grc.entity.GrcScoreEntity;
import com.company.grc.entity.GstDetailsEntity;
import com.company.grc.entity.MsmeDetailsEntity;
import com.company.grc.repository.GrcScoreRepository;
import com.company.grc.repository.GstDetailsRepository;
import com.company.grc.repository.MsmeDetailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScrapDmsService {

    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Za-z0-9]{13}$");

    private final MsmeDetailsRepository msmeDetailsRepository;
    private final GrcScoreRepository grcScoreRepository;
    private final GstDetailsRepository gstDetailsRepository;

    @Value("${app.frontend.url:https://grc.scrapli.in}")
    private String frontendUrl;

    /**
     * Validates GSTIN format as required by ScrapDMS specifications.
     * Must be exactly 15 alphanumeric characters.
     */
    public String validateAndNormalizeGstin(String gstin) {
        if (gstin == null || gstin.trim().isBlank()) {
            throw new IllegalArgumentException("GSTIN cannot be blank: exactly 15 alphanumeric characters are required.");
        }
        String clean = gstin.trim().toUpperCase();
        if (clean.length() != 15 || !GSTIN_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN format: '" + clean + "' must be exactly 15 alphanumeric characters (starting with 2-digit state code).");
        }
        return clean;
    }

    /**
     * 1. MSME Status for ScrapDMS:
     * Checks database first and returns vendor Udyam and category details.
     */
    @Transactional(readOnly = true)
    public ScrapDmsDto.ScrapDmsMsmeResponse getMsmeStatus(String rawGstin) {
        String cleanGstin = validateAndNormalizeGstin(rawGstin);
        String pan = cleanGstin.substring(2, 12);

        Optional<MsmeDetailsEntity> msmeOpt = msmeDetailsRepository.findByGstinIgnoreCase(cleanGstin);
        if (msmeOpt.isEmpty()) {
            msmeOpt = msmeDetailsRepository.findByPanNumberIgnoreCase(pan);
        }

        if (msmeOpt.isPresent()) {
            MsmeDetailsEntity m = msmeOpt.get();
            return ScrapDmsDto.ScrapDmsMsmeResponse.builder()
                    .gstin(cleanGstin)
                    .panNumber(pan)
                    .msmeStatus(m.getMsmeStatus() != null ? m.getMsmeStatus() : "VERIFIED")
                    .udyamNumber(m.getUdyamNumber())
                    .enterpriseName(m.getEnterpriseName())
                    .enterpriseType(m.getEnterpriseType())
                    .majorActivity(m.getMajorActivity())
                    .dateOfUdyamRegistration(m.getDateOfUdyamRegistration())
                    .state(m.getState())
                    .district(m.getDistrict())
                    .pincode(m.getPincode())
                    .source("DATABASE")
                    .availableInDatabase(true)
                    .message("MSME details retrieved from GRC database.")
                    .build();
        }

        return ScrapDmsDto.ScrapDmsMsmeResponse.builder()
                .gstin(cleanGstin)
                .panNumber(pan)
                .msmeStatus("DATA_UNAVAILABLE")
                .source("DATABASE")
                .availableInDatabase(false)
                .message("MSME record not found in database for GSTIN: " + cleanGstin)
                .build();
    }

    /**
     * 2. GRC Score for ScrapDMS:
     * Calculates/retrieves compliance score (0–100) and risk band (Good / Careful / Risky).
     */
    @Transactional(readOnly = true)
    public ScrapDmsDto.ScrapDmsScoreResponse getGrcScore(String rawGstin) {
        String cleanGstin = validateAndNormalizeGstin(rawGstin);

        Optional<GrcScoreEntity> scoreOpt = grcScoreRepository.findByGstinNormalized(cleanGstin);
        Optional<GstDetailsEntity> gstOpt = gstDetailsRepository.findById(cleanGstin);

        Integer score = scoreOpt.map(GrcScoreEntity::getScore).orElse(null);
        String vendorName = gstOpt.map(g -> {
            if (g.getTradeName() != null && !g.getTradeName().isBlank()) return g.getTradeName();
            if (g.getLegalName() != null && !g.getLegalName().isBlank()) return g.getLegalName();
            return cleanGstin;
        }).orElse(cleanGstin);

        String gstStatus = gstOpt.map(GstDetailsEntity::getGstStatus).orElse("Unknown");
        Integer delayGstr1 = gstOpt.map(GstDetailsEntity::getDelayCountGstr1).orElse(null);
        Integer delayGstr3b = gstOpt.map(GstDetailsEntity::getDelayCountGstr3b).orElse(null);
        String delayedMonthsGstr1 = gstOpt.map(GstDetailsEntity::getDelayedMonthsGstr1)
                .map(ComplianceRetentionService::pruneTo18MonthWindow).orElse(null);
        String delayedMonthsGstr3b = gstOpt.map(GstDetailsEntity::getDelayedMonthsGstr3b)
                .map(ComplianceRetentionService::pruneTo18MonthWindow).orElse(null);
        String gstr7Status = gstOpt.map(GstDetailsEntity::getGstr7Status).orElse("NA");

        String riskCategory = calculateRiskCategory(score);
        String reportUrl = buildReportUrl(cleanGstin);

        return ScrapDmsDto.ScrapDmsScoreResponse.builder()
                .gstin(cleanGstin)
                .vendorName(vendorName)
                .grcScore(score)
                .riskCategory(riskCategory)
                .gstStatus(gstStatus)
                .delayCountGstr1(delayGstr1)
                .delayCountGstr3b(delayGstr3b)
                .delayedMonthsGstr1(delayedMonthsGstr1)
                .delayedMonthsGstr3b(delayedMonthsGstr3b)
                .gstr7Status(gstr7Status)
                .calculatedAt(scoreOpt.map(GrcScoreEntity::getCalculatedAt).orElse(null))
                .reportUrl(reportUrl)
                .message(score != null ? "GRC score retrieved successfully." : "GRC score has not been computed yet for this vendor.")
                .build();
    }

    /**
     * 3. Details Link for ScrapDMS:
     * Generates a direct URL so ScrapDMS users can click and view the full vendor report.
     */
    @Transactional(readOnly = true)
    public ScrapDmsDto.ScrapDmsReportLinkResponse getReportLink(String rawGstin) {
        String cleanGstin = validateAndNormalizeGstin(rawGstin);
        Optional<GrcScoreEntity> scoreOpt = grcScoreRepository.findByGstinNormalized(cleanGstin);
        Optional<GstDetailsEntity> gstOpt = gstDetailsRepository.findById(cleanGstin);

        String vendorName = gstOpt.map(g -> {
            if (g.getTradeName() != null && !g.getTradeName().isBlank()) return g.getTradeName();
            if (g.getLegalName() != null && !g.getLegalName().isBlank()) return g.getLegalName();
            return cleanGstin;
        }).orElse(cleanGstin);

        Integer score = scoreOpt.map(GrcScoreEntity::getScore).orElse(null);
        String riskCategory = calculateRiskCategory(score);
        String reportUrl = buildReportUrl(cleanGstin);

        return ScrapDmsDto.ScrapDmsReportLinkResponse.builder()
                .gstin(cleanGstin)
                .vendorName(vendorName)
                .reportUrl(reportUrl)
                .grcScore(score)
                .riskCategory(riskCategory)
                .build();
    }

    public String buildReportUrl(String gstin) {
        String base = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl.replaceAll("/+$", "") : "https://grc.scrapli.in";
        return base + "/vendor-report/" + gstin;
    }

    /**
     * Determines risk band according to business rules:
     * GRC < 20: Good
     * 20 <= GRC <= 30: Careful
     * GRC > 30: Risky
     */
    public static String calculateRiskCategory(Integer score) {
        if (score == null) return "Unknown";
        if (score < 20) return "Good";
        if (score <= 30) return "Careful";
        return "Risky";
    }
}
