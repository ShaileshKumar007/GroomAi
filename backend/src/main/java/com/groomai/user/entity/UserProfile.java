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

import java.time.Instant;
import java.util.UUID;

/**
 * User profile and biometric privacy consent entity.
 * Maps to 'user_profiles' table in docs/DATA_MODEL.md.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "age_bracket", length = 32)
    private String ageBracket;

    @Column(name = "wardrobe_storage_consent_granted", nullable = false)
    @Builder.Default
    private boolean wardrobeStorageConsentGranted = false;

    @Column(name = "wardrobe_storage_consent_granted_at")
    private Instant wardrobeStorageConsentGrantedAt;

    @Column(name = "ai_styling_consent_granted", nullable = false)
    @Builder.Default
    private boolean aiStylingConsentGranted = false;

    @Column(name = "ai_styling_consent_granted_at")
    private Instant aiStylingConsentGrantedAt;

    @Column(name = "photo_consent_granted", nullable = false)
    @Builder.Default
    private boolean photoConsentGranted = false;

    @Column(name = "photo_consent_granted_at")
    private Instant photoConsentGrantedAt;

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
