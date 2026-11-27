package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * {@code POST /api/v1/me/email/change} và {@code /change/confirm} (TM 5.3, E17). Email cũ giữ nguyên
 * (đăng nhập, khôi phục) tới khi mã xác nhận đúng; xác nhận đồng bộ sang Keycloak trong cùng transaction,
 * Keycloak lỗi thì rollback toàn bộ (mã chưa bị đánh dấu đã dùng) và API trả 503.
 *
 * <p>Gọi Keycloak khi đang giữ khóa là ngoại lệ có chủ đích của plan B2 ("theo cách bù của PBI-01"); chỉ giữ khóa
 * tư vấn của riêng tài khoản này. Commit cơ sở dữ liệu lỗi sau khi Keycloak đã nhận email mới không có bước bù
 * (giới hạn như {@code UserAdminService.updateUser}).
 */
@Service
public class EmailChangeService {

    private static final String MODULE = "IDENTITY";
    private static final String ERROR_EMAIL_TAKEN = "EMAIL_TAKEN";

    private final EmailCodeAccountLoader accountLoader;
    private final UserAccountRepository userAccountRepository;
    private final EmailCodeService emailCodeService;
    private final KeycloakIdentityClient keycloakClient;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final MailQueue mailQueue;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final AppMessages messages;

    public EmailChangeService(
            EmailCodeAccountLoader accountLoader,
            UserAccountRepository userAccountRepository,
            EmailCodeService emailCodeService,
            KeycloakIdentityClient keycloakClient,
            CurrentPasswordVerifier currentPasswordVerifier,
            MailQueue mailQueue,
            AuditRecorder auditRecorder,
            PlatformTransactionManager transactionManager,
            Clock clock,
            AppMessages messages) {
        this.accountLoader = accountLoader;
        this.userAccountRepository = userAccountRepository;
        this.emailCodeService = emailCodeService;
        this.keycloakClient = keycloakClient;
        this.currentPasswordVerifier = currentPasswordVerifier;
        this.mailQueue = mailQueue;
        this.auditRecorder = auditRecorder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.messages = messages;
    }

    /**
     * Gửi mã tới {@code newEmail} và ghi {@code pending_email}; {@code email} chưa đổi.
     *
     * @throws ConflictException             {@code EMAIL_TAKEN}: email thuộc tài khoản khác hoặc chính là email hiện tại
     *                                       (câu hỏi mở 6, tạm)
     * @throws ValidationException           email sai định dạng hoặc mật khẩu hiện tại sai
     * @throws EmailCodeRateLimitedException quá giới hạn E17
     */
    public void requestChange(String newEmailRaw, String currentPassword) {
        UserAccount current = accountLoader.requireCurrentAccount();
        String newEmail = validateEmail(newEmailRaw);
        String newNormalized = Normalizer.normalize(newEmail);
        if (newNormalized.equals(current.getEmailNormalized())) {
            throw new ConflictException(ERROR_EMAIL_TAKEN);
        }
        currentPasswordVerifier.verify(current.getUsername(), currentPassword);

        transactionTemplate.executeWithoutResult(status -> {
            UserAccount account = accountLoader.lockActive(current.getId());
            // Email hiện tại có thể vừa đổi do một xác nhận khác chen vào sau lần kiểm đầu: kiểm lại trên dòng đã khóa.
            if (newNormalized.equals(account.getEmailNormalized())) {
                throw new ConflictException(ERROR_EMAIL_TAKEN);
            }
            assertEmailFree(account, newNormalized);
            String code = emailCodeService.issue(account.getId(), EmailCodePurpose.CHANGE, newEmail);
            account.requestEmailChange(newEmail, Instant.now(clock), account.getId());
            userAccountRepository.save(account);
            mailQueue.enqueue(
                    MailKind.EMAIL_CHANGE,
                    newEmail,
                    account.getPreferredLocale(),
                    Map.of("fullName", account.getFullName(), "code", code, "newEmail", newEmail),
                    account.getId());
            auditRecorder.record(AuditEvent.builder(MODULE, "EMAIL_CHANGE_REQUESTED")
                    .target("USER", account.getId().toString(), account.getUsername())
                    .after(Map.of("pendingEmail", newEmail))
                    .actor(account.getId(), account.getUsername(), List.of(), AuditActorType.USER)
                    .build());
        });
    }

    /**
     * Xác nhận mã: kiểm trùng lần nữa, {@code email = pending_email}, đã xác minh, đồng bộ Keycloak, báo email cũ.
     *
     * @throws EmailCodeInvalidException mã sai, còn lần nhập
     * @throws ConflictException         {@code EMAIL_TAKEN}: email bị tài khoản khác lấy trong lúc chờ
     * @throws vn.unicom.thi360.shared.api.ExternalServiceException Keycloak lỗi (API trả 503), mọi thay đổi bị hủy
     */
    public void confirmChange(String code) {
        UserAccount current = accountLoader.requireCurrentAccount();
        EmailCodeService.VerifyOutcome outcome = transactionTemplate.execute(status -> {
            UserAccount account = accountLoader.lockActive(current.getId());
            String pending = account.getPendingEmail();
            if (pending == null) {
                return new EmailCodeService.VerifyOutcome.Expired();
            }
            EmailCodeService.VerifyOutcome result =
                    emailCodeService.verify(account.getId(), EmailCodePurpose.CHANGE, pending, code);
            if (result instanceof EmailCodeService.VerifyOutcome.Accepted) {
                applyConfirmed(account, pending);
            }
            return result;
        });
        EmailCodeResults.throwUnlessAccepted(outcome);
    }

    /**
     * Mọi việc có thể lỗi ở phía cơ sở dữ liệu (lưu, xếp thư báo, nhật ký) chạy trước; Keycloak là bước ngoài cuối cùng
     * có thể lỗi, nên lỗi cơ sở dữ liệu không bao giờ để lại Keycloak đã đổi email mà dòng tài khoản bị rollback. Keycloak
     * lỗi (ExternalServiceException, hoặc 409 của Keycloak thành EMAIL_TAKEN) rollback cả transaction.
     */
    private void applyConfirmed(UserAccount account, String pendingEmail) {
        assertEmailFree(account, Normalizer.normalize(pendingEmail));
        String oldEmail = account.getEmail();
        Instant now = Instant.now(clock);
        account.confirmEmailChange(now, account.getId());
        try {
            userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw UserAdminService.mapUniqueConstraintViolation(ex);
        }
        if (oldEmail != null) {
            mailQueue.enqueue(
                    MailKind.EMAIL_CHANGED_NOTICE,
                    oldEmail,
                    account.getPreferredLocale(),
                    Map.of("fullName", account.getFullName(), "newEmail", account.getEmail()),
                    account.getId());
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("email", oldEmail);
        auditRecorder.record(AuditEvent.builder(MODULE, "EMAIL_CHANGED")
                .target("USER", account.getId().toString(), account.getUsername())
                .before(before)
                .after(Map.of("email", account.getEmail()))
                .actor(account.getId(), account.getUsername(), List.of(), AuditActorType.USER)
                .build());
        // Audit uses a JPA outbox; flush its pending insert before the irreversible Keycloak call.
        userAccountRepository.flush();
        keycloakClient.updateUserProfile(account.getId(), account.getEmail(), null, null, true);
    }

    private void assertEmailFree(UserAccount account, String normalizedEmail) {
        userAccountRepository.findActiveByEmailNormalized(normalizedEmail)
                .filter(other -> !other.getId().equals(account.getId()))
                .ifPresent(other -> {
                    throw new ConflictException(ERROR_EMAIL_TAKEN);
                });
    }

    private String validateEmail(String raw) {
        String email = raw == null ? "" : raw.trim();
        String code = null;
        String messageKey = null;
        if (email.isEmpty()) {
            code = "NOTBLANK";
            messageKey = "validation.user.email.notBlank";
        } else if (email.length() > 255) {
            code = "SIZE";
            messageKey = "validation.user.email.size";
        } else if (!UserAdminService.EMAIL_PATTERN.matcher(email).matches()) {
            code = "EMAIL_INVALID";
            messageKey = "validation.user.email.invalid";
        }
        if (code != null) {
            throw new ValidationException(
                    "VALIDATION_FAILED", null, List.of(new FieldError("newEmail", code, messages.get(messageKey))));
        }
        return email;
    }
}
