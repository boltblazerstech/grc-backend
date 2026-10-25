package com.company.grc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
        private String gstStatus;
        private Integer grcScore;
        private LocalDateTime mappedAt;
        private String reportUrl;
    }
}
