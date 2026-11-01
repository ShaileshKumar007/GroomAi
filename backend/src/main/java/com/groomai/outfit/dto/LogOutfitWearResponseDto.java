package com.groomai.outfit.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload confirming that an outfit wear event was successfully logged.
 * Matches docs/API_SPEC.md Section 5.2.
 */
public record LogOutfitWearResponseDto(
    UUID logId,
    UUID outfitId,
    LocalDate wornDate,
    String message
) {}
