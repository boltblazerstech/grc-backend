package com.company.grc.service;

import com.company.grc.dto.RpacpcGstDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RpacpcApiService {

    @Value("${rpacpc.base-url:https://api.rpacpc.com}")
    private String baseUrl;

    @Value("${rpacpc.gst-endpoint:/services/bv010}")
    private String gstEndpoint;

    @Value("${rpacpc.token:}")
    private String token;

    @Value("${rpacpc.secret-key:}")
    private String secretKey;

    @Value("${rpacpc.daily-limit:50}")
    private int dailyLimit;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory cost control daily hit tracker
    private volatile LocalDate currentDay = LocalDate.now();
    private final AtomicInteger dailyHits = new AtomicInteger(0);

    private void enforceDailyCap() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            currentDay = today;
            dailyHits.set(0);
        }
        if (dailyHits.incrementAndGet() > dailyLimit) {
            dailyHits.decrementAndGet();
            throw new IllegalStateException("Daily cost-control cap reached (" + dailyLimit + " API calls/day). Further calls blocked to avoid unexpected costs.");
        }
    }

    /**
     * Calls the RPACPC GST Details Advance API.
     * Enforces daily hit limits and returns parsed JsonNode payload.
     */
    public JsonNode fetchGstDetails(String gstin) {
        if (gstin == null || gstin.trim().isBlank()) {
            throw new IllegalArgumentException("GSTIN cannot be empty");
        }
        String cleanGstin = gstin.trim().toUpperCase();

        if (token == null || token.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("RPACPC credentials (token or secretKey) are missing.");
        }

        enforceDailyCap();

        String targetUrl = baseUrl.replaceAll("/+$", "") + (gstEndpoint.startsWith("/") ? gstEndpoint : "/" + gstEndpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", token.trim());
        headers.set("secretkey", secretKey.trim());

        String requestId = String.valueOf(System.currentTimeMillis()) + (int)(Math.random() * 1000);
        RpacpcGstDto.RequestPayload payload = RpacpcGstDto.RequestPayload.builder()
                .request_id(requestId)
                .consent("Y")
                .consent_text("I hear by declare my consent agreement for fetching my information via RPACPC API")
                .gstNumber(cleanGstin)
                .hsnDetails(true)
                .branchDetails(true)
                .filingDetails(true)
                .filingFrequency(true)
                .liabilityPaidDetails(true)
                .build();

        HttpEntity<RpacpcGstDto.RequestPayload> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            } else {
                throw new RuntimeException("RPACPC API returned HTTP " + response.getStatusCode());
            }
        } catch (HttpStatusCodeException e) {
            String respBody = e.getResponseBodyAsString();
            throw new RuntimeException("RPACPC API error (" + e.getStatusCode() + "): " + (respBody.isBlank() ? e.getMessage() : respBody));
        } catch (Exception e) {
            throw new RuntimeException("RPACPC API invocation failed: " + e.getMessage(), e);
        }
    }

    public int getRemainingDailyQuota() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            currentDay = today;
            dailyHits.set(0);
        }
        return Math.max(0, dailyLimit - dailyHits.get());
    }
}
