import { apiCreateWardrobeItem } from '../api/client';
import { CreateWardrobeItemPayload } from '../types/wardrobe.types';
import * as storage from '../utils/storage';

jest.mock('../utils/storage', () => ({
  getStoredTokens: jest.fn(),
  saveStoredTokens: jest.fn(),
  clearStoredTokens: jest.fn(),
}));

describe('Wardrobe Item Catalog Confirmation', () => {
  const originalFetch = global.fetch;

  const validPayload: CreateWardrobeItemPayload = {
    name: 'Navy Cotton Oxford Shirt',
    category: 'TOP',
    subcategory: 'Button-Down',
    primaryColor: 'NAVY',
    secondaryColor: 'WHITE',
    pattern: 'SOLID',
    formalityLevel: 'SMART_CASUAL',
    season: 'ALL_SEASON',
    storageKey: 'wardrobe/user-123/item-456.jpg',
    uploadTicket: 'signed.ticket.hmac',
  };

  beforeEach(() => {
    jest.clearAllMocks();
    (storage.getStoredTokens as jest.Mock).mockResolvedValue({
      accessToken: 'valid.access.jwt',
      refreshToken: 'valid.refresh.jwt',
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('submits valid confirmation request to POST /api/v1/wardrobe/items and returns 201 Created item', async () => {
    const mockCreatedResponse = {
      id: 'item-uuid-789',
      name: validPayload.name,
      category: validPayload.category,
      subcategory: validPayload.subcategory,
      primaryColor: validPayload.primaryColor,
      secondaryColor: validPayload.secondaryColor,
      pattern: validPayload.pattern,
      formalityLevel: validPayload.formalityLevel,
      season: validPayload.season,
      storageKey: validPayload.storageKey,
      downloadUrl: 'http://localhost:8080/api/v1/storage/files/wardrobe/user-123/item-456.jpg',
      status: 'ACTIVE',
      createdAt: '2026-10-25T12:00:00Z',
      updatedAt: '2026-10-25T12:00:00Z',
    };

    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => mockCreatedResponse,
    });

    const response = await apiCreateWardrobeItem(validPayload);

    expect(global.fetch).toHaveBeenCalledTimes(1);
    const [url, options] = (global.fetch as jest.Mock).mock.calls[0];

    expect(url).toContain('/wardrobe/items');
    expect(options.method).toBe('POST');
    const headers = options.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer valid.access.jwt');
    expect(headers.get('Content-Type')).toBe('application/json');

    const sentBody = JSON.parse(options.body as string);
    expect(sentBody.name).toBe(validPayload.name);
    expect(sentBody.category).toBe('TOP');
    expect(sentBody.primaryColor).toBe('NAVY');
    expect(sentBody.storageKey).toBe(validPayload.storageKey);
    expect(sentBody.uploadTicket).toBe(validPayload.uploadTicket);

    expect(response.id).toBe('item-uuid-789');
    expect(response.status).toBe('ACTIVE');
  });

  it('throws ApiError with DUPLICATE_ITEM when item with storageKey already exists (409 Conflict)', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        status: 409,
        code: 'DUPLICATE_ITEM',
        message: "A wardrobe item with storage key 'wardrobe/user-123/item-456.jpg' has already been cataloged.",
      }),
    });

    await expect(apiCreateWardrobeItem(validPayload)).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'DUPLICATE_ITEM',
    });
  });

  it('throws ApiError with EXPIRED_UPLOAD_TICKET when ticket expired before confirmation (403)', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({
        status: 403,
        code: 'EXPIRED_UPLOAD_TICKET',
        message: 'Upload intent ticket has expired.',
      }),
    });

    await expect(apiCreateWardrobeItem(validPayload)).rejects.toMatchObject({
      name: 'ApiError',
      status: 403,
      code: 'EXPIRED_UPLOAD_TICKET',
    });
  });

  it('throws ApiError with INVALID_IMAGE_PAYLOAD when server magic bytes / dimension check fails (400)', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        status: 400,
        code: 'INVALID_IMAGE_PAYLOAD',
        message: 'Image dimensions (150x150) must be at least 200x200 pixels.',
      }),
    });

    await expect(apiCreateWardrobeItem(validPayload)).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      code: 'INVALID_IMAGE_PAYLOAD',
    });
  });

  it('handles network failure during confirmation', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('Network connection timeout'));

    await expect(apiCreateWardrobeItem(validPayload)).rejects.toThrow(
      'Network connection timeout'
    );
  });
});
