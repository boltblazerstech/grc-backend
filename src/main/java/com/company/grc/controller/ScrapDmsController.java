package com.company.grc.controller;

import com.company.grc.dto.ScrapDmsDto;
import com.company.grc.service.ScrapDmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/scrapdms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ScrapDmsController {

    private final ScrapDmsService scrapDmsService;

    /**
     * 1. ScrapDMS API - MSME Status:
     * Input: GSTIN
     * Output: MSME status for that GSTIN from our database (Udyam number, enterprise type, etc.)
     */
    @GetMapping("/msme/{gstin}")
    public ResponseEntity<ScrapDmsDto.ScrapDmsMsmeResponse> getMsmeStatus(
            @PathVariable String gstin) {
        ScrapDmsDto.ScrapDmsMsmeResponse response = scrapDmsService.getMsmeStatus(gstin);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. ScrapDMS API - GRC Score:
     * Input: GSTIN
     * Output: The GRC score (0–100) and risk band (Good / Careful / Risky) calculated from the stored record.
     */
    @GetMapping("/score/{gstin}")
    public ResponseEntity<ScrapDmsDto.ScrapDmsScoreResponse> getGrcScore(
            @PathVariable String gstin) {
        ScrapDmsDto.ScrapDmsScoreResponse response = scrapDmsService.getGrcScore(gstin);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. ScrapDMS API - Details Link:
     * Input: GSTIN
     * Output: Direct URL link to vendor's report in GRC which ScrapDMS can open.
     */
    @GetMapping("/report-link/{gstin}")
    public ResponseEntity<ScrapDmsDto.ScrapDmsReportLinkResponse> getReportLink(
            @PathVariable String gstin) {
        ScrapDmsDto.ScrapDmsReportLinkResponse response = scrapDmsService.getReportLink(gstin);
        return ResponseEntity.ok(response);
    }

    /**
     * Handles GSTIN format validation errors and returns immediate HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ScrapDmsDto.ScrapDmsErrorResponse> handleValidationException(IllegalArgumentException ex) {
        ScrapDmsDto.ScrapDmsErrorResponse error = ScrapDmsDto.ScrapDmsErrorResponse.builder()
                .error(true)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
