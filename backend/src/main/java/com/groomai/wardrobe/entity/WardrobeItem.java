package com.groomai.wardrobe.entity;

import com.groomai.user.entity.User;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Wardrobe item entity representing a physical clothing item owned by a user.
 * Maps to 'wardrobe_items' table in docs/DATA_MODEL.md.
 */
@Entity
@Table(name = "wardrobe_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WardrobeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private Category category;

    @Column(name = "subcategory", length = 50)
    private String subcategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_color", nullable = false, length = 32)
    private Color primaryColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_color", length = 32)
    private Color secondaryColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern", nullable = false, length = 32)
    @Builder.Default
    private Pattern pattern = Pattern.SOLID;

    @Enumerated(EnumType.STRING)
    @Column(name = "formality_level", nullable = false, length = 32)
    @Builder.Default
    private FormalityLevel formalityLevel = FormalityLevel.CASUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "season", nullable = false, length = 32)
    @Builder.Default
    private Season season = Season.ALL_SEASON;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ItemStatus status = ItemStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
