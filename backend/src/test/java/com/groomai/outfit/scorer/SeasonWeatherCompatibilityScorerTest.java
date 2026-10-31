package com.groomai.outfit.scorer;

import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Season;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonWeatherCompatibilityScorerTest {

    private SeasonWeatherCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new SeasonWeatherCompatibilityScorer();
    }

    private WardrobeItem createItem(Category category, Season season) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Item " + category)
            .category(category)
            .season(season)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Direct seasonal clash (Summer + Winter) results in hard rejection")
    void testSummerWinterClash() {
        var top = createItem(Category.TOP, Season.SUMMER);
        var bottom = createItem(Category.BOTTOM, Season.WINTER);
        var shoes = createItem(Category.FOOTWEAR, Season.ALL_SEASON);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, null, null);

        assertTrue(result.rejected());
        assertEquals(0, result.score());
    }

    @Test
    @DisplayName("Weather absent: All-season items award 20 points with honest disclaimer")
    void testWeatherAbsentAllSeason() {
        var top = createItem(Category.TOP, Season.ALL_SEASON);
        var bottom = createItem(Category.BOTTOM, Season.ALL_SEASON);
        var shoes = createItem(Category.FOOTWEAR, Season.ALL_SEASON);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, null, null);

        assertFalse(result.rejected());
        assertEquals(20, result.score());
        assertTrue(result.rationale().contains("Weather context was not supplied; evaluated based on garment seasonality only."));
    }

    @Test
    @DisplayName("Weather absent: Adjacent seasons award 18 points with honest disclaimer")
    void testWeatherAbsentAdjacentSeasons() {
        var top = createItem(Category.TOP, Season.SPRING);
        var bottom = createItem(Category.BOTTOM, Season.SUMMER);
        var shoes = createItem(Category.FOOTWEAR, Season.ALL_SEASON);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, null, null);

        assertFalse(result.rejected());
        assertEquals(18, result.score());
        assertTrue(result.rationale().contains("Weather context was not supplied; evaluated based on garment seasonality only."));
    }

    @Test
    @DisplayName("Weather present: Hot weather rejects winter items")
    void testHotWeatherRejectsWinterItem() {
        var top = createItem(Category.TOP, Season.ALL_SEASON);
        var bottom = createItem(Category.BOTTOM, Season.WINTER);
        var shoes = createItem(Category.FOOTWEAR, Season.ALL_SEASON);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, TemperatureBracket.HOT, WeatherCondition.SUNNY);

        assertTrue(result.rejected());
        assertEquals(0, result.score());
    }

    @Test
    @DisplayName("Weather present: Cold weather penalizes outfit without outerwear by 6 points")
    void testColdWeatherUnlayeredDeduction() {
        var top = createItem(Category.TOP, Season.WINTER);
        var bottom = createItem(Category.BOTTOM, Season.FALL);
        var shoes = createItem(Category.FOOTWEAR, Season.ALL_SEASON);

        var candidate = new CandidateOutfit(top, bottom, shoes, null);
        var result = scorer.score(candidate, TemperatureBracket.COLD, null);

        assertFalse(result.rejected());
        assertEquals(14, result.score());
        assertTrue(result.rationale().contains("Cold temperatures call for an outerwear layer"));
    }

    @Test
    @DisplayName("Weather present: Cold weather with outerwear awards 20 points")
    void testColdWeatherLayeredFullScore() {
        var top = createItem(Category.TOP, Season.WINTER);
        var bottom = createItem(Category.BOTTOM, Season.FALL);
        var shoes = createItem(Category.FOOTWEAR, Season.ALL_SEASON);
        var outerwear = createItem(Category.OUTERWEAR, Season.WINTER);

        var candidate = new CandidateOutfit(top, bottom, shoes, outerwear);
        var result = scorer.score(candidate, TemperatureBracket.COLD, null);

        assertFalse(result.rejected());
        assertEquals(20, result.score());
        assertTrue(result.rationale().contains("optimal cold weather protection"));
    }
}
