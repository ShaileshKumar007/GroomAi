package com.groomai.outfit.entity;

import com.groomai.wardrobe.entity.WardrobeItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Entity mapping an individual wardrobe item to a persistent outfit.
 * Maps to 'outfit_items' table defined in V2__wardrobe_schema.sql.
 * Foreign key to wardrobe_items uses ON DELETE SET NULL to preserve outfit text if an item is eradicated.
 */
@Entity
@Table(name = "outfit_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutfitItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_id", nullable = false)
    private Outfit outfit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wardrobe_item_id")
    private WardrobeItem wardrobeItem;

    @Column(name = "item_role", nullable = false, length = 32)
    private String itemRole;
}
