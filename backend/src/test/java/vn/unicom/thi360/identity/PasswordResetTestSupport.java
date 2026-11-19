package vn.unicom.thi360.identity;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.support.MailOutbox;

/**
 * Dựng và dọn tài khoản cho các IT của ID-2 (quên mật khẩu, đặt lại, hồ sơ): mỗi tài khoản có hậu tố ngẫu nhiên,
 * dọn DB, Keycloak và khóa Redis ở {@link #cleanup()} theo mẫu {@code NoHardDeleteIT} (plan ID-2, dữ liệu test).
 */
final class PasswordResetTestSupport {

    static final String PASSWORD = "Valid@1234";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

    record Account(UUID id, String username, String email, String password) {
    }

    private final KeycloakIdentityClient keycloakClient;
    private final UserAccountRepository userAccountRepository;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final byte[] pepper;
    private final List<Account> created = new ArrayList<>();

    PasswordResetTestSupport(
            KeycloakIdentityClient keycloakClient,
            UserAccountRepository userAccountRepository,
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            String pepper) {
        this.keycloakClient = keycloakClient;
        this.userAccountRepository = userAccountRepository;
        this.jdbc = jdbc;
        this.redis = redis;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    MailOutbox mailbox() {
        return new MailOutbox(jdbc);
    }

    /** Tài khoản {@code ACTIVE}, email đã xác minh, chưa xóa: đủ điều kiện nhận thư đặt lại. */
    Account eligible(String prefix) {
        return create(prefix, UserStatus.ACTIVE, true, false);
    }

    Account create(String prefix, UserStatus status, boolean emailVerified, boolean deleted) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "." + suffix;
        String email = username + "@thi360-test.example";
        UUID id = keycloakClient.createUser(new CreateUserCommand(
                username, email, "Nguyễn Văn Test", "User", PASSWORD, status == UserStatus.ACTIVE, false, true));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        userAccountRepository.save(new UserAccount(
                id,
                String.format("U%06d", seq),
                username,
                "Nguyễn Văn Test",
                email,
                null,
                UserSource.ADMIN_CREATED,
                status,
                false,
                Instant.now(),
                id));
        if (emailVerified) {
            jdbc.update("UPDATE identity.user_account SET email_verified_at = now() WHERE id = ?", id);
        }
        if (deleted) {
            jdbc.update("UPDATE identity.user_account SET deleted_at = now() WHERE id = ?", id);
        }
        Account account = new Account(id, username, email, PASSWORD);
        created.add(account);
        return account;
    }

    /** Mã rõ trong liên kết của thư {@code PASSWORD_RESET} mới nhất gửi tới {@code email}. */
    String tokenFromLatestMail(String email) {
        String text = mailbox().require(email, MailKind.PASSWORD_RESET).text();
        Matcher matcher = TOKEN_IN_LINK.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No reset link in the latest PASSWORD_RESET mail");
        }
        return matcher.group(1);
    }

    /** HMAC-SHA256 hex của mã với pepper, cùng cách ứng dụng băm. */
    String hash(String rawToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Chèn thẳng một mã đặt lại để kiểm soát hạn và trạng thái; trả mã rõ. */
    String insertToken(UUID userId, Instant createdAt, Instant expiresAt, Instant usedAt) {
        String raw = "t" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
                "INSERT INTO identity.password_reset_token (id, user_id, token_hash, expires_at, used_at, created_at, request_ip)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                hash(raw),
                Timestamp.from(expiresAt),
                usedAt == null ? null : Timestamp.from(usedAt),
                Timestamp.from(createdAt),
                "198.51.100.9");
        return raw;
    }

    int tokenRows(UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    int unusedTokenRows(UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ? AND used_at IS NULL",
                Integer.class,
                userId);
        return count == null ? 0 : count;
    }

    /** Lùi {@code created_at} của mọi mã của tài khoản về {@code secondsAgo} giây trước. */
    void ageTokens(UUID userId, long secondsAgo) {
        jdbc.update(
                "UPDATE identity.password_reset_token SET created_at = now() - (? * interval '1 second') WHERE user_id = ?",
                secondsAgo,
                userId);
    }

    void cleanup() {
        for (Account account : created) {
            try {
                keycloakClient.deleteUser(account.id());
            } catch (Exception ignored) {
                // đã xóa trong test
            }
            jdbc.update("DELETE FROM identity.password_reset_token WHERE user_id = ?", account.id());
            jdbc.update("DELETE FROM identity.user_avatar WHERE user_id = ?", account.id());
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", account.id());
            jdbc.update("DELETE FROM identity.user_identity WHERE user_id = ?", account.id());
            jdbc.update("DELETE FROM identity.user_role WHERE user_id = ?", account.id());
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", account.id());
            redis.delete(List.of(
                    "user-sess:" + account.id(),
                    "user-sess-seq:" + account.id(),
                    "user-rev-gen:" + account.id()));
        }
        created.clear();
    }
}
