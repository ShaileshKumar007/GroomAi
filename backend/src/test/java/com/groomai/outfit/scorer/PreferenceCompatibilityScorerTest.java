package com.groomai.outfit.scorer;

import com.groomai.user.entity.StylePreferences;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenceCompatibilityScorerTest {

    private PreferenceCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new PreferenceCompatibilityScorer();
    }

    private WardrobeItem createItem(Category category, Color color, FormalityLevel formality) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Item " + category)
            .category(category)
            .primaryColor(color)
            .formalityLevel(formality)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Missing style preferences falls back to neutral 7 points")
    void testMissingPreferencesFallback() {
        var top = createItem(Category.TOP, Color.NAVY, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, FormalityLevel.CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, FormalityLevel.CASUAL);

        var result = scorer.score(List.of(top, bottom, shoes), null);
        assertEquals(7, result.score());
        assertTrue(result.rationale().contains("Neutral baseline"));
    }

    @Test
    @DisplayName("Preferred color match awards bonus up to 10 points")
    void testPreferredColorMatch() {
        var preferences = StylePreferences.builder()
            .colorPreferences(List.of("NAVY", "OLIVE"))
            .avoidedColors(List.of("YELLOW"))
            .defaultFormality("CASUAL")
            .build();

        var top = createItem(Category.TOP, Color.NAVY, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, FormalityLevel.CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, FormalityLevel.CASUAL);

        var result = scorer.score(List.of(top, bottom, shoes), preferences);
        // Base 5 + 5 (preferred navy) + 3 (casual default match) = 13 -> clamped to 10
        assertEquals(10, result.score());
        assertTrue(result.rationale().contains("Features colors aligned with your stated preferences"));
    }

    @Test
    @DisplayName("Avoided color incurs deduction")
    void testAvoidedColorDeduction() {
        var preferences = StylePreferences.builder()
            .colorPreferences(List.of("BLACK"))
            .avoidedColors(List.of("YELLOW"))
            .defaultFormality("FORMAL")
            .build();

        var top = createItem(Category.TOP, Color.YELLOW, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, FormalityLevel.CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, FormalityLevel.CASUAL);

        var result = scorer.score(List.of(top, bottom, shoes), preferences);
        // Base 5 - 6 (avoided yellow) = -1 -> clamped to 0
        assertEquals(0, result.score());
        assertTrue(result.rationale().contains("prefer to avoid"));
    }
}
