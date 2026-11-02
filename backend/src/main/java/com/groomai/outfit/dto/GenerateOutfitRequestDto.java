package com.groomai.outfit.dto;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request payload for generating deterministic outfit recommendations.
 */
public record GenerateOutfitRequestDto(
    Occasion occasion,
    TemperatureBracket temperatureBracket,
    WeatherCondition weatherCondition,
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 10, message = "Limit must not exceed 10")
    Integer limit,
    String timezone
) {
    public GenerateOutfitRequestDto(
        Occasion occasion,
        TemperatureBracket temperatureBracket,
        WeatherCondition weatherCondition,
        Integer limit
    ) {
        this(occasion, temperatureBracket, weatherCondition, limit, null);
    }

    public int effectiveLimit() {
        return (limit != null) ? limit : 3;
    }
}
