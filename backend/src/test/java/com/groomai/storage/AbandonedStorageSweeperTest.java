package com.groomai.storage;

import com.groomai.storage.config.StorageProperties;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbandonedStorageSweeperTest {

    @TempDir
    Path tempStorageDir;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private WardrobeItemRepository wardrobeItemRepository;

    private StorageProperties storageProperties;
    private AbandonedStorageSweeper sweeper;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.getLocal().setBaseDir(tempStorageDir.toString());

        sweeper = new AbandonedStorageSweeper(
            storageProperties,
            objectStorageService,
            wardrobeItemRepository
        );
    }

    @Test
    @DisplayName("Should delete unreferenced wardrobe files older than 24 hours")
    void sweepAbandonedUploads_olderThan24hAndUnreferenced_shouldDelete() throws IOException {
        when(objectStorageService.getStorageMode()).thenReturn(StorageMode.LOCAL_STREAM);

        UUID user1 = UUID.randomUUID();
        Path userDir = tempStorageDir.resolve("wardrobe").resolve(user1.toString());
        Files.createDirectories(userDir);

        Path abandonedFile = userDir.resolve("abandoned.jpg");
        Files.write(abandonedFile, new byte[]{0x01, 0x02});

        // Set modification time to 25 hours ago
        Instant oldTime = Instant.now().minus(Duration.ofHours(25));
        Files.setLastModifiedTime(abandonedFile, FileTime.from(oldTime));

        String expectedKey = "wardrobe/" + user1 + "/abandoned.jpg";
        when(wardrobeItemRepository.existsByStorageKey(expectedKey)).thenReturn(false);

        int deletedCount = sweeper.sweepAbandonedUploads(Duration.ofHours(24));

        assertThat(deletedCount).isEqualTo(1);
        verify(objectStorageService).delete(eq(expectedKey));
    }

    @Test
    @DisplayName("Should preserve wardrobe files older than 24 hours that are referenced in wardrobe_items")
    void sweepAbandonedUploads_olderThan24hButReferenced_shouldPreserve() throws IOException {
        when(objectStorageService.getStorageMode()).thenReturn(StorageMode.LOCAL_STREAM);

        UUID user1 = UUID.randomUUID();
        Path userDir = tempStorageDir.resolve("wardrobe").resolve(user1.toString());
        Files.createDirectories(userDir);

        Path catalogedFile = userDir.resolve("cataloged.jpg");
        Files.write(catalogedFile, new byte[]{0x01, 0x02});

        Instant oldTime = Instant.now().minus(Duration.ofHours(25));
        Files.setLastModifiedTime(catalogedFile, FileTime.from(oldTime));

        String expectedKey = "wardrobe/" + user1 + "/cataloged.jpg";
        when(wardrobeItemRepository.existsByStorageKey(expectedKey)).thenReturn(true);

        int deletedCount = sweeper.sweepAbandonedUploads(Duration.ofHours(24));

        assertThat(deletedCount).isEqualTo(0);
        verify(objectStorageService, never()).delete(expectedKey);
    }

    @Test
    @DisplayName("Should preserve unreferenced wardrobe files that are newer than 24 hours (within grace period)")
    void sweepAbandonedUploads_newerThan24h_shouldPreserve() throws IOException {
        when(objectStorageService.getStorageMode()).thenReturn(StorageMode.LOCAL_STREAM);

        UUID user1 = UUID.randomUUID();
        Path userDir = tempStorageDir.resolve("wardrobe").resolve(user1.toString());
        Files.createDirectories(userDir);

        Path freshFile = userDir.resolve("fresh.jpg");
        Files.write(freshFile, new byte[]{0x01, 0x02});

        // Set modification time to 1 hour ago (within 24h grace period)
        Instant freshTime = Instant.now().minus(Duration.ofHours(1));
        Files.setLastModifiedTime(freshFile, FileTime.from(freshTime));

        int deletedCount = sweeper.sweepAbandonedUploads(Duration.ofHours(24));

        assertThat(deletedCount).isEqualTo(0);
        verify(objectStorageService, never()).delete(any());
    }

    @Test
    @DisplayName("Should skip sweeping when abandonedSweeper is disabled")
    void scheduledSweep_whenDisabled_shouldSkip() {
        storageProperties.getAbandonedSweeper().setEnabled(false);

        sweeper.scheduledSweep();

        verify(objectStorageService, never()).getStorageMode();
    }

    @Test
    @DisplayName("Should handle missing wardrobe directory gracefully and return 0")
    void sweepAbandonedUploads_missingWardrobeDir_shouldReturnZero() {
        when(objectStorageService.getStorageMode()).thenReturn(StorageMode.LOCAL_STREAM);

        // tempStorageDir has no wardrobe subdirectory
        int deletedCount = sweeper.sweepAbandonedUploads(Duration.ofHours(24));

        assertThat(deletedCount).isEqualTo(0);
        verify(objectStorageService, never()).delete(any());
    }

    @Test
    @DisplayName("Should continue sweeping remaining files when one file deletion throws StorageException")
    void sweepAbandonedUploads_individualFailure_continuesProcessingRemaining() throws IOException {
        when(objectStorageService.getStorageMode()).thenReturn(StorageMode.LOCAL_STREAM);

        UUID user1 = UUID.randomUUID();
        Path userDir = tempStorageDir.resolve("wardrobe").resolve(user1.toString());
        Files.createDirectories(userDir);

        Path failingFile = userDir.resolve("failing.jpg");
        Path succeedingFile = userDir.resolve("succeeding.jpg");
        Files.write(failingFile, new byte[]{0x01});
        Files.write(succeedingFile, new byte[]{0x02});

        Instant oldTime = Instant.now().minus(Duration.ofHours(25));
        Files.setLastModifiedTime(failingFile, FileTime.from(oldTime));
        Files.setLastModifiedTime(succeedingFile, FileTime.from(oldTime));

        String failingKey = "wardrobe/" + user1 + "/failing.jpg";
        String succeedingKey = "wardrobe/" + user1 + "/succeeding.jpg";

        when(wardrobeItemRepository.existsByStorageKey(failingKey)).thenReturn(false);
        when(wardrobeItemRepository.existsByStorageKey(succeedingKey)).thenReturn(false);

        org.mockito.Mockito.doThrow(new com.groomai.storage.exception.StorageException("Simulated I/O error"))
            .when(objectStorageService).delete(failingKey);

        // Deleting failingKey fails, but succeedingKey succeeds
        int deletedCount = sweeper.sweepAbandonedUploads(Duration.ofHours(24));

        assertThat(deletedCount).isEqualTo(1);
        verify(objectStorageService).delete(eq(failingKey));
        verify(objectStorageService).delete(eq(succeedingKey));
    }
}
