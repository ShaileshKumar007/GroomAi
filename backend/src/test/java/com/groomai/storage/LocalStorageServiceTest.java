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
    @DisplayName("Should reject storing to an existing object key with StorageObjectAlreadyExistsException (create-only)")
    void testRejectExistingObject_createOnly() throws IOException {
        String key = "wardrobe/user-1/jacket.jpg";
        byte[] firstContent = "first-content".getBytes(StandardCharsets.UTF_8);
        byte[] secondContent = "second-updated-content".getBytes(StandardCharsets.UTF_8);

        storageService.store(key, new ByteArrayInputStream(firstContent), firstContent.length, "image/jpeg");

        assertThatThrownBy(() -> storageService.store(key, new ByteArrayInputStream(secondContent), secondContent.length, "image/jpeg"))
            .isInstanceOf(com.groomai.storage.exception.StorageObjectAlreadyExistsException.class)
            .hasMessageContaining("already exists");

        // Verify original content was not overwritten or corrupted
        try (InputStream stream = storageService.load(key)) {
            byte[] readBytes = stream.readAllBytes();
            assertThat(readBytes).isEqualTo(firstContent);
        }
    }

    @Test
    @DisplayName("Should safely handle concurrent upload attempts to the same key: exactly one succeeds")
    void testConcurrentUploadAttempts() throws InterruptedException {
        String key = "wardrobe/user-1/race-item.jpg";
        int threadCount = 8;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    byte[] payload = ("thread-content-" + index).getBytes(StandardCharsets.UTF_8);
                    storageService.store(key, new ByteArrayInputStream(payload), payload.length, "image/jpeg");
                    successCount.incrementAndGet();
                } catch (com.groomai.storage.exception.StorageObjectAlreadyExistsException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(storageService.exists(key)).isTrue();
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

    @Test
    @DisplayName("Should publish atomically via hard link and clean up temporary file on success")
    void testAtomicPublicationCleansUpTempFile() throws IOException {
        String key = "wardrobe/user-1/clean.jpg";
        byte[] payload = "atomic-payload".getBytes(StandardCharsets.UTF_8);

        storageService.store(key, new ByteArrayInputStream(payload), payload.length, "image/jpeg", "test-ticket");

        assertThat(storageService.exists(key)).isTrue();

        try (InputStream in = storageService.load(key)) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }

        Path parentDir = tempDir.resolve("wardrobe/user-1");
        try (var stream = java.nio.file.Files.list(parentDir)) {
            var files = stream.map(p -> p.getFileName().toString()).toList();
            assertThat(files).containsExactlyInAnyOrder("clean.jpg", "clean.jpg.meta");
            assertThat(files).noneMatch(name -> name.endsWith(".tmp"));
        }
    }

    @Test
    @DisplayName("Should clean up temp file and leave no destination file if input stream fails during store")
    void testStreamFailureCleansUpTempFileAndLeavesNoDestination() throws IOException {
        String key = "wardrobe/user-1/partial.jpg";
        InputStream failingStream = new InputStream() {
            private int count = 0;
            @Override
            public int read() throws IOException {
                if (++count > 10) {
                    throw new IOException("Simulated network stream disconnect");
                }
                return 42;
            }
        };

        assertThatThrownBy(() -> storageService.store(key, failingStream, 100, "image/jpeg"))
            .isInstanceOf(com.groomai.storage.exception.StorageException.class)
            .hasMessageContaining("Failed to store object");

        assertThat(storageService.exists(key)).isFalse();

        Path parentDir = tempDir.resolve("wardrobe/user-1");
        if (java.nio.file.Files.exists(parentDir)) {
            try (var stream = java.nio.file.Files.list(parentDir)) {
                var files = stream.map(p -> p.getFileName().toString()).toList();
                assertThat(files).noneMatch(name -> name.endsWith(".tmp"));
                assertThat(files).doesNotContain("partial.jpg");
            }
        }
    }

    @Test
    @DisplayName("Rollback on conflicting upload must never delete an existing target from another operation")
    void testConflictingUploadDoesNotDeleteExistingTarget() throws IOException {
        String key = "wardrobe/user-1/target-safe.jpg";
        byte[] originalContent = "original-verified-bytes".getBytes(StandardCharsets.UTF_8);
        storageService.store(key, new ByteArrayInputStream(originalContent), originalContent.length, "image/jpeg");

        assertThat(storageService.exists(key)).isTrue();

        byte[] newContent = "new-competing-bytes".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storageService.store(key, new ByteArrayInputStream(newContent), newContent.length, "image/jpeg"))
            .isInstanceOf(com.groomai.storage.exception.StorageObjectAlreadyExistsException.class);

        // Existing target must remain intact with original content
        assertThat(storageService.exists(key)).isTrue();
        try (InputStream in = storageService.load(key)) {
            assertThat(in.readAllBytes()).isEqualTo(originalContent);
        }
    }

    @Test
    @DisplayName("Should fail safely and clean up temp file if filesystem does not support hard links")
    void testUnsupportedFilesystemFailsSafelyWithoutPublishingOrLeavingTempFile() throws IOException {
        LocalStorageService unsupportedService = new LocalStorageService(tempDir, "http://localhost:8080") {
            @Override
            void publishFile(Path targetPath, Path tempFile) throws IOException {
                throw new UnsupportedOperationException("Hard links unsupported on this volume");
            }
        };

        String key = "wardrobe/user-1/unsupported.jpg";
        byte[] payload = "unsupported-fs-payload".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> unsupportedService.store(key, new ByteArrayInputStream(payload), payload.length, "image/jpeg"))
            .isInstanceOf(com.groomai.storage.exception.StorageException.class)
            .hasMessageContaining("Filesystem does not support atomic link-based publication");

        // Destination must not be published
        assertThat(unsupportedService.exists(key)).isFalse();

        // Temp file must be cleaned up
        Path parentDir = tempDir.resolve("wardrobe/user-1");
        if (java.nio.file.Files.exists(parentDir)) {
            try (var stream = java.nio.file.Files.list(parentDir)) {
                var files = stream.map(p -> p.getFileName().toString()).toList();
                assertThat(files).noneMatch(name -> name.endsWith(".tmp"));
                assertThat(files).doesNotContain("unsupported.jpg");
            }
        }
    }
}
