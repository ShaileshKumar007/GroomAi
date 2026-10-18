package com.groomai.storage.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Base exception for storage operations.
 */
public class StorageException extends GroomAiException {

    public StorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR");
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR");
    }

    public StorageException(String message, HttpStatus httpStatus, String errorCode) {
        super(message, httpStatus, errorCode);
    }

    public StorageException(String message, Throwable cause, HttpStatus httpStatus, String errorCode) {
        super(message, cause, httpStatus, errorCode);
    }
}
