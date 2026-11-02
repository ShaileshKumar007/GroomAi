package com.groomai.user.service;

import com.groomai.user.dto.PatchStylePreferencesRequestDto;
import com.groomai.user.dto.StylePreferencesResponseDto;
import com.groomai.user.dto.UpdateStylePreferencesRequestDto;

import java.util.UUID;

/**
 * Service managing user style preference profile persistence, validation, and retrieval.
 */
public interface StylePreferencesService {

    /**
     * Retrieve style preferences for the specified user.
     * Returns documented defaults with profileCompleted=false if no preferences exist.
     */
    StylePreferencesResponseDto getStylePreferences(UUID userId);

    /**
     * Completely replaces style preferences, enforces all constraints, and sets profileCompleted=true.
     */
    StylePreferencesResponseDto updateStylePreferences(UUID userId, UpdateStylePreferencesRequestDto request);

    /**
     * Partially updates supplied fields, validates resulting state, and preserves profileCompleted.
     */
    StylePreferencesResponseDto patchStylePreferences(UUID userId, PatchStylePreferencesRequestDto request);
}
