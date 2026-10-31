package com.groomai.outfit.scorer;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorCompatibilityScorerTest {

    private ColorCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new ColorCompatibilityScorer();
    }

    private WardrobeItem createItem(Category category, Color primaryColor) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Test " + category)
            .category(category)
            .primaryColor(primaryColor)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Neutral high-contrast classic pairings award maximum 30 points")
    void testClassicHighContrastNeutrals() {
        var top = createItem(Category.TOP, Color.NAVY);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN);

        var result = scorer.score(List.of(top, bottom, shoes));
        assertEquals(30, result.score());
        assertTrue(result.rationale().contains("neutral pairing"));
    }

    @Test
    @DisplayName("All other neutral combinations award 28 points")
    void testAllOtherNeutrals() {
        var top = createItem(Category.TOP, Color.GREY);
        var bottom = createItem(Category.BOTTOM, Color.OLIVE);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN);

        var result = scorer.score(List.of(top, bottom, shoes));
        assertEquals(28, result.score());
    }

    @Test
    @DisplayName("Single accent burgundy with navy or grey awards 30 points")
    void testSingleAccentBurgundy() {
        var top = createItem(Category.TOP, Color.BURGUNDY);
        var bottom = createItem(Category.BOTTOM, Color.NAVY);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN);

        var result = scorer.score(List.of(top, bottom, shoes));
        assertEquals(30, result.score());
        assertTrue(result.rationale().contains("burgundy"));
    }

    @Test
    @DisplayName("Single accent green with neutrals awards 29 points")
    void testSingleAccentGreen() {
        var top = createItem(Category.TOP, Color.GREEN);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE);
        var shoes = createItem(Category.FOOTWEAR, Color.WHITE);

        var result = scorer.score(List.of(top, bottom, shoes));
        assertEquals(29, result.score());
    }

    @Test
    @DisplayName("Black trousers paired with brown shoes incurs 6-point clash penalty")
    void testBlackBottomBrownShoesClash() {
        var top = createItem(Category.TOP, Color.WHITE);
        var bottom = createItem(Category.BOTTOM, Color.BLACK);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN);

        var result = scorer.score(List.of(top, bottom, shoes));
        // Base neutral 30 (White + Black) - 6 = 24
        assertEquals(24, result.score());
        assertTrue(result.rationale().contains("noticeable color friction"));
    }

    @Test
    @DisplayName("Two non-coordinated accent colors incurs 10-point penalty")
    void testMultipleAccentsClash() {
        var top = createItem(Category.TOP, Color.BLUE);
        var bottom = createItem(Category.BOTTOM, Color.GREEN);
        var shoes = createItem(Category.FOOTWEAR, Color.WHITE);

        var result = scorer.score(List.of(top, bottom, shoes));
        // Base 20 - 10 penalty = 10
        assertEquals(10, result.score());
    }

    @Test
    @DisplayName("Direct saturated clash (Red + Green) incurs 15-point penalty")
    void testDirectSaturatedClash() {
        var top = createItem(Category.TOP, Color.RED);
        var bottom = createItem(Category.BOTTOM, Color.GREEN);
        var shoes = createItem(Category.FOOTWEAR, Color.WHITE);

        var result = scorer.score(List.of(top, bottom, shoes));
        // 20 - 10 (two accents) - 15 (red+green clash) = -5 -> clamped to 0
        assertEquals(0, result.score());
    }
}
