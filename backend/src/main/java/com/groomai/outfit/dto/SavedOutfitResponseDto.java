package com.groomai.outfit.dto;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.ScoreStatus;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Representation of a saved outfit combination.
 */
public record SavedOutfitResponseDto(
    UUID id,
    String name,
    Occasion occasion,
    TemperatureBracket temperatureBracket,
    WeatherCondition weatherCondition,
    String stylingRationale,
    Integer suitabilityScore,
    ScoreStatus scoreStatus,
    Instant createdAt,
    List<OutfitItemDto> items
) {
    public SavedOutfitResponseDto(
        UUID id,
        Occasion occasion,
        TemperatureBracket temperatureBracket,
        WeatherCondition weatherCondition,
        String stylingRationale,
        Integer suitabilityScore,
        Instant createdAt,
        List<OutfitItemDto> items
    ) {
        this(
            id,
            null,
            occasion,
            temperatureBracket,
            weatherCondition,
            stylingRationale,
            suitabilityScore,
            suitabilityScore != null ? ScoreStatus.CALCULATED : ScoreStatus.CUSTOM,
            createdAt,
            items
        );
    }
}
