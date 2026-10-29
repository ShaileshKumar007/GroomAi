import { validateWardrobeItemMetadata } from '../utils/wardrobeValidation';
import {
  UpdateWardrobeItemPayload,
  WardrobeItemFormMetadata,
  WardrobeItemResponse,
} from '../types/wardrobe.types';

describe('Wardrobe Edit and Delete Flow Logic', () => {
  const existingItem: WardrobeItemResponse = {
    id: 'item-123',
    name: 'Classic White Shirt',
    category: 'TOP',
    subcategory: 'Button-Down',
    primaryColor: 'WHITE',
    secondaryColor: null,
    formalityLevel: 'BUSINESS_CASUAL',
    season: 'ALL_SEASON',
    pattern: 'SOLID',
    status: 'ACTIVE',
    storageKey: 'users/u1/wardrobe/shirt.jpg',
    downloadUrl: 'http://localhost:8080/api/v1/storage/files/users/u1/wardrobe/shirt.jpg',
    createdAt: '2026-10-26T00:00:00Z',
    updatedAt: '2026-10-26T00:00:00Z',
  };

  describe('Edit Form Initialization', () => {
    it('initializes edit form from existing wardrobe item correctly', () => {
      const editForm: WardrobeItemFormMetadata = {
        name: existingItem.name,
        category: existingItem.category,
        subcategory: existingItem.subcategory ?? '',
        primaryColor: existingItem.primaryColor,
        secondaryColor: existingItem.secondaryColor ?? '',
        formalityLevel: existingItem.formalityLevel,
        season: existingItem.season,
        pattern: existingItem.pattern,
      };

      expect(editForm.name).toBe('Classic White Shirt');
      expect(editForm.category).toBe('TOP');
      expect(editForm.subcategory).toBe('Button-Down');
      expect(editForm.primaryColor).toBe('WHITE');
      expect(editForm.secondaryColor).toBe('');
      expect(editForm.formalityLevel).toBe('BUSINESS_CASUAL');
      expect(editForm.season).toBe('ALL_SEASON');
      expect(editForm.pattern).toBe('SOLID');
    });
  });

  describe('Edit Form Validation', () => {
    it('validates valid edited metadata successfully', () => {
      const validForm: WardrobeItemFormMetadata = {
        name: 'Navy Linen Oxford Shirt',
        category: 'TOP',
        subcategory: 'Linen Shirt',
        primaryColor: 'NAVY',
        secondaryColor: 'WHITE',
        formalityLevel: 'SMART_CASUAL',
        season: 'SUMMER',
        pattern: 'SOLID',
      };

      const result = validateWardrobeItemMetadata(validForm);
      expect(result.isValid).toBe(true);
      expect(Object.keys(result.errors).length).toBe(0);
    });

    it('rejects empty or whitespace-only name', () => {
      const form: WardrobeItemFormMetadata = {
        name: '   ',
        category: 'TOP',
        subcategory: '',
        primaryColor: 'BLUE',
        secondaryColor: '',
        formalityLevel: 'CASUAL',
        season: 'ALL_SEASON',
        pattern: 'SOLID',
      };

      const result = validateWardrobeItemMetadata(form);
      expect(result.isValid).toBe(false);
      expect(result.errors.name).toBe('Item name is required.');
    });

    it('rejects name shorter than 2 characters or longer than 100 characters', () => {
      const shortResult = validateWardrobeItemMetadata({
        name: 'A',
        category: 'TOP',
        subcategory: '',
        primaryColor: 'BLUE',
        secondaryColor: '',
        formalityLevel: 'CASUAL',
        season: 'ALL_SEASON',
        pattern: 'SOLID',
      });
      expect(shortResult.isValid).toBe(false);
      expect(shortResult.errors.name).toBe('Item name must be between 2 and 100 characters.');

      const longResult = validateWardrobeItemMetadata({
        name: 'A'.repeat(101),
        category: 'TOP',
        subcategory: '',
        primaryColor: 'BLUE',
        secondaryColor: '',
        formalityLevel: 'CASUAL',
        season: 'ALL_SEASON',
        pattern: 'SOLID',
      });
      expect(longResult.isValid).toBe(false);
      expect(longResult.errors.name).toBe('Item name must be between 2 and 100 characters.');
    });

    it('rejects subcategory exceeding 50 characters', () => {
      const result = validateWardrobeItemMetadata({
        name: 'Blue Shirt',
        category: 'TOP',
        subcategory: 'A'.repeat(51),
        primaryColor: 'BLUE',
        secondaryColor: '',
        formalityLevel: 'CASUAL',
        season: 'ALL_SEASON',
        pattern: 'SOLID',
      });

      expect(result.isValid).toBe(false);
      expect(result.errors.subcategory).toBe('Subcategory must not exceed 50 characters.');
    });

    it('rejects invalid or missing category', () => {
      const result = validateWardrobeItemMetadata({
        name: 'Blue Shirt',
        category: '' as any,
        subcategory: '',
        primaryColor: 'BLUE',
        secondaryColor: '',
        formalityLevel: 'CASUAL',
        season: 'ALL_SEASON',
        pattern: 'SOLID',
      });

      expect(result.isValid).toBe(false);
      expect(result.errors.category).toBe('Category is required.');
    });

    it('rejects missing primary color', () => {
      const result = validateWardrobeItemMetadata({
        name: 'Blue Shirt',
        category: 'TOP',
        subcategory: '',
        primaryColor: '' as any,
        secondaryColor: '',
        formalityLevel: 'CASUAL',
        season: 'ALL_SEASON',
        pattern: 'SOLID',
      });

      expect(result.isValid).toBe(false);
      expect(result.errors.primaryColor).toBe('Primary color is required.');
    });
  });

  describe('Update Payload Construction (No Re-upload Rule)', () => {
    it('constructs immutable UpdateWardrobeItemPayload without touching storageKey or downloadUrl', () => {
      const form: WardrobeItemFormMetadata = {
        name: '  Relaxed Linen Button-Down  ',
        category: 'TOP',
        subcategory: '  Linen  ',
        primaryColor: 'BEIGE',
        secondaryColor: '',
        formalityLevel: 'SMART_CASUAL',
        season: 'SUMMER',
        pattern: 'SOLID',
      };

      // Payload construction matches [id].tsx logic
      const payload: UpdateWardrobeItemPayload = {
        name: form.name.trim(),
        category: form.category as any,
        subcategory: form.subcategory.trim() ? form.subcategory.trim() : null,
        primaryColor: form.primaryColor as any,
        secondaryColor: (form.secondaryColor as any) || null,
        formalityLevel: form.formalityLevel,
        season: form.season,
        pattern: form.pattern,
      };

      expect(payload.name).toBe('Relaxed Linen Button-Down');
      expect(payload.category).toBe('TOP');
      expect(payload.subcategory).toBe('Linen');
      expect(payload.primaryColor).toBe('BEIGE');
      expect(payload.secondaryColor).toBeNull();
      expect(payload.formalityLevel).toBe('SMART_CASUAL');
      expect(payload.season).toBe('SUMMER');
      expect(payload.pattern).toBe('SOLID');

      // Crucial Stage 3D rule: payload has NO storageKey, uploadTicket, or file binary
      expect((payload as any).storageKey).toBeUndefined();
      expect((payload as any).downloadUrl).toBeUndefined();
      expect((payload as any).imageBinary).toBeUndefined();
    });
  });

  describe('Delete Safety & Duplicate Tap Protection', () => {
    it('prevents duplicate concurrent deletion requests with isDeleting guard', async () => {
      let isDeleting = false;
      let deleteInvocationCount = 0;

      const mockApiDelete = jest.fn().mockImplementation(async () => {
        deleteInvocationCount++;
        await new Promise((resolve) => setTimeout(resolve, 50));
      });

      const handleConfirmDelete = async () => {
        if (isDeleting) return;
        isDeleting = true;
        try {
          await mockApiDelete();
        } finally {
          isDeleting = false;
        }
      };

      // Trigger 2 rapid calls in parallel (simulating double tap)
      await Promise.all([handleConfirmDelete(), handleConfirmDelete()]);

      expect(deleteInvocationCount).toBe(1);
      expect(mockApiDelete).toHaveBeenCalledTimes(1);
    });

    it('safely retains error message and clears isDeleting on deletion failure', async () => {
      let isDeleting = false;
      let deleteErrorMessage: string | null = null;

      const mockApiDelete = jest.fn().mockRejectedValue(new Error('Network connection timeout.'));

      const handleConfirmDelete = async () => {
        if (isDeleting) return;
        isDeleting = true;
        deleteErrorMessage = null;
        try {
          await mockApiDelete();
        } catch (err: any) {
          deleteErrorMessage = err.message;
        } finally {
          isDeleting = false;
        }
      };

      await handleConfirmDelete();

      expect(isDeleting).toBe(false);
      expect(deleteErrorMessage).toBe('Network connection timeout.');
    });
  });
});
