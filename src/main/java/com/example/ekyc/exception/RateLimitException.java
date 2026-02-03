package com.example.ekyc.exception;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class RateLimitException extends ServiceException {
    private final int requestsPerMinute;

    public RateLimitException(String message, String serviceName, int requestsPerMinute) {
        super(message, serviceName, 429);
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    @Override
    public boolean isRetryable() {
        return false; // Rate limit should not be retried immediately
    }
}
