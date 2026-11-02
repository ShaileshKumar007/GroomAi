package com.groomai.outfit.scorer;

import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.user.entity.StylePreferences;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic user style preferences compatibility scorer.
 * Range: [0, 10].
 *
 * S_explicit = 7 when profileCompleted == false.
 * S_explicit = clamp(0, 10, 5 + Δcolor + Δpattern + Δformality + Δstyle + Δgoal) when profileCompleted == true.
 * S_preference = clamp(0, 10, S_explicit + A_feedback). A_feedback = 0 for Stage 4C.3.2.
 */
@Component
public class PreferenceCompatibilityScorer {

    private static final Set<Color> NEUTRAL_PALETTE = Set.of(
        Color.BLACK, Color.WHITE, Color.GREY, Color.NAVY, Color.BEIGE
    );

    private static final Set<Color> NEUTRAL_ANCHORS = Set.of(
        Color.NAVY, Color.WHITE, Color.GREY, Color.BEIGE, Color.BROWN, Color.OLIVE
    );

    private static final Set<Color> CLASSIC_PALETTE = Set.of(
        Color.NAVY, Color.BROWN, Color.BEIGE, Color.WHITE, Color.BURGUNDY
    );

    private static final Set<Color> PREPPY_PALETTE = Set.of(
        Color.NAVY, Color.WHITE, Color.BEIGE, Color.GREEN, Color.BURGUNDY
    );

    private static final Set<Color> STREETWEAR_ACCENTS = Set.of(
        Color.RED, Color.YELLOW, Color.BLUE
    );

    public record PreferenceScoreResult(
        int score,
        int baseScore,
        int deltaColor,
        int deltaPattern,
        int deltaFormality,
        int deltaStyle,
        int deltaGoal,
        String rationale
    ) {
        public PreferenceScoreResult(int score, String rationale) {
            this(score, 5, 0, 0, 0, 0, 0, rationale);
        }
    }

    /**
     * Backward-compatible convenience scorer for unit tests and legacy callers.
     */
    public PreferenceScoreResult score(List<WardrobeItem> items, StylePreferences preferences) {
        if (items == null || items.size() < 3) {
            return new PreferenceScoreResult(7, 7, 0, 0, 0, 0, 0, "Neutral baseline (style preferences not specified).");
        }
        CandidateOutfit candidate = buildCandidateFromList(items);
        return score(candidate, preferences, 30, Collections.emptySet());
    }

    /**
     * Authoritative personalization scorer implementing the Stage 4C.3.2 explicit alignment formula.
     */
    public PreferenceScoreResult score(
        CandidateOutfit candidate,
        StylePreferences preferences,
        int colorHarmonyScore,
        Set<Color> recentlyWornColors
    ) {
        if (candidate == null) {
            return new PreferenceScoreResult(7, 7, 0, 0, 0, 0, 0, "Neutral baseline (no outfit candidate).");
        }

        // Rule: Incomplete or absent profile returns exact neutral 7/10 fallback
        if (preferences == null || !preferences.isProfileCompleted()) {
            return new PreferenceScoreResult(7, 7, 0, 0, 0, 0, 0, "Neutral baseline (style profile incomplete or unconfigured).");
        }

        List<WardrobeItem> items = candidate.getAllItems();
        int baseScore = 5;

        // 1. Color delta ([-4, +3])
        int deltaColor = computeColorDelta(items, preferences.getColorPreferences(), preferences.getAvoidedColors());

        // 2. Pattern delta ([-3, +2])
        int deltaPattern = computePatternDelta(items, preferences.getPatternPreferences(), preferences.getAvoidedPatterns());

        // 3. Formality delta ([0, +1])
        FormalityLevel modalFormality = computeModalFormality(items);
        int deltaFormality = computeFormalityDelta(modalFormality, preferences.getDefaultFormality());

        // 4. Style aesthetic delta ([-1, +1])
        PrimaryStyle primaryStyle = parsePrimaryStyle(preferences.getPrimaryStyle());
        int deltaStyle = computeStyleDelta(items, primaryStyle, modalFormality);

        // 5. Style goal delta ([0, +1])
        int deltaGoal = computeGoalDelta(
            candidate, items, modalFormality, preferences.getStyleGoals(),
            colorHarmonyScore, recentlyWornColors
        );

        int sExplicit = Math.clamp(baseScore + deltaColor + deltaPattern + deltaFormality + deltaStyle + deltaGoal, 0, 10);
        int aFeedback = 0; // Stage 4C.3.2: feedback adjustment is zero
        int sPreference = Math.clamp(sExplicit + aFeedback, 0, 10);

        String rationale = buildRationale(deltaColor, deltaPattern, deltaFormality, deltaStyle, deltaGoal, primaryStyle);

        return new PreferenceScoreResult(
            sPreference,
            baseScore,
            deltaColor,
            deltaPattern,
            deltaFormality,
            deltaStyle,
            deltaGoal,
            rationale
        );
    }

    private int computeColorDelta(List<WardrobeItem> items, List<String> preferredColors, List<String> avoidedColors) {
        List<String> pref = preferredColors != null ? preferredColors : Collections.emptyList();
        List<String> avoid = avoidedColors != null ? avoidedColors : Collections.emptyList();

        boolean hasPreferredPrimary = false;
        boolean hasPreferredSecondary = false;
        boolean hasAvoided = false;

        for (WardrobeItem item : items) {
            if (item.getPrimaryColor() != null) {
                String p = item.getPrimaryColor().name();
                if (pref.contains(p)) {
                    hasPreferredPrimary = true;
                }
                if (avoid.contains(p)) {
                    hasAvoided = true;
                }
            }
            if (item.getSecondaryColor() != null) {
                String s = item.getSecondaryColor().name();
                if (pref.contains(s)) {
                    hasPreferredSecondary = true;
                }
                if (avoid.contains(s)) {
                    hasAvoided = true;
                }
            }
        }

        int positiveBonus = 0;
        if (hasPreferredPrimary) {
            positiveBonus = 3;
        } else if (hasPreferredSecondary) {
            positiveBonus = 1;
        }

        int deduction = hasAvoided ? 4 : 0;
        return positiveBonus - deduction;
    }

    private int computePatternDelta(List<WardrobeItem> items, List<String> preferredPatterns, List<String> avoidedPatterns) {
        List<String> pref = preferredPatterns != null ? preferredPatterns : Collections.emptyList();
        List<String> avoid = avoidedPatterns != null ? avoidedPatterns : Collections.emptyList();

        boolean matchedPreferred = false;
        boolean matchedAvoided = false;

        for (WardrobeItem item : items) {
            if (item.getPattern() != null) {
                String pat = item.getPattern().name();
                if (pref.contains(pat)) matchedPreferred = true;
                if (avoid.contains(pat)) matchedAvoided = true;
            }
        }

        int delta = 0;
        if (matchedPreferred) delta += 2;
        if (matchedAvoided) delta -= 3;
        return delta;
    }

    private int computeFormalityDelta(FormalityLevel modalFormality, String defaultFormality) {
        if (modalFormality != null && defaultFormality != null && modalFormality.name().equalsIgnoreCase(defaultFormality.trim())) {
            return 1;
        }
        return 0;
    }

    private int computeStyleDelta(List<WardrobeItem> items, PrimaryStyle primaryStyle, FormalityLevel modalFormality) {
        if (primaryStyle == null) return 0;

        return switch (primaryStyle) {
            case CASUAL -> {
                boolean hasFormalOrBiz = items.stream().anyMatch(i ->
                    i.getFormalityLevel() == FormalityLevel.BUSINESS_CASUAL || i.getFormalityLevel() == FormalityLevel.FORMAL
                );
                if (hasFormalOrBiz) yield -1;

                boolean allCasual = items.stream().allMatch(i -> i.getFormalityLevel() == FormalityLevel.CASUAL);
                boolean validPatterns = items.stream().allMatch(i ->
                    i.getPattern() == null || i.getPattern() == Pattern.SOLID || i.getPattern() == Pattern.STRIPED || i.getPattern() == Pattern.TEXTURED
                );
                if (allCasual && validPatterns) yield 1;
                yield 0;
            }
            case SMART_CASUAL -> {
                boolean hasFormalOrClashPatterns = items.stream().anyMatch(i ->
                    i.getFormalityLevel() == FormalityLevel.FORMAL || i.getPattern() == Pattern.GRAPHIC || i.getPattern() == Pattern.FLORAL
                );
                if (hasFormalOrClashPatterns) yield -1;

                boolean isSmartCasualMode = modalFormality == FormalityLevel.SMART_CASUAL;
                boolean hasNeutralAnchor = items.stream().anyMatch(i ->
                    (i.getPrimaryColor() != null && NEUTRAL_ANCHORS.contains(i.getPrimaryColor()))
                        || (i.getSecondaryColor() != null && NEUTRAL_ANCHORS.contains(i.getSecondaryColor()))
                );
                if (isSmartCasualMode && hasNeutralAnchor) yield 1;
                yield 0;
            }
            case MINIMALIST -> {
                boolean hasNonSolid = items.stream().anyMatch(i -> i.getPattern() != null && i.getPattern() != Pattern.SOLID);
                boolean hasVibrantAccent = items.stream().anyMatch(i -> {
                    Color p = i.getPrimaryColor();
                    Color s = i.getSecondaryColor();
                    return (p != null && (p == Color.RED || p == Color.YELLOW || p == Color.GREEN || p == Color.BLUE || p == Color.MULTI))
                        || (s != null && (s == Color.RED || s == Color.YELLOW || s == Color.GREEN || s == Color.BLUE || s == Color.MULTI));
                });
                if (hasNonSolid || hasVibrantAccent) yield -1;

                boolean allNeutral = items.stream().allMatch(i -> i.getPrimaryColor() != null && NEUTRAL_PALETTE.contains(i.getPrimaryColor()));
                if (allNeutral) yield 1;

                long mutedCount = items.stream().filter(i -> i.getPrimaryColor() == Color.OLIVE || i.getPrimaryColor() == Color.BURGUNDY).count();
                if (mutedCount <= 1) yield 0;

                yield -1;
            }
            case CLASSIC -> {
                boolean hasClash = items.stream().anyMatch(i ->
                    i.getPattern() == Pattern.GRAPHIC || i.getPattern() == Pattern.FLORAL || i.getPrimaryColor() == Color.MULTI || i.getSecondaryColor() == Color.MULTI
                );
                if (hasClash) yield -1;

                boolean isClassicFormality = modalFormality == FormalityLevel.SMART_CASUAL || modalFormality == FormalityLevel.BUSINESS_CASUAL;
                boolean validPatterns = items.stream().allMatch(i -> {
                    Pattern p = i.getPattern();
                    return p == null || p == Pattern.SOLID || p == Pattern.STRIPED || p == Pattern.CHECKED;
                });
                boolean hasClassicTone = items.stream().anyMatch(i ->
                    (i.getPrimaryColor() != null && CLASSIC_PALETTE.contains(i.getPrimaryColor()))
                        || (i.getSecondaryColor() != null && CLASSIC_PALETTE.contains(i.getSecondaryColor()))
                );
                if (isClassicFormality && validPatterns && hasClassicTone) yield 1;
                yield 0;
            }
            case STREETWEAR -> {
                boolean hasTailoredPieces = items.stream().anyMatch(i ->
                    i.getFormalityLevel() == FormalityLevel.BUSINESS_CASUAL || i.getFormalityLevel() == FormalityLevel.FORMAL
                );
                if (hasTailoredPieces) yield -1;

                boolean allCasual = items.stream().allMatch(i -> i.getFormalityLevel() == FormalityLevel.CASUAL);
                boolean hasStreetAccent = items.stream().anyMatch(i ->
                    (i.getPrimaryColor() != null && STREETWEAR_ACCENTS.contains(i.getPrimaryColor()))
                        || (i.getSecondaryColor() != null && STREETWEAR_ACCENTS.contains(i.getSecondaryColor()))
                        || i.getPattern() == Pattern.GRAPHIC || i.getPattern() == Pattern.TEXTURED
                );
                if (allCasual && hasStreetAccent) yield 1;
                yield 0;
            }
            case PREPPY -> {
                boolean allBlack = items.stream().allMatch(i -> i.getPrimaryColor() == Color.BLACK);
                boolean hasGraphic = items.stream().anyMatch(i -> i.getPattern() == Pattern.GRAPHIC);
                if (allBlack || hasGraphic) yield -1;

                boolean isSmartCasualMode = modalFormality == FormalityLevel.SMART_CASUAL;
                boolean validPatterns = items.stream().allMatch(i -> {
                    Pattern p = i.getPattern();
                    return p == null || p == Pattern.STRIPED || p == Pattern.CHECKED || p == Pattern.SOLID;
                });
                boolean hasPreppyAnchor = items.stream().anyMatch(i ->
                    (i.getPrimaryColor() != null && PREPPY_PALETTE.contains(i.getPrimaryColor()))
                        || (i.getSecondaryColor() != null && PREPPY_PALETTE.contains(i.getSecondaryColor()))
                );
                if (isSmartCasualMode && validPatterns && hasPreppyAnchor) yield 1;
                yield 0;
            }
            case ATHLEISURE -> 0; // Grounded neutrality: missing athletic fabric technology metadata
        };
    }

    private int computeGoalDelta(
        CandidateOutfit candidate,
        List<WardrobeItem> items,
        FormalityLevel modalFormality,
        List<String> styleGoals,
        int colorHarmonyScore,
        Set<Color> recentlyWornColors
    ) {
        if (styleGoals == null || styleGoals.isEmpty()) {
            return 0;
        }

        int qualifying = 0;

        // 1. LOOK_SHARPER: modal formality >= SMART_CASUAL and internal formality gap <= 1
        if (styleGoals.contains(StyleGoal.LOOK_SHARPER.name())) {
            int rank = CandidateOutfit.getFormalityRank(modalFormality);
            int minRank = CandidateOutfit.getFormalityRank(FormalityLevel.SMART_CASUAL);
            if (rank >= minRank && candidate.getFormalityDelta() <= 1) {
                qualifying++;
            }
        }

        // 2. TRY_NEW_COLORS: S_color >= 28 and contains >= 1 primary color not worn in last 30 days
        if (styleGoals.contains(StyleGoal.TRY_NEW_COLORS.name())) {
            if (colorHarmonyScore >= 28) {
                boolean hasUnwornColor = items.stream().anyMatch(i ->
                    i.getPrimaryColor() != null && (recentlyWornColors == null || !recentlyWornColors.contains(i.getPrimaryColor()))
                );
                if (hasUnwornColor) {
                    qualifying++;
                }
            }
        }

        // COMFORT_FIRST, MINIMAL_EFFORT, EXPAND_ROTATION: 0 scoring points (deferred in Stage 4C.3.2)
        return Math.min(1, qualifying);
    }

    public static FormalityLevel computeModalFormality(List<WardrobeItem> items) {
        if (items == null || items.isEmpty()) return FormalityLevel.CASUAL;

        Map<FormalityLevel, Integer> counts = new EnumMap<>(FormalityLevel.class);
        for (WardrobeItem item : items) {
            FormalityLevel f = item.getFormalityLevel() != null ? item.getFormalityLevel() : FormalityLevel.CASUAL;
            counts.put(f, counts.getOrDefault(f, 0) + 1);
        }

        FormalityLevel best = FormalityLevel.CASUAL;
        int maxCount = -1;

        for (Map.Entry<FormalityLevel, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                best = entry.getKey();
            } else if (entry.getValue() == maxCount) {
                // Break tie by higher formality rank
                if (CandidateOutfit.getFormalityRank(entry.getKey()) > CandidateOutfit.getFormalityRank(best)) {
                    best = entry.getKey();
                }
            }
        }
        return best;
    }

    private PrimaryStyle parsePrimaryStyle(String s) {
        if (s == null) return PrimaryStyle.CASUAL;
        try {
            return PrimaryStyle.valueOf(s.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return PrimaryStyle.CASUAL;
        }
    }

    private String buildRationale(
        int deltaColor,
        int deltaPattern,
        int deltaFormality,
        int deltaStyle,
        int deltaGoal,
        PrimaryStyle primaryStyle
    ) {
        StringBuilder sb = new StringBuilder();
        if (deltaColor > 0) {
            sb.append("Features colors aligned with your stated preferences. ");
        } else if (deltaColor < 0) {
            sb.append("Contains colors you typically prefer to avoid. ");
        }

        if (deltaPattern > 0) {
            sb.append("Includes patterns you enjoy wearing. ");
        } else if (deltaPattern < 0) {
            sb.append("Contains patterns you typically prefer to avoid. ");
        }

        if (deltaFormality > 0) {
            sb.append("Reflects your default formality tier. ");
        }

        if (deltaStyle > 0 && primaryStyle != null) {
            sb.append("Harmonizes with your ").append(primaryStyle.name()).append(" aesthetic. ");
        } else if (deltaStyle < 0 && primaryStyle != null) {
            sb.append("Diverges from your typical ").append(primaryStyle.name()).append(" aesthetic. ");
        }

        if (deltaGoal > 0) {
            sb.append("Supports your active style goals. ");
        }

        return sb.isEmpty() ? "Standard alignment with your style profile." : sb.toString().trim();
    }

    private static CandidateOutfit buildCandidateFromList(List<WardrobeItem> items) {
        WardrobeItem top = items.get(0);
        WardrobeItem bottom = items.get(1);
        WardrobeItem footwear = items.get(2);
        WardrobeItem outerwear = (items.size() > 3) ? items.get(3) : null;
        WardrobeItem accessory = (items.size() > 4) ? items.get(4) : null;
        return new CandidateOutfit(top, bottom, footwear, outerwear, accessory);
    }
}
