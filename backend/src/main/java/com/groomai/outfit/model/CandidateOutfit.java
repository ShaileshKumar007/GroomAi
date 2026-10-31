package com.groomai.outfit.model;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.FormalityLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representation of an outfit combination candidate composed of user wardrobe items.
 */
public record CandidateOutfit(
    WardrobeItem top,
    WardrobeItem bottom,
    WardrobeItem footwear,
    WardrobeItem outerwear,
    WardrobeItem accessory
) {
    public CandidateOutfit {
        Objects.requireNonNull(top, "Top is required");
        Objects.requireNonNull(bottom, "Bottom is required");
        Objects.requireNonNull(footwear, "Footwear is required");
    }

    public CandidateOutfit(WardrobeItem top, WardrobeItem bottom, WardrobeItem footwear) {
        this(top, bottom, footwear, null, null);
    }

    public CandidateOutfit(WardrobeItem top, WardrobeItem bottom, WardrobeItem footwear, WardrobeItem outerwear) {
        this(top, bottom, footwear, outerwear, null);
    }

    public List<WardrobeItem> getAllItems() {
        List<WardrobeItem> items = new ArrayList<>(4);
        items.add(top);
        items.add(bottom);
        items.add(footwear);
        if (outerwear != null) {
            items.add(outerwear);
        }
        if (accessory != null) {
            items.add(accessory);
        }
        return Collections.unmodifiableList(items);
    }

    public int getFormalityDelta() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (WardrobeItem item : getAllItems()) {
            int rank = getFormalityRank(item.getFormalityLevel());
            if (rank < min) min = rank;
            if (rank > max) max = rank;
        }
        return max - min;
    }

    public static int getFormalityRank(FormalityLevel level) {
        if (level == null) return 1;
        return switch (level) {
            case CASUAL -> 1;
            case SMART_CASUAL -> 2;
            case BUSINESS_CASUAL -> 3;
            case FORMAL -> 4;
        };
    }
}
