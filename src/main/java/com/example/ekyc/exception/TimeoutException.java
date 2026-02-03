package com.example.ekyc.exception;

/**
 * Exception thrown when a service call times out.
 */
public class TimeoutException extends ServiceException {
    private final int timeoutSeconds;

    public TimeoutException(String message, String serviceName, int timeoutSeconds) {
        super(message, serviceName, 0);
        this.timeoutSeconds = timeoutSeconds;
    }

    public TimeoutException(String message, String serviceName, int timeoutSeconds, Throwable cause) {
        super(message, serviceName, 0, cause);
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public boolean isRetryable() {
        return true; // Timeouts are always retryable
    }
}
