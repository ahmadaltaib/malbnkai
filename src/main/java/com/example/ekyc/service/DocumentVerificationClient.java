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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the Document Verification Service.
 * Validates identity documents (passport, driver's license, etc.).
 * 
 * Business Rules:
 * - Document must NOT be expired
 * - Confidence score > configured threshold for PASS
 * - Expired or low confidence → REJECTED or MANUAL_REVIEW
 */
public class DocumentVerificationClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentVerificationClient.class);
    
    private final HttpClient httpClient;
    private final ServiceConfig config;

    public DocumentVerificationClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.config = ServiceConfig.getInstance();
    }

    public DocumentVerificationClient(HttpClient httpClient, ServiceConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public VerificationResult verifyDocument(Customer customer) {
        logger.info("Starting document verification for customer: {}", customer.getCustomerId());
        
        try {
            // Check expiry date first (business rule)
            if (isDocumentExpired(customer.getDocumentExpiryDate())) {
                logger.warn("Document is expired for customer: {}", customer.getCustomerId());
                return VerificationResult.builder()
                        .verificationType(VerificationType.ID_DOCUMENT)
                        .status(VerificationStatus.FAIL)
                        .confidence(0)
                        .reasons(List.of("Document has expired"))
                        .timestamp(LocalDateTime.now())
                        .build();
            }
            
            // Build request payload
            Map<String, Object> request = new HashMap<>();
            request.put("customer_id", customer.getCustomerId());
            request.put("document_type", customer.getDocumentType());
            request.put("document_number", maskDocumentNumber(customer.getDocumentNumber()));
            request.put("expiry_date", customer.getDocumentExpiryDate());
            request.put("document_image_url", customer.getDocumentImageUrl());
            
            // Call service
            String url = config.getDocumentUrl();
            ServiceResponse response = httpClient.post(url, request, config.getDocumentTimeout());
            
            // Parse and process response
            return processResponse(response, customer.getCustomerId());
            
        } catch (Exception e) {
            logger.error("Document verification failed for customer {}: {}", 
                    customer.getCustomerId(), e.getMessage());
            return VerificationResult.builder()
                    .verificationType(VerificationType.ID_DOCUMENT)
                    .status(VerificationStatus.MANUAL_REVIEW)
                    .confidence(0)
                    .reasons(List.of("Service error: " + e.getMessage()))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    private VerificationResult processResponse(ServiceResponse response, String customerId) {
        if (!response.isSuccess()) {
            logger.warn("Document service returned non-success for customer {}: {}",
                    customerId, response.getStatusCode());
            return VerificationResult.builder()
                    .verificationType(VerificationType.ID_DOCUMENT)
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
        
        // Apply business rule: confidence > 85% for PASS
        VerificationStatus finalStatus = determineStatus(status, confidence, reasons);
        
        logger.info("Document verification completed for customer {}: status={}, confidence={}",
                customerId, finalStatus, confidence);
        
        return VerificationResult.builder()
                .verificationType(VerificationType.ID_DOCUMENT)
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
        
        int threshold = config.getDocumentConfidenceThreshold();
        if (confidence > threshold) {
            return VerificationStatus.PASS;
        }
        
        // Low confidence
        if (reasons.isEmpty()) {
            reasons.add("Confidence score below threshold (" + confidence + "% < " + threshold + "%)");
        }
        return VerificationStatus.MANUAL_REVIEW;
    }

    private boolean isDocumentExpired(String expiryDate) {
        if (expiryDate == null || expiryDate.isEmpty()) {
            return true;
        }
        
        try {
            LocalDate expiry = LocalDate.parse(expiryDate);
            return expiry.isBefore(LocalDate.now());
        } catch (DateTimeParseException e) {
            logger.warn("Invalid expiry date format: {}", expiryDate);
            return true;
        }
    }

    private String maskDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.length() <= 4) {
            return "****";
        }
        return "****" + documentNumber.substring(documentNumber.length() - 4);
    }
}
