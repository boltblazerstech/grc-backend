package com.company.grc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RpacpcGstDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequestPayload {
        private String request_id;
        @Builder.Default
        private String consent = "Y";
        @Builder.Default
        private String consent_text = "I hear by declare my consent agreement for fetching my information via RPACPC API";
        private String gstNumber;
        @Builder.Default
        private Boolean hsnDetails = true;
        @Builder.Default
        private Boolean branchDetails = true;
        @Builder.Default
        private Boolean filingDetails = true;
        @Builder.Default
        private Boolean filingFrequency = true;
        @Builder.Default
        private Boolean liabilityPaidDetails = true;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse {
        private String status;
        private String message;
        private Integer code;
        @JsonProperty("sub_code")
        private String subCode;
        @JsonProperty("request_id")
        private String requestId;
        private Object data;
        private Object result;
    }
}
