package com.groomai.outfit.enums;

import com.groomai.wardrobe.enums.FormalityLevel;

import java.util.Set;

/**
 * Occasion context supported by the outfit recommendation engine.
 */
public enum Occasion {
    CASUAL(FormalityLevel.CASUAL, Set.of(FormalityLevel.CASUAL, FormalityLevel.SMART_CASUAL)),
    SMART_CASUAL(FormalityLevel.SMART_CASUAL, Set.of(FormalityLevel.CASUAL, FormalityLevel.SMART_CASUAL, FormalityLevel.BUSINESS_CASUAL)),
    BUSINESS_CASUAL(FormalityLevel.BUSINESS_CASUAL, Set.of(FormalityLevel.SMART_CASUAL, FormalityLevel.BUSINESS_CASUAL)),
    FORMAL(FormalityLevel.FORMAL, Set.of(FormalityLevel.BUSINESS_CASUAL, FormalityLevel.FORMAL)),
    DATE_NIGHT(FormalityLevel.SMART_CASUAL, Set.of(FormalityLevel.CASUAL, FormalityLevel.SMART_CASUAL, FormalityLevel.BUSINESS_CASUAL)),
    LOUNGEWEAR(FormalityLevel.CASUAL, Set.of(FormalityLevel.CASUAL)),
    ATHLETIC(FormalityLevel.CASUAL, Set.of(FormalityLevel.CASUAL));

    private final FormalityLevel targetFormality;
    private final Set<FormalityLevel> permittedFormalityLevels;

    Occasion(FormalityLevel targetFormality, Set<FormalityLevel> permittedFormalityLevels) {
        this.targetFormality = targetFormality;
        this.permittedFormalityLevels = permittedFormalityLevels;
    }

    public FormalityLevel getTargetFormality() {
        return targetFormality;
    }

    public Set<FormalityLevel> getPermittedFormalityLevels() {
        return permittedFormalityLevels;
    }

    public boolean isPermitted(FormalityLevel formalityLevel) {
        return permittedFormalityLevels.contains(formalityLevel);
    }
}
