package com.groomai.storage;

import com.groomai.storage.entity.StorageDeletionTask;

/**
 * Service managing durable cloud and local storage object eradication tasks
 * via the 'storage_deletion_outbox' table.
 */
public interface StorageDeletionOutboxService {

    /**
     * Enqueues a storage key for durable eradication.
     * Idempotent: if a task already exists for this key, returns the existing record.
     *
     * @param storageKey the object storage key to eradicate
     * @return the created or existing StorageDeletionTask
     */
    StorageDeletionTask enqueueDeletion(String storageKey);

    /**
     * Attempts immediate deletion for the given storage key.
     * If deletion succeeds, the outbox record is removed.
     * If it fails, the task remains durable in the outbox for the scheduled sweeper.
     *
     * @param storageKey the storage key to delete
     */
    void dispatchImmediate(String storageKey);

    /**
     * Processes due deletion tasks from the outbox with atomic lease claiming,
     * exponential backoff retries, and dead-lettering after 5 attempts.
     *
     * @return number of successfully processed deletion tasks
     */
    int processDueTasks();
}
