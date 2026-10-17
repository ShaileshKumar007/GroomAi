package com.groomai.storage;

import com.groomai.storage.exception.InvalidStorageKeyException;
import com.groomai.storage.exception.StorageException;
import com.groomai.storage.exception.StorageObjectNotFoundException;

import java.io.InputStream;
import java.time.Duration;

/**
 * Vendor-neutral object storage abstraction interface.
 * Implemented by LocalStorageService (for local development/testing) and S3StorageService (for cloud production).
 */
public interface ObjectStorageService {

    /**
     * Stores an object at the designated storage key.
     *
     * @param storageKey the sanitized target key (e.g., "wardrobe/{userId}/{filename}.jpg")
     * @param inputStream the data stream to write
     * @param contentLength total byte size of the content
     * @param contentType MIME type of the content (e.g., "image/jpeg")
     * @throws InvalidStorageKeyException if storageKey is invalid or contains traversal sequences
     * @throws StorageException if an I/O error occurs while persisting the object
     */
    void store(String storageKey, InputStream inputStream, long contentLength, String contentType);

    /**
     * Retrieves the binary input stream for a stored object.
     *
     * @param storageKey the storage key
     * @return InputStream to read the object's contents (caller is responsible for closing)
     * @throws InvalidStorageKeyException if storageKey is invalid or contains traversal sequences
     * @throws StorageObjectNotFoundException if no object exists for the given storageKey
     * @throws StorageException if an I/O error occurs while accessing the object
     */
    InputStream load(String storageKey);

    /**
     * Checks whether an object exists at the specified storage key.
     *
     * @param storageKey the storage key
     * @return true if the object exists and is a regular file; false otherwise
     * @throws InvalidStorageKeyException if storageKey is invalid or contains traversal sequences
     */
    boolean exists(String storageKey);

    /**
     * Retrieves metadata for the stored object.
     *
     * @param storageKey the storage key
     * @return StorageObjectMetadata describing the stored object
     * @throws InvalidStorageKeyException if storageKey is invalid or contains traversal sequences
     * @throws StorageObjectNotFoundException if no object exists for the given storageKey
     * @throws StorageException if an I/O error occurs while reading metadata
     */
    StorageObjectMetadata getMetadata(String storageKey);

    /**
     * Deletes the object at the specified storage key.
     * The operation is idempotent: attempting to delete a non-existent object succeeds silently.
     *
     * @param storageKey the storage key
     * @throws InvalidStorageKeyException if storageKey is invalid or contains traversal sequences
     * @throws StorageException if an I/O error occurs during deletion
     */
    void delete(String storageKey);

    /**
     * Generates an upload URL or target for direct upload.
     *
     * @param storageKey the target storage key
     * @param ttl duration for which the upload intent is valid (maximum 15 minutes)
     * @return the upload URL
     * @throws InvalidStorageKeyException if storageKey is invalid or contains traversal sequences
     */
    String generateUploadUrl(String storageKey, Duration ttl);

    /**
     * Generates a short-lived download URL for client consumption (TTL &le; 15 minutes).
     *
     * @param storageKey the storage key
     * @param ttl duration for which the URL remains valid (maximum 15 minutes)
     * @return the download URL
     * @throws InvalidStorageKeyException if storageKey is invalid or contains traversal sequences
     */
    String generateDownloadUrl(String storageKey, Duration ttl);

    /**
     * Returns the active storage mode.
     *
     * @return StorageMode.LOCAL_STREAM or StorageMode.S3_PRESIGNED
     */
    StorageMode getStorageMode();
}
