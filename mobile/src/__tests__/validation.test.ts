import {
  validateDisplayName,
  validateEmail,
  validatePassword,
} from '../utils/validation';

describe('Validation Utilities', () => {
  describe('validateEmail', () => {
    it('accepts valid email addresses', () => {
      expect(validateEmail('user@example.com')).toBeNull();
      expect(validateEmail('alex.sharma@groom.ai')).toBeNull();
    });

    it('rejects empty or whitespace-only email', () => {
      expect(validateEmail('')).toBe('Email is required.');
      expect(validateEmail('   ')).toBe('Email is required.');
    });

    it('rejects malformed email format', () => {
      expect(validateEmail('invalid-email')).toBe('Must be a valid email address.');
      expect(validateEmail('user@')).toBe('Must be a valid email address.');
      expect(validateEmail('@domain.com')).toBe('Must be a valid email address.');
    });

    it('rejects email exceeding 255 characters', () => {
      const longEmail = `${'a'.repeat(250)}@example.com`;
      expect(validateEmail(longEmail)).toBe('Email must not exceed 255 characters.');
    });
  });

  describe('validatePassword', () => {
    it('accepts strong password meeting all criteria', () => {
      expect(validatePassword('SecurePass123!')).toBeNull();
      expect(validatePassword('GroomAI#2026')).toBeNull();
    });

    it('rejects empty password', () => {
      expect(validatePassword('')).toBe('Password is required.');
    });

    it('rejects password shorter than 8 characters', () => {
      expect(validatePassword('Pass1!')).toBe(
        'Password must be between 8 and 100 characters.'
      );
    });

    it('rejects password missing uppercase letter', () => {
      expect(validatePassword('password123!')).toBe(
        'Password must contain at least one uppercase letter.'
      );
    });

    it('rejects password missing lowercase letter', () => {
      expect(validatePassword('PASSWORD123!')).toBe(
        'Password must contain at least one lowercase letter.'
      );
    });

    it('rejects password missing digit', () => {
      expect(validatePassword('Password!!!!')).toBe(
        'Password must contain at least one digit.'
      );
    });

    it('rejects password missing special character', () => {
      expect(validatePassword('Password123')).toBe(
        'Password must contain at least one special character.'
      );
    });
  });

  describe('validateDisplayName', () => {
    it('accepts valid or undefined display names (optional field)', () => {
      expect(validateDisplayName(undefined)).toBeNull();
      expect(validateDisplayName('')).toBeNull();
      expect(validateDisplayName('Alex Sharma')).toBeNull();
    });

    it('rejects display name shorter than 2 characters', () => {
      expect(validateDisplayName('A')).toBe(
        'Display name must be between 2 and 50 characters.'
      );
    });

    it('rejects display name longer than 50 characters', () => {
      expect(validateDisplayName('A'.repeat(51))).toBe(
        'Display name must be between 2 and 50 characters.'
      );
    });
  });
});
