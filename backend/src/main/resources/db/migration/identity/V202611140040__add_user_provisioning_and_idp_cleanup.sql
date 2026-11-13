ALTER TABLE identity.user_account
    ADD COLUMN provisioning_role_ids text,
    ADD COLUMN idp_cleanup_pending boolean NOT NULL DEFAULT false;

CREATE INDEX idx_user_account_idp_cleanup_pending
    ON identity.user_account (updated_at)
    WHERE idp_cleanup_pending = true;
