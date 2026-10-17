package com.groomai.storage;

import com.groomai.storage.exception.InvalidStorageKeyException;
import com.groomai.storage.exception.StorageObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tempDir, "http://localhost:8080");
    }

    @Test
    @DisplayName("Should store, verify existence, load, and delete an object successfully")
    void testEndToEndLifecycle() throws IOException {
        String key = "wardrobe/user-1/shirt.jpg";
        byte[] payload = "mock-jpeg-bytes-content".getBytes(StandardCharsets.UTF_8);

        assertThat(storageService.exists(key)).isFalse();

        // 1. Store
        storageService.store(key, new ByteArrayInputStream(payload), payload.length, "image/jpeg");

        // 2. Exists
        assertThat(storageService.exists(key)).isTrue();

        // 3. Load
        try (InputStream stream = storageService.load(key)) {
            byte[] readBytes = stream.readAllBytes();
            assertThat(readBytes).isEqualTo(payload);
        }

        // 4. Metadata
        StorageObjectMetadata metadata = storageService.getMetadata(key);
        assertThat(metadata.storageKey()).isEqualTo(key);
        assertThat(metadata.contentLength()).isEqualTo(payload.length);
        assertThat(metadata.contentType()).isNotNull();
        assertThat(metadata.lastModified()).isNotNull();

        // 5. Delete
        storageService.delete(key);
        assertThat(storageService.exists(key)).isFalse();

        // 6. Idempotent delete on non-existent file
        storageService.delete(key);
    }

    @Test
    @DisplayName("Should overwrite existing object atomically")
    void testOverwriteObject() throws IOException {
        String key = "wardrobe/user-1/jacket.jpg";
        byte[] firstContent = "first-content".getBytes(StandardCharsets.UTF_8);
        byte[] secondContent = "second-updated-content".getBytes(StandardCharsets.UTF_8);

        storageService.store(key, new ByteArrayInputStream(firstContent), firstContent.length, "image/jpeg");
        storageService.store(key, new ByteArrayInputStream(secondContent), secondContent.length, "image/jpeg");

        try (InputStream stream = storageService.load(key)) {
            byte[] readBytes = stream.readAllBytes();
            assertThat(readBytes).isEqualTo(secondContent);
        }
    }

    @Test
    @DisplayName("Should throw StorageObjectNotFoundException when loading missing object")
    void testLoadMissingObject() {
        String missingKey = "wardrobe/user-999/nonexistent.jpg";

        assertThatThrownBy(() -> storageService.load(missingKey))
            .isInstanceOf(StorageObjectNotFoundException.class)
            .hasMessageContaining("Storage object not found");
    }

    @Test
    @DisplayName("Should throw StorageObjectNotFoundException when getting metadata for missing object")
    void testMetadataMissingObject() {
        String missingKey = "wardrobe/user-999/nonexistent.jpg";

        assertThatThrownBy(() -> storageService.getMetadata(missingKey))
            .isInstanceOf(StorageObjectNotFoundException.class)
            .hasMessageContaining("Storage object not found");
    }

    @Test
    @DisplayName("Should reject path traversal attempts across all operations")
    void testPathTraversalProtection() {
        String traversalKey = "../../../etc/passwd.jpg";
        byte[] payload = "danger".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storageService.store(traversalKey, new ByteArrayInputStream(payload), payload.length, "image/jpeg"))
            .isInstanceOf(InvalidStorageKeyException.class);

        assertThatThrownBy(() -> storageService.load(traversalKey))
            .isInstanceOf(InvalidStorageKeyException.class);

        assertThatThrownBy(() -> storageService.exists(traversalKey))
            .isInstanceOf(InvalidStorageKeyException.class);

        assertThatThrownBy(() -> storageService.getMetadata(traversalKey))
            .isInstanceOf(InvalidStorageKeyException.class);

        assertThatThrownBy(() -> storageService.delete(traversalKey))
            .isInstanceOf(InvalidStorageKeyException.class);
    }

    @Test
    @DisplayName("Should generate proper local upload and download URLs")
    void testUrlGeneration() {
        String key = "wardrobe/user-42/item-7.jpg";

        String uploadUrl = storageService.generateUploadUrl(key, Duration.ofMinutes(15));
        assertThat(uploadUrl).isEqualTo("http://localhost:8080/api/v1/storage/upload/wardrobe/user-42/item-7.jpg");

        String downloadUrl = storageService.generateDownloadUrl(key, Duration.ofMinutes(15));
        assertThat(downloadUrl).isEqualTo("http://localhost:8080/api/v1/storage/files/wardrobe/user-42/item-7.jpg");

        assertThat(storageService.getStorageMode()).isEqualTo(StorageMode.LOCAL_STREAM);
    }

    @Test
    @DisplayName("Should reject invalid keys when generating URLs")
    void testInvalidKeyUrlGeneration() {
        assertThatThrownBy(() -> storageService.generateUploadUrl("../etc/passwd.jpg", Duration.ofMinutes(15)))
            .isInstanceOf(InvalidStorageKeyException.class);

        assertThatThrownBy(() -> storageService.generateDownloadUrl("../etc/passwd.jpg", Duration.ofMinutes(15)))
            .isInstanceOf(InvalidStorageKeyException.class);
    }
}
