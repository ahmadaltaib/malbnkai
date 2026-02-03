package com.example.ekyc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Central configuration class for the eKYC service.
 * Loads configuration from environment variables with sensible defaults.
 * Follows the 12-factor app methodology.
 */
public class ServiceConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceConfig.class);
    
    // Singleton instance
    private static ServiceConfig instance;
    
    // Service URLs
    private final String baseUrl;
    private final String documentEndpoint;
    private final String biometricEndpoint;
    private final String addressEndpoint;
    private final String sanctionsEndpoint;
    
    // Timeouts (seconds)
    private final int documentTimeout;
    private final int biometricTimeout;
    private final int addressTimeout;
    private final int sanctionsTimeout;
    
    // Confidence thresholds (percentage)
    private final int documentConfidenceThreshold;
    private final int biometricConfidenceThreshold;
    private final int biometricSimilarityThreshold;
    private final int addressConfidenceThreshold;
    
    // Business rules
    private final int addressProofValidityDays;
    
    // Retry settings
    private final int maxRetryAttempts;
    private final int[] retryBackoffMs;
    
    // Rate limiting
    private final int rateLimitRequests;
    private final int rateLimitWindowSeconds;

    private ServiceConfig() {
        logger.info("Loading eKYC service configuration from environment variables");
        
        // Service URLs
        this.baseUrl = getEnv("EKYC_SERVICE_BASE_URL", "http://localhost:8080");
        this.documentEndpoint = getEnv("EKYC_DOCUMENT_ENDPOINT", "/api/v1/verify-document");
        this.biometricEndpoint = getEnv("EKYC_BIOMETRIC_ENDPOINT", "/api/v1/face-match");
        this.addressEndpoint = getEnv("EKYC_ADDRESS_ENDPOINT", "/api/v1/verify-address");
        this.sanctionsEndpoint = getEnv("EKYC_SANCTIONS_ENDPOINT", "/api/v1/check-sanctions");
        
        // Timeouts
        this.documentTimeout = getEnvInt("EKYC_DOCUMENT_TIMEOUT", 5);
        this.biometricTimeout = getEnvInt("EKYC_BIOMETRIC_TIMEOUT", 8);
        this.addressTimeout = getEnvInt("EKYC_ADDRESS_TIMEOUT", 5);
        this.sanctionsTimeout = getEnvInt("EKYC_SANCTIONS_TIMEOUT", 3);
        
        // Confidence thresholds
        this.documentConfidenceThreshold = getEnvInt("EKYC_DOCUMENT_CONFIDENCE_THRESHOLD", 85);
        this.biometricConfidenceThreshold = getEnvInt("EKYC_BIOMETRIC_CONFIDENCE_THRESHOLD", 85);
        this.biometricSimilarityThreshold = getEnvInt("EKYC_BIOMETRIC_SIMILARITY_THRESHOLD", 85);
        this.addressConfidenceThreshold = getEnvInt("EKYC_ADDRESS_CONFIDENCE_THRESHOLD", 80);
        
        // Business rules
        this.addressProofValidityDays = getEnvInt("EKYC_ADDRESS_PROOF_VALIDITY_DAYS", 90);
        
        // Retry settings
        this.maxRetryAttempts = getEnvInt("EKYC_MAX_RETRY_ATTEMPTS", 3);
        this.retryBackoffMs = getEnvIntArray("EKYC_RETRY_BACKOFF_MS", new int[]{1000, 2000, 4000});
        
        // Rate limiting
        this.rateLimitRequests = getEnvInt("EKYC_RATE_LIMIT_REQUESTS", 10);
        this.rateLimitWindowSeconds = getEnvInt("EKYC_RATE_LIMIT_WINDOW_SECONDS", 60);
        
        logConfiguration();
    }

    /**
     * Gets the singleton instance of ServiceConfig.
     * Thread-safe initialization.
     */
    public static synchronized ServiceConfig getInstance() {
        if (instance == null) {
            instance = new ServiceConfig();
        }
        return instance;
    }

    /**
     * Resets the singleton instance. Useful for testing.
     */
    public static synchronized void reset() {
        instance = null;
    }

    // Getters for Service URLs
    
    public String getBaseUrl() {
        return baseUrl;
    }

    public String getDocumentEndpoint() {
        return documentEndpoint;
    }

    public String getBiometricEndpoint() {
        return biometricEndpoint;
    }

    public String getAddressEndpoint() {
        return addressEndpoint;
    }

    public String getSanctionsEndpoint() {
        return sanctionsEndpoint;
    }

    public String getDocumentUrl() {
        return baseUrl + documentEndpoint;
    }

    public String getBiometricUrl() {
        return baseUrl + biometricEndpoint;
    }

    public String getAddressUrl() {
        return baseUrl + addressEndpoint;
    }

    public String getSanctionsUrl() {
        return baseUrl + sanctionsEndpoint;
    }

    // Getters for Timeouts
    
    public int getDocumentTimeout() {
        return documentTimeout;
    }

    public int getBiometricTimeout() {
        return biometricTimeout;
    }

    public int getAddressTimeout() {
        return addressTimeout;
    }

    public int getSanctionsTimeout() {
        return sanctionsTimeout;
    }

    // Getters for Confidence Thresholds
    
    public int getDocumentConfidenceThreshold() {
        return documentConfidenceThreshold;
    }

    public int getBiometricConfidenceThreshold() {
        return biometricConfidenceThreshold;
    }

    public int getBiometricSimilarityThreshold() {
        return biometricSimilarityThreshold;
    }

    public int getAddressConfidenceThreshold() {
        return addressConfidenceThreshold;
    }

    // Getters for Business Rules
    
    public int getAddressProofValidityDays() {
        return addressProofValidityDays;
    }

    // Getters for Retry Settings
    
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public int[] getRetryBackoffMs() {
        return retryBackoffMs.clone(); // Return a copy for immutability
    }

    // Getters for Rate Limiting
    
    public int getRateLimitRequests() {
        return rateLimitRequests;
    }

    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    // Helper methods for reading environment variables
    
    private String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            logger.debug("Using default value for {}: {}", key, defaultValue);
            return defaultValue;
        }
        logger.debug("Loaded {} from environment: {}", key, value);
        return value;
    }

    private int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            logger.debug("Using default value for {}: {}", key, defaultValue);
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            logger.debug("Loaded {} from environment: {}", key, parsed);
            return parsed;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: '{}'. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private int[] getEnvIntArray(String key, int[] defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            logger.debug("Using default value for {}: {}", key, Arrays.toString(defaultValue));
            return defaultValue;
        }
        try {
            String[] parts = value.split(",");
            int[] result = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim());
            }
            logger.debug("Loaded {} from environment: {}", key, Arrays.toString(result));
            return result;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer array value for {}: '{}'. Using default: {}", 
                    key, value, Arrays.toString(defaultValue));
            return defaultValue;
        }
    }

    private void logConfiguration() {
        logger.info("=== eKYC Service Configuration ===");
        logger.info("Base URL: {}", baseUrl);
        logger.info("Timeouts - Document: {}s, Biometric: {}s, Address: {}s, Sanctions: {}s",
                documentTimeout, biometricTimeout, addressTimeout, sanctionsTimeout);
        logger.info("Thresholds - Document: {}%, Biometric: {}%/{}%, Address: {}%",
                documentConfidenceThreshold, biometricConfidenceThreshold, 
                biometricSimilarityThreshold, addressConfidenceThreshold);
        logger.info("Address proof validity: {} days", addressProofValidityDays);
        logger.info("Retry: {} attempts, backoff: {}ms", maxRetryAttempts, Arrays.toString(retryBackoffMs));
        logger.info("Rate limit: {} requests per {} seconds", rateLimitRequests, rateLimitWindowSeconds);
    }
}
