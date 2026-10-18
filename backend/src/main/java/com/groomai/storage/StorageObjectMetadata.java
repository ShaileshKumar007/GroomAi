package com.groomai.storage;

import java.time.Instant;

/**
 * Immutable metadata record for an object stored in object storage.
 *
 * @param storageKey the sanitized storage key identifier
 * @param contentLength size of the object in bytes
 * @param contentType MIME type of the object (e.g., "image/jpeg")
 * @param lastModified timestamp when the object was last created or updated
 * @param uploadTicket optional signed upload intent ticket associated with the stored object
 */
public record StorageObjectMetadata(
    String storageKey,
    long contentLength,
    String contentType,
    Instant lastModified,
    String uploadTicket
) {
    public StorageObjectMetadata(String storageKey, long contentLength, String contentType, Instant lastModified) {
        this(storageKey, contentLength, contentType, lastModified, null);
    }
}
