package com.example.ekyc.service;

import com.example.ekyc.model.Customer;
import com.example.ekyc.model.Decision;
import com.example.ekyc.model.KYCDecision;
import com.example.ekyc.model.VerificationResult;
import com.example.ekyc.model.VerificationStatus;
import com.example.ekyc.model.VerificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for VerificationOrchestrator.
 * Tests the full verification flow with mocked service clients.
 */
@ExtendWith(MockitoExtension.class)
class VerificationOrchestratorTest {
    
    @Mock
    private DocumentVerificationClient documentClient;
    
    @Mock
    private BiometricVerificationClient biometricClient;
    
    @Mock
    private AddressVerificationClient addressClient;
    
    @Mock
    private SanctionsScreeningClient sanctionsClient;
    
    private VerificationOrchestrator orchestrator;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        KYCDecisionEngine decisionEngine = new KYCDecisionEngine();
        orchestrator = new VerificationOrchestrator(
                documentClient, biometricClient, addressClient, sanctionsClient, decisionEngine);
        
        testCustomer = createTestCustomer();
    }

    @Test
    @DisplayName("Full verification with all PASS → APPROVED")
    void testFullVerification_AllPass_ShouldApprove() {
        // Given: All service clients return PASS
        when(documentClient.verifyDocument(any())).thenReturn(
                createPassResult(VerificationType.ID_DOCUMENT, 95));
        when(biometricClient.verifyFaceMatch(any())).thenReturn(
                createPassResult(VerificationType.FACE_MATCH, 92));
        when(addressClient.verifyAddress(any())).thenReturn(
                createPassResult(VerificationType.ADDRESS, 88));
        when(sanctionsClient.checkSanctions(any())).thenReturn(
                createPassResult(VerificationType.SANCTIONS, 100));

        // When
        KYCDecision decision = orchestrator.processFullVerification(testCustomer);

        // Then
        assertEquals(Decision.APPROVED, decision.getDecision());
        assertEquals(4, decision.getVerificationResults().size());
        assertNotNull(decision.getCorrelationId());
        
        // Verify all clients were called
        verify(documentClient).verifyDocument(testCustomer);
        verify(biometricClient).verifyFaceMatch(testCustomer);
        verify(addressClient).verifyAddress(testCustomer);
        verify(sanctionsClient).checkSanctions(testCustomer);
    }

    @Test
    @DisplayName("Sanctions HIT → REJECTED")
    void testSanctionsHit_ShouldReject() {
        // Given: Sanctions check fails
        when(documentClient.verifyDocument(any())).thenReturn(
                createPassResult(VerificationType.ID_DOCUMENT, 95));
        when(biometricClient.verifyFaceMatch(any())).thenReturn(
                createPassResult(VerificationType.FACE_MATCH, 92));
        when(addressClient.verifyAddress(any())).thenReturn(
                createPassResult(VerificationType.ADDRESS, 88));
        when(sanctionsClient.checkSanctions(any())).thenReturn(
                createFailResult(VerificationType.SANCTIONS, "Match found on sanctions list"));

        // When
        KYCDecision decision = orchestrator.processFullVerification(testCustomer);

        // Then
        assertEquals(Decision.REJECTED, decision.getDecision());
    }

    @Test
    @DisplayName("Expired document → REJECTED")
    void testExpiredDocument_ShouldReject() {
        // Given: Document is expired
        when(documentClient.verifyDocument(any())).thenReturn(
                createFailResult(VerificationType.ID_DOCUMENT, "Document has expired"));
        when(biometricClient.verifyFaceMatch(any())).thenReturn(
                createPassResult(VerificationType.FACE_MATCH, 92));
        when(addressClient.verifyAddress(any())).thenReturn(
                createPassResult(VerificationType.ADDRESS, 88));
        when(sanctionsClient.checkSanctions(any())).thenReturn(
                createPassResult(VerificationType.SANCTIONS, 100));

        // When
        KYCDecision decision = orchestrator.processFullVerification(testCustomer);

        // Then
        assertEquals(Decision.REJECTED, decision.getDecision());
    }

    @Test
    @DisplayName("Low face match confidence → MANUAL_REVIEW")
    void testLowFaceMatchConfidence_ShouldManualReview() {
        // Given: Face match has low confidence
        when(documentClient.verifyDocument(any())).thenReturn(
                createPassResult(VerificationType.ID_DOCUMENT, 95));
        when(biometricClient.verifyFaceMatch(any())).thenReturn(
                createManualReviewResult(VerificationType.FACE_MATCH, 70, "Low confidence score"));
        when(addressClient.verifyAddress(any())).thenReturn(
                createPassResult(VerificationType.ADDRESS, 88));
        when(sanctionsClient.checkSanctions(any())).thenReturn(
                createPassResult(VerificationType.SANCTIONS, 100));

        // When
        KYCDecision decision = orchestrator.processFullVerification(testCustomer);

        // Then
        assertEquals(Decision.MANUAL_REVIEW, decision.getDecision());
    }

    @Test
    @DisplayName("Partial verification - only ID and Sanctions → APPROVED")
    void testPartialVerification_ShouldApprove() {
        // Given: Only ID_DOCUMENT and SANCTIONS are requested
        when(documentClient.verifyDocument(any())).thenReturn(
                createPassResult(VerificationType.ID_DOCUMENT, 95));
        when(sanctionsClient.checkSanctions(any())).thenReturn(
                createPassResult(VerificationType.SANCTIONS, 100));

        // When
        KYCDecision decision = orchestrator.processVerification(testCustomer, 
                List.of(VerificationType.ID_DOCUMENT, VerificationType.SANCTIONS));

        // Then
        assertEquals(Decision.APPROVED, decision.getDecision());
        assertEquals(2, decision.getVerificationResults().size());
        
        // Verify only requested clients were called
        verify(documentClient).verifyDocument(testCustomer);
        verify(sanctionsClient).checkSanctions(testCustomer);
        verify(biometricClient, never()).verifyFaceMatch(any());
        verify(addressClient, never()).verifyAddress(any());
    }

    @Test
    @DisplayName("Address proof too old → REJECTED")
    void testAddressProofTooOld_ShouldReject() {
        // Given: Address proof is older than 90 days
        when(documentClient.verifyDocument(any())).thenReturn(
                createPassResult(VerificationType.ID_DOCUMENT, 95));
        when(biometricClient.verifyFaceMatch(any())).thenReturn(
                createPassResult(VerificationType.FACE_MATCH, 92));
        when(addressClient.verifyAddress(any())).thenReturn(
                createFailResult(VerificationType.ADDRESS, "Proof of address is older than 90 days"));
        when(sanctionsClient.checkSanctions(any())).thenReturn(
                createPassResult(VerificationType.SANCTIONS, 100));

        // When
        KYCDecision decision = orchestrator.processFullVerification(testCustomer);

        // Then
        assertEquals(Decision.REJECTED, decision.getDecision());
    }

    // Helper methods

    private Customer createTestCustomer() {
        return Customer.builder()
                .customerId("CUST-001")
                .fullName("John Doe")
                .dateOfBirth("1990-05-15")
                .email("john.doe@example.com")
                .phone("+1-555-123-4567")
                .address("123 Main Street, New York, NY 10001")
                .nationality("US")
                .documentType("PASSPORT")
                .documentNumber("AB1234567")
                .documentExpiryDate("2028-05-15")
                .documentImageUrl("https://example.com/docs/passport.jpg")
                .selfieUrl("https://example.com/selfie.jpg")
                .idPhotoUrl("https://example.com/id_photo.jpg")
                .proofType("UTILITY_BILL")
                .proofDate("2026-01-15")
                .proofUrl("https://example.com/proof.pdf")
                .build();
    }

    private VerificationResult createPassResult(VerificationType type, int confidence) {
        return VerificationResult.builder()
                .verificationType(type)
                .status(VerificationStatus.PASS)
                .confidence(confidence)
                .reasons(new ArrayList<>())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private VerificationResult createFailResult(VerificationType type, String reason) {
        return VerificationResult.builder()
                .verificationType(type)
                .status(VerificationStatus.FAIL)
                .confidence(0)
                .reasons(List.of(reason))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private VerificationResult createManualReviewResult(VerificationType type, int confidence, String reason) {
        return VerificationResult.builder()
                .verificationType(type)
                .status(VerificationStatus.MANUAL_REVIEW)
                .confidence(confidence)
                .reasons(List.of(reason))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
