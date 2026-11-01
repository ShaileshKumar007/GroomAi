import {
  apiGetSavedOutfitById,
  apiGetSavedOutfits,
  apiGetWearHistory,
  apiLogOutfitWear,
  apiSaveOutfit,
} from '../api/client';
import { ApiError } from '../types/api.types';
import * as storage from '../utils/storage';
import {
  LogOutfitWearPayload,
  LogOutfitWearResponseDto,
  OutfitWearHistoryDto,
  SavedOutfitResponseDto,
  SaveOutfitRequestPayload,
} from '../types/outfit.types';

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

describe('Outfit Persistence & Wear History API Client', () => {
  const originalFetch = global.fetch;

  beforeEach(async () => {
    jest.clearAllMocks();
    await storage.saveStoredTokens({
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      expiresIn: 3600,
    });
  });

  afterAll(() => {
    global.fetch = originalFetch;
  });

  const sampleSavedOutfit: SavedOutfitResponseDto = {
    id: 'outfit-saved-123',
    occasion: 'SMART_CASUAL',
    temperatureBracket: 'MILD',
    weatherCondition: 'SUNNY',
    stylingRationale: 'Cohesive smart casual ensemble.',
    suitabilityScore: 88,
    createdAt: '2026-10-30T10:00:00Z',
    items: [
      {
        role: 'TOP',
        id: 'item-top-1',
        name: 'Oxford Shirt',
        category: 'TOP',
        primaryColor: 'BLUE',
        secondaryColor: null,
        pattern: 'SOLID',
        formalityLevel: 'SMART_CASUAL',
        season: 'ALL_SEASON',
        downloadUrl: 'https://storage.local/photo1.jpg',
      },
      {
        role: 'BOTTOM',
        id: 'item-bot-1',
        name: 'Chinos',
        category: 'BOTTOM',
        primaryColor: 'BEIGE',
        secondaryColor: null,
        pattern: 'SOLID',
        formalityLevel: 'SMART_CASUAL',
        season: 'ALL_SEASON',
        downloadUrl: 'https://storage.local/photo2.jpg',
      },
      {
        role: 'FOOTWEAR',
        id: 'item-shoe-1',
        name: 'Loafers',
        category: 'FOOTWEAR',
        primaryColor: 'BROWN',
        secondaryColor: null,
        pattern: 'SOLID',
        formalityLevel: 'SMART_CASUAL',
        season: 'ALL_SEASON',
        downloadUrl: 'https://storage.local/photo3.jpg',
      },
    ],
  };

  const sampleWearResponse: LogOutfitWearResponseDto = {
    logId: 'wear-log-456',
    outfitId: 'outfit-saved-123',
    wornDate: '2026-10-30',
    message: 'Outfit recorded as worn on 2026-10-30',
  };

  const sampleWearHistoryItem: OutfitWearHistoryDto = {
    logId: 'wear-log-456',
    outfitId: 'outfit-saved-123',
    wornDate: '2026-10-30',
    notes: 'Dinner at Italian bistro',
    createdAt: '2026-10-30T12:00:00Z',
    occasion: 'SMART_CASUAL',
    suitabilityScore: 88,
    stylingRationale: 'Cohesive smart casual ensemble.',
    items: sampleSavedOutfit.items,
  };

  describe('apiSaveOutfit', () => {
    it('sends POST /api/v1/outfits with valid payload and returns SavedOutfitResponseDto', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        status: 201,
        json: async () => sampleSavedOutfit,
      });

      const payload: SaveOutfitRequestPayload = {
        itemIds: ['item-top-1', 'item-bot-1', 'item-shoe-1'],
        occasion: 'SMART_CASUAL',
        temperatureBracket: 'MILD',
        weatherCondition: 'SUNNY',
        suitabilityScore: 88,
        stylingRationale: 'Cohesive smart casual ensemble.',
      };

      const result = await apiSaveOutfit(payload);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/outfits'),
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(payload),
        })
      );
      const call = (global.fetch as jest.Mock).mock.calls[0];
      const headers = call[1].headers;
      const authHeader = typeof headers.get === 'function' ? headers.get('Authorization') : headers['Authorization'];
      expect(authHeader).toBe('Bearer test-access-token');
      expect(result.id).toBe('outfit-saved-123');
      expect(result.items).toHaveLength(3);
    });

    it('throws ApiError when saving outfit with missing required categories', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => ({
          code: 'INVALID_OUTFIT_COMPOSITION',
          message: 'Outfit must contain at least one TOP, one BOTTOM, and one FOOTWEAR',
        }),
      });

      const payload: SaveOutfitRequestPayload = {
        itemIds: ['item-top-1'],
        occasion: 'CASUAL',
      };

      await expect(apiSaveOutfit(payload)).rejects.toThrow(ApiError);
    });
  });

  describe('apiGetSavedOutfits', () => {
    it('sends GET /api/v1/outfits and returns paged saved outfits', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          content: [sampleSavedOutfit],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          last: true,
        }),
      });

      const result = await apiGetSavedOutfits({ page: 0, size: 10 });

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/outfits?page=0&size=10'),
        expect.objectContaining({
          method: 'GET',
        })
      );
      expect(result.content).toHaveLength(1);
      expect(result.content[0].id).toBe('outfit-saved-123');
    });
  });

  describe('apiGetSavedOutfitById', () => {
    it('sends GET /api/v1/outfits/{id} and returns outfit details', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => sampleSavedOutfit,
      });

      const result = await apiGetSavedOutfitById('outfit-saved-123');

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/outfits/outfit-saved-123'),
        expect.objectContaining({
          method: 'GET',
        })
      );
      expect(result.id).toBe('outfit-saved-123');
      expect(result.occasion).toBe('SMART_CASUAL');
    });

    it('throws ApiError with NOT_FOUND code on 404', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: async () => ({
          code: 'OUTFIT_NOT_FOUND',
          message: 'Outfit not found or access denied',
        }),
      });

      await expect(apiGetSavedOutfitById('nonexistent')).rejects.toThrow(ApiError);
    });
  });

  describe('apiLogOutfitWear', () => {
    it('sends POST /api/v1/outfits/{id}/wear with notes and date', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => sampleWearResponse,
      });

      const payload: LogOutfitWearPayload = {
        wornDate: '2026-10-30',
        notes: 'Dinner at Italian bistro',
        timezone: 'America/New_York',
      };

      const result = await apiLogOutfitWear('outfit-saved-123', payload);

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/outfits/outfit-saved-123/wear'),
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(payload),
        })
      );
      const call = (global.fetch as jest.Mock).mock.calls[0];
      const headers = call[1].headers;
      const contentType = typeof headers.get === 'function' ? headers.get('Content-Type') : headers['Content-Type'];
      const tzHeader = typeof headers.get === 'function' ? headers.get('X-Timezone') : headers['X-Timezone'];
      expect(contentType).toBe('application/json');
      expect(tzHeader).toBe('America/New_York');
      expect(result.logId).toBe('wear-log-456');
      expect(result.wornDate).toBe('2026-10-30');
    });

    it('defaults to device local date and timezone when payload is omitted', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => sampleWearResponse,
      });

      const result = await apiLogOutfitWear('outfit-saved-123');

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/outfits/outfit-saved-123/wear'),
        expect.objectContaining({
          method: 'POST',
        })
      );
      const call = (global.fetch as jest.Mock).mock.calls[0];
      const sentBody = JSON.parse(call[1].body as string);
      expect(sentBody.wornDate).toBeDefined();
      expect(sentBody.timezone).toBeDefined();
      expect(result.logId).toBe('wear-log-456');
    });
  });

  describe('apiGetWearHistory', () => {
    it('sends GET /api/v1/outfits/wear-history with pagination and returns records', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          content: [sampleWearHistoryItem],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          last: true,
        }),
      });

      const result = await apiGetWearHistory({ page: 0, size: 20 });

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/outfits/wear-history?page=0&size=20'),
        expect.objectContaining({
          method: 'GET',
        })
      );
      expect(result.content).toHaveLength(1);
      expect(result.content[0].logId).toBe('wear-log-456');
      expect(result.content[0].notes).toBe('Dinner at Italian bistro');
    });

    it('throws ApiError when server returns 500 error', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({
          code: 'INTERNAL_SERVER_ERROR',
          message: 'An unexpected error occurred',
        }),
      });

      await expect(apiGetWearHistory()).rejects.toThrow(ApiError);
    });
  });
});
