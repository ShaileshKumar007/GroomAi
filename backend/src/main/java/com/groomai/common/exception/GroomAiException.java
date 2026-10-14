package com.groomai.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base domain runtime exception for Groom.ai.
 * All domain-specific exceptions extend this class.
 */
public class GroomAiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public GroomAiException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public GroomAiException(String message, Throwable cause, HttpStatus httpStatus, String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
