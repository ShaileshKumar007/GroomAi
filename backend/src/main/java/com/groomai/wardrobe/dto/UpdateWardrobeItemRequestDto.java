package com.groomai.wardrobe.dto;

import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for PUT /api/v1/wardrobe/items/{id} (update wardrobe item metadata).
 * Notice: does not include storageKey, userId, or upload ticket (immutable server-managed fields).
 */
public record UpdateWardrobeItemRequestDto(
    @NotBlank(message = "Item name is required.")
    @Size(min = 2, max = 100, message = "Item name must be between 2 and 100 characters.")
    String name,

    Category category,

    @Size(max = 50, message = "Subcategory must not exceed 50 characters.")
    String subcategory,

    @NotNull(message = "Primary color is required.")
    Color primaryColor,

    Color secondaryColor,

    Pattern pattern,

    FormalityLevel formalityLevel,

    Season season
) {}
