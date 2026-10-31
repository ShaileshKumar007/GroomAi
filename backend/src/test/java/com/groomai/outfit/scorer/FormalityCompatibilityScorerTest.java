package com.groomai.outfit.scorer;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.FormalityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormalityCompatibilityScorerTest {

    private FormalityCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new FormalityCompatibilityScorer();
    }

    private WardrobeItem createItem(Category category, FormalityLevel formality) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Item " + category)
            .category(category)
            .formalityLevel(formality)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Uniform formality tier awards maximum 25 points")
    void testUniformFormalityDeltaZero() {
        var top = createItem(Category.TOP, FormalityLevel.SMART_CASUAL);
        var bottom = createItem(Category.BOTTOM, FormalityLevel.SMART_CASUAL);
        var shoes = createItem(Category.FOOTWEAR, FormalityLevel.SMART_CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, Occasion.SMART_CASUAL);

        assertFalse(result.rejected());
        assertEquals(25, result.score());
    }

    @Test
    @DisplayName("Adjacent formality tiers (delta = 1) awards 20 points")
    void testAdjacentFormalityDeltaOne() {
        var top = createItem(Category.TOP, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, FormalityLevel.SMART_CASUAL);
        var shoes = createItem(Category.FOOTWEAR, FormalityLevel.CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, Occasion.CASUAL);

        assertFalse(result.rejected());
        assertEquals(20, result.score());
    }

    @Test
    @DisplayName("Noticeable formality gap (delta = 2) awards 8 points when no occasion constraint violated")
    void testNoticeableFormalityDeltaTwo() {
        var top = createItem(Category.TOP, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, FormalityLevel.BUSINESS_CASUAL);
        var shoes = createItem(Category.FOOTWEAR, FormalityLevel.SMART_CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        // With no occasion specified, purely delta check
        var result = scorer.score(candidate, null);

        assertFalse(result.rejected());
        assertEquals(8, result.score());
    }

    @Test
    @DisplayName("Severe formality clash (delta >= 3) results in hard rejection (score 0)")
    void testSevereFormalityClashDeltaThree() {
        var top = createItem(Category.TOP, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, FormalityLevel.CASUAL);
        var shoes = createItem(Category.FOOTWEAR, FormalityLevel.FORMAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, null);

        assertTrue(result.rejected());
        assertEquals(0, result.score());
    }

    @Test
    @DisplayName("Garment violating occasion permitted bounds results in hard rejection")
    void testOccasionBoundsViolation() {
        // Business casual occasion permits only SMART_CASUAL (2) and BUSINESS_CASUAL (3)
        // Casual gym shorts (1) violate business casual bounds
        var top = createItem(Category.TOP, FormalityLevel.BUSINESS_CASUAL);
        var bottom = createItem(Category.BOTTOM, FormalityLevel.CASUAL);
        var shoes = createItem(Category.FOOTWEAR, FormalityLevel.BUSINESS_CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, Occasion.BUSINESS_CASUAL);

        assertTrue(result.rejected());
        assertEquals(0, result.score());
    }
}
