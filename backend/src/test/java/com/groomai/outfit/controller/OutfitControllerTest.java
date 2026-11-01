package com.groomai.outfit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.auth.security.JwtAuthenticationFilter;
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.RestAuthenticationEntryPoint;
import com.groomai.common.config.SecurityConfig;
import com.groomai.common.exception.GlobalExceptionHandler;
import com.groomai.outfit.dto.GenerateOutfitRequestDto;
import com.groomai.outfit.dto.GenerateOutfitResponseDto;
import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.LogOutfitWearResponseDto;
import com.groomai.outfit.dto.OutfitItemDto;
import com.groomai.outfit.dto.OutfitRecommendationDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SaveOutfitRequestDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import com.groomai.outfit.dto.ScoreBreakdownDto;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.exception.InsufficientWardrobeItemsException;
import com.groomai.outfit.service.OutfitGeneratorService;
import com.groomai.user.entity.User;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutfitController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RestAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, JwtService.class})
class OutfitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OutfitGeneratorService outfitGeneratorService;

    @MockBean
    private com.groomai.outfit.service.OutfitPersistenceService outfitPersistenceService;

    private UUID userId;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User testUser = User.builder()
            .id(userId)
            .email("user@groomai.com")
            .passwordHash("$2a$12$eXampLeHash...")
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        jwtToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Unauthenticated request to POST /api/v1/outfits/generate returns 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Valid authenticated request returns 200 OK with recommended outfits")
    void testGenerateOutfitsSuccess() throws Exception {
        var item1 = new OutfitItemDto(
            "TOP",
            UUID.randomUUID(),
            "Navy Oxford",
            Category.TOP,
            Color.NAVY,
            null,
            Pattern.SOLID,
            FormalityLevel.SMART_CASUAL,
            Season.ALL_SEASON,
            "https://storage.groomai.com/wardrobe/user/navy.jpg"
        );
        var item2 = new OutfitItemDto(
            "BOTTOM",
            UUID.randomUUID(),
            "Beige Chinos",
            Category.BOTTOM,
            Color.BEIGE,
            null,
            Pattern.SOLID,
            FormalityLevel.SMART_CASUAL,
            Season.ALL_SEASON,
            "https://storage.groomai.com/wardrobe/user/chinos.jpg"
        );
        var item3 = new OutfitItemDto(
            "FOOTWEAR",
            UUID.randomUUID(),
            "Brown Loafers",
            Category.FOOTWEAR,
            Color.BROWN,
            null,
            Pattern.SOLID,
            FormalityLevel.SMART_CASUAL,
            Season.ALL_SEASON,
            "https://storage.groomai.com/wardrobe/user/loafers.jpg"
        );

        var breakdown = new ScoreBreakdownDto(28, 25, 20, 14, 5, 0);
        var recommendation = new OutfitRecommendationDto(
            UUID.randomUUID(),
            Occasion.SMART_CASUAL,
            92,
            false,
            "Classic contrast with timeless smart casual balance.",
            breakdown,
            List.of(item1, item2, item3)
        );

        when(outfitGeneratorService.generateOutfits(eq(userId), any(GenerateOutfitRequestDto.class)))
            .thenReturn(new GenerateOutfitResponseDto(List.of(recommendation)));

        var request = new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, null, null, 3);

        mockMvc.perform(post("/api/v1/outfits/generate")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outfits[0].suitabilityScore").value(92))
            .andExpect(jsonPath("$.outfits[0].occasion").value("SMART_CASUAL"))
            .andExpect(jsonPath("$.outfits[0].scoreBreakdown.colorHarmony").value(28))
            .andExpect(jsonPath("$.outfits[0].items[0].role").value("TOP"))
            .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    @DisplayName("Validation error: Limit greater than 10 returns 400 Bad Request")
    void testLimitGreaterThan10Returns400() throws Exception {
        String invalidPayload = """
            {
                "occasion": "SMART_CASUAL",
                "limit": 15
            }
            """;

        mockMvc.perform(post("/api/v1/outfits/generate")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Validation error: Limit less than 1 returns 400 Bad Request")
    void testLimitLessThan1Returns400() throws Exception {
        String invalidPayload = """
            {
                "occasion": "SMART_CASUAL",
                "limit": 0
            }
            """;

        mockMvc.perform(post("/api/v1/outfits/generate")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Validation error: Invalid occasion enum returns 400 Bad Request")
    void testInvalidOccasionReturns400() throws Exception {
        String invalidPayload = """
            {
                "occasion": "GALA_PREMIERE"
            }
            """;

        mockMvc.perform(post("/api/v1/outfits/generate")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Insufficient wardrobe items throws InsufficientWardrobeItemsException and returns 422 Unprocessable Entity")
    void testInsufficientWardrobeReturns422() throws Exception {
        when(outfitGeneratorService.generateOutfits(eq(userId), any(GenerateOutfitRequestDto.class)))
            .thenThrow(new InsufficientWardrobeItemsException(List.of(Category.BOTTOM, Category.FOOTWEAR)));

        mockMvc.perform(post("/api/v1/outfits/generate")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_WARDROBE_ITEMS"))
            .andExpect(jsonPath("$.missingCategories[0]").value("BOTTOM"))
            .andExpect(jsonPath("$.missingCategories[1]").value("FOOTWEAR"));
    }

    @Test
    @DisplayName("Empty combinations return 200 OK with empty outfits array and warning message")
    void testEmptyCombinationsReturns200WithWarnings() throws Exception {
        when(outfitGeneratorService.generateOutfits(eq(userId), any(GenerateOutfitRequestDto.class)))
            .thenReturn(new GenerateOutfitResponseDto(
                Collections.emptyList(),
                List.of("No compatible outfit combinations found matching occasion FORMAL with current wardrobe items.")
            ));

        mockMvc.perform(post("/api/v1/outfits/generate")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occasion\": \"FORMAL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outfits").isEmpty())
            .andExpect(jsonPath("$.warnings[0]").value("No compatible outfit combinations found matching occasion FORMAL with current wardrobe items."));
    }

    @Test
    @DisplayName("Save outfit: POST /api/v1/outfits returns 201 Created with saved outfit ID")
    void testSaveOutfitReturns201() throws Exception {
        UUID savedOutfitId = UUID.randomUUID();
        UUID topId = UUID.randomUUID();
        UUID bottomId = UUID.randomUUID();
        UUID shoeId = UUID.randomUUID();

        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            List.of(topId, bottomId, shoeId),
            Occasion.SMART_CASUAL,
            TemperatureBracket.MILD,
            WeatherCondition.SUNNY,
            "Classic styling",
            90
        );

        SavedOutfitResponseDto responseDto = new SavedOutfitResponseDto(
            savedOutfitId,
            Occasion.SMART_CASUAL,
            TemperatureBracket.MILD,
            WeatherCondition.SUNNY,
            "Classic styling",
            90,
            java.time.Instant.now(),
            List.of()
        );

        when(outfitPersistenceService.saveOutfit(eq(userId), any(SaveOutfitRequestDto.class)))
            .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/outfits")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(savedOutfitId.toString()))
            .andExpect(jsonPath("$.suitabilityScore").value(90));
    }

    @Test
    @DisplayName("Get saved outfits: GET /api/v1/outfits returns 200 OK paginated page")
    void testGetSavedOutfitsReturns200() throws Exception {
        when(outfitPersistenceService.getSavedOutfits(eq(userId), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/outfits")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Get saved outfit by ID: GET /api/v1/outfits/{id} returns 200 OK")
    void testGetSavedOutfitByIdReturns200() throws Exception {
        UUID outfitId = UUID.randomUUID();
        SavedOutfitResponseDto responseDto = new SavedOutfitResponseDto(
            outfitId,
            Occasion.CASUAL,
            null,
            null,
            "Everyday casual",
            85,
            java.time.Instant.now(),
            List.of()
        );

        when(outfitPersistenceService.getSavedOutfitById(eq(userId), eq(outfitId)))
            .thenReturn(responseDto);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/outfits/" + outfitId)
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(outfitId.toString()))
            .andExpect(jsonPath("$.occasion").value("CASUAL"));
    }

    @Test
    @DisplayName("Log outfit wear: POST /api/v1/outfits/{id}/wear returns 200 OK")
    void testLogOutfitWearReturns200() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        java.time.LocalDate today = java.time.LocalDate.now();

        LogOutfitWearResponseDto response = new LogOutfitWearResponseDto(
            logId,
            outfitId,
            today,
            "Outfit wear successfully logged."
        );

        when(outfitPersistenceService.logOutfitWear(eq(userId), eq(outfitId), any()))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/outfits/" + outfitId + "/wear")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wornDate\": \"" + today + "\", \"notes\": \"Great day\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logId").value(logId.toString()))
            .andExpect(jsonPath("$.outfitId").value(outfitId.toString()))
            .andExpect(jsonPath("$.message").value("Outfit wear successfully logged."));
    }

    @Test
    @DisplayName("Get wear history: GET /api/v1/outfits/wear-history returns 200 OK")
    void testGetWearHistoryReturns200() throws Exception {
        when(outfitPersistenceService.getWearHistory(eq(userId), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/outfits/wear-history")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }
}
