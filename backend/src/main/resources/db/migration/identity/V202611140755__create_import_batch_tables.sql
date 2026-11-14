CREATE TABLE identity.import_batch (
    id uuid PRIMARY KEY,
    idempotency_key text,
    file_object_key text,
    original_filename text,
    must_change_password boolean NOT NULL DEFAULT false,
    status smallint NOT NULL CHECK (status IN (1, 2, 3, 4, 5, 6, 7)),
    total_rows int NOT NULL DEFAULT 0,
    valid_rows int NOT NULL DEFAULT 0,
    invalid_rows int NOT NULL DEFAULT 0,
    created_rows int NOT NULL DEFAULT 0,
    failed_rows int NOT NULL DEFAULT 0,
    result_object_key text,
    result_expires_at timestamptz,
    result_deleted_at timestamptz,
    source_deleted_at timestamptz,
    has_generated_passwords boolean NOT NULL DEFAULT false,
    error_message text,
    confirmed_at timestamptz,
    confirmed_by uuid,
    started_at timestamptz,
    completed_at timestamptz,
    created_by uuid NOT NULL,
    created_by_username text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_import_batch_idempotency_key
    ON identity.import_batch (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_import_batch_status_confirmed
    ON identity.import_batch (confirmed_at, created_at)
    WHERE status = 3;

CREATE TABLE identity.import_row (
    id uuid PRIMARY KEY,
    batch_id uuid NOT NULL REFERENCES identity.import_batch (id) ON DELETE CASCADE,
    row_no int NOT NULL,
    raw jsonb NOT NULL,
    username_normalized text,
    email_normalized text,
    class_code_normalized text,
    resolved_class_id uuid,
    password_generated boolean NOT NULL DEFAULT false,
    errors jsonb NOT NULL DEFAULT '[]'::jsonb,
    status smallint NOT NULL CHECK (status IN (1, 2, 3, 4)),
    created_user_id uuid,
    failure_code text,
    failure_reason text,
    processed_at timestamptz,
    CONSTRAINT uq_import_row_batch_row_no UNIQUE (batch_id, row_no)
);

CREATE INDEX idx_import_row_batch_status_row_no
    ON identity.import_row (batch_id, status, row_no);
