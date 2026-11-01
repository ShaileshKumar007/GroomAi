package com.groomai.outfit.repository;

import com.groomai.outfit.entity.Outfit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Outfit entity.
 * Guarantees tenant isolation by scoping operations to userId.
 */
@Repository
public interface OutfitRepository extends JpaRepository<Outfit, UUID> {

    Optional<Outfit> findByIdAndUserId(UUID id, UUID userId);

    Page<Outfit> findByUserId(UUID userId, Pageable pageable);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
