package vn.unicom.thi360.identity.application;

import java.sql.Array;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.BulkAuthzAction;
import vn.unicom.thi360.identity.api.BulkAuthzItemResponse;
import vn.unicom.thi360.identity.api.BulkAuthzOutcome;
import vn.unicom.thi360.identity.api.BulkAuthzRequest;
import vn.unicom.thi360.identity.api.BulkAuthzResponse;
import vn.unicom.thi360.identity.api.BulkAuthzTarget;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.idempotency.IdempotencyService;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;

@Service
public class BulkAuthzService {

    public static final int MAX_BATCH_USERS = 500;

    public static final String REASON_USER_NOT_AVAILABLE = "USER_NOT_AVAILABLE";
    public static final String REASON_ROLE_INACTIVE = "ROLE_INACTIVE";
    public static final String REASON_PRIVILEGE_ESCALATION = "PRIVILEGE_ESCALATION";
    public static final String REASON_PERMISSION_SCOPE_MISMATCH = "PERMISSION_SCOPE_MISMATCH";
    public static final String REASON_DIRECT_PERMISSION_REQUIRES_MANAGE = "DIRECT_PERMISSION_REQUIRES_MANAGE";
    public static final String REASON_SELF_LOCKOUT = "SELF_LOCKOUT";
    public static final String REASON_PERMISSION_REQUIRES_VIEW =
            PermissionSetNormalizer.ERROR_PERMISSION_REQUIRES_VIEW;

    private static final String MODULE = "IDENTITY";
    private static final String IDEMPOTENCY_SCOPE = "AUTHZ_BULK";

    private static final String LOAD_BATCH_AUTHZ_SQL =
            """
            SELECT 'ROLE' AS entry_type,
                   ur.user_id,
                   r.id AS role_id,
                   r.code AS role_code,
                   r.access AS role_access,
                   r.status AS role_status,
                   p.code AS perm_code,
                   NULL::uuid AS perm_id
            FROM identity.user_role ur
            JOIN identity.role r ON r.id = ur.role_id
            LEFT JOIN identity.role_permission rp ON rp.role_id = r.id
            LEFT JOIN identity.permission p ON p.id = rp.permission_id
            WHERE ur.user_id = ANY(?)
            UNION ALL
            SELECT 'PERM' AS entry_type,
                   up.user_id,
                   NULL::uuid AS role_id,
                   NULL::text AS role_code,
                   NULL::smallint AS role_access,
                   NULL::smallint AS role_status,
                   p.code AS perm_code,
                   up.permission_id AS perm_id
            FROM identity.user_permission up
            JOIN identity.permission p ON p.id = up.permission_id
            WHERE up.user_id = ANY(?)
            """;

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionSetNormalizer permissionSetNormalizer;
    private final PrivilegeGuard privilegeGuard;
    private final AuthzSnapshotService authzSnapshotService;
    private final IdempotencyService idempotencyService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public BulkAuthzService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            PermissionSetNormalizer permissionSetNormalizer,
            PrivilegeGuard privilegeGuard,
            AuthzSnapshotService authzSnapshotService,
            IdempotencyService idempotencyService,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionSetNormalizer = permissionSetNormalizer;
        this.privilegeGuard = privilegeGuard;
        this.authzSnapshotService = authzSnapshotService;
        this.idempotencyService = idempotencyService;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BulkAuthzResponse preview(BulkAuthzRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ValidatedTargets targets = validateAndLoadTargets(request);
        BatchEvaluation evaluation = evaluateBatch(actor, request, targets);
        return evaluation.toResponse(null);
    }

    public BulkAuthzResponse apply(BulkAuthzRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException(List.of(
                    new FieldError("Idempotency-Key", "IDEMPOTENCY_KEY_REQUIRED", "Thiếu header Idempotency-Key")));
        }
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ValidatedTargets targets = validateAndLoadTargets(request);

        String trimmedKey = idempotencyKey.trim();
        return idempotencyService.executeOnce(trimmedKey, IDEMPOTENCY_SCOPE, BulkAuthzResponse.class, () -> {
            lockUserAuthzState(targets.userIds());
            BatchEvaluation evaluation = evaluateBatch(actor, request, targets);
            UUID batchId = Ids.newId();
            Instant now = Instant.now(clock);
            String trimmedReason = request.reason() == null ? null : request.reason().trim();

            applyChanges(actor, request, targets, evaluation, batchId, now, trimmedReason);
            return evaluation.toResponse(batchId);
        });
    }

    private ValidatedTargets validateAndLoadTargets(BulkAuthzRequest request) {
        if (request.userIds() == null || request.userIds().isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("userIds", "NOT_EMPTY", "Danh sách tài khoản không được để trống")));
        }
        if (request.userIds().size() > MAX_BATCH_USERS) {
            throw new ValidationException(
                    "BULK_LIMIT_EXCEEDED",
                    null,
                    List.of(new FieldError(
                            "userIds", "BULK_LIMIT_EXCEEDED", "Số lượng tài khoản thao tác hàng loạt tối đa là 500")));
        }
        List<UUID> distinctUserIds = List.copyOf(new LinkedHashSet<>(request.userIds()));

        if (request.target() == BulkAuthzTarget.ROLE) {
            if (request.roleIds() == null || request.roleIds().isEmpty()) {
                throw new ValidationException(List.of(
                        new FieldError("roleIds", "NOT_EMPTY", "Danh sách vai trò không được để trống")));
            }
            List<UUID> distinctRoleIds = List.copyOf(new LinkedHashSet<>(request.roleIds()));
            Map<UUID, Role> rolesById = new LinkedHashMap<>();
            for (Role role : roleRepository.findAllById(distinctRoleIds)) {
                rolesById.put(role.getId(), role);
            }
            if (rolesById.size() != distinctRoleIds.size()) {
                throw new NotFoundException("NOT_FOUND", null);
            }
            List<Role> orderedRoles = distinctRoleIds.stream().map(rolesById::get).toList();
            Map<UUID, Set<Permission>> rolePermissions = loadPermissionsForRoles(distinctRoleIds);
            return new ValidatedTargets(distinctUserIds, orderedRoles, rolePermissions, List.of(), Map.of());
        }

        // target == PERMISSION
        if (request.permissionIds() == null || request.permissionIds().isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("permissionIds", "NOT_EMPTY", "Danh sách quyền không được để trống")));
        }
        if (request.action() == BulkAuthzAction.GRANT
                && (request.reason() == null || request.reason().isBlank())) {
            throw new ValidationException(List.of(
                    new FieldError("reason", "REASON_REQUIRED", "Vui lòng nhập lý do khi gán quyền trực tiếp")));
        }
        List<UUID> distinctPermIds = List.copyOf(new LinkedHashSet<>(request.permissionIds()));
        Map<UUID, PermissionEntity> permsById = new LinkedHashMap<>();
        for (PermissionEntity pe : permissionRepository.findAllById(distinctPermIds)) {
            permsById.put(pe.getId(), pe);
        }
        if (permsById.size() != distinctPermIds.size()) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        Map<String, PermissionEntity> allPermsByCode = new LinkedHashMap<>();
        for (PermissionEntity pe : permissionRepository.findAll()) {
            allPermsByCode.put(pe.getCode(), pe);
        }
        List<PermissionEntity> orderedPerms = distinctPermIds.stream().map(permsById::get).toList();
        return new ValidatedTargets(distinctUserIds, List.of(), Map.of(), orderedPerms, allPermsByCode);
    }

    private BatchEvaluation evaluateBatch(
            CurrentUser actor, BulkAuthzRequest request, ValidatedTargets targets) {
        List<UUID> userIds = targets.userIds();
        Map<UUID, UserAccount> usersById = new HashMap<>();
        for (UserAccount ua : userAccountRepository.findAllByIdIn(userIds)) {
            usersById.put(ua.getId(), ua);
        }

        Map<UUID, UserBatchAuthzState> stateByUser = loadBatchStates(userIds);

        List<UserEvaluation> evaluations = new ArrayList<>(userIds.size());
        int willChange = 0;
        int noChange = 0;
        int skipped = 0;

        for (UUID userId : userIds) {
            UserAccount account = usersById.get(userId);
            if (account == null || account.getDeletedAt() != null) {
                skipped++;
                evaluations.add(UserEvaluation.skipped(userId, null, REASON_USER_NOT_AVAILABLE));
                continue;
            }
            UserBatchAuthzState state = stateByUser.getOrDefault(userId, UserBatchAuthzState.empty());
            UserEvaluation eval = request.target() == BulkAuthzTarget.ROLE
                    ? evaluateRoleTarget(actor, account, state, request.action(), targets)
                    : evaluatePermissionTarget(actor, account, state, request.action(), targets);

            switch (eval.item().outcome()) {
                case WILL_CHANGE -> willChange++;
                case NO_CHANGE -> noChange++;
                case SKIPPED -> skipped++;
            }
            evaluations.add(eval);
        }

        return new BatchEvaluation(willChange, noChange, skipped, evaluations);
    }

    private UserEvaluation evaluateRoleTarget(
            CurrentUser actor,
            UserAccount account,
            UserBatchAuthzState state,
            BulkAuthzAction action,
            ValidatedTargets targets) {
        UUID userId = account.getId();
        String username = account.getUsername();

        if (action == BulkAuthzAction.GRANT) {
            List<Role> rolesToAdd = new ArrayList<>();
            for (Role role : targets.roles()) {
                if (!state.roles.containsKey(role.getId())) {
                    rolesToAdd.add(role);
                }
            }
            for (Role role : rolesToAdd) {
                if (role.getStatus() != RoleStatus.ACTIVE) {
                    return UserEvaluation.skipped(userId, username, REASON_ROLE_INACTIVE);
                }
            }
            // Privilege escalation check against all permissions contained in requested roles
            EnumSet<Permission> allRequestedRolePerms = EnumSet.noneOf(Permission.class);
            for (Role role : targets.roles()) {
                allRequestedRolePerms.addAll(targets.rolePermissions().getOrDefault(role.getId(), Set.of()));
            }
            if (!privilegeGuard.canGrant(actor, allRequestedRolePerms)) {
                return UserEvaluation.skipped(userId, username, REASON_PRIVILEGE_ESCALATION);
            }
            for (Role role : rolesToAdd) {
                if (role.getAccess() == Access.EXAM) {
                    Set<Permission> perms = targets.rolePermissions().getOrDefault(role.getId(), Set.of());
                    if (perms.stream().anyMatch(p -> p.scope() == Access.MANAGE)) {
                        return UserEvaluation.skipped(userId, username, REASON_PERMISSION_SCOPE_MISMATCH);
                    }
                }
            }
            if (rolesToAdd.isEmpty()) {
                return UserEvaluation.noChange(userId, username);
            }
            List<UUID> addedIds = rolesToAdd.stream().map(Role::getId).toList();
            List<String> addedCodes = rolesToAdd.stream().map(Role::getCode).toList();
            List<String> beforeCodes = state.roleCodesSorted();
            List<String> afterCodes = new ArrayList<>(beforeCodes);
            afterCodes.addAll(addedCodes);
            Collections.sort(afterCodes);
            return UserEvaluation.willChange(
                    userId, username, addedCodes, List.of(), addedIds, List.of(), beforeCodes, afterCodes);
        }

        // action == REVOKE
        List<Role> rolesToRemove = new ArrayList<>();
        Set<UUID> removeIds = new LinkedHashSet<>();
        for (Role role : targets.roles()) {
            if (state.roles.containsKey(role.getId())) {
                rolesToRemove.add(role);
                removeIds.add(role.getId());
            }
        }
        if (rolesToRemove.isEmpty()) {
            return UserEvaluation.noChange(userId, username);
        }

        // Compute resulting effective permissions to check SELF_LOCKOUT
        boolean resultingHasManage = false;
        EnumSet<Permission> resultingEffective = EnumSet.noneOf(Permission.class);
        for (UserRoleEntry entry : state.roles.values()) {
            if (!removeIds.contains(entry.roleId) && entry.status == RoleStatus.ACTIVE) {
                if (entry.access == Access.MANAGE) {
                    resultingHasManage = true;
                }
                resultingEffective.addAll(entry.permissions);
            }
        }
        if (resultingHasManage) {
            resultingEffective.addAll(state.directPermissions.keySet());
        }
        if (privilegeGuard.willCauseSelfLockout(actor, userId, resultingEffective)) {
            return UserEvaluation.skipped(userId, username, REASON_SELF_LOCKOUT);
        }

        List<UUID> removedIds = rolesToRemove.stream().map(Role::getId).toList();
        List<String> removedCodes = rolesToRemove.stream().map(Role::getCode).toList();
        List<String> beforeCodes = state.roleCodesSorted();
        List<String> afterCodes = beforeCodes.stream()
                .filter(c -> !removedCodes.contains(c))
                .toList();
        return UserEvaluation.willChange(
                userId, username, List.of(), removedCodes, List.of(), removedIds, beforeCodes, afterCodes);
    }

    private UserEvaluation evaluatePermissionTarget(
            CurrentUser actor,
            UserAccount account,
            UserBatchAuthzState state,
            BulkAuthzAction action,
            ValidatedTargets targets) {
        UUID userId = account.getId();
        String username = account.getUsername();

        EnumSet<Permission> requestedPerms = EnumSet.noneOf(Permission.class);
        for (PermissionEntity pe : targets.permissions()) {
            Permission.findByCode(pe.getCode()).ifPresent(requestedPerms::add);
        }

        if (action == BulkAuthzAction.GRANT) {
            Set<Permission> normalized = permissionSetNormalizer.normalize(requestedPerms);
            if (!privilegeGuard.canGrant(actor, normalized)) {
                return UserEvaluation.skipped(userId, username, REASON_PRIVILEGE_ESCALATION);
            }
            if (!state.hasActiveManageRole()) {
                return UserEvaluation.skipped(userId, username, REASON_DIRECT_PERMISSION_REQUIRES_MANAGE);
            }

            Set<Permission> activeRolePerms = state.activeRolePermissions();
            List<Permission> toAdd = new ArrayList<>();
            for (Permission p : requestedPerms) {
                if (!activeRolePerms.contains(p) && !state.directPermissions.containsKey(p)) {
                    toAdd.add(p);
                }
                if (p.kind() != Permission.Kind.VIEW) {
                    Permission view = Permission.viewPermissionOf(p.object()).orElse(null);
                    if (view != null
                            && !activeRolePerms.contains(view)
                            && !state.directPermissions.containsKey(view)
                            && !toAdd.contains(view)) {
                        toAdd.add(view);
                    }
                }
            }
            if (toAdd.isEmpty()) {
                return UserEvaluation.noChange(userId, username);
            }
            toAdd.sort((a, b) -> Integer.compare(a.bitIndex(), b.bitIndex()));
            List<UUID> addedIds = new ArrayList<>(toAdd.size());
            List<String> addedCodes = new ArrayList<>(toAdd.size());
            for (Permission p : toAdd) {
                PermissionEntity pe = targets.allPermsByCode().get(p.code());
                if (pe != null) {
                    addedIds.add(pe.getId());
                    addedCodes.add(p.code());
                }
            }
            List<String> beforeCodes = state.directPermissionCodesSorted();
            List<String> afterCodes = new ArrayList<>(beforeCodes);
            afterCodes.addAll(addedCodes);
            Collections.sort(afterCodes);
            return UserEvaluation.willChange(
                    userId, username, addedCodes, List.of(), addedIds, List.of(), beforeCodes, afterCodes);
        }

        // action == REVOKE (direct permissions only)
        List<Permission> toRemove = new ArrayList<>();
        EnumSet<Permission> removeSet = EnumSet.noneOf(Permission.class);
        for (Permission p : requestedPerms) {
            if (state.directPermissions.containsKey(p)) {
                toRemove.add(p);
                removeSet.add(p);
            }
        }
        if (toRemove.isEmpty()) {
            return UserEvaluation.noChange(userId, username);
        }

        EnumSet<Permission> remainingDirect = EnumSet.noneOf(Permission.class);
        for (Permission p : state.directPermissions.keySet()) {
            if (!removeSet.contains(p)) {
                remainingDirect.add(p);
            }
        }
        if (permissionSetNormalizer.hasMissingRequiredView(remainingDirect, state.activeRolePermissions())) {
            return UserEvaluation.skipped(userId, username, REASON_PERMISSION_REQUIRES_VIEW);
        }

        EnumSet<Permission> resultingEffective = EnumSet.noneOf(Permission.class);
        resultingEffective.addAll(state.activeRolePermissions());
        if (state.hasActiveManageRole()) {
            for (Permission p : state.directPermissions.keySet()) {
                if (!removeSet.contains(p)) {
                    resultingEffective.add(p);
                }
            }
        }
        if (privilegeGuard.willCauseSelfLockout(actor, userId, resultingEffective)) {
            return UserEvaluation.skipped(userId, username, REASON_SELF_LOCKOUT);
        }

        toRemove.sort((a, b) -> Integer.compare(a.bitIndex(), b.bitIndex()));
        List<UUID> removedIds = new ArrayList<>(toRemove.size());
        List<String> removedCodes = new ArrayList<>(toRemove.size());
        for (Permission p : toRemove) {
            removedIds.add(state.directPermissions.get(p));
            removedCodes.add(p.code());
        }
        List<String> beforeCodes = state.directPermissionCodesSorted();
        List<String> afterCodes = beforeCodes.stream()
                .filter(c -> !removedCodes.contains(c))
                .toList();
        return UserEvaluation.willChange(
                userId, username, List.of(), removedCodes, List.of(), removedIds, beforeCodes, afterCodes);
    }

    private void applyChanges(
            CurrentUser actor,
            BulkAuthzRequest request,
            ValidatedTargets targets,
            BatchEvaluation evaluation,
            UUID batchId,
            Instant now,
            String trimmedReason) {
        List<UserEvaluation> changes = evaluation.evaluations.stream()
                .filter(e -> e.item().outcome() == BulkAuthzOutcome.WILL_CHANGE)
                .toList();

        if (!changes.isEmpty()) {
            if (request.target() == BulkAuthzTarget.ROLE) {
                if (request.action() == BulkAuthzAction.GRANT) {
                    List<Object[]> batchArgs = new ArrayList<>();
                    for (UserEvaluation ev : changes) {
                        for (UUID roleId : ev.addedIds()) {
                            batchArgs.add(new Object[] {
                                ev.item().userId(), roleId, actor.id(), java.sql.Timestamp.from(now)
                            });
                        }
                    }
                    int[] counts = jdbcTemplate.batchUpdate(
                            "INSERT INTO identity.user_role (user_id, role_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                            batchArgs);
                    assertAffectedRowCounts(counts, batchArgs.size(), "INSERT user_role");
                } else {
                    List<Object[]> batchArgs = new ArrayList<>();
                    for (UserEvaluation ev : changes) {
                        for (UUID roleId : ev.removedIds()) {
                            batchArgs.add(new Object[] {ev.item().userId(), roleId});
                        }
                    }
                    int[] counts = jdbcTemplate.batchUpdate(
                            "DELETE FROM identity.user_role WHERE user_id = ? AND role_id = ?",
                            batchArgs);
                    assertAffectedRowCounts(counts, batchArgs.size(), "DELETE user_role");
                }
            } else {
                if (request.action() == BulkAuthzAction.GRANT) {
                    List<Object[]> batchArgs = new ArrayList<>();
                    for (UserEvaluation ev : changes) {
                        for (UUID permId : ev.addedIds()) {
                            batchArgs.add(new Object[] {
                                ev.item().userId(), permId, actor.id(), java.sql.Timestamp.from(now), trimmedReason
                            });
                        }
                    }
                    int[] counts = jdbcTemplate.batchUpdate(
                            "INSERT INTO identity.user_permission (user_id, permission_id, granted_by, granted_at, reason) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                            batchArgs);
                    assertAffectedRowCounts(counts, batchArgs.size(), "INSERT user_permission");
                } else {
                    List<Object[]> batchArgs = new ArrayList<>();
                    for (UserEvaluation ev : changes) {
                        for (UUID permId : ev.removedIds()) {
                            batchArgs.add(new Object[] {ev.item().userId(), permId});
                        }
                    }
                    int[] counts = jdbcTemplate.batchUpdate(
                            "DELETE FROM identity.user_permission WHERE user_id = ? AND permission_id = ?",
                            batchArgs);
                    assertAffectedRowCounts(counts, batchArgs.size(), "DELETE user_permission");
                }
            }

            List<UUID> changedUserIds = changes.stream().map(e -> e.item().userId()).toList();
            authzSnapshotService.evictAll(changedUserIds);

            AuditActorType actorType =
                    actor.realm() == Realm.MASTER ? AuditActorType.PLATFORM : AuditActorType.USER;
            String itemAction = request.target() == BulkAuthzTarget.ROLE
                    ? "USER_ROLES_CHANGED"
                    : "USER_PERMISSIONS_CHANGED";
            String stateField = request.target() == BulkAuthzTarget.ROLE ? "roleCodes" : "permissionCodes";
            for (UserEvaluation ev : changes) {
                Map<String, Object> before = new LinkedHashMap<>();
                before.put("batchId", batchId.toString());
                before.put(stateField, ev.beforeCodes());

                Map<String, Object> after = new LinkedHashMap<>();
                after.put("batchId", batchId.toString());
                after.put(stateField, ev.afterCodes());

                auditRecorder.record(AuditEvent.builder(MODULE, itemAction)
                        .target("USER", ev.item().userId().toString(), ev.item().username())
                        .before(before)
                        .after(after)
                        .reason(trimmedReason)
                        .actor(actor.id(), actor.username(), null, actorType)
                        .build());
            }
        }

        AuditActorType actorType =
                actor.realm() == Realm.MASTER ? AuditActorType.PLATFORM : AuditActorType.USER;
        Map<String, Object> summaryAfter = new LinkedHashMap<>();
        summaryAfter.put("batchId", batchId.toString());
        summaryAfter.put("action", request.action().name());
        summaryAfter.put("target", request.target().name());
        summaryAfter.put("willChange", evaluation.willChange());
        summaryAfter.put("noChange", evaluation.noChange());
        summaryAfter.put("skipped", evaluation.skipped());

        auditRecorder.record(AuditEvent.builder(MODULE, "AUTHZ_BULK_APPLIED")
                .target("AUTHZ_BULK", batchId.toString(), request.action() + ":" + request.target())
                .after(summaryAfter)
                .reason(trimmedReason)
                .actor(actor.id(), actor.username(), null, actorType)
                .build());
    }

    private void lockUserAuthzState(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<UUID> sortedIds = userIds.stream().sorted().toList();
        jdbcTemplate.query(
                con -> {
                    var ps = con.prepareStatement(
                            "SELECT id FROM identity.user_account WHERE id = ANY(?) ORDER BY id FOR UPDATE");
                    ps.setArray(1, con.createArrayOf("uuid", sortedIds.toArray(UUID[]::new)));
                    return ps;
                },
                rs -> {});
        jdbcTemplate.query(
                con -> {
                    var ps = con.prepareStatement(
                            "SELECT user_id, role_id FROM identity.user_role WHERE user_id = ANY(?) ORDER BY user_id, role_id FOR UPDATE");
                    ps.setArray(1, con.createArrayOf("uuid", sortedIds.toArray(UUID[]::new)));
                    return ps;
                },
                rs -> {});
        jdbcTemplate.query(
                con -> {
                    var ps = con.prepareStatement(
                            "SELECT user_id, permission_id FROM identity.user_permission WHERE user_id = ANY(?) ORDER BY user_id, permission_id FOR UPDATE");
                    ps.setArray(1, con.createArrayOf("uuid", sortedIds.toArray(UUID[]::new)));
                    return ps;
                },
                rs -> {});
    }

    private static void assertAffectedRowCounts(int[] counts, int expectedOperations, String operation) {
        if (counts.length != expectedOperations) {
            throw new IllegalStateException(
                    "Unexpected batch update count length for " + operation + ": "
                            + counts.length + " vs " + expectedOperations);
        }
        for (int count : counts) {
            if (count != 1) {
                throw new IllegalStateException(
                        "Unexpected affected row count for " + operation + ": " + count + " (expected 1)");
            }
        }
    }

    private Map<UUID, UserBatchAuthzState> loadBatchStates(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserBatchAuthzState> states = new HashMap<>();
        jdbcTemplate.query(
                con -> {
                    var ps = con.prepareStatement(LOAD_BATCH_AUTHZ_SQL);
                    Array array = con.createArrayOf("uuid", userIds.toArray(UUID[]::new));
                    ps.setArray(1, array);
                    ps.setArray(2, array);
                    return ps;
                },
                rs -> {
                    String entryType = rs.getString("entry_type");
                    UUID userId = rs.getObject("user_id", UUID.class);
                    UserBatchAuthzState state =
                            states.computeIfAbsent(userId, id -> new UserBatchAuthzState());
                    if ("ROLE".equals(entryType)) {
                        UUID roleId = rs.getObject("role_id", UUID.class);
                        String roleCode = rs.getString("role_code");
                        Access roleAccess = Access.fromCode(rs.getShort("role_access"));
                        RoleStatus roleStatus = RoleStatus.fromCode(rs.getShort("role_status"));
                        UserRoleEntry entry = state.roles.computeIfAbsent(
                                roleId, id -> new UserRoleEntry(id, roleCode, roleAccess, roleStatus));
                        String permCode = rs.getString("perm_code");
                        if (permCode != null) {
                            Permission.findByCode(permCode).ifPresent(entry.permissions::add);
                        }
                    } else if ("PERM".equals(entryType)) {
                        String permCode = rs.getString("perm_code");
                        UUID permId = rs.getObject("perm_id", UUID.class);
                        if (permCode != null && permId != null) {
                            Permission.findByCode(permCode)
                                    .ifPresent(p -> state.directPermissions.put(p, permId));
                        }
                    }
                });
        return states;
    }

    private Map<UUID, Set<Permission>> loadPermissionsForRoles(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
        String sql =
                """
                SELECT rp.role_id, p.code
                FROM identity.role_permission rp
                JOIN identity.permission p ON p.id = rp.permission_id
                WHERE rp.role_id IN (%s)
                """.formatted(placeholders);
        Map<UUID, Set<Permission>> result = new HashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID roleId = rs.getObject("role_id", UUID.class);
                    String code = rs.getString("code");
                    Permission.findByCode(code)
                            .ifPresent(p -> result.computeIfAbsent(roleId, k -> EnumSet.noneOf(Permission.class)).add(p));
                },
                roleIds.toArray());
        return result;
    }

    private record ValidatedTargets(
            List<UUID> userIds,
            List<Role> roles,
            Map<UUID, Set<Permission>> rolePermissions,
            List<PermissionEntity> permissions,
            Map<String, PermissionEntity> allPermsByCode) {
    }

    private static final class UserRoleEntry {
        private final UUID roleId;
        private final String roleCode;
        private final Access access;
        private final RoleStatus status;
        private final EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);

        private UserRoleEntry(UUID roleId, String roleCode, Access access, RoleStatus status) {
            this.roleId = roleId;
            this.roleCode = roleCode;
            this.access = access;
            this.status = status;
        }
    }

    private static final class UserBatchAuthzState {
        private final Map<UUID, UserRoleEntry> roles = new LinkedHashMap<>();
        private final Map<Permission, UUID> directPermissions = new LinkedHashMap<>();

        private static UserBatchAuthzState empty() {
            return new UserBatchAuthzState();
        }

        private boolean hasActiveManageRole() {
            return roles.values().stream()
                    .anyMatch(r -> r.status == RoleStatus.ACTIVE && r.access == Access.MANAGE);
        }

        private Set<Permission> activeRolePermissions() {
            EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
            for (UserRoleEntry entry : roles.values()) {
                if (entry.status == RoleStatus.ACTIVE) {
                    result.addAll(entry.permissions);
                }
            }
            return result;
        }

        private List<String> roleCodesSorted() {
            return roles.values().stream().map(r -> r.roleCode).sorted().toList();
        }

        private List<String> directPermissionCodesSorted() {
            return directPermissions.keySet().stream().map(Permission::code).sorted().toList();
        }
    }

    private record UserEvaluation(
            BulkAuthzItemResponse item,
            List<UUID> addedIds,
            List<UUID> removedIds,
            List<String> beforeCodes,
            List<String> afterCodes) {

        static UserEvaluation skipped(UUID userId, String username, String reasonCode) {
            return new UserEvaluation(
                    new BulkAuthzItemResponse(
                            userId, username, BulkAuthzOutcome.SKIPPED, List.of(), List.of(), reasonCode),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        static UserEvaluation noChange(UUID userId, String username) {
            return new UserEvaluation(
                    new BulkAuthzItemResponse(
                            userId, username, BulkAuthzOutcome.NO_CHANGE, List.of(), List.of(), null),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        static UserEvaluation willChange(
                UUID userId,
                String username,
                List<String> added,
                List<String> removed,
                List<UUID> addedIds,
                List<UUID> removedIds,
                List<String> beforeCodes,
                List<String> afterCodes) {
            return new UserEvaluation(
                    new BulkAuthzItemResponse(
                            userId,
                            username,
                            BulkAuthzOutcome.WILL_CHANGE,
                            List.copyOf(added),
                            List.copyOf(removed),
                            null),
                    List.copyOf(addedIds),
                    List.copyOf(removedIds),
                    List.copyOf(beforeCodes),
                    List.copyOf(afterCodes));
        }
    }

    private record BatchEvaluation(
            int willChange, int noChange, int skipped, List<UserEvaluation> evaluations) {
        BulkAuthzResponse toResponse(UUID batchId) {
            return new BulkAuthzResponse(
                    batchId,
                    willChange,
                    noChange,
                    skipped,
                    evaluations.stream().map(UserEvaluation::item).toList());
        }
    }
}
