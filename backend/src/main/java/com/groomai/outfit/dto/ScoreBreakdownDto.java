package com.groomai.outfit.dto;

/**
 * Detailed sub-score breakdown demonstrating deterministic scoring arithmetic.
 */
public record ScoreBreakdownDto(
    int colorHarmony,
    int formalityAlignment,
    int seasonWeatherCompatibility,
    int patternRestraint,
    int preferenceMatch,
    int wearPenalty
) {
    /**
     * Computes the net suitability score from the component sub-scores.
     * Guaranteed to match the total suitability score.
     */
    public int computeTotal() {
        int base = colorHarmony + formalityAlignment + seasonWeatherCompatibility + patternRestraint + preferenceMatch;
        return Math.clamp(base - wearPenalty, 0, 100);
    }
}
