package com.example.ekyc.service;

import com.example.ekyc.client.HttpClient;
import com.example.ekyc.client.ServiceResponse;
import com.example.ekyc.config.ServiceConfig;
import com.example.ekyc.model.Customer;
import com.example.ekyc.model.VerificationResult;
import com.example.ekyc.model.VerificationStatus;
import com.example.ekyc.model.VerificationType;
import com.example.ekyc.util.JsonUtils;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the Sanctions Screening Service.
 * Checks customer against global sanctions lists.
 * 
 * CRITICAL SERVICE - Business Rules:
 * - ANY match → REJECTED immediately
 * - Service MUST succeed (cannot proceed if service fails)
 * - This is the most critical check in the eKYC process
 */
public class SanctionsScreeningClient {
    
    private static final Logger logger = LoggerFactory.getLogger(SanctionsScreeningClient.class);
    
    private final HttpClient httpClient;
    private final ServiceConfig config;

    public SanctionsScreeningClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.config = ServiceConfig.getInstance();
    }

    public SanctionsScreeningClient(HttpClient httpClient, ServiceConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public VerificationResult checkSanctions(Customer customer) {
        logger.info("Starting sanctions screening for customer: {}", customer.getCustomerId());
        
        try {
            // Build request payload
            Map<String, Object> request = new HashMap<>();
            request.put("customer_id", customer.getCustomerId());
            request.put("full_name", customer.getFullName());
            request.put("date_of_birth", customer.getDateOfBirth());
            request.put("nationality", customer.getNationality());
            
            // Call service
            String url = config.getSanctionsUrl();
            ServiceResponse response = httpClient.post(url, request, config.getSanctionsTimeout());
            
            // Parse and process response
            return processResponse(response, customer.getCustomerId());
            
        } catch (Exception e) {
            // CRITICAL: Sanctions check failure must be treated seriously
            logger.error("CRITICAL: Sanctions screening failed for customer {}: {}", 
                    customer.getCustomerId(), e.getMessage());
            
            // For sanctions, service failure should result in MANUAL_REVIEW 
            // as we cannot approve without a successful sanctions check
            return VerificationResult.builder()
                    .verificationType(VerificationType.SANCTIONS)
                    .status(VerificationStatus.MANUAL_REVIEW)
                    .confidence(0)
                    .reasons(List.of("CRITICAL: Sanctions service unavailable - " + e.getMessage()))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    private VerificationResult processResponse(ServiceResponse response, String customerId) {
        if (!response.isSuccess()) {
            logger.error("CRITICAL: Sanctions service returned non-success for customer {}: {}",
                    customerId, response.getStatusCode());
            return VerificationResult.builder()
                    .verificationType(VerificationType.SANCTIONS)
                    .status(VerificationStatus.MANUAL_REVIEW)
                    .confidence(0)
                    .reasons(List.of("CRITICAL: Sanctions service error: " + response.getStatusCode()))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        JsonObject json = JsonUtils.fromJson(response.getBody(), JsonObject.class);
        String status = json.has("status") ? json.get("status").getAsString() : "UNKNOWN";
        int matchCount = json.has("match_count") ? json.get("match_count").getAsInt() : 0;
        
        List<String> reasons = new ArrayList<>();
        
        // Extract matches if present (for logging/audit purposes)
        if (json.has("matches") && json.get("matches").isJsonArray()) {
            json.getAsJsonArray("matches").forEach(e -> {
                if (e.isJsonObject()) {
                    JsonObject match = e.getAsJsonObject();
                    String matchName = match.has("name") ? match.get("name").getAsString() : "Unknown";
                    String matchList = match.has("list") ? match.get("list").getAsString() : "Unknown List";
                    reasons.add("Match found: " + matchName + " on " + matchList);
                } else {
                    reasons.add("Match: " + e.getAsString());
                }
            });
        }
        
        // Determine status: HIT = FAIL (REJECTED), CLEAR = PASS
        VerificationStatus finalStatus;
        if ("HIT".equals(status) || matchCount > 0) {
            finalStatus = VerificationStatus.FAIL;
            if (reasons.isEmpty()) {
                reasons.add("Sanctions match found (" + matchCount + " match(es))");
            }
            logger.warn("SANCTIONS HIT for customer {}: {} match(es) found", customerId, matchCount);
        } else if ("CLEAR".equals(status)) {
            finalStatus = VerificationStatus.PASS;
            logger.info("Sanctions screening CLEAR for customer {}", customerId);
        } else {
            // Unknown status - treat as manual review
            finalStatus = VerificationStatus.MANUAL_REVIEW;
            reasons.add("Unknown sanctions status: " + status);
            logger.warn("Unknown sanctions status '{}' for customer {}", status, customerId);
        }
        
        return VerificationResult.builder()
                .verificationType(VerificationType.SANCTIONS)
                .status(finalStatus)
                .confidence(finalStatus == VerificationStatus.PASS ? 100 : 0)
                .reasons(reasons)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
