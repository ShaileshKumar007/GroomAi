import { apiGenerateOutfits } from '../api/client';
import { ApiError } from '../types/api.types';
import * as storage from '../utils/storage';
import {
  GenerateOutfitRequestPayload,
  GenerateOutfitResponseDto,
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

describe('Outfit Recommendation API Client', () => {
  const originalFetch = global.fetch;

  beforeEach(async () => {
    jest.clearAllMocks();
    await storage.saveStoredTokens({
      accessToken: 'valid-test-access-token',
      refreshToken: 'valid-test-refresh-token',
      expiresIn: 3600,
    });
  });

  afterAll(() => {
    global.fetch = originalFetch;
  });

  const mockSuccessfulResponse: GenerateOutfitResponseDto = {
    outfits: [
      {
        id: 'outfit-1-uuid',
        occasion: 'SMART_CASUAL',
        suitabilityScore: 92,
        aiStylingAvailable: false,
        stylingRationale: 'Navy and Beige create classic harmony.',
        scoreBreakdown: {
          colorHarmony: 28,
          formalityAlignment: 25,
          seasonWeatherCompatibility: 20,
          patternRestraint: 14,
          preferenceMatch: 5,
          wearPenalty: 0,
        },
        items: [
          {
            role: 'TOP',
            id: 'top-1',
            name: 'Navy Shirt',
            category: 'TOP',
            primaryColor: 'NAVY',
            secondaryColor: null,
            pattern: 'SOLID',
            formalityLevel: 'SMART_CASUAL',
            season: 'ALL_SEASON',
            downloadUrl: 'http://localhost/images/navy.jpg',
          },
          {
            role: 'BOTTOM',
            id: 'bottom-1',
            name: 'Beige Chinos',
            category: 'BOTTOM',
            primaryColor: 'BEIGE',
            secondaryColor: null,
            pattern: 'SOLID',
            formalityLevel: 'SMART_CASUAL',
            season: 'ALL_SEASON',
            downloadUrl: 'http://localhost/images/chinos.jpg',
          },
          {
            role: 'FOOTWEAR',
            id: 'footwear-1',
            name: 'Brown Loafers',
            category: 'FOOTWEAR',
            primaryColor: 'BROWN',
            secondaryColor: null,
            pattern: 'SOLID',
            formalityLevel: 'SMART_CASUAL',
            season: 'ALL_SEASON',
            downloadUrl: 'http://localhost/images/loafers.jpg',
          },
        ],
      },
    ],
    warnings: [],
  };

  it('omits weather keys from request body when weather context is omitted', async () => {
    let capturedBody = '';
    global.fetch = jest.fn().mockImplementation((_url: string, init?: RequestInit) => {
      capturedBody = (init?.body as string) || '';
      return Promise.resolve({
        ok: true,
        json: async () => mockSuccessfulResponse,
      });
    });

    const payload: GenerateOutfitRequestPayload = {
      occasion: 'SMART_CASUAL',
      limit: 3,
    };

    const result = await apiGenerateOutfits(payload);

    expect(result.outfits).toHaveLength(1);
    expect(result.outfits[0].suitabilityScore).toBe(92);

    const parsed = JSON.parse(capturedBody);
    expect(parsed.occasion).toBe('SMART_CASUAL');
    expect(parsed.limit).toBe(3);
    expect(parsed.temperatureBracket).toBeUndefined();
    expect(parsed.weatherCondition).toBeUndefined();
  });

  it('includes temperatureBracket and weatherCondition when supplied by user', async () => {
    let capturedBody = '';
    global.fetch = jest.fn().mockImplementation((_url: string, init?: RequestInit) => {
      capturedBody = (init?.body as string) || '';
      return Promise.resolve({
        ok: true,
        json: async () => mockSuccessfulResponse,
      });
    });

    const payload: GenerateOutfitRequestPayload = {
      occasion: 'CASUAL',
      temperatureBracket: 'COOL',
      weatherCondition: 'RAINY',
      limit: 3,
    };

    await apiGenerateOutfits(payload);

    const parsed = JSON.parse(capturedBody);
    expect(parsed.occasion).toBe('CASUAL');
    expect(parsed.temperatureBracket).toBe('COOL');
    expect(parsed.weatherCondition).toBe('RAINY');
    expect(parsed.limit).toBe(3);
  });

  it('parses HTTP 422 INSUFFICIENT_WARDROBE_ITEMS with missingCategories', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      json: async () => ({
        timestamp: '2026-10-30T00:00:00Z',
        status: 422,
        error: 'Unprocessable Entity',
        code: 'INSUFFICIENT_WARDROBE_ITEMS',
        message: 'Active wardrobe requires at least one item from TOP, BOTTOM, and FOOTWEAR',
        path: '/api/v1/outfits/generate',
        missingCategories: ['BOTTOM', 'FOOTWEAR'],
      }),
    });

    try {
      await apiGenerateOutfits({ occasion: 'FORMAL' });
      fail('Expected apiGenerateOutfits to throw ApiError');
    } catch (err: unknown) {
      expect(err).toBeInstanceOf(ApiError);
      const apiError = err as ApiError;
      expect(apiError.status).toBe(422);
      expect(apiError.code).toBe('INSUFFICIENT_WARDROBE_ITEMS');
      expect(apiError.missingCategories).toEqual(['BOTTOM', 'FOOTWEAR']);
      expect(apiError.message).toContain('at least one item');
    }
  });

  it('parses HTTP 400 VALIDATION_FAILED when invalid parameters are passed', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        timestamp: '2026-10-30T00:00:00Z',
        status: 400,
        error: 'Bad Request',
        code: 'VALIDATION_FAILED',
        message: 'Limit must not exceed 10',
        path: '/api/v1/outfits/generate',
      }),
    });

    try {
      await apiGenerateOutfits({ limit: 15 });
      fail('Expected apiGenerateOutfits to throw ApiError');
    } catch (err: unknown) {
      expect(err).toBeInstanceOf(ApiError);
      const apiError = err as ApiError;
      expect(apiError.status).toBe(400);
      expect(apiError.code).toBe('VALIDATION_FAILED');
      expect(apiError.message).toBe('Limit must not exceed 10');
    }
  });

  it('returns empty outfits with warnings on HTTP 200 when no items match constraints', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        outfits: [],
        warnings: ['No active garments met the formality requirements for occasion FORMAL.'],
      }),
    });

    const result = await apiGenerateOutfits({ occasion: 'FORMAL' });

    expect(result.outfits).toEqual([]);
    expect(result.warnings).toHaveLength(1);
    expect(result.warnings[0]).toContain('FORMAL');
  });

  it('handles 401 unauthorized and refreshes token automatically', async () => {
    let callCount = 0;
    global.fetch = jest.fn().mockImplementation((url: string) => {
      callCount++;
      if (url.includes('/auth/refresh')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            accessToken: 'refreshed-access-token',
            refreshToken: 'refreshed-refresh-token',
            expiresIn: 3600,
          }),
        });
      }
      if (callCount === 1) {
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({
            code: 'TOKEN_EXPIRED',
            message: 'Access token expired',
          }),
        });
      }
      return Promise.resolve({
        ok: true,
        json: async () => mockSuccessfulResponse,
      });
    });

    const result = await apiGenerateOutfits({ occasion: 'SMART_CASUAL' });

    expect(result.outfits).toHaveLength(1);
    expect(callCount).toBe(3); // 1st try -> 401, refresh token -> 200, retry original -> 200
  });
});
