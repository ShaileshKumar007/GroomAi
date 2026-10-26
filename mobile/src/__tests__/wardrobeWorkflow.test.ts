import {
  validateImagePermissions,
  pickAndNormalizeClothingImage,
  formatWardrobeError,
} from '../utils/wardrobeWorkflow';
import * as ImagePicker from 'expo-image-picker';
import * as imageNormalization from '../utils/imageNormalization';
import { ApiError } from '../types/api.types';

jest.mock('expo-image-picker', () => ({
  requestCameraPermissionsAsync: jest.fn(),
  requestMediaLibraryPermissionsAsync: jest.fn(),
  launchCameraAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
  PermissionStatus: {
    GRANTED: 'granted',
    DENIED: 'denied',
    UNDETERMINED: 'undetermined',
  },
}));

jest.mock('../utils/imageNormalization', () => {
  class MockImageNormalizationError extends Error {
    readonly code: string;
    constructor(message: string, code: string) {
      super(message);
      this.name = 'ImageNormalizationError';
      this.code = code;
    }
  }
  return {
    normalizeImageToJpeg: jest.fn(),
    cleanupNormalizedImage: jest.fn(),
    ImageNormalizationError: MockImageNormalizationError,
  };
});

describe('Wardrobe Workflow Utilities', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('validateImagePermissions', () => {
    it('returns granted true when camera permission is granted', async () => {
      (ImagePicker.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.GRANTED,
        canAskAgain: true,
      });

      const result = await validateImagePermissions('camera');
      expect(result.granted).toBe(true);
      expect(result.error).toBeUndefined();
    });

    it('returns PERMISSION_DENIED when camera permission is denied with canAskAgain=true', async () => {
      (ImagePicker.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.DENIED,
        canAskAgain: true,
      });

      const result = await validateImagePermissions('camera');
      expect(result.granted).toBe(false);
      expect(result.error?.type).toBe('PERMISSION_DENIED');
      expect(result.error?.message).toContain('Camera access is required');
    });

    it('returns PERMISSION_PERMANENTLY_DENIED when camera permission is denied with canAskAgain=false', async () => {
      (ImagePicker.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.DENIED,
        canAskAgain: false,
      });

      const result = await validateImagePermissions('camera');
      expect(result.granted).toBe(false);
      expect(result.error?.type).toBe('PERMISSION_PERMANENTLY_DENIED');
      expect(result.error?.message).toContain('Settings');
    });

    it('returns PERMISSION_DENIED when photo library permission is denied', async () => {
      (ImagePicker.requestMediaLibraryPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.DENIED,
        canAskAgain: true,
      });

      const result = await validateImagePermissions('library');
      expect(result.granted).toBe(false);
      expect(result.error?.type).toBe('PERMISSION_DENIED');
      expect(result.error?.message).toContain('Photo library access is required');
    });

    it('returns PERMISSION_PERMANENTLY_DENIED when photo library permission is permanently denied', async () => {
      (ImagePicker.requestMediaLibraryPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.DENIED,
        canAskAgain: false,
      });

      const result = await validateImagePermissions('library');
      expect(result.granted).toBe(false);
      expect(result.error?.type).toBe('PERMISSION_PERMANENTLY_DENIED');
      expect(result.error?.message).toContain('Settings');
    });
  });

  describe('pickAndNormalizeClothingImage', () => {
    it('throws error when permission is denied', async () => {
      (ImagePicker.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.DENIED,
        canAskAgain: true,
      });

      await expect(pickAndNormalizeClothingImage('camera')).rejects.toThrow(
        imageNormalization.ImageNormalizationError
      );
    });

    it('returns null on graceful user cancellation', async () => {
      (ImagePicker.requestMediaLibraryPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.GRANTED,
        canAskAgain: true,
      });

      (ImagePicker.launchImageLibraryAsync as jest.Mock).mockResolvedValue({
        canceled: true,
        assets: null,
      });

      const result = await pickAndNormalizeClothingImage('library');
      expect(result).toBeNull();
      expect(imageNormalization.normalizeImageToJpeg).not.toHaveBeenCalled();
    });

    it('normalizes selected image to JPEG and cleans up previous image', async () => {
      (ImagePicker.requestMediaLibraryPermissionsAsync as jest.Mock).mockResolvedValue({
        status: ImagePicker.PermissionStatus.GRANTED,
        canAskAgain: true,
      });

      (ImagePicker.launchImageLibraryAsync as jest.Mock).mockResolvedValue({
        canceled: false,
        assets: [{ uri: 'ph://new-image.heic' }],
      });

      (imageNormalization.normalizeImageToJpeg as jest.Mock).mockResolvedValue({
        uri: 'file:///cache/normalized.jpg',
        width: 1200,
        height: 1600,
        byteSize: 340000,
        contentType: 'image/jpeg',
      });

      const result = await pickAndNormalizeClothingImage('library', 'file:///cache/old.jpg');

      expect(imageNormalization.cleanupNormalizedImage).toHaveBeenCalledWith('file:///cache/old.jpg');
      expect(imageNormalization.normalizeImageToJpeg).toHaveBeenCalledWith('ph://new-image.heic');
      expect(result).toEqual({
        uri: 'file:///cache/normalized.jpg',
        width: 1200,
        height: 1600,
        byteSize: 340000,
        contentType: 'image/jpeg',
      });
    });
  });

  describe('formatWardrobeError', () => {
    it('formats ApiError with CONSENT_REQUIRED properly', () => {
      const apiErr = new ApiError('Consent missing', 'CONSENT_REQUIRED', 403);
      const formatted = formatWardrobeError(apiErr);
      expect(formatted.type).toBe('CONSENT_REQUIRED');
      expect(formatted.message).toContain('Wardrobe storage consent is required');
      expect(formatted.status).toBe(403);
    });

    it('formats ApiError with UNSUPPORTED_IMAGE_FORMAT properly', () => {
      const apiErr = new ApiError('Format rejected', 'UNSUPPORTED_IMAGE_FORMAT', 400);
      const formatted = formatWardrobeError(apiErr);
      expect(formatted.type).toBe('UNSUPPORTED_FORMAT');
      expect(formatted.message).toContain('only JPEG');
    });

    it('formats 401 ApiError as UNAUTHORIZED session expired', () => {
      const apiErr = new ApiError('Token expired', 'UNAUTHORIZED', 401);
      const formatted = formatWardrobeError(apiErr);
      expect(formatted.type).toBe('UNAUTHORIZED');
      expect(formatted.message).toContain('Session expired');
    });

    it('formats ImageNormalizationError correctly', () => {
      const normErr = new imageNormalization.ImageNormalizationError(
        'Image dimensions (100x100) must be at least 200x200',
        'IMAGE_TOO_SMALL'
      );
      const formatted = formatWardrobeError(normErr);
      expect(formatted.type).toBe('IMAGE_TOO_SMALL');
      expect(formatted.message).toBe('Image dimensions (100x100) must be at least 200x200');
    });

    it('formats Network error properly', () => {
      const netErr = new Error('Network request failed');
      const formatted = formatWardrobeError(netErr);
      expect(formatted.type).toBe('NETWORK_ERROR');
      expect(formatted.message).toContain('Unable to connect to the Groom.ai backend');
    });
  });
});
