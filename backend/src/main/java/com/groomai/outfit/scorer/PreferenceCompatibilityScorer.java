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
                boolean hasFormalOrBiz = false;
                boolean allCasual = true;
                boolean validPatterns = true;
                for (WardrobeItem i : items) {
                    FormalityLevel f = i.getFormalityLevel();
                    if (f == FormalityLevel.BUSINESS_CASUAL || f == FormalityLevel.FORMAL) {
                        hasFormalOrBiz = true;
                    }
                    if (f != FormalityLevel.CASUAL) {
                        allCasual = false;
                    }
                    Pattern p = i.getPattern();
                    if (p != null && p != Pattern.SOLID && p != Pattern.STRIPED && p != Pattern.TEXTURED) {
                        validPatterns = false;
                    }
                }
                if (hasFormalOrBiz) yield -1;
                if (allCasual && validPatterns) yield 1;
                yield 0;
            }
            case SMART_CASUAL -> {
                boolean hasFormalOrClashPatterns = false;
                boolean hasNeutralAnchor = false;
                for (WardrobeItem i : items) {
                    FormalityLevel f = i.getFormalityLevel();
                    Pattern p = i.getPattern();
                    if (f == FormalityLevel.FORMAL || p == Pattern.GRAPHIC || p == Pattern.FLORAL) {
                        hasFormalOrClashPatterns = true;
                    }
                    Color pc = i.getPrimaryColor();
                    Color sc = i.getSecondaryColor();
                    if ((pc != null && NEUTRAL_ANCHORS.contains(pc)) || (sc != null && NEUTRAL_ANCHORS.contains(sc))) {
                        hasNeutralAnchor = true;
                    }
                }
                if (hasFormalOrClashPatterns) yield -1;
                if (modalFormality == FormalityLevel.SMART_CASUAL && hasNeutralAnchor) yield 1;
                yield 0;
            }
            case MINIMALIST -> {
                boolean hasNonSolid = false;
                boolean hasVibrantAccent = false;
                boolean allNeutral = true;
                int mutedCount = 0;
                for (WardrobeItem i : items) {
                    Pattern p = i.getPattern();
                    if (p != null && p != Pattern.SOLID) hasNonSolid = true;

                    Color pc = i.getPrimaryColor();
                    Color sc = i.getSecondaryColor();
                    if ((pc != null && (pc == Color.RED || pc == Color.YELLOW || pc == Color.GREEN || pc == Color.BLUE || pc == Color.MULTI))
                        || (sc != null && (sc == Color.RED || sc == Color.YELLOW || sc == Color.GREEN || sc == Color.BLUE || sc == Color.MULTI))) {
                        hasVibrantAccent = true;
                    }

                    if (pc == null || !NEUTRAL_PALETTE.contains(pc)) allNeutral = false;
                    if (pc == Color.OLIVE || pc == Color.BURGUNDY) mutedCount++;
                }
                if (hasNonSolid || hasVibrantAccent) yield -1;
                if (allNeutral) yield 1;
                if (mutedCount <= 1) yield 0;
                yield -1;
            }
            case CLASSIC -> {
                boolean hasClash = false;
                boolean validPatterns = true;
                boolean hasClassicTone = false;
                for (WardrobeItem i : items) {
                    Pattern p = i.getPattern();
                    Color pc = i.getPrimaryColor();
                    Color sc = i.getSecondaryColor();
                    if (p == Pattern.GRAPHIC || p == Pattern.FLORAL || pc == Color.MULTI || sc == Color.MULTI) {
                        hasClash = true;
                    }
                    if (p != null && p != Pattern.SOLID && p != Pattern.STRIPED && p != Pattern.CHECKED) {
                        validPatterns = false;
                    }
                    if ((pc != null && CLASSIC_PALETTE.contains(pc)) || (sc != null && CLASSIC_PALETTE.contains(sc))) {
                        hasClassicTone = true;
                    }
                }
                if (hasClash) yield -1;
                boolean isClassicFormality = modalFormality == FormalityLevel.SMART_CASUAL || modalFormality == FormalityLevel.BUSINESS_CASUAL;
                if (isClassicFormality && validPatterns && hasClassicTone) yield 1;
                yield 0;
            }
            case STREETWEAR -> {
                boolean hasTailoredPieces = false;
                boolean allCasual = true;
                boolean hasStreetAccent = false;
                for (WardrobeItem i : items) {
                    FormalityLevel f = i.getFormalityLevel();
                    if (f == FormalityLevel.BUSINESS_CASUAL || f == FormalityLevel.FORMAL) {
                        hasTailoredPieces = true;
                    }
                    if (f != FormalityLevel.CASUAL) {
                        allCasual = false;
                    }
                    Color pc = i.getPrimaryColor();
                    Color sc = i.getSecondaryColor();
                    Pattern p = i.getPattern();
                    if ((pc != null && STREETWEAR_ACCENTS.contains(pc))
                        || (sc != null && STREETWEAR_ACCENTS.contains(sc))
                        || p == Pattern.GRAPHIC || p == Pattern.TEXTURED) {
                        hasStreetAccent = true;
                    }
                }
                if (hasTailoredPieces) yield -1;
                if (allCasual && hasStreetAccent) yield 1;
                yield 0;
            }
            case PREPPY -> {
                boolean allBlack = true;
                boolean hasGraphic = false;
                boolean validPatterns = true;
                boolean hasPreppyAnchor = false;
                for (WardrobeItem i : items) {
                    if (i.getPrimaryColor() != Color.BLACK) allBlack = false;
                    Pattern p = i.getPattern();
                    if (p == Pattern.GRAPHIC) hasGraphic = true;
                    if (p != null && p != Pattern.STRIPED && p != Pattern.CHECKED && p != Pattern.SOLID) {
                        validPatterns = false;
                    }
                    Color pc = i.getPrimaryColor();
                    Color sc = i.getSecondaryColor();
                    if ((pc != null && PREPPY_PALETTE.contains(pc)) || (sc != null && PREPPY_PALETTE.contains(sc))) {
                        hasPreppyAnchor = true;
                    }
                }
                if (allBlack || hasGraphic) yield -1;
                boolean isSmartCasualMode = modalFormality == FormalityLevel.SMART_CASUAL;
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
                boolean hasUnwornColor = false;
                for (WardrobeItem i : items) {
                    Color pc = i.getPrimaryColor();
                    if (pc != null && (recentlyWornColors == null || !recentlyWornColors.contains(pc))) {
                        hasUnwornColor = true;
                        break;
                    }
                }
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
