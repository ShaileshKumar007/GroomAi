package com.groomai.outfit.scorer;

import com.groomai.outfit.dto.ScoreBreakdownDto;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.outfit.model.ScoredOutfit;
import com.groomai.user.entity.StylePreferences;
import com.groomai.wardrobe.entity.WardrobeItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates deterministic scoring across all 5 modular components and wear history deductions.
 */
@Component
public class OutfitScoringEngine {

    private final ColorCompatibilityScorer colorScorer;
    private final FormalityCompatibilityScorer formalityScorer;
    private final SeasonWeatherCompatibilityScorer seasonWeatherScorer;
    private final PatternCompatibilityScorer patternScorer;
    private final PreferenceCompatibilityScorer preferenceScorer;
    private final WearHistoryPenaltyScorer wearScorer;

    @Autowired
    public OutfitScoringEngine(
        ColorCompatibilityScorer colorScorer,
        FormalityCompatibilityScorer formalityScorer,
        SeasonWeatherCompatibilityScorer seasonWeatherScorer,
        PatternCompatibilityScorer patternScorer,
        PreferenceCompatibilityScorer preferenceScorer,
        WearHistoryPenaltyScorer wearScorer
    ) {
        this.colorScorer = colorScorer;
        this.formalityScorer = formalityScorer;
        this.seasonWeatherScorer = seasonWeatherScorer;
        this.patternScorer = patternScorer;
        this.preferenceScorer = preferenceScorer;
        this.wearScorer = wearScorer;
    }

    public Optional<ScoredOutfit> scoreOutfit(
        CandidateOutfit candidate,
        Occasion occasion,
        TemperatureBracket temperatureBracket,
        WeatherCondition weatherCondition,
        StylePreferences preferences,
        Map<UUID, Integer> wearDaysMap
    ) {
        List<WardrobeItem> items = candidate.getAllItems();

        // 1. Formality gate
        var formalityResult = formalityScorer.score(candidate, occasion);
        if (formalityResult.rejected()) {
            return Optional.empty();
        }

        // 2. Season and weather gate
        var seasonWeatherResult = seasonWeatherScorer.score(candidate, temperatureBracket, weatherCondition);
        if (seasonWeatherResult.rejected()) {
            return Optional.empty();
        }

        // 3. Color harmony
        var colorResult = colorScorer.score(items);

        // 4. Pattern restraint
        var patternResult = patternScorer.score(items);

        // 5. User preferences
        var preferenceResult = preferenceScorer.score(items, preferences);

        // 6. Wear history penalty
        var wearResult = wearScorer.score(items, wearDaysMap);

        // Compute exact score breakdown
        ScoreBreakdownDto breakdown = new ScoreBreakdownDto(
            colorResult.score(),
            formalityResult.score(),
            seasonWeatherResult.score(),
            patternResult.score(),
            preferenceResult.score(),
            wearResult.penalty()
        );

        int suitabilityScore = breakdown.computeTotal();

        // Assemble honest styling rationale
        StringBuilder rationale = new StringBuilder();
        rationale.append(colorResult.rationale()).append(" ");
        rationale.append(formalityResult.rationale()).append(" ");
        rationale.append(seasonWeatherResult.rationale());

        ScoredOutfit scoredOutfit = new ScoredOutfit(
            candidate,
            suitabilityScore,
            breakdown,
            rationale.toString().trim(),
            wearResult.daysSinceWornSum()
        );

        return Optional.of(scoredOutfit);
    }
}
