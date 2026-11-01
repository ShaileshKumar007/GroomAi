package com.groomai.outfit.exception;

import com.groomai.common.exception.GroomAiException;
import com.groomai.wardrobe.enums.Category;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when the user's active wardrobe does not contain the minimum required core categories (TOP, BOTTOM, FOOTWEAR).
 */
public class InsufficientWardrobeItemsException extends GroomAiException {

    private final List<String> missingCategories;

    public InsufficientWardrobeItemsException(List<Category> missingCategories) {
        super(
            "A minimum of one active Top, Bottom, and Footwear is required to generate outfits.",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "INSUFFICIENT_WARDROBE_ITEMS"
        );
        this.missingCategories = missingCategories != null
            ? missingCategories.stream().map(Enum::name).toList()
            : Collections.emptyList();
    }

    public List<String> getMissingCategories() {
        return missingCategories;
    }
}
