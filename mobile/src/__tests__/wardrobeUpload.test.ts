import { apiUploadBinary } from '../api/client';
import { UploadIntentResponse } from '../types/wardrobe.types';
import * as FileSystem from 'expo-file-system/legacy';
import * as storage from '../utils/storage';

jest.mock('../utils/storage', () => ({
  getStoredTokens: jest.fn(),
  saveStoredTokens: jest.fn(),
  clearStoredTokens: jest.fn(),
}));

describe('Wardrobe Binary Image Upload', () => {
  const localUploadIntent: UploadIntentResponse = {
    uploadMode: 'LOCAL_STREAM',
    uploadUrl: 'http://localhost:8080/api/v1/storage/upload/wardrobe/user-123/item-456.jpg',
    storageKey: 'wardrobe/user-123/item-456.jpg',
    expiresAt: '2026-10-25T12:00:00Z',
    httpMethod: 'POST',
    requiredHeaders: {
      'Content-Type': 'multipart/form-data',
      'X-Upload-Ticket': 'signed.ticket.hmac',
    },
    uploadTicket: 'signed.ticket.hmac',
  };

  const s3UploadIntent: UploadIntentResponse = {
    uploadMode: 'S3_PRESIGNED',
    uploadUrl: 'https://s3.amazonaws.com/groomai-assets/wardrobe/user-123/item-456.jpg?X-Amz-Signature=abc',
    storageKey: 'wardrobe/user-123/item-456.jpg',
    expiresAt: '2026-10-25T12:00:00Z',
    httpMethod: 'PUT',
    requiredHeaders: {
      'Content-Type': 'image/jpeg',
    },
    uploadTicket: 'signed.ticket.hmac',
  };

  const mockFileUri = 'file:///cache/clothing-normalized.jpg';

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('LOCAL_STREAM Mode', () => {
    it('uploads multipart with Authorization bearer and X-Upload-Ticket headers', async () => {
      (storage.getStoredTokens as jest.Mock).mockResolvedValue({
        accessToken: 'access.jwt.token',
        refreshToken: 'refresh.jwt.token',
      });

      (FileSystem.uploadAsync as jest.Mock).mockResolvedValue({
        status: 200,
        body: JSON.stringify({ message: 'File uploaded successfully.', storageKey: localUploadIntent.storageKey }),
        headers: {},
      });

      await apiUploadBinary(localUploadIntent, mockFileUri);

      expect(FileSystem.uploadAsync).toHaveBeenCalledTimes(1);
      const [url, uri, options] = (FileSystem.uploadAsync as jest.Mock).mock.calls[0];

      expect(url).toBe(localUploadIntent.uploadUrl);
      expect(uri).toBe(mockFileUri);
      expect(options.httpMethod).toBe('POST');
      expect(options.uploadType).toBe(FileSystem.FileSystemUploadType.MULTIPART);
      expect(options.fieldName).toBe('file');
      expect(options.mimeType).toBe('image/jpeg');

      // Authorization & ticket present
      expect(options.headers['Authorization']).toBe('Bearer access.jwt.token');
      expect(options.headers['X-Upload-Ticket']).toBe('signed.ticket.hmac');
      // Content-Type multipart header without boundary removed so native stack manages boundary
      expect(options.headers['Content-Type']).toBeUndefined();
    });

    it('throws ApiError with STORAGE_OBJECT_ALREADY_EXISTS when backend returns 409', async () => {
      (storage.getStoredTokens as jest.Mock).mockResolvedValue({
        accessToken: 'access.jwt.token',
      });

      (FileSystem.uploadAsync as jest.Mock).mockResolvedValue({
        status: 409,
        body: JSON.stringify({
          status: 409,
          code: 'STORAGE_OBJECT_ALREADY_EXISTS',
          message: 'Storage object already exists at storage key: wardrobe/user-123/item-456.jpg',
        }),
        headers: {},
      });

      await expect(apiUploadBinary(localUploadIntent, mockFileUri)).rejects.toMatchObject({
        name: 'ApiError',
        status: 409,
        code: 'STORAGE_OBJECT_ALREADY_EXISTS',
      });
    });

    it('throws ApiError with EXPIRED_UPLOAD_TICKET when ticket has expired (403)', async () => {
      (storage.getStoredTokens as jest.Mock).mockResolvedValue({
        accessToken: 'access.jwt.token',
      });

      (FileSystem.uploadAsync as jest.Mock).mockResolvedValue({
        status: 403,
        body: JSON.stringify({
          status: 403,
          code: 'EXPIRED_UPLOAD_TICKET',
          message: 'Upload intent ticket has expired.',
        }),
        headers: {},
      });

      await expect(apiUploadBinary(localUploadIntent, mockFileUri)).rejects.toMatchObject({
        name: 'ApiError',
        status: 403,
        code: 'EXPIRED_UPLOAD_TICKET',
      });
    });

    it('throws ApiError with INVALID_UPLOAD_TICKET when ticket is missing or invalid (403)', async () => {
      (storage.getStoredTokens as jest.Mock).mockResolvedValue({
        accessToken: 'access.jwt.token',
      });

      (FileSystem.uploadAsync as jest.Mock).mockResolvedValue({
        status: 403,
        body: JSON.stringify({
          status: 403,
          code: 'INVALID_UPLOAD_TICKET',
          message: 'Missing or invalid upload ticket.',
        }),
        headers: {},
      });

      await expect(apiUploadBinary(localUploadIntent, mockFileUri)).rejects.toMatchObject({
        name: 'ApiError',
        status: 403,
        code: 'INVALID_UPLOAD_TICKET',
      });
    });

    it('handles network failure during upload without exposing tokens in error', async () => {
      (storage.getStoredTokens as jest.Mock).mockResolvedValue({
        accessToken: 'access.jwt.token',
      });

      (FileSystem.uploadAsync as jest.Mock).mockRejectedValue(new Error('Connection reset by peer'));

      await expect(apiUploadBinary(localUploadIntent, mockFileUri)).rejects.toMatchObject({
        name: 'ApiError',
        code: 'NETWORK_ERROR',
        message: expect.not.stringContaining('access.jwt.token'),
      });
    });
  });

  describe('S3_PRESIGNED Mode', () => {
    it('uploads binary PUT directly to S3 URL without leaking Authorization or X-Upload-Ticket', async () => {
      (FileSystem.uploadAsync as jest.Mock).mockResolvedValue({
        status: 200,
        body: '',
        headers: {},
      });

      await apiUploadBinary(s3UploadIntent, mockFileUri);

      expect(FileSystem.uploadAsync).toHaveBeenCalledTimes(1);
      const [url, uri, options] = (FileSystem.uploadAsync as jest.Mock).mock.calls[0];

      expect(url).toBe(s3UploadIntent.uploadUrl);
      expect(uri).toBe(mockFileUri);
      expect(options.httpMethod).toBe('PUT');
      expect(options.uploadType).toBe(FileSystem.FileSystemUploadType.BINARY_CONTENT);
      expect(options.headers['Content-Type']).toBe('image/jpeg');

      // Security check: NO credentials passed to external S3
      expect(options.headers['Authorization']).toBeUndefined();
      expect(options.headers['X-Upload-Ticket']).toBeUndefined();
    });

    it('throws ApiError when S3 returns non-200 status', async () => {
      (FileSystem.uploadAsync as jest.Mock).mockResolvedValue({
        status: 403,
        body: '<Error><Code>RequestTimeout</Code></Error>',
        headers: {},
      });

      await expect(apiUploadBinary(s3UploadIntent, mockFileUri)).rejects.toMatchObject({
        name: 'ApiError',
        status: 403,
        code: 'S3_UPLOAD_FAILED',
      });
    });
  });
});
