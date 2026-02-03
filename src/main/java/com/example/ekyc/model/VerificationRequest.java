package com.example.ekyc.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a verification request containing customer data and types of verification to perform.
 */
public class VerificationRequest {
    private final String requestId;
    private final String customerId;
    private final List<VerificationType> verificationTypes;
    private final LocalDateTime timestamp;

    public VerificationRequest(String requestId, String customerId, 
                               List<VerificationType> verificationTypes, LocalDateTime timestamp) {
        this.requestId = requestId;
        this.customerId = customerId;
        this.verificationTypes = verificationTypes != null 
                ? Collections.unmodifiableList(verificationTypes) 
                : Collections.emptyList();
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<VerificationType> getVerificationTypes() {
        return verificationTypes;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerificationRequest that = (VerificationRequest) o;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return "VerificationRequest{" +
                "requestId='" + requestId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", verificationTypes=" + verificationTypes +
                ", timestamp=" + timestamp +
                '}';
    }
}
