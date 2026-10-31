package com.groomai.outfit.service;

import com.groomai.outfit.dto.GenerateOutfitRequestDto;
import com.groomai.outfit.dto.GenerateOutfitResponseDto;

import java.util.UUID;

/**
 * Service contract for deterministic outfit recommendation generation.
 */
public interface OutfitGeneratorService {

    /**
     * Generates ranked outfit recommendations from the user's active wardrobe.
     *
     * @param userId authenticated user ID
     * @param request generation criteria including occasion, optional weather, and limit
     * @return top-ranked outfit recommendations with score breakdowns and honest rationales
     */
    GenerateOutfitResponseDto generateOutfits(UUID userId, GenerateOutfitRequestDto request);
}
