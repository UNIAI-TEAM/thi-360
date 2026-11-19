package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mã OTP email (TM 5.3, E15). Chỉ giữ HMAC của mã. {@code usedAt} đánh dấu mã đã dùng hoặc đã bị mã mới
 * thay thế, nên mỗi tài khoản và mỗi mục đích chỉ có một mã còn hiệu lực (chỉ mục duy nhất ở cơ sở dữ liệu).
 */
@Entity
@Table(name = "email_code", schema = "identity")
public class EmailCode {

    public static final Duration TTL = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailCodePurpose purpose;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailCode() {
    }

    public EmailCode(UUID id, UUID userId, EmailCodePurpose purpose, String email, String codeHash, Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.expiresAt = now.plus(TTL);
    }

    /** Mã còn nhập được: chưa dùng, chưa hết hạn (đúng giây hết hạn là đã hết), chưa sai quá số lần cho phép. */
    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt) && attempts < MAX_ATTEMPTS;
    }

    /** Ghi một lần nhập sai và trả số lần còn lại. */
    public int recordWrongAttempt() {
        attempts++;
        return remainingAttempts();
    }

    private int remainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }

    public void markUsed(Instant now) {
        this.usedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }
}
