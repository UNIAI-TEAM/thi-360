package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mã đặt lại mật khẩu (TM 5.4). Bảng chỉ giữ {@code token_hash} (HMAC với
 * {@code THI360_TOKEN_PEPPER}, plan B6); mã rõ chỉ xuất hiện trong liên kết thư.
 * {@code used_at} đặt khi mã bị chiếm, bị thay bằng yêu cầu mới (E16) hoặc bị hủy (tài khoản không còn
 * {@code ACTIVE}, hoặc địa chỉ email đã đổi so với lúc phát hành); {@code invalidated_at} phân biệt hai trường hợp
 * sau với mã đang bị chiếm; {@code password_changed_at} là lúc Keycloak nhận mật khẩu mới (mã dùng xong vĩnh viễn);
 * {@code sessions_revoked_at} là lúc phiên cũ đã bị thu hồi.
 */
@Entity
@Table(name = "password_reset_token", schema = "identity")
public class PasswordResetToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "request_ip")
    private String requestIp;

    @Column(name = "recovery_email_normalized", nullable = false)
    private String recoveryEmailNormalized;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "sessions_revoked_at")
    private Instant sessionsRevokedAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt,
            String requestIp,
            String recoveryEmailNormalized) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.requestIp = requestIp == null || requestIp.isBlank() ? null : requestIp.trim();
        this.recoveryEmailNormalized =
                Objects.requireNonNull(recoveryEmailNormalized, "recoveryEmailNormalized must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public String getRecoveryEmailNormalized() {
        return recoveryEmailNormalized;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getSessionsRevokedAt() {
        return sessionsRevokedAt;
    }
}
