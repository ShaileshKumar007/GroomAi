package com.groomai.storage.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an upload intent ticket has expired (> 15 minutes since issuance).
 * Maps to error code "EXPIRED_UPLOAD_TICKET" (HTTP 403 Forbidden).
 */
public class ExpiredUploadTicketException extends GroomAiException {

    public ExpiredUploadTicketException(String message) {
        super(message, HttpStatus.FORBIDDEN, "EXPIRED_UPLOAD_TICKET");
    }
}
