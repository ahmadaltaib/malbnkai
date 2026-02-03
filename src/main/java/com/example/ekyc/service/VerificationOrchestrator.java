package com.example.ekyc.service;

import com.example.ekyc.client.HttpClient;
import com.example.ekyc.client.RetryableHttpClient;
import com.example.ekyc.client.SimpleHttpClient;
import com.example.ekyc.config.ServiceConfig;
import com.example.ekyc.model.Customer;
import com.example.ekyc.model.KYCDecision;
import com.example.ekyc.model.VerificationResult;
import com.example.ekyc.model.VerificationType;
import com.example.ekyc.util.CorrelationIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the KYC verification process.
 * Coordinates calls to all verification services and aggregates results.
 */
public class VerificationOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(VerificationOrchestrator.class);
    
    private final DocumentVerificationClient documentClient;
    private final BiometricVerificationClient biometricClient;
    private final AddressVerificationClient addressClient;
    private final SanctionsScreeningClient sanctionsClient;
    private final KYCDecisionEngine decisionEngine;

    /**
     * Creates an orchestrator with default configuration from ServiceConfig.
     * Uses environment variables or defaults for all settings.
     */
    public VerificationOrchestrator() {
        SimpleHttpClient simpleClient = new SimpleHttpClient();
        HttpClient retryableClient = new RetryableHttpClient(simpleClient);
        
        this.documentClient = new DocumentVerificationClient(retryableClient);
        this.biometricClient = new BiometricVerificationClient(retryableClient);
        this.addressClient = new AddressVerificationClient(retryableClient);
        this.sanctionsClient = new SanctionsScreeningClient(retryableClient);
        this.decisionEngine = new KYCDecisionEngine();
    }

    /**
     * Creates an orchestrator with a custom ServiceConfig.
     * @param config The service configuration to use
     */
    public VerificationOrchestrator(ServiceConfig config) {
        SimpleHttpClient simpleClient = new SimpleHttpClient(
                config.getRateLimitRequests(), 
                config.getRateLimitWindowSeconds());
        HttpClient retryableClient = new RetryableHttpClient(
                simpleClient,
                config.getMaxRetryAttempts(),
                config.getRetryBackoffMs());
        
        this.documentClient = new DocumentVerificationClient(retryableClient, config);
        this.biometricClient = new BiometricVerificationClient(retryableClient, config);
        this.addressClient = new AddressVerificationClient(retryableClient, config);
        this.sanctionsClient = new SanctionsScreeningClient(retryableClient, config);
        this.decisionEngine = new KYCDecisionEngine();
    }

    /**
     * Creates an orchestrator with custom service clients.
     * Useful for testing with mocked clients.
     */
    public VerificationOrchestrator(DocumentVerificationClient documentClient,
                                     BiometricVerificationClient biometricClient,
                                     AddressVerificationClient addressClient,
                                     SanctionsScreeningClient sanctionsClient,
                                     KYCDecisionEngine decisionEngine) {
        this.documentClient = documentClient;
        this.biometricClient = biometricClient;
        this.addressClient = addressClient;
        this.sanctionsClient = sanctionsClient;
        this.decisionEngine = decisionEngine;
    }

    /**
     * Processes a full verification request for a customer.
     * Executes all verification types and returns the final decision.
     * 
     * @param customer The customer to verify
     * @param verificationTypes List of verification types to perform
     * @return The final KYC decision with all results
     */
    public KYCDecision processVerification(Customer customer, List<VerificationType> verificationTypes) {
        String correlationId = CorrelationIdGenerator.generate();
        CorrelationIdGenerator.setCorrelationId(correlationId);
        
        try {
            logger.info("Starting KYC verification for customer: {} with types: {}",
                    customer.getCustomerId(), verificationTypes);
            
            List<VerificationResult> results = new ArrayList<>();
            
            // Execute each verification type
            for (VerificationType type : verificationTypes) {
                VerificationResult result = executeVerification(customer, type);
                results.add(result);
                
                logger.info("Verification {} completed: status={}, confidence={}",
                        type, result.getStatus(), result.getConfidence());
            }
            
            // Make final decision
            KYCDecision decision = decisionEngine.makeDecision(results, correlationId);
            
            logger.info("KYC verification completed for customer {}: decision={}",
                    customer.getCustomerId(), decision.getDecision());
            
            return decision;
            
        } finally {
            CorrelationIdGenerator.clear();
        }
    }

    /**
     * Processes a full verification request with all verification types.
     * 
     * @param customer The customer to verify
     * @return The final KYC decision with all results
     */
    public KYCDecision processFullVerification(Customer customer) {
        return processVerification(customer, List.of(
                VerificationType.ID_DOCUMENT,
                VerificationType.FACE_MATCH,
                VerificationType.ADDRESS,
                VerificationType.SANCTIONS
        ));
    }

    private VerificationResult executeVerification(Customer customer, VerificationType type) {
        logger.debug("Executing verification: {}", type);
        
        switch (type) {
            case ID_DOCUMENT:
                return documentClient.verifyDocument(customer);
            case FACE_MATCH:
                return biometricClient.verifyFaceMatch(customer);
            case ADDRESS:
                return addressClient.verifyAddress(customer);
            case SANCTIONS:
                return sanctionsClient.checkSanctions(customer);
            default:
                throw new IllegalArgumentException("Unknown verification type: " + type);
        }
    }
}
