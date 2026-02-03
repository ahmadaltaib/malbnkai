package com.example.ekyc.client;

import com.example.ekyc.config.ServiceConfig;
import com.example.ekyc.exception.RateLimitException;
import com.example.ekyc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

/**
 * Mock HTTP client implementation for testing.
 * Simulates HTTP responses without making actual network calls.
 */
public class SimpleHttpClient implements HttpClient {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleHttpClient.class);
    
    // Rate limit settings (configurable)
    private final int rateLimitRequests;
    private final int rateLimitWindowSeconds;
    
    // Configurable response handlers for each endpoint
    private final Map<String, Function<Object, ServiceResponse>> responseHandlers = new ConcurrentHashMap<>();
    
    // Rate limiting: track timestamps per service endpoint
    private final Map<String, Deque<Instant>> requestTimestamps = new ConcurrentHashMap<>();
    
    // Configuration flags for testing scenarios
    private boolean simulateTimeout = false;
    private int timeoutsBeforeSuccess = 0;
    private int timeoutCounter = 0;
    private int serverErrorCode = 0;
    private int serverErrorsBeforeSuccess = 0;
    private int serverErrorCounter = 0;

    public SimpleHttpClient() {
        ServiceConfig config = ServiceConfig.getInstance();
        this.rateLimitRequests = config.getRateLimitRequests();
        this.rateLimitWindowSeconds = config.getRateLimitWindowSeconds();
    }

    public SimpleHttpClient(int rateLimitRequests, int rateLimitWindowSeconds) {
        this.rateLimitRequests = rateLimitRequests;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    @Override
    public ServiceResponse post(String url, Object body, int timeoutSeconds) {
        logger.debug("Making POST request to {} with timeout {}s", url, timeoutSeconds);
        
        // Check rate limit
        String serviceKey = extractServiceKey(url);
        if (!checkAndUpdateRateLimit(serviceKey)) {
            logger.warn("Rate limit exceeded for service: {}", serviceKey);
            throw new RateLimitException(
                    "Rate limit exceeded: " + rateLimitRequests + " requests per minute",
                    serviceKey,
                    rateLimitRequests
            );
        }
        
        // Handle simulated timeouts
        if (simulateTimeout && timeoutCounter < timeoutsBeforeSuccess) {
            timeoutCounter++;
            logger.debug("Simulating timeout ({}/{})", timeoutCounter, timeoutsBeforeSuccess);
            return ServiceResponse.timeout();
        }
        
        // Handle simulated server errors
        if (serverErrorCode > 0 && serverErrorCounter < serverErrorsBeforeSuccess) {
            serverErrorCounter++;
            logger.debug("Simulating server error {} ({}/{})", 
                    serverErrorCode, serverErrorCounter, serverErrorsBeforeSuccess);
            return ServiceResponse.error(serverErrorCode, "{\"error\": \"Internal Server Error\"}");
        }
        
        // Check for configured response handler
        Function<Object, ServiceResponse> handler = responseHandlers.get(url);
        if (handler != null) {
            return handler.apply(body);
        }
        
        // Default mock responses based on endpoint
        return generateDefaultResponse(url, body);
    }

    /**
     * Registers a custom response handler for an endpoint.
     */
    public void registerHandler(String url, Function<Object, ServiceResponse> handler) {
        responseHandlers.put(url, handler);
    }

    /**
     * Clears all registered handlers.
     */
    public void clearHandlers() {
        responseHandlers.clear();
    }

    /**
     * Configures the client to simulate timeouts.
     * @param count Number of timeouts before returning success
     */
    public void simulateTimeouts(int count) {
        this.simulateTimeout = true;
        this.timeoutsBeforeSuccess = count;
        this.timeoutCounter = 0;
    }

    /**
     * Configures the client to simulate server errors.
     * @param errorCode HTTP error code (5xx)
     * @param count Number of errors before returning success
     */
    public void simulateServerErrors(int errorCode, int count) {
        this.serverErrorCode = errorCode;
        this.serverErrorsBeforeSuccess = count;
        this.serverErrorCounter = 0;
    }

    /**
     * Resets all simulation configurations.
     */
    public void resetSimulations() {
        this.simulateTimeout = false;
        this.timeoutsBeforeSuccess = 0;
        this.timeoutCounter = 0;
        this.serverErrorCode = 0;
        this.serverErrorsBeforeSuccess = 0;
        this.serverErrorCounter = 0;
    }

    /**
     * Clears rate limit tracking for testing.
     */
    public void clearRateLimits() {
        requestTimestamps.clear();
    }

    private boolean checkAndUpdateRateLimit(String serviceKey) {
        Deque<Instant> timestamps = requestTimestamps.computeIfAbsent(
                serviceKey, k -> new ConcurrentLinkedDeque<>());
        
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(rateLimitWindowSeconds);
        
        // Remove timestamps outside the window
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }
        
        // Check if we're at the limit
        if (timestamps.size() >= rateLimitRequests) {
            return false;
        }
        
        // Add current timestamp
        timestamps.addLast(now);
        return true;
    }

    private String extractServiceKey(String url) {
        // Extract the service path (e.g., /api/v1/verify-document)
        int apiIndex = url.indexOf("/api/");
        if (apiIndex >= 0) {
            return url.substring(apiIndex);
        }
        return url;
    }

    private ServiceResponse generateDefaultResponse(String url, Object body) {
        String requestJson = JsonUtils.toJson(body);
        logger.debug("Request body: {}", requestJson);
        
        if (url.contains("verify-document")) {
            return generateDocumentResponse();
        } else if (url.contains("face-match")) {
            return generateFaceMatchResponse();
        } else if (url.contains("verify-address")) {
            return generateAddressResponse();
        } else if (url.contains("check-sanctions")) {
            return generateSanctionsResponse();
        }
        
        return ServiceResponse.error(404, "{\"error\": \"Unknown endpoint\"}");
    }

    private ServiceResponse generateDocumentResponse() {
        String response = JsonUtils.toJson(Map.of(
                "status", "PASS",
                "confidence", 92,
                "reasons", new String[]{}
        ));
        return ServiceResponse.success(200, response);
    }

    private ServiceResponse generateFaceMatchResponse() {
        String response = JsonUtils.toJson(Map.of(
                "status", "PASS",
                "confidence", 88,
                "similarity_score", 91
        ));
        return ServiceResponse.success(200, response);
    }

    private ServiceResponse generateAddressResponse() {
        String response = JsonUtils.toJson(Map.of(
                "status", "PASS",
                "confidence", 85,
                "reasons", new String[]{}
        ));
        return ServiceResponse.success(200, response);
    }

    private ServiceResponse generateSanctionsResponse() {
        String response = JsonUtils.toJson(Map.of(
                "status", "CLEAR",
                "match_count", 0,
                "matches", new String[]{}
        ));
        return ServiceResponse.success(200, response);
    }
}
