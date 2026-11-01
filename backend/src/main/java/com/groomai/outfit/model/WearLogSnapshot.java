package com.groomai.outfit.model;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.ScoreStatus;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.Pattern;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of an outfit combination at the exact moment a wear event is logged.
 * Preserves historical integrity even if garments or outfits are modified or deleted.
 */
public record WearLogSnapshot(
    int version,
    UUID outfitId,
    String name,
    Occasion occasion,
    TemperatureBracket temperatureBracket,
    WeatherCondition weatherCondition,
    String stylingRationale,
    Integer suitabilityScore,
    ScoreStatus scoreStatus,
    List<WearLogItemSnapshot> items
) {

    public record WearLogItemSnapshot(
        UUID wardrobeItemId,
        String itemRole,
        Category category,
        String subcategory,
        Color primaryColor,
        Color secondaryColor,
        Pattern pattern,
        String fabric,
        String brand,
        String originalStorageKey
    ) {}
}
