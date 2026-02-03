package com.example.ekyc.client;

import com.example.ekyc.exception.ServiceException;
import com.example.ekyc.exception.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RetryableHttpClient retry logic.
 */
@ExtendWith(MockitoExtension.class)
class RetryableHttpClientTest {
    
    @Mock
    private HttpClient delegateClient;
    
    private RetryableHttpClient retryableClient;
    
    private static final String TEST_URL = "http://localhost:8080/api/v1/verify-document";
    private static final Object TEST_BODY = Map.of("test", "data");
    private static final int TIMEOUT = 5;

    @BeforeEach
    void setUp() {
        // Use explicit parameters to avoid depending on ServiceConfig singleton
        int maxRetries = 3;
        int[] backoffMs = {1000, 2000, 4000};
        retryableClient = new RetryableHttpClient(delegateClient, maxRetries, backoffMs);
    }

    @Test
    @DisplayName("test_retry_on_timeout: Mock service times out twice, succeeds on 3rd attempt")
    void testRetryOnTimeout_SucceedsAfterTwoTimeouts() {
        // Given: Service times out twice, then succeeds
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.timeout())
                .thenReturn(ServiceResponse.timeout())
                .thenReturn(ServiceResponse.success(200, "{\"status\": \"PASS\"}"));

        // When
        ServiceResponse response = retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT);

        // Then
        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        verify(delegateClient, times(3)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }

    @Test
    @DisplayName("test_retry_on_5xx: Retry logic works, Service returns 500 error")
    void testRetryOn5xx_SucceedsAfterServerErrors() {
        // Given: Service returns 500 twice, then succeeds
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.error(500, "{\"error\": \"Internal Server Error\"}"))
                .thenReturn(ServiceResponse.error(503, "{\"error\": \"Service Unavailable\"}"))
                .thenReturn(ServiceResponse.success(200, "{\"status\": \"PASS\"}"));

        // When
        ServiceResponse response = retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT);

        // Then
        assertTrue(response.isSuccess());
        verify(delegateClient, times(3)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }

    @Test
    @DisplayName("test_retry_on_4xx: Fail immediately, no retries")
    void testNoRetryOn4xx_FailsImmediately() {
        // Given: Service returns 400 Bad Request
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.error(400, "{\"error\": \"Bad Request\"}"));

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, 
                () -> retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT));
        
        assertEquals(400, exception.getStatusCode());
        assertFalse(exception.isRetryable());
        
        // Verify only called once (no retries for 4xx)
        verify(delegateClient, times(1)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }

    @Test
    @DisplayName("No retry on 404 - client error")
    void testNoRetryOn404() {
        // Given
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.error(404, "{\"error\": \"Not Found\"}"));

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, 
                () -> retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT));
        
        assertEquals(404, exception.getStatusCode());
        verify(delegateClient, times(1)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }

    @Test
    @DisplayName("All retries exhausted on timeout → TimeoutException")
    void testAllRetriesExhaustedOnTimeout() {
        // Given: Service always times out
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.timeout());

        // When/Then
        assertThrows(TimeoutException.class, 
                () -> retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT));
        
        // Should try 3 times total
        verify(delegateClient, times(3)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }

    @Test
    @DisplayName("All retries exhausted on 5xx → ServiceException")
    void testAllRetriesExhaustedOn5xx() {
        // Given: Service always returns 500
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.error(500, "{\"error\": \"Internal Server Error\"}"));

        // When/Then
        ServiceException exception = assertThrows(ServiceException.class, 
                () -> retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT));
        
        assertEquals(500, exception.getStatusCode());
        verify(delegateClient, times(3)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }

    @Test
    @DisplayName("Success on first attempt - no retries needed")
    void testSuccessOnFirstAttempt() {
        // Given
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.success(200, "{\"status\": \"PASS\"}"));

        // When
        ServiceResponse response = retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT);

        // Then
        assertTrue(response.isSuccess());
        verify(delegateClient, times(1)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }

    @Test
    @DisplayName("Mixed errors - timeout then 500 then success")
    void testMixedErrors_EventualSuccess() {
        // Given: First timeout, then 500, then success
        when(delegateClient.post(anyString(), any(), anyInt()))
                .thenReturn(ServiceResponse.timeout())
                .thenReturn(ServiceResponse.error(502, "{\"error\": \"Bad Gateway\"}"))
                .thenReturn(ServiceResponse.success(200, "{\"status\": \"PASS\"}"));

        // When
        ServiceResponse response = retryableClient.post(TEST_URL, TEST_BODY, TIMEOUT);

        // Then
        assertTrue(response.isSuccess());
        verify(delegateClient, times(3)).post(TEST_URL, TEST_BODY, TIMEOUT);
    }
}
