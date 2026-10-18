package com.groomai.wardrobe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.auth.security.JwtAuthenticationFilter;
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.RestAuthenticationEntryPoint;
import com.groomai.common.config.SecurityConfig;
import com.groomai.common.exception.GlobalExceptionHandler;
import com.groomai.user.entity.User;
import com.groomai.user.exception.ConsentRequiredException;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.dto.CreateWardrobeItemRequestDto;
import com.groomai.wardrobe.dto.UploadIntentRequestDto;
import com.groomai.wardrobe.dto.UploadIntentResponseDto;
import com.groomai.wardrobe.dto.WardrobeItemResponseDto;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.exception.DuplicateWardrobeItemException;
import com.groomai.wardrobe.exception.UnsupportedImageFormatException;
import com.groomai.wardrobe.service.WardrobeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WardrobeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RestAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, JwtService.class})
class WardrobeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private WardrobeService wardrobeService;

    private UUID userId;
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("user@example.com").role("ROLE_USER").build();
        token = jwtService.generateAccessToken(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("POST /upload-intent: unauthenticated request returns 401 Unauthorized")
    void uploadIntent_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/wardrobe/upload-intent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UploadIntentRequestDto("image/jpeg", 1024L))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /upload-intent: missing consent returns 403 Forbidden with CONSENT_REQUIRED")
    void uploadIntent_missingConsent_returns403() throws Exception {
        UploadIntentRequestDto request = new UploadIntentRequestDto("image/jpeg", 2048500L);
        when(wardrobeService.createUploadIntent(eq(userId), any())).thenThrow(
            new ConsentRequiredException("Wardrobe storage consent is required before requesting an upload intent.")
        );

        mockMvc.perform(post("/api/v1/wardrobe/upload-intent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"))
            .andExpect(jsonPath("$.message").value("Wardrobe storage consent is required before requesting an upload intent."));
    }

    @Test
    @DisplayName("POST /upload-intent: unsupported format returns 400 Bad Request with UNSUPPORTED_IMAGE_FORMAT")
    void uploadIntent_unsupportedFormat_returns400() throws Exception {
        UploadIntentRequestDto request = new UploadIntentRequestDto("image/heic", 2048500L);
        when(wardrobeService.createUploadIntent(eq(userId), any())).thenThrow(
            new UnsupportedImageFormatException("Unsupported image format: image/heic. Only image/jpeg is accepted.")
        );

        mockMvc.perform(post("/api/v1/wardrobe/upload-intent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_FORMAT"));
    }

    @Test
    @DisplayName("POST /upload-intent: oversized file returns 400 Bad Request with VALIDATION_FAILED")
    void uploadIntent_oversizedFile_returns400() throws Exception {
        UploadIntentRequestDto request = new UploadIntentRequestDto("image/jpeg", 20000000L); // 20 MB

        mockMvc.perform(post("/api/v1/wardrobe/upload-intent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /upload-intent: valid request returns 201 Created with UploadIntentResponseDto")
    void uploadIntent_validRequest_returns201() throws Exception {
        UploadIntentRequestDto request = new UploadIntentRequestDto("image/jpeg", 2048500L);
        String storageKey = "wardrobe/" + userId + "/item-123.jpg";
        UploadIntentResponseDto response = new UploadIntentResponseDto(
            "LOCAL_STREAM",
            "http://localhost:8080/api/v1/storage/upload/" + storageKey,
            storageKey,
            Instant.parse("2026-10-18T02:00:00Z"),
            "POST",
            Map.of("Content-Type", "multipart/form-data")
        );

        when(wardrobeService.createUploadIntent(eq(userId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/wardrobe/upload-intent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uploadMode").value("LOCAL_STREAM"))
            .andExpect(jsonPath("$.storageKey").value(storageKey))
            .andExpect(jsonPath("$.httpMethod").value("POST"))
            .andExpect(jsonPath("$.requiredHeaders['Content-Type']").value("multipart/form-data"));
    }

    @Test
    @DisplayName("POST /items: validation failure on missing required fields returns 400 VALIDATION_FAILED")
    void createItem_missingRequiredFields_returns400() throws Exception {
        // Missing name and category
        String invalidJson = """
            {
                "primaryColor": "NAVY",
                "storageKey": "wardrobe/%s/item-123.jpg"
            }
            """.formatted(userId);

        mockMvc.perform(post("/api/v1/wardrobe/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /items: cross-tenant access returns 403 Forbidden")
    void createItem_crossTenant_returns403() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            null,
            Pattern.SOLID,
            FormalityLevel.CASUAL,
            Season.ALL_SEASON,
            "wardrobe/" + otherUserId + "/item-123.jpg"
        );

        when(wardrobeService.createWardrobeItem(eq(userId), any())).thenThrow(
            new AccessDeniedException("Access to storage key denied: cross-tenant access prohibited.")
        );

        mockMvc.perform(post("/api/v1/wardrobe/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /items: duplicate storage key returns 409 Conflict with DUPLICATE_ITEM")
    void createItem_duplicateKey_returns409() throws Exception {
        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            null,
            Pattern.SOLID,
            FormalityLevel.CASUAL,
            Season.ALL_SEASON,
            "wardrobe/" + userId + "/item-123.jpg"
        );

        when(wardrobeService.createWardrobeItem(eq(userId), any())).thenThrow(
            new DuplicateWardrobeItemException("A wardrobe item with this storage key already exists.")
        );

        mockMvc.perform(post("/api/v1/wardrobe/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_ITEM"));
    }

    @Test
    @DisplayName("POST /items: valid request returns 201 Created with WardrobeItemResponseDto")
    void createItem_validRequest_returns201() throws Exception {
        String storageKey = "wardrobe/" + userId + "/item-123.jpg";
        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            Color.WHITE,
            Pattern.SOLID,
            FormalityLevel.SMART_CASUAL,
            Season.ALL_SEASON,
            storageKey
        );

        UUID itemId = UUID.randomUUID();
        WardrobeItemResponseDto response = new WardrobeItemResponseDto(
            itemId,
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            Color.WHITE,
            Pattern.SOLID,
            FormalityLevel.SMART_CASUAL,
            Season.ALL_SEASON,
            storageKey,
            "http://localhost:8080/api/v1/storage/files/" + storageKey,
            ItemStatus.ACTIVE,
            Instant.parse("2026-10-18T01:30:00Z"),
            Instant.parse("2026-10-18T01:30:00Z")
        );

        when(wardrobeService.createWardrobeItem(eq(userId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/wardrobe/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(itemId.toString()))
            .andExpect(jsonPath("$.name").value("Oxford Shirt"))
            .andExpect(jsonPath("$.category").value("TOP"))
            .andExpect(jsonPath("$.primaryColor").value("NAVY"))
            .andExpect(jsonPath("$.downloadUrl").value("http://localhost:8080/api/v1/storage/files/" + storageKey))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
