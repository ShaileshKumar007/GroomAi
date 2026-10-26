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

    /**
     * Lists paginated wardrobe items for the authenticated user matching docs/API_SPEC.md Section 4.3.
     */
    @org.springframework.web.bind.annotation.GetMapping("/items")
    public ResponseEntity<com.groomai.common.dto.PagedResponse<WardrobeItemResponseDto>> listWardrobeItems(
        @AuthenticationPrincipal UserPrincipal principal,
        @org.springframework.web.bind.annotation.RequestParam(required = false) com.groomai.wardrobe.enums.Category category,
        @org.springframework.web.bind.annotation.RequestParam(required = false) com.groomai.wardrobe.enums.ItemStatus status,
        @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
        org.springframework.data.domain.Pageable pageable
    ) {
        com.groomai.common.dto.PagedResponse<WardrobeItemResponseDto> response =
            wardrobeService.listWardrobeItems(principal.id(), category, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a single wardrobe item by ID for the authenticated user matching docs/API_SPEC.md Section 4.4.
     */
    @org.springframework.web.bind.annotation.GetMapping("/items/{id}")
    public ResponseEntity<WardrobeItemResponseDto> getWardrobeItem(
        @AuthenticationPrincipal UserPrincipal principal,
        @org.springframework.web.bind.annotation.PathVariable java.util.UUID id
    ) {
        WardrobeItemResponseDto response = wardrobeService.getWardrobeItem(principal.id(), id);
        return ResponseEntity.ok(response);
    }
}
