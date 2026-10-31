package com.groomai.outfit.dto;

import com.groomai.outfit.enums.Occasion;
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
    Occasion occasion,
    TemperatureBracket temperatureBracket,
    WeatherCondition weatherCondition,
    String stylingRationale,
    int suitabilityScore,
    Instant createdAt,
    List<OutfitItemDto> items
) {}
