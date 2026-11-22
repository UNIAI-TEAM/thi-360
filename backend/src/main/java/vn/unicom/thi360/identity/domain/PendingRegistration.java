package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Phiên tự đăng ký chờ xác minh mã OTP email trước khi tạo tài khoản chính thức (UNI-2773, TM Q13).
 *
 * <p>Tuân thủ đúng quy tắc E15 của {@link EmailCode}: mã OTP có hiệu lực 10 phút, tối đa 5 lần nhập sai, mỗi mã chỉ
 * dùng được một lần. Bản thân phiên chờ còn phép bấm "Gửi lại mã" trong vòng 24 giờ kể từ lúc tạo miễn chưa hoàn tất.
 */
@Entity
@Table(name = "pending_registration", schema = "identity")
public class PendingRegistration {

    public static final Duration CODE_TTL = EmailCode.TTL;
    public static final int MAX_ATTEMPTS = EmailCode.MAX_ATTEMPTS;
    public static final Duration SESSION_TTL = Duration.ofHours(24);

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(name = "username_normalized", nullable = false)
    private String usernameNormalized;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_normalized", nullable = false)
    private String emailNormalized;

    @Column(name = "password_ciphertext", nullable = false)
    private String passwordCiphertext;

    @Column(name = "terms_version", nullable = false)
    private String termsVersion;

    @Column(name = "preferred_locale", nullable = false)
    private String preferredLocale;

    @Column(name = "callback_url")
    private String callbackUrl;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "code_expires_at", nullable = false)
    private Instant codeExpiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "code_used_at")
    private Instant codeUsedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PendingRegistration() {
    }

    public PendingRegistration(
            UUID id,
            String username,
            String usernameNormalized,
            String fullName,
            String email,
            String emailNormalized,
            String passwordCiphertext,
            String termsVersion,
            String preferredLocale,
            String callbackUrl,
            String codeHash,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.usernameNormalized = Objects.requireNonNull(usernameNormalized, "usernameNormalized must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.emailNormalized = Objects.requireNonNull(emailNormalized, "emailNormalized must not be null");
        this.passwordCiphertext = Objects.requireNonNull(passwordCiphertext, "passwordCiphertext must not be null");
        this.termsVersion = Objects.requireNonNull(termsVersion, "termsVersion must not be null");
        this.preferredLocale = Objects.requireNonNull(preferredLocale, "preferredLocale must not be null");
        this.callbackUrl = callbackUrl;
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash must not be null");
        this.codeExpiresAt = Objects.requireNonNull(now, "now must not be null").plus(CODE_TTL);
        this.attempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean canResend(Instant now) {
        return completedAt == null && now.isBefore(createdAt.plus(SESSION_TTL));
    }

    public boolean isCodeUsable(Instant now) {
        return completedAt == null
                && codeUsedAt == null
                && attempts < MAX_ATTEMPTS
                && now.isBefore(codeExpiresAt);
    }

    public void updateDetails(
            String username,
            String usernameNormalized,
            String fullName,
            String passwordCiphertext,
            String termsVersion,
            String preferredLocale,
            String callbackUrl,
            Instant now) {
        this.username = Objects.requireNonNull(username);
        this.usernameNormalized = Objects.requireNonNull(usernameNormalized);
        this.fullName = Objects.requireNonNull(fullName);
        this.passwordCiphertext = Objects.requireNonNull(passwordCiphertext);
        this.termsVersion = Objects.requireNonNull(termsVersion);
        this.preferredLocale = Objects.requireNonNull(preferredLocale);
        this.callbackUrl = callbackUrl;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void replaceCode(String newCodeHash, Instant now) {
        this.codeHash = Objects.requireNonNull(newCodeHash);
        this.codeExpiresAt = Objects.requireNonNull(now).plus(CODE_TTL);
        this.attempts = 0;
        this.codeUsedAt = null;
        this.updatedAt = now;
    }

    public int recordWrongAttempt(Instant now) {
        this.attempts += 1;
        this.updatedAt = Objects.requireNonNull(now);
        return Math.max(0, MAX_ATTEMPTS - this.attempts);
    }

    public void consumeCode(Instant now) {
        this.codeUsedAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public void releaseCode(Instant now) {
        if (this.completedAt == null) {
            this.codeUsedAt = null;
            this.updatedAt = Objects.requireNonNull(now);
        }
    }

    public void markCompleted(Instant now) {
        this.completedAt = Objects.requireNonNull(now);
        this.passwordCiphertext = "";
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordCiphertext() {
        return passwordCiphertext;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCodeExpiresAt() {
        return codeExpiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCodeUsedAt() {
        return codeUsedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
