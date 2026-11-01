package com.groomai.outfit.repository;

import com.groomai.outfit.entity.OutfitItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for OutfitItem entity.
 */
@Repository
public interface OutfitItemRepository extends JpaRepository<OutfitItem, UUID> {

    List<OutfitItem> findByOutfitId(UUID outfitId);

    List<OutfitItem> findByWardrobeItemId(UUID wardrobeItemId);
}
