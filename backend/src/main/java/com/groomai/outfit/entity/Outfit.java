package com.groomai.outfit.entity;

import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a persistent outfit combination.
 * Maps to 'outfits' table defined in V2__wardrobe_schema.sql.
 */
@Entity
@Table(name = "outfits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Outfit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "occasion", nullable = false, length = 50)
    private Occasion occasion;

    @Enumerated(EnumType.STRING)
    @Column(name = "temperature_bracket", length = 32)
    private TemperatureBracket temperatureBracket;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_condition", length = 32)
    private WeatherCondition weatherCondition;

    @Column(name = "styling_rationale", nullable = false, columnDefinition = "TEXT")
    private String stylingRationale;

    @Column(name = "suitability_score", nullable = false)
    private int suitabilityScore;

    @OneToMany(mappedBy = "outfit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OutfitItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public void addItem(OutfitItem item) {
        items.add(item);
        item.setOutfit(this);
    }
}
