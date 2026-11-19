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
import org.springframework.data.domain.PageRequest;
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
import vn.unicom.thi360.shared.api.ValidationException;
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
 * (đúng một người thắng khi hai yêu cầu cùng mã) rồi gọi Keycloak ngoài transaction. Chỉ khi Keycloak từ chối
 * <b>rõ ràng</b> việc ghi mật khẩu thì mã mới được trả về chưa dùng (AC-21), và chỉ khi dòng vẫn là mã hiện hành
 * (chưa bị yêu cầu mới thay). Ghi thành công hoặc không loại trừ được việc đã ghi (hết thời gian, 5xx) thì mã dùng
 * xong vĩnh viễn: một mã đã cho phép đổi mật khẩu không bao giờ được cấp lại quyền. Khi Keycloak xác nhận đã ghi,
 * phiên cũ được thu hồi (Redis và Keycloak, fail-closed) TRƯỚC khi chốt mã và audit thành công; thu hồi lỗi hoặc
 * tiến trình chết trước khi chốt thì dòng còn dấu ghi trước mà chưa chốt, và {@link #reconcileUnfinalizedWrites}
 * thu hồi rồi chốt mà không ghi lại mật khẩu (người dùng yêu cầu liên kết mới nếu cần).
 *
 * <p>Ghi trước: dấu {@code password_write_started_at} được commit (transaction riêng) trước khi gọi Keycloak. Mọi kết
 * quả khác "từ chối rõ ràng" (thành công, hết thời gian, 5xx, ngoại lệ, tiến trình chết) để mã không dùng lại được và
 * thu hồi phiên (ngay trong lời gọi nếu còn chạy, hoặc ở {@link #reconcileUnfinalizedWrites}). Việc đối soát không
 * bao giờ ghi lại mật khẩu.
 */
@Service
public class PasswordResetService {

    static final String ERR_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    static final String ERR_WRITE_INDETERMINATE = "PASSWORD_WRITE_INDETERMINATE";
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String MODULE = "identity";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTES = 32;
    private static final String RESET_PATH = "/reset-password?token=";
    private static final int REVOCATION_BATCH = 50;

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
        tokenRepository.invalidateAllLiveOfUser(user.getId(), now);
        tokenRepository.saveAndFlush(new PasswordResetToken(
                Ids.newId(),
                user.getId(),
                hash(rawToken),
                now.plus(properties.tokenTtl()),
                now,
                requestIp,
                user.getEmailNormalized(),
                user.getEmailVerifiedAt().truncatedTo(ChronoUnit.MICROS)));
        mailQueue.enqueue(
                MailKind.PASSWORD_RESET,
                user.getEmail(),
                user.getPreferredLocale(),
                Map.of("fullName", user.getFullName(), "resetUrl", resetUrlPrefix + rawToken),
                user.getId());
        auditRecorder.record(AuditEvent.builder(MODULE, "PASSWORD_RESET_REQUESTED")
                .actorType(AuditActorType.SYSTEM)
                .target("USER", user.getId().toString(), user.getUsername())
                .outcome(AuditOutcome.SUCCESS)
                .build());
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

        // Dấu ghi trước commit riêng TRƯỚC khi gọi Keycloak: từ đây mọi kết quả khác "từ chối rõ ràng" đều để lại dòng
        // mà job đối soát thấy được, kể cả khi tiến trình chết ngay sau khi Keycloak đã đổi mật khẩu.
        if (!markWriteStarted(claim)) {
            throw invalidToken();
        }
        try {
            keycloakClient.setPassword(claim.userId(), newPassword, false);
        } catch (RuntimeException ex) {
            if (isDefiniteRejection(ex)) {
                releaseClaim(claim);
            } else {
                log.atError()
                        .setCause(ex)
                        .addKeyValue("event", "password_reset.write_indeterminate")
                        .addKeyValue("tokenId", claim.tokenId().toString())
                        .addKeyValue("userId", claim.userId().toString())
                        .log("Password write outcome unknown; reset token stays consumed, sessions are revoked");
                // Fail-closed: có thể Keycloak đã đổi mật khẩu. Thu hồi lỗi thì job đối soát làm lại.
                revokeSessions(claim.tokenId(), claim.userId());
            }
            throw ex;
        }

        // Keycloak đã xác nhận ghi: thu hồi phiên TRƯỚC khi chốt. Thu hồi lỗi hoặc tiến trình chết ở đây thì dòng còn
        // dấu ghi trước, chưa chốt; job đối soát thu hồi rồi chốt. Mã không bao giờ được mở lại.
        if (revokeSessions(claim.tokenId(), claim.userId())) {
            finalizeReset(claim);
        }
    }

    /**
     * Đối soát các lần ghi mật khẩu có dấu ghi trước mà chưa hoàn tất sau thời gian chờ (tiến trình chết sau khi ghi,
     * thu hồi phiên hoặc hoàn tất lỗi, Keycloak trả lời muộn hoặc không trả lời). Kết quả không chắc chắn nên
     * fail-closed: thu hồi mọi phiên của tài khoản (Redis và Keycloak), ghi audit {@code PASSWORD_RESET} với kết quả
     * chưa xác định rồi hoàn tất. Không bao giờ ghi lại mật khẩu. Trả số dòng đã hoàn tất.
     */
    public int reconcileUnfinalizedWrites() {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        List<PasswordResetToken> rows = tokenRepository.findUnfinalizedWrites(
                now.minus(properties.writeReconcileGrace()), PageRequest.of(0, REVOCATION_BATCH));
        int done = 0;
        for (PasswordResetToken row : rows) {
            if (reconcileWrite(row.getId(), row.getUserId())) {
                done++;
            }
        }
        return done;
    }

    private boolean reconcileWrite(UUID tokenId, UUID userId) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    tokenRepository.markReconcileAttempted(tokenId, Instant.now(clock).truncatedTo(ChronoUnit.MICROS)));
            userAdminService.revokeAllSessionsFailClosed(userId);
            String username = userAccountRepository.findById(userId).map(UserAccount::getUsername).orElse(null);
            transactionTemplate.executeWithoutResult(status -> {
                Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
                tokenRepository.markSessionsRevoked(tokenId, now);
                if (tokenRepository.markFinalized(tokenId, now) == 1) {
                    auditRecorder.record(AuditEvent.builder(MODULE, "PASSWORD_RESET")
                            .actorType(AuditActorType.SYSTEM)
                            .target("USER", userId.toString(), username)
                            .outcome(AuditOutcome.FAILURE)
                            .errorCode(ERR_WRITE_INDETERMINATE)
                            .reason("Keycloak password write outcome unconfirmed; all sessions revoked")
                            .build());
                }
            });
            return true;
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "password_reset.reconcile_failed")
                    .addKeyValue("tokenId", tokenId.toString())
                    .addKeyValue("userId", userId.toString())
                    .log("Password write reconciliation failed; it will be retried");
            return false;
        }
    }

    /** Lỗi chắc chắn chưa ghi mật khẩu: chính sách mật khẩu của Keycloak hoặc yêu cầu bị từ chối/không tới nơi. */
    private static boolean isDefiniteRejection(RuntimeException ex) {
        return ex instanceof ValidationException
                || ex instanceof KeycloakIdentityClient.PasswordWriteRejectedException;
    }

    /**
     * Sau khi Keycloak nhận mật khẩu và phiên cũ đã thu hồi: đánh dấu mã dùng xong và ghi audit thành công trong cùng
     * một transaction. Lỗi DB ở bước này chỉ được ghi log (mật khẩu đã đổi, không thể báo thất bại): dòng còn dấu
     * ghi trước mà chưa hoàn tất nên {@link #reconcileUnfinalizedWrites} ghi audit. Audit chỉ ghi một lần (người
     * hoàn tất dòng trước).
     */
    private void finalizeReset(Claim claim) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
                tokenRepository.markPasswordChanged(claim.tokenId(), now);
                if (tokenRepository.markFinalized(claim.tokenId(), now) == 1) {
                    auditRecorder.record(AuditEvent.builder(MODULE, "PASSWORD_RESET")
                            .actor(claim.userId(), claim.username(), List.of(), AuditActorType.USER)
                            .target("USER", claim.userId().toString(), claim.username())
                            .outcome(AuditOutcome.SUCCESS)
                            .build());
                }
            });
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "password_reset.finalize_failed")
                    .addKeyValue("tokenId", claim.tokenId().toString())
                    .addKeyValue("userId", claim.userId().toString())
                    .log("Password was changed but could not be finalized; reconciliation will record the audit");
        }
    }

    /**
     * Thu hồi mọi phiên của tài khoản (fail-closed) rồi ghi dấu đã thu hồi. Lỗi thì ghi log
     * {@code password_reset.indeterminate} (không có bí mật), để dòng chưa chốt cho job đối soát và không mở lại mã.
     */
    private boolean revokeSessions(UUID tokenId, UUID userId) {
        try {
            userAdminService.revokeAllSessionsFailClosed(userId);
            transactionTemplate.executeWithoutResult(status ->
                    tokenRepository.markSessionsRevoked(tokenId, Instant.now(clock).truncatedTo(ChronoUnit.MICROS)));
            return true;
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "password_reset.indeterminate")
                    .addKeyValue("tokenId", tokenId.toString())
                    .addKeyValue("userId", userId.toString())
                    .log("Password reset indeterminate: old sessions not revoked yet; reconciliation will revoke and finalize");
            return false;
        }
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
            tokenRepository.invalidate(tokenId, now);
            return null;
        }
        if (token.getUsedAt() != null || token.getInvalidatedAt() != null || !token.getExpiresAt().isAfter(now)) {
            return null;
        }
        if (!recoveryContactUnchanged(user, token)) {
            // Email nhận liên kết đã bị đổi/xóa/chưa xác minh sau khi phát hành: liên kết cũ hết quyền.
            tokenRepository.invalidate(tokenId, now);
            return null;
        }
        passwordPolicy.validate("newPassword", newPassword, user.getUsername());
        if (tokenRepository.claimUsable(tokenId, now) != 1) {
            return null;
        }
        return new Claim(tokenId, user.getId(), user.getUsername(), now);
    }

    /**
     * Tài khoản vẫn dùng đúng địa chỉ đã nhận liên kết, địa chỉ đó còn xác minh và là đúng lần xác minh lúc phát hành:
     * xác minh lại (kể cả cùng địa chỉ) sau khi đổi liên hệ làm liên kết cũ vô hiệu.
     */
    private static boolean recoveryContactUnchanged(UserAccount user, PasswordResetToken token) {
        Instant verifiedNow = user.getEmailVerifiedAt();
        Instant verifiedAtIssue = token.getRecoveryEmailVerifiedAt();
        return user.getEmail() != null
                && verifiedNow != null
                && verifiedAtIssue != null
                && verifiedAtIssue.truncatedTo(ChronoUnit.MICROS).equals(verifiedNow.truncatedTo(ChronoUnit.MICROS))
                && token.getRecoveryEmailNormalized().equals(user.getEmailNormalized());
    }

    /**
     * Trả mã về chưa dùng sau khi Keycloak từ chối rõ ràng. Dưới cùng khóa dòng tài khoản với phát hành, và chỉ khi
     * dòng vẫn là mã hiện hành chưa bị thay (câu UPDATE có điều kiện), nên yêu cầu mới trong lúc gọi không bị
     * hồi sinh mã cũ.
     */
    private void releaseClaim(Claim claim) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.query(
                        "SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE", rs -> {}, claim.userId());
                tokenRepository.clearWriteMark(claim.tokenId());
                tokenRepository.releaseClaim(claim.tokenId(), claim.claimedAt());
            });
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "password_reset.release_failed")
                    .addKeyValue("tokenId", claim.tokenId().toString())
                    .log("Failed to release password reset token after downstream failure");
        }
    }

    /**
     * Commit dấu ghi trước trong transaction riêng. {@code false} khi mã đã bị yêu cầu mới vô hiệu giữa lúc chiếm và
     * lúc này (không được ghi mật khẩu). Lỗi DB thì chắc chắn chưa ghi: trả mã về rồi ném lỗi.
     */
    private boolean markWriteStarted(Claim claim) {
        Integer updated;
        try {
            updated = transactionTemplate.execute(status -> tokenRepository.markWriteStarted(
                    claim.tokenId(), claim.claimedAt(), Instant.now(clock).truncatedTo(ChronoUnit.MICROS)));
        } catch (RuntimeException ex) {
            releaseClaim(claim);
            throw ex;
        }
        return updated != null && updated == 1;
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

    private record Claim(UUID tokenId, UUID userId, String username, Instant claimedAt) {
    }
}
