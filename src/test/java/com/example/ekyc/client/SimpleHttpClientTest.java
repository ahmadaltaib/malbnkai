package com.example.ekyc.client;

import com.example.ekyc.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimpleHttpClient including rate limiting.
 */
class SimpleHttpClientTest {
    
    private SimpleHttpClient httpClient;
    
    private static final String DOCUMENT_URL = "http://localhost:8080/api/v1/verify-document";
    private static final String SANCTIONS_URL = "http://localhost:8080/api/v1/check-sanctions";
    private static final Object TEST_BODY = Map.of("customer_id", "CUST-001");

    @BeforeEach
    void setUp() {
        // Use explicit parameters to avoid depending on ServiceConfig singleton
        httpClient = new SimpleHttpClient(10, 60); // 10 requests per 60 seconds
        httpClient.clearRateLimits();
        httpClient.resetSimulations();
    }

    @Test
    @DisplayName("test_rate_limit_enforcement: Make 11 requests rapidly, 10 succeed, 11th blocked")
    void testRateLimitEnforcement() {
        // Given: Rate limit is 10 requests per minute
        
        // When: Make 10 requests (should all succeed)
        for (int i = 0; i < 10; i++) {
            ServiceResponse response = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
            assertTrue(response.isSuccess(), "Request " + (i + 1) + " should succeed");
        }

        // Then: 11th request should be rate limited
        assertThrows(RateLimitException.class, 
                () -> httpClient.post(DOCUMENT_URL, TEST_BODY, 5),
                "11th request should be rate limited");
    }

    @Test
    @DisplayName("Rate limits are independent per service")
    void testRateLimitsPerService() {
        // Given: Two different services
        
        // When: Make 10 requests to document service
        for (int i = 0; i < 10; i++) {
            ServiceResponse response = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
            assertTrue(response.isSuccess());
        }
        
        // Then: Can still make requests to sanctions service (different rate limit)
        ServiceResponse response = httpClient.post(SANCTIONS_URL, TEST_BODY, 3);
        assertTrue(response.isSuccess(), "Sanctions service should have its own rate limit");
    }

    @Test
    @DisplayName("Default document verification response")
    void testDefaultDocumentResponse() {
        ServiceResponse response = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        
        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("PASS"));
    }

    @Test
    @DisplayName("Default sanctions screening response")
    void testDefaultSanctionsResponse() {
        ServiceResponse response = httpClient.post(SANCTIONS_URL, TEST_BODY, 3);
        
        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("CLEAR"));
    }

    @Test
    @DisplayName("Simulate timeout")
    void testSimulateTimeout() {
        // Given: Configure to simulate 2 timeouts before success
        httpClient.simulateTimeouts(2);
        
        // When/Then: First two requests timeout
        ServiceResponse response1 = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        assertTrue(response1.isTimedOut());
        
        ServiceResponse response2 = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        assertTrue(response2.isTimedOut());
        
        // Third request succeeds
        ServiceResponse response3 = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        assertTrue(response3.isSuccess());
    }

    @Test
    @DisplayName("Simulate server errors")
    void testSimulateServerErrors() {
        // Given: Configure to simulate 2 server errors before success
        httpClient.simulateServerErrors(500, 2);
        
        // When/Then: First two requests return 500
        ServiceResponse response1 = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        assertTrue(response1.isServerError());
        assertEquals(500, response1.getStatusCode());
        
        ServiceResponse response2 = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        assertTrue(response2.isServerError());
        
        // Third request succeeds
        ServiceResponse response3 = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        assertTrue(response3.isSuccess());
    }

    @Test
    @DisplayName("Custom response handler")
    void testCustomResponseHandler() {
        // Given: Register a custom handler
        httpClient.registerHandler(DOCUMENT_URL, body -> 
                ServiceResponse.success(200, "{\"status\": \"FAIL\", \"confidence\": 50}"));
        
        // When
        ServiceResponse response = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        
        // Then
        assertTrue(response.isSuccess());
        assertTrue(response.getBody().contains("FAIL"));
        assertTrue(response.getBody().contains("50"));
    }

    @Test
    @DisplayName("Unknown endpoint returns 404")
    void testUnknownEndpoint() {
        ServiceResponse response = httpClient.post("http://localhost:8080/api/v1/unknown", TEST_BODY, 5);
        
        assertEquals(404, response.getStatusCode());
        assertTrue(response.isClientError());
    }

    @Test
    @DisplayName("Reset simulations")
    void testResetSimulations() {
        // Given: Configure simulations
        httpClient.simulateTimeouts(5);
        httpClient.simulateServerErrors(500, 5);
        
        // When: Reset
        httpClient.resetSimulations();
        
        // Then: Normal response
        ServiceResponse response = httpClient.post(DOCUMENT_URL, TEST_BODY, 5);
        assertTrue(response.isSuccess());
    }
}
