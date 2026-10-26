import {
  validateWardrobeItemMetadata,
  VALID_CATEGORIES,
  VALID_COLORS,
  VALID_FORMALITIES,
  VALID_PATTERNS,
  VALID_SEASONS,
} from '../utils/wardrobeValidation';
import { WardrobeItemFormMetadata } from '../types/wardrobe.types';

describe('Wardrobe Item Metadata Validation', () => {
  const validMetadata: WardrobeItemFormMetadata = {
    name: 'Navy Cotton Oxford Shirt',
    category: 'TOP',
    subcategory: 'Button-Down',
    primaryColor: 'NAVY',
    secondaryColor: 'WHITE',
    pattern: 'SOLID',
    formalityLevel: 'SMART_CASUAL',
    season: 'ALL_SEASON',
  };

  it('passes validation with complete valid metadata', () => {
    const result = validateWardrobeItemMetadata(validMetadata);
    expect(result.isValid).toBe(true);
    expect(result.errors).toEqual({});
  });

  describe('Garment Name Validation', () => {
    it('fails when name is empty or whitespace', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        name: '   ',
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.name).toBe('Item name is required.');
    });

    it('fails when name is shorter than 2 characters', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        name: 'A',
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.name).toBe('Item name must be between 2 and 100 characters.');
    });

    it('fails when name exceeds 100 characters', () => {
      const longName = 'A'.repeat(101);
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        name: longName,
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.name).toBe('Item name must be between 2 and 100 characters.');
    });

    it('accepts trimmed name with exactly 2 characters and 100 characters', () => {
      const minNameResult = validateWardrobeItemMetadata({
        ...validMetadata,
        name: '  Ab  ',
      });
      expect(minNameResult.isValid).toBe(true);

      const maxName = 'B'.repeat(100);
      const maxNameResult = validateWardrobeItemMetadata({
        ...validMetadata,
        name: maxName,
      });
      expect(maxNameResult.isValid).toBe(true);
    });
  });

  describe('Category Validation', () => {
    it('fails when category is empty', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        category: '',
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.category).toBe('Category is required.');
    });

    it('fails when category is not in the allowed enums', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        // @ts-expect-error test invalid string
        category: 'HAT',
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.category).toBe('Category is required.');
    });

    it('accepts all valid backend categories', () => {
      for (const cat of VALID_CATEGORIES) {
        const result = validateWardrobeItemMetadata({
          ...validMetadata,
          category: cat,
        });
        expect(result.isValid).toBe(true);
      }
    });
  });

  describe('Subcategory Validation', () => {
    it('accepts empty or null subcategory (optional)', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        subcategory: '',
      });
      expect(result.isValid).toBe(true);
    });

    it('fails when subcategory exceeds 50 characters', () => {
      const longSubcategory = 'S'.repeat(51);
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        subcategory: longSubcategory,
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.subcategory).toBe('Subcategory must not exceed 50 characters.');
    });

    it('accepts valid subcategory up to 50 characters', () => {
      const subcategory50 = 'C'.repeat(50);
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        subcategory: subcategory50,
      });
      expect(result.isValid).toBe(true);
    });
  });

  describe('Color Validation', () => {
    it('fails when primaryColor is empty', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        primaryColor: '',
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.primaryColor).toBe('Primary color is required.');
    });

    it('fails when primaryColor is not a valid enum', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        // @ts-expect-error test invalid string
        primaryColor: 'PINK',
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.primaryColor).toBe('Primary color is required.');
    });

    it('accepts all 13 valid backend colors for primaryColor', () => {
      for (const col of VALID_COLORS) {
        const result = validateWardrobeItemMetadata({
          ...validMetadata,
          primaryColor: col,
        });
        expect(result.isValid).toBe(true);
      }
    });

    it('accepts empty string for secondaryColor (optional)', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        secondaryColor: '',
      });
      expect(result.isValid).toBe(true);
    });

    it('fails when secondaryColor is an invalid string', () => {
      const result = validateWardrobeItemMetadata({
        ...validMetadata,
        // @ts-expect-error test invalid string
        secondaryColor: 'CYAN',
      });
      expect(result.isValid).toBe(false);
      expect(result.errors.secondaryColor).toBe('Secondary color must be a valid color.');
    });
  });

  describe('Formality, Pattern, and Season Enums', () => {
    it('accepts all valid formalities', () => {
      for (const formality of VALID_FORMALITIES) {
        const result = validateWardrobeItemMetadata({
          ...validMetadata,
          formalityLevel: formality,
        });
        expect(result.isValid).toBe(true);
      }
    });

    it('accepts all valid patterns', () => {
      for (const pattern of VALID_PATTERNS) {
        const result = validateWardrobeItemMetadata({
          ...validMetadata,
          pattern,
        });
        expect(result.isValid).toBe(true);
      }
    });

    it('accepts all valid seasons', () => {
      for (const season of VALID_SEASONS) {
        const result = validateWardrobeItemMetadata({
          ...validMetadata,
          season,
        });
        expect(result.isValid).toBe(true);
      }
    });
  });
});
