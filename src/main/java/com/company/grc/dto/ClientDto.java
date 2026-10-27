package com.company.grc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ClientDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClientRequestDto {
        private String clientGstin;
        private String clientName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClientResponseDto {
        private Long id;
        private String clientGstin;
        private String clientName;
        private String logoUrl;
        private Boolean hasLogo;
        private Long vendorCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VendorMappingRequestDto {
        private List<String> vendorGstins;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MappedVendorDto {
        private String vendorGstin;
        private String tradeName;
        private String legalName;
        private String gstType;
        private LocalDate registrationDate;
        private Double ageYears;
        private String gstStatus;
        private Integer delayCountGstr1;
        private Integer delayCountGstr3b;
        private String delayedMonthsGstr1;
        private String delayedMonthsGstr3b;
        private Integer grcScore;
        private LocalDateTime mappedAt;
        private String reportUrl;

        // Compliance details matching standard template
        private String gstdNo;
        private String gstr7Status;
        private Integer gstr7DelayCount;
        private Integer gstr7MissedCount;
        private Boolean tdsApplicable;
        private String aadhaarValidation;
        private String coreActivity;
        private String aggregateTurnover;
        private String panNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkImportClientItem {
        private String clientName;
        private String clientGstin;
        private List<String> vendorGstins;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkImportRequestDto {
        private List<BulkImportClientItem> clients;
        @Builder.Default
        private Boolean replaceExisting = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkImportResultDto {
        private int clientsCreated;
        private int clientsUpdated;
        private int vendorsMapped;
        private List<Map<String, Object>> details;
    }
}
