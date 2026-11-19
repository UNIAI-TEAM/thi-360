package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;

/** {@code POST /api/v1/me/email/verification} và {@code /verify} (TM 5.3, E15, E18). */
@Service
public class EmailCodeVerificationService {

    private static final String MODULE = "IDENTITY";

    private final EmailCodeAccountLoader accountLoader;
    private final UserAccountRepository userAccountRepository;
    private final EmailCodeService emailCodeService;
    private final MailQueue mailQueue;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EmailCodeVerificationService(
            EmailCodeAccountLoader accountLoader,
            UserAccountRepository userAccountRepository,
            EmailCodeService emailCodeService,
            MailQueue mailQueue,
            AuditRecorder auditRecorder,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.accountLoader = accountLoader;
        this.userAccountRepository = userAccountRepository;
        this.emailCodeService = emailCodeService;
        this.mailQueue = mailQueue;
        this.auditRecorder = auditRecorder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Gửi hoặc gửi lại mã tới email hiện tại.
     *
     * @throws BusinessRuleException {@code EMAIL_REQUIRED} khi tài khoản chưa có email (câu hỏi mở 5, tạm)
     * @throws EmailCodeRateLimitedException quá giới hạn E15
     */
    public void sendVerificationCode() {
        UserAccount current = accountLoader.requireCurrentAccount();
        transactionTemplate.executeWithoutResult(status -> {
            UserAccount account = userAccountRepository.findById(current.getId()).orElse(current);
            if (account.getEmail() == null) {
                throw new BusinessRuleException("EMAIL_REQUIRED");
            }
            EmailCodeService.IssuedCode issued =
                    emailCodeService.issue(account.getId(), EmailCodePurpose.VERIFY, account.getEmail());
            mailQueue.enqueue(
                    MailKind.EMAIL_VERIFY,
                    account.getEmail(),
                    account.getPreferredLocale(),
                    Map.of("fullName", account.getFullName(), "code", issued.code()),
                    account.getId());
        });
    }

    /**
     * @throws EmailCodeInvalidException mã sai, còn lần nhập
     * @throws BusinessRuleException     {@code EMAIL_CODE_EXPIRED}: hết hạn, đã dùng, đã bị thay hoặc sai quá 5 lần
     */
    public void verify(String code) {
        UserAccount current = accountLoader.requireCurrentAccount();
        // Lần nhập sai phải được commit trước khi báo lỗi, nên kết quả đi ra ngoài transaction rồi mới ném.
        EmailCodeService.VerifyOutcome outcome = transactionTemplate.execute(status -> {
            UserAccount account = userAccountRepository.findById(current.getId()).orElse(current);
            if (account.getEmail() == null) {
                return new EmailCodeService.VerifyOutcome.Expired();
            }
            EmailCodeService.VerifyOutcome result =
                    emailCodeService.verify(account.getId(), EmailCodePurpose.VERIFY, account.getEmail(), code);
            if (result instanceof EmailCodeService.VerifyOutcome.Accepted) {
                Instant now = Instant.now(clock);
                account.markEmailVerified(now, account.getId());
                userAccountRepository.save(account);
                auditRecorder.record(AuditEvent.builder(MODULE, "EMAIL_VERIFIED")
                        .target("USER", account.getId().toString(), account.getUsername())
                        .after(Map.of("email", account.getEmail()))
                        .actor(account.getId(), account.getUsername(), List.of(), AuditActorType.USER)
                        .build());
            }
            return result;
        });
        EmailCodeResults.throwUnlessAccepted(outcome);
    }
}
