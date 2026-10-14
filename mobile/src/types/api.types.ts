/**
 * Standard API error models matching docs/API_SPEC.md Section 1.4.
 * Strict TypeScript types with zero `any`.
 */

export interface ApiErrorDetail {
  readonly field: string;
  readonly issue: string;
}

export interface ApiErrorResponse {
  readonly timestamp: string;
  readonly status: number;
  readonly error: string;
  readonly code: string;
  readonly message: string;
  readonly path: string;
  readonly details?: readonly ApiErrorDetail[];
}

export interface HealthResponse {
  readonly status: string;
  readonly service: string;
  readonly timestamp: string;
}
