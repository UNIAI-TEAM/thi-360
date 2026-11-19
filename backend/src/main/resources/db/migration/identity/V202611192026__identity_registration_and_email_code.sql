-- ID-1 (UNI-2710): đăng ký tự do, xác minh email, đổi email (TM 5.1, 5.3).
-- Ba cột mới để trống với tài khoản cũ: tài khoản cũ không bị coi là đã chấp nhận điều khoản.
ALTER TABLE identity.user_account
    ADD COLUMN terms_version text,
    ADD COLUMN terms_accepted_at timestamptz,
    ADD COLUMN pending_email text;

-- Mã OTP chỉ lưu dạng HMAC-SHA256 (khóa là THI360_TOKEN_PEPPER); không có cột mã rõ.
-- used_at đánh dấu mã đã dùng hoặc đã bị mã mới thay thế: chỉ mục duy nhất bên dưới bảo đảm
-- mỗi tài khoản và mỗi mục đích chỉ có một mã còn hiệu lực.
CREATE TABLE identity.email_code (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identity.user_account (id),
    purpose text NOT NULL CHECK (purpose IN ('VERIFY', 'CHANGE')),
    email text NOT NULL,
    code_hash text NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts int NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    used_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX uq_email_code_live
    ON identity.email_code (user_id, purpose)
    WHERE used_at IS NULL;

-- Đếm số mã đã gửi trong 24 giờ và tính thời gian chờ gửi lại (E15, E17).
CREATE INDEX idx_email_code_user_purpose_created
    ON identity.email_code (user_id, purpose, created_at DESC);
