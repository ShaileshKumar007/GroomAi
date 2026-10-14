import { Platform } from 'react-native';
import { ApiErrorResponse, HealthResponse } from '../types/api.types';
import {
  AuthResponse,
  LoginPayload,
  RegisterPayload,
  TokenRefreshResponse,
  UserSummary,
} from '../types/auth.types';
import { clearStoredTokens, getStoredTokens, saveStoredTokens } from '../utils/storage';

const DEFAULT_DEV_URL =
  Platform.OS === 'android' ? 'http://10.0.2.2:8080/api/v1' : 'http://localhost:8080/api/v1';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL || DEFAULT_DEV_URL;

// Session expiration listener (for logging user out automatically upon terminal 401)
type SessionExpiredCallback = () => void;
let onSessionExpiredListener: SessionExpiredCallback | null = null;

export function setSessionExpiredListener(callback: SessionExpiredCallback | null): void {
  onSessionExpiredListener = callback;
}

// Active refresh promise mutex to prevent concurrent refresh races
let activeRefreshPromise: Promise<string | null> | null = null;

/**
 * Public health check fetch helper.
 */
export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_BASE_URL}/health`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`);
  }

  const data: unknown = await response.json();
  return data as HealthResponse;
}

/**
 * Parses and formats structured errors from docs/API_SPEC.md standard envelope.
 */
async function parseErrorResponse(response: Response): Promise<string> {
  try {
    const data: unknown = await response.json();
    if (data && typeof data === 'object' && 'message' in data) {
      const errorPayload = data as ApiErrorResponse;
      return errorPayload.message;
    }
  } catch {
    // If not JSON, fall back to status text
  }
  return `Request failed with status ${response.status}`;
}

/**
 * Handles refreshing tokens with a singleton mutex.
 */
async function refreshAccessToken(): Promise<string | null> {
  if (activeRefreshPromise) {
    return activeRefreshPromise;
  }

  activeRefreshPromise = (async () => {
    try {
      const stored = await getStoredTokens();
      if (!stored || !stored.refreshToken) {
        await clearStoredTokens();
        onSessionExpiredListener?.();
        return null;
      }

      const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({ refreshToken: stored.refreshToken }),
      });

      if (!response.ok) {
        await clearStoredTokens();
        onSessionExpiredListener?.();
        return null;
      }

      const data: unknown = await response.json();
      const tokens = data as TokenRefreshResponse;

      await saveStoredTokens({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        expiresIn: tokens.expiresIn,
      });

      return tokens.accessToken;
    } catch {
      await clearStoredTokens();
      onSessionExpiredListener?.();
      return null;
    } finally {
      activeRefreshPromise = null;
    }
  })();

  return activeRefreshPromise;
}

/**
 * Generic authenticated fetch wrapper with automatic token refresh on 401.
 */
export async function authenticatedFetch(
  endpoint: string,
  options: RequestInit = {},
  isRetry = false
): Promise<Response> {
  const tokens = await getStoredTokens();
  const headers = new Headers(options.headers || {});

  if (tokens?.accessToken) {
    headers.set('Authorization', `Bearer ${tokens.accessToken}`);
  }
  headers.set('Accept', 'application/json');

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  // Eligible 401 handling — attempt single token refresh
  if (response.status === 401 && !isRetry) {
    const newAccessToken = await refreshAccessToken();
    if (newAccessToken) {
      return authenticatedFetch(endpoint, options, true);
    }
  }

  return response;
}

/**
 * POST /api/v1/auth/register
 */
export async function apiRegister(payload: RegisterPayload): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const errorMsg = await parseErrorResponse(response);
    throw new Error(errorMsg);
  }

  const data: unknown = await response.json();
  const authResponse = data as AuthResponse;

  await saveStoredTokens({
    accessToken: authResponse.accessToken,
    refreshToken: authResponse.refreshToken,
    expiresIn: authResponse.expiresIn,
  });

  return authResponse;
}

/**
 * POST /api/v1/auth/login
 */
export async function apiLogin(payload: LoginPayload): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const errorMsg = await parseErrorResponse(response);
    throw new Error(errorMsg);
  }

  const data: unknown = await response.json();
  const authResponse = data as AuthResponse;

  await saveStoredTokens({
    accessToken: authResponse.accessToken,
    refreshToken: authResponse.refreshToken,
    expiresIn: authResponse.expiresIn,
  });

  return authResponse;
}

/**
 * POST /api/v1/auth/logout
 */
export async function apiLogout(): Promise<void> {
  try {
    const tokens = await getStoredTokens();
    if (tokens?.refreshToken) {
      await authenticatedFetch('/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken: tokens.refreshToken }),
      });
    }
  } finally {
    await clearStoredTokens();
  }
}

/**
 * GET /api/v1/auth/me
 */
export async function apiGetMe(): Promise<UserSummary> {
  const response = await authenticatedFetch('/auth/me', {
    method: 'GET',
  });

  if (!response.ok) {
    const errorMsg = await parseErrorResponse(response);
    throw new Error(errorMsg);
  }

  const data: unknown = await response.json();
  return data as UserSummary;
}
