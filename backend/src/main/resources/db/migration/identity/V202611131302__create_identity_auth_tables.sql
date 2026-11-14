CREATE SEQUENCE IF NOT EXISTS identity.user_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE identity.user_account (
    id uuid PRIMARY KEY,
    code text NOT NULL UNIQUE,
    username text NOT NULL,
    username_normalized text NOT NULL,
    full_name text NOT NULL,
    email text,
    email_normalized text,
    email_verified_at timestamptz,
    phone text,
    source smallint NOT NULL CHECK (source IN (1, 2, 3, 4)),
    status smallint NOT NULL CHECK (status IN (1, 2, 3)),
    inactive_reason text,
    inactivated_at timestamptz,
    inactivated_by uuid,
    must_change_password boolean NOT NULL DEFAULT false,
    last_login_at timestamptz,
    deleted_at timestamptz,
    deleted_by uuid,
    delete_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_user_account_username_active
    ON identity.user_account (username_normalized)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_user_account_email_active
    ON identity.user_account (email_normalized)
    WHERE deleted_at IS NULL AND email_normalized IS NOT NULL;

CREATE INDEX idx_user_account_status_active
    ON identity.user_account (status)
    WHERE deleted_at IS NULL;

CREATE TABLE identity.user_identity (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identity.user_account (id),
    provider smallint NOT NULL CHECK (provider IN (1, 2)),
    provider_subject text NOT NULL,
    email_at_link text,
    linked_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_user_identity_user_id
    ON identity.user_identity (user_id);

CREATE TABLE identity.platform_admin (
    id uuid PRIMARY KEY,
    username text NOT NULL,
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
