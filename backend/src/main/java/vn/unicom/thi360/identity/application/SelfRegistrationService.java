package vn.unicom.thi360.identity.application;

import jakarta.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.RegisterRequest;
import vn.unicom.thi360.identity.api.RegisterStartResponse;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.domain.EmailCodeSendPolicy;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.PendingRegistration;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserIdentity;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.domain.UsernamePolicy;
import vn.unicom.thi360.identity.infrastructure.EmailCodeHasher;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.PendingPasswordCipher;
import vn.unicom.thi360.identity.infrastructure.PendingRegistrationRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Tự đăng ký tài khoản học viên hai bước có xác minh OTP email bắt buộc trước khi tạo tài khoản (TM 5.1, Q13,
 * UNI-2773):
 * <ol>
 *   <li><b>Bước 1 ({@link #startRegistration}):</b> kiểm tra hợp lệ đầu vào và trùng tên đăng nhập / email, mã hóa tạm
 *       mật khẩu bằng {@link PendingPasswordCipher}, băm mã OTP 6 chữ số bằng {@link EmailCodeHasher} và xếp thư
 *       {@code EMAIL_VERIFY}. Chưa tạo dòng trong {@code identity.user_account} hay user trên Keycloak.</li>
 *   <li><b>Bước 2 ({@link #verifyAndRegister}):</b> kiểm tra mã OTP một lần dùng, rồi tạo tài khoản theo cơ chế hai pha
 *       {@code PROVISIONING -> Keycloak -> ACTIVE} của PBI-01 với {@code emailVerified = true} ngay từ đầu, đặt phiên
 *       cookie {@code HttpOnly} và trả đường dẫn chuyển hướng.</li>
 * </ol>
 */
@Service
public class SelfRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(SelfRegistrationService.class);
    private static final String MODULE = "IDENTITY";
    private static final String STUDENT_ROLE_CODE_NORMALIZED = "student";
    private static final String ERROR_USERNAME_TAKEN = "USERNAME_TAKEN";
    private static final String ERROR_EMAIL_TAKEN = "EMAIL_TAKEN";
    private static final String ERROR_EMAIL_CODE_EXPIRED = "EMAIL_CODE_EXPIRED";

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final UsernamePolicy usernamePolicy;
    private final PasswordPolicy passwordPolicy;
    private final KeycloakIdentityClient keycloakClient;
    private final AuthService authService;
    private final EmailCodeGenerator emailCodeGenerator;
    private final EmailCodeHasher emailCodeHasher;
    private final PendingPasswordCipher passwordCipher;
    private final MailQueue mailQueue;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTx;
    private final UserAdminService userAdminService;
    private final Clock clock;
    private final AppMessages messages;

    public SelfRegistrationService(
            UserAccountRepository userAccountRepository,
            UserIdentityRepository userIdentityRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            UsernamePolicy usernamePolicy,
            PasswordPolicy passwordPolicy,
            KeycloakIdentityClient keycloakClient,
            UserAdminService userAdminService,
            AuthService authService,
            EmailCodeGenerator emailCodeGenerator,
            EmailCodeHasher emailCodeHasher,
            PendingPasswordCipher passwordCipher,
            MailQueue mailQueue,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock,
            AppMessages messages) {
        this.userAccountRepository = userAccountRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.usernamePolicy = usernamePolicy;
        this.passwordPolicy = passwordPolicy;
        this.keycloakClient = keycloakClient;
        this.authService = authService;
        this.emailCodeGenerator = emailCodeGenerator;
        this.emailCodeHasher = emailCodeHasher;
        this.passwordCipher = passwordCipher;
        this.mailQueue = mailQueue;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.messages = messages;
        this.userAdminService = userAdminService;
    }

    /**
     * Bước 1: kiểm tra đầu vào, kiểm tra tên đăng nhập / email chưa có tài khoản hoạt động, tạo (hoặc cập nhật) phiên
     * đăng ký chờ và xếp thư OTP 6 chữ số.
     */
    public RegisterStartResponse startRegistration(RegisterRequest request, String localeCookieValue) {
        ValidInput input = validate(request);
        String locale = normalizeLocale(localeCookieValue);
        resolveStudentRole();
        ensureAvailable(input.usernameNormalized(), input.emailNormalized());

        RegisterStartResponse result = transactionTemplate.execute(status -> {
            lockEmail(input.emailNormalized());
            ensureAvailable(input.usernameNormalized(), input.emailNormalized());

            Instant now = Instant.now(clock);
            List<Instant> sent = findSentSince(input.emailNormalized(), now.minus(EmailCodeSendPolicy.DAILY_WINDOW));

            if (request.registrationId() != null) {
                PendingRegistration existing =
                        pendingRegistrationRepository.findByIdForUpdate(request.registrationId()).orElse(null);
                if (existing != null
                        && existing.canResend(now)
                        && existing.getEmailNormalized().equals(input.emailNormalized())) {
                    String cipher = passwordCipher.encrypt(existing.getId(), request.password());
                    existing.updateDetails(
                            input.username(),
                            input.usernameNormalized(),
                            input.fullName(),
                            cipher,
                            input.termsVersion(),
                            locale,
                            request.callbackUrl(),
                            now);
                    long cooldownWait = cooldownRemainingSeconds(sent, now);
                    if (cooldownWait > 0) {
                        if (!existing.isCodeUsable(now)) {
                            throw new EmailCodeRateLimitedException(cooldownWait);
                        }
                        pendingRegistrationRepository.saveAndFlush(existing);
                        return new RegisterStartResponse(existing.getId(), input.email(), cooldownWait);
                    }
                    OptionalLong dailyWait = EmailCodeSendPolicy.retryAfterSeconds(sent, now);
                    if (dailyWait.isPresent()) {
                        if (!existing.isCodeUsable(now)) {
                            throw new EmailCodeRateLimitedException(dailyWait.getAsLong());
                        }
                        pendingRegistrationRepository.saveAndFlush(existing);
                        return new RegisterStartResponse(existing.getId(), input.email(), dailyWait.getAsLong());
                    }
                    String otp = emailCodeGenerator.next();
                    existing.replaceCode(
                            emailCodeHasher.hash(existing.getId(), EmailCodePurpose.VERIFY, otp), now);
                    pendingRegistrationRepository.saveAndFlush(existing);
                    recordSend(existing.getId(), input.emailNormalized(), now);
                    enqueueVerifyMail(input.email(), locale, input.fullName(), otp);
                    return new RegisterStartResponse(
                            existing.getId(), input.email(), EmailCodeSendPolicy.RESEND_COOLDOWN.toSeconds());
                }
            }

            OptionalLong wait = EmailCodeSendPolicy.retryAfterSeconds(sent, now);
            if (wait.isPresent()) {
                throw new EmailCodeRateLimitedException(wait.getAsLong());
            }

            UUID registrationId = Ids.newId();
            String otp = emailCodeGenerator.next();
            String codeHash = emailCodeHasher.hash(registrationId, EmailCodePurpose.VERIFY, otp);
            String cipher = passwordCipher.encrypt(registrationId, request.password());
            PendingRegistration pending = new PendingRegistration(
                    registrationId,
                    input.username(),
                    input.usernameNormalized(),
                    input.fullName(),
                    input.email(),
                    input.emailNormalized(),
                    cipher,
                    input.termsVersion(),
                    locale,
                    request.callbackUrl(),
                    codeHash,
                    now);
            pendingRegistrationRepository.saveAndFlush(pending);
            recordSend(registrationId, input.emailNormalized(), now);
            enqueueVerifyMail(input.email(), locale, input.fullName(), otp);
            return new RegisterStartResponse(
                    registrationId, input.email(), EmailCodeSendPolicy.RESEND_COOLDOWN.toSeconds());
        });
        if (result == null) {
            throw new IllegalStateException("Failed to create pending registration");
        }
        return result;
    }

    /** Gửi lại mã OTP 6 chữ số cho phiên đăng ký đang chờ, áp đúng giới hạn E15 (60 giây và 5 mã / 24 giờ). */
    public RegisterStartResponse resendCode(UUID registrationId, String localeCookieValue) {
        PendingRegistration snapshot = pendingRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessRuleException(ERROR_EMAIL_CODE_EXPIRED));
        String emailNormalized = snapshot.getEmailNormalized();

        RegisterStartResponse result = transactionTemplate.execute(status -> {
            lockEmail(emailNormalized);
            PendingRegistration pending = pendingRegistrationRepository.findByIdForUpdate(registrationId)
                    .orElseThrow(() -> new BusinessRuleException(ERROR_EMAIL_CODE_EXPIRED));
            Instant now = Instant.now(clock);
            if (!pending.canResend(now)) {
                throw new BusinessRuleException(ERROR_EMAIL_CODE_EXPIRED);
            }
            ensureAvailable(pending.getUsernameNormalized(), pending.getEmailNormalized());

            List<Instant> sent = findSentSince(pending.getEmailNormalized(), now.minus(EmailCodeSendPolicy.DAILY_WINDOW));
            OptionalLong wait = EmailCodeSendPolicy.retryAfterSeconds(sent, now);
            if (wait.isPresent()) {
                throw new EmailCodeRateLimitedException(wait.getAsLong());
            }

            String otp = emailCodeGenerator.next();
            pending.replaceCode(emailCodeHasher.hash(registrationId, EmailCodePurpose.VERIFY, otp), now);
            pendingRegistrationRepository.saveAndFlush(pending);
            recordSend(registrationId, pending.getEmailNormalized(), now);

            String mailLocale = localeCookieValue != null && ("vi".equals(localeCookieValue) || "en".equals(localeCookieValue))
                    ? localeCookieValue
                    : pending.getPreferredLocale();
            enqueueVerifyMail(pending.getEmail(), mailLocale, pending.getFullName(), otp);
            return new RegisterStartResponse(
                    registrationId, pending.getEmail(), EmailCodeSendPolicy.RESEND_COOLDOWN.toSeconds());
        });
        if (result == null) {
            throw new IllegalStateException("Failed to resend registration OTP");
        }
        return result;
    }

    /**
     * Bước 2: kiểm tra mã OTP 6 chữ số, tạo tài khoản học viên đã xác minh email trên cả {@code identity} và Keycloak,
     * đặt phiên cookie {@code HttpOnly} và trả đường dẫn chuyển hướng.
     */
    public String verifyAndRegister(UUID registrationId, String submittedCode, HttpServletResponse response) {
        RegisteredAccount registered = verifyAndCreateAccount(registrationId, submittedCode);
        try {
            return authService.openSessionForRegisteredAccount(
                    registered.account(),
                    registered.password(),
                    registered.callbackUrl(),
                    registered.preferredLocale(),
                    response);
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "register.session_failed")
                    .addKeyValue("userId", registered.account().getId().toString())
                    .log("Account registered but opening the session failed; the user must log in");
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public AuthService.MobileLoginResult verifyAndRegisterMobile(UUID registrationId, String submittedCode) {
        RegisteredAccount registered = verifyAndCreateAccount(registrationId, submittedCode);
        try {
            return authService.openMobileSessionForRegisteredAccount(
                    registered.account(),
                    registered.password(),
                    registered.preferredLocale());
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "register.mobile_session_failed")
                    .addKeyValue("userId", registered.account().getId().toString())
                    .log("Account registered but opening the mobile session failed; the user must log in");
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    private RegisteredAccount verifyAndCreateAccount(UUID registrationId, String submittedCode) {
        VerifyStep step = transactionTemplate.execute(status -> {
            PendingRegistration pending = pendingRegistrationRepository.findByIdForUpdate(registrationId).orElse(null);
            Instant now = Instant.now(clock);
            if (pending == null || !pending.isCodeUsable(now)) {
                return new VerifyStep.Expired();
            }
            String trimmed = submittedCode == null ? "" : submittedCode.trim();
            if (!emailCodeHasher.matches(registrationId, EmailCodePurpose.VERIFY, trimmed, pending.getCodeHash())) {
                int remaining = pending.recordWrongAttempt(now);
                pendingRegistrationRepository.saveAndFlush(pending);
                return new VerifyStep.Wrong(remaining);
            }
            ensureAvailable(pending.getUsernameNormalized(), pending.getEmailNormalized());
            String plaintextPassword = passwordCipher.decrypt(registrationId, pending.getPasswordCiphertext());
            pending.consumeCode(now);
            pendingRegistrationRepository.saveAndFlush(pending);
            return new VerifyStep.Verified(
                    new ValidInput(
                            pending.getUsername(),
                            pending.getUsernameNormalized(),
                            pending.getFullName(),
                            pending.getEmail(),
                            pending.getEmailNormalized(),
                            pending.getTermsVersion()),
                    plaintextPassword,
                    pending.getPreferredLocale(),
                    pending.getCallbackUrl());
        });

        if (step == null || step instanceof VerifyStep.Expired) {
            throw new BusinessRuleException(ERROR_EMAIL_CODE_EXPIRED);
        }
        if (step instanceof VerifyStep.Wrong wrong) {
            throw new EmailCodeInvalidException(wrong.remainingAttempts());
        }
        VerifyStep.Verified verified = (VerifyStep.Verified) step;
        ValidInput input = verified.input();
        String locale = verified.preferredLocale();
        Role studentRole = resolveStudentRole();

        UUID provisionalId = Ids.newId();
        try {
            insertProvisional(provisionalId, input, studentRole, locale);
        } catch (RuntimeException ex) {
            releaseConsumedCodeQuietly(registrationId);
            throw ex;
        }

        UUID keycloakUserId;
        try {
            keycloakUserId = createKeycloakUser(provisionalId, input);
        } catch (RuntimeException ex) {
            if (userAccountRepository.findById(provisionalId).isEmpty()) {
                releaseConsumedCodeQuietly(registrationId);
            }
            throw ex;
        }
        try {
            keycloakClient.setPassword(keycloakUserId, verified.password(), false);
        } catch (RuntimeException ex) {
            userAdminService.rollbackCreatedRegistration(provisionalId, keycloakUserId);
            releaseConsumedCodeQuietly(registrationId);
            throw ex;
        }

        try {
            activate(provisionalId, keycloakUserId, studentRole, registrationId);
        } catch (RuntimeException ex) {
            userAdminService.rollbackCreatedRegistration(provisionalId, keycloakUserId);
            releaseConsumedCodeQuietly(registrationId);
            throw ex;
        }

        UserAccount account = userAccountRepository.findById(keycloakUserId)
                .orElseThrow(() -> new IllegalStateException("Registered account missing: " + keycloakUserId));
        return new RegisteredAccount(account, verified.password(), locale, verified.callbackUrl());
    }

    private record RegisteredAccount(
            UserAccount account, String password, String preferredLocale, String callbackUrl) {
    }

    private void releaseConsumedCodeQuietly(UUID registrationId) {
        try {
            requiresNewTx.executeWithoutResult(status -> pendingRegistrationRepository
                    .findByIdForUpdate(registrationId)
                    .ifPresent(pending -> {
                        pending.releaseCode(Instant.now(clock));
                        pendingRegistrationRepository.saveAndFlush(pending);
                    }));
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("registrationId", registrationId.toString())
                    .log("Failed to release consumed registration OTP after rollback");
        }
    }

    private sealed interface VerifyStep {
        record Expired() implements VerifyStep {}
        record Wrong(int remainingAttempts) implements VerifyStep {}
        record Verified(ValidInput input, String password, String preferredLocale, String callbackUrl)
                implements VerifyStep {}
    }

    private record ValidInput(
            String username,
            String usernameNormalized,
            String fullName,
            String email,
            String emailNormalized,
            String termsVersion) {
    }

    private static String normalizeLocale(String localeCookieValue) {
        return "vi".equals(localeCookieValue) || "en".equals(localeCookieValue)
                ? localeCookieValue
                : UserAccount.DEFAULT_LOCALE;
    }

    private void ensureAvailable(String usernameNormalized, String emailNormalized) {
        if (userAccountRepository.findActiveByUsernameNormalized(usernameNormalized).isPresent()) {
            throw new ConflictException(ERROR_USERNAME_TAKEN);
        }
        if (userAccountRepository.findActiveByEmailNormalized(emailNormalized).isPresent()) {
            throw new ConflictException(ERROR_EMAIL_TAKEN);
        }
    }

    private void lockEmail(String emailNormalized) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                rs -> null,
                "identity.pending_registration",
                emailNormalized);
    }

    private List<Instant> findSentSince(String emailNormalized, Instant since) {
        return jdbcTemplate.query(
                """
                SELECT created_at
                FROM identity.pending_registration_send
                WHERE email_normalized = ?
                  AND created_at >= ?
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> rs.getTimestamp("created_at").toInstant(),
                emailNormalized,
                Timestamp.from(since));
    }

    private void recordSend(UUID registrationId, String emailNormalized, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO identity.pending_registration_send (id, registration_id, email_normalized, created_at)
                VALUES (?, ?, ?, ?)
                """,
                Ids.newId(),
                registrationId,
                emailNormalized,
                Timestamp.from(now));
    }

    private static long cooldownRemainingSeconds(List<Instant> sent, Instant now) {
        if (sent.isEmpty()) {
            return 0;
        }
        Duration remaining = Duration.between(now, sent.getFirst().plus(EmailCodeSendPolicy.RESEND_COOLDOWN));
        if (remaining.isNegative() || remaining.isZero()) {
            return 0;
        }
        return remaining.getNano() > 0 ? remaining.getSeconds() + 1 : remaining.getSeconds();
    }

    private void enqueueVerifyMail(String email, String locale, String fullName, String otp) {
        mailQueue.enqueue(
                MailKind.EMAIL_VERIFY,
                email,
                locale,
                Map.of("fullName", fullName, "code", otp),
                null);
    }

    /** Trả đủ lỗi theo trường trong một lần (TM 5.1: kiểm ở máy chủ, AC-5). */
    private ValidInput validate(RegisterRequest request) {
        List<FieldError> errors = new ArrayList<>();

        UsernamePolicy.CheckResult usernameCheck = usernamePolicy.check(request.username());
        if (!usernameCheck.valid()) {
            errors.add(new FieldError("username", usernameCheck.reasonCode(), usernameCheck.message()));
        }
        errors.addAll(passwordPolicy.check("password", request.password(), usernameCheck.trimmed()));

        String fullName = request.fullName() == null ? "" : request.fullName().trim();
        if (fullName.isEmpty()) {
            errors.add(new FieldError("fullName", "NOTBLANK", messages.get("validation.user.fullName.notBlank")));
        } else if (fullName.length() > 200) {
            errors.add(new FieldError("fullName", "SIZE", messages.get("validation.user.fullName.size")));
        }

        String email = request.email() == null ? "" : request.email().trim();
        if (email.isEmpty()) {
            errors.add(new FieldError("email", "NOTBLANK", messages.get("validation.user.email.notBlank")));
        } else if (email.length() > 255) {
            errors.add(new FieldError("email", "SIZE", messages.get("validation.user.email.size")));
        } else if (!UserAdminService.EMAIL_PATTERN.matcher(email).matches()) {
            errors.add(new FieldError("email", "EMAIL_INVALID", messages.get("validation.user.email.invalid")));
        }

        if (!Boolean.TRUE.equals(request.acceptTerms())) {
            errors.add(new FieldError(
                    "acceptTerms", "TERMS_REQUIRED", messages.get("validation.register.terms.required")));
        }
        String termsVersion = request.termsVersion() == null ? "" : request.termsVersion().trim();
        if (termsVersion.isEmpty()) {
            errors.add(new FieldError(
                    "termsVersion", "NOTBLANK", messages.get("validation.register.termsVersion.notBlank")));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("VALIDATION_FAILED", null, errors);
        }
        return new ValidInput(
                usernameCheck.trimmed(),
                usernameCheck.normalized(),
                fullName,
                email,
                Normalizer.normalize(email),
                termsVersion);
    }

    private Role resolveStudentRole() {
        Role role = roleRepository.findByCodeNormalized(STUDENT_ROLE_CODE_NORMALIZED)
                .orElseThrow(() -> new IllegalStateException("System role STUDENT not found"));
        if (role.getStatus() != RoleStatus.ACTIVE) {
            throw new ConflictException("ROLE_INACTIVE");
        }
        return role;
    }

    private void insertProvisional(UUID provisionalId, ValidInput input, Role studentRole, String locale) {
        Instant now = Instant.now(clock);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
                UserAccount provisional = new UserAccount(
                        provisionalId,
                        String.format("U%06d", seq == null ? 1L : seq),
                        input.username(),
                        input.fullName(),
                        input.email(),
                        null,
                        UserSource.SELF_REGISTERED,
                        UserStatus.PROVISIONING,
                        false,
                        now,
                        null);
                provisional.setProvisioningRoleIds(studentRole.getId().toString());
                provisional.recordTermsAcceptance(input.termsVersion(), now);
                provisional.changePreferredLocale(locale);
                // Email đã được xác minh qua mã OTP ở Bước 2 trước khi tạo dòng tài khoản (UNI-2773).
                provisional.markEmailVerified(now, null);
                userAccountRepository.saveAndFlush(provisional);
            });
        } catch (DataIntegrityViolationException ex) {
            // Chỉ mục duy nhất ở cơ sở dữ liệu là nơi phân xử khi nhiều yêu cầu đồng thời (AC-1, AC-2).
            throw UserAdminService.mapUniqueConstraintViolation(ex);
        }
    }

    private UUID createKeycloakUser(UUID provisionalId, ValidInput input) {
        String[] names = UserAdminService.splitFullName(input.fullName());
        KeycloakIdentityClient.CreateUserCommand command = new KeycloakIdentityClient.CreateUserCommand(
                input.username(), input.email(), names[0], names[1], null, true, false, true);
        try {
            return keycloakClient.createSelfRegisteredUser(command);
        } catch (ConflictException conflict) {
            return retryAfterStaleCleanup(provisionalId, input, command);
        } catch (ValidationException ex) {
            userAdminService.deleteProvisionalUserQuietly(provisionalId);
            throw ex;
        } catch (KeycloakIdentityClient.CreateUserRejectedException ex) {
            userAdminService.deleteProvisionalUserQuietly(provisionalId);
            throw ex;
        } catch (ExternalServiceException ex) {
            // A lookup showing no user cannot prove an in-flight create will not complete later.
            // Retain the provisional row so reconciliation can recover either outcome.
            throw ex;
        }
    }

    /**
     * Keycloak báo 409 lần đầu: hoặc user của một tài khoản đã xóa mềm chưa được dọn ({@code idp_cleanup_pending}), hoặc
     * một user thật sự trùng. Dọn mục đầu rồi thử tạo lại một lần; lần thử lại được xử lý đúng như lần đầu: 409 thì
     * phân biệt trùng tên đăng nhập với trùng email, lỗi chưa rõ kết quả thì giữ dòng tạm để đối soát.
     */
    private UUID retryAfterStaleCleanup(
            UUID provisionalId, ValidInput input, KeycloakIdentityClient.CreateUserCommand command) {
        List<UserAccount> pending = userAccountRepository.findPendingIdpCleanupByUsernameOrEmail(
                input.usernameNormalized(), input.emailNormalized());
        if (pending.isEmpty()) {
            throw rejectKeycloakConflict(provisionalId, input);
        }
        try {
            for (UserAccount stale : pending) {
                if (stale.getStatus() != UserStatus.PROVISIONING) {
                    keycloakClient.deleteUser(stale.getId());
                    requiresNewTx.executeWithoutResult(status ->
                            userAccountRepository.updateIdpCleanupPending(stale.getId(), false, Instant.now(clock)));
                } else if (stale.getSource() == UserSource.SELF_REGISTERED) {
                    var subject = keycloakClient.findUserIdByUsername(stale.getUsername());
                    if (subject.isPresent()
                            && keycloakClient.matchesRegistrationIdentity(
                                    subject.get(), stale.getUsernameNormalized(), stale.getEmailNormalized())) {
                        keycloakClient.deleteUser(subject.get());
                    }
                    userAdminService.deleteProvisionalUserQuietly(stale.getId());
                } else {
                    userAdminService.cleanupOwnedRegistrationBeforeRetry(stale);
                }
            }
        } catch (RuntimeException ex) {
            // Việc dọn lỗi trước khi lần tạo của chính ta chạy: Keycloak chưa có user của dòng này, bỏ dòng tạm là an toàn.
            userAdminService.deleteProvisionalUserQuietly(provisionalId);
            throw ex instanceof ExternalServiceException
                    ? ex
                    : new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
        try {
            return keycloakClient.createSelfRegisteredUser(command);
        } catch (ConflictException ex) {
            throw rejectKeycloakConflict(provisionalId, input);
        } catch (ValidationException ex) {
            userAdminService.deleteProvisionalUserQuietly(provisionalId);
            throw ex;
        } catch (KeycloakIdentityClient.CreateUserRejectedException ex) {
            userAdminService.deleteProvisionalUserQuietly(provisionalId);
            throw ex;
        } catch (RuntimeException ex) {
            throw ex instanceof ExternalServiceException
                    ? ex
                    : new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    /** 409 xác định của Keycloak: không có user nào của ta để dọn, bỏ dòng tạm và báo trùng. */
    private ConflictException rejectKeycloakConflict(UUID provisionalId, ValidInput input) {
        userAdminService.deleteProvisionalUserQuietly(provisionalId);
        return new ConflictException(keycloakHasUsername(input.username()) ? ERROR_USERNAME_TAKEN : ERROR_EMAIL_TAKEN);
    }

    /** Không tra được thì coi là trùng tên đăng nhập, đúng cách hiểu mặc định của {@code KeycloakIdentityClient.createUser}. */
    private boolean keycloakHasUsername(String username) {
        try {
            return keycloakClient.findUserIdByUsername(username).isPresent();
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /** Nâng dòng tạm lên ACTIVE, gán vai trò, đánh dấu hoàn tất phiên chờ và ghi nhật ký kiểm toán trong một transaction. */
    private void activate(UUID provisionalId, UUID keycloakUserId, Role studentRole, UUID registrationId) {
        Instant activatedAt = Instant.now(clock);
        transactionTemplate.executeWithoutResult(status -> {
            int updated = userAccountRepository.promoteProvisionedUser(
                    provisionalId,
                    keycloakUserId,
                    UserStatus.PROVISIONING.code(),
                    UserStatus.ACTIVE.code(),
                    activatedAt,
                    keycloakUserId);
            if (updated != 1) {
                throw new IllegalStateException("Expected 1 promoted user row, got " + updated);
            }
            UserAccount account = userAccountRepository.findById(keycloakUserId)
                    .orElseThrow(() -> new IllegalStateException("Promoted user not found: " + keycloakUserId));
            account.markEmailVerified(activatedAt, keycloakUserId);
            userAccountRepository.save(account);

            userIdentityRepository.save(new UserIdentity(
                    Ids.newId(),
                    keycloakUserId,
                    IdentityProvider.LOCAL,
                    keycloakUserId.toString(),
                    account.getEmailNormalized(),
                    activatedAt));
            userRoleRepository.saveAndFlush(new UserRole(keycloakUserId, studentRole.getId(), null, activatedAt));

            pendingRegistrationRepository.findByIdForUpdate(registrationId).ifPresent(pending -> {
                pending.markCompleted(activatedAt);
                pendingRegistrationRepository.saveAndFlush(pending);
            });

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("code", account.getCode());
            after.put("username", account.getUsername());
            after.put("fullName", account.getFullName());
            after.put("email", account.getEmail());
            after.put("emailVerified", true);
            after.put("source", account.getSource().name());
            after.put("status", account.getStatus().name());
            after.put("roleCodes", List.of(studentRole.getCode()));
            after.put("termsVersion", account.getTermsVersion());
            after.put("preferredLocale", account.getPreferredLocale());
            auditRecorder.record(AuditEvent.builder(MODULE, "USER_SELF_REGISTERED")
                    .target("USER", keycloakUserId.toString(), account.getUsername())
                    .after(after)
                    .actor(keycloakUserId, account.getUsername(), List.of(), AuditActorType.USER)
                    .build());

            log.atInfo()
                    .addKeyValue("event", "user.self_registered")
                    .addKeyValue("userId", keycloakUserId.toString())
                    .addKeyValue("code", account.getCode())
                    .log("User account self-registered with verified email");
        });
    }
}
