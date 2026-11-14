ALTER TABLE identity.import_batch
    ADD COLUMN lease_token uuid,
    ADD COLUMN result_retry_count int NOT NULL DEFAULT 0;

CREATE INDEX idx_import_batch_pending_source_delete
    ON identity.import_batch (completed_at, updated_at)
    WHERE status IN (5, 6, 7) AND file_object_key IS NOT NULL AND source_deleted_at IS NULL;
