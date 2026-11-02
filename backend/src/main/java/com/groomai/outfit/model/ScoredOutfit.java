package com.groomai.outfit.model;

import com.groomai.outfit.dto.ScoreBreakdownDto;
import com.groomai.wardrobe.entity.WardrobeItem;

import java.util.Comparator;
import java.util.UUID;

/**
 * Scored candidate outfit with breakdown, explanation, and deterministic tie-breaking criteria.
 */
public record ScoredOutfit(
    UUID id,
    CandidateOutfit candidate,
    int suitabilityScore,
    ScoreBreakdownDto scoreBreakdown,
    String stylingRationale,
    int daysSinceLastWornSum,
    int formalityDelta,
    int neverWornCount,
    double normalizedDaysSinceLastWorn,
    boolean hasExpandRotationGoal
) {
    public ScoredOutfit(
        CandidateOutfit candidate,
        int suitabilityScore,
        ScoreBreakdownDto scoreBreakdown,
        String stylingRationale,
        int daysSinceLastWornSum
    ) {
        this(
            UUID.randomUUID(),
            candidate,
            suitabilityScore,
            scoreBreakdown,
            stylingRationale,
            daysSinceLastWornSum,
            candidate.getFormalityDelta(),
            0,
            daysSinceLastWornSum,
            false
        );
    }

    public ScoredOutfit(
        CandidateOutfit candidate,
        int suitabilityScore,
        ScoreBreakdownDto scoreBreakdown,
        String stylingRationale,
        int daysSinceLastWornSum,
        int neverWornCount,
        double normalizedDaysSinceLastWorn,
        boolean hasExpandRotationGoal
    ) {
        this(
            UUID.randomUUID(),
            candidate,
            suitabilityScore,
            scoreBreakdown,
            stylingRationale,
            daysSinceLastWornSum,
            candidate.getFormalityDelta(),
            neverWornCount,
            normalizedDaysSinceLastWorn,
            hasExpandRotationGoal
        );
    }

    /**
     * Comparator defining full ranking priority (highest ranked / best outfit first).
     * Used for sorting final recommendation results descending.
     */
    public static final Comparator<ScoredOutfit> RANKING_COMPARATOR = (a, b) -> {
        // 1. Total suitability score (descending)
        int scoreCmp = Integer.compare(b.suitabilityScore(), a.suitabilityScore());
        if (scoreCmp != 0) return scoreCmp;

        // If EXPAND_ROTATION is active for both candidates, prioritize rotation discovery
        if (a.hasExpandRotationGoal() && b.hasExpandRotationGoal()) {
            // 2a. Never-worn garment count (descending)
            int neverWornCmp = Integer.compare(b.neverWornCount(), a.neverWornCount());
            if (neverWornCmp != 0) return neverWornCmp;

            // 3a. Normalized days since last wear (descending: longer rested clothes first)
            int wearCmp = Double.compare(b.normalizedDaysSinceLastWorn(), a.normalizedDaysSinceLastWorn());
            if (wearCmp != 0) return wearCmp;

            // 4a. Higher color harmony sub-score (descending)
            int colorCmp = Integer.compare(
                b.scoreBreakdown().colorHarmony(),
                a.scoreBreakdown().colorHarmony()
            );
            if (colorCmp != 0) return colorCmp;

            // 5a. Lower formality delta (ascending: 0 is better than 1, etc.)
            int deltaCmp = Integer.compare(a.formalityDelta(), b.formalityDelta());
            if (deltaCmp != 0) return deltaCmp;
        } else if (a.hasExpandRotationGoal() != b.hasExpandRotationGoal()) {
            return Boolean.compare(b.hasExpandRotationGoal(), a.hasExpandRotationGoal());
        } else {
            // Standard / default ranking (preserves Stage 4A baseline harmony prioritization)
            // 2b. Never-worn garment count (descending: outfits activating unworn items prioritized)
            int neverWornCmp = Integer.compare(b.neverWornCount(), a.neverWornCount());
            if (neverWornCmp != 0) return neverWornCmp;

            // 3b. Higher color harmony sub-score (descending)
            int colorCmp = Integer.compare(
                b.scoreBreakdown().colorHarmony(),
                a.scoreBreakdown().colorHarmony()
            );
            if (colorCmp != 0) return colorCmp;

            // 4b. Lower formality delta (ascending)
            int deltaCmp = Integer.compare(a.formalityDelta(), b.formalityDelta());
            if (deltaCmp != 0) return deltaCmp;

            // 5b. Normalized days since last wear (descending)
            int wearCmp = Double.compare(b.normalizedDaysSinceLastWorn(), a.normalizedDaysSinceLastWorn());
            if (wearCmp != 0) return wearCmp;
        }

        // 6. Stable lexicographical UUID comparison across ALL slots (top, bottom, footwear, outerwear, accessory)
        return compareStableSlots(a.candidate(), b.candidate());
    };

    private static int compareStableSlots(CandidateOutfit a, CandidateOutfit b) {
        int topCmp = a.top().getId().compareTo(b.top().getId());
        if (topCmp != 0) return topCmp;

        int bottomCmp = a.bottom().getId().compareTo(b.bottom().getId());
        if (bottomCmp != 0) return bottomCmp;

        int footwearCmp = a.footwear().getId().compareTo(b.footwear().getId());
        if (footwearCmp != 0) return footwearCmp;

        int outerCmp = compareOptionalSlot(a.outerwear(), b.outerwear());
        if (outerCmp != 0) return outerCmp;

        return compareOptionalSlot(a.accessory(), b.accessory());
    }

    private static int compareOptionalSlot(WardrobeItem a, WardrobeItem b) {
        if (a != null && b != null) {
            return a.getId().compareTo(b.getId());
        } else if (a != null) {
            return -1; // Present item comes before absent item
        } else if (b != null) {
            return 1;
        }
        return 0;
    }

    /**
     * Comparator for min-heap priority queue.
     * The lowest-ranked outfit is at the head of the heap so that polling evicts the worst candidate.
     */
    public static final Comparator<ScoredOutfit> MIN_HEAP_COMPARATOR = RANKING_COMPARATOR.reversed();
}
