package com.groomai.wardrobe.controller;

import com.groomai.auth.security.UserPrincipal;
import com.groomai.wardrobe.dto.CreateWardrobeItemRequestDto;
import com.groomai.wardrobe.dto.UploadIntentRequestDto;
import com.groomai.wardrobe.dto.UploadIntentResponseDto;
import com.groomai.wardrobe.dto.WardrobeItemResponseDto;
import com.groomai.wardrobe.service.WardrobeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing wardrobe digital catalog endpoints according to docs/API_SPEC.md Section 4.
 */
@RestController
@RequestMapping("/api/v1/wardrobe")
public class WardrobeController {

    private final WardrobeService wardrobeService;

    public WardrobeController(WardrobeService wardrobeService) {
        this.wardrobeService = wardrobeService;
    }

    /**
     * Request pre-signed or local stream upload intent for a wardrobe image.
     * Enforces wardrobe storage consent and accepts only image/jpeg.
     */
    @PostMapping("/upload-intent")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UploadIntentResponseDto> createUploadIntent(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UploadIntentRequestDto request
    ) {
        UploadIntentResponseDto response = wardrobeService.createUploadIntent(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Confirm and catalog an uploaded item into the digital wardrobe.
     * Verifies JPEG magic bytes, minimum dimensions (200x200), and tenancy.
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<WardrobeItemResponseDto> createWardrobeItem(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateWardrobeItemRequestDto request
    ) {
        WardrobeItemResponseDto response = wardrobeService.createWardrobeItem(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
