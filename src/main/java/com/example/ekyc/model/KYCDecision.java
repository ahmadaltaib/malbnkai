package com.example.ekyc.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Final KYC decision containing the overall decision and all verification results.
 */
public class KYCDecision {
    private final Decision decision;
    private final List<VerificationResult> verificationResults;
    private final LocalDateTime timestamp;
    private final String correlationId;

    public KYCDecision(Decision decision, List<VerificationResult> verificationResults,
                       LocalDateTime timestamp, String correlationId) {
        this.decision = decision;
        this.verificationResults = verificationResults != null 
                ? Collections.unmodifiableList(verificationResults) 
                : Collections.emptyList();
        this.timestamp = timestamp;
        this.correlationId = correlationId;
    }

    public Decision getDecision() {
        return decision;
    }

    public List<VerificationResult> getVerificationResults() {
        return verificationResults;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KYCDecision that = (KYCDecision) o;
        return decision == that.decision &&
                Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, correlationId);
    }

    @Override
    public String toString() {
        return "KYCDecision{" +
                "decision=" + decision +
                ", verificationResults=" + verificationResults +
                ", timestamp=" + timestamp +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Decision decision;
        private List<VerificationResult> verificationResults;
        private LocalDateTime timestamp;
        private String correlationId;

        public Builder decision(Decision decision) {
            this.decision = decision;
            return this;
        }

        public Builder verificationResults(List<VerificationResult> verificationResults) {
            this.verificationResults = verificationResults;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public KYCDecision build() {
            return new KYCDecision(decision, verificationResults, 
                    timestamp != null ? timestamp : LocalDateTime.now(), correlationId);
        }
    }
}
