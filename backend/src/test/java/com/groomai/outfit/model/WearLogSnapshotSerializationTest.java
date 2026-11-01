package com.groomai.outfit.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.ScoreStatus;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WearLogSnapshotSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("WearLogSnapshot serializes to JSON and deserializes back faithfully")
    void testSerializationRoundTrip() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID wardrobeItemId = UUID.randomUUID();

        WearLogSnapshot.WearLogItemSnapshot item = new WearLogSnapshot.WearLogItemSnapshot(
            wardrobeItemId,
            "TOP",
            Category.TOP,
            "Oxford Shirt",
            Color.NAVY,
            Color.WHITE,
            Pattern.STRIPED,
            "100% Linen",
            "J.Crew",
            "wardrobe/user-1/linen.jpg"
        );

        WearLogSnapshot snapshot = new WearLogSnapshot(
            1,
            outfitId,
            "Summer Linen Look",
            Occasion.CASUAL,
            TemperatureBracket.HOT,
            WeatherCondition.SUNNY,
            "Breathable summer linen pairing",
            92,
            ScoreStatus.CALCULATED,
            List.of(item)
        );

        String json = objectMapper.writeValueAsString(snapshot);
        assertThat(json).contains("\"version\":1");
        assertThat(json).contains("\"outfitId\":\"" + outfitId + "\"");
        assertThat(json).contains("\"name\":\"Summer Linen Look\"");
        assertThat(json).contains("\"scoreStatus\":\"CALCULATED\"");
        assertThat(json).contains("\"fabric\":\"100% Linen\"");
        assertThat(json).contains("\"brand\":\"J.Crew\"");

        WearLogSnapshot deserialized = objectMapper.readValue(json, WearLogSnapshot.class);
        assertThat(deserialized.version()).isEqualTo(1);
        assertThat(deserialized.outfitId()).isEqualTo(outfitId);
        assertThat(deserialized.name()).isEqualTo("Summer Linen Look");
        assertThat(deserialized.occasion()).isEqualTo(Occasion.CASUAL);
        assertThat(deserialized.suitabilityScore()).isEqualTo(92);
        assertThat(deserialized.scoreStatus()).isEqualTo(ScoreStatus.CALCULATED);
        assertThat(deserialized.items()).hasSize(1);

        WearLogSnapshot.WearLogItemSnapshot deserializedItem = deserialized.items().get(0);
        assertThat(deserializedItem.wardrobeItemId()).isEqualTo(wardrobeItemId);
        assertThat(deserializedItem.category()).isEqualTo(Category.TOP);
        assertThat(deserializedItem.primaryColor()).isEqualTo(Color.NAVY);
        assertThat(deserializedItem.pattern()).isEqualTo(Pattern.STRIPED);
        assertThat(deserializedItem.fabric()).isEqualTo("100% Linen");
        assertThat(deserializedItem.brand()).isEqualTo("J.Crew");
    }

    @Test
    @DisplayName("WearLogSnapshot serializes and deserializes null score and CUSTOM status cleanly")
    void testNullScoreSerialization() throws Exception {
        WearLogSnapshot snapshot = new WearLogSnapshot(
            1,
            UUID.randomUUID(),
            null,
            Occasion.FORMAL,
            null,
            null,
            "Custom pairing",
            null,
            ScoreStatus.CUSTOM,
            List.of()
        );

        String json = objectMapper.writeValueAsString(snapshot);
        assertThat(json).contains("\"suitabilityScore\":null");
        assertThat(json).contains("\"scoreStatus\":\"CUSTOM\"");

        WearLogSnapshot deserialized = objectMapper.readValue(json, WearLogSnapshot.class);
        assertThat(deserialized.suitabilityScore()).isNull();
        assertThat(deserialized.scoreStatus()).isEqualTo(ScoreStatus.CUSTOM);
    }
}
