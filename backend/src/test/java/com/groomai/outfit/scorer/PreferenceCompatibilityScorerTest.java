package com.groomai.outfit.scorer;

import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.user.entity.StylePreferences;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenceCompatibilityScorerTest {

    private PreferenceCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new PreferenceCompatibilityScorer();
    }

    private WardrobeItem createItem(
        Category category,
        Color primaryColor,
        Color secondaryColor,
        Pattern pattern,
        FormalityLevel formality
    ) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Item " + category)
            .category(category)
            .primaryColor(primaryColor)
            .secondaryColor(secondaryColor)
            .pattern(pattern)
            .formalityLevel(formality)
            .season(Season.ALL_SEASON)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    private WardrobeItem createSimpleItem(Category category, Color primaryColor, FormalityLevel formality) {
        return createItem(category, primaryColor, null, Pattern.SOLID, formality);
    }

    @Test
    @DisplayName("Incomplete profile (profileCompleted == false) returns neutral baseline score 7")
    void testIncompleteProfileReturnsNeutralSeven() {
        var prefs = StylePreferences.builder()
            .profileCompleted(false)
            .primaryStyle(PrimaryStyle.MINIMALIST.name())
            .colorPreferences(List.of("BLACK", "WHITE"))
            .avoidedColors(List.of("YELLOW"))
            .patternPreferences(List.of("SOLID"))
            .avoidedPatterns(List.of("FLORAL"))
            .defaultFormality("CASUAL")
            .styleGoals(List.of(StyleGoal.COMFORT_FIRST.name()))
            .build();

        var top = createSimpleItem(Category.TOP, Color.YELLOW, FormalityLevel.CASUAL);
        var bottom = createSimpleItem(Category.BOTTOM, Color.YELLOW, FormalityLevel.CASUAL);
        var shoes = createSimpleItem(Category.FOOTWEAR, Color.YELLOW, FormalityLevel.CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, prefs, 25, Collections.emptySet());

        assertEquals(7, result.score());
        assertTrue(result.rationale().contains("profile incomplete"));
    }

    @Test
    @DisplayName("Null profile returns neutral baseline score 7")
    void testNullProfileReturnsNeutralSeven() {
        var top = createSimpleItem(Category.TOP, Color.NAVY, FormalityLevel.CASUAL);
        var bottom = createSimpleItem(Category.BOTTOM, Color.BEIGE, FormalityLevel.CASUAL);
        var shoes = createSimpleItem(Category.FOOTWEAR, Color.BROWN, FormalityLevel.CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, null, 25, Collections.emptySet());

        assertEquals(7, result.score());
        assertTrue(result.rationale().contains("profile incomplete"));
    }

    @Test
    @DisplayName("Complete profile with neutral items returns baseline score 5")
    void testCompleteProfileNeutralItemsReturnsFive() {
        var prefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name()) // ATHLEISURE style gives 0
            .colorPreferences(List.of("NAVY"))
            .avoidedColors(List.of("YELLOW"))
            .patternPreferences(List.of("STRIPED"))
            .avoidedPatterns(List.of("FLORAL"))
            .defaultFormality("FORMAL")
            .styleGoals(List.of(StyleGoal.EXPAND_ROTATION.name())) // EXPAND_ROTATION gives 0 in scoring
            .build();

        // Items with BEIGE and GREY (no match, no avoid), SOLID pattern (no match, no avoid), CASUAL (mismatches FORMAL default)
        var top = createSimpleItem(Category.TOP, Color.BEIGE, FormalityLevel.CASUAL);
        var bottom = createSimpleItem(Category.BOTTOM, Color.GREY, FormalityLevel.CASUAL);
        var shoes = createSimpleItem(Category.FOOTWEAR, Color.WHITE, FormalityLevel.CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, prefs, 20, Collections.emptySet());

        // Base 5 + 0 color + 0 pattern + 0 formality + 0 style + 0 goal = 5
        assertEquals(5, result.score());
    }

    @Test
    @DisplayName("Color preferences: primary color match awards +3 (capped at +3 even with multiple matches), secondary-only match awards +1")
    void testColorScoringMatches() {
        var prefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .colorPreferences(List.of("NAVY", "WHITE"))
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL") // Mismatched to isolate color score
            .styleGoals(Collections.emptyList())
            .build();

        // 1. One primary match (+3)
        var top1 = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom1 = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes1 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res1 = scorer.score(new CandidateOutfit(top1, bottom1, shoes1), prefs, 25, Collections.emptySet());
        // Base 5 + 3 = 8
        assertEquals(8, res1.score());

        // 2. Multiple primary matches still award strictly +3 (positive color bonus cannot exceed +3)
        var top2 = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom2 = createItem(Category.BOTTOM, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes2 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res2 = scorer.score(new CandidateOutfit(top2, bottom2, shoes2), prefs, 25, Collections.emptySet());
        // Base 5 + 3 = 8
        assertEquals(8, res2.score());

        // 3. Secondary-only match awards +1 only when NO primary color matches
        var top3 = createItem(Category.TOP, Color.BEIGE, Color.NAVY, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom3 = createItem(Category.BOTTOM, Color.GREY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes3 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res3 = scorer.score(new CandidateOutfit(top3, bottom3, shoes3), prefs, 25, Collections.emptySet());
        // Base 5 + 1 = 6
        assertEquals(6, res3.score());

        // 4. Primary match present along with secondary match: awards +3 (not +4)
        var top4 = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom4 = createItem(Category.BOTTOM, Color.BEIGE, Color.WHITE, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes4 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res4 = scorer.score(new CandidateOutfit(top4, bottom4, shoes4), prefs, 25, Collections.emptySet());
        // Base 5 + 3 = 8
        assertEquals(8, res4.score());
    }

    @Test
    @DisplayName("Avoided color penalty (-4) applies alongside positive match: (+3 - 4 = -1) and (+1 - 4 = -3)")
    void testAvoidedColorAndSimultaneousPreferredAvoided() {
        var prefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .colorPreferences(List.of("NAVY", "WHITE"))
            .avoidedColors(List.of("YELLOW"))
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();

        // 1. Avoided primary color alone: 5 - 4 = 1
        var top1 = createItem(Category.TOP, Color.YELLOW, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom1 = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes1 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res1 = scorer.score(new CandidateOutfit(top1, bottom1, shoes1), prefs, 25, Collections.emptySet());
        assertEquals(1, res1.score());

        // 2. Preferred primary (NAVY: +3) and avoided secondary (YELLOW: -4) on same item: net -1 (Base 5 - 1 = 4)
        var top2 = createItem(Category.TOP, Color.NAVY, Color.YELLOW, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom2 = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes2 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res2 = scorer.score(new CandidateOutfit(top2, bottom2, shoes2), prefs, 25, Collections.emptySet());
        assertEquals(4, res2.score());

        // 3. Preferred secondary-only (+1) and avoided primary (-4) on different items: net -3 (Base 5 - 3 = 2)
        var top3 = createItem(Category.TOP, Color.BEIGE, Color.NAVY, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom3 = createItem(Category.BOTTOM, Color.YELLOW, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes3 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res3 = scorer.score(new CandidateOutfit(top3, bottom3, shoes3), prefs, 25, Collections.emptySet());
        assertEquals(2, res3.score());
    }

    @Test
    @DisplayName("Pattern preferences: preferred awards +2, avoided incurs -3, simultaneous net -1")
    void testPatternScoring() {
        var prefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(List.of("STRIPED"))
            .avoidedPatterns(List.of("FLORAL"))
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();

        // Preferred pattern: Base 5 + 2 = 7
        var top1 = createItem(Category.TOP, Color.BEIGE, null, Pattern.STRIPED, FormalityLevel.CASUAL);
        var bottom1 = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes1 = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var res1 = scorer.score(new CandidateOutfit(top1, bottom1, shoes1), prefs, 25, Collections.emptySet());
        assertEquals(7, res1.score());

        // Avoided pattern: Base 5 - 3 = 2
        var top2 = createItem(Category.TOP, Color.BEIGE, null, Pattern.FLORAL, FormalityLevel.CASUAL);
        var res2 = scorer.score(new CandidateOutfit(top2, bottom1, shoes1), prefs, 25, Collections.emptySet());
        assertEquals(2, res2.score());

        // Both preferred and avoided present in outfit: Base 5 + 2 - 3 = 4
        var bottom3 = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.STRIPED, FormalityLevel.CASUAL);
        var res3 = scorer.score(new CandidateOutfit(top2, bottom3, shoes1), prefs, 25, Collections.emptySet());
        assertEquals(4, res3.score());
    }

    @Test
    @DisplayName("Default formality match awards +1; mismatch awards 0")
    void testDefaultFormalityMatchAndMismatch() {
        var prefsMatch = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("SMART_CASUAL")
            .styleGoals(Collections.emptyList())
            .build();

        var top = createItem(Category.TOP, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var resMatch = scorer.score(candidate, prefsMatch, 25, Collections.emptySet());
        // Base 5 + 1 formality match = 6
        assertEquals(6, resMatch.score());

        var prefsMismatch = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        var resMismatch = scorer.score(candidate, prefsMismatch, 25, Collections.emptySet());
        // Base 5 + 0 = 5
        assertEquals(5, resMismatch.score());
    }

    @Test
    @DisplayName("Primary style rules for CASUAL, SMART_CASUAL, MINIMALIST, CLASSIC, STREETWEAR, PREPPY, and ATHLEISURE")
    void testAllSevenPrimaryStyles() {
        // 1. CASUAL style (+1 for all CASUAL with SOLID/STRIPED/TEXTURED; -1 if contains BUSINESS_CASUAL/FORMAL)
        var casualPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.CASUAL.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        var topC = createItem(Category.TOP, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottomC = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.STRIPED, FormalityLevel.CASUAL);
        var shoesC = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.TEXTURED, FormalityLevel.CASUAL);
        assertEquals(6, scorer.score(new CandidateOutfit(topC, bottomC, shoesC), casualPrefs, 25, Collections.emptySet()).score());

        // CASUAL negative (-1) when containing BUSINESS_CASUAL
        var bottomBiz = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.BUSINESS_CASUAL);
        assertEquals(4, scorer.score(new CandidateOutfit(topC, bottomBiz, shoesC), casualPrefs, 25, Collections.emptySet()).score());

        // 2. SMART_CASUAL style (+1 for modal SMART_CASUAL with neutral anchor; -1 for FORMAL or GRAPHIC/FLORAL)
        var smartPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.SMART_CASUAL.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        var topSC = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var bottomSC = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoesSC = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        assertEquals(6, scorer.score(new CandidateOutfit(topSC, bottomSC, shoesSC), smartPrefs, 25, Collections.emptySet()).score());

        // 3. MINIMALIST style: all neutral primary colors and strictly SOLID (+1); vibrant accents or non-solid (-1)
        var minPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.MINIMALIST.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        var topM = createItem(Category.TOP, Color.BLACK, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottomM = createItem(Category.BOTTOM, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoesM = createItem(Category.FOOTWEAR, Color.GREY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        assertEquals(6, scorer.score(new CandidateOutfit(topM, bottomM, shoesM), minPrefs, 25, Collections.emptySet()).score());

        // MINIMALIST with non-solid pattern (-1: Base 5 - 1 = 4)
        var topMStriped = createItem(Category.TOP, Color.BLACK, null, Pattern.STRIPED, FormalityLevel.CASUAL);
        assertEquals(4, scorer.score(new CandidateOutfit(topMStriped, bottomM, shoesM), minPrefs, 25, Collections.emptySet()).score());

        // 4. CLASSIC style: modal SMART_CASUAL/BUSINESS_CASUAL, patterns SOLID/STRIPED/CHECKED, classic tones (+1)
        var classicPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.CLASSIC.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        var topCl = createItem(Category.TOP, Color.NAVY, null, Pattern.STRIPED, FormalityLevel.BUSINESS_CASUAL);
        var bottomCl = createItem(Category.BOTTOM, Color.GREY, null, Pattern.CHECKED, FormalityLevel.BUSINESS_CASUAL);
        var shoesCl = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.BUSINESS_CASUAL);
        assertEquals(6, scorer.score(new CandidateOutfit(topCl, bottomCl, shoesCl), classicPrefs, 25, Collections.emptySet()).score());

        // 5. STREETWEAR style: modal CASUAL and street accent/pattern (+1); tailored pieces (-1)
        var streetPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.STREETWEAR.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        var topSt = createItem(Category.TOP, Color.BLACK, null, Pattern.GRAPHIC, FormalityLevel.CASUAL);
        var bottomSt = createItem(Category.BOTTOM, Color.OLIVE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoesSt = createItem(Category.FOOTWEAR, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        assertEquals(6, scorer.score(new CandidateOutfit(topSt, bottomSt, shoesSt), streetPrefs, 25, Collections.emptySet()).score());

        // 6. PREPPY style: modal SMART_CASUAL, preppy palette anchor, patterns SOLID/STRIPED/CHECKED (+1)
        var prepPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.PREPPY.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        var topPr = createItem(Category.TOP, Color.NAVY, null, Pattern.STRIPED, FormalityLevel.SMART_CASUAL);
        var bottomPr = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoesPr = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        assertEquals(6, scorer.score(new CandidateOutfit(topPr, bottomPr, shoesPr), prepPrefs, 25, Collections.emptySet()).score());

        // 7. ATHLEISURE style: always neutral 0
        var athPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();
        assertEquals(5, scorer.score(new CandidateOutfit(topC, bottomC, shoesC), athPrefs, 25, Collections.emptySet()).score());
    }

    @Test
    @DisplayName("Style goals: LOOK_SHARPER (+1), TRY_NEW_COLORS (+1), deferred goals produce strictly 0 (COMFORT_FIRST, MINIMAL_EFFORT, EXPAND_ROTATION)")
    void testAllStyleGoalsAndDeferredZeroBonus() {
        var basePrefsBuilder = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL");

        // 1. LOOK_SHARPER: awards +1 when modal formality >= SMART_CASUAL and deltaF <= 1
        var lsPrefs = basePrefsBuilder.styleGoals(List.of(StyleGoal.LOOK_SHARPER.name())).build();
        var topLS = createItem(Category.TOP, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var bottomLS = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoesLS = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        assertEquals(6, scorer.score(new CandidateOutfit(topLS, bottomLS, shoesLS), lsPrefs, 25, Collections.emptySet()).score());

        // 2. EXPAND_ROTATION: strictly 0 scoring bonus (handled in comparator)
        var erPrefs = basePrefsBuilder.styleGoals(List.of(StyleGoal.EXPAND_ROTATION.name())).build();
        assertEquals(5, scorer.score(new CandidateOutfit(topLS, bottomLS, shoesLS), erPrefs, 25, Collections.emptySet()).score());

        // 3. COMFORT_FIRST: strictly 0 scoring bonus (deferred in Stage 4C.3.2)
        var cfPrefs = basePrefsBuilder.styleGoals(List.of(StyleGoal.COMFORT_FIRST.name())).build();
        var topCF = createItem(Category.TOP, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottomCF = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoesCF = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);
        assertEquals(5, scorer.score(new CandidateOutfit(topCF, bottomCF, shoesCF), cfPrefs, 25, Collections.emptySet()).score());

        // 4. MINIMAL_EFFORT: strictly 0 scoring bonus (deferred in Stage 4C.3.2)
        var mePrefs = basePrefsBuilder.styleGoals(List.of(StyleGoal.MINIMAL_EFFORT.name())).build();
        var topME = createItem(Category.TOP, Color.BLACK, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottomME = createItem(Category.BOTTOM, Color.BLACK, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoesME = createItem(Category.FOOTWEAR, Color.BLACK, null, Pattern.SOLID, FormalityLevel.CASUAL);
        assertEquals(5, scorer.score(new CandidateOutfit(topME, bottomME, shoesME), mePrefs, 25, Collections.emptySet()).score());

        // 5. TRY_NEW_COLORS: awards +1 when colorHarmonyScore >= 28 and contains primary color not worn in last 30 days
        var tncPrefs = basePrefsBuilder.styleGoals(List.of(StyleGoal.TRY_NEW_COLORS.name())).build();
        Set<Color> recentlyWorn = Set.of(Color.BLACK, Color.WHITE);
        var topTNC = createItem(Category.TOP, Color.BURGUNDY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottomTNC = createItem(Category.BOTTOM, Color.BLACK, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoesTNC = createItem(Category.FOOTWEAR, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        // S_color 28 >= 28 and BURGUNDY not in recently worn -> +1 (Base 5 + 1 = 6)
        assertEquals(6, scorer.score(new CandidateOutfit(topTNC, bottomTNC, shoesTNC), tncPrefs, 28, recentlyWorn).score());

        // When S_color < 28 (e.g. 27), exploration bonus is denied (Base 5 + 0 = 5)
        assertEquals(5, scorer.score(new CandidateOutfit(topTNC, bottomTNC, shoesTNC), tncPrefs, 27, recentlyWorn).score());

        // When all primary colors were recently worn, exploration bonus is denied (Base 5 + 0 = 5)
        Set<Color> allWorn = Set.of(Color.BURGUNDY, Color.BLACK, Color.WHITE);
        assertEquals(5, scorer.score(new CandidateOutfit(topTNC, bottomTNC, shoesTNC), tncPrefs, 28, allWorn).score());

        // 6. Goal Cap: Multiple qualifying goals (LOOK_SHARPER + TRY_NEW_COLORS) capped at +1
        var multiGoalPrefs = basePrefsBuilder.styleGoals(List.of(StyleGoal.LOOK_SHARPER.name(), StyleGoal.TRY_NEW_COLORS.name())).build();
        var topMulti = createItem(Category.TOP, Color.BURGUNDY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var bottomMulti = createItem(Category.BOTTOM, Color.BLACK, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoesMulti = createItem(Category.FOOTWEAR, Color.WHITE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        // LOOK_SHARPER qualifies (+1), TRY_NEW_COLORS qualifies (+1). Sum = 2, capped at +1. Base 5 + 1 = 6.
        assertEquals(6, scorer.score(new CandidateOutfit(topMulti, bottomMulti, shoesMulti), multiGoalPrefs, 29, recentlyWorn).score());
    }

    @Test
    @DisplayName("Score clamping: strictly bounded within [0, 10]")
    void testScoreClampingBounds() {
        var basePrefsBuilder = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.ATHLEISURE.name())
            .defaultFormality("CASUAL");

        // Upper clamp: 5 + 3 (color) + 2 (pattern) + 1 (formality) = 11 -> clamped to 10
        var maxPrefs = basePrefsBuilder
            .colorPreferences(List.of("NAVY", "WHITE"))
            .patternPreferences(List.of("SOLID"))
            .styleGoals(Collections.emptyList())
            .build();
        var topMax = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottomMax = createItem(Category.BOTTOM, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoesMax = createItem(Category.FOOTWEAR, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        assertEquals(10, scorer.score(new CandidateOutfit(topMax, bottomMax, shoesMax), maxPrefs, 25, Collections.emptySet()).score());

        // Lower clamp: 5 - 4 (color) - 3 (pattern) = -2 -> clamped to 0
        var minPrefs = basePrefsBuilder
            .avoidedColors(List.of("YELLOW"))
            .avoidedPatterns(List.of("FLORAL"))
            .styleGoals(Collections.emptyList())
            .build();
        var topMin = createItem(Category.TOP, Color.YELLOW, null, Pattern.FLORAL, FormalityLevel.FORMAL);
        var bottomMin = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.FORMAL);
        var shoesMin = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.FORMAL);
        assertEquals(0, scorer.score(new CandidateOutfit(topMin, bottomMin, shoesMin), minPrefs, 25, Collections.emptySet()).score());
    }

    @Test
    @DisplayName("Example 1: Smart Casual Minimalist from specification reaches 10")
    void testSpecificationExample1() {
        var prefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.SMART_CASUAL.name())
            .defaultFormality("SMART_CASUAL")
            .colorPreferences(List.of("NAVY", "WHITE"))
            .avoidedColors(List.of("YELLOW"))
            .patternPreferences(List.of("SOLID"))
            .avoidedPatterns(List.of("FLORAL"))
            .styleGoals(List.of(StyleGoal.LOOK_SHARPER.name()))
            .build();

        var top = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, prefs, 28, Collections.emptySet());

        // Base 5 + 3 (navy preferred) + 2 (solid pattern) + 1 (formality match) + 1 (smart casual style) + 1 (look_sharper) = 13 -> clamped to 10
        assertEquals(10, result.score());
    }

    @Test
    @DisplayName("Example 2: Streetwear Enthusiast from specification reaches 10")
    void testSpecificationExample2() {
        var prefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.STREETWEAR.name())
            .defaultFormality("CASUAL")
            .colorPreferences(List.of("BLACK", "WHITE"))
            .avoidedColors(List.of("RED"))
            .patternPreferences(List.of("GRAPHIC"))
            .styleGoals(List.of(StyleGoal.COMFORT_FIRST.name()))
            .build();

        var top = createItem(Category.TOP, Color.BLACK, null, Pattern.GRAPHIC, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.OLIVE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, prefs, 26, Collections.emptySet());

        // Base 5 + 3 (color: primary match) + 2 (graphic pattern) + 1 (formality match) + 1 (streetwear style) + 0 (comfort_first deferred) = 12 -> clamped to 10
        assertEquals(10, result.score());
    }

    @Test
    @DisplayName("Example 3: Classic Professional with avoided color and mismatch from specification scores 6")
    void testSpecificationExample3() {
        var prefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.CLASSIC.name())
            .defaultFormality("BUSINESS_CASUAL")
            .colorPreferences(List.of("NAVY", "GREY"))
            .avoidedColors(List.of("YELLOW"))
            .patternPreferences(List.of("STRIPED", "SOLID"))
            .styleGoals(List.of(StyleGoal.LOOK_SHARPER.name()))
            .build();

        var top = createItem(Category.TOP, Color.YELLOW, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.NAVY, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, prefs, 24, Collections.emptySet());

        // Base 5 - 4 (yellow avoided) + 3 (navy preferred) + 2 (solid preferred) + 0 (formality mismatch) + 0 (classic requires smart/business casual) + 0 (look_sharper requires modal >= 2) = 6
        assertEquals(6, result.score());
    }

    @Test
    @DisplayName("Example 4: Incomplete profile returns exactly 7")
    void testSpecificationExample4() {
        var prefs = StylePreferences.builder()
            .profileCompleted(false)
            .build();

        var top = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);

        var candidate = new CandidateOutfit(top, bottom, shoes);
        var result = scorer.score(candidate, prefs, 30, Collections.emptySet());

        assertEquals(7, result.score());
    }

    @Test
    @DisplayName("STREETWEAR: Mixed CASUAL and SMART_CASUAL garments do not receive positive streetwear score (+0 pts)")
    void testStreetwearMixedCasualAndSmartCasualDoesNotReceivePositiveScore() {
        var streetPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.STREETWEAR.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();

        // 1. Mixed outfit: 1 SMART_CASUAL + 2 CASUAL. Modal formality is CASUAL, contains GRAPHIC pattern.
        var smartTop = createItem(Category.TOP, Color.BLACK, null, Pattern.GRAPHIC, FormalityLevel.SMART_CASUAL);
        var casualBottom = createItem(Category.BOTTOM, Color.OLIVE, null, Pattern.SOLID, FormalityLevel.CASUAL);
        var casualShoes = createItem(Category.FOOTWEAR, Color.WHITE, null, Pattern.SOLID, FormalityLevel.CASUAL);

        var mixedCandidate = new CandidateOutfit(smartTop, casualBottom, casualShoes);
        // Base 5 + 0 style delta = 5 (does NOT get +1 because not all items are CASUAL)
        assertEquals(5, scorer.score(mixedCandidate, streetPrefs, 25, Collections.emptySet()).score());

        // 2. Pure casual outfit: All 3 garments are CASUAL, contains GRAPHIC pattern.
        var casualTop = createItem(Category.TOP, Color.BLACK, null, Pattern.GRAPHIC, FormalityLevel.CASUAL);
        var pureCasualCandidate = new CandidateOutfit(casualTop, casualBottom, casualShoes);
        // Base 5 + 1 style delta = 6 (gets +1 because all items are CASUAL and has GRAPHIC pattern)
        assertEquals(6, scorer.score(pureCasualCandidate, streetPrefs, 25, Collections.emptySet()).score());
    }

    @Test
    @DisplayName("PREPPY: All-solid smart-casual outfits with preppy palette anchor receive +1")
    void testPreppyAllSolidSmartCasualReceivesPositiveBonus() {
        var preppyPrefs = StylePreferences.builder()
            .profileCompleted(true)
            .primaryStyle(PrimaryStyle.PREPPY.name())
            .colorPreferences(Collections.emptyList())
            .avoidedColors(Collections.emptyList())
            .patternPreferences(Collections.emptyList())
            .avoidedPatterns(Collections.emptyList())
            .defaultFormality("FORMAL")
            .styleGoals(Collections.emptyList())
            .build();

        // All-solid smart-casual outfit with preppy palette anchors (NAVY, BEIGE, BROWN)
        var top = createItem(Category.TOP, Color.NAVY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var bottom = createItem(Category.BOTTOM, Color.BEIGE, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var shoes = createItem(Category.FOOTWEAR, Color.BROWN, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);

        var preppyCandidate = new CandidateOutfit(top, bottom, shoes);
        // Base 5 + 1 preppy style delta = 6
        assertEquals(6, scorer.score(preppyCandidate, preppyPrefs, 25, Collections.emptySet()).score());

        // Contrast: All-solid smart-casual outfit WITHOUT preppy palette anchors (all GREY)
        var greyTop = createItem(Category.TOP, Color.GREY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var greyBottom = createItem(Category.BOTTOM, Color.GREY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);
        var greyShoes = createItem(Category.FOOTWEAR, Color.GREY, null, Pattern.SOLID, FormalityLevel.SMART_CASUAL);

        var nonPreppyCandidate = new CandidateOutfit(greyTop, greyBottom, greyShoes);
        // Base 5 + 0 style delta = 5 (Neutral tier: no preppy palette anchor)
        assertEquals(5, scorer.score(nonPreppyCandidate, preppyPrefs, 25, Collections.emptySet()).score());
    }
}
