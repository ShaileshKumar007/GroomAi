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
}
