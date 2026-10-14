/**
 * Authentication and User models matching docs/API_SPEC.md.
 * Strict TypeScript types with zero `any`.
 */

export interface UserSummary {
  readonly id: string;
  readonly email: string;
  readonly displayName: string | null;
  readonly createdAt: string;
}

export interface AuthTokens {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly expiresIn: number;
}

export interface AuthResponse {
  readonly userId: string;
  readonly email: string;
  readonly displayName: string | null;
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly expiresIn: number;
}

export interface TokenRefreshResponse {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly expiresIn: number;
}

export interface RegisterPayload {
  readonly email: string;
  readonly password: string;
  readonly displayName?: string;
}

export interface LoginPayload {
  readonly email: string;
  readonly password: string;
}
