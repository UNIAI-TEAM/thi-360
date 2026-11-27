package vn.unicom.thi360.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.AccountDeletionBlocker;
import vn.unicom.thi360.identity.api.AccountDeletionCheckResponse;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.EmailCodeRepository;
import vn.unicom.thi360.identity.infrastructure.ImportRowRepository;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.PasswordResetTokenRepository;
import vn.unicom.thi360.identity.infrastructure.PendingRegistrationRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.spi.ActiveAttemptChecker;
import vn.unicom.thi360.identity.spi.PendingGradingChecker;
import vn.unicom.thi360.identity.spi.SelfDeletionOrderChecker;
import vn.unicom.thi360.identity.spi.StudentLearningProvider;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.api.UnauthenticatedException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.Realm;

@Service
public class AccountSelfDeletionService {

    public static final String SELF_DELETED_REASON = "SELF_DELETED";

    private static final Logger log = LoggerFactory.getLogger(AccountSelfDeletionService.class);
    private static final String MODULE = "IDENTITY";
    private static final String TARGET_TYPE = "USER";
    private static final String AUDIT_ACTION = "USER_SELF_DELETED";
    private static final String ERROR_NOT_ALLOWED = "ACCOUNT_DELETION_NOT_ALLOWED";

    private final UserAccountRepository userAccountRepository;
    private final ImportRowRepository importRowRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final EmailCodeRepository emailCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final List<ActiveAttemptChecker> activeAttemptCheckers;
    private final List<PendingGradingChecker> pendingGradingCheckers;
    private final List<SelfDeletionOrderChecker> orderCheckers;
    private final List<StudentLearningProvider> studentLearningProviders;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final UserAdminService userAdminService;
    private final AvatarService avatarService;
    private final KeycloakIdentityClient keycloakClient;
    private final MailQueue mailQueue;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Object[] userLocks;

    public AccountSelfDeletionService(
            UserAccountRepository userAccountRepository,
            ImportRowRepository importRowRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            EmailCodeRepository emailCodeRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserIdentityRepository userIdentityRepository,
            List<ActiveAttemptChecker> activeAttemptCheckers,
            List<PendingGradingChecker> pendingGradingCheckers,
            List<SelfDeletionOrderChecker> orderCheckers,
            List<StudentLearningProvider> studentLearningProviders,
            CurrentPasswordVerifier currentPasswordVerifier,
            UserAdminService userAdminService,
            AvatarService avatarService,
            KeycloakIdentityClient keycloakClient,
            MailQueue mailQueue,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.importRowRepository = Objects.requireNonNull(importRowRepository);
        this.pendingRegistrationRepository = Objects.requireNonNull(pendingRegistrationRepository);
        this.emailCodeRepository = Objects.requireNonNull(emailCodeRepository);
        this.passwordResetTokenRepository = Objects.requireNonNull(passwordResetTokenRepository);
        this.userIdentityRepository = Objects.requireNonNull(userIdentityRepository);
        this.activeAttemptCheckers = activeAttemptCheckers == null ? List.of() : List.copyOf(activeAttemptCheckers);
        this.pendingGradingCheckers = pendingGradingCheckers == null ? List.of() : List.copyOf(pendingGradingCheckers);
        this.orderCheckers = orderCheckers == null ? List.of() : List.copyOf(orderCheckers);
        this.studentLearningProviders = studentLearningProviders == null
                ? List.of()
                : List.copyOf(studentLearningProviders);
        this.currentPasswordVerifier = Objects.requireNonNull(currentPasswordVerifier);
        this.userAdminService = Objects.requireNonNull(userAdminService);
        this.avatarService = Objects.requireNonNull(avatarService);
        this.keycloakClient = Objects.requireNonNull(keycloakClient);
        this.mailQueue = Objects.requireNonNull(mailQueue);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
        this.userLocks = new Object[256];
        for (int i = 0; i < this.userLocks.length; i++) {
            this.userLocks[i] = new Object();
        }
    }

    @Transactional(readOnly = true)
    public AccountDeletionCheckResponse check(CurrentUser current) {
        UUID userId = requireStudentId(current);
        UserAccount account = userAccountRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new UnauthenticatedException("UNAUTHENTICATED", null));
        List<AccountDeletionBlocker> blockers = collectBlockers(account.getId());
        int unusedAttempts = countUnusedAttempts(account.getId());
        boolean pendingGrading = pendingGradingCheckers.stream()
                .anyMatch(checker -> checker.hasPendingGrading(account.getId()));
        return new AccountDeletionCheckResponse(blockers.isEmpty(), blockers, unusedAttempts, pendingGrading);
    }

    public void delete(CurrentUser current, String currentPassword) {
        UUID userId = requireStudentId(current);
        Boolean deletedNow;
        synchronized (lockFor(userId)) {
            UserAccount snapshot = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new UnauthenticatedException("UNAUTHENTICATED", null));
            if (snapshot.isDeleted()) {
                return;
            }

            try {
                currentPasswordVerifier.verify(snapshot.getUsername(), currentPassword);
            } catch (ValidationException ex) {
                if ("WRONG_OLD_PASSWORD".equals(ex.getCode()) && isDeletedInDb(userId)) {
                    return;
                }
                throw ex;
            }

            deletedNow = transactionTemplate.execute(status -> executeDeletionInTx(userId));
        }
        if (Boolean.TRUE.equals(deletedNow)) {
            try {
                keycloakClient.logoutAllSessions(userId);
            } catch (RuntimeException ex) {
                log.atWarn()
                        .setCause(ex)
                        .addKeyValue("userId", userId.toString())
                        .log("Keycloak logoutAllSessions failed after self-deletion; continuing cleanup");
            }
            userAdminService.cleanupDeletedUserInKeycloak(userId);
        }
    }

    private boolean executeDeletionInTx(UUID userId) {
        jdbcTemplate.query("SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE", rs -> {}, userId);
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null || user.isDeleted()) {
            return false;
        }

        List<AccountDeletionBlocker> blockers = collectBlockers(userId);
        if (!blockers.isEmpty()) {
            throw new AccountDeletionBlockedException(blockers);
        }

        String oldUsernameNormalized = user.getUsernameNormalized();
        String oldEmail = user.getEmail();
        String oldEmailNormalized = user.getEmailNormalized();
        String oldFullName = user.getFullName();
        String oldPreferredLocale = user.getPreferredLocale();
        UUID importRowId = user.getImportRowId();
        UserStatus oldStatus = user.getStatus();
        Instant now = Instant.now(clock);

        if (oldEmail != null && !oldEmail.isBlank()) {
            mailQueue.enqueue(
                    MailKind.ACCOUNT_DELETED,
                    oldEmail,
                    oldPreferredLocale,
                    Map.of("fullName", oldFullName),
                    userId);
        }

        pendingRegistrationRepository.deleteByUsernameNormalized(oldUsernameNormalized);
        if (oldEmailNormalized != null) {
            pendingRegistrationRepository.deleteByEmailNormalized(oldEmailNormalized);
        }

        String anonymous = user.anonymizeForSelfDeletion(now);
        anonymizeImportRows(userId, importRowId, anonymous);
        userAdminService.softDeleteAndPublish(user, SELF_DELETED_REASON, userId, now);

        avatarService.removeInCurrentTransaction(userId);
        emailCodeRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        userIdentityRepository.deleteByUserId(userId);

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (oldStatus != UserStatus.INACTIVE) {
            before.put("status", oldStatus.name());
            after.put("status", UserStatus.INACTIVE.name());
        }
        before.put("deletedAt", null);
        after.put("deletedAt", now.toString());
        after.put("deleteReason", SELF_DELETED_REASON);
        auditRecorder.record(AuditEvent.builder(MODULE, AUDIT_ACTION)
                .actor(userId, anonymous, List.of(), AuditActorType.USER)
                .target(TARGET_TYPE, userId.toString(), anonymous)
                .before(before)
                .after(after)
                .reason(SELF_DELETED_REASON)
                .build());
        return true;
    }

    private void anonymizeImportRows(UUID userId, UUID importRowId, String anonymous) {
        Map<UUID, ImportRow> rowsById = new LinkedHashMap<>();
        if (importRowId != null) {
            importRowRepository.findById(importRowId).ifPresent(row -> rowsById.put(row.getId(), row));
        }
        for (ImportRow row : importRowRepository.findAllByCreatedUserIdOrProvisionalUserId(userId)) {
            rowsById.put(row.getId(), row);
        }
        if (rowsById.isEmpty()) {
            return;
        }
        for (ImportRow row : rowsById.values()) {
            row.anonymizeForSelfDeletion(anonymous);
        }
        importRowRepository.saveAllAndFlush(rowsById.values());
    }

    private List<AccountDeletionBlocker> collectBlockers(UUID userId) {
        List<AccountDeletionBlocker> blockers = new ArrayList<>(3);
        if (activeAttemptCheckers.stream().anyMatch(checker -> checker.hasActiveAttempt(userId))) {
            blockers.add(AccountDeletionBlocker.ATTEMPT_IN_PROGRESS);
        }
        if (orderCheckers.stream().anyMatch(checker -> checker.hasPaidUnfulfilledOrder(userId))) {
            blockers.add(AccountDeletionBlocker.ORDER_BEING_CHECKED);
        }
        if (orderCheckers.stream().anyMatch(checker -> checker.hasPendingPaymentOrder(userId))) {
            blockers.add(AccountDeletionBlocker.ORDER_PENDING_PAYMENT);
        }
        return List.copyOf(blockers);
    }

    private int countUnusedAttempts(UUID userId) {
        int total = 0;
        for (StudentLearningProvider provider : studentLearningProviders) {
            StudentLearningProvider.StudentLearningData data = provider.loadForUser(userId);
            if (data == null) {
                continue;
            }
            for (StudentLearningProvider.EntitlementItem item : data.entitlements()) {
                if (item != null) {
                    total += item.attemptsLeft();
                }
            }
        }
        return total;
    }

    private boolean isDeletedInDb(UUID userId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            List<Timestamp> rows = jdbcTemplate.query(
                    "SELECT deleted_at FROM identity.user_account WHERE id = ? FOR SHARE",
                    (rs, rowNum) -> rs.getTimestamp(1),
                    userId);
            return !rows.isEmpty() && rows.getFirst() != null;
        }));
    }

    private Object lockFor(UUID userId) {
        return userLocks[Math.floorMod(userId.hashCode(), userLocks.length)];
    }

    private static UUID requireStudentId(CurrentUser current) {
        if (current == null) {
            throw new UnauthenticatedException("UNAUTHENTICATED", null);
        }
        if (current.realm() != Realm.APP || current.access() != Access.EXAM) {
            throw new ForbiddenException(ERROR_NOT_ALLOWED, null);
        }
        return current.id();
    }
}
