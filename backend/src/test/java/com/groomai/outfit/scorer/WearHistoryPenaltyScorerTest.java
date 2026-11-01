package com.groomai.outfit.scorer;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WearHistoryPenaltyScorerTest {

    private WearHistoryPenaltyScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new WearHistoryPenaltyScorer();
    }

    private WardrobeItem createItem(Category category) {
        return WardrobeItem.builder()
            .id(UUID.randomUUID())
            .name("Item " + category)
            .category(category)
            .storageKey("wardrobe/user/" + UUID.randomUUID() + ".jpg")
            .build();
    }

    @Test
    @DisplayName("Empty or null wear history applies zero deduction without inventing wear dates")
    void testEmptyWearHistoryAppliesZeroPenalty() {
        var top = createItem(Category.TOP);
        var bottom = createItem(Category.BOTTOM);
        var shoes = createItem(Category.FOOTWEAR);

        var result = scorer.score(List.of(top, bottom, shoes), Collections.emptyMap());
        assertEquals(0, result.penalty());
        assertTrue(result.rationale().contains("no recent wear history logged"));
    }

    @Test
    @DisplayName("Item worn today (0 days) applies maximum 20-point penalty")
    void testItemWornTodayApplies20PointPenalty() {
        var top = createItem(Category.TOP);
        var bottom = createItem(Category.BOTTOM);
        var shoes = createItem(Category.FOOTWEAR);

        Map<UUID, Integer> wearMap = Map.of(top.getId(), 0);
        var result = scorer.score(List.of(top, bottom, shoes), wearMap);

        assertEquals(20, result.penalty());
        assertTrue(result.rationale().contains("worn earlier today"));
    }

    @Test
    @DisplayName("Item worn 2 days ago applies 12-point penalty")
    void testItemWornRecentlyApplies12PointPenalty() {
        var top = createItem(Category.TOP);
        var bottom = createItem(Category.BOTTOM);
        var shoes = createItem(Category.FOOTWEAR);

        Map<UUID, Integer> wearMap = Map.of(bottom.getId(), 2);
        var result = scorer.score(List.of(top, bottom, shoes), wearMap);

        assertEquals(12, result.penalty());
    }

    @Test
    @DisplayName("Cumulative penalty is clamped to 20 points")
    void testPenaltyCap() {
        var top = createItem(Category.TOP);
        var bottom = createItem(Category.BOTTOM);
        var shoes = createItem(Category.FOOTWEAR);

        // 12 + 12 = 24 -> clamped to 20
        Map<UUID, Integer> wearMap = Map.of(top.getId(), 2, bottom.getId(), 3);
        var result = scorer.score(List.of(top, bottom, shoes), wearMap);

        assertEquals(20, result.penalty());
    }
}
