package com.groomai.storage.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an upload intent ticket is missing, malformed, tampered with,
 * issued for a different user/key, or has an invalid purpose.
 * Maps to error code "INVALID_UPLOAD_TICKET" (HTTP 403 Forbidden).
 */
public class InvalidUploadTicketException extends GroomAiException {

    public InvalidUploadTicketException(String message) {
        super(message, HttpStatus.FORBIDDEN, "INVALID_UPLOAD_TICKET");
    }
}
