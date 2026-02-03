package com.example.ekyc.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of a single verification check.
 */
public class VerificationResult {
    private final VerificationType verificationType;
    private final VerificationStatus status;
    private final int confidence; // 0-100
    private final List<String> reasons;
    private final LocalDateTime timestamp;

    public VerificationResult(VerificationType verificationType, VerificationStatus status,
                              int confidence, List<String> reasons, LocalDateTime timestamp) {
        this.verificationType = verificationType;
        this.status = status;
        this.confidence = confidence;
        this.reasons = reasons != null 
                ? Collections.unmodifiableList(reasons) 
                : Collections.emptyList();
        this.timestamp = timestamp;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public int getConfidence() {
        return confidence;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerificationResult that = (VerificationResult) o;
        return confidence == that.confidence &&
                verificationType == that.verificationType &&
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(verificationType, status, confidence);
    }

    @Override
    public String toString() {
        return "VerificationResult{" +
                "verificationType=" + verificationType +
                ", status=" + status +
                ", confidence=" + confidence +
                ", reasons=" + reasons +
                ", timestamp=" + timestamp +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private VerificationType verificationType;
        private VerificationStatus status;
        private int confidence;
        private List<String> reasons;
        private LocalDateTime timestamp;

        public Builder verificationType(VerificationType verificationType) {
            this.verificationType = verificationType;
            return this;
        }

        public Builder status(VerificationStatus status) {
            this.status = status;
            return this;
        }

        public Builder confidence(int confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reasons(List<String> reasons) {
            this.reasons = reasons;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public VerificationResult build() {
            return new VerificationResult(verificationType, status, confidence, reasons, 
                    timestamp != null ? timestamp : LocalDateTime.now());
        }
    }
}
