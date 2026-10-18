package com.groomai.wardrobe.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Response payload for POST /api/v1/wardrobe/upload-intent.
 */
public record UploadIntentResponseDto(
    String uploadMode,
    String uploadUrl,
    String storageKey,
    Instant expiresAt,
    String httpMethod,
    Map<String, String> requiredHeaders,
    String uploadTicket
) {
    public UploadIntentResponseDto(
        String uploadMode,
        String uploadUrl,
        String storageKey,
        Instant expiresAt,
        String httpMethod,
        Map<String, String> requiredHeaders
    ) {
        this(uploadMode, uploadUrl, storageKey, expiresAt, httpMethod, requiredHeaders, null);
    }
}
