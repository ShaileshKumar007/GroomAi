package com.groomai.outfit.scorer;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternCompatibilityScorerTest {

    private PatternCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new PatternCompatibilityScorer();
    }

    private WardrobeItem createItem(Category category, Pattern pattern) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Item " + category)
            .category(category)
            .pattern(pattern)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("All solid items award maximum 15 points")
    void testAllSolidItems() {
        var top = createItem(Category.TOP, Pattern.SOLID);
        var bottom = createItem(Category.BOTTOM, Pattern.SOLID);
        var shoes = createItem(Category.FOOTWEAR, Pattern.SOLID);

        var result = scorer.score(List.of(top, bottom, shoes));
        assertEquals(15, result.score());
        assertTrue(result.rationale().contains("distraction-free visual balance"));
    }

    @Test
    @DisplayName("Single focal pattern anchored by solids awards 14 points")
    void testRuleOfOnePattern() {
        var top = createItem(Category.TOP, Pattern.STRIPED);
        var bottom = createItem(Category.BOTTOM, Pattern.SOLID);
        var shoes = createItem(Category.FOOTWEAR, Pattern.SOLID);

        var result = scorer.score(List.of(top, bottom, shoes));
        assertEquals(14, result.score());
        assertTrue(result.rationale().contains("striped"));
    }

    @Test
    @DisplayName("Pattern clash (Floral + Checked) heavily penalizes pattern score to 3")
    void testPatternClash() {
        var top = createItem(Category.TOP, Pattern.FLORAL);
        var bottom = createItem(Category.BOTTOM, Pattern.CHECKED);
        var shoes = createItem(Category.FOOTWEAR, Pattern.SOLID);

        var result = scorer.score(List.of(top, bottom, shoes));
        assertEquals(3, result.score());
        assertTrue(result.rationale().contains("visual conflict and clutter"));
    }
}
