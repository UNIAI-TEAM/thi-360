ALTER TABLE identity.import_batch
    ADD COLUMN confirmed_by_username text,
    ADD COLUMN lease_expires_at timestamptz,
    ADD COLUMN next_retry_at timestamptz,
    ADD COLUMN retry_count int NOT NULL DEFAULT 0;

CREATE INDEX idx_import_batch_claimable
    ON identity.import_batch (status, next_retry_at, lease_expires_at, confirmed_at, created_at)
    WHERE status IN (3, 4);

ALTER TABLE identity.import_row
    ADD COLUMN provisional_user_id uuid,
    ADD COLUMN generated_password_enc text,
    ADD COLUMN retryable boolean NOT NULL DEFAULT false,
    ADD COLUMN attempt_count int NOT NULL DEFAULT 0;

ALTER TABLE identity.user_account
    ADD COLUMN import_row_id uuid;

CREATE UNIQUE INDEX uq_user_account_import_row_active
    ON identity.user_account (import_row_id)
    WHERE import_row_id IS NOT NULL AND deleted_at IS NULL;
