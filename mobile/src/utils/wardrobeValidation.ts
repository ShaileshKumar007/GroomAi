import {
  Category,
  Color,
  FormalityLevel,
  Pattern,
  Season,
  WardrobeItemFormMetadata,
} from '../types/wardrobe.types';

export const VALID_CATEGORIES: readonly Category[] = [
  'TOP',
  'BOTTOM',
  'FOOTWEAR',
  'OUTERWEAR',
  'ACCESSORY',
];

export const VALID_COLORS: readonly Color[] = [
  'WHITE',
  'BLACK',
  'NAVY',
  'GREY',
  'BEIGE',
  'BROWN',
  'BLUE',
  'GREEN',
  'OLIVE',
  'RED',
  'BURGUNDY',
  'YELLOW',
  'MULTI',
];

export const VALID_FORMALITIES: readonly FormalityLevel[] = [
  'CASUAL',
  'SMART_CASUAL',
  'BUSINESS_CASUAL',
  'FORMAL',
];

export const VALID_PATTERNS: readonly Pattern[] = [
  'SOLID',
  'STRIPED',
  'CHECKED',
  'FLORAL',
  'GRAPHIC',
  'TEXTURED',
];

export const VALID_SEASONS: readonly Season[] = [
  'ALL_SEASON',
  'SPRING',
  'SUMMER',
  'FALL',
  'WINTER',
];

export type WardrobeValidationErrors = Partial<
  Record<keyof WardrobeItemFormMetadata, string>
>;

export interface WardrobeValidationResult {
  readonly isValid: boolean;
  readonly errors: WardrobeValidationErrors;
}

/**
 * Validates wardrobe item metadata matching backend constraints from CreateWardrobeItemRequestDto.
 */
export function validateWardrobeItemMetadata(
  metadata: WardrobeItemFormMetadata
): WardrobeValidationResult {
  const errors: WardrobeValidationErrors = {};

  // 1. Name: 2–100 characters, trimmed
  const trimmedName = metadata.name ? metadata.name.trim() : '';
  if (!trimmedName) {
    errors.name = 'Item name is required.';
  } else if (trimmedName.length < 2 || trimmedName.length > 100) {
    errors.name = 'Item name must be between 2 and 100 characters.';
  }

  // 2. Category: Required enum
  if (!metadata.category || !VALID_CATEGORIES.includes(metadata.category as Category)) {
    errors.category = 'Category is required.';
  }

  // 3. Subcategory: Optional, max 50 chars
  if (metadata.subcategory && metadata.subcategory.trim().length > 50) {
    errors.subcategory = 'Subcategory must not exceed 50 characters.';
  }

  // 4. Primary Color: Required enum
  if (!metadata.primaryColor || !VALID_COLORS.includes(metadata.primaryColor as Color)) {
    errors.primaryColor = 'Primary color is required.';
  }

  // 5. Secondary Color: Optional enum
  if (
    metadata.secondaryColor &&
    !VALID_COLORS.includes(metadata.secondaryColor as Color)
  ) {
    errors.secondaryColor = 'Secondary color must be a valid color.';
  }

  // 6. Formality: Valid enum
  if (
    metadata.formalityLevel &&
    !VALID_FORMALITIES.includes(metadata.formalityLevel as FormalityLevel)
  ) {
    errors.formalityLevel = 'Formality level is invalid.';
  }

  // 7. Pattern: Valid enum
  if (metadata.pattern && !VALID_PATTERNS.includes(metadata.pattern as Pattern)) {
    errors.pattern = 'Pattern is invalid.';
  }

  // 8. Season: Valid enum
  if (metadata.season && !VALID_SEASONS.includes(metadata.season as Season)) {
    errors.season = 'Season is invalid.';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}
