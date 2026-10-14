import * as SecureStore from 'expo-secure-store';
import { AuthTokens } from '../types/auth.types';

const ACCESS_TOKEN_KEY = 'groomai_access_token';
const REFRESH_TOKEN_KEY = 'groomai_refresh_token';
const EXPIRES_IN_KEY = 'groomai_expires_in';

/**
 * SecureStore token persistence adapter.
 * Uses hardware-backed keychain / keystore on iOS/Android.
 */
export async function getStoredTokens(): Promise<AuthTokens | null> {
  try {
    const [accessToken, refreshToken, expiresInStr] = await Promise.all([
      SecureStore.getItemAsync(ACCESS_TOKEN_KEY),
      SecureStore.getItemAsync(REFRESH_TOKEN_KEY),
      SecureStore.getItemAsync(EXPIRES_IN_KEY),
    ]);

    if (!accessToken || !refreshToken) {
      return null;
    }

    const expiresIn = expiresInStr ? parseInt(expiresInStr, 10) : 900;
    return {
      accessToken,
      refreshToken,
      expiresIn: isNaN(expiresIn) ? 900 : expiresIn,
    };
  } catch {
    return null;
  }
}

export async function saveStoredTokens(tokens: AuthTokens): Promise<void> {
  try {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_TOKEN_KEY, tokens.accessToken),
      SecureStore.setItemAsync(REFRESH_TOKEN_KEY, tokens.refreshToken),
      SecureStore.setItemAsync(EXPIRES_IN_KEY, tokens.expiresIn.toString()),
    ]);
  } catch (error) {
    throw new Error('Failed to securely store authentication session.');
  }
}

export async function clearStoredTokens(): Promise<void> {
  try {
    await Promise.all([
      SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY),
      SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
      SecureStore.deleteItemAsync(EXPIRES_IN_KEY),
    ]);
  } catch {
    // Ignore cleanup deletion errors
  }
}
