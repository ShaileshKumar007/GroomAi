package com.groomai.storage.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an object referenced by storage key is not found in storage.
 * Maps to error code "STORAGE_OBJECT_NOT_FOUND" matching docs/API_SPEC.md.
 */
public class StorageObjectNotFoundException extends GroomAiException {

    public StorageObjectNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "STORAGE_OBJECT_NOT_FOUND");
    }

    public StorageObjectNotFoundException(String message, HttpStatus httpStatus) {
        super(message, httpStatus, "STORAGE_OBJECT_NOT_FOUND");
    }
}
