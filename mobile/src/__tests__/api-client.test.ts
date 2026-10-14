import {
  apiGetMe,
  apiLogin,
  apiLogout,
  apiRegister,
  authenticatedFetch,
  fetchHealth,
} from '../api/client';
import * as storage from '../utils/storage';

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

describe('Mobile API Client', () => {
  const originalFetch = global.fetch;

  beforeEach(async () => {
    jest.clearAllMocks();
    await storage.clearStoredTokens();
  });

  afterAll(() => {
    global.fetch = originalFetch;
  });

  it('fetchHealth returns status UP', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: 'UP',
        service: 'groomai-backend',
        timestamp: '2026-10-15T00:00:00Z',
      }),
    } as Response);

    const health = await fetchHealth();
    expect(health.status).toBe('UP');
    expect(health.service).toBe('groomai-backend');
  });

  it('apiRegister persists tokens and returns user auth payload', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        userId: '11111111-2222-3333-4444-555555555555',
        email: 'alex@example.com',
        displayName: 'Alex',
        accessToken: 'mock_jwt_access',
        refreshToken: 'rt_mock_refresh',
        expiresIn: 900,
      }),
    } as Response);

    const res = await apiRegister({
      email: 'alex@example.com',
      password: 'Password123!',
      displayName: 'Alex',
    });

    expect(res.userId).toBe('11111111-2222-3333-4444-555555555555');
    expect(res.accessToken).toBe('mock_jwt_access');

    // Verify stored in SecureStore
    const tokens = await storage.getStoredTokens();
    expect(tokens?.accessToken).toBe('mock_jwt_access');
    expect(tokens?.refreshToken).toBe('rt_mock_refresh');
  });

  it('apiLogin persists tokens and surfaces errors on invalid credentials', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({
        timestamp: '2026-10-15T00:00:00Z',
        status: 401,
        code: 'INVALID_CREDENTIALS',
        message: 'Invalid email or password.',
      }),
    } as Response);

    await expect(
      apiLogin({ email: 'bad@example.com', password: 'WrongPassword1!' })
    ).rejects.toThrow('Invalid email or password.');
  });

  it('authenticatedFetch attaches Bearer token header', async () => {
    await storage.saveStoredTokens({
      accessToken: 'my_bearer_token',
      refreshToken: 'my_refresh_token',
      expiresIn: 900,
    });

    global.fetch = jest.fn().mockImplementation((url: string, opts: RequestInit) => {
      const headers = opts.headers as Headers;
      expect(headers.get('Authorization')).toBe('Bearer my_bearer_token');
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({ id: '123' }),
      } as Response);
    });

    const res = await authenticatedFetch('/auth/me');
    expect(res.ok).toBe(true);
  });

  it('apiGetMe retrieves authenticated user summary', async () => {
    await storage.saveStoredTokens({
      accessToken: 'valid_access_token',
      refreshToken: 'valid_refresh_token',
      expiresIn: 900,
    });

    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        id: 'u-123',
        email: 'alex@example.com',
        displayName: 'Alex Sharma',
        createdAt: '2026-10-15T00:00:00Z',
      }),
    } as Response);

    const user = await apiGetMe();
    expect(user.id).toBe('u-123');
    expect(user.email).toBe('alex@example.com');
    expect(user.displayName).toBe('Alex Sharma');
  });

  it('apiLogout clears tokens and notifies backend', async () => {
    await storage.saveStoredTokens({
      accessToken: 'access_to_clear',
      refreshToken: 'refresh_to_revoke',
      expiresIn: 900,
    });

    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 204,
    } as Response);

    await apiLogout();

    const remaining = await storage.getStoredTokens();
    expect(remaining).toBeNull();
  });
});
