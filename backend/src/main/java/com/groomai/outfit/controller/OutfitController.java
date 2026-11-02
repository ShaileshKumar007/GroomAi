package com.groomai.outfit.controller;

import com.groomai.auth.security.UserPrincipal;
import com.groomai.outfit.dto.GenerateOutfitRequestDto;
import com.groomai.outfit.dto.GenerateOutfitResponseDto;
import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.LogOutfitWearResponseDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SaveOutfitRequestDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import com.groomai.outfit.service.OutfitGeneratorService;
import com.groomai.outfit.service.OutfitPersistenceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing deterministic outfit recommendation, persistence,
 * and wear logging endpoints according to docs/API_SPEC.md Section 5.
 */
@RestController
@RequestMapping("/api/v1/outfits")
public class OutfitController {

    private final OutfitGeneratorService outfitGeneratorService;
    private final OutfitPersistenceService outfitPersistenceService;

    public OutfitController(
        OutfitGeneratorService outfitGeneratorService,
        OutfitPersistenceService outfitPersistenceService
    ) {
        this.outfitGeneratorService = outfitGeneratorService;
        this.outfitPersistenceService = outfitPersistenceService;
    }

    /**
     * Generate deterministic outfit recommendations from the authenticated user's active wardrobe.
     * Operates completely offline without external AI dependencies.
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateOutfitResponseDto> generateOutfits(
        @AuthenticationPrincipal UserPrincipal principal,
        @org.springframework.web.bind.annotation.RequestHeader(value = "X-Timezone", required = false) String timezoneHeader,
        @Valid @RequestBody(required = false) GenerateOutfitRequestDto request
    ) {
        String tz = (request != null && request.timezone() != null && !request.timezone().isBlank())
            ? request.timezone()
            : timezoneHeader;
        GenerateOutfitRequestDto effectiveRequest = (request != null)
            ? new GenerateOutfitRequestDto(request.occasion(), request.temperatureBracket(), request.weatherCondition(), request.limit(), tz)
            : new GenerateOutfitRequestDto(null, null, null, 3, tz);

        GenerateOutfitResponseDto response = outfitGeneratorService.generateOutfits(
            principal.id(),
            effectiveRequest
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Save an outfit recommendation into persistent storage.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<SavedOutfitResponseDto> saveOutfit(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SaveOutfitRequestDto request
    ) {
        SavedOutfitResponseDto response = outfitPersistenceService.saveOutfit(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve a paginated list of the authenticated user's saved outfits.
     */
    @GetMapping
    public ResponseEntity<Page<SavedOutfitResponseDto>> getSavedOutfits(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SavedOutfitResponseDto> page = outfitPersistenceService.getSavedOutfits(principal.id(), pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * Retrieve a single saved outfit by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SavedOutfitResponseDto> getSavedOutfitById(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        SavedOutfitResponseDto response = outfitPersistenceService.getSavedOutfitById(principal.id(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * Log a saved outfit as worn on a specific date.
     */
    @PostMapping("/{id}/wear")
    public ResponseEntity<LogOutfitWearResponseDto> logOutfitWear(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestHeader(value = "X-Timezone", required = false) String timezoneHeader,
        @Valid @RequestBody(required = false) LogOutfitWearRequestDto request
    ) {
        String tz = (request != null && request.timezone() != null && !request.timezone().isBlank())
            ? request.timezone()
            : timezoneHeader;
        LogOutfitWearRequestDto effectiveRequest = (request != null)
            ? new LogOutfitWearRequestDto(request.wornDate(), request.notes(), tz)
            : new LogOutfitWearRequestDto(null, null, tz);

        LogOutfitWearResponseDto response = outfitPersistenceService.logOutfitWear(principal.id(), id, effectiveRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve historical wear logs for the authenticated user.
     * Maps to both /wear-history and /history.
     */
    @GetMapping({"/wear-history", "/history"})
    public ResponseEntity<Page<OutfitWearHistoryDto>> getWearHistory(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "wornDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<OutfitWearHistoryDto> page = outfitPersistenceService.getWearHistory(principal.id(), pageable);
        return ResponseEntity.ok(page);
    }
}
