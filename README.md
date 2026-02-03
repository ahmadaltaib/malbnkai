# eKYC Verification Service

A production-ready electronic Know Your Customer (eKYC) verification service built in plain Java. This service orchestrates identity verification across 4 external microservices for financial institutions to onboard customers while complying with KYC/AML regulations.

## Features

- **Document Verification** - Validates identity documents (passport, driver's license)
- **Biometric Verification** - Face matching between selfie and ID photo
- **Address Verification** - Validates proof of address documents
- **Sanctions Screening** - Checks against global sanctions lists (CRITICAL)
- **Retry Logic** - Exponential backoff for transient failures
- **Rate Limiting** - Configurable per-service rate limits
- **Structured Logging** - Correlation IDs for request tracing
- **Externalized Configuration** - Environment variable-based configuration

## Application Structure

```
src/main/java/com/example/ekyc/
├── Application.java                 # Main entry point
├── config/
│   └── ServiceConfig.java           # Centralized configuration (env vars)
├── model/
│   ├── Customer.java                # Customer data model
│   ├── Decision.java                # Final decision enum (APPROVED/REJECTED/MANUAL_REVIEW)
│   ├── KYCDecision.java             # Final decision with results
│   ├── VerificationRequest.java     # Verification request model
│   ├── VerificationResult.java      # Individual verification result
│   ├── VerificationStatus.java      # Status enum (PASS/FAIL/MANUAL_REVIEW)
│   └── VerificationType.java        # Type enum (ID_DOCUMENT/FACE_MATCH/ADDRESS/SANCTIONS)
├── client/
│   ├── HttpClient.java              # HTTP client interface
│   ├── SimpleHttpClient.java        # Mock HTTP client with rate limiting
│   ├── RetryableHttpClient.java     # Retry wrapper with exponential backoff
│   └── ServiceResponse.java         # HTTP response wrapper
├── service/
│   ├── DocumentVerificationClient.java   # Document verification service client
│   ├── BiometricVerificationClient.java  # Face match service client
│   ├── AddressVerificationClient.java    # Address verification service client
│   ├── SanctionsScreeningClient.java     # Sanctions screening service client
│   ├── KYCDecisionEngine.java            # Business rules engine
│   └── VerificationOrchestrator.java     # Orchestrates verification flow
├── exception/
│   ├── ServiceException.java        # Base service exception
│   ├── TimeoutException.java        # Timeout exception
│   ├── RateLimitException.java      # Rate limit exception
│   └── ValidationException.java     # Validation exception
└── util/
    ├── CorrelationIdGenerator.java  # Correlation ID for request tracing
    └── JsonUtils.java               # JSON serialization utilities

src/test/java/com/example/ekyc/
├── client/
│   ├── RetryableHttpClientTest.java # Retry logic tests
│   └── SimpleHttpClientTest.java    # Rate limiting tests
└── service/
    ├── KYCDecisionEngineTest.java       # Decision engine unit tests
    ├── ServiceClientTest.java           # Service client unit tests
    └── VerificationOrchestratorTest.java # Integration tests
```

## Prerequisites

- **Java 17** or higher
- **Gradle 8.x** (wrapper included)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd mal
```

### 2. Build the Project

```bash
./gradlew clean build
```

### 3. Run Tests

```bash
./gradlew test
```

View test report at: `build/reports/tests/test/index.html`

### 4. Run the Application

```bash
./gradlew run
```

## Configuration

All settings can be configured via environment variables. If not set, sensible defaults are used.

### Service URLs

| Variable | Default | Description |
|----------|---------|-------------|
| `EKYC_SERVICE_BASE_URL` | `http://localhost:8080` | Base URL for all services |
| `EKYC_DOCUMENT_ENDPOINT` | `/api/v1/verify-document` | Document verification endpoint |
| `EKYC_BIOMETRIC_ENDPOINT` | `/api/v1/face-match` | Biometric verification endpoint |
| `EKYC_ADDRESS_ENDPOINT` | `/api/v1/verify-address` | Address verification endpoint |
| `EKYC_SANCTIONS_ENDPOINT` | `/api/v1/check-sanctions` | Sanctions screening endpoint |

### Timeouts (seconds)

| Variable | Default | Description |
|----------|---------|-------------|
| `EKYC_DOCUMENT_TIMEOUT` | `5` | Document service timeout |
| `EKYC_BIOMETRIC_TIMEOUT` | `8` | Biometric service timeout |
| `EKYC_ADDRESS_TIMEOUT` | `5` | Address service timeout |
| `EKYC_SANCTIONS_TIMEOUT` | `3` | Sanctions service timeout |

### Confidence Thresholds (%)

| Variable | Default | Description |
|----------|---------|-------------|
| `EKYC_DOCUMENT_CONFIDENCE_THRESHOLD` | `85` | Document confidence threshold |
| `EKYC_BIOMETRIC_CONFIDENCE_THRESHOLD` | `85` | Biometric confidence threshold |
| `EKYC_BIOMETRIC_SIMILARITY_THRESHOLD` | `85` | Face similarity threshold |
| `EKYC_ADDRESS_CONFIDENCE_THRESHOLD` | `80` | Address confidence threshold |

### Business Rules

| Variable | Default | Description |
|----------|---------|-------------|
| `EKYC_ADDRESS_PROOF_VALIDITY_DAYS` | `90` | Max age of address proof (days) |

### Retry Settings

| Variable | Default | Description |
|----------|---------|-------------|
| `EKYC_MAX_RETRY_ATTEMPTS` | `3` | Maximum retry attempts |
| `EKYC_RETRY_BACKOFF_MS` | `1000,2000,4000` | Backoff delays (ms, comma-separated) |

### Rate Limiting

| Variable | Default | Description |
|----------|---------|-------------|
| `EKYC_RATE_LIMIT_REQUESTS` | `10` | Requests per window |
| `EKYC_RATE_LIMIT_WINDOW_SECONDS` | `60` | Rate limit window (seconds) |

### Example: Custom Configuration

```bash
export EKYC_SERVICE_BASE_URL=http://production-api:8080
export EKYC_DOCUMENT_CONFIDENCE_THRESHOLD=90
export EKYC_MAX_RETRY_ATTEMPTS=5
./gradlew run
```

## Business Rules

### Decision Logic

| Decision | Condition |
|----------|-----------|
| **APPROVED** | All verification checks PASS |
| **REJECTED** | Sanctions HIT, expired document, or critical failures |
| **MANUAL_REVIEW** | Low confidence scores or partial failures |

### Verification Thresholds

| Check | PASS Condition | FAIL Condition |
|-------|----------------|----------------|
| Document | Confidence > 85%, not expired | Expired document |
| Biometric | Confidence > 85%, Similarity > 85% | Face match failed |
| Address | Confidence > 80%, proof < 90 days old | Proof too old |
| Sanctions | Status = CLEAR | ANY match (critical) |

## API Reference

### External Services (Mocked)

| Service | Endpoint | Timeout | Rate Limit |
|---------|----------|---------|------------|
| Document Verification | `POST /api/v1/verify-document` | 5s | 10 req/min |
| Biometric Service | `POST /api/v1/face-match` | 8s | 10 req/min |
| Address Verification | `POST /api/v1/verify-address` | 5s | 10 req/min |
| Sanctions Screening | `POST /api/v1/check-sanctions` | 3s | 10 req/min |

## Development

### Running Tests with Report

```bash
./gradlew clean test
# Open build/reports/tests/test/index.html in browser
```

### Code Structure Guidelines

- **No frameworks** - Plain Java only (no Spring, Micronaut, Quarkus)
- **Allowed dependencies** - Gson, SLF4J/Logback, JUnit 5, Mockito
- **Logging** - Use SLF4J, never `System.out.println`
- **Exceptions** - Use custom exception hierarchy
- **Configuration** - All settings via `ServiceConfig`

## License

This project is for educational/evaluation purposes.
