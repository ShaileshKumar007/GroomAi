package com.groomai.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User style preferences entity.
 * Maps to 'style_preferences' table in docs/DATA_MODEL.md.
 */
@Entity
@Table(name = "style_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StylePreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "primary_style", nullable = false, length = 50)
    @Builder.Default
    private String primaryStyle = "CASUAL";

    @Column(name = "preferred_fit", nullable = false, length = 32)
    @Builder.Default
    private String preferredFit = "REGULAR";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "color_preferences", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> colorPreferences = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avoided_colors", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> avoidedColors = new ArrayList<>();

    @Column(name = "default_formality", nullable = false, length = 32)
    @Builder.Default
    private String defaultFormality = "CASUAL";

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
