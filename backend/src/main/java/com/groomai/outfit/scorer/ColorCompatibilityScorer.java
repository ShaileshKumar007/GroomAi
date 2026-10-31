package com.groomai.outfit.scorer;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic color harmony scorer based on menswear neutral grounding and accent rules.
 * Score range: [0, 30].
 */
@Component
public class ColorCompatibilityScorer {

    public static final Set<Color> NEUTRAL_ANCHORS = EnumSet.of(
        Color.WHITE, Color.BLACK, Color.NAVY, Color.GREY, Color.BEIGE, Color.BROWN, Color.OLIVE
    );

    public static final Set<Color> ACCENT_COLORS = EnumSet.of(
        Color.BLUE, Color.GREEN, Color.RED, Color.BURGUNDY, Color.YELLOW
    );

    public record ColorScoreResult(int score, String rationale) {}

    public ColorScoreResult score(List<WardrobeItem> items) {
        if (items == null || items.isEmpty()) {
            return new ColorScoreResult(0, "No garments evaluated for color harmony.");
        }

        Set<Color> primaryColors = EnumSet.noneOf(Color.class);
        int accentCount = 0;
        int multiCount = 0;
        Color accentColor = null;

        for (WardrobeItem item : items) {
            Color color = item.getPrimaryColor();
            if (color != null) {
                primaryColors.add(color);
                if (ACCENT_COLORS.contains(color)) {
                    accentCount++;
                    accentColor = color;
                } else if (color == Color.MULTI) {
                    multiCount++;
                }
            }
        }

        int baseScore;
        String rationale;

        // Rule A1: Purely neutral palette
        if (accentCount == 0 && multiCount == 0) {
            if (isClassicHighContrastNeutral(primaryColors)) {
                baseScore = 30;
                rationale = "Timeless high-contrast neutral pairing with versatile grounding.";
            } else {
                baseScore = 28;
                rationale = "Cohesive neutral palette providing sophisticated, understated balance.";
            }
        }
        // Rule A2: Exactly one accent grounded by neutrals
        else if (accentCount == 1 && multiCount == 0) {
            if (accentColor == Color.BURGUNDY && (primaryColors.contains(Color.GREY) || primaryColors.contains(Color.NAVY))) {
                baseScore = 30;
                rationale = "Classic burgundy accent elegantly grounded by neutral tones.";
            } else if ((accentColor == Color.GREEN || accentColor == Color.OLIVE)
                && (primaryColors.contains(Color.BEIGE) || primaryColors.contains(Color.BROWN) || primaryColors.contains(Color.WHITE))) {
                baseScore = 29;
                rationale = "Natural earth-toned accent harmonizing effortlessly with warm neutrals.";
            } else if (accentColor == Color.BLUE && (primaryColors.contains(Color.BROWN) || primaryColors.contains(Color.BEIGE))) {
                baseScore = 28;
                rationale = "Complementary blue accent nicely anchored by earthy neutral leather.";
            } else {
                baseScore = 26;
                rationale = "Single accent piece grounded by neutral companion pieces.";
            }
        }
        // Monochromatic / Tonal
        else if (isTonalPairing(primaryColors)) {
            baseScore = 25;
            rationale = "Tonal color harmony sharing cohesive shade variations.";
        } else {
            baseScore = 20;
            rationale = "Diverse palette requiring careful styling balance.";
        }

        // Rule A4: Deductions
        int penalty = 0;

        // Black & Brown clash (Bottom black + Footwear brown)
        if (hasBlackBottomBrownFootwearClash(items)) {
            penalty += 6;
            rationale += " Black trousers and brown footwear create noticeable color friction.";
        }

        // Two non-coordinated accents
        if (accentCount >= 2) {
            penalty += 10;
            rationale += " Multiple saturated accent colors compete for visual focus.";
        }

        // Direct saturated clash: Red + Green, Red + Yellow
        if (primaryColors.contains(Color.RED) && primaryColors.contains(Color.GREEN)) {
            penalty += 15;
            rationale += " High-contrast red and green clash visually.";
        }
        if (primaryColors.contains(Color.RED) && primaryColors.contains(Color.YELLOW)) {
            penalty += 15;
            rationale += " Loud red and yellow primary clash.";
        }

        if (multiCount > 1) {
            penalty += 15;
            rationale += " Multiple multi-colored patterns create visual overload.";
        }

        int finalScore = Math.clamp(baseScore - penalty, 0, 30);
        return new ColorScoreResult(finalScore, rationale.trim());
    }

    private boolean isClassicHighContrastNeutral(Set<Color> colors) {
        return (colors.contains(Color.NAVY) && colors.contains(Color.BEIGE))
            || (colors.contains(Color.WHITE) && colors.contains(Color.NAVY))
            || (colors.contains(Color.BLACK) && colors.contains(Color.GREY))
            || (colors.contains(Color.WHITE) && colors.contains(Color.BLACK));
    }

    private boolean isTonalPairing(Set<Color> colors) {
        return (colors.contains(Color.NAVY) && colors.contains(Color.BLUE))
            || (colors.contains(Color.BLACK) && colors.contains(Color.GREY) && colors.size() <= 2);
    }

    private boolean hasBlackBottomBrownFootwearClash(List<WardrobeItem> items) {
        Color bottomColor = null;
        Color footwearColor = null;
        for (WardrobeItem item : items) {
            if (item.getCategory() == Category.BOTTOM) {
                bottomColor = item.getPrimaryColor();
            } else if (item.getCategory() == Category.FOOTWEAR) {
                footwearColor = item.getPrimaryColor();
            }
        }
        return bottomColor == Color.BLACK && footwearColor == Color.BROWN;
    }
}
