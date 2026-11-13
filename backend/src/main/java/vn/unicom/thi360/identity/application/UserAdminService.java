package vn.unicom.thi360.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.BulkUserActionItemResult;
import vn.unicom.thi360.identity.api.BulkUserActionPreviewResponse;
import vn.unicom.thi360.identity.api.BulkUserActionRequest;
import vn.unicom.thi360.identity.api.BulkUserActionResponse;
import vn.unicom.thi360.identity.api.BulkUserItemOutcome;
import vn.unicom.thi360.identity.api.BulkUserSkippedItem;
import vn.unicom.thi360.identity.api.CreateUserRequest;
import vn.unicom.thi360.identity.api.DeactivateUserRequest;
import vn.unicom.thi360.identity.api.DeactivationCheckResponse;
import vn.unicom.thi360.identity.api.DeletionCheckResponse;
import vn.unicom.thi360.identity.api.ResetPasswordRequest;
import vn.unicom.thi360.identity.api.UpdateUserRequest;
import vn.unicom.thi360.identity.api.UserBulkAction;
import vn.unicom.thi360.identity.api.UserDetailResponse;
import vn.unicom.thi360.identity.api.UserRoleSummary;
import vn.unicom.thi360.identity.api.UserSummaryResponse;
import vn.unicom.thi360.identity.api.UsernameAvailabilityResponse;
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
import vn.unicom.thi360.identity.spi.AccountDeletionGuard;
import vn.unicom.thi360.identity.spi.ActiveAttemptChecker;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.identity.spi.UserDeleted;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotencyService;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.SessionStore;
import vn.unicom.thi360.shared.web.AppMessages;

@Service
public class UserAdminService {

    public static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "username", "fullName", "lastLoginAt");
    public static final int MAX_BULK_USERS = 500;

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final String MODULE = "IDENTITY";
    private static final String TARGET_TYPE = "USER";
    private static final String STUDENT_ROLE_CODE_NORMALIZED = "student";
    private static final String ERROR_USERNAME_TAKEN = "USERNAME_TAKEN";
    private static final String ERROR_EMAIL_TAKEN = "EMAIL_TAKEN";
    private static final String ERROR_ROLE_INACTIVE = "ROLE_INACTIVE";
    private static final String ERROR_CLASS_NOT_SUPPORTED = "CLASS_NOT_SUPPORTED";
    private static final String ERROR_SELF_DEACTIVATE = "SELF_DEACTIVATE";
    private static final String ERROR_SELF_DELETE = "SELF_DELETE";
    private static final String ERROR_USER_HAS_TRANSACTIONS = "USER_HAS_TRANSACTIONS";
    private static final String BULK_IDEMPOTENCY_ENDPOINT = "POST:/api/v1/admin/users/bulk-actions";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private final UserAccountRepository userAccountRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UsernamePolicy usernamePolicy;
    private final PasswordPolicy passwordPolicy;
    private final PrivilegeGuard privilegeGuard;
    private final AuthzSnapshotService authzSnapshotService;
    private final SessionStore sessionStore;
    private final IdempotencyService idempotencyService;
    private final OutboxPublisher outboxPublisher;
    private final List<AccountDeletionGuard> deletionGuards;
    private final List<ActiveAttemptChecker> activeAttemptCheckers;
    private final KeycloakIdentityClient keycloakClient;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final AppMessages messages;

    public UserAdminService(
            UserAccountRepository userAccountRepository,
            UserIdentityRepository userIdentityRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UsernamePolicy usernamePolicy,
            PasswordPolicy passwordPolicy,
            PrivilegeGuard privilegeGuard,
            AuthzSnapshotService authzSnapshotService,
            SessionStore sessionStore,
            IdempotencyService idempotencyService,
            OutboxPublisher outboxPublisher,
            List<AccountDeletionGuard> deletionGuards,
            List<ActiveAttemptChecker> activeAttemptCheckers,
            KeycloakIdentityClient keycloakClient,
            CurrentUserProvider currentUserProvider,
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
        this.privilegeGuard = privilegeGuard;
        this.authzSnapshotService = authzSnapshotService;
        this.sessionStore = sessionStore;
        this.idempotencyService = idempotencyService;
        this.outboxPublisher = outboxPublisher;
        this.deletionGuards = deletionGuards == null ? List.of() : List.copyOf(deletionGuards);
        this.activeAttemptCheckers = activeAttemptCheckers == null ? List.of() : List.copyOf(activeAttemptCheckers);
        this.keycloakClient = keycloakClient;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.messages = messages;
    }
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> listUsers(
            String q,
            UserSource source,
            UserStatus status,
            UUID roleId,
            UUID classId,
            Boolean neverLoggedIn,
            Boolean includeDeleted,
            PageQuery pageQuery) {
        if (classId != null) {
            throw new ValidationException(
                    ERROR_CLASS_NOT_SUPPORTED,
                    null,
                    List.of(new FieldError("classId", ERROR_CLASS_NOT_SUPPORTED, messages.get(ERROR_CLASS_NOT_SUPPORTED))));
        }

        StringBuilder where = new StringBuilder("u.status <> ?");
        List<Object> params = new ArrayList<>();
        params.add(UserStatus.PROVISIONING.code());

        if (!Boolean.TRUE.equals(includeDeleted)) {
            where.append(" AND u.deleted_at IS NULL");
        }
        if (source != null) {
            where.append(" AND u.source = ?");
            params.add(source.code());
        }
        if (status != null) {
            where.append(" AND u.status = ?");
            params.add(status.code());
        }
        if (roleId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM identity.user_role ur WHERE ur.user_id = u.id AND ur.role_id = ?)");
            params.add(roleId);
        }
        if (Boolean.TRUE.equals(neverLoggedIn)) {
            where.append(" AND u.last_login_at IS NULL");
        } else if (Boolean.FALSE.equals(neverLoggedIn)) {
            where.append(" AND u.last_login_at IS NOT NULL");
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            where.append(
                    " AND (LOWER(u.code) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(u.username) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(u.full_name) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(u.email, '')) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(u.phone, '')) LIKE ? ESCAPE '\\')");
            for (int i = 0; i < 5; i++) {
                params.add(pattern);
            }
        }

        String countSql = "SELECT COUNT(*) FROM identity.user_account u WHERE " + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            return PageResponse.from(new PageImpl<>(List.of(), pageQuery.toPageable(), 0L));
        }

        String orderBy = buildOrderBy(pageQuery.sort());
        String dataSql =
                """
                SELECT u.id,
                       u.code,
                       u.username,
                       u.full_name,
                       u.email,
                       u.phone,
                       u.source,
                       u.status,
                       u.must_change_password,
                       u.last_login_at,
                       u.deleted_at,
                       u.version,
                       u.created_at,
                       u.updated_at
                FROM identity.user_account u
                WHERE\s"""
                        + where
                        + " "
                        + orderBy
                        + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageQuery.size());
        dataParams.add((long) pageQuery.page() * pageQuery.size());

        List<UserRow> rows = jdbcTemplate.query(dataSql, (rs, rowNum) -> mapUserRow(rs), dataParams.toArray());
        List<UUID> userIds = rows.stream().map(UserRow::id).toList();
        Map<UUID, List<UserRoleSummary>> rolesByUserId = loadRolesForUsers(userIds);

        List<UserSummaryResponse> items = rows.stream()
                .map(row -> new UserSummaryResponse(
                        row.id(),
                        row.code(),
                        row.username(),
                        row.fullName(),
                        row.email(),
                        row.phone(),
                        row.source(),
                        row.status(),
                        row.mustChangePassword(),
                        row.lastLoginAt(),
                        row.deletedAt(),
                        rolesByUserId.getOrDefault(row.id(), List.of()),
                        row.version(),
                        row.createdAt(),
                        row.updatedAt()))
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageQuery.toPageable(), totalCount));
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUser(UUID userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .filter(u -> u.getStatus() != UserStatus.PROVISIONING)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        List<UserRoleSummary> roles = loadRolesForUsers(List.of(userId)).getOrDefault(userId, List.of());
        return toDetailResponse(user, roles);
    }

    @Transactional(readOnly = true)
    public UsernameAvailabilityResponse checkUsernameAvailability(String username) {
        UsernamePolicy.CheckResult result = usernamePolicy.check(username);
        if (!result.valid()) {
            return new UsernameAvailabilityResponse(false, result.reasonCode());
        }
        if (userAccountRepository.findActiveByUsernameNormalized(result.normalized()).isPresent()) {
            return new UsernameAvailabilityResponse(false, ERROR_USERNAME_TAKEN);
        }
        return new UsernameAvailabilityResponse(true, null);
    }

    public UserDetailResponse createUser(CreateUserRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();

        if (request.classIds() != null && !request.classIds().isEmpty()) {
            throw new ValidationException(
                    ERROR_CLASS_NOT_SUPPORTED,
                    null,
                    List.of(new FieldError("classIds", ERROR_CLASS_NOT_SUPPORTED, messages.get(ERROR_CLASS_NOT_SUPPORTED))));
        }

        String trimmedUsername = usernamePolicy.validate("username", request.username());
        passwordPolicy.validate("password", request.password(), trimmedUsername);
        String trimmedFullName = validateRequiredFullName(request.fullName());
        String trimmedEmail = normalizeAndValidateEmail(request.email());
        String trimmedPhone = normalizeAndValidatePhone(request.phone());
        boolean mustChangePassword = Boolean.TRUE.equals(request.mustChangePassword());

        List<Role> targetRoles = resolveTargetRoles(actor, request.roleIds());

        String normalizedUsername = Normalizer.normalize(trimmedUsername);
        String normalizedEmail = Normalizer.normalize(trimmedEmail);
        if (userAccountRepository.findActiveByUsernameNormalized(normalizedUsername).isPresent()) {
            throw new ConflictException(ERROR_USERNAME_TAKEN);
        }
        if (normalizedEmail != null && userAccountRepository.findActiveByEmailNormalized(normalizedEmail).isPresent()) {
            throw new ConflictException(ERROR_EMAIL_TAKEN);
        }

        UUID provisionalId = Ids.newId();
        Instant now = Instant.now(clock);
        String roleIdsIntent = targetRoles.stream()
                .map(role -> role.getId().toString())
                .collect(Collectors.joining(","));
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
                String code = String.format("U%06d", seq == null ? 1L : seq);
                UserAccount provisional = new UserAccount(
                        provisionalId,
                        code,
                        trimmedUsername,
                        trimmedFullName,
                        trimmedEmail,
                        trimmedPhone,
                        UserSource.ADMIN_CREATED,
                        UserStatus.PROVISIONING,
                        mustChangePassword,
                        now,
                        actor.id());
                provisional.setProvisioningRoleIds(roleIdsIntent);
                userAccountRepository.saveAndFlush(provisional);
            });
        } catch (DataIntegrityViolationException ex) {
            throw mapUniqueConstraintViolation(ex);
        }

        String[] names = splitFullName(trimmedFullName);
        UUID keycloakUserId;
        try {
            keycloakUserId = createKeycloakUserWithPendingCleanupRetry(
                    provisionalId,
                    trimmedUsername,
                    normalizedUsername,
                    trimmedEmail,
                    normalizedEmail,
                    names[0],
                    names[1]);
        } catch (ConflictException | ValidationException ex) {
            deleteProvisionalUserQuietly(provisionalId);
            throw ex;
        } catch (ExternalServiceException ex) {
            if (userAccountRepository.findById(provisionalId).isPresent()) {
                log.atError()
                        .addKeyValue("event", "user.create.keycloak_failed")
                        .addKeyValue("provisionalUserId", provisionalId.toString())
                        .log("Keycloak createUser failed while user remains PROVISIONING");
            }
            throw ex;
        }
        try {
            keycloakClient.setPassword(keycloakUserId, request.password(), false);
        } catch (ValidationException ex) {
            if (deleteKeycloakUserQuietly(keycloakUserId)) {
                deleteProvisionalUserQuietly(provisionalId);
            } else {
                markProvisionalIdpCleanupPendingQuietly(provisionalId);
            }
            throw ex;
        } catch (ExternalServiceException ex) {
            log.atError()
                    .addKeyValue("event", "user.create.set_password_failed")
                    .addKeyValue("provisionalUserId", provisionalId.toString())
                    .addKeyValue("keycloakUserId", keycloakUserId.toString())
                    .log("Keycloak setPassword failed while user remains PROVISIONING");
            throw ex;
        }

        Instant activatedAt = Instant.now(clock);
        try {
            return transactionTemplate.execute(status -> {
                int updated = userAccountRepository.promoteProvisionedUser(
                        provisionalId,
                        keycloakUserId,
                        UserStatus.PROVISIONING.code(),
                        UserStatus.ACTIVE.code(),
                        activatedAt,
                        actor.id());
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
                List<UserRole> assignments = targetRoles.stream()
                        .map(role -> new UserRole(keycloakUserId, role.getId(), actor.id(), activatedAt))
                        .toList();
                userRoleRepository.saveAll(assignments);
                userRoleRepository.flush();

                Map<String, Object> after = new LinkedHashMap<>();
                after.put("code", account.getCode());
                after.put("username", account.getUsername());
                after.put("fullName", account.getFullName());
                after.put("email", account.getEmail());
                after.put("phone", account.getPhone());
                after.put("source", account.getSource().name());
                after.put("status", account.getStatus().name());
                after.put("mustChangePassword", account.isMustChangePassword());
                after.put("roleCodes", targetRoles.stream().map(Role::getCode).toList());

                recordAudit(actor, "USER_CREATED", keycloakUserId, account.getUsername(), null, after, null);

                log.atInfo()
                        .addKeyValue("event", "user.created")
                        .addKeyValue("userId", keycloakUserId.toString())
                        .addKeyValue("code", account.getCode())
                        .log("User account created");

                return toDetailResponse(account, toRoleSummaries(targetRoles));
            });
        } catch (RuntimeException ex) {
            if (deleteKeycloakUserQuietly(keycloakUserId)) {
                deleteProvisionalUserQuietly(provisionalId);
            } else {
                markProvisionalIdpCleanupPendingQuietly(provisionalId);
            }
            throw ex;
        }
    }

    @Transactional
    public UserDetailResponse updateUser(UUID userId, UpdateUserRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();

        UserAccount user = lockActiveManageableUser(actor, userId);

        if (!Objects.equals(user.getVersion(), request.version())) {
            throw new VersionConflictException();
        }

        String validatedFullName = request.fullName() == null ? null : validateRequiredFullName(request.fullName());
        String validatedEmail = request.email() == null ? null : normalizeAndValidateEmail(request.email());
        String validatedPhone = request.phone() == null ? null : normalizeAndValidatePhone(request.phone());

        String oldFullName = user.getFullName();
        String oldEmail = user.getEmail();
        String oldEmailNormalized = user.getEmailNormalized();
        Instant oldEmailVerifiedAt = user.getEmailVerifiedAt();
        String oldPhone = user.getPhone();

        if (request.email() != null) {
            String newEmailNormalized = Normalizer.normalize(validatedEmail);
            if (newEmailNormalized != null && !newEmailNormalized.equals(oldEmailNormalized)) {
                userAccountRepository.findActiveByEmailNormalized(newEmailNormalized)
                        .filter(existing -> !existing.getId().equals(userId))
                        .ifPresent(existing -> {
                            throw new ConflictException(ERROR_EMAIL_TAKEN);
                        });
            }
        }

        // Preserve empty-string intent for clearing email/phone when non-null blank was supplied
        String emailArg = request.email() == null ? null : (validatedEmail == null ? "" : validatedEmail);
        String phoneArg = request.phone() == null ? null : (validatedPhone == null ? "" : validatedPhone);

        Instant now = Instant.now(clock);
        boolean changed = user.updateProfile(validatedFullName, emailArg, phoneArg, now, actor.id());
        if (changed) {
            try {
                user = userAccountRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException ex) {
                throw mapUniqueConstraintViolation(ex);
            }

            boolean fullNameChanged = !Objects.equals(oldFullName, user.getFullName());
            boolean emailChanged = !Objects.equals(oldEmail, user.getEmail());
            boolean phoneChanged = !Objects.equals(oldPhone, user.getPhone());
            boolean emailVerifiedCleared = !Objects.equals(oldEmailVerifiedAt, user.getEmailVerifiedAt());

            if (fullNameChanged || emailChanged) {
                String[] names = splitFullName(user.getFullName());
                keycloakClient.updateUserProfile(
                        userId,
                        emailChanged ? (user.getEmail() == null ? "" : user.getEmail()) : null,
                        fullNameChanged ? (names[0] == null ? "" : names[0]) : null,
                        fullNameChanged ? (names[1] == null ? "" : names[1]) : null,
                        false);
            }

            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            if (fullNameChanged) {
                before.put("fullName", oldFullName);
                after.put("fullName", user.getFullName());
            }
            if (emailChanged) {
                before.put("email", oldEmail);
                after.put("email", user.getEmail());
            }
            if (emailVerifiedCleared) {
                before.put("emailVerifiedAt", oldEmailVerifiedAt == null ? null : oldEmailVerifiedAt.toString());
                after.put("emailVerifiedAt", null);
            }
            if (phoneChanged) {
                before.put("phone", oldPhone);
                after.put("phone", user.getPhone());
            }

            recordAudit(actor, "USER_UPDATED", userId, user.getUsername(), before, after, null);
        }

        List<UserRoleSummary> roles = loadRolesForUsers(List.of(userId)).getOrDefault(userId, List.of());
        return toDetailResponse(user, roles);
    }

    public UserDetailResponse deactivateUser(UUID userId, DeactivateUserRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        if (actor.realm() == Realm.APP && actor.id().equals(userId)) {
            throw new ConflictException(ERROR_SELF_DEACTIVATE);
        }
        String trimmedReason = validateReason("reason", request.reason(), "validation.user.deactivateReason.required");
        Boolean[] restoreKcEnabledOnRollback = {null};
        try {
            return transactionTemplate.execute(status -> {
                UserAccount user = lockActiveManageableUser(actor, userId);

                UserStatus oldStatus = user.getStatus();
                String oldReason = user.getInactiveReason();
                Instant now = Instant.now(clock);

                revokeAllSessionsFailClosed(userId);
                keycloakClient.setEnabled(userId, false);
                restoreKcEnabledOnRollback[0] = oldStatus == UserStatus.ACTIVE;

                user.deactivate(trimmedReason, now, actor.id());
                user = userAccountRepository.saveAndFlush(user);

                Map<String, Object> before = new LinkedHashMap<>();
                Map<String, Object> after = new LinkedHashMap<>();
                if (oldStatus != UserStatus.INACTIVE) {
                    before.put("status", oldStatus.name());
                    after.put("status", UserStatus.INACTIVE.name());
                }
                if (!Objects.equals(oldReason, user.getInactiveReason())) {
                    before.put("inactiveReason", oldReason);
                    after.put("inactiveReason", user.getInactiveReason());
                }
                if (!after.isEmpty()) {
                    recordAudit(actor, "USER_DEACTIVATED", userId, user.getUsername(), before, after, trimmedReason);
                }

                List<UserRoleSummary> roles = loadRolesForUsers(List.of(userId)).getOrDefault(userId, List.of());
                return toDetailResponse(user, roles);
            });
        } catch (RuntimeException ex) {
            if (restoreKcEnabledOnRollback[0] != null) {
                compensateKeycloakEnabled(userId, restoreKcEnabledOnRollback[0]);
            }
            throw ex;
        }
    }

    public UserDetailResponse activateUser(UUID userId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Boolean[] restoreKcEnabledOnRollback = {null};
        try {
            return transactionTemplate.execute(status -> {
                UserAccount user = lockActiveManageableUser(actor, userId);

                UserStatus oldStatus = user.getStatus();
                String oldReason = user.getInactiveReason();
                Instant now = Instant.now(clock);

                keycloakClient.setEnabled(userId, true);
                restoreKcEnabledOnRollback[0] = oldStatus == UserStatus.ACTIVE;

                user.activate(now, actor.id());
                user = userAccountRepository.saveAndFlush(user);

                Map<String, Object> before = new LinkedHashMap<>();
                Map<String, Object> after = new LinkedHashMap<>();
                if (oldStatus != UserStatus.ACTIVE) {
                    before.put("status", oldStatus.name());
                    after.put("status", UserStatus.ACTIVE.name());
                }
                if (oldReason != null) {
                    before.put("inactiveReason", oldReason);
                    after.put("inactiveReason", null);
                }
                if (!after.isEmpty()) {
                    recordAudit(actor, "USER_ACTIVATED", userId, user.getUsername(), before, after, null);
                }

                List<UserRoleSummary> roles = loadRolesForUsers(List.of(userId)).getOrDefault(userId, List.of());
                return toDetailResponse(user, roles);
            });
        } catch (RuntimeException ex) {
            if (restoreKcEnabledOnRollback[0] != null) {
                compensateKeycloakEnabled(userId, restoreKcEnabledOnRollback[0]);
            }
            throw ex;
        }
    }

    public UserDetailResponse resetPassword(UUID userId, ResetPasswordRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        boolean mustChange = Boolean.TRUE.equals(request.mustChangePassword());
        String trimmedReason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (trimmedReason != null && trimmedReason.length() > 500) {
            throw new ValidationException(List.of(
                    new FieldError("reason", "SIZE", messages.get("validation.reason.size"))));
        }

        transactionTemplate.executeWithoutResult(status -> {
            UserAccount user = lockActiveManageableUser(actor, userId);
            passwordPolicy.validate("password", request.password(), user.getUsername());
        });

        revokeAllSessionsFailClosed(userId);

        record ResetPrep(boolean oldMustChange, String username, UserDetailResponse response) {}
        ResetPrep prep = transactionTemplate.execute(status -> {
            UserAccount user = lockActiveManageableUser(actor, userId);
            boolean oldMustChange = user.isMustChangePassword();
            Instant now = Instant.now(clock);
            if (oldMustChange != mustChange) {
                user.updateMustChangePassword(mustChange, now, actor.id());
                user = userAccountRepository.saveAndFlush(user);
            }

            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            if (oldMustChange != mustChange) {
                before.put("mustChangePassword", oldMustChange);
                after.put("mustChangePassword", mustChange);
            }
            recordAudit(
                    actor,
                    "USER_PASSWORD_RESET",
                    userId,
                    user.getUsername(),
                    before.isEmpty() ? null : before,
                    after.isEmpty() ? null : after,
                    trimmedReason);

            List<UserRoleSummary> roles = loadRolesForUsers(List.of(userId)).getOrDefault(userId, List.of());
            return new ResetPrep(oldMustChange, user.getUsername(), toDetailResponse(user, roles));
        });

        try {
            keycloakClient.setPassword(userId, request.password(), false);
            return prep.response();
        } catch (RuntimeException ex) {
            String errorCode = switch (ex) {
                case ExternalServiceException ese -> ese.getCode();
                case ValidationException ve -> ve.getCode();
                default -> "IDENTITY_PROVIDER_ERROR";
            };
            try {
                requiresNewTx.executeWithoutResult(status -> {
                    jdbcTemplate.query(
                            "SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE",
                            rs -> {},
                            userId);
                    UserAccount user = userAccountRepository.findById(userId)
                            .filter(u -> !u.isDeleted())
                            .orElse(null);
                    if (user != null && user.isMustChangePassword() != prep.oldMustChange()) {
                        user.updateMustChangePassword(prep.oldMustChange(), Instant.now(clock), actor.id());
                        userAccountRepository.saveAndFlush(user);
                    }
                    Map<String, Object> compBefore = new LinkedHashMap<>();
                    Map<String, Object> compAfter = new LinkedHashMap<>();
                    if (mustChange != prep.oldMustChange()) {
                        compBefore.put("mustChangePassword", mustChange);
                        compAfter.put("mustChangePassword", prep.oldMustChange());
                    }
                    AuditActorType actorType = actor.realm() == Realm.MASTER
                            ? AuditActorType.PLATFORM
                            : AuditActorType.USER;
                    auditRecorder.record(AuditEvent.builder(MODULE, "USER_PASSWORD_RESET")
                            .target(TARGET_TYPE, userId.toString(), prep.username())
                            .before(compBefore.isEmpty() ? null : compBefore)
                            .after(compAfter.isEmpty() ? null : compAfter)
                            .reason(trimmedReason)
                            .outcome(AuditOutcome.FAILURE)
                            .errorCode(errorCode)
                            .actor(actor.id(), actor.username(), null, actorType)
                            .build());
                });
            } catch (RuntimeException compEx) {
                log.atError()
                        .setCause(compEx)
                        .addKeyValue("userId", userId.toString())
                        .addKeyValue("errorCode", errorCode)
                        .log("Failed to compensate resetPassword after Keycloak failure");
            }
            if (ex instanceof ExternalServiceException ese) {
                throw ese;
            }
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public void deleteUser(UUID userId, String reason) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        if (actor.realm() == Realm.APP && actor.id().equals(userId)) {
            throw new ConflictException(ERROR_SELF_DELETE);
        }
        String trimmedReason = validateReason("reason", reason, "validation.user.deleteReason.required");
        transactionTemplate.executeWithoutResult(status -> {
            UserAccount user = lockActiveManageableUser(actor, userId);
            List<AccountDeletionGuard.Blocker> blockers = collectDeletionBlockers(userId);
            if (!blockers.isEmpty()) {
                List<FieldError> fieldErrors = blockers.stream()
                        .map(b -> new FieldError(
                                b.source(),
                                b.code() == null || b.code().isBlank() ? ERROR_USER_HAS_TRANSACTIONS : b.code(),
                                b.message() == null || b.message().isBlank()
                                        ? messages.get(ERROR_USER_HAS_TRANSACTIONS)
                                        : b.message()))
                        .toList();
                throw new ConflictException(ERROR_USER_HAS_TRANSACTIONS, null, fieldErrors);
            }
            performSoftDeleteLocal(actor, user, trimmedReason, Instant.now(clock));
        });
        cleanupDeletedUserInKeycloak(userId);
    }

    @Transactional(readOnly = true)
    public DeletionCheckResponse checkDeletion(UUID userId) {
        requireExistingNonDeletedUser(userId);
        List<AccountDeletionGuard.Blocker> blockers = collectDeletionBlockers(userId);
        return new DeletionCheckResponse(blockers.isEmpty(), blockers);
    }

    @Transactional(readOnly = true)
    public DeactivationCheckResponse checkDeactivation(UUID userId) {
        requireExistingNonDeletedUser(userId);
        boolean activeAttempt = activeAttemptCheckers.stream().anyMatch(checker -> checker.hasActiveAttempt(userId));
        return new DeactivationCheckResponse(activeAttempt);
    }

    @Transactional(readOnly = true)
    public BulkUserActionPreviewResponse previewBulkActions(BulkUserActionRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        List<UUID> distinctIds = validateBulkUserIds(request.userIds());
        Map<UUID, UserAccount> accountsById = loadAccountsById(distinctIds);

        int affected = 0;
        List<BulkUserSkippedItem> skipped = new ArrayList<>();
        for (UUID uid : distinctIds) {
            BulkSkipDecision decision = evaluateBulkEligibility(actor, request.action(), uid, accountsById.get(uid));
            if (decision == null) {
                affected++;
            } else {
                UserAccount ua = accountsById.get(uid);
                skipped.add(new BulkUserSkippedItem(
                        uid,
                        ua == null ? null : ua.getUsername(),
                        decision.code(),
                        localizeReason(decision.messageKey(), decision.messageArgs(), decision.code())));
            }
        }
        return new BulkUserActionPreviewResponse(distinctIds.size(), affected, List.copyOf(skipped));
    }

    public BulkUserActionResponse applyBulkActions(BulkUserActionRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException(List.of(
                    new FieldError("Idempotency-Key", "IDEMPOTENCY_KEY_REQUIRED", messages.get("validation.idempotencyKey.required"))));
        }
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        List<UUID> distinctIds = validateBulkUserIds(request.userIds());
        String trimmedReason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if ((request.action() == UserBulkAction.DEACTIVATE || request.action() == UserBulkAction.DELETE)
                && trimmedReason == null) {
            throw new ValidationException(List.of(
                    new FieldError("reason", "REASON_REQUIRED", messages.get("validation.user.bulkReason.required"))));
        }

        String scope = BULK_IDEMPOTENCY_ENDPOINT + ":" + actor.realm().name() + ":" + actor.id();
        String digest = computeBulkDigest(request.action(), distinctIds, trimmedReason);

        StoredBulkUserActionResponse stored = idempotencyService.executeOnce(
                idempotencyKey.trim(),
                scope,
                digest,
                StoredBulkUserActionResponse.class,
                () -> executeBulkItems(actor, request.action(), distinctIds, trimmedReason));
        return localizeBulkResponse(stored);
    }

    private StoredBulkUserActionResponse executeBulkItems(
            CurrentUser actor, UserBulkAction action, List<UUID> distinctIds, String trimmedReason) {
        UUID batchId = Ids.newId();
        Instant now = Instant.now(clock);

        List<StoredBulkUserActionItemResult> results = new ArrayList<>(distinctIds.size());
        int succeeded = 0;
        int skipped = 0;
        int failed = 0;

        for (UUID uid : distinctIds) {
            StoredBulkUserActionItemResult item = executeSingleBulkItem(actor, action, uid, trimmedReason, now);
            results.add(item);
            switch (item.outcome()) {
                case SUCCEEDED -> succeeded++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }

        return new StoredBulkUserActionResponse(
                batchId, action, distinctIds.size(), succeeded, skipped, failed, List.copyOf(results));
    }

    private StoredBulkUserActionItemResult executeSingleBulkItem(
            CurrentUser actor, UserBulkAction action, UUID uid, String trimmedReason, Instant now) {
        String[] knownUsername = {null};
        Boolean[] restoreKcEnabledOnRollback = {null};
        try {
            StoredBulkUserActionItemResult committed = requiresNewTx.execute(status -> {
                jdbcTemplate.query(
                        "SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE",
                        rs -> {},
                        uid);
                UserAccount user = userAccountRepository.findById(uid).orElse(null);
                knownUsername[0] = user == null ? null : user.getUsername();
                BulkSkipDecision skip = evaluateBulkEligibility(actor, action, uid, user);
                if (skip != null) {
                    return new StoredBulkUserActionItemResult(
                            uid,
                            knownUsername[0],
                            BulkUserItemOutcome.SKIPPED,
                            skip.code(),
                            skip.messageKey(),
                            skip.messageArgs(),
                            null);
                }
                switch (action) {
                    case DEACTIVATE -> {
                        UserStatus oldStatus = user.getStatus();
                        String oldReason = user.getInactiveReason();
                        revokeAllSessionsFailClosed(uid);
                        keycloakClient.setEnabled(uid, false);
                        restoreKcEnabledOnRollback[0] = oldStatus == UserStatus.ACTIVE;
                        user.deactivate(trimmedReason, now, actor.id());
                        user = userAccountRepository.saveAndFlush(user);
                        Map<String, Object> before = new LinkedHashMap<>();
                        Map<String, Object> after = new LinkedHashMap<>();
                        if (oldStatus != UserStatus.INACTIVE) {
                            before.put("status", oldStatus.name());
                            after.put("status", UserStatus.INACTIVE.name());
                        }
                        if (!Objects.equals(oldReason, user.getInactiveReason())) {
                            before.put("inactiveReason", oldReason);
                            after.put("inactiveReason", user.getInactiveReason());
                        }
                        recordAudit(actor, "USER_DEACTIVATED", uid, user.getUsername(), before, after, trimmedReason);
                    }
                    case ACTIVATE -> {
                        UserStatus oldStatus = user.getStatus();
                        String oldReason = user.getInactiveReason();
                        keycloakClient.setEnabled(uid, true);
                        restoreKcEnabledOnRollback[0] = oldStatus == UserStatus.ACTIVE;
                        user.activate(now, actor.id());
                        user = userAccountRepository.saveAndFlush(user);
                        Map<String, Object> before = new LinkedHashMap<>();
                        Map<String, Object> after = new LinkedHashMap<>();
                        if (oldStatus != UserStatus.ACTIVE) {
                            before.put("status", oldStatus.name());
                            after.put("status", UserStatus.ACTIVE.name());
                        }
                        if (oldReason != null) {
                            before.put("inactiveReason", oldReason);
                            after.put("inactiveReason", null);
                        }
                        recordAudit(actor, "USER_ACTIVATED", uid, user.getUsername(), before, after, null);
                    }
                    case DELETE -> performSoftDeleteLocal(actor, user, trimmedReason, now);
                }
                return new StoredBulkUserActionItemResult(
                        uid, user.getUsername(), BulkUserItemOutcome.SUCCEEDED, null, null, List.of(), null);
            });
            if (committed != null
                    && committed.outcome() == BulkUserItemOutcome.SUCCEEDED
                    && action == UserBulkAction.DELETE) {
                cleanupDeletedUserInKeycloak(uid);
            }
            return committed;
        } catch (ExternalServiceException ex) {
            if (restoreKcEnabledOnRollback[0] != null) {
                compensateKeycloakEnabled(uid, restoreKcEnabledOnRollback[0]);
            }
            return new StoredBulkUserActionItemResult(
                    uid, knownUsername[0], BulkUserItemOutcome.FAILED, ex.getCode(), "bulk.user.fail.idpError", List.of(), null);
        } catch (RuntimeException ex) {
            if (restoreKcEnabledOnRollback[0] != null) {
                compensateKeycloakEnabled(uid, restoreKcEnabledOnRollback[0]);
            }
            log.atError()
                    .setCause(ex)
                    .addKeyValue("userId", uid.toString())
                    .addKeyValue("action", action.name())
                    .log("Bulk user action item failed; transaction rolled back");
            return new StoredBulkUserActionItemResult(
                    uid, knownUsername[0], BulkUserItemOutcome.FAILED, "INTERNAL_ERROR", "bulk.user.fail.internal", List.of(), null);
        }
    }

    private BulkUserActionResponse localizeBulkResponse(StoredBulkUserActionResponse stored) {
        List<BulkUserActionItemResult> localizedResults = stored.results().stream()
                .map(item -> new BulkUserActionItemResult(
                        item.id(),
                        item.username(),
                        item.outcome(),
                        item.reasonCode(),
                        localizeBulkItemReason(item)))
                .toList();
        return new BulkUserActionResponse(
                stored.batchId(),
                stored.action(),
                stored.total(),
                stored.succeeded(),
                stored.skipped(),
                stored.failed(),
                localizedResults);
    }

    private String localizeBulkItemReason(StoredBulkUserActionItemResult item) {
        if (item.outcome() == BulkUserItemOutcome.SUCCEEDED) {
            return null;
        }
        if (item.reasonKey() != null && !item.reasonKey().isBlank()) {
            return localizeReason(item.reasonKey(), item.reasonArgs(), item.reasonCode());
        }
        return item.reason();
    }

    private String localizeReason(String messageKey, List<String> messageArgs, String fallback) {
        if (messageKey == null || messageKey.isBlank()) {
            return fallback;
        }
        Object[] args = messageArgs == null || messageArgs.isEmpty() ? new Object[0] : messageArgs.toArray();
        return messages.get(messageKey, args);
    }

    private void performSoftDeleteLocal(CurrentUser actor, UserAccount user, String trimmedReason, Instant now) {
        UUID userId = user.getId();
        UserStatus oldStatus = user.getStatus();
        user.softDelete(trimmedReason, now, actor.id());
        user = userAccountRepository.saveAndFlush(user);
        sessionStore.deleteAllSessions(userId);
        authzSnapshotService.evict(userId);
        outboxPublisher.publish(
                UserDeleted.OUTBOX_TOPIC,
                new UserDeleted(userId, user.getUsername(), now, actor.id()));

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (oldStatus != UserStatus.INACTIVE) {
            before.put("status", oldStatus.name());
            after.put("status", UserStatus.INACTIVE.name());
        }
        before.put("deletedAt", null);
        after.put("deletedAt", now.toString());
        after.put("deleteReason", trimmedReason);
        recordAudit(actor, "USER_DELETED", userId, user.getUsername(), before, after, trimmedReason);
    }

    private void cleanupDeletedUserInKeycloak(UUID userId) {
        try {
            keycloakClient.deleteUser(userId);
            requiresNewTx.executeWithoutResult(status ->
                    userAccountRepository.updateIdpCleanupPending(userId, false, Instant.now(clock)));
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("userId", userId.toString())
                    .log("Keycloak deleteUser failed after local soft-delete; leaving idp_cleanup_pending=true");
        }
    }

    private UUID createKeycloakUserWithPendingCleanupRetry(
            UUID provisionalId,
            String trimmedUsername,
            String normalizedUsername,
            String trimmedEmail,
            String normalizedEmail,
            String firstName,
            String lastName) {
        KeycloakIdentityClient.CreateUserCommand cmd = new KeycloakIdentityClient.CreateUserCommand(
                trimmedUsername,
                trimmedEmail,
                firstName,
                lastName,
                null,
                true,
                false,
                false);
        try {
            return keycloakClient.createUser(cmd);
        } catch (ConflictException conflictEx) {
            List<UserAccount> pendingRows = userAccountRepository.findPendingIdpCleanupByUsernameOrEmail(
                    normalizedUsername, normalizedEmail);
            if (pendingRows.isEmpty()) {
                throw conflictEx;
            }
            for (UserAccount pending : pendingRows) {
                try {
                    if (pending.getStatus() != UserStatus.PROVISIONING) {
                        keycloakClient.deleteUser(pending.getId());
                        requiresNewTx.executeWithoutResult(status ->
                                userAccountRepository.updateIdpCleanupPending(
                                        pending.getId(), false, Instant.now(clock)));
                    } else {
                        Optional<UUID> orphanKcId = keycloakClient.findUserIdByUsername(pending.getUsername());
                        if (orphanKcId.isPresent()) {
                            keycloakClient.deleteUser(orphanKcId.get());
                        }
                        deleteProvisionalUserQuietly(pending.getId());
                    }
                } catch (ExternalServiceException ex) {
                    deleteProvisionalUserQuietly(provisionalId);
                    throw ex;
                } catch (RuntimeException ex) {
                    deleteProvisionalUserQuietly(provisionalId);
                    throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
                }
            }
            return keycloakClient.createUser(cmd);
        }
    }
    private void revokeAllSessionsFailClosed(UUID userId) {
        RuntimeException firstFailure = null;
        try {
            sessionStore.deleteAllSessions(userId);
        } catch (RuntimeException ex) {
            firstFailure = ex;
        }
        try {
            authzSnapshotService.evict(userId);
        } catch (RuntimeException ex) {
            if (firstFailure == null) {
                firstFailure = ex;
            }
        }
        try {
            keycloakClient.logoutAllSessions(userId);
        } catch (RuntimeException ex) {
            if (firstFailure == null) {
                firstFailure = ex;
            }
        }
        if (firstFailure != null) {
            if (firstFailure instanceof ExternalServiceException ese) {
                throw ese;
            }
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, firstFailure);
        }
    }

    private void compensateKeycloakEnabled(UUID userId, boolean restoreEnabled) {
        try {
            keycloakClient.setEnabled(userId, restoreEnabled);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("userId", userId.toString())
                    .addKeyValue("enabled", restoreEnabled)
                    .log("Failed to compensate Keycloak enabled state after rollback; persisting idp_enabled_sync_pending");
            markIdpEnabledSyncPendingQuietly(userId);
        }
    }

    private void markIdpEnabledSyncPendingQuietly(UUID userId) {
        try {
            requiresNewTx.executeWithoutResult(status ->
                    userAccountRepository.updateIdpEnabledSyncPending(userId, true, Instant.now(clock)));
        } catch (RuntimeException ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("userId", userId.toString())
                    .log("Failed to persist idp_enabled_sync_pending after Keycloak compensation failure");
        }
    }

    private UserAccount lockActiveManageableUser(CurrentUser actor, UUID userId) {
        jdbcTemplate.query(
                "SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE",
                rs -> {},
                userId);
        UserAccount user = requireExistingNonDeletedUser(userId);
        Set<Permission> targetPermissions = authzSnapshotService.computeEffectivePermissions(userId);
        privilegeGuard.assertCanGrant(actor, targetPermissions);
        return user;
    }

    private UserAccount requireExistingNonDeletedUser(UUID userId) {
        return userAccountRepository.findById(userId)
                .filter(u -> !u.isDeleted() && u.getStatus() != UserStatus.PROVISIONING)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    private List<AccountDeletionGuard.Blocker> collectDeletionBlockers(UUID userId) {
        if (deletionGuards.isEmpty()) {
            return List.of();
        }
        List<AccountDeletionGuard.Blocker> all = new ArrayList<>();
        for (AccountDeletionGuard guard : deletionGuards) {
            List<AccountDeletionGuard.Blocker> found = guard.check(userId);
            if (found != null && !found.isEmpty()) {
                all.addAll(found);
            }
        }
        return List.copyOf(all);
    }

    private BulkSkipDecision evaluateBulkEligibility(
            CurrentUser actor, UserBulkAction action, UUID userId, UserAccount user) {
        if (user == null || user.isDeleted() || user.getStatus() == UserStatus.PROVISIONING) {
            return new BulkSkipDecision("USER_NOT_AVAILABLE", "bulk.user.skip.notAvailable");
        }
        if (action == UserBulkAction.DEACTIVATE && actor.realm() == Realm.APP && actor.id().equals(userId)) {
            return new BulkSkipDecision(ERROR_SELF_DEACTIVATE, ERROR_SELF_DEACTIVATE);
        }
        if (action == UserBulkAction.DELETE && actor.realm() == Realm.APP && actor.id().equals(userId)) {
            return new BulkSkipDecision(ERROR_SELF_DELETE, ERROR_SELF_DELETE);
        }
        if (action == UserBulkAction.DEACTIVATE && user.getStatus() == UserStatus.INACTIVE) {
            return new BulkSkipDecision("ALREADY_INACTIVE", "bulk.user.skip.alreadyInactive");
        }
        if (action == UserBulkAction.ACTIVATE && user.getStatus() == UserStatus.ACTIVE) {
            return new BulkSkipDecision("ALREADY_ACTIVE", "bulk.user.skip.alreadyActive");
        }
        Set<Permission> targetPermissions = authzSnapshotService.computeEffectivePermissions(userId);
        if (!privilegeGuard.canGrant(actor, targetPermissions)) {
            return new BulkSkipDecision("PRIVILEGE_ESCALATION", "bulk.user.skip.privilegeEscalation");
        }
        if (action == UserBulkAction.DELETE) {
            List<AccountDeletionGuard.Blocker> blockers = collectDeletionBlockers(userId);
            if (!blockers.isEmpty()) {
                String sources = blockers.stream().map(AccountDeletionGuard.Blocker::source).distinct().reduce((a, b) -> a + ", " + b).orElse("");
                return new BulkSkipDecision(ERROR_USER_HAS_TRANSACTIONS, "bulk.user.skip.hasTransactions", List.of(sources));
            }
        }
        return null;
    }

    private Map<UUID, UserAccount> loadAccountsById(List<UUID> userIds) {
        Map<UUID, UserAccount> map = new HashMap<>();
        for (UserAccount ua : userAccountRepository.findAllByIdIn(userIds)) {
            map.put(ua.getId(), ua);
        }
        return map;
    }


    private List<UUID> validateBulkUserIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("userIds", "NOTEMPTY", messages.get("validation.userIds.notEmpty"))));
        }
        if (userIds.size() > MAX_BULK_USERS) {
            throw new ValidationException(
                    "BULK_LIMIT_EXCEEDED",
                    null,
                    List.of(new FieldError(
                            "userIds", "BULK_LIMIT_EXCEEDED", messages.get("BULK_LIMIT_EXCEEDED"))));
        }
        Set<UUID> seen = new LinkedHashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                seen.add(id);
            }
        }
        if (seen.isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("userIds", "NOTEMPTY", messages.get("validation.userIds.notEmpty"))));
        }
        return List.copyOf(seen);
    }

    private static String computeBulkDigest(UserBulkAction action, List<UUID> distinctIds, String trimmedReason) {
        String reasonPart = trimmedReason == null ? "" : trimmedReason;
        String canonical = "action=" + action.name()
                + "|users=" + distinctIds
                + "|reason=" + reasonPart.length() + ':' + reasonPart;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String validateReason(String field, String reason, String emptyMessageKey) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(List.of(
                    new FieldError(field, "REASON_REQUIRED", messages.get(emptyMessageKey))));
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new ValidationException(List.of(
                    new FieldError(field, "SIZE", messages.get("validation.reason.size"))));
        }
        return trimmed;
    }

    private record BulkSkipDecision(String code, String messageKey, List<String> messageArgs) {
        private BulkSkipDecision(String code, String messageKey) {
            this(code, messageKey, List.of());
        }
    }

    record StoredBulkUserActionResponse(
            UUID batchId,
            UserBulkAction action,
            int total,
            int succeeded,
            int skipped,
            int failed,
            List<StoredBulkUserActionItemResult> results
    ) {
    }

    record StoredBulkUserActionItemResult(
            UUID id,
            String username,
            BulkUserItemOutcome outcome,
            String reasonCode,
            String reasonKey,
            List<String> reasonArgs,
            String reason
    ) {
    }

    private List<Role> resolveTargetRoles(CurrentUser actor, List<UUID> requestedRoleIds) {
        if (requestedRoleIds == null || requestedRoleIds.isEmpty()) {
            Role studentRole = roleRepository.findByCodeNormalized(STUDENT_ROLE_CODE_NORMALIZED)
                    .orElseThrow(() -> new IllegalStateException("System role STUDENT not found"));
            if (studentRole.getStatus() != RoleStatus.ACTIVE) {
                throw new ConflictException(ERROR_ROLE_INACTIVE);
            }
            return List.of(studentRole);
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID roleId : requestedRoleIds) {
            if (roleId != null) {
                uniqueIds.add(roleId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return resolveTargetRoles(actor, null);
        }

        Map<UUID, Role> foundById = new HashMap<>();
        for (Role role : roleRepository.findAllById(uniqueIds)) {
            foundById.put(role.getId(), role);
        }
        if (foundById.size() != uniqueIds.size()) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        List<Role> targetRoles = new ArrayList<>(uniqueIds.size());
        for (UUID id : uniqueIds) {
            Role role = foundById.get(id);
            if (role.getStatus() != RoleStatus.ACTIVE) {
                throw new ConflictException(ERROR_ROLE_INACTIVE);
            }
            targetRoles.add(role);
        }

        boolean isOnlyStudent = targetRoles.size() == 1
                && STUDENT_ROLE_CODE_NORMALIZED.equals(targetRoles.getFirst().getCodeNormalized());
        if (!isOnlyStudent) {
            assertHasUserAssignPermission(actor);
            Set<Permission> permissionsToGrant = loadPermissionsForRoles(uniqueIds);
            privilegeGuard.assertCanGrant(actor, permissionsToGrant);
        }
        return List.copyOf(targetRoles);
    }

    private static void assertHasUserAssignPermission(CurrentUser actor) {
        if (actor.realm() == Realm.MASTER || actor.permissions().isAllGranted()) {
            return;
        }
        if (!actor.permissions().hasBit(Permission.USER_ASSIGN.bitIndex())
                && !actor.permissions().hasCode(Permissions.USER_ASSIGN)) {
            throw new ForbiddenException("FORBIDDEN", null);
        }
    }

    private Set<Permission> loadPermissionsForRoles(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
        String sql =
                """
                SELECT DISTINCT p.code
                FROM identity.role_permission rp
                JOIN identity.permission p ON p.id = rp.permission_id
                WHERE rp.role_id IN (%s)
                """.formatted(placeholders);
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        jdbcTemplate.query(
                sql,
                rs -> {
                    String code = rs.getString("code");
                    Permission.findByCode(code).ifPresent(permissions::add);
                },
                roleIds.toArray());
        return permissions;
    }

    private Map<UUID, List<UserRoleSummary>> loadRolesForUsers(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        String sql =
                """
                SELECT ur.user_id,
                       r.id AS role_id,
                       r.code AS role_code,
                       r.name AS role_name,
                       r.access AS role_access
                FROM identity.user_role ur
                JOIN identity.role r ON r.id = ur.role_id
                WHERE ur.user_id IN (%s)
                ORDER BY r.is_system DESC, r.code ASC
                """.formatted(placeholders);
        Map<UUID, List<UserRoleSummary>> result = new HashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID uid = rs.getObject("user_id", UUID.class);
                    UserRoleSummary summary = new UserRoleSummary(
                            rs.getObject("role_id", UUID.class),
                            rs.getString("role_code"),
                            rs.getString("role_name"),
                            Access.fromCode(rs.getShort("role_access")));
                    result.computeIfAbsent(uid, k -> new ArrayList<>()).add(summary);
                },
                userIds.toArray());
        return result;
    }

    private void recordAudit(
            CurrentUser actor,
            String action,
            UUID targetId,
            String targetLabel,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason) {
        AuditActorType actorType = actor.realm() == Realm.MASTER ? AuditActorType.PLATFORM : AuditActorType.USER;
        auditRecorder.record(AuditEvent.builder(MODULE, action)
                .target(TARGET_TYPE, targetId.toString(), targetLabel)
                .before(before)
                .after(after)
                .reason(reason)
                .actor(actor.id(), actor.username(), null, actorType)
                .build());
    }

    private void deleteProvisionalUserQuietly(UUID provisionalId) {
        try {
            requiresNewTx.executeWithoutResult(status -> userAccountRepository.deleteById(provisionalId));
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("provisionalUserId", provisionalId.toString())
                    .log("Failed to clean up PROVISIONING user row");
        }
    }

    private void markProvisionalIdpCleanupPendingQuietly(UUID provisionalId) {
        try {
            requiresNewTx.executeWithoutResult(status ->
                    userAccountRepository.updateIdpCleanupPending(provisionalId, true, Instant.now(clock)));
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("provisionalUserId", provisionalId.toString())
                    .log("Failed to mark PROVISIONING user for IdP cleanup");
        }
    }

    private boolean deleteKeycloakUserQuietly(UUID keycloakUserId) {
        try {
            keycloakClient.deleteUser(keycloakUserId);
            return true;
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("keycloakUserId", keycloakUserId.toString())
                    .log("Failed to clean up Keycloak user during rollback");
            return false;
        }
    }

    private static RuntimeException mapUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                String name = cve.getConstraintName().toLowerCase(Locale.ROOT);
                if (name.contains("uq_user_account_username_active")) {
                    return new ConflictException(ERROR_USERNAME_TAKEN);
                }
                if (name.contains("uq_user_account_email_active")) {
                    return new ConflictException(ERROR_EMAIL_TAKEN);
                }
            }
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("uq_user_account_username_active")) {
                    return new ConflictException(ERROR_USERNAME_TAKEN);
                }
                if (lower.contains("uq_user_account_email_active")) {
                    return new ConflictException(ERROR_EMAIL_TAKEN);
                }
            }
            cursor = cursor.getCause();
        }
        return ex;
    }

    private String validateRequiredFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException(List.of(
                    new FieldError("fullName", "NOTBLANK", messages.get("validation.user.fullName.notBlank"))));
        }
        String trimmed = fullName.trim();
        if (trimmed.length() > 200) {
            throw new ValidationException(List.of(
                    new FieldError("fullName", "SIZE", messages.get("validation.user.fullName.size"))));
        }
        return trimmed;
    }

    private String normalizeAndValidateEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.length() > 255) {
            throw new ValidationException(List.of(
                    new FieldError("email", "SIZE", messages.get("validation.user.email.size"))));
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(List.of(
                    new FieldError("email", "EMAIL_INVALID", messages.get("validation.user.email.invalid"))));
        }
        return trimmed;
    }

    private String normalizeAndValidatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() > 30) {
            throw new ValidationException(List.of(
                    new FieldError("phone", "SIZE", messages.get("validation.user.phone.size"))));
        }
        return trimmed;
    }

    private static String[] splitFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[] {null, null};
        }
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            return new String[] {normalized, null};
        }
        return new String[] {
            normalized.substring(firstSpace + 1),
            normalized.substring(0, firstSpace)
        };
    }

    private static List<UserRoleSummary> toRoleSummaries(List<Role> roles) {
        return roles.stream()
                .map(role -> new UserRoleSummary(role.getId(), role.getCode(), role.getName(), role.getAccess()))
                .toList();
    }

    private static UserDetailResponse toDetailResponse(UserAccount user, List<UserRoleSummary> roles) {
        return new UserDetailResponse(
                user.getId(),
                user.getCode(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getEmailVerifiedAt(),
                user.getPhone(),
                user.getSource(),
                user.getStatus(),
                user.isMustChangePassword(),
                user.getLastLoginAt(),
                user.getInactiveReason(),
                user.getInactivatedAt(),
                user.getInactivatedBy(),
                user.getDeletedAt(),
                user.getDeletedBy(),
                user.getDeleteReason(),
                roles == null ? List.of() : List.copyOf(roles),
                user.getVersion(),
                user.getCreatedAt(),
                user.getCreatedBy(),
                user.getUpdatedAt(),
                user.getUpdatedBy());
    }

    private static UserRow mapUserRow(ResultSet rs) throws SQLException {
        return new UserRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                UserSource.fromCode(rs.getShort("source")),
                UserStatus.fromCode(rs.getShort("status")),
                rs.getBoolean("must_change_password"),
                toInstant(rs.getTimestamp("last_login_at")),
                toInstant(rs.getTimestamp("deleted_at")),
                rs.getLong("version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY u.created_at DESC, u.id DESC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String dir = order.isAscending() ? " ASC" : " DESC";
            String clause = switch (order.getProperty()) {
                case "createdAt" -> "u.created_at" + dir;
                case "username" -> "u.username" + dir;
                case "fullName" -> "u.full_name" + dir;
                case "lastLoginAt" -> "u.last_login_at" + dir + " NULLS LAST";
                default -> null;
            };
            if (clause != null) {
                clauses.add(clause);
            }
        }
        if (clauses.isEmpty()) {
            return "ORDER BY u.created_at DESC, u.id DESC";
        }
        clauses.add("u.id DESC");
        return "ORDER BY " + String.join(", ", clauses);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record UserRow(
            UUID id,
            String code,
            String username,
            String fullName,
            String email,
            String phone,
            UserSource source,
            UserStatus status,
            boolean mustChangePassword,
            Instant lastLoginAt,
            Instant deletedAt,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
