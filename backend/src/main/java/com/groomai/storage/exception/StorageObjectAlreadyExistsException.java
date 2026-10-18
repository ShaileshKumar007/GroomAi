package com.groomai.storage.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an upload attempt targets a storage key that already exists.
 * Enforces create-only storage semantics to prevent replay and overwrite attacks.
 * Maps to HTTP 409 Conflict with code "STORAGE_OBJECT_ALREADY_EXISTS".
 */
public class StorageObjectAlreadyExistsException extends GroomAiException {

    public StorageObjectAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT, "STORAGE_OBJECT_ALREADY_EXISTS");
    }

    public StorageObjectAlreadyExistsException(String message, HttpStatus httpStatus) {
        super(message, httpStatus, "STORAGE_OBJECT_ALREADY_EXISTS");
    }
}
