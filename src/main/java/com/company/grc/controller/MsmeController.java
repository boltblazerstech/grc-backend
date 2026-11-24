package com.company.grc.controller;

import com.company.grc.dto.MsmeDto;
import com.company.grc.service.MsmeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/msme")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MsmeController {

    private final MsmeService msmeService;

    /**
     * Database-First MSME lookup for a vendor GSTIN.
     * Checks local database first (0 cost). If missing, runs automated fetch subject to 50 hits/day cap.
     */
    @GetMapping("/{gstin}")
    public ResponseEntity<MsmeDto.MsmeDetailsResponse> getMsmeDetails(
            @PathVariable String gstin,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        MsmeDto.MsmeDetailsResponse response = msmeService.getMsmeDetails(gstin, false, forceRefresh);
        return ResponseEntity.ok(response);
    }

    /**
     * Manual "Refresh MSME" trigger for a vendor.
     * Bypasses the 50 hits/day automated limit and forces a fresh external API sync.
     */
    @PostMapping("/refresh/{gstin}")
    public ResponseEntity<MsmeDto.MsmeDetailsResponse> manualRefreshMsme(
            @PathVariable String gstin) {

        MsmeDto.MsmeDetailsResponse response = msmeService.getMsmeDetails(gstin, true, true);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns daily API usage statistics and the 60-day audit log summary.
     */
    @GetMapping("/usage-stats")
    public ResponseEntity<MsmeDto.DailyUsageStatsResponse> getDailyUsageStats() {
        MsmeDto.DailyUsageStatsResponse stats = msmeService.getDailyUsageStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Seeds or updates a local test MSME record directly in the database.
     * Useful for testing the Database-First architecture and UI without calling external APIs.
     */
    @PostMapping("/test-seed")
    public ResponseEntity<MsmeDto.MsmeDetailsResponse> seedTestMsmeRecord(
            @RequestBody MsmeDto.SeedMsmeRequest request) {

        MsmeDto.MsmeDetailsResponse response = msmeService.seedTestMsmeRecord(request);
        return ResponseEntity.ok(response);
    }
}
