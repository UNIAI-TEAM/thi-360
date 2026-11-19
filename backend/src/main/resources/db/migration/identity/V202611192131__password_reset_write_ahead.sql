-- Ghi trước khi đặt mật khẩu (R1-1/R2-1) và gắn mã với lần xác minh email lúc phát hành (R1-3).
--
-- password_write_started_at: dấu ghi trước, commit trong transaction riêng TRƯỚC khi gọi Keycloak. Còn dấu mà
-- chưa có finalized_at nghĩa là Keycloak có thể đã đổi mật khẩu (thành công, hết thời gian, 5xx, tiến trình chết):
-- job đối soát thu hồi mọi phiên của tài khoản, ghi audit rồi hoàn tất; không bao giờ ghi lại mật khẩu. Keycloak từ
-- chối rõ ràng thì dấu được xóa cùng lúc trả mã về chưa dùng.
-- finalized_at: lúc audit PASSWORD_RESET được ghi (thành công hoặc chưa xác định) cùng transaction với dấu này.
-- reconcile_attempted_at: lần đối soát gần nhất, để dòng kẹt không làm đói dòng mới.
-- recovery_email_verified_at: giá trị email_verified_at lúc phát hành; xác minh lại sau khi đổi liên hệ làm mã cũ vô hiệu.
ALTER TABLE identity.password_reset_token
    ADD COLUMN password_write_started_at timestamptz,
    ADD COLUMN finalized_at timestamptz,
    ADD COLUMN reconcile_attempted_at timestamptz,
    ADD COLUMN recovery_email_verified_at timestamptz;

UPDATE identity.password_reset_token t
SET finalized_at = t.password_changed_at
WHERE t.password_changed_at IS NOT NULL;

UPDATE identity.password_reset_token t
SET recovery_email_verified_at = u.email_verified_at
FROM identity.user_account u
WHERE u.id = t.user_id;

CREATE INDEX idx_password_reset_token_write_unfinalized
    ON identity.password_reset_token (password_write_started_at)
    WHERE password_write_started_at IS NOT NULL AND finalized_at IS NULL;
