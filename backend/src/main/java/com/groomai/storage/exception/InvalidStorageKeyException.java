package com.groomai.storage.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a storage key fails safety checks, contains path traversal sequences,
 * or has an invalid format.
 */
public class InvalidStorageKeyException extends GroomAiException {

    public InvalidStorageKeyException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY");
    }
}
