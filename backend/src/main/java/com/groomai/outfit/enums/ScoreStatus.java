package com.groomai.outfit.enums;

/**
 * Indicates how an outfit's suitability score was produced.
 */
public enum ScoreStatus {
    /**
     * Authoritatively calculated by the backend deterministic scoring engine.
     */
    CALCULATED,

    /**
     * User-curated custom combination that may not satisfy strict compatibility gates.
     */
    CUSTOM,

    /**
     * Legacy, unverified, or unscored outfit.
     */
    UNSCORED
}
