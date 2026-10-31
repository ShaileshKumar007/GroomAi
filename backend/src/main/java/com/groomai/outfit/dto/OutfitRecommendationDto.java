package com.groomai.outfit.dto;

import com.groomai.outfit.enums.Occasion;

import java.util.List;
import java.util.UUID;

/**
 * Recommended outfit card returned to the client.
 */
public record OutfitRecommendationDto(
    UUID id,
    Occasion occasion,
    int suitabilityScore,
    boolean aiStylingAvailable,
    String stylingRationale,
    ScoreBreakdownDto scoreBreakdown,
    List<OutfitItemDto> items
) {}
