package com.groomai.user.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation requires user consent that has not been granted.
 * Maps to error code "CONSENT_REQUIRED" matching docs/API_SPEC.md.
 */
public class ConsentRequiredException extends GroomAiException {

    public ConsentRequiredException(String message) {
        super(message, HttpStatus.FORBIDDEN, "CONSENT_REQUIRED");
    }
}
