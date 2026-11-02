package com.groomai.user.dto;

import com.groomai.user.entity.StylePreferences;
import com.groomai.user.enums.PreferredFit;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Response payload representing a user's style preferences profile.
 * Matching docs/API_SPEC.md Section 3.4.
 */
public record StylePreferencesResponseDto(
    boolean profileCompleted,
    PrimaryStyle primaryStyle,
    PreferredFit preferredFit,
    List<Color> colorPreferences,
    List<Color> avoidedColors,
    List<Pattern> patternPreferences,
    List<Pattern> avoidedPatterns,
    FormalityLevel defaultFormality,
    List<StyleGoal> styleGoals,
    Instant updatedAt
) {
    /**
     * Factory method for unconfigured users (no database row exists yet).
     */
    public static StylePreferencesResponseDto unconfigured() {
        return new StylePreferencesResponseDto(
            false,
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            FormalityLevel.CASUAL,
            List.of(),
            Instant.now()
        );
    }

    /**
     * Map from StylePreferences JPA entity to DTO.
     */
    public static StylePreferencesResponseDto fromEntity(StylePreferences entity) {
        if (entity == null) {
            return unconfigured();
        }

        return new StylePreferencesResponseDto(
            entity.isProfileCompleted(),
            parsePrimaryStyle(entity.getPrimaryStyle()),
            parsePreferredFit(entity.getPreferredFit()),
            parseColors(entity.getColorPreferences()),
            parseColors(entity.getAvoidedColors()),
            parsePatterns(entity.getPatternPreferences()),
            parsePatterns(entity.getAvoidedPatterns()),
            parseFormality(entity.getDefaultFormality()),
            parseStyleGoals(entity.getStyleGoals()),
            entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now()
        );
    }

    private static PrimaryStyle parsePrimaryStyle(String s) {
        if (s == null) return PrimaryStyle.CASUAL;
        try {
            return PrimaryStyle.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PrimaryStyle.CASUAL;
        }
    }

    private static PreferredFit parsePreferredFit(String s) {
        if (s == null) return PreferredFit.REGULAR;
        try {
            return PreferredFit.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PreferredFit.REGULAR;
        }
    }

    private static FormalityLevel parseFormality(String s) {
        if (s == null) return FormalityLevel.CASUAL;
        try {
            return FormalityLevel.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FormalityLevel.CASUAL;
        }
    }

    private static List<Color> parseColors(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
            .map(s -> {
                try {
                    return Color.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private static List<Pattern> parsePatterns(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
            .map(s -> {
                try {
                    return Pattern.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private static List<StyleGoal> parseStyleGoals(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
            .map(s -> {
                try {
                    return StyleGoal.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }
}
