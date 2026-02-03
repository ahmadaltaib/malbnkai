package com.example.ekyc.exception;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when input validation fails.
 */
public class ValidationException extends RuntimeException {
    private final List<String> validationErrors;

    public ValidationException(String message) {
        super(message);
        this.validationErrors = Collections.singletonList(message);
    }

    public ValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null 
                ? Collections.unmodifiableList(validationErrors) 
                : Collections.emptyList();
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
