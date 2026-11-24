package com.company.grc.service;

import com.company.grc.dto.MsmeDto;
import com.company.grc.entity.MsmeApiLogEntity;
import com.company.grc.entity.MsmeDetailsEntity;
import com.company.grc.repository.MsmeApiLogRepository;
import com.company.grc.repository.MsmeDetailsRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final DeepvueApiService deepvueApiService;

    /**
     * Retrieves MSME details for a given GSTIN following the Database-First architecture
     * and strictly enforcing the 50 hits/day cost control cap for automated calls.
     *
     * @param gstin        15-character vendor GSTIN
     * @param isManual     true if triggered by a user's manual action (bypasses 50/day cap)
     * @param forceRefresh true to bypass database-first cache and perform fresh fetch
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

        // 1. Database-First Layer: check local database first
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

            MsmeApiLogEntity limitLog = MsmeApiLogEntity.builder()
                    .callDate(today)
                    .callTimestamp(LocalDateTime.now())
                    .callType("AUTO")
                    .gstin(cleanGstin)
                    .panNumber(pan)
                    .httpStatus(429)
                    .status("LIMIT_REACHED")
                    .errorMessage("Daily auto limit of " + DAILY_AUTO_LIMIT + " API hits reached. Automated sync blocked.")
                    .build();
            msmeApiLogRepository.save(limitLog);

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

        // 3. Fetch from External Provider (DeepVue PAN-to-MSME)
        try {
            log.info("Initiating external MSME API call for PAN: {} (Type: {}, Today Auto Calls: {}/{})",
                    pan, callType, todayAutoCalls, DAILY_AUTO_LIMIT);

            JsonNode apiRoot = deepvueApiService.fetchMsmeDetails(pan);
            String subCode = apiRoot.path("sub_code").asText("UNKNOWN");
            int httpStatus = apiRoot.path("code").asInt(200);

            if (!"SUCCESS".equalsIgnoreCase(subCode) || apiRoot.path("data").isMissingNode() || apiRoot.path("data").isNull()) {
                String errorMsg = apiRoot.path("message").asText("No MSME record found for PAN " + pan);

                // Save negative cache in database so repeated calls are avoided
                MsmeDetailsEntity notFoundEntity = msmeDetailsRepository.findByGstinIgnoreCase(cleanGstin)
                        .orElse(MsmeDetailsEntity.builder().gstin(cleanGstin).panNumber(pan).build());
                notFoundEntity.setMsmeStatus("NOT_REGISTERED");
                notFoundEntity.setIsManual(isManual);
                notFoundEntity.setRawResponseJson(apiRoot.toString());
                notFoundEntity.setLastFetchedAt(LocalDateTime.now());
                msmeDetailsRepository.save(notFoundEntity);

                // Log audit
                recordLog(today, callType, cleanGstin, pan, httpStatus, "NO_RECORD", errorMsg);

                long updatedRemaining = isManual ? remainingQuota : Math.max(0, remainingQuota - 1);
                return mapToResponse(notFoundEntity, "EXTERNAL_API", false, updatedRemaining, errorMsg);
            }

            JsonNode data = apiRoot.path("data");
            MsmeDetailsEntity entity = msmeDetailsRepository.findByGstinIgnoreCase(cleanGstin)
                    .orElse(MsmeDetailsEntity.builder().gstin(cleanGstin).panNumber(pan).build());

            entity.setMsmeStatus("VERIFIED");
            entity.setUdyamNumber(data.path("udyam_number").asText(null));
            entity.setEnterpriseName(data.path("enterprise_name").asText(null));
            entity.setOrganizationType(data.path("organization_type").asText(null));
            entity.setMajorActivity(data.path("major_activity").asText(null));
            entity.setDateOfIncorporation(data.path("date_of_incorporation").asText(null));
            entity.setDateOfCommencement(data.path("date_of_commencement").asText(null));
            entity.setDateOfUdyamRegistration(data.path("date_of_udyam_registration").asText(null));
            entity.setSocialCategory(data.path("social_category").asText(null));
            entity.setDic(data.path("dic").asText(null));
            entity.setMsmeDi(data.path("msme_di").asText(null));
            entity.setMobile(data.path("mobile").asText(null));
            entity.setEmail(data.path("email").asText(null));

            // Extract enterprise type (latest from list)
            JsonNode typeList = data.path("enterprise_type");
            if (typeList.isArray() && typeList.size() > 0) {
                entity.setEnterpriseType(typeList.get(0).path("enterprise_type").asText(null));
            }

            // Extract address
            JsonNode address = data.path("official_address");
            if (!address.isMissingNode()) {
                entity.setState(address.path("state").asText(null));
                entity.setDistrict(address.path("district").asText(null));
                entity.setPincode(address.path("pincode").asText(null));
            }

            entity.setRawResponseJson(apiRoot.toString());
            entity.setIsManual(isManual);
            entity.setLastFetchedAt(LocalDateTime.now());
            MsmeDetailsEntity saved = msmeDetailsRepository.save(entity);

            recordLog(today, callType, cleanGstin, pan, httpStatus, "SUCCESS", "MSME details fetched and saved successfully.");
            pruneAuditLogsOlderThan60Days(today);

            long updatedRemaining = isManual ? remainingQuota : Math.max(0, remainingQuota - 1);
            return mapToResponse(saved, "EXTERNAL_API", false, updatedRemaining, "Successfully fetched from MSME API and saved to database.");

        } catch (Exception e) {
            log.error("Error during MSME API fetch for GSTIN {}: {}", cleanGstin, e.getMessage());
            recordLog(today, callType, cleanGstin, pan, 500, "FAILED", e.getMessage());

            // If an older database record existed, return it gracefully
            Optional<MsmeDetailsEntity> fallback = msmeDetailsRepository.findByGstinIgnoreCase(cleanGstin);
            if (fallback.isPresent()) {
                return mapToResponse(fallback.get(), "DATABASE_FALLBACK", true, remainingQuota,
                        "API call failed: " + e.getMessage() + ". Returning previously stored database record.");
            }

            return MsmeDto.MsmeDetailsResponse.builder()
                    .gstin(cleanGstin)
                    .panNumber(pan)
                    .msmeStatus("DATA_UNAVAILABLE")
                    .fromDatabase(false)
                    .source("FAILED")
                    .dailyAutoQuotaRemaining(remainingQuota)
                    .message("External API error: " + e.getMessage())
                    .build();
        }
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
     * Seeds or updates a local test MSME record directly in the database.
     * Useful for verifying the Database-First architecture and UI without consuming API hits.
     */
    @Transactional
    public MsmeDto.MsmeDetailsResponse seedTestMsmeRecord(MsmeDto.SeedMsmeRequest request) {
        if (request == null || request.getGstin() == null || request.getGstin().trim().length() < 15) {
            throw new IllegalArgumentException("Valid 15-character GSTIN is required for seeding test record.");
        }

        String cleanGstin = request.getGstin().trim().toUpperCase();
        String pan = cleanGstin.substring(2, 12);

        MsmeDetailsEntity entity = msmeDetailsRepository.findByGstinIgnoreCase(cleanGstin)
                .orElse(MsmeDetailsEntity.builder().gstin(cleanGstin).panNumber(pan).build());

        entity.setPanNumber(pan);
        entity.setMsmeStatus(request.getMsmeStatus() != null ? request.getMsmeStatus() : "VERIFIED");
        entity.setUdyamNumber(request.getUdyamNumber() != null ? request.getUdyamNumber() : "UDYAM-TEST-" + pan);
        entity.setEnterpriseName(request.getEnterpriseName() != null ? request.getEnterpriseName() : "TEST ENTERPRISE");
        entity.setEnterpriseType(request.getEnterpriseType() != null ? request.getEnterpriseType() : "Small");
        entity.setMajorActivity(request.getMajorActivity() != null ? request.getMajorActivity() : "Services");
        entity.setState(request.getState() != null ? request.getState() : "DELHI");
        entity.setPincode(request.getPincode() != null ? request.getPincode() : "110001");
        entity.setIsManual(true);
        entity.setLastFetchedAt(LocalDateTime.now());

        MsmeDetailsEntity saved = msmeDetailsRepository.save(entity);

        long todayAuto = msmeApiLogRepository.countByCallDateAndCallType(LocalDate.now(), "AUTO");
        long remaining = Math.max(0, DAILY_AUTO_LIMIT - todayAuto);

        return mapToResponse(saved, "DATABASE_SEEDED", true, remaining, "Test record successfully seeded into database.");
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
