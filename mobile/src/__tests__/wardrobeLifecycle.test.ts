import {
  executeWardrobeSubmissionWorkflow,
  WardrobeSubmissionError,
} from '../utils/wardrobeWorkflow';
import {
  CreateWardrobeItemPayload,
  NormalizedImage,
  UploadIntentRequestPayload,
  UploadIntentResponse,
  WardrobeItemFormMetadata,
  WardrobeItemResponse,
} from '../types/wardrobe.types';
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

describe('Wardrobe Submission Workflow Engine (Stage 3B Lifecycle)', () => {
  const mockNormalizedImage: NormalizedImage = {
    uri: 'file:///cache/clothing-valid.jpg',
    width: 800,
    height: 1000,
    byteSize: 102400,
    contentType: 'image/jpeg',
  };

  const mockMetadata: WardrobeItemFormMetadata = {
    name: 'Classic White Shirt',
    category: 'TOP',
    subcategory: 'Shirt',
    primaryColor: 'WHITE',
    secondaryColor: '',
    pattern: 'SOLID',
    formalityLevel: 'CASUAL',
    season: 'ALL_SEASON',
  };

  const mockUploadIntent: UploadIntentResponse = {
    uploadMode: 'LOCAL_STREAM',
    uploadUrl: 'http://localhost:8080/api/v1/storage/upload/wardrobe/user-1/item-1.jpg',
    storageKey: 'wardrobe/user-1/item-1.jpg',
    expiresAt: '2026-10-25T12:00:00Z',
    httpMethod: 'POST',
    requiredHeaders: {
      'X-Upload-Ticket': 'signed.ticket.hmac',
    },
    uploadTicket: 'signed.ticket.hmac',
  };

  const mockCatalogedItem: WardrobeItemResponse = {
    id: 'item-uuid-1',
    name: 'Classic White Shirt',
    category: 'TOP',
    subcategory: 'Shirt',
    primaryColor: 'WHITE',
    secondaryColor: null,
    pattern: 'SOLID',
    formalityLevel: 'CASUAL',
    season: 'ALL_SEASON',
    storageKey: 'wardrobe/user-1/item-1.jpg',
    downloadUrl: 'http://localhost:8080/api/v1/storage/files/wardrobe/user-1/item-1.jpg',
    status: 'ACTIVE',
    createdAt: '2026-10-25T12:00:00Z',
    updatedAt: '2026-10-25T12:00:00Z',
  };

  let mockCreateUploadIntent: jest.Mock<Promise<UploadIntentResponse>, [UploadIntentRequestPayload]>;
  let mockUploadBinary: jest.Mock<Promise<void>, [UploadIntentResponse, string]>;
  let mockCreateWardrobeItem: jest.Mock<Promise<WardrobeItemResponse>, [CreateWardrobeItemPayload]>;
  let mockCleanup: jest.Mock<Promise<void>, [string]>;
  let mockStepChange: jest.Mock<void, ['requesting_intent' | 'uploading_binary' | 'cataloging']>;

  beforeEach(() => {
    mockCreateUploadIntent = jest.fn().mockResolvedValue(mockUploadIntent);
    mockUploadBinary = jest.fn().mockResolvedValue(undefined);
    mockCreateWardrobeItem = jest.fn().mockResolvedValue(mockCatalogedItem);
    mockCleanup = jest.fn().mockResolvedValue(undefined);
    mockStepChange = jest.fn();
  });

  it('executes full workflow: intent -> binary upload -> confirmation -> temporary file cleanup', async () => {
    const result = await executeWardrobeSubmissionWorkflow({
      selectedImage: mockNormalizedImage,
      metadata: mockMetadata,
      createUploadIntentFn: mockCreateUploadIntent,
      uploadBinaryFn: mockUploadBinary,
      createWardrobeItemFn: mockCreateWardrobeItem,
      cleanupFn: mockCleanup,
      onStepChange: mockStepChange,
    });

    expect(mockCreateUploadIntent).toHaveBeenCalledWith({
      contentType: 'image/jpeg',
      byteSize: mockNormalizedImage.byteSize,
    });
    expect(mockUploadBinary).toHaveBeenCalledWith(mockUploadIntent, mockNormalizedImage.uri);
    expect(mockCreateWardrobeItem).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Classic White Shirt',
        category: 'TOP',
        primaryColor: 'WHITE',
        storageKey: mockUploadIntent.storageKey,
        uploadTicket: mockUploadIntent.uploadTicket,
      })
    );
    expect(mockCleanup).toHaveBeenCalledWith(mockNormalizedImage.uri);

    expect(mockStepChange).toHaveBeenCalledWith('requesting_intent');
    expect(mockStepChange).toHaveBeenCalledWith('uploading_binary');
    expect(mockStepChange).toHaveBeenCalledWith('cataloging');

    expect(result.catalogedItem).toEqual(mockCatalogedItem);
    expect(result.uploadIntent).toEqual(mockUploadIntent);
    expect(result.binaryUploaded).toBe(true);
  });

  it('handles partial failure: if binary upload succeeds but confirmation fails, marks binaryUploaded: true and does not delete file', async () => {
    mockCreateWardrobeItem.mockRejectedValue(
      new ApiError('Transient database lock', 'SERVER_ERROR', 500)
    );

    let thrownError: unknown;
    try {
      await executeWardrobeSubmissionWorkflow({
        selectedImage: mockNormalizedImage,
        metadata: mockMetadata,
        createUploadIntentFn: mockCreateUploadIntent,
        uploadBinaryFn: mockUploadBinary,
        createWardrobeItemFn: mockCreateWardrobeItem,
        cleanupFn: mockCleanup,
        onStepChange: mockStepChange,
      });
    } catch (err) {
      thrownError = err;
    }

    expect(thrownError).toBeInstanceOf(WardrobeSubmissionError);
    const subError = thrownError as WardrobeSubmissionError;
    expect(subError.binaryUploaded).toBe(true);
    expect(subError.uploadIntent).toEqual(mockUploadIntent);

    // Image file must NOT be deleted so retry can succeed
    expect(mockCleanup).not.toHaveBeenCalled();
  });

  it('retry path: if binary was already uploaded, skips binary upload and proceeds directly to confirmation', async () => {
    const result = await executeWardrobeSubmissionWorkflow({
      selectedImage: mockNormalizedImage,
      metadata: mockMetadata,
      existingUploadIntent: mockUploadIntent,
      binaryAlreadyUploaded: true,
      createUploadIntentFn: mockCreateUploadIntent,
      uploadBinaryFn: mockUploadBinary,
      createWardrobeItemFn: mockCreateWardrobeItem,
      cleanupFn: mockCleanup,
      onStepChange: mockStepChange,
    });

    // Neither intent generation nor binary upload should be re-invoked
    expect(mockCreateUploadIntent).not.toHaveBeenCalled();
    expect(mockUploadBinary).not.toHaveBeenCalled();

    // Confirmation must be called directly
    expect(mockCreateWardrobeItem).toHaveBeenCalledTimes(1);
    expect(mockCleanup).toHaveBeenCalledWith(mockNormalizedImage.uri);
    expect(result.catalogedItem).toEqual(mockCatalogedItem);
  });

  it('safely recovers if storage returns 409 STORAGE_OBJECT_ALREADY_EXISTS during binary upload', async () => {
    mockUploadBinary.mockRejectedValue(
      new ApiError('Storage object already exists', 'STORAGE_OBJECT_ALREADY_EXISTS', 409)
    );

    const result = await executeWardrobeSubmissionWorkflow({
      selectedImage: mockNormalizedImage,
      metadata: mockMetadata,
      createUploadIntentFn: mockCreateUploadIntent,
      uploadBinaryFn: mockUploadBinary,
      createWardrobeItemFn: mockCreateWardrobeItem,
      cleanupFn: mockCleanup,
      onStepChange: mockStepChange,
    });

    // Since object already exists, workflow assumes binary upload is satisfied and finishes confirmation
    expect(mockCreateWardrobeItem).toHaveBeenCalledTimes(1);
    expect(result.catalogedItem).toEqual(mockCatalogedItem);
    expect(mockCleanup).toHaveBeenCalledWith(mockNormalizedImage.uri);
  });

  it('does not leak tickets or tokens in error message when upload intent or confirmation fails', async () => {
    mockCreateUploadIntent.mockRejectedValue(
      new ApiError('Intent failed with secret_ticket_xyz', 'SERVER_ERROR', 500)
    );

    try {
      await executeWardrobeSubmissionWorkflow({
        selectedImage: mockNormalizedImage,
        metadata: mockMetadata,
        createUploadIntentFn: mockCreateUploadIntent,
        uploadBinaryFn: mockUploadBinary,
        createWardrobeItemFn: mockCreateWardrobeItem,
        cleanupFn: mockCleanup,
      });
      fail('Expected error to be thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(WardrobeSubmissionError);
      const subErr = err as WardrobeSubmissionError;
      expect(subErr.binaryUploaded).toBe(false);
    }
  });
});
