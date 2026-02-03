package com.example.ekyc.client;

import com.example.ekyc.config.ServiceConfig;
import com.example.ekyc.exception.ServiceException;
import com.example.ekyc.exception.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client wrapper that implements retry logic with exponential backoff.
 * Retries on timeouts and 5xx errors, but NOT on 4xx errors.
 */
public class RetryableHttpClient implements HttpClient {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryableHttpClient.class);
    
    private final HttpClient delegate;
    private final int maxRetryAttempts;
    private final int[] backoffDelaysMs;

    public RetryableHttpClient(HttpClient delegate) {
        this.delegate = delegate;
        ServiceConfig config = ServiceConfig.getInstance();
        this.maxRetryAttempts = config.getMaxRetryAttempts();
        this.backoffDelaysMs = config.getRetryBackoffMs();
    }

    public RetryableHttpClient(HttpClient delegate, int maxRetryAttempts, int[] backoffDelaysMs) {
        this.delegate = delegate;
        this.maxRetryAttempts = maxRetryAttempts;
        this.backoffDelaysMs = backoffDelaysMs;
    }

    @Override
    public ServiceResponse post(String url, Object body, int timeoutSeconds) {
        String serviceName = extractServiceName(url);
        ServiceResponse response = null;
        int attempts = 0;
        
        while (attempts < maxRetryAttempts) {
            attempts++;
            
            try {
                response = delegate.post(url, body, timeoutSeconds);
                
                // Check if response is successful
                if (response.isSuccess()) {
                    if (attempts > 1) {
                        logger.info("Request succeeded on attempt {} for {}", attempts, serviceName);
                    }
                    return response;
                }
                
                // Check if error is retryable (timeout or 5xx)
                if (response.isRetryable()) {
                    if (attempts < maxRetryAttempts) {
                        int delayMs = getBackoffDelay(attempts - 1);
                        if (response.isTimedOut()) {
                            logger.warn("Request timed out for {} (attempt {}/{}). Retrying in {}ms",
                                    serviceName, attempts, maxRetryAttempts, delayMs);
                        } else {
                            logger.warn("Server error {} for {} (attempt {}/{}). Retrying in {}ms",
                                    response.getStatusCode(), serviceName, attempts, maxRetryAttempts, delayMs);
                        }
                        sleep(delayMs);
                    }
                } else {
                    // 4xx errors are not retryable
                    logger.error("Non-retryable error {} for {}. Failing immediately.",
                            response.getStatusCode(), serviceName);
                    throw new ServiceException(
                            "Service returned error: " + response.getStatusCode(),
                            serviceName,
                            response.getStatusCode()
                    );
                }
                
            } catch (ServiceException e) {
                // Re-throw ServiceExceptions (including RateLimitException)
                throw e;
            } catch (Exception e) {
                logger.error("Unexpected error during request to {}: {}", serviceName, e.getMessage());
                if (attempts >= maxRetryAttempts) {
                    throw new ServiceException("Request failed after " + maxRetryAttempts + " attempts", 
                            serviceName, e);
                }
                int delayMs = getBackoffDelay(attempts - 1);
                logger.warn("Retrying after unexpected error (attempt {}/{}). Waiting {}ms",
                        attempts, maxRetryAttempts, delayMs);
                sleep(delayMs);
            }
        }
        
        // All retries exhausted
        if (response != null && response.isTimedOut()) {
            throw new TimeoutException(
                    "Request timed out after " + maxRetryAttempts + " attempts",
                    serviceName,
                    timeoutSeconds
            );
        }
        
        throw new ServiceException(
                "Request failed after " + maxRetryAttempts + " attempts",
                serviceName,
                response != null ? response.getStatusCode() : 0
        );
    }

    private int getBackoffDelay(int index) {
        if (index < backoffDelaysMs.length) {
            return backoffDelaysMs[index];
        }
        // If we run out of configured delays, use the last one
        return backoffDelaysMs[backoffDelaysMs.length - 1];
    }

    private String extractServiceName(String url) {
        if (url.contains("verify-document")) {
            return "DocumentVerification";
        } else if (url.contains("face-match")) {
            return "BiometricService";
        } else if (url.contains("verify-address")) {
            return "AddressVerification";
        } else if (url.contains("check-sanctions")) {
            return "SanctionsScreening";
        }
        return "UnknownService";
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Retry sleep interrupted");
        }
    }
}
