package com.company.grc.service;

import com.company.grc.dto.MsmeDto;
import com.company.grc.entity.MsmeApiLogEntity;
import com.company.grc.entity.MsmeDetailsEntity;
import com.company.grc.repository.MsmeApiLogRepository;
import com.company.grc.repository.MsmeDetailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MsmeService {

    public static final int DAILY_AUTO_LIMIT = 50;

    private final MsmeDetailsRepository msmeDetailsRepository;
    private final MsmeApiLogRepository msmeApiLogRepository;

    /**
     * Retrieves MSME details for a given GSTIN following the Database-First architecture
     * and enforcing daily cost control rules.
     * Note: External paid providers (such as DeepVue) are strictly excluded; Figmit integration will be added later.
     *
     * @param gstin        15-character vendor GSTIN
     * @param isManual     true if triggered by a user's manual action (bypasses 50/day cap)
     * @param forceRefresh true if user explicitly requested a fresh check
     */
    @Transactional
    public MsmeDto.MsmeDetailsResponse getMsmeDetails(String gstin, boolean isManual, boolean forceRefresh) {
        if (gstin == null || gstin.trim().length() < 15) {
            throw new IllegalArgumentException("Invalid GSTIN: GSTIN must be a 15-character alphanumeric identifier.");
        }

        String cleanGstin = gstin.trim().toUpperCase();
        String pan = cleanGstin.substring(2, 12);
        LocalDate today = LocalDate.now();

        long todayAutoCalls = msmeApiLogRepository.countByCallDateAndCallType(today, "AUTO");
        long remainingQuota = Math.max(0, DAILY_AUTO_LIMIT - todayAutoCalls);

        // 1. Database-First Layer: check local database first (zero API cost)
        if (!forceRefresh) {
            Optional<MsmeDetailsEntity> existingOpt = msmeDetailsRepository.findByGstinIgnoreCase(cleanGstin);
            if (existingOpt.isEmpty()) {
                existingOpt = msmeDetailsRepository.findByPanNumberIgnoreCase(pan);
            }

            if (existingOpt.isPresent()) {
                log.info("MSME record found in local database for GSTIN: {}. Zero external API calls made.", cleanGstin);
                return mapToResponse(existingOpt.get(), "DATABASE", true, remainingQuota,
                        "Retrieved from local database with zero external API cost.");
            }
        }

        // 2. Cost Control Check: Automated calls capped at 50 per day
        String callType = isManual ? "MANUAL" : "AUTO";
        if (!isManual && todayAutoCalls >= DAILY_AUTO_LIMIT) {
            log.warn("MSME Daily Auto Limit Reached ({} / {}). Blocking automated fetch for GSTIN: {}",
                    todayAutoCalls, DAILY_AUTO_LIMIT, cleanGstin);

            recordLog(today, "AUTO", cleanGstin, pan, 429, "LIMIT_REACHED",
                    "Daily auto limit of " + DAILY_AUTO_LIMIT + " API hits reached. Automated sync blocked.");

            return MsmeDto.MsmeDetailsResponse.builder()
                    .gstin(cleanGstin)
                    .panNumber(pan)
                    .msmeStatus("DATA_UNAVAILABLE")
                    .fromDatabase(false)
                    .source("BLOCKED_LIMIT")
                    .dailyAutoQuotaRemaining(0)
                    .message("Daily automated quota of 50 API hits reached. Please use manual refresh or try tomorrow.")
                    .build();
        }

        // 3. Not in database: External provider (DeepVue) is omitted.
        // Figmit provider integration will be connected later as instructed.
        recordLog(today, callType, cleanGstin, pan, 200, "NOT_IN_DATABASE",
                "Vendor not found in local database. External provider integration (Figmit) pending.");

        long updatedRemaining = isManual ? remainingQuota : Math.max(0, remainingQuota - 1);
        pruneAuditLogsOlderThan60Days(today);

        return MsmeDto.MsmeDetailsResponse.builder()
                .gstin(cleanGstin)
                .panNumber(pan)
                .msmeStatus("DATA_UNAVAILABLE")
                .fromDatabase(false)
                .source("NOT_IN_DATABASE")
                .dailyAutoQuotaRemaining(updatedRemaining)
                .message("Vendor MSME record not found in local database. External provider (Figmit) integration will be connected later.")
                .build();
    }

    /**
     * Gets daily API usage statistics for the last 60 days and today's auto usage vs 50 limit.
     */
    @Transactional(readOnly = true)
    public MsmeDto.DailyUsageStatsResponse getDailyUsageStats() {
        LocalDate today = LocalDate.now();
        long todayAuto = msmeApiLogRepository.countByCallDateAndCallType(today, "AUTO");
        long todayManual = msmeApiLogRepository.countByCallDateAndCallType(today, "MANUAL");
        long remaining = Math.max(0, DAILY_AUTO_LIMIT - todayAuto);

        LocalDate since60Days = today.minusDays(60);
        List<Map<String, Object>> last60Days = msmeApiLogRepository.getDailyUsageStatsSummary(since60Days);

        return MsmeDto.DailyUsageStatsResponse.builder()
                .todayDate(today)
                .dailyAutoLimit(DAILY_AUTO_LIMIT)
                .todayAutoCalls(todayAuto)
                .todayRemainingAutoQuota(remaining)
                .todayManualCalls(todayManual)
                .todayTotalCalls(todayAuto + todayManual)
                .last60DaysUsage(last60Days)
                .build();
    }

    /**
     * Seeds or updates a local MSME record directly in the database.
     * Useful for storing and verifying MSME records under the Database-First architecture without external API dependencies.
     */
    @Transactional
    public MsmeDto.MsmeDetailsResponse seedTestMsmeRecord(MsmeDto.SeedMsmeRequest request) {
        if (request == null || request.getGstin() == null || request.getGstin().trim().length() < 15) {
            throw new IllegalArgumentException("Valid 15-character GSTIN is required for seeding record.");
        }

        String cleanGstin = request.getGstin().trim().toUpperCase();
        String pan = cleanGstin.substring(2, 12);

        MsmeDetailsEntity entity = msmeDetailsRepository.findByGstinIgnoreCase(cleanGstin)
                .orElse(MsmeDetailsEntity.builder().gstin(cleanGstin).panNumber(pan).build());

        entity.setPanNumber(pan);
        entity.setMsmeStatus(request.getMsmeStatus() != null ? request.getMsmeStatus() : "VERIFIED");
        entity.setUdyamNumber(request.getUdyamNumber() != null ? request.getUdyamNumber() : "UDYAM-" + pan);
        entity.setEnterpriseName(request.getEnterpriseName() != null ? request.getEnterpriseName() : "LOCAL ENTERPRISE");
        entity.setEnterpriseType(request.getEnterpriseType() != null ? request.getEnterpriseType() : "Small");
        entity.setMajorActivity(request.getMajorActivity() != null ? request.getMajorActivity() : "Services");
        entity.setState(request.getState() != null ? request.getState() : "DELHI");
        entity.setPincode(request.getPincode() != null ? request.getPincode() : "110001");
        entity.setIsManual(true);
        entity.setLastFetchedAt(LocalDateTime.now());

        MsmeDetailsEntity saved = msmeDetailsRepository.save(entity);

        long todayAuto = msmeApiLogRepository.countByCallDateAndCallType(LocalDate.now(), "AUTO");
        long remaining = Math.max(0, DAILY_AUTO_LIMIT - todayAuto);

        return mapToResponse(saved, "DATABASE_LOCAL", true, remaining, "Record successfully stored in local database.");
    }

    private void recordLog(LocalDate date, String type, String gstin, String pan, int httpStatus, String status, String error) {
        try {
            MsmeApiLogEntity logEntry = MsmeApiLogEntity.builder()
                    .callDate(date)
                    .callTimestamp(LocalDateTime.now())
                    .callType(type)
                    .gstin(gstin)
                    .panNumber(pan)
                    .httpStatus(httpStatus)
                    .status(status)
                    .errorMessage(error)
                    .build();
            msmeApiLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.error("Failed to record MSME API log entry: {}", ex.getMessage());
        }
    }

    private void pruneAuditLogsOlderThan60Days(LocalDate today) {
        try {
            LocalDate cutoff = today.minusDays(60);
            msmeApiLogRepository.deleteByCallDateBefore(cutoff);
        } catch (Exception ex) {
            log.warn("Could not prune MSME logs older than 60 days: {}", ex.getMessage());
        }
    }

    private MsmeDto.MsmeDetailsResponse mapToResponse(MsmeDetailsEntity entity, String source, boolean fromDb,
                                                      long quotaRemaining, String message) {
        return MsmeDto.MsmeDetailsResponse.builder()
                .gstin(entity.getGstin())
                .panNumber(entity.getPanNumber())
                .msmeStatus(entity.getMsmeStatus())
                .udyamNumber(entity.getUdyamNumber())
                .enterpriseName(entity.getEnterpriseName())
                .enterpriseType(entity.getEnterpriseType())
                .organizationType(entity.getOrganizationType())
                .majorActivity(entity.getMajorActivity())
                .dateOfUdyamRegistration(entity.getDateOfUdyamRegistration())
                .state(entity.getState())
                .district(entity.getDistrict())
                .pincode(entity.getPincode())
                .lastFetchedAt(entity.getLastFetchedAt())
                .source(source)
                .fromDatabase(fromDb)
                .dailyAutoQuotaRemaining(quotaRemaining)
                .message(message)
                .build();
    }
}
