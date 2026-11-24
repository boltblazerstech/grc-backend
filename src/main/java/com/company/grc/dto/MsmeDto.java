package com.company.grc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class MsmeDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MsmeDetailsResponse {
        private String gstin;
        private String panNumber;
        private String msmeStatus; // 'YES', 'NO', 'DATA_UNAVAILABLE', 'VERIFIED'
        private String udyamNumber;
        private String enterpriseName;
        private String enterpriseType; // Micro, Small, Medium
        private String organizationType;
        private String majorActivity; // Services, Manufacturing
        private String dateOfUdyamRegistration;
        private String state;
        private String district;
        private String pincode;
        private LocalDateTime lastFetchedAt;
        private String source; // 'DATABASE' (0 cost) or 'EXTERNAL_API'
        private boolean fromDatabase;
        private long dailyAutoQuotaRemaining;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyUsageStatsResponse {
        private LocalDate todayDate;
        private long dailyAutoLimit; // 50
        private long todayAutoCalls;
        private long todayRemainingAutoQuota;
        private long todayManualCalls;
        private long todayTotalCalls;
        private List<Map<String, Object>> last60DaysUsage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeedMsmeRequest {
        private String gstin;
        private String udyamNumber;
        private String enterpriseName;
        private String enterpriseType; // Micro, Small, Medium
        private String majorActivity;  // Services, Manufacturing
        private String msmeStatus;     // YES, NO, VERIFIED
        private String state;
        private String pincode;
    }
}
