package com.example.ekyc.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for generating and managing correlation IDs for request tracing.
 */
public final class CorrelationIdGenerator {
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    
    private CorrelationIdGenerator() {
        // Utility class, no instantiation
    }

    /**
     * Generates a new correlation ID.
     * @return A unique correlation ID
     */
    public static String generate() {
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Sets the correlation ID in the MDC for logging.
     * @param correlationId The correlation ID to set
     */
    public static void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }

    /**
     * Gets the current correlation ID from the MDC.
     * @return The current correlation ID or null if not set
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * Clears the correlation ID from the MDC.
     */
    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
