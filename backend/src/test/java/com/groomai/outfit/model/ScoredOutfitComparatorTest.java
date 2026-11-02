package com.groomai.outfit.model;

import com.groomai.outfit.dto.ScoreBreakdownDto;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoredOutfitComparatorTest {

    private WardrobeItem createItem(UUID id, Category category) {
        return WardrobeItem.builder()
            .id(id)
            .name("Item " + category)
            .category(category)
            .primaryColor(Color.NAVY)
            .formalityLevel(FormalityLevel.CASUAL)
            .pattern(Pattern.SOLID)
            .season(Season.ALL_SEASON)
            .storageKey("key/" + id)
            .build();
    }

    private ScoredOutfit buildScoredOutfit(
        CandidateOutfit candidate,
        int suitabilityScore,
        int colorHarmony,
        int formalityDelta,
        int neverWornCount,
        double normalizedDaysSinceLastWorn,
        boolean hasExpandRotationGoal
    ) {
        ScoreBreakdownDto bd = new ScoreBreakdownDto(colorHarmony, 20, 20, 10, 7, 0);
        return new ScoredOutfit(
            UUID.randomUUID(),
            candidate,
            suitabilityScore,
            bd,
            "Rationale",
            (int) normalizedDaysSinceLastWorn * 3,
            formalityDelta,
            neverWornCount,
            normalizedDaysSinceLastWorn,
            hasExpandRotationGoal
        );
    }

    @Test
    @DisplayName("Tier 1: Suitability score strictly dominates ranking (higher score comes first)")
    void testSuitabilityScoreDominates() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        var c1 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));
        var c2 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));

        // Outfit A has suitability score 90, Outfit B has 85
        var outfitA = buildScoredOutfit(c1, 90, 25, 1, 0, 5.0, false);
        var outfitB = buildScoredOutfit(c2, 85, 30, 0, 3, 30.0, true);

        List<ScoredOutfit> list = new ArrayList<>(List.of(outfitB, outfitA));
        list.sort(ScoredOutfit.RANKING_COMPARATOR);

        assertEquals(outfitA, list.get(0));
        assertEquals(outfitB, list.get(1));
    }

    @Test
    @DisplayName("Tier 2: Never-worn garment count breaks ties when suitability scores are equal")
    void testNeverWornCountTieBreaker() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        var c1 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));
        var c2 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));

        // Same score 90; Outfit A has 2 never-worn garments, Outfit B has 0
        var outfitA = buildScoredOutfit(c1, 90, 28, 0, 2, 0.0, false);
        var outfitB = buildScoredOutfit(c2, 90, 30, 0, 0, 20.0, false);

        List<ScoredOutfit> list = new ArrayList<>(List.of(outfitB, outfitA));
        list.sort(ScoredOutfit.RANKING_COMPARATOR);

        assertEquals(outfitA, list.get(0));
        assertEquals(outfitB, list.get(1));
    }

    @Test
    @DisplayName("EXPAND_ROTATION: Prioritizes recency ahead of color harmony and formality delta")
    void testExpandRotationPrioritizesRecency() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        var c1 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));
        var c2 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));

        // Both have EXPAND_ROTATION, same score 90, same never-worn count 0.
        // Outfit A has higher normalized days since last wear (35.0 vs 10.0), but lower color harmony (25 vs 30).
        var outfitA = buildScoredOutfit(c1, 90, 25, 1, 0, 35.0, true);
        var outfitB = buildScoredOutfit(c2, 90, 30, 0, 0, 10.0, true);

        List<ScoredOutfit> list = new ArrayList<>(List.of(outfitB, outfitA));
        list.sort(ScoredOutfit.RANKING_COMPARATOR);

        // Outfit A should win because EXPAND_ROTATION ranks rested clothes before color harmony
        assertEquals(outfitA, list.get(0));
        assertEquals(outfitB, list.get(1));
    }

    @Test
    @DisplayName("Standard Ranking (without EXPAND_ROTATION): Color harmony and formality delta precede recency")
    void testStandardRankingPrioritizesHarmonyOverRecency() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        var c1 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));
        var c2 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));

        // Neither has EXPAND_ROTATION, same score 90, same never-worn count 0.
        // Outfit A has higher color harmony (30 vs 25), but lower recency (10.0 vs 35.0).
        var outfitA = buildScoredOutfit(c1, 90, 30, 0, 0, 10.0, false);
        var outfitB = buildScoredOutfit(c2, 90, 25, 0, 0, 35.0, false);

        List<ScoredOutfit> list = new ArrayList<>(List.of(outfitB, outfitA));
        list.sort(ScoredOutfit.RANKING_COMPARATOR);

        // Outfit A wins because standard ranking prioritizes color harmony over wear recency
        assertEquals(outfitA, list.get(0));
        assertEquals(outfitB, list.get(1));
    }

    @Test
    @DisplayName("Normalized days since last wear correctly balances 3-item vs 4-item combinations")
    void testNormalizedRecencyAverages() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID u4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        var c3Item = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));
        var c4Item = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR), createItem(u4, Category.OUTERWEAR));

        // 3-item outfit with items worn 30, 30, 30 days ago -> average = 30.0 days.
        // 4-item outfit with items worn 20, 20, 20, 20 days ago -> raw sum = 80, but average = 20.0 days.
        var outfit3 = buildScoredOutfit(c3Item, 90, 30, 0, 0, 30.0, true);
        var outfit4 = buildScoredOutfit(c4Item, 90, 30, 0, 0, 20.0, true);

        List<ScoredOutfit> list = new ArrayList<>(List.of(outfit4, outfit3));
        list.sort(ScoredOutfit.RANKING_COMPARATOR);

        // outfit3 has higher normalized days (30.0 vs 20.0) so it ranks first despite lower raw sum
        assertEquals(outfit3, list.get(0));
        assertEquals(outfit4, list.get(1));
    }

    @Test
    @DisplayName("Stable UUID ordering across all slots: top, bottom, footwear, outerwear, and accessory")
    void testStableUuidOrderingAcrossAllSlots() {
        UUID topA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID topB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID bottom = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID footwear = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID outerA = UUID.fromString("00000000-0000-0000-0000-000000000030");
        UUID outerB = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID accA = UUID.fromString("00000000-0000-0000-0000-000000000040");
        UUID accB = UUID.fromString("00000000-0000-0000-0000-000000000041");

        // 1. Difference in TOP slot
        var cTopA = new CandidateOutfit(createItem(topA, Category.TOP), createItem(bottom, Category.BOTTOM), createItem(footwear, Category.FOOTWEAR));
        var cTopB = new CandidateOutfit(createItem(topB, Category.TOP), createItem(bottom, Category.BOTTOM), createItem(footwear, Category.FOOTWEAR));
        var oTopA = buildScoredOutfit(cTopA, 90, 30, 0, 0, 10.0, false);
        var oTopB = buildScoredOutfit(cTopB, 90, 30, 0, 0, 10.0, false);
        assertTrue(ScoredOutfit.RANKING_COMPARATOR.compare(oTopA, oTopB) < 0);

        // 2. Difference in OUTERWEAR slot
        var cOuterA = new CandidateOutfit(createItem(topA, Category.TOP), createItem(bottom, Category.BOTTOM), createItem(footwear, Category.FOOTWEAR), createItem(outerA, Category.OUTERWEAR));
        var cOuterB = new CandidateOutfit(createItem(topA, Category.TOP), createItem(bottom, Category.BOTTOM), createItem(footwear, Category.FOOTWEAR), createItem(outerB, Category.OUTERWEAR));
        var oOuterA = buildScoredOutfit(cOuterA, 90, 30, 0, 0, 10.0, false);
        var oOuterB = buildScoredOutfit(cOuterB, 90, 30, 0, 0, 10.0, false);
        assertTrue(ScoredOutfit.RANKING_COMPARATOR.compare(oOuterA, oOuterB) < 0);

        // 3. Difference in ACCESSORY slot
        var cAccA = new CandidateOutfit(createItem(topA, Category.TOP), createItem(bottom, Category.BOTTOM), createItem(footwear, Category.FOOTWEAR), null, createItem(accA, Category.ACCESSORY));
        var cAccB = new CandidateOutfit(createItem(topA, Category.TOP), createItem(bottom, Category.BOTTOM), createItem(footwear, Category.FOOTWEAR), null, createItem(accB, Category.ACCESSORY));
        var oAccA = buildScoredOutfit(cAccA, 90, 30, 0, 0, 10.0, false);
        var oAccB = buildScoredOutfit(cAccB, 90, 30, 0, 0, 10.0, false);
        assertTrue(ScoredOutfit.RANKING_COMPARATOR.compare(oAccA, oAccB) < 0);
    }

    @Test
    @DisplayName("EXPAND_ROTATION is a profile-level flag: all candidates in a single request share identical flag state and consistent ordering")
    void testConsistentComparatorBehaviorAcrossCandidatesWithProfileFlag() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID u4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        var c1 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u3, Category.FOOTWEAR));
        var c2 = new CandidateOutfit(createItem(u1, Category.TOP), createItem(u2, Category.BOTTOM), createItem(u4, Category.FOOTWEAR));

        // When user profile has EXPAND_ROTATION, all candidates have hasExpandRotationGoal = true
        var outfit1 = buildScoredOutfit(c1, 88, 30, 0, 1, 25.0, true);
        var outfit2 = buildScoredOutfit(c2, 88, 25, 0, 1, 40.0, true);

        // In EXPAND_ROTATION ranking, tier 3a (normalized days since last worn: 40.0 vs 25.0) takes precedence over tier 4a (color harmony: 30 vs 25)
        assertTrue(ScoredOutfit.RANKING_COMPARATOR.compare(outfit2, outfit1) < 0);
        assertEquals(-ScoredOutfit.RANKING_COMPARATOR.compare(outfit2, outfit1), ScoredOutfit.RANKING_COMPARATOR.compare(outfit1, outfit2));

        // When user profile does NOT have EXPAND_ROTATION, all candidates have hasExpandRotationGoal = false
        var stdOutfit1 = buildScoredOutfit(c1, 88, 30, 0, 1, 25.0, false);
        var stdOutfit2 = buildScoredOutfit(c2, 88, 25, 0, 1, 40.0, false);

        // In standard ranking, tier 3b (color harmony: 30 vs 25) takes precedence over tier 5b (normalized days: 40.0 vs 25.0)
        assertTrue(ScoredOutfit.RANKING_COMPARATOR.compare(stdOutfit1, stdOutfit2) < 0);
        assertEquals(-ScoredOutfit.RANKING_COMPARATOR.compare(stdOutfit1, stdOutfit2), ScoredOutfit.RANKING_COMPARATOR.compare(stdOutfit2, stdOutfit1));
    }
}
