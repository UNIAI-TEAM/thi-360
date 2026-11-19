CREATE TABLE identity.password_reset_token (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identity.user_account (id),
    token_hash text NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    request_ip text,
    -- Email đã chuẩn hóa của địa chỉ nhận liên kết lúc phát hành: đặt lại chỉ hợp lệ khi tài khoản vẫn dùng đúng
    -- địa chỉ này và còn xác minh.
    recovery_email_normalized text NOT NULL,
    -- used_at đặt khi mã bị chiếm, bị thay hoặc bị hủy. invalidated_at đánh dấu mã bị thay/hủy (không bao giờ
    -- được trả về chưa dùng). password_changed_at: Keycloak đã nhận mật khẩu mới, mã dùng xong vĩnh viễn.
    -- sessions_revoked_at: phiên cũ đã bị thu hồi sau khi đổi mật khẩu (job thử lại khi còn NULL).
    invalidated_at timestamptz,
    password_changed_at timestamptz,
    sessions_revoked_at timestamptz
);

CREATE UNIQUE INDEX uq_password_reset_token_hash
    ON identity.password_reset_token (token_hash);

CREATE INDEX idx_password_reset_token_user_created
    ON identity.password_reset_token (user_id, created_at);

CREATE INDEX idx_password_reset_token_revocation_pending
    ON identity.password_reset_token (password_changed_at)
    WHERE password_changed_at IS NOT NULL AND sessions_revoked_at IS NULL;

CREATE TABLE identity.user_avatar (
    user_id uuid PRIMARY KEY REFERENCES identity.user_account (id),
    asset_id uuid NOT NULL UNIQUE,
    object_key text NOT NULL,
    size_bytes bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
