package com.groomai.user.controller;

import com.groomai.auth.security.UserPrincipal;
import com.groomai.user.dto.PatchStylePreferencesRequestDto;
import com.groomai.user.dto.StylePreferencesResponseDto;
import com.groomai.user.dto.UpdateStylePreferencesRequestDto;
import com.groomai.user.service.StylePreferencesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing style preferences endpoints according to docs/API_SPEC.md Sections 3.4 and 3.5.
 */
@RestController
@RequestMapping("/api/v1/profile/style-preferences")
public class StylePreferencesController {

    private final StylePreferencesService stylePreferencesService;

    public StylePreferencesController(StylePreferencesService stylePreferencesService) {
        this.stylePreferencesService = stylePreferencesService;
    }

    /**
     * Get style preferences for authenticated user.
     * Returns documented defaults with profileCompleted=false for unconfigured users.
     */
    @GetMapping
    public ResponseEntity<StylePreferencesResponseDto> getStylePreferences(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        StylePreferencesResponseDto response = stylePreferencesService.getStylePreferences(principal.id());
        return ResponseEntity.ok(response);
    }

    /**
     * Completely replace style preferences for authenticated user and mark profileCompleted=true.
     */
    @PutMapping
    public ResponseEntity<StylePreferencesResponseDto> updateStylePreferences(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateStylePreferencesRequestDto request
    ) {
        StylePreferencesResponseDto response = stylePreferencesService.updateStylePreferences(principal.id(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Partially update style preferences for authenticated user while preserving profileCompleted.
     */
    @PatchMapping
    public ResponseEntity<StylePreferencesResponseDto> patchStylePreferences(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody PatchStylePreferencesRequestDto request
    ) {
        StylePreferencesResponseDto response = stylePreferencesService.patchStylePreferences(principal.id(), request);
        return ResponseEntity.ok(response);
    }
}
