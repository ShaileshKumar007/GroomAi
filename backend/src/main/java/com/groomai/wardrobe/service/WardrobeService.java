package com.groomai.wardrobe.service;

import com.groomai.wardrobe.dto.CreateWardrobeItemRequestDto;
import com.groomai.wardrobe.dto.UploadIntentRequestDto;
import com.groomai.wardrobe.dto.UploadIntentResponseDto;
import com.groomai.wardrobe.dto.WardrobeItemResponseDto;

import java.util.UUID;

/**
 * Service orchestrating wardrobe cataloging, upload intent lifecycle, and item persistence.
 */
public interface WardrobeService {

    /**
     * Creates an upload intent for an authenticated user, enforcing wardrobe storage consent
     * and supported image content types.
     *
     * @param userId authenticated user ID
     * @param request upload intent request specifications
     * @return UploadIntentResponseDto containing upload URL, key, and required headers
     */
    UploadIntentResponseDto createUploadIntent(UUID userId, UploadIntentRequestDto request);

    /**
     * Confirms and catalogs an uploaded wardrobe item into PostgreSQL after verifying
     * tenant ownership, payload integrity, and image dimensions.
     *
     * @param userId authenticated user ID
     * @param request item metadata and storage key
     * @return cataloged WardrobeItemResponseDto with ephemeral download URL
     */
    WardrobeItemResponseDto createWardrobeItem(UUID userId, CreateWardrobeItemRequestDto request);
}
