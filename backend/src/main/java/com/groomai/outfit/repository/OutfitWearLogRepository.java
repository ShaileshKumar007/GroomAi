package com.groomai.outfit.repository;

import com.groomai.outfit.entity.OutfitWearLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for OutfitWearLog entity.
 */
@Repository
public interface OutfitWearLogRepository extends JpaRepository<OutfitWearLog, UUID> {

    Page<OutfitWearLog> findByUserId(UUID userId, Pageable pageable);

    boolean existsByUserIdAndOutfitIdAndWornDate(UUID userId, UUID outfitId, LocalDate wornDate);

    @Query("""
        SELECT oi.wardrobeItem.id, MAX(owl.wornDate)
        FROM OutfitWearLog owl
        JOIN owl.outfit o
        JOIN o.items oi
        WHERE owl.user.id = :userId
          AND oi.wardrobeItem.id IS NOT NULL
        GROUP BY oi.wardrobeItem.id
    """)
    List<Object[]> findLatestWornDatesByUserId(@Param("userId") UUID userId);
}
