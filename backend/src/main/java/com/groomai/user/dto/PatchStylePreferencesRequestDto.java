package com.groomai.user.dto;

import com.groomai.user.enums.PreferredFit;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;

import java.util.List;

/**
 * Request payload for PATCH /api/v1/profile/style-preferences (partial update).
 * Fields are optional (nullable). Omitted fields remain null and are preserved.
 * An explicitly supplied empty list (e.g. []) clears that preference collection.
 */
public record PatchStylePreferencesRequestDto(
    PrimaryStyle primaryStyle,
    PreferredFit preferredFit,
    List<Color> colorPreferences,
    List<Color> avoidedColors,
    List<Pattern> patternPreferences,
    List<Pattern> avoidedPatterns,
    FormalityLevel defaultFormality,
    List<StyleGoal> styleGoals
) {}
