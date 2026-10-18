package com.groomai.wardrobe.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an uploaded image fails validation (magic bytes, dimensions, or corruption).
 * Maps to error code "INVALID_IMAGE_PAYLOAD" matching docs/API_SPEC.md.
 */
public class InvalidImagePayloadException extends GroomAiException {

    public InvalidImagePayloadException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_IMAGE_PAYLOAD");
    }
}
