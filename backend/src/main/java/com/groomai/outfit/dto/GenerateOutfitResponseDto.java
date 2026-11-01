package com.groomai.outfit.dto;

import java.util.Collections;
import java.util.List;

/**
 * Top-level response payload for POST /api/v1/outfits/generate.
 */
public record GenerateOutfitResponseDto(
    List<OutfitRecommendationDto> outfits,
    List<String> warnings
) {
    public GenerateOutfitResponseDto(List<OutfitRecommendationDto> outfits) {
        this(outfits, Collections.emptyList());
    }
}
