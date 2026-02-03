package com.example.ekyc.service;

import com.example.ekyc.model.Decision;
import com.example.ekyc.model.KYCDecision;
import com.example.ekyc.model.VerificationResult;
import com.example.ekyc.model.VerificationStatus;
import com.example.ekyc.model.VerificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KYCDecisionEngine.
 * Tests all decision paths as specified in requirements.
 */
class KYCDecisionEngineTest {
    
    private KYCDecisionEngine decisionEngine;
    private static final String CORRELATION_ID = "REQ-TEST001";

    @BeforeEach
    void setUp() {
        decisionEngine = new KYCDecisionEngine();
    }

    @Test
    @DisplayName("test_all_verifications_pass: All PASS → APPROVED")
    void testAllVerificationsPass_ShouldApprove() {
        // Given: All verifications pass with high confidence
        List<VerificationResult> results = List.of(
                createResult(VerificationType.ID_DOCUMENT, VerificationStatus.PASS, 95),
                createResult(VerificationType.FACE_MATCH, VerificationStatus.PASS, 92),
                createResult(VerificationType.ADDRESS, VerificationStatus.PASS, 88),
                createResult(VerificationType.SANCTIONS, VerificationStatus.PASS, 100)
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.APPROVED, decision.getDecision());
        assertEquals(4, decision.getVerificationResults().size());
        assertEquals(CORRELATION_ID, decision.getCorrelationId());
    }

    @Test
    @DisplayName("test_sanctions_hit: Sanctions HIT → REJECTED")
    void testSanctionsHit_ShouldReject() {
        // Given: Sanctions check fails (HIT on sanctions list)
        List<VerificationResult> results = List.of(
                createResult(VerificationType.ID_DOCUMENT, VerificationStatus.PASS, 95),
                createResult(VerificationType.FACE_MATCH, VerificationStatus.PASS, 92),
                createResult(VerificationType.ADDRESS, VerificationStatus.PASS, 88),
                createResultWithReasons(VerificationType.SANCTIONS, VerificationStatus.FAIL, 0, 
                        List.of("Match found on OFAC sanctions list"))
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.REJECTED, decision.getDecision());
    }

    @Test
    @DisplayName("test_low_confidence_scores: Low confidence → MANUAL_REVIEW")
    void testLowConfidenceScores_ShouldManualReview() {
        // Given: Face match has low confidence score (70%)
        List<VerificationResult> results = List.of(
                createResult(VerificationType.ID_DOCUMENT, VerificationStatus.PASS, 95),
                createResultWithReasons(VerificationType.FACE_MATCH, VerificationStatus.MANUAL_REVIEW, 70,
                        List.of("Low confidence score")),
                createResult(VerificationType.ADDRESS, VerificationStatus.PASS, 85),
                createResult(VerificationType.SANCTIONS, VerificationStatus.PASS, 100)
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.MANUAL_REVIEW, decision.getDecision());
    }

    @Test
    @DisplayName("test_expired_document: Expired doc → REJECTED")
    void testExpiredDocument_ShouldReject() {
        // Given: Document verification fails due to expiry
        List<VerificationResult> results = List.of(
                createResultWithReasons(VerificationType.ID_DOCUMENT, VerificationStatus.FAIL, 0,
                        List.of("Document has expired")),
                createResult(VerificationType.FACE_MATCH, VerificationStatus.PASS, 92),
                createResult(VerificationType.ADDRESS, VerificationStatus.PASS, 88),
                createResult(VerificationType.SANCTIONS, VerificationStatus.PASS, 100)
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.REJECTED, decision.getDecision());
    }

    @Test
    @DisplayName("Multiple MANUAL_REVIEW results → MANUAL_REVIEW")
    void testMultipleManualReview_ShouldManualReview() {
        // Given: Multiple verifications need manual review
        List<VerificationResult> results = List.of(
                createResult(VerificationType.ID_DOCUMENT, VerificationStatus.MANUAL_REVIEW, 75),
                createResult(VerificationType.FACE_MATCH, VerificationStatus.MANUAL_REVIEW, 70),
                createResult(VerificationType.ADDRESS, VerificationStatus.PASS, 85),
                createResult(VerificationType.SANCTIONS, VerificationStatus.PASS, 100)
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.MANUAL_REVIEW, decision.getDecision());
    }

    @Test
    @DisplayName("Empty results → MANUAL_REVIEW")
    void testEmptyResults_ShouldManualReview() {
        // When
        KYCDecision decision = decisionEngine.makeDecision(Collections.emptyList(), CORRELATION_ID);

        // Then
        assertEquals(Decision.MANUAL_REVIEW, decision.getDecision());
    }

    @Test
    @DisplayName("Null results → MANUAL_REVIEW")
    void testNullResults_ShouldManualReview() {
        // When
        KYCDecision decision = decisionEngine.makeDecision(null, CORRELATION_ID);

        // Then
        assertEquals(Decision.MANUAL_REVIEW, decision.getDecision());
    }

    @Test
    @DisplayName("Sanctions MANUAL_REVIEW (service unavailable) → MANUAL_REVIEW")
    void testSanctionsServiceUnavailable_ShouldManualReview() {
        // Given: Sanctions service unavailable, returned MANUAL_REVIEW
        List<VerificationResult> results = List.of(
                createResult(VerificationType.ID_DOCUMENT, VerificationStatus.PASS, 95),
                createResult(VerificationType.FACE_MATCH, VerificationStatus.PASS, 92),
                createResult(VerificationType.ADDRESS, VerificationStatus.PASS, 88),
                createResultWithReasons(VerificationType.SANCTIONS, VerificationStatus.MANUAL_REVIEW, 0,
                        List.of("Sanctions service unavailable"))
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.MANUAL_REVIEW, decision.getDecision());
    }

    @Test
    @DisplayName("Address verification FAIL → REJECTED")
    void testAddressVerificationFail_ShouldReject() {
        // Given: Address proof is too old
        List<VerificationResult> results = List.of(
                createResult(VerificationType.ID_DOCUMENT, VerificationStatus.PASS, 95),
                createResult(VerificationType.FACE_MATCH, VerificationStatus.PASS, 92),
                createResultWithReasons(VerificationType.ADDRESS, VerificationStatus.FAIL, 0,
                        List.of("Proof of address is older than 90 days")),
                createResult(VerificationType.SANCTIONS, VerificationStatus.PASS, 100)
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.REJECTED, decision.getDecision());
    }

    @Test
    @DisplayName("Only sanctions check passes → APPROVED (single check)")
    void testSingleSanctionsPass_ShouldApprove() {
        // Given: Only sanctions check performed and passes
        List<VerificationResult> results = List.of(
                createResult(VerificationType.SANCTIONS, VerificationStatus.PASS, 100)
        );

        // When
        KYCDecision decision = decisionEngine.makeDecision(results, CORRELATION_ID);

        // Then
        assertEquals(Decision.APPROVED, decision.getDecision());
    }

    // Helper methods

    private VerificationResult createResult(VerificationType type, VerificationStatus status, int confidence) {
        return VerificationResult.builder()
                .verificationType(type)
                .status(status)
                .confidence(confidence)
                .reasons(new ArrayList<>())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private VerificationResult createResultWithReasons(VerificationType type, VerificationStatus status, 
                                                        int confidence, List<String> reasons) {
        return VerificationResult.builder()
                .verificationType(type)
                .status(status)
                .confidence(confidence)
                .reasons(reasons)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
