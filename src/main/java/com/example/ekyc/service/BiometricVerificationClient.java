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
 * Client for the Biometric (Face Match) Service.
 * Compares selfie with ID photo for identity verification.
 * 
 * Business Rules:
 * - Confidence score > configured threshold for PASS
 * - Similarity score > configured threshold for PASS
 * - Low scores → MANUAL_REVIEW
 */
public class BiometricVerificationClient {
    
    private static final Logger logger = LoggerFactory.getLogger(BiometricVerificationClient.class);
    
    private final HttpClient httpClient;
    private final ServiceConfig config;

    public BiometricVerificationClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.config = ServiceConfig.getInstance();
    }

    public BiometricVerificationClient(HttpClient httpClient, ServiceConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public VerificationResult verifyFaceMatch(Customer customer) {
        logger.info("Starting biometric verification for customer: {}", customer.getCustomerId());
        
        try {
            // Build request payload
            Map<String, Object> request = new HashMap<>();
            request.put("customer_id", customer.getCustomerId());
            request.put("selfie_url", customer.getSelfieUrl());
            request.put("id_photo_url", customer.getIdPhotoUrl());
            
            // Call service
            String url = config.getBiometricUrl();
            ServiceResponse response = httpClient.post(url, request, config.getBiometricTimeout());
            
            // Parse and process response
            return processResponse(response, customer.getCustomerId());
            
        } catch (Exception e) {
            logger.error("Biometric verification failed for customer {}: {}", 
                    customer.getCustomerId(), e.getMessage());
            return VerificationResult.builder()
                    .verificationType(VerificationType.FACE_MATCH)
                    .status(VerificationStatus.MANUAL_REVIEW)
                    .confidence(0)
                    .reasons(List.of("Service error: " + e.getMessage()))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    private VerificationResult processResponse(ServiceResponse response, String customerId) {
        if (!response.isSuccess()) {
            logger.warn("Biometric service returned non-success for customer {}: {}",
                    customerId, response.getStatusCode());
            return VerificationResult.builder()
                    .verificationType(VerificationType.FACE_MATCH)
                    .status(VerificationStatus.MANUAL_REVIEW)
                    .confidence(0)
                    .reasons(List.of("Service returned error: " + response.getStatusCode()))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        JsonObject json = JsonUtils.fromJson(response.getBody(), JsonObject.class);
        String status = json.has("status") ? json.get("status").getAsString() : "FAIL";
        int confidence = json.has("confidence") ? json.get("confidence").getAsInt() : 0;
        int similarityScore = json.has("similarity_score") ? json.get("similarity_score").getAsInt() : 0;
        
        List<String> reasons = new ArrayList<>();
        
        // Apply business rules
        VerificationStatus finalStatus = determineStatus(status, confidence, similarityScore, reasons);
        
        logger.info("Biometric verification completed for customer {}: status={}, confidence={}, similarity={}",
                customerId, finalStatus, confidence, similarityScore);
        
        return VerificationResult.builder()
                .verificationType(VerificationType.FACE_MATCH)
                .status(finalStatus)
                .confidence(confidence)
                .reasons(reasons)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private VerificationStatus determineStatus(String serviceStatus, int confidence, 
                                               int similarityScore, List<String> reasons) {
        if ("FAIL".equals(serviceStatus)) {
            reasons.add("Face match failed");
            return VerificationStatus.FAIL;
        }
        
        int confidenceThreshold = config.getBiometricConfidenceThreshold();
        int similarityThreshold = config.getBiometricSimilarityThreshold();
        
        // Both confidence and similarity must exceed threshold for PASS
        if (confidence > confidenceThreshold && similarityScore > similarityThreshold) {
            return VerificationStatus.PASS;
        }
        
        // Add specific reasons for low scores
        if (confidence <= confidenceThreshold) {
            reasons.add("Low confidence score (" + confidence + "% <= " + confidenceThreshold + "%)");
        }
        if (similarityScore <= similarityThreshold) {
            reasons.add("Low similarity score (" + similarityScore + "% <= " + similarityThreshold + "%)");
        }
        
        return VerificationStatus.MANUAL_REVIEW;
    }
}
