-- UNI-2773 (TM Q13): phiên tự đăng ký chờ xác minh mã OTP email trước khi tạo tài khoản chính thức.
-- Mật khẩu chỉ lưu dạng mã hóa AES-256-GCM (khóa dẫn xuất từ THI360_TOKEN_PEPPER) và xóa ngay khi hoàn tất;
-- mã OTP 6 chữ số chỉ lưu dạng HMAC-SHA256.

CREATE TABLE identity.pending_registration (
    id uuid PRIMARY KEY,
    username text NOT NULL,
    username_normalized text NOT NULL,
    full_name text NOT NULL,
    email text NOT NULL,
    email_normalized text NOT NULL,
    password_ciphertext text NOT NULL,
    terms_version text NOT NULL,
    preferred_locale text NOT NULL,
    callback_url text,
    code_hash text NOT NULL,
    code_expires_at timestamptz NOT NULL,
    attempts int NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    code_used_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX idx_pending_registration_created_at
    ON identity.pending_registration (created_at);

-- Nhật ký phát mã OTP đăng ký theo email_normalized để áp đúng giới hạn E15 (60 giây chờ gửi lại, tối đa 5 mã / 24 giờ).
CREATE TABLE identity.pending_registration_send (
    id uuid PRIMARY KEY,
    registration_id uuid NOT NULL REFERENCES identity.pending_registration (id) ON DELETE CASCADE,
    email_normalized text NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_pending_registration_send_email_created
    ON identity.pending_registration_send (email_normalized, created_at DESC);
