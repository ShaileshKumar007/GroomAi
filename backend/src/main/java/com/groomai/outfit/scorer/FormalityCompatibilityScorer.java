package com.groomai.outfit.scorer;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.FormalityLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic formality consistency and occasion alignment scorer.
 * Score range: [0, 25].
 */
@Component
public class FormalityCompatibilityScorer {

    public record FormalityScoreResult(int score, boolean rejected, String rationale) {}

    public FormalityScoreResult score(CandidateOutfit candidate, Occasion occasion) {
        List<WardrobeItem> items = candidate.getAllItems();
        int delta = candidate.getFormalityDelta();

        // Hard gate: ΔF >= 3 is an irreconcilable formality clash (e.g. gym shorts + tuxedo jacket)
        if (delta >= 3) {
            return new FormalityScoreResult(0, true, "Severe formality clash across garments (delta >= 3).");
        }

        // Check occasion constraints if specified
        if (occasion != null) {
            for (WardrobeItem item : items) {
                if (!occasion.isPermitted(item.getFormalityLevel())) {
                    return new FormalityScoreResult(
                        0,
                        true,
                        "Garment '" + item.getName() + "' with formality " + item.getFormalityLevel()
                            + " is outside acceptable bounds for occasion " + occasion + "."
                    );
                }
            }
        }

        int score;
        String rationale;

        if (occasion != null) {
            boolean allMatchTarget = items.stream()
                .allMatch(item -> item.getFormalityLevel() == occasion.getTargetFormality());

            if (allMatchTarget) {
                score = 25;
                rationale = "All garments align uniformly at the " + occasion.getTargetFormality() + " tier, ideal for " + occasion + ".";
            } else if (delta == 1) {
                score = 20;
                rationale = "Intentional hi-lo styling with adjacent formality tiers suitable for " + occasion + ".";
            } else {
                score = 18;
                rationale = "Acceptable formality variation within permitted occasion bounds.";
            }
        } else {
            // No occasion requested, purely internal formality delta
            if (delta == 0) {
                score = 25;
                rationale = "Uniform formality level across all pieces.";
            } else if (delta == 1) {
                score = 20;
                rationale = "Adjacent formality tiers creating clean, intentional styling contrast.";
            } else {
                score = 8;
                rationale = "Noticeable formality gap between pieces.";
            }
        }

        return new FormalityScoreResult(Math.clamp(score, 0, 25), false, rationale);
    }
}
