package com.groomai.outfit.scorer;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitScoringEngineTest {

    private OutfitScoringEngine scoringEngine;

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
    }

    private WardrobeItem createItem(
        Category category,
        Color color,
        FormalityLevel formality,
        Pattern pattern,
        Season season
    ) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Item " + category)
            .category(category)
            .primaryColor(color)
            .formalityLevel(formality)
            .pattern(pattern)
            .season(season)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Arithmetic check: Total suitability score strictly equals sub-scores sum minus wear penalty")
    void testArithmeticSubScoresMatchTotal() {
        var top = createItem(Category.TOP, Color.NAVY, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, FormalityLevel.SMART_CASUAL, Pattern.SOLID, Season.ALL_SEASON);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var resultOpt = scoringEngine.scoreOutfit(
            candidate,
            Occasion.SMART_CASUAL,
            null,
            null,
            null,
            Collections.emptyMap()
        );

        assertTrue(resultOpt.isPresent());
        var scored = resultOpt.get();
        var bd = scored.scoreBreakdown();

        // Color = 30 (Navy + Beige + Brown classic contrast)
        // Formality = 25 (Smart Casual target match)
        // Season = 20 (All-season, weather absent)
        // Pattern = 15 (Solid dominance)
        // Preference = 7 (Missing preference baseline)
        // Wear = 0 (Empty wear history)
        // Base = 30 + 25 + 20 + 15 + 7 = 97
        int expectedTotal = bd.computeTotal();
        assertEquals(expectedTotal, scored.suitabilityScore());
        assertEquals(
            bd.colorHarmony() + bd.formalityAlignment() + bd.seasonWeatherCompatibility()
                + bd.patternRestraint() + bd.preferenceMatch() - bd.wearPenalty(),
            scored.suitabilityScore()
        );
    }

    @Test
    @DisplayName("Irreconcilable formality clash returns empty optional (discarded combination)")
    void testFormalityClashReturnsEmpty() {
        var top = createItem(Category.TOP, Color.NAVY, FormalityLevel.CASUAL, Pattern.SOLID, Season.ALL_SEASON);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, FormalityLevel.CASUAL, Pattern.SOLID, Season.ALL_SEASON);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, FormalityLevel.FORMAL, Pattern.SOLID, Season.ALL_SEASON);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var resultOpt = scoringEngine.scoreOutfit(
            candidate,
            null,
            null,
            null,
            null,
            Collections.emptyMap()
        );

        assertFalse(resultOpt.isPresent());
    }
}
