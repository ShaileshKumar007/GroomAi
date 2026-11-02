package com.groomai.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.auth.security.JwtAuthenticationFilter;
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.RestAuthenticationEntryPoint;
import com.groomai.common.config.SecurityConfig;
import com.groomai.common.exception.GlobalExceptionHandler;
import com.groomai.user.dto.PatchStylePreferencesRequestDto;
import com.groomai.user.dto.StylePreferencesResponseDto;
import com.groomai.user.dto.UpdateStylePreferencesRequestDto;
import com.groomai.user.entity.User;
import com.groomai.user.enums.PreferredFit;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
import com.groomai.user.exception.StylePreferencesValidationException;
import com.groomai.user.repository.UserRepository;
import com.groomai.user.service.StylePreferencesService;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StylePreferencesController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RestAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, JwtService.class})
class StylePreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private StylePreferencesService stylePreferencesService;

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

    // --- GET /api/v1/profile/style-preferences ---

    @Test
    @DisplayName("GET /api/v1/profile/style-preferences: unauthenticated request returns 401 Unauthorized")
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/profile/style-preferences"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/profile/style-preferences: returns defaults with profileCompleted=false for unconfigured user")
    void get_unconfiguredUser_returnsDefaults() throws Exception {
        when(stylePreferencesService.getStylePreferences(userId)).thenReturn(StylePreferencesResponseDto.unconfigured());

        mockMvc.perform(get("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCompleted").value(false))
            .andExpect(jsonPath("$.primaryStyle").value("CASUAL"))
            .andExpect(jsonPath("$.preferredFit").value("REGULAR"))
            .andExpect(jsonPath("$.defaultFormality").value("CASUAL"))
            .andExpect(jsonPath("$.colorPreferences").isEmpty())
            .andExpect(jsonPath("$.avoidedColors").isEmpty())
            .andExpect(jsonPath("$.patternPreferences").isEmpty())
            .andExpect(jsonPath("$.avoidedPatterns").isEmpty())
            .andExpect(jsonPath("$.styleGoals").isEmpty());

        verify(stylePreferencesService).getStylePreferences(userId);
    }

    @Test
    @DisplayName("GET /api/v1/profile/style-preferences: returns populated profile with profileCompleted=true")
    void get_configuredUser_returnsStoredPreferences() throws Exception {
        StylePreferencesResponseDto responseDto = new StylePreferencesResponseDto(
            true,
            PrimaryStyle.SMART_CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY, Color.WHITE),
            List.of(Color.YELLOW),
            List.of(Pattern.SOLID),
            List.of(Pattern.FLORAL),
            FormalityLevel.SMART_CASUAL,
            List.of(StyleGoal.LOOK_SHARPER),
            Instant.now()
        );

        when(stylePreferencesService.getStylePreferences(userId)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.primaryStyle").value("SMART_CASUAL"))
            .andExpect(jsonPath("$.preferredFit").value("REGULAR"))
            .andExpect(jsonPath("$.colorPreferences[0]").value("NAVY"))
            .andExpect(jsonPath("$.colorPreferences[1]").value("WHITE"))
            .andExpect(jsonPath("$.avoidedColors[0]").value("YELLOW"))
            .andExpect(jsonPath("$.patternPreferences[0]").value("SOLID"))
            .andExpect(jsonPath("$.avoidedPatterns[0]").value("FLORAL"))
            .andExpect(jsonPath("$.defaultFormality").value("SMART_CASUAL"))
            .andExpect(jsonPath("$.styleGoals[0]").value("LOOK_SHARPER"));
    }

    // --- PUT /api/v1/profile/style-preferences ---

    @Test
    @DisplayName("PUT /api/v1/profile/style-preferences: unauthenticated request returns 401")
    void put_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/profile/style-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile/style-preferences: valid replacement sets profileCompleted=true")
    void put_validPayload_returns200WithCompletedTrue() throws Exception {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.MINIMALIST,
            PreferredFit.SLIM,
            List.of(Color.BLACK, Color.WHITE, Color.GREY),
            List.of(Color.RED),
            List.of(Pattern.SOLID),
            List.of(Pattern.GRAPHIC),
            FormalityLevel.SMART_CASUAL,
            List.of(StyleGoal.MINIMAL_EFFORT)
        );

        StylePreferencesResponseDto responseDto = new StylePreferencesResponseDto(
            true,
            PrimaryStyle.MINIMALIST,
            PreferredFit.SLIM,
            List.of(Color.BLACK, Color.WHITE, Color.GREY),
            List.of(Color.RED),
            List.of(Pattern.SOLID),
            List.of(Pattern.GRAPHIC),
            FormalityLevel.SMART_CASUAL,
            List.of(StyleGoal.MINIMAL_EFFORT),
            Instant.now()
        );

        when(stylePreferencesService.updateStylePreferences(eq(userId), any())).thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.primaryStyle").value("MINIMALIST"))
            .andExpect(jsonPath("$.preferredFit").value("SLIM"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile/style-preferences: missing required fields returns 400 VALIDATION_FAILED")
    void put_missingRequiredFields_returns400() throws Exception {
        String invalidJson = """
            {
                "primaryStyle": "CASUAL"
            }
            """;

        mockMvc.perform(put("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile/style-preferences: invalid enum string returns 400 VALIDATION_FAILED")
    void put_invalidEnumString_returns400() throws Exception {
        String invalidJson = """
            {
                "primaryStyle": "UNKNOWN_AESTHETIC",
                "preferredFit": "REGULAR",
                "colorPreferences": ["NAVY"],
                "avoidedColors": [],
                "patternPreferences": ["SOLID"],
                "avoidedPatterns": [],
                "defaultFormality": "CASUAL",
                "styleGoals": []
            }
            """;

        mockMvc.perform(put("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile/style-preferences: service duplicate/intersection validation error returns 400 VALIDATION_FAILED")
    void put_domainValidationFailure_returns400() throws Exception {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY),
            List.of(Color.NAVY), // duplicate/intersecting
            List.of(Pattern.SOLID),
            List.of(),
            FormalityLevel.CASUAL,
            List.of()
        );

        when(stylePreferencesService.updateStylePreferences(eq(userId), any())).thenThrow(
            new StylePreferencesValidationException("Color cannot be both preferred and avoided: NAVY")
        );

        mockMvc.perform(put("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value("Color cannot be both preferred and avoided: NAVY"));
    }

    // --- PATCH /api/v1/profile/style-preferences ---

    @Test
    @DisplayName("PATCH /api/v1/profile/style-preferences: unauthenticated request returns 401")
    void patch_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/style-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/profile/style-preferences: partial update succeeds and preserves profileCompleted")
    void patch_partialUpdate_returns200() throws Exception {
        PatchStylePreferencesRequestDto request = new PatchStylePreferencesRequestDto(
            null,
            null,
            null,
            List.of(Color.YELLOW),
            null,
            null,
            null,
            null
        );

        StylePreferencesResponseDto responseDto = new StylePreferencesResponseDto(
            false, // preserved false
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(),
            List.of(Color.YELLOW),
            List.of(),
            List.of(),
            FormalityLevel.CASUAL,
            List.of(),
            Instant.now()
        );

        when(stylePreferencesService.patchStylePreferences(eq(userId), any())).thenReturn(responseDto);

        mockMvc.perform(patch("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCompleted").value(false))
            .andExpect(jsonPath("$.avoidedColors[0]").value("YELLOW"));
    }

    @Test
    @DisplayName("PATCH /api/v1/profile/style-preferences: supplying empty array clears that collection")
    void patch_emptyArray_clearsCollection() throws Exception {
        String jsonWithEmptyArray = """
            {
                "colorPreferences": []
            }
            """;

        StylePreferencesResponseDto responseDto = new StylePreferencesResponseDto(
            true,
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(),
            List.of(Color.YELLOW),
            List.of(),
            List.of(),
            FormalityLevel.CASUAL,
            List.of(),
            Instant.now()
        );

        when(stylePreferencesService.patchStylePreferences(eq(userId), any())).thenReturn(responseDto);

        mockMvc.perform(patch("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonWithEmptyArray))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.colorPreferences").isEmpty());
    }

    @Test
    @DisplayName("PATCH /api/v1/profile/style-preferences: invalid enum string returns 400 VALIDATION_FAILED")
    void patch_invalidEnumString_returns400() throws Exception {
        String invalidJson = """
            {
                "preferredFit": "SUPER_TIGHT"
            }
            """;

        mockMvc.perform(patch("/api/v1/profile/style-preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
