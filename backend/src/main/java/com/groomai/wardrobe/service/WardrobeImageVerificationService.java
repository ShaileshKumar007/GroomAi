package com.groomai.wardrobe.service;

/**
 * Service responsible for validating uploaded wardrobe images prior to cataloging.
 * Enforces magic byte checks, file size constraints, and minimum image dimensions
 * without decompressing full image rasters into JVM heap memory.
 */
public interface WardrobeImageVerificationService {

    /**
     * Verifies that the object at storageKey exists, is a valid JPEG (magic bytes FF D8 FF),
     * does not exceed 10MB, and meets minimum dimensions of 200x200 pixels.
     *
     * @param storageKey the canonical storage key to inspect
     */
    void verify(String storageKey);
}
