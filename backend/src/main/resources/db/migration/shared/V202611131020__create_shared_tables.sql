CREATE SCHEMA IF NOT EXISTS shared;
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE shared.outbox_event (
    id uuid PRIMARY KEY,
    topic text NOT NULL,
    payload jsonb NOT NULL,
    trace_id text,
    created_at timestamptz NOT NULL DEFAULT now(),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    failed_at timestamptz,
    attempts int NOT NULL DEFAULT 0,
    last_error text
);

CREATE INDEX idx_outbox_event_unprocessed
    ON shared.outbox_event (created_at)
    WHERE processed_at IS NULL;

CREATE TABLE shared.idempotency_key (
    key text NOT NULL,
    scope text NOT NULL,
    response jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, key)
);

CREATE TABLE shared.system_setting (
    key text PRIMARY KEY,
    value jsonb NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid
);
