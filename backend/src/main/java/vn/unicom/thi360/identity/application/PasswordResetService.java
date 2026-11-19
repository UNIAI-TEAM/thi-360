package vn.unicom.thi360.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.PasswordResetToken;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.PasswordResetTokenRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/**
 * Quên và đặt lại mật khẩu (TM 5.4, E16, E27). Chỉ tài khoản {@code identity.user_account} (realm {@code thi-360})
 * có mã; tài khoản nền tảng (realm {@code master}) không có dòng ở đây nên không bao giờ nhận thư (E27).
 *
 * <p>{@link #issue}: tra tài khoản, áp giới hạn E16 theo tài khoản, vô hiệu mã cũ, lưu mã mới và xếp thư trong
 * <b>một</b> transaction (xếp thư lỗi thì không còn mã). Dòng tài khoản bị khóa {@code FOR UPDATE} nên hai
 * yêu cầu đồng thời của một tài khoản chạy lần lượt và chỉ một mã còn hiệu lực.
 *
 * <p>{@link #reset}: không giữ khóa dòng khi gọi Keycloak (plan B2). Chiếm mã bằng một câu UPDATE có điều kiện
 * (đúng một người thắng khi hai yêu cầu cùng mã), gọi Keycloak ngoài transaction, lỗi thì trả mã về chưa dùng.
 */
@Service
public class PasswordResetService {

    static final String ERR_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String MODULE = "identity";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTES = 32;
    private static final String RESET_PATH = "/reset-password?token=";

    private final PasswordResetTokenRepository tokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordResetProperties properties;
    private final PasswordPolicy passwordPolicy;
    private final KeycloakIdentityClient keycloakClient;
    private final UserAdminService userAdminService;
    private final MailQueue mailQueue;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final byte[] pepper;
    private final String resetUrlPrefix;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserAccountRepository userAccountRepository,
            PasswordResetProperties properties,
            PasswordPolicy passwordPolicy,
            KeycloakIdentityClient keycloakClient,
            UserAdminService userAdminService,
            MailQueue mailQueue,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock,
            TokenPepperProperties pepperProperties,
            RedirectProperties redirectProperties) {
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.properties = Objects.requireNonNull(properties);
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy);
        this.keycloakClient = Objects.requireNonNull(keycloakClient);
        this.userAdminService = Objects.requireNonNull(userAdminService);
        this.mailQueue = Objects.requireNonNull(mailQueue);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
        this.pepper = Objects.requireNonNull(pepperProperties).pepper().getBytes(StandardCharsets.UTF_8);
        this.resetUrlPrefix = stripTrailingSlashes(Objects.requireNonNull(redirectProperties).webUrl()) + RESET_PATH;
    }

    /**
     * Tạo mã và xếp thư cho {@code email} nếu đủ điều kiện (TM 5.4): tài khoản {@code ACTIVE}, chưa xóa, đã xác
     * minh email. Không đủ điều kiện hoặc vượt giới hạn E16 thì không làm gì và không báo lỗi (caller trả 202).
     *
     * @throws RuntimeException khi xếp thư hoặc ghi DB lỗi; transaction rollback nên không còn mã
     */
    public void issue(String email, String requestIp) {
        String normalized = Normalizer.normalize(email);
        if (normalized == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> issueInTransaction(normalized, requestIp));
    }

    private void issueInTransaction(String normalizedEmail, String requestIp) {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM identity.user_account WHERE email_normalized = ? AND deleted_at IS NULL FOR UPDATE",
                UUID.class,
                normalizedEmail);
        if (ids.isEmpty()) {
            return;
        }
        UserAccount user = userAccountRepository.findById(ids.getFirst()).orElse(null);
        if (user == null
                || user.isDeleted()
                || user.getStatus() != UserStatus.ACTIVE
                || user.getEmailVerifiedAt() == null
                || user.getEmail() == null) {
            return;
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        List<PasswordResetToken> recent = tokenRepository.findRecentByUser(
                user.getId(), now.minus(properties.accountDailyWindow()));
        if (exceedsAccountLimits(recent, now)) {
            log.atInfo()
                    .addKeyValue("event", "password_reset.account_limited")
                    .addKeyValue("userId", user.getId().toString())
                    .log("Password reset request ignored: per-account limit");
            return;
        }

        String rawToken = newRawToken();
        tokenRepository.invalidateAllUnusedOfUser(user.getId(), now);
        tokenRepository.saveAndFlush(new PasswordResetToken(
                Ids.newId(),
                user.getId(),
                hash(rawToken),
                now.plus(properties.tokenTtl()),
                now,
                requestIp));
        mailQueue.enqueue(
                MailKind.PASSWORD_RESET,
                user.getEmail(),
                user.getPreferredLocale(),
                Map.of("fullName", user.getFullName(), "resetUrl", resetUrlPrefix + rawToken),
                user.getId());
    }

    /**
     * E16: tối đa 1 yêu cầu mỗi {@code accountCooldown} và {@code accountDailyLimit} yêu cầu mỗi
     * {@code accountDailyWindow} (cửa sổ trượt). {@code recent} là các mã tạo trong cửa sổ ngày, mới nhất trước.
     * Đúng bằng cooldown thì được phép (biên "giây 60"); mã bị vô hiệu cũng tính vào hạn mức.
     */
    boolean exceedsAccountLimits(List<PasswordResetToken> recent, Instant now) {
        if (recent.size() >= properties.accountDailyLimit()) {
            return true;
        }
        return !recent.isEmpty()
                && recent.getFirst().getCreatedAt().isAfter(now.minus(properties.accountCooldown()));
    }

    /**
     * Đặt lại mật khẩu bằng mã trong thư (TM 5.4). Trả về bình thường khi thành công (204); mã sai, đã dùng, hết
     * hạn hoặc tài khoản không còn {@code ACTIVE} thì 422 {@code RESET_TOKEN_INVALID}. {@code must_change_password}
     * giữ nguyên (quyết định tạm của plan ID-2).
     */
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(PasswordResetService::invalidToken);

        Claim claim = transactionTemplate.execute(status -> claimToken(token.getId(), newPassword));
        if (claim == null) {
            throw invalidToken();
        }

        try {
            keycloakClient.setPassword(claim.userId(), newPassword, false);
            userAdminService.revokeAllSessionsFailClosed(claim.userId());
        } catch (RuntimeException ex) {
            releaseClaim(token.getId(), claim.claimedAt());
            throw ex;
        }

        transactionTemplate.executeWithoutResult(status -> auditRecorder.record(
                AuditEvent.builder(MODULE, "PASSWORD_RESET")
                        .actor(claim.userId(), claim.username(), List.of(), AuditActorType.USER)
                        .target("USER", claim.userId().toString(), claim.username())
                        .outcome(AuditOutcome.SUCCESS)
                        .build()));
    }

    /**
     * Giai đoạn DB của {@link #reset}: khóa dòng tài khoản, kiểm tra mã, tài khoản và chính sách mật khẩu rồi chiếm
     * mã. Trả {@code null} khi mã không dùng được. Mật khẩu sai chính sách ném lỗi theo trường và rollback, nên mã
     * chưa bị đánh dấu dùng.
     */
    private Claim claimToken(UUID tokenId, String newPassword) {
        PasswordResetToken token = tokenRepository.findById(tokenId).orElse(null);
        if (token == null) {
            return null;
        }
        jdbcTemplate.query(
                "SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE", rs -> {}, token.getUserId());
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        UserAccount user = userAccountRepository.findById(token.getUserId()).orElse(null);
        if (user == null || user.isDeleted() || user.getStatus() != UserStatus.ACTIVE) {
            // Tài khoản bị khóa: không mở khóa, mã bị hủy và trả cùng lỗi (TM 5.4 bước 2).
            tokenRepository.claimUsable(tokenId, now);
            return null;
        }
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            return null;
        }
        passwordPolicy.validate("newPassword", newPassword, user.getUsername());
        if (tokenRepository.claimUsable(tokenId, now) != 1) {
            return null;
        }
        return new Claim(user.getId(), user.getUsername(), now);
    }

    private void releaseClaim(UUID tokenId, Instant claimedAt) {
        try {
            transactionTemplate.executeWithoutResult(status -> tokenRepository.releaseClaim(tokenId, claimedAt));
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "password_reset.release_failed")
                    .addKeyValue("tokenId", tokenId.toString())
                    .log("Failed to release password reset token after downstream failure");
        }
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** HMAC-SHA256 của mã với pepper (plan B6), dạng hex. Mã rõ không bao giờ được lưu. */
    String hash(String rawToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    private static BusinessRuleException invalidToken() {
        return new BusinessRuleException(ERR_TOKEN_INVALID);
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    private record Claim(UUID userId, String username, Instant claimedAt) {
    }
}
