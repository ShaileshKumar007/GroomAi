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
 * Request payload for POST /api/v1/wardrobe/items (confirm and catalog).
 */
public record CreateWardrobeItemRequestDto(
    @NotBlank(message = "Item name is required.")
    @Size(min = 2, max = 100, message = "Item name must be between 2 and 100 characters.")
    String name,

    @NotNull(message = "Category is required.")
    Category category,

    @Size(max = 50, message = "Subcategory must not exceed 50 characters.")
    String subcategory,

    @NotNull(message = "Primary color is required.")
    Color primaryColor,

    Color secondaryColor,

    Pattern pattern,

    FormalityLevel formalityLevel,

    Season season,

    @NotBlank(message = "Storage key is required.")
    String storageKey,

    String uploadTicket
) {
    public CreateWardrobeItemRequestDto(
        String name,
        Category category,
        String subcategory,
        Color primaryColor,
        Color secondaryColor,
        Pattern pattern,
        FormalityLevel formalityLevel,
        Season season,
        String storageKey
    ) {
        this(name, category, subcategory, primaryColor, secondaryColor, pattern, formalityLevel, season, storageKey, null);
    }
}
