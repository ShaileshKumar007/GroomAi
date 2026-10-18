package com.groomai.storage;

/**
 * Storage mode indicating how client uploads and downloads are handled.
 * Matches uploadMode in docs/API_SPEC.md.
 */
public enum StorageMode {
    /**
     * Local filesystem storage for development, offline testing, and local network devices.
     */
    LOCAL_STREAM,

    /**
     * S3-compatible cloud object storage using pre-signed direct upload and download URLs.
     */
    S3_PRESIGNED
}
