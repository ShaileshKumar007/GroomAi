package com.groomai.outfit.scorer;

import com.groomai.wardrobe.entity.WardrobeItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic wear history repetition penalty scorer.
 * Deduction range: [0, 20].
 */
@Component
public class WearHistoryPenaltyScorer {

    public record WearPenaltyResult(int penalty, int daysSinceWornSum, String rationale) {}

    public WearPenaltyResult score(List<WardrobeItem> items, Map<UUID, Integer> itemDaysSinceLastWornMap) {
        if (itemDaysSinceLastWornMap == null || itemDaysSinceLastWornMap.isEmpty() || items == null || items.isEmpty()) {
            // Missing wear history policy: zero penalty fallback, never infer or invent wear dates
            return new WearPenaltyResult(0, 999, "Fresh rotation (no recent wear history logged).");
        }

        int totalPenalty = 0;
        int totalDaysSum = 0;
        int minDays = Integer.MAX_VALUE;

        for (WardrobeItem item : items) {
            Integer days = itemDaysSinceLastWornMap.get(item.getId());
            if (days == null || days > 7) {
                totalDaysSum += 14; // Default unlogged / well-rested item
            } else {
                totalDaysSum += days;
                if (days < minDays) {
                    minDays = days;
                }

                if (days == 0) {
                    totalPenalty += 20;
                } else if (days <= 3) {
                    totalPenalty += 12;
                } else {
                    totalPenalty += 5;
                }
            }
        }

        int finalPenalty = Math.clamp(totalPenalty, 0, 20);
        String rationale;
        if (finalPenalty == 0) {
            rationale = "Fresh rotation; items have not been worn recently.";
        } else if (minDays == 0) {
            rationale = "Contains items worn earlier today; repetition penalty applied.";
        } else {
            rationale = "Contains items worn within the last " + minDays + " days; modest repetition penalty applied.";
        }

        return new WearPenaltyResult(finalPenalty, totalDaysSum, rationale);
    }
}
