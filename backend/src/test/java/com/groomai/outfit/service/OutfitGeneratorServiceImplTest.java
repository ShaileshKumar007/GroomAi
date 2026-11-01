package com.groomai.outfit.service;

import com.groomai.outfit.dto.GenerateOutfitRequestDto;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.exception.InsufficientWardrobeItemsException;
import com.groomai.outfit.scorer.ColorCompatibilityScorer;
import com.groomai.outfit.scorer.FormalityCompatibilityScorer;
import com.groomai.outfit.scorer.OutfitScoringEngine;
import com.groomai.outfit.scorer.PatternCompatibilityScorer;
import com.groomai.outfit.scorer.PreferenceCompatibilityScorer;
import com.groomai.outfit.scorer.SeasonWeatherCompatibilityScorer;
import com.groomai.outfit.scorer.WearHistoryPenaltyScorer;
import com.groomai.storage.ObjectStorageService;
import com.groomai.user.entity.User;
import com.groomai.user.repository.StylePreferencesRepository;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitGeneratorServiceImplTest {

    @Mock
    private WardrobeItemRepository wardrobeItemRepository;

    @Mock
    private StylePreferencesRepository stylePreferencesRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    private OutfitScoringEngine scoringEngine;
    private OutfitGeneratorServiceImpl outfitGeneratorService;

    private final UUID testUserId = UUID.randomUUID();
    private User testUser;

    @BeforeEach
    void setUp() {
        scoringEngine = new OutfitScoringEngine(
            new ColorCompatibilityScorer(),
            new FormalityCompatibilityScorer(),
            new SeasonWeatherCompatibilityScorer(),
            new PatternCompatibilityScorer(),
            new PreferenceCompatibilityScorer(),
            new WearHistoryPenaltyScorer()
        );

        outfitGeneratorService = new OutfitGeneratorServiceImpl(
            wardrobeItemRepository,
            stylePreferencesRepository,
            objectStorageService,
            scoringEngine
        );

        testUser = User.builder().id(testUserId).email("user@groomai.com").build();

        // Lenient mock for download URLs
        org.mockito.Mockito.lenient().when(objectStorageService.generateDownloadUrl(anyString(), any(Duration.class)))
            .thenAnswer(invocation -> "https://storage.groomai.com/" + invocation.getArgument(0));

        org.mockito.Mockito.lenient().when(stylePreferencesRepository.findByUserId(testUserId))
            .thenReturn(Optional.empty());
    }

    private WardrobeItem createItem(
        Category category,
        String name,
        Color primaryColor,
        FormalityLevel formality,
        Pattern pattern,
        Season season,
        ItemStatus status
    ) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .name(name)
            .category(category)
            .primaryColor(primaryColor)
            .formalityLevel(formality)
            .pattern(pattern)
            .season(season)
            .status(status)
            .storageKey("wardrobe/" + testUserId + "/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Under-threshold wardrobe (missing Bottom and Footwear) throws InsufficientWardrobeItemsException with missing categories")
    void testMissingCategoriesThrowsException() {
        var top = createItem(Category.TOP, "Navy Oxford", Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        when(wardrobeItemRepository.findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE))
            .thenReturn(List.of(top));

        var ex = assertThrows(InsufficientWardrobeItemsException.class, () ->
            outfitGeneratorService.generateOutfits(testUserId, new GenerateOutfitRequestDto(null, null, null, 3))
        );

        assertTrue(ex.getMissingCategories().contains("BOTTOM"));
        assertTrue(ex.getMissingCategories().contains("FOOTWEAR"));
        assertFalse(ex.getMissingCategories().contains("TOP"));
    }

    @Test
    @DisplayName("Active-item filtering: Archived garments are excluded from recommendation generation")
    void testArchivedItemsExcluded() {
        var top = createItem(Category.TOP, "Navy Oxford", Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var bottom = createItem(Category.BOTTOM, "Chinos", Color.BEIGE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var shoes = createItem(Category.FOOTWEAR, "Loafers", Color.BROWN, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);

        when(wardrobeItemRepository.findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE))
            .thenReturn(List.of(top, bottom, shoes));

        var response = outfitGeneratorService.generateOutfits(testUserId, new GenerateOutfitRequestDto(null, null, null, 3));

        verify(wardrobeItemRepository).findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE);
        assertEquals(1, response.outfits().size());
    }

    @Test
    @DisplayName("Generates correct 3-piece core outfit (Top, Bottom, Footwear) with honest explanation")
    void testCoreThreePieceOutfitGeneration() {
        var top = createItem(Category.TOP, "Navy Oxford", Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var bottom = createItem(Category.BOTTOM, "Beige Chinos", Color.BEIGE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var shoes = createItem(Category.FOOTWEAR, "Brown Loafers", Color.BROWN, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);

        when(wardrobeItemRepository.findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE))
            .thenReturn(List.of(top, bottom, shoes));

        var response = outfitGeneratorService.generateOutfits(
            testUserId,
            new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, null, null, 3)
        );

        assertEquals(1, response.outfits().size());
        var outfit = response.outfits().get(0);
        assertEquals(3, outfit.items().size());
        assertEquals("TOP", outfit.items().get(0).role());
        assertEquals("BOTTOM", outfit.items().get(1).role());
        assertEquals("FOOTWEAR", outfit.items().get(2).role());
        assertTrue(outfit.suitabilityScore() >= 90);
        assertTrue(outfit.stylingRationale().contains("Weather context was not supplied; evaluated based on garment seasonality only."));
    }

    @Test
    @DisplayName("Outerwear anti-duplication: Cold weather replaces unlayered candidate with layered candidate, never duplicating")
    void testOuterwearAntiDuplication() {
        var top = createItem(Category.TOP, "Navy Oxford", Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var bottom = createItem(Category.BOTTOM, "Beige Chinos", Color.BEIGE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var shoes = createItem(Category.FOOTWEAR, "Brown Loafers", Color.BROWN, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var jacket = createItem(Category.OUTERWEAR, "Navy Wool Overcoat", Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.WINTER, ItemStatus.ACTIVE);

        when(wardrobeItemRepository.findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE))
            .thenReturn(List.of(top, bottom, shoes, jacket));

        var response = outfitGeneratorService.generateOutfits(
            testUserId,
            new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, TemperatureBracket.COLD, null, 3)
        );

        // Anti-duplication check: Should return ONLY 1 outfit (the layered version), NOT both layered and unlayered!
        assertEquals(1, response.outfits().size());
        var outfit = response.outfits().get(0);
        assertEquals(4, outfit.items().size());
        assertEquals("OUTERWEAR", outfit.items().get(3).role());
    }

    @Test
    @DisplayName("No compatible combinations returns HTTP 200 OK with empty outfits and descriptive warning")
    void testNoCompatibleCombinationsReturnsWarning() {
        // Casual items only, but user requests FORMAL occasion
        var top = createItem(Category.TOP, "Graphic Tee", Color.WHITE, FormalityLevel.CASUAL, Pattern.GRAPHIC, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var bottom = createItem(Category.BOTTOM, "Sweatpants", Color.GREY, FormalityLevel.CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var shoes = createItem(Category.FOOTWEAR, "Running Shoes", Color.BLACK, FormalityLevel.CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);

        when(wardrobeItemRepository.findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE))
            .thenReturn(List.of(top, bottom, shoes));

        var response = outfitGeneratorService.generateOutfits(
            testUserId,
            new GenerateOutfitRequestDto(Occasion.FORMAL, null, null, 3)
        );

        assertTrue(response.outfits().isEmpty());
        assertFalse(response.warnings().isEmpty());
        assertTrue(response.warnings().get(0).contains("No compatible outfit combinations found matching occasion FORMAL"));
    }

    @Test
    @DisplayName("Respects requested limit parameter (e.g. limit = 2 returns at most 2 outfits)")
    void testLimitRespected() {
        var top1 = createItem(Category.TOP, "Top 1", Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var top2 = createItem(Category.TOP, "Top 2", Color.WHITE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var top3 = createItem(Category.TOP, "Top 3", Color.GREY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);

        var bottom = createItem(Category.BOTTOM, "Bottom", Color.BEIGE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var shoes = createItem(Category.FOOTWEAR, "Shoes", Color.BROWN, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);

        when(wardrobeItemRepository.findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE))
            .thenReturn(List.of(top1, top2, top3, bottom, shoes));

        var response = outfitGeneratorService.generateOutfits(
            testUserId,
            new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, null, null, 2)
        );

        assertEquals(2, response.outfits().size());
    }

    @Test
    @DisplayName("Deterministic tie-breaking cascade ensures 1,000 runs produce identical ranking")
    void testRepeatedRunRankingStability() {
        var top1 = createItem(Category.TOP, "Navy Oxford", Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var top2 = createItem(Category.TOP, "White Oxford", Color.WHITE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var bottom = createItem(Category.BOTTOM, "Beige Chinos", Color.BEIGE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);
        var shoes = createItem(Category.FOOTWEAR, "Brown Loafers", Color.BROWN, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON, ItemStatus.ACTIVE);

        when(wardrobeItemRepository.findByUserIdAndStatus(testUserId, ItemStatus.ACTIVE))
            .thenReturn(List.of(top1, top2, bottom, shoes));

        var firstRun = outfitGeneratorService.generateOutfits(
            testUserId,
            new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, null, null, 3)
        );

        UUID firstRunTopOutfitItemId = firstRun.outfits().get(0).items().get(0).id();

        for (int i = 0; i < 1000; i++) {
            var subsequentRun = outfitGeneratorService.generateOutfits(
                testUserId,
                new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, null, null, 3)
            );
            assertEquals(firstRunTopOutfitItemId, subsequentRun.outfits().get(0).items().get(0).id());
        }
    }
}
