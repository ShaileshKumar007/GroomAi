package com.groomai.user.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when style preferences fail validation rules (duplicates, forbidden intersections, or bounds).
 */
public class StylePreferencesValidationException extends GroomAiException {

    public StylePreferencesValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }
}
