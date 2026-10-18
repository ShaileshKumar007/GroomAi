package com.groomai.wardrobe.dto;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a cataloged wardrobe item with ephemeral downloadUrl.
 */
public record WardrobeItemResponseDto(
    UUID id,
    String name,
    Category category,
    String subcategory,
    Color primaryColor,
    Color secondaryColor,
    Pattern pattern,
    FormalityLevel formalityLevel,
    Season season,
    String storageKey,
    String downloadUrl,
    ItemStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static WardrobeItemResponseDto fromEntity(WardrobeItem item, String downloadUrl) {
        return new WardrobeItemResponseDto(
            item.getId(),
            item.getName(),
            item.getCategory(),
            item.getSubcategory(),
            item.getPrimaryColor(),
            item.getSecondaryColor(),
            item.getPattern(),
            item.getFormalityLevel(),
            item.getSeason(),
            item.getStorageKey(),
            downloadUrl,
            item.getStatus(),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}
