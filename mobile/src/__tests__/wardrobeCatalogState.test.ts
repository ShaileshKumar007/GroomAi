// Mock expo-image-picker
jest.mock('expo-image-picker', () => ({
  requestCameraPermissionsAsync: jest.fn(),
  requestMediaLibraryPermissionsAsync: jest.fn(),
  launchCameraAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
  PermissionStatus: {
    GRANTED: 'granted',
    DENIED: 'denied',
    UNDETERMINED: 'undetermined',
  },
}));

jest.mock('../utils/imageNormalization', () => ({
  normalizeImageToJpeg: jest.fn(),
  cleanupNormalizedImage: jest.fn(),
  ImageNormalizationError: class MockError extends Error {},
}));

import { Category, WardrobeItemResponse, WardrobeListParams } from '../types/wardrobe.types';
import { formatWardrobeError } from '../utils/wardrobeWorkflow';
import { ApiError } from '../types/api.types';

describe('Wardrobe Catalog State & Workflow Logic', () => {
  const mockItem1: WardrobeItemResponse = {
    id: 'item-1',
    name: 'Blue Oxford Shirt',
    category: 'TOP',
    subcategory: 'SHIRT',
    primaryColor: 'BLUE',
    secondaryColor: null,
    pattern: 'SOLID',
    formalityLevel: 'CASUAL',
    season: 'SPRING',
    status: 'ACTIVE',
    storageKey: 'users/user-1/wardrobe/1.jpg',
    downloadUrl: 'http://localhost:8080/api/v1/storage/files/users/user-1/wardrobe/1.jpg',
    createdAt: '2026-10-26T01:00:00Z',
    updatedAt: '2026-10-26T01:00:00Z',
  };

  const mockItem2: WardrobeItemResponse = {
    id: 'item-2',
    name: 'Navy Chinos',
    category: 'BOTTOM',
    subcategory: 'CHINOS',
    primaryColor: 'NAVY',
    secondaryColor: null,
    pattern: 'SOLID',
    formalityLevel: 'SMART_CASUAL',
    season: 'ALL_SEASON',
    status: 'ACTIVE',
    storageKey: 'users/user-1/wardrobe/2.jpg',
    downloadUrl: 'http://localhost:8080/api/v1/storage/files/users/user-1/wardrobe/2.jpg',
    createdAt: '2026-10-26T02:00:00Z',
    updatedAt: '2026-10-26T02:00:00Z',
  };

  describe('Category query construction', () => {
    function buildQueryParams(selectedCategory: Category | 'ALL'): WardrobeListParams {
      return {
        page: 0,
        size: 50,
        status: 'ACTIVE',
        ...(selectedCategory !== 'ALL' ? { category: selectedCategory } : {}),
      };
    }

    it('builds query without category when ALL is selected', () => {
      const params = buildQueryParams('ALL');
      expect(params.category).toBeUndefined();
      expect(params.status).toBe('ACTIVE');
      expect(params.page).toBe(0);
      expect(params.size).toBe(50);
    });

    it('builds query with category TOP when TOP is selected', () => {
      const params = buildQueryParams('TOP');
      expect(params.category).toBe('TOP');
      expect(params.status).toBe('ACTIVE');
    });

    it('builds query with category FOOTWEAR when FOOTWEAR is selected', () => {
      const params = buildQueryParams('FOOTWEAR');
      expect(params.category).toBe('FOOTWEAR');
    });
  });

  describe('Client-side item filtering helper', () => {
    const allItems = [mockItem1, mockItem2];

    function filterItems(items: WardrobeItemResponse[], filter: Category | 'ALL'): WardrobeItemResponse[] {
      return filter === 'ALL' ? items : items.filter(i => i.category === filter);
    }

    it('returns all items when filter is ALL', () => {
      const filtered = filterItems(allItems, 'ALL');
      expect(filtered.length).toBe(2);
    });

    it('filters items correctly by category', () => {
      const filtered = filterItems(allItems, 'TOP');
      expect(filtered.length).toBe(1);
      expect(filtered[0].id).toBe('item-1');
      expect(filtered[0].category).toBe('TOP');
    });

    it('returns empty array when no items match category', () => {
      const filtered = filterItems(allItems, 'OUTERWEAR');
      expect(filtered.length).toBe(0);
    });
  });

  describe('Error formatting for Catalog UI', () => {
    it('formats 404 item not found error', () => {
      const error = new ApiError('Wardrobe item not found.', 'ITEM_NOT_FOUND', 404);
      const formatted = formatWardrobeError(error);
      expect(formatted.status).toBe(404);
      expect(formatted.message).toBe('Wardrobe item not found.');
    });

    it('formats network error nicely for user display', () => {
      const error = new ApiError('Network connection lost.', 'NETWORK_ERROR', 0);
      const formatted = formatWardrobeError(error);
      expect(formatted.type).toBe('NETWORK_ERROR');
      expect(formatted.message).toContain('Network connection lost');
    });

    it('formats generic unknown errors safely', () => {
      const error = new Error('Unexpected JavaScript failure');
      const formatted = formatWardrobeError(error);
      expect(formatted.type).toBe('UNKNOWN');
      expect(formatted.message).toBe('Unexpected JavaScript failure');
    });
  });

  describe('User session isolation logic', () => {
    interface CatalogState {
      userId: string | null;
      items: WardrobeItemResponse[];
      errorMessage: string | null;
    }

    function handleUserChange(
      prevState: CatalogState,
      newUserId: string | null
    ): CatalogState {
      if (prevState.userId !== newUserId) {
        // Reset state across user sessions or on logout
        return {
          userId: newUserId,
          items: [],
          errorMessage: null,
        };
      }
      return prevState;
    }

    it('clears items and error message when user logs out', () => {
      const state: CatalogState = {
        userId: 'user-1',
        items: [mockItem1],
        errorMessage: 'Some stale error',
      };

      const nextState = handleUserChange(state, null);
      expect(nextState.userId).toBeNull();
      expect(nextState.items).toEqual([]);
      expect(nextState.errorMessage).toBeNull();
    });

    it('clears previous user items when switching to a different user', () => {
      const state: CatalogState = {
        userId: 'user-1',
        items: [mockItem1],
        errorMessage: null,
      };

      const nextState = handleUserChange(state, 'user-2');
      expect(nextState.userId).toBe('user-2');
      expect(nextState.items).toEqual([]);
      expect(nextState.errorMessage).toBeNull();
    });

    it('preserves items if user ID has not changed', () => {
      const state: CatalogState = {
        userId: 'user-1',
        items: [mockItem1],
        errorMessage: null,
      };

      const nextState = handleUserChange(state, 'user-1');
      expect(nextState).toBe(state);
      expect(nextState.items.length).toBe(1);
    });
  });
});
