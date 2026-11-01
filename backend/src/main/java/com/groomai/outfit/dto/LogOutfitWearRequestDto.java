package com.groomai.outfit.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request payload for recording an outfit wear event.
 */
public record LogOutfitWearRequestDto(
    LocalDate wornDate,
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    String notes
) {}
