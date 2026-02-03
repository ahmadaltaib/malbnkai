package com.example.ekyc;

import com.example.ekyc.config.ServiceConfig;
import com.example.ekyc.model.Customer;
import com.example.ekyc.model.KYCDecision;
import com.example.ekyc.service.VerificationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point demonstrating the eKYC verification service.
 * Configuration is loaded from environment variables with sensible defaults.
 */
public class Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        logger.info("Starting eKYC Verification Service");
        
        // Load configuration (from environment variables or defaults)
        ServiceConfig config = ServiceConfig.getInstance();
        logger.info("Using base URL: {}", config.getBaseUrl());
        
        // Create a sample customer
        Customer customer = Customer.builder()
                .customerId("CUST-001")
                .fullName("John Doe")
                .dateOfBirth("1990-05-15")
                .email("john.doe@example.com")
                .phone("+1-555-123-4567")
                .address("123 Main Street, New York, NY 10001")
                .nationality("US")
                .documentType("PASSPORT")
                .documentNumber("AB1234567")
                .documentExpiryDate("2028-05-15")
                .documentImageUrl("https://example.com/docs/passport.jpg")
                .selfieUrl("https://example.com/selfie.jpg")
                .idPhotoUrl("https://example.com/id_photo.jpg")
                .proofType("UTILITY_BILL")
                .proofDate("2026-01-15")
                .proofUrl("https://example.com/proof.pdf")
                .build();
        
        // Create orchestrator using configuration
        VerificationOrchestrator orchestrator = new VerificationOrchestrator();
        
        logger.info("Processing full KYC verification for customer: {}", customer.getCustomerId());
        KYCDecision decision = orchestrator.processFullVerification(customer);
        
        logger.info("=== KYC Decision ===");
        logger.info("Decision: {}", decision.getDecision());
        logger.info("Correlation ID: {}", decision.getCorrelationId());
        logger.info("Verification Results:");
        decision.getVerificationResults().forEach(result -> 
            logger.info("  - {}: {} (confidence: {}%)", 
                    result.getVerificationType(), 
                    result.getStatus(), 
                    result.getConfidence())
        );
        
        logger.info("eKYC Verification Service completed");
    }
}