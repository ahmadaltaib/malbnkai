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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the Address Verification Service.
 * Verifies customer address using proof documents.
 * 
 * Business Rules:
 * - Proof must be dated within configured validity period
 * - Confidence score > configured threshold for PASS
 */
public class AddressVerificationClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AddressVerificationClient.class);
    
    private final HttpClient httpClient;
    private final ServiceConfig config;

    public AddressVerificationClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.config = ServiceConfig.getInstance();
    }

    public AddressVerificationClient(HttpClient httpClient, ServiceConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public VerificationResult verifyAddress(Customer customer) {
        logger.info("Starting address verification for customer: {}", customer.getCustomerId());
        
        try {
            int proofValidityDays = config.getAddressProofValidityDays();
            
            // Check proof date first (business rule)
            if (isProofTooOld(customer.getProofDate(), proofValidityDays)) {
                logger.warn("Address proof is older than {} days for customer: {}", 
                        proofValidityDays, customer.getCustomerId());
                return VerificationResult.builder()
                        .verificationType(VerificationType.ADDRESS)
                        .status(VerificationStatus.FAIL)
                        .confidence(0)
                        .reasons(List.of("Proof of address is older than " + proofValidityDays + " days"))
                        .timestamp(LocalDateTime.now())
                        .build();
            }
            
            // Build request payload
            Map<String, Object> request = new HashMap<>();
            request.put("customer_id", customer.getCustomerId());
            request.put("address", customer.getAddress());
            request.put("proof_type", customer.getProofType());
            request.put("proof_date", customer.getProofDate());
            request.put("proof_url", customer.getProofUrl());
            
            // Call service
            String url = config.getAddressUrl();
            ServiceResponse response = httpClient.post(url, request, config.getAddressTimeout());
            
            // Parse and process response
            return processResponse(response, customer.getCustomerId());
            
        } catch (Exception e) {
            logger.error("Address verification failed for customer {}: {}", 
                    customer.getCustomerId(), e.getMessage());
            return VerificationResult.builder()
                    .verificationType(VerificationType.ADDRESS)
                    .status(VerificationStatus.MANUAL_REVIEW)
                    .confidence(0)
                    .reasons(List.of("Service error: " + e.getMessage()))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    private VerificationResult processResponse(ServiceResponse response, String customerId) {
        if (!response.isSuccess()) {
            logger.warn("Address service returned non-success for customer {}: {}",
                    customerId, response.getStatusCode());
            return VerificationResult.builder()
                    .verificationType(VerificationType.ADDRESS)
                    .status(VerificationStatus.MANUAL_REVIEW)
                    .confidence(0)
                    .reasons(List.of("Service returned error: " + response.getStatusCode()))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        JsonObject json = JsonUtils.fromJson(response.getBody(), JsonObject.class);
        String status = json.has("status") ? json.get("status").getAsString() : "FAIL";
        int confidence = json.has("confidence") ? json.get("confidence").getAsInt() : 0;
        
        List<String> reasons = new ArrayList<>();
        if (json.has("reasons") && json.get("reasons").isJsonArray()) {
            json.getAsJsonArray("reasons").forEach(e -> reasons.add(e.getAsString()));
        }
        
        // Apply business rule: confidence > 80% for PASS
        VerificationStatus finalStatus = determineStatus(status, confidence, reasons);
        
        logger.info("Address verification completed for customer {}: status={}, confidence={}",
                customerId, finalStatus, confidence);
        
        return VerificationResult.builder()
                .verificationType(VerificationType.ADDRESS)
                .status(finalStatus)
                .confidence(confidence)
                .reasons(reasons)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private VerificationStatus determineStatus(String serviceStatus, int confidence, List<String> reasons) {
        if ("FAIL".equals(serviceStatus)) {
            return VerificationStatus.FAIL;
        }
        
        int threshold = config.getAddressConfidenceThreshold();
        if (confidence > threshold) {
            return VerificationStatus.PASS;
        }
        
        // Low confidence
        if (reasons.isEmpty()) {
            reasons.add("Confidence score below threshold (" + confidence + "% <= " + threshold + "%)");
        }
        return VerificationStatus.MANUAL_REVIEW;
    }

    private boolean isProofTooOld(String proofDate, int validityDays) {
        if (proofDate == null || proofDate.isEmpty()) {
            return true;
        }
        
        try {
            LocalDate date = LocalDate.parse(proofDate);
            long daysSinceProof = ChronoUnit.DAYS.between(date, LocalDate.now());
            return daysSinceProof > validityDays;
        } catch (DateTimeParseException e) {
            logger.warn("Invalid proof date format: {}", proofDate);
            return true;
        }
    }
}
