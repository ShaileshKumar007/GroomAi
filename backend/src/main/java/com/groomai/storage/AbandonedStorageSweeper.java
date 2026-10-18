package com.groomai.storage;

import com.groomai.storage.config.StorageProperties;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Scheduled sweeper that removes unreferenced, abandoned uploaded wardrobe files
 * after a 24-hour grace period, preventing disk leaks from unconfirmed upload intents.
 */
@Component
public class AbandonedStorageSweeper {

    private static final Logger log = LoggerFactory.getLogger(AbandonedStorageSweeper.class);

    private final StorageProperties storageProperties;
    private final ObjectStorageService objectStorageService;
    private final WardrobeItemRepository wardrobeItemRepository;

    public AbandonedStorageSweeper(
        StorageProperties storageProperties,
        ObjectStorageService objectStorageService,
        WardrobeItemRepository wardrobeItemRepository
    ) {
        this.storageProperties = storageProperties;
        this.objectStorageService = objectStorageService;
        this.wardrobeItemRepository = wardrobeItemRepository;
    }

    /**
     * Runs hourly by default to sweep abandoned uploads older than the configured grace period (24 hours).
     */
    @Scheduled(cron = "${groomai.storage.abandoned-sweeper.cron:0 0 * * * *}")
    public void scheduledSweep() {
        if (!storageProperties.getAbandonedSweeper().isEnabled()) {
            log.debug("Abandoned storage sweeper is disabled; skipping scheduled run.");
            return;
        }

        Duration gracePeriod = Duration.ofHours(storageProperties.getAbandonedSweeper().getGracePeriodHours());
        sweepAbandonedUploads(gracePeriod);
    }

    /**
     * Scans storage files and deletes any wardrobe file unreferenced in wardrobe_items
     * whose last-modified timestamp is older than the specified grace period.
     *
     * @param gracePeriod minimum age threshold for considering an upload abandoned
     * @return count of abandoned files deleted
     */
    public int sweepAbandonedUploads(Duration gracePeriod) {
        if (objectStorageService.getStorageMode() != StorageMode.LOCAL_STREAM) {
            log.debug("AbandonedStorageSweeper currently operates on local storage mode; skipping.");
            return 0;
        }

        Path baseDir = Paths.get(storageProperties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        Path wardrobeDir = baseDir.resolve("wardrobe").normalize();

        if (!Files.exists(wardrobeDir) || !Files.isDirectory(wardrobeDir)) {
            log.debug("Wardrobe storage directory does not exist at [{}]; nothing to sweep.", wardrobeDir);
            return 0;
        }

        Instant cutoffTime = Instant.now().minus(gracePeriod);
        AtomicInteger deletedCount = new AtomicInteger(0);

        try (Stream<Path> stream = Files.walk(wardrobeDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(file -> !file.getFileName().toString().endsWith(".meta"))
                .forEach(file -> {
                    try {
                        Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                        if (lastModified.isBefore(cutoffTime)) {
                            Path relativePath = baseDir.relativize(file);
                            String storageKey = relativePath.toString().replace('\\', '/');

                            boolean cataloged = wardrobeItemRepository.existsByStorageKey(storageKey);
                            if (!cataloged) {
                                objectStorageService.delete(storageKey);
                                deletedCount.incrementAndGet();
                                log.info("Swept abandoned uncataloged storage object: [{}] (last modified: {})", storageKey, lastModified);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error evaluating storage file for abandoned sweep: [{}]: {}", file, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.error("Failed to traverse wardrobe storage directory for sweep: {}", e.getMessage(), e);
        }

        log.info("Abandoned storage sweep completed. Deleted {} abandoned files.", deletedCount.get());
        return deletedCount.get();
    }
}
