# eKYC Verification Service - Requirements & Java Implementation Plan

## Executive Summary
Build an eKYC (electronic Know Your Customer) Verification Service that orchestrates verification requests across 4 external microservices for financial institutions to onboard customers while complying with regulatory requirements.

---

## REQUIREMENTS EXTRACTION

### 1. BUSINESS CONTEXT
- **Domain**: Digital identity verification for financial services
- **Target Users**: Financial institutions for customer onboarding
- **Regulatory Requirement**: Compliance with KYC/AML regulations
- **Architecture**: Microservices-based orchestration

### 2. EXTERNAL MICROSERVICES (4 Total)

#### Service 1: Document Verification Service
- **Endpoint**: `POST /api/v1/verify-document`
- **Timeout**: 5 seconds
- **Rate Limit**: 10 requests/minute
- **Request**: customer_id, document_type, document_number, expiry_date, document_image_url
- **Response**: status (PASS/FAIL/MANUAL_REVIEW), confidence (0-100), reasons []

#### Service 2: Biometric Service
- **Endpoint**: `POST /api/v1/face-match`
- **Timeout**: 8 seconds
- **Rate Limit**: 10 requests/minute
- **Request**: customer_id, selfie_url, id_photo_url
- **Response**: status (PASS/FAIL/MANUAL_REVIEW), confidence (0-100), similarity_score

#### Service 3: Address Verification Service
- **Endpoint**: `POST /api/v1/verify-address`
- **Timeout**: 5 seconds
- **Rate Limit**: 10 requests/minute
- **Request**: customer_id, address, proof_type, proof_date, proof_url
- **Response**: status (PASS/FAIL/MANUAL_REVIEW), confidence (0-100), reasons []

#### Service 4: Sanctions Screening Service (CRITICAL)
- **Endpoint**: `POST /api/v1/check-sanctions`
- **Timeout**: 3 seconds
- **Rate Limit**: 10 requests/minute
- **Request**: customer_id, full_name, date_of_birth, nationality
- **Response**: status (CLEAR/HIT), match_count, matches []
- **Note**: CRITICAL - must succeed for APPROVED decision

### 3. DATA MODELS

#### Customer
```java
{
  customer_id: String (e.g., "CUST-001")
  full_name: String
  date_of_birth: String (ISO 8601)
  email: String
  phone: String
  address: String
}
```

#### VerificationRequest
```java
{
  request_id: String (e.g., "REQ-12345")
  customer_id: String
  verification_types: List<String> (ID_DOCUMENT, FACE_MATCH, ADDRESS, SANCTIONS)
  timestamp: LocalDateTime (ISO 8601)
}
```

#### VerificationResult
```java
{
  verification_type: String (ID_DOCUMENT, FACE_MATCH, ADDRESS, SANCTIONS)
  status: enum (PASS, FAIL, MANUAL_REVIEW)
  confidence: int (0-100)
  reasons: List<String>
  timestamp: LocalDateTime
}
```

#### KYCDecision
```java
{
  decision: enum (APPROVED, REJECTED, MANUAL_REVIEW)
  verification_results: List<VerificationResult>
  timestamp: LocalDateTime
}
```

### 4. BUSINESS RULES

#### Document Verification Rules
- Document must NOT be expired
- Confidence score > 85% for PASS
- Expired or low confidence → REJECTED or MANUAL_REVIEW

#### Face Match Rules
- Confidence score > 85% for PASS
- Similarity score > 85% for PASS
- Low scores → MANUAL_REVIEW

#### Address Verification Rules
- Proof must be dated within last 90 days
- Confidence score > 80% for PASS

#### Sanctions Check Rules (CRITICAL)
- ANY match → REJECTED immediately
- MUST succeed (cannot proceed if service fails)

#### Final Decision Logic
- **APPROVED**: All checks PASS
- **REJECTED**: Sanctions hit OR expired document OR critical failures
- **MANUAL_REVIEW**: Low confidence scores OR partial failures

### 5. CONSTRAINTS & REQUIREMENTS

#### Technical Constraints
- No application frameworks (Spring Boot, Micronaut, Quarkus) - plain Java only
- Each service: rate limit of 10 requests/minute
- Services may timeout or fail (must handle gracefully)
- Services may return partial failures requiring manual review

#### Production-Readiness Requirements
1. **Observability**: Operational visibility via logging
2. **Error Handling**: Graceful failure with meaningful error messages
3. **Validation**: Input and business rule verification
4. **Clean Code**: Readable, maintainable, well-organized
5. **Quality over Speed**: Better to complete less but do it well

---

## MILESTONE BREAKDOWN

### MILESTONE 1: Core Functionality (40-45 minutes)
**Target**: Average candidates should complete this

#### Requirements
1.1 **Data Models** (5-10 min)
   - Customer class
   - VerificationRequest class
   - VerificationResult class
   - Decision enum (APPROVED, REJECTED, MANUAL_REVIEW)
   - Status enum (PASS, FAIL, MANUAL_REVIEW)

1.2 **HTTP Client for Services** (10-15 min)
   - POST request capability
   - 5-second timeout (default)
   - JSON response parsing
   - Basic error handling (timeout, 500 errors)
   - Mock services (NO real HTTP calls)

1.3 **Service Clients** (15-20 min)
   - DocumentVerificationClient
   - BiometricVerificationClient
   - AddressVerificationClient
   - SanctionsScreeningClient
   - Each returns VerificationResult

1.4 **Decision Engine** (10-15 min)
   - Input: List<VerificationResult>
   - Output: KYCDecision
   - Apply all business rules
   - Handle edge cases

1.5 **Orchestrator** (10-15 min)
   - Input: Customer + verification types
   - Call appropriate service clients
   - Aggregate results
   - Call decision engine
   - Return final decision

1.6 **Basic Testing** (5 min)
   - Unit tests for decision engine (different scenarios)
   - End-to-end happy path test
   - Mocked service clients

#### Success Criteria
- ✅ All data models defined
- ✅ HTTP client can make requests (mocked)
- ✅ All 4 service clients implemented
- ✅ Decision engine applies business rules correctly
- ✅ Orchestrator works end-to-end
- ✅ Basic tests pass (happy path + decision logic)
- ✅ Code runs without errors

#### Test Cases for Milestone 1
- `test_all_verifications_pass`: All services PASS → APPROVED
- `test_sanctions_hit`: Sanctions HIT → REJECTED
- `test_low_confidence_scores`: Face match 70% → MANUAL_REVIEW
- `test_expired_document`: Document expired → REJECTED

---

### MILESTONE 2: Production-Ready Essentials (15-20 minutes)
**Target**: Good candidates complete M1 + M2 partially

#### MUST HAVE (3 requirements)

2.1 **Logging & Observability** (5-10 min)
   - Structured logging framework (not System.out.println)
   - Correlation IDs to track requests across services
   - Log levels: INFO, WARNING, ERROR
   - Never log sensitive data (PII, passwords)

2.2 **Retry Logic with Exponential Backoff** (10-15 min)
   - Retry on timeout and 5xx errors (NOT 4xx)
   - Max 3 retry attempts
   - Exponential backoff: 1s, 2s, 4s
   - Log each retry attempt

2.3 **Error Handling** (5-10 min)
   - Custom exception hierarchy
   - ServiceException, RateLimitException, ValidationException, etc.
   - Comprehensive try-catch blocks
   - Meaningful error messages

#### NICE TO HAVE (only if time)

2.4 **Configuration Management**
   - Externalize configuration (no hardcoded values)
   - Load from environment variables or config files
   - Service URLs, timeouts configurable

2.5 **Input Validation**
   - Validate customer data before processing
   - Check required fields
   - Validate email format, phone format
   - Return clear validation errors

#### Success Criteria for Milestone 2
**Must Complete**:
- ✅ Structured logging with correlation IDs
- ✅ Retry logic with exponential backoff works
- ✅ Custom exceptions defined and used
- ✅ Unit tests for retry logic and error scenarios

**Nice to Have**:
- Configuration externalized
- Input validation implemented

---

### MILESTONE 3: Advanced Features (Bonus - only if time permits)
**Target**: Only expert candidates

**Pick ONE** (20-30 min each):

**Option A: Rate Limiter**
- Track requests per service with timestamps
- Block or queue requests exceeding limit
- Reset counter after 60 seconds (sliding window)
- Track rate limits independently per service

**Option B: Parallel Service Calls**
- Call non-dependent services simultaneously
- All 4 services can be called in parallel
- Aggregate results when all complete
- Handle partial failures

**Option C: Metrics Collection**
- Total requests per service
- Success/failure rates
- Average response time
- Timeout count

**Option D: Circuit Breaker**
- Track consecutive failures per service
- Open circuit after 5 consecutive failures
- Half-open state after timeout
- Close circuit on successful calls

---

## JAVA IMPLEMENTATION PLAN

### PROJECT STRUCTURE
```
ekyc-service/
├── src/main/java/com/ekyc/
│   ├── model/
│   │   ├── Customer.java
│   │   ├── VerificationRequest.java
│   │   ├── VerificationResult.java
│   │   ├── KYCDecision.java
│   │   ├── Decision.java (enum)
│   │   └── VerificationStatus.java (enum)
│   ├── client/
│   │   ├── HttpClient.java (interface)
│   │   ├── SimpleHttpClient.java (implementation with mocking)
│   │   └── ServiceResponse.java
│   ├── service/
│   │   ├── DocumentVerificationClient.java
│   │   ├── BiometricVerificationClient.java
│   │   ├── AddressVerificationClient.java
│   │   ├── SanctionsScreeningClient.java
│   │   ├── KYCDecisionEngine.java
│   │   └── VerificationOrchestrator.java
│   ├── exception/
│   │   ├── ServiceException.java
│   │   ├── RateLimitException.java
│   │   ├── ValidationException.java
│   │   └── TimeoutException.java
│   ├── config/
│   │   └── ServiceConfig.java
│   └── util/
│       ├── Logger.java (or use SLF4J)
│       ├── CorrelationIdGenerator.java
│       └── Validator.java
├── src/test/java/com/ekyc/
│   ├── service/
│   │   ├── KYCDecisionEngineTest.java
│   │   ├── VerificationOrchestratorTest.java
│   │   └── ServiceClientTests.java
│   ├── mock/
│   │   ├── MockDocumentVerificationClient.java
│   │   ├── MockBiometricClient.java
│   │   ├── MockAddressClient.java
│   │   └── MockSanctionsClient.java
│   └── integration/
│       └── EndToEndTest.java
├── pom.xml (or build.gradle)
└── README.md
```

### DEPENDENCIES (Maven pom.xml)

```xml
<!-- Core -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>

<!-- Logging (SLF4J + Logback) -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.5</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.7</version>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.2.1</version>
    <scope>test</scope>
</dependency>
```

### IMPLEMENTATION SEQUENCE

#### Phase 1: Data Models (5-10 min)
1. Create enums: `Decision.java`, `VerificationStatus.java`
2. Create model classes: `Customer.java`, `VerificationRequest.java`, `VerificationResult.java`, `KYCDecision.java`
3. Add JSON serialization/deserialization methods (using Gson)

#### Phase 2: HTTP Client & Mocking (10-15 min)
1. Create `HttpClient.java` interface
2. Implement `SimpleHttpClient.java` with mocking capability
3. Support timeout configuration (5s default)
4. Parse JSON responses
5. Handle basic errors (timeout, 500 errors)

#### Phase 3: Service Clients (15-20 min)
1. Create `DocumentVerificationClient.java`
   - Validate document expiry
   - Extract confidence score
2. Create `BiometricVerificationClient.java`
   - Extract confidence and similarity score
3. Create `AddressVerificationClient.java`
   - Validate proof date (within 90 days)
   - Extract confidence score
4. Create `SanctionsScreeningClient.java`
   - Check for hits (critical)
   - Handle failures gracefully

#### Phase 4: Decision Engine (10-15 min)
1. Create `KYCDecisionEngine.java`
2. Implement decision logic:
   - APPROVED: All checks PASS
   - REJECTED: Sanctions hit OR expired doc OR critical failures
   - MANUAL_REVIEW: Low confidence OR partial failures
3. Apply all business rules

#### Phase 5: Orchestrator (10-15 min)
1. Create `VerificationOrchestrator.java`
2. Accept Customer + verification types
3. Call appropriate service clients in sequence
4. Aggregate results
5. Call decision engine
6. Return KYCDecision

#### Phase 6: Testing (5 min)
1. Create mock clients for testing
2. Write decision engine tests
3. Write end-to-end happy path test
4. Ensure all tests pass

#### Phase 7: Logging & Error Handling (5-10 min) - MILESTONE 2
1. Add SLF4J logging
2. Generate correlation IDs
3. Log at appropriate levels
4. Create custom exceptions
5. Add retry logic (if time permits)

---

## CODE SKELETON EXAMPLES

### 1. Data Models

```java
// Decision.java
public enum Decision {
    APPROVED,
    REJECTED,
    MANUAL_REVIEW
}

// VerificationStatus.java
public enum VerificationStatus {
    PASS,
    FAIL,
    MANUAL_REVIEW
}

// Customer.java
public class Customer {
    private String customerId;
    private String fullName;
    private String dateOfBirth; // ISO 8601
    private String email;
    private String phone;
    private String address;
    
    // Constructor, getters, setters
}

// VerificationResult.java
public class VerificationResult {
    private String verificationType; // ID_DOCUMENT, FACE_MATCH, ADDRESS, SANCTIONS
    private VerificationStatus status;
    private int confidence; // 0-100
    private List<String> reasons;
    private LocalDateTime timestamp;
    
    // Constructor, getters, setters
}

// KYCDecision.java
public class KYCDecision {
    private Decision decision;
    private List<VerificationResult> verificationResults;
    private LocalDateTime timestamp;
    
    // Constructor, getters, setters
}
```

### 2. HTTP Client Interface

```java
// HttpClient.java (interface)
public interface HttpClient {
    ServiceResponse post(String url, Object body, int timeoutSeconds) throws Exception;
}

// ServiceResponse.java
public class ServiceResponse {
    private int statusCode;
    private String body;
    private boolean timedOut;
    private Exception exception;
    
    // Getters
}
```

### 3. Service Client Example

```java
// DocumentVerificationClient.java
public class DocumentVerificationClient {
    private HttpClient httpClient;
    private String baseUrl;
    private static final Logger logger = LoggerFactory.getLogger(DocumentVerificationClient.class);
    
    public VerificationResult verifyDocument(Customer customer, String documentType, 
                                             String documentNumber, String expiryDate,
                                             String documentImageUrl) {
        try {
            // Build request
            Map<String, Object> request = new HashMap<>();
            request.put("customer_id", customer.getCustomerId());
            request.put("document_type", documentType);
            request.put("document_number", documentNumber);
            request.put("expiry_date", expiryDate);
            request.put("document_image_url", documentImageUrl);
            
            // Call service
            ServiceResponse response = httpClient.post(
                baseUrl + "/api/v1/verify-document",
                request,
                5 // 5 second timeout
            );
            
            // Parse response
            // Check expiry date
            // Extract confidence
            // Return VerificationResult
            
        } catch (Exception e) {
            logger.error("Document verification failed", e);
            return new VerificationResult(
                "ID_DOCUMENT",
                VerificationStatus.MANUAL_REVIEW,
                0,
                Arrays.asList("Service failed: " + e.getMessage()),
                LocalDateTime.now()
            );
        }
    }
}
```

### 4. Decision Engine

```java
// KYCDecisionEngine.java
public class KYCDecisionEngine {
    private static final Logger logger = LoggerFactory.getLogger(KYCDecisionEngine.class);
    
    public KYCDecision makeDecision(List<VerificationResult> results) {
        // Check for sanctions hit (critical - immediate rejection)
        for (VerificationResult result : results) {
            if ("SANCTIONS".equals(result.getVerificationType()) && 
                VerificationStatus.FAIL.equals(result.getStatus())) {
                logger.warn("Sanctions check failed - REJECTING");
                return new KYCDecision(
                    Decision.REJECTED,
                    results,
                    LocalDateTime.now()
                );
            }
        }
        
        // Apply business rules
        boolean allPass = true;
        boolean hasLowConfidence = false;
        
        for (VerificationResult result : results) {
            if (!VerificationStatus.PASS.equals(result.getStatus())) {
                allPass = false;
                if (VerificationStatus.MANUAL_REVIEW.equals(result.getStatus())) {
                    hasLowConfidence = true;
                }
            }
        }
        
        Decision decision;
        if (allPass) {
            decision = Decision.APPROVED;
            logger.info("All verifications passed - APPROVED");
        } else if (hasLowConfidence) {
            decision = Decision.MANUAL_REVIEW;
            logger.info("Some checks inconclusive - MANUAL_REVIEW");
        } else {
            decision = Decision.REJECTED;
            logger.warn("Some checks failed - REJECTED");
        }
        
        return new KYCDecision(decision, results, LocalDateTime.now());
    }
}
```

### 5. Orchestrator

```java
// VerificationOrchestrator.java
public class VerificationOrchestrator {
    private DocumentVerificationClient docClient;
    private BiometricVerificationClient bioClient;
    private AddressVerificationClient addrClient;
    private SanctionsScreeningClient sanctionsClient;
    private KYCDecisionEngine decisionEngine;
    private static final Logger logger = LoggerFactory.getLogger(VerificationOrchestrator.class);
    
    public KYCDecision processVerification(Customer customer, List<String> verificationType) {
        List<VerificationResult> results = new ArrayList<>();
        String correlationId = generateCorrelationId();
        
        logger.info("Starting verification for customer: {} with correlation ID: {}",
                   customer.getCustomerId(), correlationId);
        
        // Call each service client based on verification types
        for (String type : verificationType) {
            VerificationResult result = null;
            
            switch (type) {
                case "ID_DOCUMENT":
                    result = docClient.verifyDocument(customer, ...);
                    break;
                case "FACE_MATCH":
                    result = bioClient.faceMatch(customer, ...);
                    break;
                case "ADDRESS":
                    result = addrClient.verifyAddress(customer, ...);
                    break;
                case "SANCTIONS":
                    result = sanctionsClient.checkSanctions(customer);
                    break;
            }
            
            if (result != null) {
                results.add(result);
                logger.info("Verification {} completed with status: {}",
                           type, result.getStatus());
            }
        }
        
        // Make decision
        KYCDecision decision = decisionEngine.makeDecision(results);
        logger.info("Final decision: {} for customer: {}",
                   decision.getDecision(), customer.getCustomerId());
        
        return decision;
    }
    
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
```

### 6. Basic Test Example

```java
// KYCDecisionEngineTest.java
@RunWith(JUnit4.class)
public class KYCDecisionEngineTest {
    private KYCDecisionEngine decisionEngine;
    
    @Before
    public void setUp() {
        decisionEngine = new KYCDecisionEngine();
    }
    
    @Test
    public void testAllVerificationsPass_ShouldApprove() {
        List<VerificationResult> results = Arrays.asList(
            new VerificationResult("ID_DOCUMENT", VerificationStatus.PASS, 95, new ArrayList<>(), LocalDateTime.now()),
            new VerificationResult("FACE_MATCH", VerificationStatus.PASS, 92, new ArrayList<>(), LocalDateTime.now()),
            new VerificationResult("ADDRESS", VerificationStatus.PASS, 88, new ArrayList<>(), LocalDateTime.now()),
            new VerificationResult("SANCTIONS", VerificationStatus.PASS, 100, new ArrayList<>(), LocalDateTime.now())
        );
        
        KYCDecision decision = decisionEngine.makeDecision(results);
        
        assertEquals(Decision.APPROVED, decision.getDecision());
    }
    
    @Test
    public void testSanctionsHit_ShouldReject() {
        List<VerificationResult> results = Arrays.asList(
            new VerificationResult("SANCTIONS", VerificationStatus.FAIL, 0, 
                                  Arrays.asList("Match found on sanctions list"), LocalDateTime.now())
        );
        
        KYCDecision decision = decisionEngine.makeDecision(results);
        
        assertEquals(Decision.REJECTED, decision.getDecision());
    }
    
    @Test
    public void testLowConfidenceScores_ShouldManualReview() {
        List<VerificationResult> results = Arrays.asList(
            new VerificationResult("FACE_MATCH", VerificationStatus.MANUAL_REVIEW, 70, 
                                  Arrays.asList("Low confidence score"), LocalDateTime.now())
        );
        
        KYCDecision decision = decisionEngine.makeDecision(results);
        
        assertEquals(Decision.MANUAL_REVIEW, decision.getDecision());
    }
}
```

---

## TIME MANAGEMENT GUIDE

### Realistic 60-Minute Allocation

**Minutes 0-5: Planning**
- Read requirements carefully
- Decide on approach (TDD strategy)
- Plan class structure

**Minutes 5-50: Milestone 1 (40-45 min)**
- 5-10 min: Data models
- 10 min: Tests for decision engine (TDD!)
- 10 min: Decision engine implementation
- 10-15 min: HTTP client with mocking
- 15-20 min: 4 service clients
- 10-15 min: Orchestrator
- 5 min: Happy path test

**Minutes 50-60: Milestone 2 (10 min if fast)**
- 5 min: Add structured logging
- 5 min: Add retry logic basics
- OR continue polishing M1

**Minutes 60+: Milestone 3 (bonus)**
- Pick ONE feature if time permits

---

## BUILD & RUN INSTRUCTIONS

### Maven
```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package
mvn clean package

# Run main class (if applicable)
java -cp target/ekyc-service.jar com.ekyc.Main
```

### Gradle
```bash
# Compile
gradle clean build

# Run tests
gradle test

# Build
gradle build
```

---

## SUCCESS CRITERIA CHECKLIST

### Milestone 1 ✓
- [ ] All data models defined
- [ ] HTTP client can make requests (mocked)
- [ ] All 4 service clients implemented
- [ ] Decision engine applies business rules correctly
- [ ] Orchestrator works end-to-end
- [ ] Basic tests pass
- [ ] Code runs without errors

### Milestone 2 (MUST HAVE) ✓
- [ ] Structured logging with correlation IDs
- [ ] Retry logic with exponential backoff
- [ ] Custom exceptions defined and used

### Milestone 3 (BONUS) ✓
- [ ] One advanced feature implemented (pick ONE)

---

## KEY PRODUCTION-READY PRINCIPLES

1. **Observability**: Every significant operation logged with correlation ID
2. **Error Handling**: Graceful degradation, meaningful error messages
3. **Validation**: Input validation before processing
4. **Configuration**: Externalized, environment-aware
5. **Testing**: TDD approach, good test coverage
6. **Code Quality**: Clean, readable, maintainable

---

## FINAL NOTES

- **Quality > Quantity**: Better to complete M1 well than rush all 3 milestones
- **Most candidates complete M1 + partial M2**: That's considered GOOD performance
- **Focus on happy path first**: Then add error handling
- **Tests are crucial**: They prove your code works
- **Communication matters**: Your verbal explanation during recording is part of evaluation

Good luck! 🚀