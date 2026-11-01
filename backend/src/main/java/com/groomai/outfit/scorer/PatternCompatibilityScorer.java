package com.groomai.outfit.scorer;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Pattern;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic pattern restraint scorer.
 * Score range: [0, 15].
 */
@Component
public class PatternCompatibilityScorer {

    public record PatternScoreResult(int score, String rationale) {}

    public PatternScoreResult score(List<WardrobeItem> items) {
        if (items == null || items.isEmpty()) {
            return new PatternScoreResult(15, "Solid baseline pattern.");
        }

        int loudPatternCount = 0;
        int texturedCount = 0;
        int solidCount = 0;
        String focalPatternName = null;

        for (WardrobeItem item : items) {
            Pattern p = item.getPattern() != null ? item.getPattern() : Pattern.SOLID;
            switch (p) {
                case SOLID -> solidCount++;
                case TEXTURED -> texturedCount++;
                case STRIPED, CHECKED, FLORAL, GRAPHIC -> {
                    loudPatternCount++;
                    focalPatternName = p.name().toLowerCase();
                }
            }
        }

        int score;
        String rationale;

        if (loudPatternCount == 0) {
            score = 15;
            if (texturedCount > 0) {
                rationale = "Subtle texture paired with clean solid pieces provides sophisticated depth.";
            } else {
                rationale = "Solid pieces ensure clean, distraction-free visual balance.";
            }
        } else if (loudPatternCount == 1) {
            score = 14;
            rationale = "Focal " + focalPatternName + " piece anchored cleanly by solid companions.";
        } else if (loudPatternCount == 2 && items.stream().anyMatch(i -> i.getPattern() == Pattern.STRIPED) && texturedCount > 0) {
            score = 10;
            rationale = "Subtle pattern layering with stripes and textured weave.";
        } else {
            // Pattern clash
            score = 3;
            rationale = "Multiple competing patterns create visual conflict and clutter.";
        }

        return new PatternScoreResult(Math.clamp(score, 0, 15), rationale);
    }
}
