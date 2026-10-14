/**
 * Client-side validation utilities matching docs/API_SPEC.md constraints.
 * Strict typing with zero `any`.
 */

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_UPPERCASE = /[A-Z]/;
const PASSWORD_LOWERCASE = /[a-z]/;
const PASSWORD_DIGIT = /\d/;
const PASSWORD_SPECIAL = /[^a-zA-Z\d]/;

export function validateEmail(email: string): string | null {
  const trimmed = email.trim();
  if (!trimmed) {
    return 'Email is required.';
  }
  if (trimmed.length > 255) {
    return 'Email must not exceed 255 characters.';
  }
  if (!EMAIL_REGEX.test(trimmed)) {
    return 'Must be a valid email address.';
  }
  return null;
}

export function validatePassword(password: string): string | null {
  if (!password) {
    return 'Password is required.';
  }
  if (password.length < 8 || password.length > 100) {
    return 'Password must be between 8 and 100 characters.';
  }
  if (!PASSWORD_UPPERCASE.test(password)) {
    return 'Password must contain at least one uppercase letter.';
  }
  if (!PASSWORD_LOWERCASE.test(password)) {
    return 'Password must contain at least one lowercase letter.';
  }
  if (!PASSWORD_DIGIT.test(password)) {
    return 'Password must contain at least one digit.';
  }
  if (!PASSWORD_SPECIAL.test(password)) {
    return 'Password must contain at least one special character.';
  }
  return null;
}

export function validateDisplayName(name?: string): string | null {
  if (!name || !name.trim()) {
    return null; // Optional
  }
  const trimmed = name.trim();
  if (trimmed.length < 2 || trimmed.length > 50) {
    return 'Display name must be between 2 and 50 characters.';
  }
  return null;
}
