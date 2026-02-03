package com.example.ekyc.service;

import com.example.ekyc.model.Decision;
import com.example.ekyc.model.KYCDecision;
import com.example.ekyc.model.VerificationResult;
import com.example.ekyc.model.VerificationStatus;
import com.example.ekyc.model.VerificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Decision engine that determines the final KYC decision based on verification results.
 * 
 * Decision Logic:
 * - APPROVED: All checks PASS
 * - REJECTED: Sanctions HIT OR expired document OR critical failures
 * - MANUAL_REVIEW: Low confidence scores OR partial failures
 */
public class KYCDecisionEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(KYCDecisionEngine.class);

    /**
     * Makes the final KYC decision based on all verification results.
     * 
     * @param results List of verification results from all checks
     * @param correlationId Correlation ID for logging
     * @return The final KYC decision
     */
    public KYCDecision makeDecision(List<VerificationResult> results, String correlationId) {
        int resultCount = results != null ? results.size() : 0;
        logger.info("Making KYC decision based on {} verification results", resultCount);
        
        if (results == null || results.isEmpty()) {
            logger.warn("No verification results provided - defaulting to MANUAL_REVIEW");
            return KYCDecision.builder()
                    .decision(Decision.MANUAL_REVIEW)
                    .verificationResults(results)
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();
        }
        
        // CRITICAL: Check for sanctions hit first (immediate rejection)
        if (hasSanctionsHit(results)) {
            logger.warn("SANCTIONS HIT detected - REJECTING");
            return KYCDecision.builder()
                    .decision(Decision.REJECTED)
                    .verificationResults(results)
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();
        }
        
        // Check for critical failures (FAIL status on any check)
        if (hasCriticalFailure(results)) {
            logger.warn("Critical failure detected - REJECTING");
            return KYCDecision.builder()
                    .decision(Decision.REJECTED)
                    .verificationResults(results)
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();
        }
        
        // Check if all verifications passed
        if (allVerificationsPassed(results)) {
            logger.info("All verifications passed - APPROVED");
            return KYCDecision.builder()
                    .decision(Decision.APPROVED)
                    .verificationResults(results)
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();
        }
        
        // Some checks require manual review (low confidence, partial failures)
        logger.info("Some checks inconclusive - MANUAL_REVIEW");
        return KYCDecision.builder()
                .decision(Decision.MANUAL_REVIEW)
                .verificationResults(results)
                .timestamp(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
    }

    /**
     * Checks if there's a sanctions hit in the results.
     * A sanctions FAIL is the most critical rejection reason.
     */
    private boolean hasSanctionsHit(List<VerificationResult> results) {
        return results.stream()
                .filter(r -> r.getVerificationType() == VerificationType.SANCTIONS)
                .anyMatch(r -> r.getStatus() == VerificationStatus.FAIL);
    }

    /**
     * Checks for critical failures (FAIL status) that should result in rejection.
     * This includes:
     * - Document verification failure (e.g., expired document)
     * - Any explicit FAIL status
     */
    private boolean hasCriticalFailure(List<VerificationResult> results) {
        return results.stream()
                .anyMatch(r -> r.getStatus() == VerificationStatus.FAIL);
    }

    /**
     * Checks if all verifications have PASS status.
     */
    private boolean allVerificationsPassed(List<VerificationResult> results) {
        return results.stream()
                .allMatch(r -> r.getStatus() == VerificationStatus.PASS);
    }
}
