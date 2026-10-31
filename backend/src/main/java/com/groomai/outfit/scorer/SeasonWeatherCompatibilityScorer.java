package com.groomai.outfit.scorer;

import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Season;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic season and weather compatibility scorer.
 * Unifies garment seasonality with optional client-supplied weather context to eliminate double-counting.
 * Score range: [0, 20].
 */
@Component
public class SeasonWeatherCompatibilityScorer {

    public record SeasonWeatherScoreResult(int score, boolean rejected, String rationale) {}

    public SeasonWeatherScoreResult score(
        CandidateOutfit candidate,
        TemperatureBracket temperatureBracket,
        WeatherCondition weatherCondition
    ) {
        List<WardrobeItem> items = candidate.getAllItems();
        Set<Season> seasons = EnumSet.noneOf(Season.class);
        for (WardrobeItem item : items) {
            if (item.getSeason() != null) {
                seasons.add(item.getSeason());
            }
        }

        // Hard gate 1: Summer + Winter collision is always rejected
        if (seasons.contains(Season.SUMMER) && seasons.contains(Season.WINTER)) {
            return new SeasonWeatherScoreResult(0, true, "Direct seasonal clash: combining Summer and Winter pieces.");
        }

        // Hard gate 2: Weather temperature infeasibility when user provided weather
        if (temperatureBracket != null) {
            if (temperatureBracket == TemperatureBracket.HOT && seasons.contains(Season.WINTER)) {
                return new SeasonWeatherScoreResult(0, true, "Winter garments are unwearable in Hot weather.");
            }
            if (temperatureBracket == TemperatureBracket.COLD && seasons.contains(Season.SUMMER)) {
                return new SeasonWeatherScoreResult(0, true, "Summer garments are unwearable in Cold weather.");
            }
        }

        // Case 1: Weather context was NOT supplied
        if (temperatureBracket == null) {
            int score;
            String rationale;

            if (seasons.isEmpty() || seasons.equals(EnumSet.of(Season.ALL_SEASON))) {
                score = 20;
                rationale = "All-season versatile pieces. Weather context was not supplied; evaluated based on garment seasonality only.";
            } else if (isAdjacentSeasonMix(seasons)) {
                score = 18;
                rationale = "Harmonious transitional seasonal pairing. Weather context was not supplied; evaluated based on garment seasonality only.";
            } else {
                score = 16;
                rationale = "Compatible garment seasonality. Weather context was not supplied; evaluated based on garment seasonality only.";
            }

            return new SeasonWeatherScoreResult(Math.clamp(score, 0, 20), false, rationale);
        }

        // Case 2: User-supplied weather context
        int score = 18;
        StringBuilder rationale = new StringBuilder("Based on user-supplied weather: ")
            .append(temperatureBracket);
        if (weatherCondition != null) {
            rationale.append(" / ").append(weatherCondition);
        }
        rationale.append(". ");

        switch (temperatureBracket) {
            case HOT -> {
                if (candidate.outerwear() != null) {
                    score = 14;
                    rationale.append("Outerwear layer may feel uncomfortably warm in hot weather.");
                } else {
                    score = 20;
                    rationale.append("Breathable, lightweight pieces suited for hot weather.");
                }
            }
            case COLD -> {
                if (candidate.outerwear() != null) {
                    score = 20;
                    rationale.append("Layered with outerwear for optimal cold weather protection.");
                } else {
                    score = 14;
                    rationale.append("Cold temperatures call for an outerwear layer.");
                }
            }
            case COOL -> {
                if (candidate.outerwear() != null) {
                    score = 20;
                    rationale.append("Outerwear layer provides comfortable warmth for cool weather.");
                } else {
                    score = 16;
                    rationale.append("Light core outfit suitable for cool conditions.");
                }
            }
            case WARM, MILD -> {
                score = 20;
                rationale.append("Garments provide balanced breathability for comfortable conditions.");
            }
        }

        if (weatherCondition == WeatherCondition.RAINY || weatherCondition == WeatherCondition.SNOWY) {
            // Weather conditions reward outerwear protection if present
            if (candidate.outerwear() != null && score < 20) {
                score = Math.min(20, score + 2);
            }
        }

        return new SeasonWeatherScoreResult(Math.clamp(score, 0, 20), false, rationale.toString().trim());
    }

    private boolean isAdjacentSeasonMix(Set<Season> seasons) {
        if (seasons.contains(Season.SPRING) && (seasons.contains(Season.SUMMER) || seasons.contains(Season.FALL))) {
            return true;
        }
        if (seasons.contains(Season.FALL) && (seasons.contains(Season.WINTER) || seasons.contains(Season.SPRING))) {
            return true;
        }
        return seasons.contains(Season.ALL_SEASON) && seasons.size() <= 2;
    }
}
