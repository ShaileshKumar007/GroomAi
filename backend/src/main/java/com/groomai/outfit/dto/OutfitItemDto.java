package com.groomai.outfit.dto;

import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;

import java.util.UUID;

/**
 * Item summary within a generated outfit combination.
 */
public record OutfitItemDto(
    String role,
    UUID id,
    String name,
    Category category,
    Color primaryColor,
    Color secondaryColor,
    Pattern pattern,
    FormalityLevel formalityLevel,
    Season season,
    String downloadUrl
) {}
