package com.groomai.wardrobe.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WardrobeEnumsTest {

    @Test
    @DisplayName("Category enum should contain all 5 required categories")
    void testCategories() {
        assertThat(Category.values()).containsExactly(
            Category.TOP,
            Category.BOTTOM,
            Category.FOOTWEAR,
            Category.OUTERWEAR,
            Category.ACCESSORY
        );
    }

    @Test
    @DisplayName("FormalityLevel enum should contain all 4 formality tiers")
    void testFormalityLevels() {
        assertThat(FormalityLevel.values()).containsExactly(
            FormalityLevel.CASUAL,
            FormalityLevel.SMART_CASUAL,
            FormalityLevel.BUSINESS_CASUAL,
            FormalityLevel.FORMAL
        );
    }

    @Test
    @DisplayName("Color enum should contain all 13 documented colors")
    void testColors() {
        assertThat(Color.values()).containsExactly(
            Color.WHITE,
            Color.BLACK,
            Color.NAVY,
            Color.GREY,
            Color.BEIGE,
            Color.BROWN,
            Color.BLUE,
            Color.GREEN,
            Color.OLIVE,
            Color.RED,
            Color.BURGUNDY,
            Color.YELLOW,
            Color.MULTI
        );
    }

    @Test
    @DisplayName("Pattern enum should contain all 6 surface patterns")
    void testPatterns() {
        assertThat(Pattern.values()).containsExactly(
            Pattern.SOLID,
            Pattern.STRIPED,
            Pattern.CHECKED,
            Pattern.FLORAL,
            Pattern.GRAPHIC,
            Pattern.TEXTURED
        );
    }

    @Test
    @DisplayName("Season enum should contain all 5 seasonality options")
    void testSeasons() {
        assertThat(Season.values()).containsExactly(
            Season.SPRING,
            Season.SUMMER,
            Season.FALL,
            Season.WINTER,
            Season.ALL_SEASON
        );
    }

    @Test
    @DisplayName("ItemStatus enum should contain ACTIVE and ARCHIVED")
    void testItemStatuses() {
        assertThat(ItemStatus.values()).containsExactly(
            ItemStatus.ACTIVE,
            ItemStatus.ARCHIVED
        );
    }
}
