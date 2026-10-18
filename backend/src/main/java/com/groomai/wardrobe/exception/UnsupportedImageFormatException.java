package com.groomai.wardrobe.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an image upload format is not supported (only image/jpeg is accepted for MVP).
 * Maps to error code "UNSUPPORTED_IMAGE_FORMAT" matching docs/API_SPEC.md.
 */
public class UnsupportedImageFormatException extends GroomAiException {

    public UnsupportedImageFormatException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE_FORMAT");
    }
}
