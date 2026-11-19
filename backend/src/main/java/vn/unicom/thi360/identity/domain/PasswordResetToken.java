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
 * {@code used_at} đánh dấu mã đã dùng hoặc mã bị vô hiệu khi có yêu cầu mới (E16),
 * và mã bị hủy khi tài khoản không còn {@code ACTIVE} lúc đặt lại.
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

    protected PasswordResetToken() {
    }

    public PasswordResetToken(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt,
            String requestIp) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.requestIp = requestIp == null || requestIp.isBlank() ? null : requestIp.trim();
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
}
