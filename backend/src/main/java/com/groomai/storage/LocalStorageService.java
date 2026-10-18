package com.groomai.storage;

import com.groomai.storage.config.StorageProperties;
import com.groomai.storage.exception.StorageException;
import com.groomai.storage.exception.StorageObjectAlreadyExistsException;
import com.groomai.storage.exception.StorageObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

/**
 * Local filesystem implementation of ObjectStorageService.
 * Used for local development, offline workflows, and physical device testing over LAN.
 * Persists files in a private root directory outside web root.
 */
public class LocalStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path baseDir;
    private final String baseUrl;

    public LocalStorageService(StorageProperties properties) {
        this(
            Paths.get(properties.getLocal().getBaseDir()),
            properties.getLocal().getBaseUrl()
        );
    }

    public LocalStorageService(Path baseDir, String baseUrl) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        try {
            Files.createDirectories(this.baseDir);
            log.info("Initialized LocalStorageService at base directory: [{}]", this.baseDir);
        } catch (IOException e) {
            throw new StorageException("Failed to initialize storage directory at: " + this.baseDir, e);
        }
    }

    @Override
    public void store(String storageKey, InputStream inputStream, long contentLength, String contentType) {
        store(storageKey, inputStream, contentLength, contentType, null);
    }

    @Override
    public void store(String storageKey, InputStream inputStream, long contentLength, String contentType, String uploadTicket) {
        Path targetPath = StorageKeyValidator.resolveAndVerify(baseDir, storageKey);

        if (Files.exists(targetPath)) {
            log.warn("Storage object already exists at key [{}]: cannot overwrite", storageKey);
            throw new StorageObjectAlreadyExistsException(
                "A storage object already exists at storage key: " + storageKey
            );
        }

        Path parentDir = targetPath.getParent();
        try {
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // Write to a temporary file first, then atomically publish via hard link without overwrite
            Path tempFile = Files.createTempFile(parentDir, "upload_", ".tmp");
            boolean targetPublished = false;
            try {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

                // Publish atomically using hard link: creates targetPath referencing the completed inode.
                // Fails atomically with FileAlreadyExistsException if targetPath already exists (atomic no-overwrite).
                try {
                    publishFile(targetPath, tempFile);
                    targetPublished = true;
                } catch (UnsupportedOperationException e) {
                    log.error("Filesystem does not support atomic link-based publication at: {}", targetPath, e);
                    throw new StorageException("Filesystem does not support atomic link-based publication at: " + targetPath, e);
                }

                if (uploadTicket != null && !uploadTicket.isBlank()) {
                    Path metaPath = getMetaPath(targetPath);
                    Files.writeString(metaPath, uploadTicket, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }

                log.debug("Successfully stored object at key: [{}] (bytes: {})", storageKey, contentLength);
            } catch (Exception e) {
                if (targetPublished) {
                    // Only roll back destination if THIS operation published it (e.g., meta write failed)
                    Files.deleteIfExists(getMetaPath(targetPath));
                    Files.deleteIfExists(targetPath);
                }
                throw e;
            } finally {
                // Ensure temp file is cleaned up on success and all failure paths
                Files.deleteIfExists(tempFile);
            }
        } catch (FileAlreadyExistsException e) {
            log.warn("Storage object already exists at key [{}]: cannot overwrite", storageKey);
            throw new StorageObjectAlreadyExistsException(
                "A storage object already exists at storage key: " + storageKey
            );
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to store object at key [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to store object at storage key: " + storageKey, e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        Path targetPath = StorageKeyValidator.resolveAndVerify(baseDir, storageKey);

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new StorageObjectNotFoundException("Storage object not found for key: " + storageKey);
        }

        try {
            return Files.newInputStream(targetPath, StandardOpenOption.READ);
        } catch (IOException e) {
            log.error("Failed to read storage object [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to read storage object at key: " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path targetPath = StorageKeyValidator.resolveAndVerify(baseDir, storageKey);
        return Files.exists(targetPath) && Files.isRegularFile(targetPath);
    }

    @Override
    public StorageObjectMetadata getMetadata(String storageKey) {
        Path targetPath = StorageKeyValidator.resolveAndVerify(baseDir, storageKey);

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new StorageObjectNotFoundException("Storage object not found for key: " + storageKey);
        }

        try {
            long size = Files.size(targetPath);
            Instant lastModified = Files.getLastModifiedTime(targetPath).toInstant();
            String probedType = Files.probeContentType(targetPath);
            String contentType = (probedType != null) ? probedType : "image/jpeg";

            String uploadTicket = null;
            Path metaPath = getMetaPath(targetPath);
            if (Files.exists(metaPath) && Files.isRegularFile(metaPath)) {
                uploadTicket = Files.readString(metaPath, StandardCharsets.UTF_8).trim();
            }

            return new StorageObjectMetadata(storageKey, size, contentType, lastModified, uploadTicket);
        } catch (IOException e) {
            log.error("Failed to read metadata for storage key [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to read metadata for storage key: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path targetPath = StorageKeyValidator.resolveAndVerify(baseDir, storageKey);
        try {
            boolean deleted = Files.deleteIfExists(targetPath);
            Path metaPath = getMetaPath(targetPath);
            Files.deleteIfExists(metaPath);
            if (deleted) {
                log.debug("Deleted storage object at key: [{}]", storageKey);
            }
        } catch (IOException e) {
            log.error("Failed to delete storage object [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to delete storage object at key: " + storageKey, e);
        }
    }

    /**
     * Atomically links the targetPath to the completed tempFile inode.
     * Package-private to allow testing unsupported filesystem exceptions.
     */
    void publishFile(Path targetPath, Path tempFile) throws IOException {
        Files.createLink(targetPath, tempFile);
    }

    private Path getMetaPath(Path targetPath) {
        return targetPath.resolveSibling(targetPath.getFileName().toString() + ".meta");
    }

    @Override
    public String generateUploadUrl(String storageKey, Duration ttl) {
        StorageKeyValidator.validateKey(storageKey);
        return baseUrl + "/api/v1/storage/upload/" + storageKey;
    }

    @Override
    public String generateDownloadUrl(String storageKey, Duration ttl) {
        StorageKeyValidator.validateKey(storageKey);
        return baseUrl + "/api/v1/storage/files/" + storageKey;
    }

    @Override
    public StorageMode getStorageMode() {
        return StorageMode.LOCAL_STREAM;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
