package com.groomai.wardrobe;

import com.groomai.user.entity.User;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WardrobeItemTest {

    @Test
    @DisplayName("Should create WardrobeItem with sensible defaults")
    void testWardrobeItemDefaults() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .build();

        WardrobeItem item = WardrobeItem.builder()
            .user(user)
            .name("White Linen Shirt")
            .category(Category.TOP)
            .primaryColor(Color.WHITE)
            .storageKey("wardrobe/test/item1.jpg")
            .build();

        assertThat(item.getName()).isEqualTo("White Linen Shirt");
        assertThat(item.getCategory()).isEqualTo(Category.TOP);
        assertThat(item.getPrimaryColor()).isEqualTo(Color.WHITE);
        assertThat(item.getSecondaryColor()).isNull();
        assertThat(item.getSubcategory()).isNull();
        assertThat(item.getPattern()).isEqualTo(Pattern.SOLID);
        assertThat(item.getFormalityLevel()).isEqualTo(FormalityLevel.CASUAL);
        assertThat(item.getSeason()).isEqualTo(Season.ALL_SEASON);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update updatedAt timestamp on preUpdate")
    void testPreUpdate() throws InterruptedException {
        WardrobeItem item = WardrobeItem.builder()
            .name("Chinos")
            .category(Category.BOTTOM)
            .primaryColor(Color.BEIGE)
            .storageKey("key1")
            .createdAt(Instant.now().minusSeconds(100))
            .updatedAt(Instant.now().minusSeconds(100))
            .build();

        Instant previousUpdate = item.getUpdatedAt();
        Thread.sleep(10);
        item.preUpdate();

        assertThat(item.getUpdatedAt()).isAfter(previousUpdate);
    }
}
