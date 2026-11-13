ALTER TABLE identity.user_account
    ADD COLUMN idp_enabled_sync_pending boolean NOT NULL DEFAULT false;

CREATE INDEX idx_user_account_idp_enabled_sync_pending
    ON identity.user_account (updated_at)
    WHERE idp_enabled_sync_pending = true AND deleted_at IS NULL;
