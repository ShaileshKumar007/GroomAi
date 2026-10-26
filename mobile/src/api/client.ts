import { Platform } from 'react-native';
import { ApiErrorResponse, HealthResponse } from '../types/api.types';
import {
  AuthResponse,
  LoginPayload,
  RegisterPayload,
  TokenRefreshResponse,
  UserSummary,
} from '../types/auth.types';
import {
  CreateWardrobeItemPayload,
  PagedResponse,
  UploadIntentRequestPayload,
  UploadIntentResponse,
  WardrobeItemResponse,
  WardrobeListParams,
} from '../types/wardrobe.types';
import * as FileSystem from 'expo-file-system/legacy';
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

export { ApiError } from '../types/api.types';
import { ApiError } from '../types/api.types';

/**
 * POST /api/v1/wardrobe/upload-intent
 * Requests an authorized upload intent ticket and URL for a normalized JPEG image.
 */
export async function apiCreateUploadIntent(
  payload: UploadIntentRequestPayload
): Promise<UploadIntentResponse> {
  const response = await authenticatedFetch('/wardrobe/upload-intent', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorCode = 'UNKNOWN';
    let errorMessage = `Request failed with status ${response.status}`;
    try {
      const data: unknown = await response.json();
      if (data && typeof data === 'object') {
        const errorPayload = data as ApiErrorResponse;
        if (errorPayload.code) errorCode = errorPayload.code;
        if (errorPayload.message) errorMessage = errorPayload.message;
      }
    } catch {
      // Non-JSON error body fallback
    }
    throw new ApiError(errorMessage, errorCode, response.status);
  }

  const data: unknown = await response.json();
  return data as UploadIntentResponse;
}

/**
 * Uploads normalized JPEG image bytes using the protocol specified by the uploadMode.
 * - LOCAL_STREAM: multipart POST with Authorization and X-Upload-Ticket headers.
 * - S3_PRESIGNED: binary PUT directly to signed S3 URL with required headers only.
 */
export async function apiUploadBinary(
  uploadIntent: UploadIntentResponse,
  fileUri: string
): Promise<void> {
  const { uploadMode, uploadUrl, requiredHeaders, uploadTicket } = uploadIntent;

  if (uploadMode === 'LOCAL_STREAM') {
    const tokens = await getStoredTokens();
    const headers: Record<string, string> = {
      ...requiredHeaders,
    };
    if (tokens?.accessToken) {
      headers['Authorization'] = `Bearer ${tokens.accessToken}`;
    }
    if (uploadTicket && !headers['X-Upload-Ticket']) {
      headers['X-Upload-Ticket'] = uploadTicket;
    }
    // Remove Content-Type so that FileSystem.uploadAsync generates the multipart boundary
    delete headers['Content-Type'];
    delete headers['content-type'];

    let result: FileSystem.FileSystemUploadResult;
    try {
      result = await FileSystem.uploadAsync(uploadUrl, fileUri, {
        httpMethod: 'POST',
        uploadType: FileSystem.FileSystemUploadType.MULTIPART,
        fieldName: 'file',
        mimeType: 'image/jpeg',
        headers,
      });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      throw new ApiError(`Network failure during image upload: ${msg}`, 'NETWORK_ERROR', 0);
    }

    if (result.status < 200 || result.status >= 300) {
      let code = 'UPLOAD_FAILED';
      let message = `Local upload failed with status ${result.status}`;
      try {
        const parsed = JSON.parse(result.body);
        if (parsed.code) code = parsed.code;
        if (parsed.message) message = parsed.message;
      } catch {
        // non-JSON error body fallback
      }
      throw new ApiError(message, code, result.status);
    }
  } else {
    // S3_PRESIGNED mode: send binary bytes with required headers only
    const headers: Record<string, string> = {
      ...requiredHeaders,
    };
    // Ensure no authorization token or upload ticket is leaked to external S3
    delete headers['Authorization'];
    delete headers['authorization'];
    delete headers['X-Upload-Ticket'];

    let result: FileSystem.FileSystemUploadResult;
    try {
      result = await FileSystem.uploadAsync(uploadUrl, fileUri, {
        httpMethod: 'PUT',
        uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
        headers,
      });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      throw new ApiError(`Network failure during cloud storage upload: ${msg}`, 'NETWORK_ERROR', 0);
    }

    if (result.status < 200 || result.status >= 300) {
      throw new ApiError(
        `Cloud storage upload failed with status ${result.status}`,
        'S3_UPLOAD_FAILED',
        result.status
      );
    }
  }
}

/**
 * POST /api/v1/wardrobe/items
 * Confirms and catalogs an uploaded clothing item with verified metadata into the digital wardrobe.
 */
export async function apiCreateWardrobeItem(
  payload: CreateWardrobeItemPayload
): Promise<WardrobeItemResponse> {
  const response = await authenticatedFetch('/wardrobe/items', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let errorCode = 'UNKNOWN';
    let errorMessage = `Item confirmation failed with status ${response.status}`;
    try {
      const data: unknown = await response.json();
      if (data && typeof data === 'object') {
        const errorPayload = data as ApiErrorResponse;
        if (errorPayload.code) errorCode = errorPayload.code;
        if (errorPayload.message) errorMessage = errorPayload.message;
      }
    } catch {
      // Non-JSON error body fallback
    }
    throw new ApiError(errorMessage, errorCode, response.status);
  }

  const data: unknown = await response.json();
  return data as WardrobeItemResponse;
}

/**
 * GET /api/v1/wardrobe/items
 * Retrieves paginated wardrobe items for the authenticated user, optionally filtered by category or status.
 */
export async function apiGetWardrobeItems(
  params?: WardrobeListParams
): Promise<PagedResponse<WardrobeItemResponse>> {
  const queryParams = new URLSearchParams();
  if (params?.category) {
    queryParams.append('category', params.category);
  }
  if (params?.status) {
    queryParams.append('status', params.status);
  }
  if (params?.page !== undefined) {
    queryParams.append('page', String(params.page));
  }
  if (params?.size !== undefined) {
    queryParams.append('size', String(params.size));
  }
  if (params?.sort) {
    queryParams.append('sort', params.sort);
  }

  const queryString = queryParams.toString();
  const endpoint = queryString ? `/wardrobe/items?${queryString}` : '/wardrobe/items';

  const response = await authenticatedFetch(endpoint, {
    method: 'GET',
  });

  if (!response.ok) {
    let errorCode = 'UNKNOWN';
    let errorMessage = `Failed to fetch wardrobe items with status ${response.status}`;
    try {
      const data: unknown = await response.json();
      if (data && typeof data === 'object') {
        const errorPayload = data as ApiErrorResponse;
        if (errorPayload.code) errorCode = errorPayload.code;
        if (errorPayload.message) errorMessage = errorPayload.message;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(errorMessage, errorCode, response.status);
  }

  const data: unknown = await response.json();
  return data as PagedResponse<WardrobeItemResponse>;
}

/**
 * GET /api/v1/wardrobe/items/{id}
 * Retrieves full details for a single wardrobe item owned by the authenticated user.
 */
export async function apiGetWardrobeItemById(id: string): Promise<WardrobeItemResponse> {
  const response = await authenticatedFetch(`/wardrobe/items/${id}`, {
    method: 'GET',
  });

  if (!response.ok) {
    let errorCode = 'UNKNOWN';
    let errorMessage = `Failed to fetch wardrobe item with status ${response.status}`;
    try {
      const data: unknown = await response.json();
      if (data && typeof data === 'object') {
        const errorPayload = data as ApiErrorResponse;
        if (errorPayload.code) errorCode = errorPayload.code;
        if (errorPayload.message) errorMessage = errorPayload.message;
      }
    } catch {
      // Non-JSON fallback
    }
    throw new ApiError(errorMessage, errorCode, response.status);
  }

  const data: unknown = await response.json();
  return data as WardrobeItemResponse;
}
