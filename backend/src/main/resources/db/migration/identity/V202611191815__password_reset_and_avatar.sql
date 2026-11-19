CREATE TABLE identity.password_reset_token (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identity.user_account (id),
    token_hash text NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    request_ip text
);

CREATE UNIQUE INDEX uq_password_reset_token_hash
    ON identity.password_reset_token (token_hash);

CREATE INDEX idx_password_reset_token_user_created
    ON identity.password_reset_token (user_id, created_at);

CREATE TABLE identity.user_avatar (
    user_id uuid PRIMARY KEY REFERENCES identity.user_account (id),
    asset_id uuid NOT NULL UNIQUE,
    object_key text NOT NULL,
    size_bytes bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
