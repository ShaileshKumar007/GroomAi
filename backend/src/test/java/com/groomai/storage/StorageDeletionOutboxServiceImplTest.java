package com.groomai.storage;

import com.groomai.storage.entity.StorageDeletionTask;
import com.groomai.storage.repository.StorageDeletionTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageDeletionOutboxServiceImplTest {

    @Mock
    private StorageDeletionTaskRepository outboxRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    private StorageDeletionOutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new StorageDeletionOutboxServiceImpl(outboxRepository, objectStorageService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("enqueueDeletion: saves new PENDING task when not already present")
    void enqueueDeletion_createsNewTask() {
        String storageKey = "wardrobe/user-1/shirt.jpg";

        when(outboxRepository.findFirstByStorageKeyAndStatusNot(storageKey, "FAILED")).thenReturn(Optional.empty());
        when(outboxRepository.save(any(StorageDeletionTask.class))).thenAnswer(invocation -> {
            StorageDeletionTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        StorageDeletionTask result = outboxService.enqueueDeletion(storageKey);

        assertThat(result.getStorageKey()).isEqualTo(storageKey);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getAttempts()).isEqualTo(0);
        verify(outboxRepository).save(any(StorageDeletionTask.class));
    }

    @Test
    @DisplayName("enqueueDeletion: returns existing task if already enqueued")
    void enqueueDeletion_idempotent() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        StorageDeletionTask existing = StorageDeletionTask.builder()
            .id(UUID.randomUUID())
            .storageKey(storageKey)
            .status("PENDING")
            .build();

        when(outboxRepository.findFirstByStorageKeyAndStatusNot(storageKey, "FAILED")).thenReturn(Optional.of(existing));

        StorageDeletionTask result = outboxService.enqueueDeletion(storageKey);

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(outboxRepository, never()).save(any(StorageDeletionTask.class));
    }

    @Test
    @DisplayName("dispatchImmediate: without active transaction, does NOT delete immediately; defers to scheduled sweeper")
    void dispatchImmediate_withoutTransaction_defersToSweeper() {
        String storageKey = "wardrobe/user-1/shirt.jpg";

        outboxService.dispatchImmediate(storageKey);

        // Verification: physical deletion and claim are NOT triggered immediately
        verify(objectStorageService, never()).delete(any());
        verify(outboxRepository, never()).claimTaskByStorageKey(any(), any(), any());
    }

    @Test
    @DisplayName("dispatchImmediate: when active controlling transaction exists, registers afterCommit synchronization")
    void dispatchImmediate_withActiveTransaction_defersToAfterCommit() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PROCESSING")
            .attempts(1)
            .build();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // 1. Call dispatchImmediate inside transaction
        outboxService.dispatchImmediate(storageKey);

        // Verify: physical deletion NOT called before commit!
        verify(objectStorageService, never()).delete(storageKey);

        // 2. Simulate transaction commit
        when(outboxRepository.claimTaskByStorageKey(eq(storageKey), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(outboxRepository.findFirstByStorageKey(storageKey)).thenReturn(Optional.of(task));
        when(outboxRepository.completeTask(eq(taskId), eq(1), any(Instant.class))).thenReturn(1);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();

        // Verify: physical deletion and completion called ONLY after commit!
        verify(objectStorageService).delete(storageKey);
        verify(outboxRepository).completeTask(eq(taskId), eq(1), any(Instant.class));
    }

    @Test
    @DisplayName("dispatchImmediate: when active transaction rolls back, physical deletion NEVER happens")
    void dispatchImmediate_withActiveTransaction_rollbackDoesNotDelete() {
        String storageKey = "wardrobe/user-1/shirt.jpg";

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // Call dispatchImmediate inside transaction
        outboxService.dispatchImmediate(storageKey);

        // Simulate rollback (afterCommit is NOT called; synchronization cleared)
        TransactionSynchronizationManager.clearSynchronization();

        // Verify: physical deletion was NEVER called
        verify(objectStorageService, never()).delete(storageKey);
        verify(outboxRepository, never()).claimTaskByStorageKey(any(), any(), any());
    }

    @Test
    @DisplayName("dispatchImmediate: skips execution if task was already claimed by concurrent worker")
    void dispatchImmediate_alreadyClaimed_skips() {
        String storageKey = "wardrobe/user-1/shirt.jpg";

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        outboxService.dispatchImmediate(storageKey);

        // Claim returns 0 (already claimed by another worker or not found)
        when(outboxRepository.claimTaskByStorageKey(eq(storageKey), any(Instant.class), any(Instant.class))).thenReturn(0);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.get(0).afterCommit();

        verify(objectStorageService, never()).delete(any());
        verify(outboxRepository, never()).completeTask(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("dispatchImmediate: storage error preserves task in outbox with recordTaskFailure")
    void dispatchImmediate_storageError_preservesTaskAndSchedulesRetry() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PROCESSING")
            .attempts(1)
            .build();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        outboxService.dispatchImmediate(storageKey);

        when(outboxRepository.claimTaskByStorageKey(eq(storageKey), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(outboxRepository.findFirstByStorageKey(storageKey)).thenReturn(Optional.of(task));
        doThrow(new RuntimeException("Cloud S3 timeout")).when(objectStorageService).delete(storageKey);
        when(outboxRepository.recordTaskFailure(eq(taskId), eq(1), any(Instant.class), eq("PENDING"), any(Instant.class), any(String.class))).thenReturn(1);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.get(0).afterCommit();

        verify(objectStorageService).delete(storageKey);
        verify(outboxRepository, never()).completeTask(any(), any(Integer.class), any());
        verify(outboxRepository).recordTaskFailure(eq(taskId), eq(1), any(Instant.class), eq("PENDING"), any(Instant.class), any(String.class));
    }

    @Test
    @DisplayName("processDueTasks: claims and deletes storage object for due tasks using claim fencing")
    void processDueTasks_success() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PENDING")
            .attempts(0)
            .nextAttemptAt(Instant.now().minusSeconds(60))
            .build();

        StorageDeletionTask claimedTask = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PROCESSING")
            .attempts(1)
            .build();

        when(outboxRepository.findDueTasks(any(Instant.class), any(Pageable.class))).thenReturn(List.of(task));
        when(outboxRepository.claimTaskById(eq(taskId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(outboxRepository.findById(taskId)).thenReturn(Optional.of(claimedTask));
        when(outboxRepository.completeTask(eq(taskId), eq(1), any(Instant.class))).thenReturn(1);

        int processed = outboxService.processDueTasks();

        assertThat(processed).isEqualTo(1);
        verify(objectStorageService).delete(storageKey);
        verify(outboxRepository).completeTask(eq(taskId), eq(1), any(Instant.class));
    }

    @Test
    @DisplayName("processDueTasks: skips task when atomic claim returns 0 (concurrent worker won)")
    void processDueTasks_concurrentClaim_skips() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PENDING")
            .attempts(0)
            .nextAttemptAt(Instant.now().minusSeconds(60))
            .build();

        when(outboxRepository.findDueTasks(any(Instant.class), any(Pageable.class))).thenReturn(List.of(task));
        when(outboxRepository.claimTaskById(eq(taskId), any(Instant.class), any(Instant.class))).thenReturn(0);

        int processed = outboxService.processDueTasks();

        assertThat(processed).isEqualTo(0);
        verify(objectStorageService, never()).delete(any());
        verify(outboxRepository, never()).completeTask(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("processDueTasks: failure schedules retry with backoff")
    void processDueTasks_failure_retriesWithBackoff() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PENDING")
            .attempts(0)
            .nextAttemptAt(Instant.now().minusSeconds(60))
            .build();

        StorageDeletionTask claimedTask = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PROCESSING")
            .attempts(1)
            .build();

        when(outboxRepository.findDueTasks(any(Instant.class), any(Pageable.class))).thenReturn(List.of(task));
        when(outboxRepository.claimTaskById(eq(taskId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(outboxRepository.findById(taskId)).thenReturn(Optional.of(claimedTask));
        doThrow(new RuntimeException("S3 500 error")).when(objectStorageService).delete(storageKey);
        when(outboxRepository.recordTaskFailure(eq(taskId), eq(1), any(Instant.class), eq("PENDING"), any(Instant.class), any(String.class))).thenReturn(1);

        int processed = outboxService.processDueTasks();

        assertThat(processed).isEqualTo(0);
        verify(outboxRepository, never()).completeTask(any(), any(Integer.class), any());
        verify(outboxRepository).recordTaskFailure(eq(taskId), eq(1), any(Instant.class), eq("PENDING"), any(Instant.class), any(String.class));
    }

    @Test
    @DisplayName("processDueTasks: transitions to FAILED after 5 failed attempts")
    void processDueTasks_transitionsToFailedAfterMaxAttempts() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PENDING")
            .attempts(4)
            .nextAttemptAt(Instant.now().minusSeconds(60))
            .build();

        StorageDeletionTask claimedTask = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PROCESSING")
            .attempts(5) // Attempt 5 reached
            .build();

        when(outboxRepository.findDueTasks(any(Instant.class), any(Pageable.class))).thenReturn(List.of(task));
        when(outboxRepository.claimTaskById(eq(taskId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(outboxRepository.findById(taskId)).thenReturn(Optional.of(claimedTask));
        doThrow(new RuntimeException("Permanent permission denied")).when(objectStorageService).delete(storageKey);
        when(outboxRepository.recordTaskFailure(eq(taskId), eq(5), any(Instant.class), eq("FAILED"), any(Instant.class), any(String.class))).thenReturn(1);

        int processed = outboxService.processDueTasks();

        assertThat(processed).isEqualTo(0);
        verify(outboxRepository, never()).completeTask(any(), any(Integer.class), any());
        verify(outboxRepository).recordTaskFailure(eq(taskId), eq(5), any(Instant.class), eq("FAILED"), any(Instant.class), any(String.class));
    }

    @Test
    @DisplayName("staleWorkerFencing: completeTask returns 0 if lease expired and task was reclaimed; stale worker does not purge")
    void staleWorkerFencing_completion_fencedOut() {
        String storageKey = "wardrobe/user-1/shirt.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PENDING")
            .attempts(0)
            .build();

        StorageDeletionTask claimedTask = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PROCESSING")
            .attempts(1)
            .build();

        when(outboxRepository.findDueTasks(any(Instant.class), any(Pageable.class))).thenReturn(List.of(task));
        when(outboxRepository.claimTaskById(eq(taskId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(outboxRepository.findById(taskId)).thenReturn(Optional.of(claimedTask));
        // completeTask returns 0 because another worker reclaimed the task (attempts changed)
        when(outboxRepository.completeTask(eq(taskId), eq(1), any(Instant.class))).thenReturn(0);

        int processed = outboxService.processDueTasks();

        assertThat(processed).isEqualTo(0);
        verify(objectStorageService).delete(storageKey);
        verify(outboxRepository).completeTask(eq(taskId), eq(1), any(Instant.class));
    }

    @Test
    @DisplayName("missingObjectHandling: objectStorageService.delete completes successfully when object is already absent")
    void missingObjectHandling_succeedsAndPurgesTask() {
        String storageKey = "wardrobe/user-1/already-deleted.jpg";
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PENDING")
            .attempts(0)
            .build();

        StorageDeletionTask claimedTask = StorageDeletionTask.builder()
            .id(taskId)
            .storageKey(storageKey)
            .status("PROCESSING")
            .attempts(1)
            .build();

        when(outboxRepository.findDueTasks(any(Instant.class), any(Pageable.class))).thenReturn(List.of(task));
        when(outboxRepository.claimTaskById(eq(taskId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(outboxRepository.findById(taskId)).thenReturn(Optional.of(claimedTask));
        when(outboxRepository.completeTask(eq(taskId), eq(1), any(Instant.class))).thenReturn(1);

        int processed = outboxService.processDueTasks();

        assertThat(processed).isEqualTo(1);
        verify(objectStorageService).delete(storageKey);
        verify(outboxRepository).completeTask(eq(taskId), eq(1), any(Instant.class));
    }
}
