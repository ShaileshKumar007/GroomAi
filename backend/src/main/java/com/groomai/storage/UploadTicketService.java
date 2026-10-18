package com.groomai.storage;

import io.jsonwebtoken.Claims;

import java.time.Duration;
import java.util.UUID;

/**
 * Service generating and cryptographically validating short-lived, tamper-evident
 * upload-intent tickets binding user ID, canonical storage key, purpose, and expiration.
 */
public interface UploadTicketService {

    String PURPOSE_UPLOAD_INTENT = "UPLOAD_INTENT";
    String CLAIM_STORAGE_KEY = "storageKey";
    String CLAIM_PURPOSE = "purpose";

    /**
     * Generates a signed upload ticket binding the authenticated user ID and target storage key.
     *
     * @param userId the owning user ID
     * @param storageKey the target canonical storage key
     * @param ttl ticket validity duration (maximum 15 minutes)
     * @return compact signed JWT ticket string
     */
    String generateUploadTicket(UUID userId, String storageKey, Duration ttl);

    /**
     * Validates an upload ticket against expected ownership, key, purpose, and expiration.
     *
     * @param ticket the compact signed ticket to validate
     * @param expectedUserId the authenticated user ID
     * @param expectedStorageKey the canonical storage key being accessed
     * @return validated Claims payload
     * @throws com.groomai.storage.exception.InvalidUploadTicketException if ticket is missing, malformed, tampered with, or mismatched
     * @throws com.groomai.storage.exception.ExpiredUploadTicketException if ticket is expired
     */
    Claims validateUploadTicket(String ticket, UUID expectedUserId, String expectedStorageKey);
}
