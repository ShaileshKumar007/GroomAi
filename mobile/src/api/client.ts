import { HealthResponse } from '../types/api.types';

const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

/**
 * Public health check fetch helper.
 * Zero use of `any` — strongly typed against docs/API_SPEC.md.
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
