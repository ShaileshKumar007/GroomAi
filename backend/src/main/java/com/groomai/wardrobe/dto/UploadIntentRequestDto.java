package com.groomai.wardrobe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for POST /api/v1/wardrobe/upload-intent.
 */
public record UploadIntentRequestDto(
    @NotBlank(message = "Content type is required.")
    String contentType,

    @NotNull(message = "Byte size is required.")
    @Positive(message = "Byte size must be positive.")
    @Max(value = 10485760, message = "Byte size must not exceed 10MB (10485760 bytes).")
    Long byteSize
) {}
