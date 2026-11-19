CREATE TABLE shared.email_message (
    id uuid PRIMARY KEY,
    kind text NOT NULL,
    to_email text NOT NULL,
    locale text NOT NULL,
    subject text NOT NULL,
    html text NOT NULL,
    text text NOT NULL,
    user_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    attempts int NOT NULL DEFAULT 0,
    sent_at timestamptz,
    failed_at timestamptz,
    last_error text
);

CREATE INDEX idx_email_message_pending
    ON shared.email_message (next_attempt_at)
    WHERE sent_at IS NULL AND failed_at IS NULL;
