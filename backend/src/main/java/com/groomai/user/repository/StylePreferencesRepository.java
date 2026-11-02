package com.groomai.user.repository;

import com.groomai.user.entity.StylePreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for StylePreferences entity.
 */
@Repository
public interface StylePreferencesRepository extends JpaRepository<StylePreferences, UUID> {

    Optional<StylePreferences> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
        INSERT INTO style_preferences (
            id, user_id, primary_style, preferred_fit, default_formality,
            profile_completed, color_preferences, avoided_colors,
            pattern_preferences, avoided_patterns, style_goals,
            created_at, updated_at
        ) VALUES (
            :id, :userId, :primaryStyle, :preferredFit, :defaultFormality,
            :profileCompleted, CAST(:colorPreferences AS jsonb), CAST(:avoidedColors AS jsonb),
            CAST(:patternPreferences AS jsonb), CAST(:avoidedPatterns AS jsonb), CAST(:styleGoals AS jsonb),
            :createdAt, :updatedAt
        )
        ON CONFLICT (user_id)
        DO NOTHING
    """, nativeQuery = true)
    int insertStylePreferencesIdempotent(
        @org.springframework.data.repository.query.Param("id") UUID id,
        @org.springframework.data.repository.query.Param("userId") UUID userId,
        @org.springframework.data.repository.query.Param("primaryStyle") String primaryStyle,
        @org.springframework.data.repository.query.Param("preferredFit") String preferredFit,
        @org.springframework.data.repository.query.Param("defaultFormality") String defaultFormality,
        @org.springframework.data.repository.query.Param("profileCompleted") boolean profileCompleted,
        @org.springframework.data.repository.query.Param("colorPreferences") String colorPreferences,
        @org.springframework.data.repository.query.Param("avoidedColors") String avoidedColors,
        @org.springframework.data.repository.query.Param("patternPreferences") String patternPreferences,
        @org.springframework.data.repository.query.Param("avoidedPatterns") String avoidedPatterns,
        @org.springframework.data.repository.query.Param("styleGoals") String styleGoals,
        @org.springframework.data.repository.query.Param("createdAt") java.time.Instant createdAt,
        @org.springframework.data.repository.query.Param("updatedAt") java.time.Instant updatedAt
    );
}
