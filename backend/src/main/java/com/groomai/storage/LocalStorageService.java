package com.groomai.storage;

import com.groomai.storage.config.StorageProperties;
import com.groomai.storage.exception.StorageException;
import com.groomai.storage.exception.StorageObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
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
        Path targetPath = StorageKeyValidator.resolveAndVerify(baseDir, storageKey);

        Path parentDir = targetPath.getParent();
        try {
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // Write to a temporary file first, then atomically move to target
            Path tempFile = Files.createTempFile(parentDir, "upload_", ".tmp");
            try {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

                try {
                    Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                log.debug("Successfully stored object at key: [{}] (bytes: {})", storageKey, contentLength);
            } finally {
                // Ensure temp file is cleaned up if move failed
                Files.deleteIfExists(tempFile);
            }
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

            return new StorageObjectMetadata(storageKey, size, contentType, lastModified);
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
            if (deleted) {
                log.debug("Deleted storage object at key: [{}]", storageKey);
            }
        } catch (IOException e) {
            log.error("Failed to delete storage object [{}]: {}", storageKey, e.getMessage(), e);
            throw new StorageException("Failed to delete storage object at key: " + storageKey, e);
        }
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
