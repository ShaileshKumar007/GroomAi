import { apiCreateUploadIntent } from '../api/client';
import * as storage from '../utils/storage';

jest.mock('react-native', () => ({
  Platform: {
    OS: 'ios',
    select: (obj: Record<string, unknown>) => obj.ios ?? obj.default,
  },
}));

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

describe('Wardrobe Upload Intent API Client', () => {
  const originalFetch = global.fetch;

  beforeEach(async () => {
    jest.clearAllMocks();
    await storage.saveStoredTokens({
      accessToken: 'valid-access-token',
      refreshToken: 'valid-refresh-token',
      expiresIn: 900,
    });
  });

  afterAll(() => {
    global.fetch = originalFetch;
  });

  it('successfully requests upload intent with authenticated Bearer token', async () => {
    const mockIntentResponse = {
      uploadMode: 'LOCAL_STREAM',
      uploadUrl: 'http://localhost:8080/api/v1/storage/upload/wardrobe/u1/item.jpg',
      storageKey: 'wardrobe/u1/item.jpg',
      expiresAt: '2026-10-25T16:00:00Z',
      httpMethod: 'POST',
      requiredHeaders: {
        'Content-Type': 'multipart/form-data',
        'X-Upload-Ticket': 'ticket-xyz',
      },
      uploadTicket: 'ticket-xyz',
    };

    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => mockIntentResponse,
    } as Response);

    const result = await apiCreateUploadIntent({
      contentType: 'image/jpeg',
      byteSize: 1048576,
    });

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/wardrobe/upload-intent',
      expect.objectContaining({
        method: 'POST',
        headers: expect.any(Headers),
        body: JSON.stringify({
          contentType: 'image/jpeg',
          byteSize: 1048576,
        }),
      })
    );

    expect(result).toEqual(mockIntentResponse);
  });

  it('correctly handles and surfaces 403 CONSENT_REQUIRED from backend', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({
        timestamp: '2026-10-25T16:00:00Z',
        status: 403,
        error: 'Forbidden',
        code: 'CONSENT_REQUIRED',
        message: 'Wardrobe storage consent is required before requesting an upload intent.',
        path: '/api/v1/wardrobe/upload-intent',
      }),
    } as Response);

    await expect(
      apiCreateUploadIntent({
        contentType: 'image/jpeg',
        byteSize: 50000,
      })
    ).rejects.toMatchObject({
      name: 'ApiError',
      code: 'CONSENT_REQUIRED',
      status: 403,
      message: 'Wardrobe storage consent is required before requesting an upload intent.',
    });
  });

  it('correctly handles 400 UNSUPPORTED_IMAGE_FORMAT', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        timestamp: '2026-10-25T16:00:00Z',
        status: 400,
        error: 'Bad Request',
        code: 'UNSUPPORTED_IMAGE_FORMAT',
        message: 'Unsupported image format: image/heic. Only image/jpeg is accepted.',
        path: '/api/v1/wardrobe/upload-intent',
      }),
    } as Response);

    await expect(
      apiCreateUploadIntent({
        contentType: 'image/jpeg',
        byteSize: 50000,
      })
    ).rejects.toMatchObject({
      code: 'UNSUPPORTED_IMAGE_FORMAT',
      status: 400,
    });
  });

  it('correctly handles network connection failure', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('Network request failed'));

    await expect(
      apiCreateUploadIntent({
        contentType: 'image/jpeg',
        byteSize: 50000,
      })
    ).rejects.toThrow('Network request failed');
  });
});
