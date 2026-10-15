package com.groomai.wardrobe.repository;

import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for WardrobeItem entity.
 * Enforces user tenancy isolation across all queries.
 */
@Repository
public interface WardrobeItemRepository extends JpaRepository<WardrobeItem, UUID> {

    Page<WardrobeItem> findByUserId(UUID userId, Pageable pageable);

    Page<WardrobeItem> findByUserIdAndStatus(UUID userId, ItemStatus status, Pageable pageable);

    Page<WardrobeItem> findByUserIdAndCategoryAndStatus(UUID userId, Category category, ItemStatus status, Pageable pageable);

    Page<WardrobeItem> findByUserIdAndCategory(UUID userId, Category category, Pageable pageable);

    List<WardrobeItem> findByUserIdAndStatus(UUID userId, ItemStatus status);

    Optional<WardrobeItem> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByStorageKey(String storageKey);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, ItemStatus status);

    long countByUserIdAndCategoryAndStatus(UUID userId, Category category, ItemStatus status);

    @Query("SELECT w.storageKey FROM WardrobeItem w WHERE w.user.id = :userId")
    List<String> findStorageKeysByUserId(@Param("userId") UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
