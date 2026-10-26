import {
  normalizeImageToJpeg,
  cleanupNormalizedImage,
  ImageNormalizationError,
  MAX_FILE_SIZE_BYTES,
  JPEG_COMPRESSION_QUALITY,
} from '../utils/imageNormalization';
import * as ImageManipulator from 'expo-image-manipulator';
import * as FileSystem from 'expo-file-system/legacy';

jest.mock('expo-image-manipulator', () => ({
  manipulateAsync: jest.fn(),
  SaveFormat: {
    JPEG: 'jpeg',
    PNG: 'png',
  },
}));

jest.mock('expo-file-system/legacy', () => ({
  getInfoAsync: jest.fn(),
  deleteAsync: jest.fn(),
}));

describe('Image Normalization Utility', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('successfully normalizes a valid image to JPEG with explicit quality', async () => {
    (ImageManipulator.manipulateAsync as jest.Mock).mockResolvedValue({
      uri: 'file:///cache/normalized.jpg',
      width: 800,
      height: 1000,
    });

    (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
      exists: true,
      size: 524288, // 512 KB
    });

    const result = await normalizeImageToJpeg('ph://image-123.heic');

    expect(ImageManipulator.manipulateAsync).toHaveBeenCalledWith(
      'ph://image-123.heic',
      [],
      {
        format: ImageManipulator.SaveFormat.JPEG,
        compress: JPEG_COMPRESSION_QUALITY,
      }
    );

    expect(result).toEqual({
      uri: 'file:///cache/normalized.jpg',
      width: 800,
      height: 1000,
      byteSize: 524288,
      contentType: 'image/jpeg',
    });
  });

  it('rejects image when dimensions are below minimum 200x200 pixels and cleans up file', async () => {
    (ImageManipulator.manipulateAsync as jest.Mock).mockResolvedValue({
      uri: 'file:///cache/too_small.jpg',
      width: 150,
      height: 300,
    });

    await expect(normalizeImageToJpeg('file:///source/small.jpg')).rejects.toThrow(
      ImageNormalizationError
    );

    await expect(normalizeImageToJpeg('file:///source/small.jpg')).rejects.toMatchObject({
      code: 'IMAGE_TOO_SMALL',
    });

    expect(FileSystem.deleteAsync).toHaveBeenCalledWith('file:///cache/too_small.jpg', {
      idempotent: true,
    });
  });

  it('rejects image when file size exceeds maximum limit (10MB) and cleans up file', async () => {
    (ImageManipulator.manipulateAsync as jest.Mock).mockResolvedValue({
      uri: 'file:///cache/too_large.jpg',
      width: 2000,
      height: 3000,
    });

    (FileSystem.getInfoAsync as jest.Mock).mockResolvedValue({
      exists: true,
      size: MAX_FILE_SIZE_BYTES + 1024, // > 10MB
    });

    await expect(normalizeImageToJpeg('file:///source/huge.heic')).rejects.toThrow(
      ImageNormalizationError
    );

    await expect(normalizeImageToJpeg('file:///source/huge.heic')).rejects.toMatchObject({
      code: 'FILE_TOO_LARGE',
    });

    expect(FileSystem.deleteAsync).toHaveBeenCalledWith('file:///cache/too_large.jpg', {
      idempotent: true,
    });
  });

  it('rejects empty or whitespace URI with INVALID_URI', async () => {
    await expect(normalizeImageToJpeg('')).rejects.toMatchObject({
      code: 'INVALID_URI',
    });
    await expect(normalizeImageToJpeg('   ')).rejects.toMatchObject({
      code: 'INVALID_URI',
    });
  });

  it('wraps native manipulation failure into NORMALIZATION_FAILED', async () => {
    (ImageManipulator.manipulateAsync as jest.Mock).mockRejectedValue(
      new Error('Decoding HEIC failed')
    );

    await expect(normalizeImageToJpeg('file:///corrupt.heic')).rejects.toMatchObject({
      code: 'NORMALIZATION_FAILED',
    });
  });

  it('cleanupNormalizedImage safely removes temporary files', async () => {
    (FileSystem.deleteAsync as jest.Mock).mockResolvedValue(undefined);

    await cleanupNormalizedImage('file:///cache/test.jpg');
    expect(FileSystem.deleteAsync).toHaveBeenCalledWith('file:///cache/test.jpg', {
      idempotent: true,
    });

    // Null/undefined uri should not throw
    await cleanupNormalizedImage(null);
    await cleanupNormalizedImage(undefined);
  });
});
