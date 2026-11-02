package com.groomai.user.dto;

import com.groomai.user.enums.PreferredFit;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for PUT /api/v1/profile/style-preferences (complete resource replacement).
 * Matching docs/API_SPEC.md Section 3.5.
 */
public record UpdateStylePreferencesRequestDto(
    @NotNull(message = "primaryStyle is required.")
    PrimaryStyle primaryStyle,

    @NotNull(message = "preferredFit is required.")
    PreferredFit preferredFit,

    @NotNull(message = "colorPreferences is required.")
    @Size(max = 10, message = "Maximum 10 color preferences allowed.")
    List<Color> colorPreferences,

    @NotNull(message = "avoidedColors is required.")
    @Size(max = 10, message = "Maximum 10 avoided colors allowed.")
    List<Color> avoidedColors,

    @NotNull(message = "patternPreferences is required.")
    @Size(max = 5, message = "Maximum 5 pattern preferences allowed.")
    List<Pattern> patternPreferences,

    @NotNull(message = "avoidedPatterns is required.")
    @Size(max = 5, message = "Maximum 5 avoided patterns allowed.")
    List<Pattern> avoidedPatterns,

    @NotNull(message = "defaultFormality is required.")
    FormalityLevel defaultFormality,

    @NotNull(message = "styleGoals is required.")
    @Size(max = 3, message = "Maximum 3 style goals allowed.")
    List<StyleGoal> styleGoals
) {}
