package com.company.grc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ScrapDmsDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScrapDmsErrorResponse {
        private boolean error;
        private String message;
        private String gstin;
        private LocalDateTime timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScrapDmsMsmeResponse {
        private String gstin;
        private String panNumber;
        private String msmeStatus; // VERIFIED, NOT_REGISTERED, DATA_UNAVAILABLE
        private String udyamNumber;
        private String enterpriseName;
        private String enterpriseType; // Micro, Small, Medium
        private String majorActivity;  // Services, Manufacturing
        private String dateOfUdyamRegistration;
        private String state;
        private String district;
        private String pincode;
        private String source; // DATABASE
        private boolean availableInDatabase;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScrapDmsScoreResponse {
        private String gstin;
        private String vendorName;
        private Integer grcScore;
        private String riskCategory; // Good (<20), Careful (20-30), Risky (>30)
        private String gstStatus;    // Active, Inactive, Cancelled, Unknown
        private Integer delayCountGstr1;
        private Integer delayCountGstr3b;
        private String delayedMonthsGstr1;
        private String delayedMonthsGstr3b;
        private String gstr7Status;
        private LocalDateTime calculatedAt;
        private String reportUrl;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScrapDmsReportLinkResponse {
        private String gstin;
        private String vendorName;
        private String reportUrl;
        private Integer grcScore;
        private String riskCategory;
    }
}
