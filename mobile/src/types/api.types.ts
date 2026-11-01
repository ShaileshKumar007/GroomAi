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
  readonly missingCategories?: readonly string[];
}

export interface HealthResponse {
  readonly status: string;
  readonly service: string;
  readonly timestamp: string;
}

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly missingCategories?: readonly string[];

  constructor(
    message: string,
    code: string,
    status: number,
    missingCategories?: readonly string[]
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.missingCategories = missingCategories;
  }
}
