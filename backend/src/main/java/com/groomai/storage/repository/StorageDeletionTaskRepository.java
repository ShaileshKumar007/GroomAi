package com.groomai.storage.repository;

import com.groomai.storage.entity.StorageDeletionTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StorageDeletionTaskRepository extends JpaRepository<StorageDeletionTask, UUID> {

    /**
     * Finds pending tasks that are due for deletion attempts or whose lease has expired.
     */
    @Query("""
        SELECT t FROM StorageDeletionTask t
        WHERE (t.status = 'PENDING' AND t.nextAttemptAt <= :now)
           OR (t.status = 'PROCESSING' AND t.leaseExpiresAt IS NOT NULL AND t.leaseExpiresAt <= :now)
        ORDER BY t.nextAttemptAt ASC
        """)
    List<StorageDeletionTask> findDueTasks(@Param("now") Instant now, Pageable pageable);

    /**
     * Checks if a deletion task already exists for the given storage key.
     */
    boolean existsByStorageKey(String storageKey);

    /**
     * Finds the first active deletion task for the given storage key not in FAILED status.
     */
    Optional<StorageDeletionTask> findFirstByStorageKeyAndStatusNot(String storageKey, String status);

    /**
     * Finds the first deletion task for the given storage key.
     */
    Optional<StorageDeletionTask> findFirstByStorageKey(String storageKey);

    /**
     * Atomically claims a task by ID, transitioning it to PROCESSING with an incremented attempt count
     * and a lease expiry timestamp. Returns 1 if claimed, 0 if already claimed by another worker.
     */
    @Modifying
    @Query("""
        UPDATE StorageDeletionTask t
        SET t.status = 'PROCESSING',
            t.lastAttemptAt = :now,
            t.leaseExpiresAt = :leaseExpiresAt,
            t.attempts = t.attempts + 1
        WHERE t.id = :id
          AND (t.status = 'PENDING' OR (t.status = 'PROCESSING' AND t.leaseExpiresAt <= :now))
        """)
    int claimTaskById(
        @Param("id") UUID id,
        @Param("now") Instant now,
        @Param("leaseExpiresAt") Instant leaseExpiresAt
    );

    /**
     * Atomically claims a task by storage key, transitioning it to PROCESSING with an incremented attempt count
     * and a lease expiry timestamp. Returns 1 if claimed, 0 if already claimed by another worker.
     */
    @Modifying
    @Query("""
        UPDATE StorageDeletionTask t
        SET t.status = 'PROCESSING',
            t.lastAttemptAt = :now,
            t.leaseExpiresAt = :leaseExpiresAt,
            t.attempts = t.attempts + 1
        WHERE t.storageKey = :storageKey
          AND (t.status = 'PENDING' OR (t.status = 'PROCESSING' AND t.leaseExpiresAt <= :now))
        """)
    int claimTaskByStorageKey(
        @Param("storageKey") String storageKey,
        @Param("now") Instant now,
        @Param("leaseExpiresAt") Instant leaseExpiresAt
    );

    /**
     * Atomically deletes a completed task using claim fencing (id, attempts, leaseExpiresAt).
     * Prevents a stale worker whose lease expired from deleting a task that was reclaimed by another worker.
     */
    @Modifying
    @Query("""
        DELETE FROM StorageDeletionTask t
        WHERE t.id = :id
          AND t.attempts = :attempts
          AND t.leaseExpiresAt = :leaseExpiresAt
        """)
    int completeTask(
        @Param("id") UUID id,
        @Param("attempts") int attempts,
        @Param("leaseExpiresAt") Instant leaseExpiresAt
    );

    /**
     * Atomically records a task failure using claim fencing (id, attempts, leaseExpiresAt).
     * Prevents a stale worker whose lease expired from overwriting a newer claim's state.
     */
    @Modifying
    @Query("""
        UPDATE StorageDeletionTask t
        SET t.status = :newStatus,
            t.nextAttemptAt = :nextAttemptAt,
            t.leaseExpiresAt = NULL,
            t.errorMessage = :errorMessage
        WHERE t.id = :id
          AND t.attempts = :attempts
          AND t.leaseExpiresAt = :leaseExpiresAt
        """)
    int recordTaskFailure(
        @Param("id") UUID id,
        @Param("attempts") int attempts,
        @Param("leaseExpiresAt") Instant leaseExpiresAt,
        @Param("newStatus") String newStatus,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("errorMessage") String errorMessage
    );
}
