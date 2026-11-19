package vn.unicom.thi360.identity.application;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserIdentity;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.domain.UsernamePolicy;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Tự đăng ký tài khoản học viên (TM 5.1, PBI-06). Theo cách tạo tài khoản của PBI-01
 * ({@code UserAdminService.createUserInternal}): dòng {@code PROVISIONING} trước, rồi Keycloak, rồi nâng lên
 * {@code ACTIVE}; Keycloak lỗi thì bù để không còn tài khoản dở dang. Không giữ khóa dòng khi gọi Keycloak.
 *
 * <p>Dòng {@code PROVISIONING} ghi sẵn {@code provisioning_role_ids} (vai trò Học viên), điều khoản và ngôn ngữ,
 * để {@code ProvisioningReconcilerJob} hoàn tất đúng nếu tiến trình chết giữa chừng.
 */
@Service
public class SelfRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(SelfRegistrationService.class);
    private static final String MODULE = "IDENTITY";
    private static final String STUDENT_ROLE_CODE_NORMALIZED = "student";
    private static final String ERROR_USERNAME_TAKEN = "USERNAME_TAKEN";
    private static final String ERROR_EMAIL_TAKEN = "EMAIL_TAKEN";

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UsernamePolicy usernamePolicy;
    private final PasswordPolicy passwordPolicy;
    private final KeycloakIdentityClient keycloakClient;
    private final AuthService authService;
    private final EmailCodeService emailCodeService;
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
            UsernamePolicy usernamePolicy,
            PasswordPolicy passwordPolicy,
            KeycloakIdentityClient keycloakClient,
            UserAdminService userAdminService,
            AuthService authService,
            EmailCodeService emailCodeService,
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
        this.usernamePolicy = usernamePolicy;
        this.passwordPolicy = passwordPolicy;
        this.keycloakClient = keycloakClient;
        this.authService = authService;
        this.emailCodeService = emailCodeService;
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
     * @param localeCookieValue giá trị cookie {@code thi360_locale}; chỉ {@code vi}, {@code en} được ghi
     * @return đường dẫn chuyển hướng; phiên đã được đặt trong cookie của {@code response}
     */
    public String register(RegisterRequest request, String localeCookieValue, HttpServletResponse response) {
        ValidInput input = validate(request);
        String locale = "vi".equals(localeCookieValue) || "en".equals(localeCookieValue)
                ? localeCookieValue
                : UserAccount.DEFAULT_LOCALE;
        Role studentRole = resolveStudentRole();

        if (userAccountRepository.findActiveByUsernameNormalized(input.usernameNormalized()).isPresent()) {
            throw new ConflictException(ERROR_USERNAME_TAKEN);
        }
        if (userAccountRepository.findActiveByEmailNormalized(input.emailNormalized()).isPresent()) {
            throw new ConflictException(ERROR_EMAIL_TAKEN);
        }

        UUID provisionalId = Ids.newId();
        insertProvisional(provisionalId, input, studentRole, locale);

        UUID keycloakUserId = createKeycloakUser(provisionalId, input);
        try {
            keycloakClient.setPassword(keycloakUserId, request.password(), false);
        } catch (RuntimeException ex) {
            // Cả lỗi chính sách mật khẩu của Keycloak lẫn lỗi mạng: user Keycloak chưa có mật khẩu, bỏ cả hai phía.
            userAdminService.rollbackCreatedRegistration(provisionalId, keycloakUserId);
            throw ex;
        }

        try {
            activate(provisionalId, keycloakUserId, input, studentRole, locale);
        } catch (RuntimeException ex) {
            userAdminService.rollbackCreatedRegistration(provisionalId, keycloakUserId);
            throw ex;
        }

        // Câu hỏi mở 1 (tạm): tài khoản đã tạo xong mà tạo phiên lỗi thì giữ tài khoản, trả lỗi, người dùng đăng nhập lại.
        UserAccount account = userAccountRepository.findById(keycloakUserId)
                .orElseThrow(() -> new IllegalStateException("Registered account missing: " + keycloakUserId));
        try {
            return authService.openSessionForRegisteredAccount(
                    account, request.password(), request.callbackUrl(), locale, response);
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("event", "register.session_failed")
                    .addKeyValue("userId", keycloakUserId.toString())
                    .log("Account registered but opening the session failed; the user must log in");
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    private record ValidInput(
            String username,
            String usernameNormalized,
            String fullName,
            String email,
            String emailNormalized,
            String termsVersion) {
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
        // Câu hỏi mở 7 (tạm): lưu đúng giá trị gửi lên, không so với bản hiện hành.
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
                input.username(), input.email(), names[0], names[1], null, true, false, false);
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

    /** Nâng dòng tạm lên ACTIVE, gán vai trò, ghi nhật ký và xếp thư OTP trong một transaction (TM 5.1). */
    private void activate(UUID provisionalId, UUID keycloakUserId, ValidInput input, Role studentRole, String locale) {
        Instant activatedAt = Instant.now(clock);
        transactionTemplate.executeWithoutResult(status -> {
            int updated = userAccountRepository.promoteProvisionedUser(
                    provisionalId,
                    keycloakUserId,
                    UserStatus.PROVISIONING.code(),
                    UserStatus.ACTIVE.code(),
                    activatedAt,
                    null);
            if (updated != 1) {
                throw new IllegalStateException("Expected 1 promoted user row, got " + updated);
            }
            UserAccount account = userAccountRepository.findById(keycloakUserId)
                    .orElseThrow(() -> new IllegalStateException("Promoted user not found: " + keycloakUserId));
            userIdentityRepository.save(new UserIdentity(
                    Ids.newId(),
                    keycloakUserId,
                    IdentityProvider.LOCAL,
                    keycloakUserId.toString(),
                    account.getEmailNormalized(),
                    activatedAt));
            userRoleRepository.saveAndFlush(new UserRole(keycloakUserId, studentRole.getId(), null, activatedAt));

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("code", account.getCode());
            after.put("username", account.getUsername());
            after.put("fullName", account.getFullName());
            after.put("email", account.getEmail());
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

            String otp = emailCodeService.issue(keycloakUserId, EmailCodePurpose.VERIFY, account.getEmail());
            mailQueue.enqueue(
                    MailKind.EMAIL_VERIFY,
                    account.getEmail(),
                    account.getPreferredLocale(),
                    Map.of("fullName", account.getFullName(), "code", otp),
                    keycloakUserId);

            log.atInfo()
                    .addKeyValue("event", "user.self_registered")
                    .addKeyValue("userId", keycloakUserId.toString())
                    .addKeyValue("code", account.getCode())
                    .log("User account self-registered");
        });
    }
}
