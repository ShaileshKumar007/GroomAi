package com.groomai.wardrobe.exception;

import com.groomai.common.exception.GroomAiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting to catalog a wardrobe item with a storage key that already exists.
 * Maps to error code "DUPLICATE_ITEM" matching docs/API_SPEC.md.
 */
public class DuplicateWardrobeItemException extends GroomAiException {

    public DuplicateWardrobeItemException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_ITEM");
    }
}
