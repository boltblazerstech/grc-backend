package com.company.grc.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanGstr7DataResponse {

    private String panNumber;
    private String companyName;
    @JsonProperty("isApplicable")
    private Boolean isApplicable;
    private List<GstinData> gstins;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GstinData {
        private String gstin;
        private String gstr7Status;
        private Integer gstr7DelayCount;
        private Integer gstr7MissedCount;
        private LocalDateTime gstr7LastUpdated;
        private String gstType;
        private String gstdNo;
        private String tradeName;
        private String legalName;
        private LocalDateTime createdAt;
    }
}
