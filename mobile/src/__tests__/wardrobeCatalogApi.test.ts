import {
  apiGetWardrobeItems,
  apiGetWardrobeItemById,
  ApiError,
} from '../api/client';
import * as storage from '../utils/storage';
import { WardrobeItemResponse, PagedResponse } from '../types/wardrobe.types';

// Mock react-native
jest.mock('react-native', () => ({
  Platform: {
    OS: 'ios',
    select: (obj: Record<string, unknown>) => obj.ios ?? obj.default,
  },
}));

// Mock expo-secure-store
jest.mock('expo-secure-store', () => {
  const store = new Map<string, string>();
  return {
    getItemAsync: jest.fn(async (key: string) => store.get(key) || null),
    setItemAsync: jest.fn(async (key: string, val: string) => {
      store.set(key, val);
    }),
    deleteItemAsync: jest.fn(async (key: string) => {
      store.delete(key);
    }),
  };
});

describe('Wardrobe Catalog API Client', () => {
  const originalFetch = global.fetch;

  const mockItem: WardrobeItemResponse = {
    id: '123e4567-e89b-12d3-a456-426614174000',
    name: 'Classic White Oxford Shirt',
    category: 'TOP',
    subcategory: 'SHIRT',
    primaryColor: 'WHITE',
    secondaryColor: 'BLUE',
    pattern: 'SOLID',
    formalityLevel: 'SMART_CASUAL',
    season: 'ALL_SEASON',
    status: 'ACTIVE',
    storageKey: 'users/user-456/wardrobe/shirt.jpg',
    downloadUrl: 'http://localhost:8080/api/v1/storage/files/users/user-456/wardrobe/shirt.jpg',
    createdAt: '2026-10-26T00:00:00Z',
    updatedAt: '2026-10-26T00:00:00Z',
  };

  const mockPagedResponse: PagedResponse<WardrobeItemResponse> = {
    content: [mockItem],
    pageNumber: 0,
    pageSize: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    await storage.clearStoredTokens();
    await storage.saveStoredTokens({
      accessToken: 'valid-test-access-token',
      refreshToken: 'valid-test-refresh-token',
      expiresIn: 3600,
    });
  });

  afterAll(() => {
    global.fetch = originalFetch;
  });

  describe('apiGetWardrobeItems', () => {
    it('successfully retrieves paged wardrobe items', async () => {
      let requestedUrl = '';
      let authHeader = '';

      global.fetch = jest.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        requestedUrl = url;
        const headers = init?.headers as Headers | undefined;
        authHeader = headers?.get('Authorization') ?? '';
        return {
          ok: true,
          status: 200,
          json: async () => mockPagedResponse,
        } as Response;
      });

      const response = await apiGetWardrobeItems();

      expect(requestedUrl).toContain('/wardrobe/items');
      expect(authHeader).toBe('Bearer valid-test-access-token');
      expect(response.content.length).toBe(1);
      expect(response.content[0].name).toBe('Classic White Oxford Shirt');
      expect(response.totalElements).toBe(1);
      expect(response.totalPages).toBe(1);
    });

    it('correctly appends category and pagination query parameters', async () => {
      let requestedUrl = '';

      global.fetch = jest.fn().mockImplementation(async (url: string) => {
        requestedUrl = url;
        return {
          ok: true,
          status: 200,
          json: async () => mockPagedResponse,
        } as Response;
      });

      await apiGetWardrobeItems({
        category: 'TOP',
        status: 'ACTIVE',
        page: 1,
        size: 15,
        sort: 'createdAt,desc',
      });

      expect(requestedUrl).toContain('category=TOP');
      expect(requestedUrl).toContain('status=ACTIVE');
      expect(requestedUrl).toContain('page=1');
      expect(requestedUrl).toContain('size=15');
      expect(requestedUrl).toContain('sort=createdAt%2Cdesc');
    });

    it('returns empty list without error when wardrobe is empty', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          content: [],
          pageNumber: 0,
          pageSize: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        }),
      } as Response);

      const response = await apiGetWardrobeItems();
      expect(response.content).toEqual([]);
      expect(response.totalElements).toBe(0);
      expect(response.first).toBe(true);
      expect(response.last).toBe(true);
    });

    it('throws ApiError with error details on HTTP 500 error', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({
          timestamp: '2026-10-26T00:00:00Z',
          status: 500,
          error: 'Internal Server Error',
          code: 'DATABASE_ERROR',
          message: 'An unexpected database error occurred.',
        }),
      } as Response);

      await expect(apiGetWardrobeItems()).rejects.toThrow(ApiError);
      try {
        await apiGetWardrobeItems();
      } catch (err) {
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(500);
        expect(apiErr.code).toBe('DATABASE_ERROR');
        expect(apiErr.message).toBe('An unexpected database error occurred.');
      }
    });
  });

  describe('apiGetWardrobeItemById', () => {
    it('successfully retrieves a single wardrobe item by ID', async () => {
      let requestedUrl = '';
      global.fetch = jest.fn().mockImplementation(async (url: string) => {
        requestedUrl = url;
        return {
          ok: true,
          status: 200,
          json: async () => mockItem,
        } as Response;
      });

      const response = await apiGetWardrobeItemById(mockItem.id);

      expect(requestedUrl).toContain(`/wardrobe/items/${mockItem.id}`);
      expect(response.id).toBe(mockItem.id);
      expect(response.category).toBe('TOP');
      expect(response.downloadUrl).toContain('/storage/files/');
    });

    it('throws ApiError with ITEM_NOT_FOUND code on 404', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: async () => ({
          timestamp: '2026-10-26T00:00:00Z',
          status: 404,
          error: 'Not Found',
          code: 'ITEM_NOT_FOUND',
          message: 'Wardrobe item not found.',
        }),
      } as Response);

      await expect(apiGetWardrobeItemById('missing-item-id')).rejects.toThrow(ApiError);
      try {
        await apiGetWardrobeItemById('missing-item-id');
      } catch (err) {
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(404);
        expect(apiErr.code).toBe('ITEM_NOT_FOUND');
        expect(apiErr.message).toBe('Wardrobe item not found.');
      }
    });
  });
});
