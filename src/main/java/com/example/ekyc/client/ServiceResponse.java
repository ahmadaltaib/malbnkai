package com.example.ekyc.client;

import java.util.Objects;

/**
 * Encapsulates the response from an external service call.
 */
public class ServiceResponse {
    private final int statusCode;
    private final String body;
    private final boolean timedOut;
    private final Exception exception;

    private ServiceResponse(int statusCode, String body, boolean timedOut, Exception exception) {
        this.statusCode = statusCode;
        this.body = body;
        this.timedOut = timedOut;
        this.exception = exception;
    }

    public static ServiceResponse success(int statusCode, String body) {
        return new ServiceResponse(statusCode, body, false, null);
    }

    public static ServiceResponse timeout() {
        return new ServiceResponse(0, null, true, null);
    }

    public static ServiceResponse error(int statusCode, String body) {
        return new ServiceResponse(statusCode, body, false, null);
    }

    public static ServiceResponse error(Exception exception) {
        return new ServiceResponse(0, null, false, exception);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public Exception getException() {
        return exception;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300 && !timedOut && exception == null;
    }

    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isRetryable() {
        return timedOut || isServerError();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceResponse that = (ServiceResponse) o;
        return statusCode == that.statusCode &&
                timedOut == that.timedOut &&
                Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, body, timedOut);
    }

    @Override
    public String toString() {
        return "ServiceResponse{" +
                "statusCode=" + statusCode +
                ", timedOut=" + timedOut +
                ", hasBody=" + (body != null) +
                ", hasException=" + (exception != null) +
                '}';
    }
}
