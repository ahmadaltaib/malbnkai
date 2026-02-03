package com.example.ekyc.service;

import com.example.ekyc.client.HttpClient;
import com.example.ekyc.client.ServiceResponse;
import com.example.ekyc.config.ServiceConfig;
import com.example.ekyc.model.Customer;
import com.example.ekyc.model.VerificationResult;
import com.example.ekyc.model.VerificationStatus;
import com.example.ekyc.model.VerificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for individual service clients with mocked HTTP responses.
 */
@ExtendWith(MockitoExtension.class)
class ServiceClientTest {
    
    @Mock
    private HttpClient httpClient;
    
    // Use ServiceConfig singleton for tests (uses default values)
    private final ServiceConfig config = ServiceConfig.getInstance();

    private Customer createValidCustomer() {
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
                .documentExpiryDate(LocalDate.now().plusYears(2).toString()) // Valid future date
                .documentImageUrl("https://example.com/docs/passport.jpg")
                .selfieUrl("https://example.com/selfie.jpg")
                .idPhotoUrl("https://example.com/id_photo.jpg")
                .proofType("UTILITY_BILL")
                .proofDate(LocalDate.now().minusDays(30).toString()) // Within 90 days
                .proofUrl("https://example.com/proof.pdf")
                .build();
    }

    @Nested
    @DisplayName("DocumentVerificationClient Tests")
    class DocumentVerificationClientTests {
        
        private DocumentVerificationClient client;
        
        @BeforeEach
        void setUp() {
            client = new DocumentVerificationClient(httpClient, config);
        }

        @Test
        @DisplayName("Valid document with high confidence → PASS")
        void testValidDocument_HighConfidence_ShouldPass() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.success(200, 
                            "{\"status\": \"PASS\", \"confidence\": 95, \"reasons\": []}"));
            
            Customer customer = createValidCustomer();
            
            // When
            VerificationResult result = client.verifyDocument(customer);
            
            // Then
            assertEquals(VerificationType.ID_DOCUMENT, result.getVerificationType());
            assertEquals(VerificationStatus.PASS, result.getStatus());
            assertEquals(95, result.getConfidence());
        }

        @Test
        @DisplayName("Expired document → FAIL (before calling service)")
        void testExpiredDocument_ShouldFailImmediately() {
            // Given: Document expired last year
            Customer customer = Customer.builder()
                    .customerId("CUST-001")
                    .fullName("John Doe")
                    .documentType("PASSPORT")
                    .documentNumber("AB1234567")
                    .documentExpiryDate(LocalDate.now().minusYears(1).toString())
                    .documentImageUrl("https://example.com/docs/passport.jpg")
                    .build();
            
            // When
            VerificationResult result = client.verifyDocument(customer);
            
            // Then
            assertEquals(VerificationStatus.FAIL, result.getStatus());
            assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("expired")));
            
            // Service should not be called
            verify(httpClient, never()).post(anyString(), any(), anyInt());
        }

        @Test
        @DisplayName("Low confidence score → MANUAL_REVIEW")
        void testLowConfidence_ShouldManualReview() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.success(200, 
                            "{\"status\": \"PASS\", \"confidence\": 70, \"reasons\": []}"));
            
            // When
            VerificationResult result = client.verifyDocument(createValidCustomer());
            
            // Then
            assertEquals(VerificationStatus.MANUAL_REVIEW, result.getStatus());
            assertEquals(70, result.getConfidence());
        }
    }

    @Nested
    @DisplayName("BiometricVerificationClient Tests")
    class BiometricVerificationClientTests {
        
        private BiometricVerificationClient client;
        
        @BeforeEach
        void setUp() {
            client = new BiometricVerificationClient(httpClient, config);
        }

        @Test
        @DisplayName("High confidence and similarity → PASS")
        void testHighScores_ShouldPass() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.success(200, 
                            "{\"status\": \"PASS\", \"confidence\": 92, \"similarity_score\": 90}"));
            
            // When
            VerificationResult result = client.verifyFaceMatch(createValidCustomer());
            
            // Then
            assertEquals(VerificationType.FACE_MATCH, result.getVerificationType());
            assertEquals(VerificationStatus.PASS, result.getStatus());
        }

        @Test
        @DisplayName("Low similarity score → MANUAL_REVIEW")
        void testLowSimilarity_ShouldManualReview() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.success(200, 
                            "{\"status\": \"PASS\", \"confidence\": 92, \"similarity_score\": 70}"));
            
            // When
            VerificationResult result = client.verifyFaceMatch(createValidCustomer());
            
            // Then
            assertEquals(VerificationStatus.MANUAL_REVIEW, result.getStatus());
            assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("similarity")));
        }
    }

    @Nested
    @DisplayName("AddressVerificationClient Tests")
    class AddressVerificationClientTests {
        
        private AddressVerificationClient client;
        
        @BeforeEach
        void setUp() {
            client = new AddressVerificationClient(httpClient, config);
        }

        @Test
        @DisplayName("Valid proof within 90 days → PASS")
        void testValidProof_ShouldPass() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.success(200, 
                            "{\"status\": \"PASS\", \"confidence\": 85, \"reasons\": []}"));
            
            // When
            VerificationResult result = client.verifyAddress(createValidCustomer());
            
            // Then
            assertEquals(VerificationType.ADDRESS, result.getVerificationType());
            assertEquals(VerificationStatus.PASS, result.getStatus());
        }

        @Test
        @DisplayName("Proof older than 90 days → FAIL")
        void testOldProof_ShouldFail() {
            // Given: Proof date is 100 days ago
            Customer customer = Customer.builder()
                    .customerId("CUST-001")
                    .address("123 Main Street")
                    .proofType("UTILITY_BILL")
                    .proofDate(LocalDate.now().minusDays(100).toString())
                    .proofUrl("https://example.com/proof.pdf")
                    .build();
            
            // When
            VerificationResult result = client.verifyAddress(customer);
            
            // Then
            assertEquals(VerificationStatus.FAIL, result.getStatus());
            assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("90 days")));
            
            // Service should not be called
            verify(httpClient, never()).post(anyString(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("SanctionsScreeningClient Tests")
    class SanctionsScreeningClientTests {
        
        private SanctionsScreeningClient client;
        
        @BeforeEach
        void setUp() {
            client = new SanctionsScreeningClient(httpClient, config);
        }

        @Test
        @DisplayName("CLEAR status → PASS")
        void testClearStatus_ShouldPass() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.success(200, 
                            "{\"status\": \"CLEAR\", \"match_count\": 0, \"matches\": []}"));
            
            // When
            VerificationResult result = client.checkSanctions(createValidCustomer());
            
            // Then
            assertEquals(VerificationType.SANCTIONS, result.getVerificationType());
            assertEquals(VerificationStatus.PASS, result.getStatus());
            assertEquals(100, result.getConfidence());
        }

        @Test
        @DisplayName("HIT status → FAIL (critical)")
        void testHitStatus_ShouldFail() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.success(200, 
                            "{\"status\": \"HIT\", \"match_count\": 1, \"matches\": [\"OFAC SDN List\"]}"));
            
            // When
            VerificationResult result = client.checkSanctions(createValidCustomer());
            
            // Then
            assertEquals(VerificationStatus.FAIL, result.getStatus());
            assertEquals(0, result.getConfidence());
        }

        @Test
        @DisplayName("Service error → MANUAL_REVIEW (critical service)")
        void testServiceError_ShouldManualReview() {
            // Given
            when(httpClient.post(anyString(), any(), anyInt()))
                    .thenReturn(ServiceResponse.error(500, "{\"error\": \"Internal Server Error\"}"));
            
            // When
            VerificationResult result = client.checkSanctions(createValidCustomer());
            
            // Then
            assertEquals(VerificationStatus.MANUAL_REVIEW, result.getStatus());
            assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("CRITICAL")));
        }
    }
}
