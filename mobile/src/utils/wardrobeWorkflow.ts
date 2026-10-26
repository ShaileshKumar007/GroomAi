import * as ImagePicker from 'expo-image-picker';
import { ApiError } from '../types/api.types';
import {
  normalizeImageToJpeg,
  cleanupNormalizedImage,
  ImageNormalizationError,
} from './imageNormalization';
import {
  Category,
  Color,
  CreateWardrobeItemPayload,
  NormalizedImage,
  UploadIntentRequestPayload,
  UploadIntentResponse,
  WardrobeError,
  WardrobeErrorType,
  WardrobeItemFormMetadata,
  WardrobeItemResponse,
} from '../types/wardrobe.types';

export interface PermissionCheckResult {
  readonly granted: boolean;
  readonly error?: WardrobeError;
}

/**
 * Checks and requests required permissions for camera or media library.
 */
export async function validateImagePermissions(
  source: 'camera' | 'library'
): Promise<PermissionCheckResult> {
  if (source === 'camera') {
    const { status, canAskAgain } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== ImagePicker.PermissionStatus.GRANTED) {
      if (!canAskAgain) {
        return {
          granted: false,
          error: {
            type: 'PERMISSION_PERMANENTLY_DENIED',
            message:
              'Camera permission was permanently denied. Please open device Settings to allow camera access.',
          },
        };
      }
      return {
        granted: false,
        error: {
          type: 'PERMISSION_DENIED',
          message: 'Camera access is required to take clothing photos.',
        },
      };
    }
  } else {
    const { status, canAskAgain } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== ImagePicker.PermissionStatus.GRANTED) {
      if (!canAskAgain) {
        return {
          granted: false,
          error: {
            type: 'PERMISSION_PERMANENTLY_DENIED',
            message:
              'Photo library permission was permanently denied. Please open device Settings to allow photo access.',
          },
        };
      }
      return {
        granted: false,
        error: {
          type: 'PERMISSION_DENIED',
          message: 'Photo library access is required to select clothing photos.',
        },
      };
    }
  }

  return { granted: true };
}

/**
 * Executes full image acquisition workflow: permission check -> native picker -> JPEG normalization.
 * Returns null if the user cancels the picker.
 */
export async function pickAndNormalizeClothingImage(
  source: 'camera' | 'library',
  previousImageUri?: string | null
): Promise<NormalizedImage | null> {
  const permResult = await validateImagePermissions(source);
  if (!permResult.granted) {
    const error = permResult.error;
    throw new ImageNormalizationError(
      error?.message || 'Permission denied.',
      error?.type || 'PERMISSION_DENIED'
    );
  }

  const pickerResult =
    source === 'camera'
      ? await ImagePicker.launchCameraAsync({
          mediaTypes: ['images'],
          allowsEditing: false,
          quality: 1,
        })
      : await ImagePicker.launchImageLibraryAsync({
          mediaTypes: ['images'],
          allowsEditing: false,
          quality: 1,
        });

  if (pickerResult.canceled || !pickerResult.assets?.[0]?.uri) {
    return null; // Graceful user cancellation
  }

  const sourceUri = pickerResult.assets[0].uri;

  // Clean up any previously cached normalized image before generating new one
  if (previousImageUri) {
    await cleanupNormalizedImage(previousImageUri);
  }

  return await normalizeImageToJpeg(sourceUri);
}

/**
 * Translates backend API errors, normalization errors, or network errors
 * into structured user-facing messages without leaking credentials or tickets.
 */
export function formatWardrobeError(err: unknown): WardrobeError {
  if (err instanceof ApiError) {
    if (err.code === 'CONSENT_REQUIRED') {
      return {
        type: 'CONSENT_REQUIRED',
        message:
          'Wardrobe storage consent is required before requesting an upload intent. Please grant storage consent in your profile settings.',
        status: err.status,
      };
    }
    if (err.code === 'UNSUPPORTED_IMAGE_FORMAT') {
      return {
        type: 'UNSUPPORTED_FORMAT',
        message: 'Unsupported image format: only JPEG images are accepted.',
        status: err.status,
      };
    }
    if (err.code === 'EXPIRED_UPLOAD_TICKET') {
      return {
        type: 'EXPIRED_TICKET',
        message: 'Upload authorization ticket has expired. Please request a new upload authorization.',
        status: err.status,
      };
    }
    if (err.code === 'INVALID_UPLOAD_TICKET') {
      return {
        type: 'INVALID_TICKET',
        message: 'Upload authorization ticket is invalid or missing.',
        status: err.status,
      };
    }
    if (err.code === 'STORAGE_OBJECT_ALREADY_EXISTS') {
      return {
        type: 'STORAGE_OBJECT_ALREADY_EXISTS',
        message: 'The image file already exists in storage. You can proceed directly to catalog confirmation.',
        status: err.status,
      };
    }
    if (err.code === 'DUPLICATE_ITEM') {
      return {
        type: 'DUPLICATE_ITEM',
        message: 'This clothing item has already been cataloged in your wardrobe.',
        status: err.status,
      };
    }
    if (err.code === 'INVALID_IMAGE_PAYLOAD') {
      return {
        type: 'INVALID_PAYLOAD',
        message: 'The image payload failed server verification (must be a valid JPEG of at least 200×200 pixels).',
        status: err.status,
      };
    }
    if (err.code === 'NETWORK_ERROR') {
      return {
        type: 'NETWORK_ERROR',
        message: err.message || 'Network connection lost. Please check your connectivity and try again.',
        status: err.status,
      };
    }
    if (err.status === 401) {
      return {
        type: 'UNAUTHORIZED',
        message: 'Session expired. Please sign in again.',
        status: err.status,
      };
    }
    return {
      type: 'SERVER_ERROR',
      message: err.message || 'Server request failed.',
      status: err.status,
    };
  }

  if (err instanceof ImageNormalizationError) {
    let type: WardrobeErrorType = 'NORMALIZATION_FAILED';
    if (err.code === 'IMAGE_TOO_SMALL') type = 'IMAGE_TOO_SMALL';
    if (err.code === 'FILE_TOO_LARGE') type = 'FILE_TOO_LARGE';
    if (err.code === 'PERMISSION_DENIED') type = 'PERMISSION_DENIED';
    if (err.code === 'PERMISSION_PERMANENTLY_DENIED') type = 'PERMISSION_PERMANENTLY_DENIED';

    return {
      type,
      message: err.message,
    };
  }

  const msg = err instanceof Error ? err.message : String(err);
  if (msg.includes('Network') || msg.includes('Failed to fetch')) {
    return {
      type: 'NETWORK_ERROR',
      message: 'Unable to connect to the Groom.ai backend. Please check your connection.',
    };
  }

  return {
    type: 'UNKNOWN',
    message: msg || 'An unexpected error occurred.',
  };
}

export interface ExecuteWardrobeSubmissionParams {
  readonly selectedImage: NormalizedImage;
  readonly metadata: WardrobeItemFormMetadata;
  readonly existingUploadIntent?: UploadIntentResponse | null;
  readonly binaryAlreadyUploaded?: boolean;
  createUploadIntentFn: (payload: UploadIntentRequestPayload) => Promise<UploadIntentResponse>;
  uploadBinaryFn: (intent: UploadIntentResponse, uri: string) => Promise<void>;
  createWardrobeItemFn: (payload: CreateWardrobeItemPayload) => Promise<WardrobeItemResponse>;
  cleanupFn: (uri: string) => Promise<void>;
  onStepChange?: (step: 'requesting_intent' | 'uploading_binary' | 'cataloging') => void;
}

export interface ExecuteWardrobeSubmissionResult {
  readonly catalogedItem: WardrobeItemResponse;
  readonly uploadIntent: UploadIntentResponse;
  readonly binaryUploaded: true;
}

export class WardrobeSubmissionError extends Error {
  readonly code: string;
  readonly binaryUploaded: boolean;
  readonly uploadIntent: UploadIntentResponse | null;
  readonly originalError: unknown;

  constructor(
    message: string,
    code: string,
    binaryUploaded: boolean,
    uploadIntent: UploadIntentResponse | null,
    originalError: unknown
  ) {
    super(message);
    this.name = 'WardrobeSubmissionError';
    this.code = code;
    this.binaryUploaded = binaryUploaded;
    this.uploadIntent = uploadIntent;
    this.originalError = originalError;
  }
}

/**
 * Pure submission orchestration workflow supporting partial failure recovery:
 * 1. Obtains upload intent if not already authorized.
 * 2. Uploads binary image if not already uploaded (handling 409 already exists safely).
 * 3. Confirms catalog creation.
 * 4. Cleans up temporary image on successful confirmation.
 */
export async function executeWardrobeSubmissionWorkflow(
  params: ExecuteWardrobeSubmissionParams
): Promise<ExecuteWardrobeSubmissionResult> {
  const {
    selectedImage,
    metadata,
    existingUploadIntent,
    binaryAlreadyUploaded,
    createUploadIntentFn,
    uploadBinaryFn,
    createWardrobeItemFn,
    cleanupFn,
    onStepChange,
  } = params;

  let currentIntent = existingUploadIntent ?? null;
  let isBinaryUploaded = binaryAlreadyUploaded ?? false;

  // 1. Authorize Upload Intent
  if (!currentIntent) {
    onStepChange?.('requesting_intent');
    try {
      currentIntent = await createUploadIntentFn({
        contentType: selectedImage.contentType,
        byteSize: selectedImage.byteSize,
      });
    } catch (err) {
      const formatted = formatWardrobeError(err);
      throw new WardrobeSubmissionError(
        formatted.message,
        formatted.type,
        false,
        null,
        err
      );
    }
  }

  // 2. Upload Normalized Binary
  if (!isBinaryUploaded) {
    onStepChange?.('uploading_binary');
    try {
      await uploadBinaryFn(currentIntent, selectedImage.uri);
      isBinaryUploaded = true;
    } catch (err) {
      const formatted = formatWardrobeError(err);
      if (formatted.type === 'STORAGE_OBJECT_ALREADY_EXISTS') {
        isBinaryUploaded = true;
      } else {
        throw new WardrobeSubmissionError(
          formatted.message,
          formatted.type,
          false,
          currentIntent,
          err
        );
      }
    }
  }

  // 3. Confirm and Catalog Item
  onStepChange?.('cataloging');
  let cataloged: WardrobeItemResponse;
  try {
    const payload: CreateWardrobeItemPayload = {
      name: metadata.name.trim(),
      category: metadata.category as Category,
      subcategory: metadata.subcategory.trim() ? metadata.subcategory.trim() : null,
      primaryColor: metadata.primaryColor as Color,
      secondaryColor: metadata.secondaryColor ? (metadata.secondaryColor as Color) : null,
      pattern: metadata.pattern,
      formalityLevel: metadata.formalityLevel,
      season: metadata.season,
      storageKey: currentIntent.storageKey,
      uploadTicket: currentIntent.uploadTicket ?? null,
    };

    cataloged = await createWardrobeItemFn(payload);
  } catch (err) {
    const formatted = formatWardrobeError(err);
    // Since binary upload already succeeded, record binaryUploaded: true in the error!
    throw new WardrobeSubmissionError(
      formatted.message,
      formatted.type,
      true,
      currentIntent,
      err
    );
  }

  // 4. Safe temporary file cleanup after verified confirmation
  if (selectedImage.uri) {
    await cleanupFn(selectedImage.uri);
  }

  return {
    catalogedItem: cataloged,
    uploadIntent: currentIntent,
    binaryUploaded: true,
  };
}
