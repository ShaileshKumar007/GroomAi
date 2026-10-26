import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';
import * as FileSystem from 'expo-file-system/legacy';
import { NormalizedImage } from '../types/wardrobe.types';

export const MIN_IMAGE_DIMENSION_PX = 200;
export const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
export const JPEG_COMPRESSION_QUALITY = 0.85;

export class ImageNormalizationError extends Error {
  readonly code: string;

  constructor(message: string, code: string) {
    super(message);
    this.name = 'ImageNormalizationError';
    this.code = code;
  }
}

/**
 * Normalizes an image from local URI (HEIC/HEIF/PNG/JPEG) into a valid JPEG file
 * with explicit compression quality, dimension validation (>= 200x200), and
 * size validation (<= 10MB).
 */
export async function normalizeImageToJpeg(sourceUri: string): Promise<NormalizedImage> {
  if (!sourceUri || sourceUri.trim().length === 0) {
    throw new ImageNormalizationError(
      'Invalid image source URI provided.',
      'INVALID_URI'
    );
  }

  let manipulatedResult;
  try {
    manipulatedResult = await manipulateAsync(
      sourceUri,
      [], // No crop/resize actions; purely transcode to standard JPEG
      {
        format: SaveFormat.JPEG,
        compress: JPEG_COMPRESSION_QUALITY,
      }
    );
  } catch (err) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    throw new ImageNormalizationError(
      `Failed to process or transcode image: ${errorMsg}`,
      'NORMALIZATION_FAILED'
    );
  }

  if (!manipulatedResult || !manipulatedResult.uri) {
    throw new ImageNormalizationError(
      'Image processing did not return a valid output file URI.',
      'NORMALIZATION_FAILED'
    );
  }

  const { uri, width, height } = manipulatedResult;

  // Validate minimum dimensions (200x200)
  if (width < MIN_IMAGE_DIMENSION_PX || height < MIN_IMAGE_DIMENSION_PX) {
    // Clean up rejected temporary output file
    await cleanupNormalizedImage(uri);
    throw new ImageNormalizationError(
      `Image dimensions (${width}×${height} px) must be at least ${MIN_IMAGE_DIMENSION_PX}×${MIN_IMAGE_DIMENSION_PX} pixels.`,
      'IMAGE_TOO_SMALL'
    );
  }

  // Inspect resulting file size
  let byteSize: number;
  try {
    const fileInfo = await FileSystem.getInfoAsync(uri);
    if (!fileInfo.exists) {
      throw new ImageNormalizationError(
        'Processed JPEG file was not found on device storage.',
        'FILE_NOT_FOUND'
      );
    }
    byteSize = fileInfo.size ?? 0;
  } catch (err) {
    if (err instanceof ImageNormalizationError) {
      throw err;
    }
    const errorMsg = err instanceof Error ? err.message : String(err);
    throw new ImageNormalizationError(
      `Failed to read metadata for processed image: ${errorMsg}`,
      'STORAGE_READ_ERROR'
    );
  }

  // Validate maximum file size (10MB)
  if (byteSize > MAX_FILE_SIZE_BYTES) {
    await cleanupNormalizedImage(uri);
    const sizeInMb = (byteSize / (1024 * 1024)).toFixed(1);
    throw new ImageNormalizationError(
      `Processed image size (${sizeInMb}MB) exceeds maximum limit of 10MB.`,
      'FILE_TOO_LARGE'
    );
  }

  return {
    uri,
    width,
    height,
    byteSize,
    contentType: 'image/jpeg',
  };
}

/**
 * Safely removes a temporary normalized image file from cache storage.
 */
export async function cleanupNormalizedImage(fileUri: string | null | undefined): Promise<void> {
  if (!fileUri) return;
  try {
    await FileSystem.deleteAsync(fileUri, { idempotent: true });
  } catch {
    // Non-fatal cleanup suppression
  }
}
