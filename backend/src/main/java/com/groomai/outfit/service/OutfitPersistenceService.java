package com.groomai.outfit.service;

import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.LogOutfitWearResponseDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SaveOutfitRequestDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing persistent outfits, wear events, and historical wear tracking.
 */
public interface OutfitPersistenceService {

    SavedOutfitResponseDto saveOutfit(UUID userId, SaveOutfitRequestDto request);

    Page<SavedOutfitResponseDto> getSavedOutfits(UUID userId, Pageable pageable);

    SavedOutfitResponseDto getSavedOutfitById(UUID userId, UUID outfitId);

    LogOutfitWearResponseDto logOutfitWear(UUID userId, UUID outfitId, LogOutfitWearRequestDto request);

    Page<OutfitWearHistoryDto> getWearHistory(UUID userId, Pageable pageable);

    Map<UUID, Integer> getRecentItemWearDays(UUID userId, LocalDate referenceDate);

    default Map<UUID, Integer> getRecentItemWearDays(UUID userId) {
        return getRecentItemWearDays(userId, LocalDate.now(ZoneOffset.UTC));
    }
}
