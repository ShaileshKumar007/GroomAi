package com.groomai.outfit.dto;

import com.groomai.outfit.enums.Occasion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Representation of a historical outfit wear event.
 */
public record OutfitWearHistoryDto(
    UUID logId,
    UUID outfitId,
    LocalDate wornDate,
    String notes,
    Instant createdAt,
    Occasion occasion,
    Integer suitabilityScore,
    String stylingRationale,
    List<OutfitItemDto> items
) {}
