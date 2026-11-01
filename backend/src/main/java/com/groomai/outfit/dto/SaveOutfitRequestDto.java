package com.groomai.outfit.dto;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for saving an outfit recommendation to persistent storage.
 */
public record SaveOutfitRequestDto(
    @NotNull(message = "Item IDs list is required")
    @Size(min = 3, max = 5, message = "Outfit must contain between 3 and 5 items")
    List<UUID> itemIds,

    @NotNull(message = "Occasion is required")
    Occasion occasion,

    TemperatureBracket temperatureBracket,
    WeatherCondition weatherCondition,

    @Size(max = 100, message = "Outfit name must not exceed 100 characters")
    String name,

    String stylingRationale,

    Integer suitabilityScore
) {
    public SaveOutfitRequestDto(
        List<UUID> itemIds,
        Occasion occasion,
        TemperatureBracket temperatureBracket,
        WeatherCondition weatherCondition,
        String stylingRationale,
        Integer suitabilityScore
    ) {
        this(itemIds, occasion, temperatureBracket, weatherCondition, null, stylingRationale, suitabilityScore);
    }
}
