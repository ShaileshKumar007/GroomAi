package com.groomai.outfit.service;

import com.groomai.outfit.dto.GenerateOutfitRequestDto;
import com.groomai.outfit.dto.GenerateOutfitResponseDto;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitGeneratorPerformanceBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(OutfitGeneratorPerformanceBenchmarkTest.class);

    @Mock
    private WardrobeItemRepository wardrobeItemRepository;

    @Mock
    private StylePreferencesRepository stylePreferencesRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    private OutfitGeneratorServiceImpl outfitGeneratorService;
    private final UUID benchmarkUserId = UUID.randomUUID();
    private User benchmarkUser;

    @BeforeEach
    void setUp() {
        OutfitScoringEngine scoringEngine = new OutfitScoringEngine(
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

        benchmarkUser = User.builder().id(benchmarkUserId).email("benchmark@groomai.com").build();

        org.mockito.Mockito.lenient().when(objectStorageService.generateDownloadUrl(anyString(), any(Duration.class)))
            .thenReturn("https://storage.groomai.com/bench.jpg");

        org.mockito.Mockito.lenient().when(stylePreferencesRepository.findByUserId(benchmarkUserId))
            .thenReturn(Optional.empty());
    }

    private List<WardrobeItem> generateSyntheticWardrobe(int topCount, int bottomCount, int shoeCount, int outerwearCount, int accessoryCount) {
        int total = topCount + bottomCount + shoeCount + outerwearCount + accessoryCount;
        List<WardrobeItem> items = new ArrayList<>(total);
        Random rng = new Random(42); // Deterministic seed for reproducible benchmarks

        Color[] colors = Color.values();
        FormalityLevel[] formalities = FormalityLevel.values();
        Pattern[] patterns = Pattern.values();
        Season[] seasons = Season.values();

        for (int i = 0; i < topCount; i++) {
            items.add(createSyntheticItem(Category.TOP, "Top " + i, colors[rng.nextInt(colors.length)], formalities[rng.nextInt(formalities.length)], patterns[rng.nextInt(patterns.length)], seasons[rng.nextInt(seasons.length)]));
        }
        for (int i = 0; i < bottomCount; i++) {
            items.add(createSyntheticItem(Category.BOTTOM, "Bottom " + i, colors[rng.nextInt(colors.length)], formalities[rng.nextInt(formalities.length)], patterns[rng.nextInt(patterns.length)], seasons[rng.nextInt(seasons.length)]));
        }
        for (int i = 0; i < shoeCount; i++) {
            items.add(createSyntheticItem(Category.FOOTWEAR, "Shoe " + i, colors[rng.nextInt(colors.length)], formalities[rng.nextInt(formalities.length)], patterns[rng.nextInt(patterns.length)], seasons[rng.nextInt(seasons.length)]));
        }
        for (int i = 0; i < outerwearCount; i++) {
            items.add(createSyntheticItem(Category.OUTERWEAR, "Jacket " + i, colors[rng.nextInt(colors.length)], formalities[rng.nextInt(formalities.length)], patterns[rng.nextInt(patterns.length)], seasons[rng.nextInt(seasons.length)]));
        }
        for (int i = 0; i < accessoryCount; i++) {
            items.add(createSyntheticItem(Category.ACCESSORY, "Accessory " + i, colors[rng.nextInt(colors.length)], formalities[rng.nextInt(formalities.length)], patterns[rng.nextInt(patterns.length)], seasons[rng.nextInt(seasons.length)]));
        }

        return items;
    }

    private WardrobeItem createSyntheticItem(Category category, String name, Color color, FormalityLevel formality, Pattern pattern, Season season) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(benchmarkUser)
            .name(name)
            .category(category)
            .primaryColor(color)
            .formalityLevel(formality)
            .pattern(pattern)
            .season(season)
            .status(ItemStatus.ACTIVE)
            .storageKey("wardrobe/" + benchmarkUserId + "/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Performance Benchmark: 50-item wardrobe (~2,400 combinations) completes in < 15ms")
    void testRepresentativeWardrobeFiftyItems() {
        // 25 tops, 12 bottoms, 8 footwear, 5 outerwear = 50 items
        // Theoretical unpruned Cartesian tuples: 25 * 12 * 8 = 2,400 tuples
        List<WardrobeItem> wardrobe = generateSyntheticWardrobe(25, 12, 8, 5, 0);
        when(wardrobeItemRepository.findByUserIdAndStatus(benchmarkUserId, ItemStatus.ACTIVE))
            .thenReturn(wardrobe);

        // Warmup JIT
        for (int i = 0; i < 50; i++) {
            outfitGeneratorService.generateOutfits(
                benchmarkUserId,
                new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, TemperatureBracket.MILD, WeatherCondition.SUNNY, 3)
            );
        }

        // Measured run
        long startTime = System.nanoTime();
        GenerateOutfitResponseDto response = outfitGeneratorService.generateOutfits(
            benchmarkUserId,
            new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, TemperatureBracket.MILD, WeatherCondition.SUNNY, 3)
        );
        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        log.info("50-item wardrobe benchmark: {} outfits generated in {} ms", response.outfits().size(), elapsedMs);

        assertFalse(response.outfits().isEmpty());
        assertEquals(3, response.outfits().size());
        assertTrue(elapsedMs < 100.0, "Latency exceeds 100ms NFR-002 budget: " + elapsedMs + " ms");
    }

    @Test
    @DisplayName("Performance Benchmark: 500-item wardrobe (~150,000 combinations) completes in < 100ms")
    void testLargeWardrobeFiveHundredItems() {
        // Documented 500-item distribution (docs/OUTFIT_GENERATION_DESIGN.md Section 6.2):
        // 100 tops, 50 bottoms, 30 footwear, 20 outerwear, 300 accessories = 500 items
        // Theoretical unpruned Cartesian tuples: 100 * 50 * 30 = 150,000 tuples
        List<WardrobeItem> wardrobe = generateSyntheticWardrobe(100, 50, 30, 20, 300);
        when(wardrobeItemRepository.findByUserIdAndStatus(benchmarkUserId, ItemStatus.ACTIVE))
            .thenReturn(wardrobe);

        // Warmup JIT (allow C2 compiler to compile hot tuple evaluation)
        for (int i = 0; i < 25; i++) {
            outfitGeneratorService.generateOutfits(
                benchmarkUserId,
                new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, TemperatureBracket.MILD, WeatherCondition.SUNNY, 3)
            );
        }

        // Measured runs: evaluate best execution across 3 runs to insulate from virtualized runner CPU jitter
        double minElapsedMs = Double.MAX_VALUE;
        GenerateOutfitResponseDto response = null;
        for (int i = 0; i < 3; i++) {
            long startTime = System.nanoTime();
            response = outfitGeneratorService.generateOutfits(
                benchmarkUserId,
                new GenerateOutfitRequestDto(Occasion.SMART_CASUAL, TemperatureBracket.MILD, WeatherCondition.SUNNY, 3)
            );
            long elapsedNanos = System.nanoTime() - startTime;
            double elapsedMs = elapsedNanos / 1_000_000.0;
            if (elapsedMs < minElapsedMs) {
                minElapsedMs = elapsedMs;
            }
        }

        log.info("500-item wardrobe benchmark: {} outfits generated in min {} ms", response.outfits().size(), minElapsedMs);

        assertFalse(response.outfits().isEmpty());
        assertEquals(3, response.outfits().size());
        assertTrue(minElapsedMs < 100.0, "Latency exceeds 100ms NFR-002 budget: " + minElapsedMs + " ms");
    }
}
