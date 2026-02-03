package com.example.ekyc.exception;

/**
 * Base exception for all service-related errors.
 */
public class ServiceException extends RuntimeException {
    private final String serviceName;
    private final int statusCode;

    public ServiceException(String message, String serviceName) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = 0;
    }

    public ServiceException(String message, String serviceName, int statusCode) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public ServiceException(String message, String serviceName, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = 0;
    }

    public ServiceException(String message, String serviceName, int statusCode, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return statusCode >= 500 && statusCode < 600;
    }
}
