package com.groomai.outfit.scorer;

import com.groomai.user.entity.StylePreferences;
import com.groomai.wardrobe.entity.WardrobeItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic user style preferences compatibility scorer.
 * Score range: [0, 10].
 */
@Component
public class PreferenceCompatibilityScorer {

    public record PreferenceScoreResult(int score, String rationale) {}

    public PreferenceScoreResult score(List<WardrobeItem> items, StylePreferences preferences) {
        if (preferences == null) {
            return new PreferenceScoreResult(7, "Neutral baseline (style preferences not specified).");
        }

        int score = 5;
        StringBuilder rationale = new StringBuilder();

        boolean matchedPreferredColor = false;
        boolean matchedAvoidedColor = false;

        List<String> preferredColors = preferences.getColorPreferences();
        List<String> avoidedColors = preferences.getAvoidedColors();

        for (WardrobeItem item : items) {
            if (item.getPrimaryColor() != null) {
                String colorName = item.getPrimaryColor().name();
                if (preferredColors != null && preferredColors.contains(colorName)) {
                    matchedPreferredColor = true;
                }
                if (avoidedColors != null && avoidedColors.contains(colorName)) {
                    matchedAvoidedColor = true;
                }
            }
        }

        if (matchedPreferredColor) {
            score += 5;
            rationale.append("Features colors aligned with your stated preferences. ");
        }

        if (matchedAvoidedColor) {
            score -= 6;
            rationale.append("Contains colors you typically prefer to avoid. ");
        }

        if (preferences.getDefaultFormality() != null) {
            boolean formalityMatch = items.stream()
                .anyMatch(i -> i.getFormalityLevel() != null && i.getFormalityLevel().name().equalsIgnoreCase(preferences.getDefaultFormality()));
            if (formalityMatch) {
                score += 3;
                rationale.append("Reflects your default style profile. ");
            }
        }

        int finalScore = Math.clamp(score, 0, 10);
        String finalRationale = rationale.isEmpty() ? "Standard alignment with your style profile." : rationale.toString().trim();
        return new PreferenceScoreResult(finalScore, finalRationale);
    }
}
