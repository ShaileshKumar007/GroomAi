package com.groomai.storage;

import com.groomai.storage.entity.StorageDeletionTask;
import com.groomai.storage.repository.StorageDeletionTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StorageDeletionOutboxServiceImpl implements StorageDeletionOutboxService {

    private static final Logger log = LoggerFactory.getLogger(StorageDeletionOutboxServiceImpl.class);

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    // Bounded retry schedule from AGENTS.md: 15 min, 1 hour, 6 hours, 16h 45min (24h total)
    public static final Duration[] RETRY_DELAYS = new Duration[] {
        Duration.ofMinutes(15),
        Duration.ofHours(1),
        Duration.ofHours(6),
        Duration.ofMinutes(1005) // 16 hours 45 minutes
    };

    /**
     * Immutable receipt representing an atomically claimed deletion task.
     * Serves as a fencing identity (id, attempts, leaseExpiresAt) to prevent stale worker writes.
     */
    public record ClaimReceipt(
        UUID id,
        String storageKey,
        int attempts,
        Instant leaseExpiresAt
    ) {}

    private final StorageDeletionTaskRepository outboxRepository;
    private final ObjectStorageService objectStorageService;
    private final TransactionTemplate transactionTemplate;

    public StorageDeletionOutboxServiceImpl(
        StorageDeletionTaskRepository outboxRepository,
        ObjectStorageService objectStorageService,
        PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.objectStorageService = objectStorageService;
        if (transactionManager != null) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            this.transactionTemplate = null;
        }
    }

    // Overloaded constructor for unit tests without full Spring transaction manager
    public StorageDeletionOutboxServiceImpl(
        StorageDeletionTaskRepository outboxRepository,
        ObjectStorageService objectStorageService
    ) {
        this(outboxRepository, objectStorageService, null);
    }

    @Override
    public StorageDeletionTask enqueueDeletion(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank for deletion outbox.");
        }

        // Idempotency: return active existing task if already enqueued
        return outboxRepository.findFirstByStorageKeyAndStatusNot(storageKey, "FAILED")
            .orElseGet(() -> {
                StorageDeletionTask task = StorageDeletionTask.builder()
                    .storageKey(storageKey)
                    .status("PENDING")
                    .attempts(0)
                    .nextAttemptAt(Instant.now())
                    .build();
                StorageDeletionTask saved = outboxRepository.save(task);
                log.info("Enqueued storage key [{}] to storage_deletion_outbox with ID [{}]", storageKey, saved.getId());
                return saved;
            });
    }

    @Override
    public void dispatchImmediate(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        boolean hasActiveTx = TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();

        if (hasActiveTx) {
            log.debug("Active controlling transaction detected; registering post-commit eradication for [{}]", storageKey);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        executeImmediateDispatch(storageKey);
                    } catch (Exception e) {
                        log.warn("Post-commit immediate eradication error for storage key [{}]: {}", storageKey, e.getMessage());
                    }
                }
            });
            return;
        }

        // If there is no active transaction controlling the reference, do not delete immediately.
        // Leave the durable outbox task for the scheduled worker.
        log.info("No active transaction detected for storage key [{}]; physical deletion deferred to scheduled outbox sweeper",
            storageKey);
    }

    public void executeImmediateDispatch(String storageKey) {
        Instant now = Instant.now();
        Instant leaseExpiresAt = now.plus(LEASE_DURATION);

        // Phase 1: Claim task in a short, dedicated database transaction
        ClaimReceipt receipt = inTransaction(() -> {
            int claimed = outboxRepository.claimTaskByStorageKey(storageKey, now, leaseExpiresAt);
            if (claimed == 0) {
                log.debug("Storage deletion task for key [{}] was already claimed or does not exist", storageKey);
                return null;
            }
            StorageDeletionTask task = outboxRepository.findFirstByStorageKey(storageKey).orElse(null);
            if (task == null) {
                return null;
            }
            return new ClaimReceipt(task.getId(), task.getStorageKey(), task.getAttempts(), leaseExpiresAt);
        });

        if (receipt == null) {
            return;
        }

        // Phase 2 & 3: Perform storage I/O outside DB transaction, then record result in separate transaction
        processClaimedTask(receipt, now);
    }

    @Override
    @Scheduled(cron = "${groomai.storage.outbox-sweeper.cron:0 */1 * * * *}") // Every 1 minute
    public int processDueTasks() {
        Instant now = Instant.now();
        List<StorageDeletionTask> dueTasks = outboxRepository.findDueTasks(now, PageRequest.of(0, 50));

        if (dueTasks.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (StorageDeletionTask task : dueTasks) {
            boolean success = claimAndExecuteTask(task, now);
            if (success) {
                successCount++;
            }
        }
        return successCount;
    }

    public boolean claimAndExecuteTask(StorageDeletionTask task, Instant now) {
        Instant leaseExpiresAt = now.plus(LEASE_DURATION);

        // Phase 1: Claim task in a short, dedicated database transaction
        ClaimReceipt receipt = inTransaction(() -> {
            int claimed = outboxRepository.claimTaskById(task.getId(), now, leaseExpiresAt);
            if (claimed == 0) {
                log.debug("Task [{}] for storage key [{}] already claimed by concurrent worker",
                    task.getId(), task.getStorageKey());
                return null;
            }
            StorageDeletionTask currentTask = outboxRepository.findById(task.getId()).orElse(task);
            return new ClaimReceipt(currentTask.getId(), currentTask.getStorageKey(), currentTask.getAttempts(), leaseExpiresAt);
        });

        if (receipt == null) {
            return false;
        }

        // Phase 2 & 3: Perform storage I/O outside DB transaction, then record result in separate transaction
        return processClaimedTask(receipt, now);
    }

    private boolean processClaimedTask(ClaimReceipt receipt, Instant now) {
        // Phase 2: Perform physical storage deletion OUTSIDE of any database transaction
        Exception storageException = null;
        try {
            log.info("Performing physical eradication for storage key [{}] (attempt {})",
                receipt.storageKey(), receipt.attempts());
            objectStorageService.delete(receipt.storageKey());
        } catch (Exception e) {
            storageException = e;
        }

        // Phase 3: Record completion or failure in a separate database transaction using claim fencing
        if (storageException == null) {
            return handleExecutionSuccess(receipt);
        } else {
            handleExecutionFailure(receipt, storageException, now);
            return false;
        }
    }

    private boolean handleExecutionSuccess(ClaimReceipt receipt) {
        return Boolean.TRUE.equals(inTransaction(() -> {
            int deleted = outboxRepository.completeTask(receipt.id(), receipt.attempts(), receipt.leaseExpiresAt());
            if (deleted > 0) {
                log.info("Successfully confirmed eradication and purged outbox task [{}] for key [{}]",
                    receipt.id(), receipt.storageKey());
                return true;
            } else {
                log.warn("Task [{}] for storage key [{}] was modified by another claim before completion could be recorded; lease expired",
                    receipt.id(), receipt.storageKey());
                return false;
            }
        }));
    }

    private void handleExecutionFailure(ClaimReceipt receipt, Exception e, Instant now) {
        inTransaction(() -> {
            log.warn("Failed deletion attempt {}/{} for storage key [{}]: {}",
                receipt.attempts(), MAX_ATTEMPTS, receipt.storageKey(), e.getMessage());

            if (receipt.attempts() >= MAX_ATTEMPTS) {
                int updated = outboxRepository.recordTaskFailure(
                    receipt.id(),
                    receipt.attempts(),
                    receipt.leaseExpiresAt(),
                    "FAILED",
                    now,
                    e.getMessage()
                );
                if (updated > 0) {
                    log.error("STORAGE_DELETION_DEAD_LETTER: Storage key [{}] failed all {} deletion attempts. Task ID: [{}]",
                        receipt.storageKey(), MAX_ATTEMPTS, receipt.id());
                }
            } else {
                int delayIndex = Math.min(receipt.attempts() - 1, RETRY_DELAYS.length - 1);
                Duration delay = RETRY_DELAYS[delayIndex];
                Instant nextAttemptAt = now.plus(delay);
                int updated = outboxRepository.recordTaskFailure(
                    receipt.id(),
                    receipt.attempts(),
                    receipt.leaseExpiresAt(),
                    "PENDING",
                    nextAttemptAt,
                    e.getMessage()
                );
                if (updated > 0) {
                    log.info("Scheduled retry for storage key [{}] in {} minutes (attempt {})",
                        receipt.storageKey(), delay.toMinutes(), receipt.attempts() + 1);
                }
            }
            return null;
        });
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        } else {
            return action.get();
        }
    }
}
