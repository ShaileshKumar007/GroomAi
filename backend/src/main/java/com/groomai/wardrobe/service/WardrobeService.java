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

    /**
     * Lists cataloged wardrobe items for an authenticated user, enforcing tenant isolation,
     * optional category and status filtering, and pagination.
     *
     * @param userId authenticated user ID
     * @param category optional garment category filter
     * @param status optional item status filter (defaults to ACTIVE)
     * @param pageable pagination and sorting parameters
     * @return paginated envelope of WardrobeItemResponseDto items with ephemeral download URLs
     */
    com.groomai.common.dto.PagedResponse<WardrobeItemResponseDto> listWardrobeItems(
        UUID userId,
        com.groomai.wardrobe.enums.Category category,
        com.groomai.wardrobe.enums.ItemStatus status,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Retrieves a single wardrobe item by ID, enforcing tenant isolation.
     *
     * @param userId authenticated user ID
     * @param itemId wardrobe item ID
     * @return WardrobeItemResponseDto with ephemeral download URL
     */
    WardrobeItemResponseDto getWardrobeItem(UUID userId, UUID itemId);

    /**
     * Updates an existing wardrobe item's mutable metadata, enforcing tenant isolation.
     *
     * @param userId authenticated user ID
     * @param itemId wardrobe item ID
     * @param request updated item metadata
     * @return updated WardrobeItemResponseDto with ephemeral download URL
     */
    WardrobeItemResponseDto updateWardrobeItem(
        UUID userId,
        UUID itemId,
        com.groomai.wardrobe.dto.UpdateWardrobeItemRequestDto request
    );

    /**
     * Deletes a wardrobe item, enforcing tenant isolation, and enqueues its storage key
     * to storage_deletion_outbox in the same transaction for durable eradication.
     *
     * @param userId authenticated user ID
     * @param itemId wardrobe item ID
     */
    void deleteWardrobeItem(UUID userId, UUID itemId);
}
