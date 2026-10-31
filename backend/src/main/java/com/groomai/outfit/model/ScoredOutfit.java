package com.groomai.outfit.model;

import com.groomai.outfit.dto.ScoreBreakdownDto;

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
    int formalityDelta
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
            candidate.getFormalityDelta()
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

        // 2. Least recently worn (larger days sum first)
        int wearCmp = Integer.compare(b.daysSinceLastWornSum(), a.daysSinceLastWornSum());
        if (wearCmp != 0) return wearCmp;

        // 3. Higher color harmony sub-score (descending)
        int colorCmp = Integer.compare(
            b.scoreBreakdown().colorHarmony(),
            a.scoreBreakdown().colorHarmony()
        );
        if (colorCmp != 0) return colorCmp;

        // 4. Lower formality delta (ascending: 0 is better than 1, etc.)
        int deltaCmp = Integer.compare(a.formalityDelta(), b.formalityDelta());
        if (deltaCmp != 0) return deltaCmp;

        // 5. Stable lexicographical UUID comparison
        int topCmp = a.candidate().top().getId().compareTo(b.candidate().top().getId());
        if (topCmp != 0) return topCmp;

        int bottomCmp = a.candidate().bottom().getId().compareTo(b.candidate().bottom().getId());
        if (bottomCmp != 0) return bottomCmp;

        int footwearCmp = a.candidate().footwear().getId().compareTo(b.candidate().footwear().getId());
        if (footwearCmp != 0) return footwearCmp;

        if (a.candidate().outerwear() != null && b.candidate().outerwear() != null) {
            return a.candidate().outerwear().getId().compareTo(b.candidate().outerwear().getId());
        } else if (a.candidate().outerwear() != null) {
            return -1;
        } else if (b.candidate().outerwear() != null) {
            return 1;
        }

        return 0;
    };

    /**
     * Comparator for min-heap priority queue.
     * The lowest-ranked outfit is at the head of the heap so that polling evicts the worst candidate.
     */
    public static final Comparator<ScoredOutfit> MIN_HEAP_COMPARATOR = RANKING_COMPARATOR.reversed();
}
