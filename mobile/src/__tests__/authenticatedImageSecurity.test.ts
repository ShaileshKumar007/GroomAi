import * as storage from '../utils/storage';

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

describe('Authenticated Image Security & Resolution Rules', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await storage.clearStoredTokens();
  });

  // Helper matching the exact resolution algorithm used in AuthenticatedImage
  async function resolveImageSource(uri: string): Promise<{
    hasError: boolean;
    source: { uri: string; headers?: Record<string, string> } | null;
  }> {
    if (!uri || uri.trim().length === 0) {
      return { hasError: true, source: null };
    }

    const isLocalStorage =
      uri.includes('/storage/files/') || uri.includes('/api/v1/storage/');

    if (isLocalStorage) {
      try {
        const tokens = await storage.getStoredTokens();
        if (tokens?.accessToken) {
          return {
            hasError: false,
            source: {
              uri,
              headers: {
                Authorization: `Bearer ${tokens.accessToken}`,
              },
            },
          };
        }
        return { hasError: false, source: { uri } };
      } catch {
        return { hasError: false, source: { uri } };
      }
    } else {
      // Cloud storage: S3 pre-signed URL, never attach Authorization header
      return { hasError: false, source: { uri } };
    }
  }

  it('injects Bearer authorization header for local storage file endpoint', async () => {
    await storage.saveStoredTokens({
      accessToken: 'auth-jwt-token-xyz',
      refreshToken: 'refresh-jwt-token-xyz',
      expiresIn: 3600,
    });

    const localUrl = 'http://localhost:8080/api/v1/storage/files/users/u123/wardrobe/item.jpg';
    const result = await resolveImageSource(localUrl);

    expect(result.hasError).toBe(false);
    expect(result.source).toBeDefined();
    expect(result.source?.uri).toBe(localUrl);
    expect(result.source?.headers?.['Authorization']).toBe('Bearer auth-jwt-token-xyz');
  });

  it('omits Bearer authorization header for external S3 pre-signed URLs', async () => {
    await storage.saveStoredTokens({
      accessToken: 'auth-jwt-token-xyz',
      refreshToken: 'refresh-jwt-token-xyz',
      expiresIn: 3600,
    });

    const s3Url = 'https://groomai-assets.s3.amazonaws.com/users/u123/wardrobe/item.jpg?X-Amz-Signature=abc123';
    const result = await resolveImageSource(s3Url);

    expect(result.hasError).toBe(false);
    expect(result.source).toBeDefined();
    expect(result.source?.uri).toBe(s3Url);
    expect(result.source?.headers).toBeUndefined();
  });

  it('safely handles local storage file endpoint when user has no stored token', async () => {
    const localUrl = 'http://localhost:8080/api/v1/storage/files/users/u123/wardrobe/item.jpg';
    const result = await resolveImageSource(localUrl);

    expect(result.hasError).toBe(false);
    expect(result.source).toBeDefined();
    expect(result.source?.uri).toBe(localUrl);
    expect(result.source?.headers).toBeUndefined();
  });

  it('treats empty or whitespace-only URI as error with fallback state', async () => {
    const emptyResult = await resolveImageSource('');
    expect(emptyResult.hasError).toBe(true);
    expect(emptyResult.source).toBeNull();

    const whitespaceResult = await resolveImageSource('   ');
    expect(whitespaceResult.hasError).toBe(true);
    expect(whitespaceResult.source).toBeNull();
  });
});
