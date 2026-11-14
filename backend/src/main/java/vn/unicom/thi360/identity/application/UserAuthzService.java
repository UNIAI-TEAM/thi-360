package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
import vn.unicom.thi360.identity.api.EffectivePermissionItemResponse;
import vn.unicom.thi360.identity.api.EffectivePermissionsResponse;
import vn.unicom.thi360.identity.api.PermissionSourceResponse;
import vn.unicom.thi360.identity.api.UpdateUserPermissionsRequest;
import vn.unicom.thi360.identity.api.UpdateUserRolesRequest;
import vn.unicom.thi360.identity.api.UserDirectPermissionItemResponse;
import vn.unicom.thi360.identity.api.UserDirectPermissionsResponse;
import vn.unicom.thi360.identity.api.UserRoleItemResponse;
import vn.unicom.thi360.identity.api.UserRolesResponse;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserPermission;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserPermissionRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.identity.spi.PermissionObject;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.web.AppMessages;
@Service
public class UserAuthzService {

    private static final String MODULE = "IDENTITY";
    private static final String ERROR_ROLE_INACTIVE = "ROLE_INACTIVE";
    private static final String ERROR_DIRECT_PERMISSION_REQUIRES_MANAGE = "DIRECT_PERMISSION_REQUIRES_MANAGE";
    private static final String ERROR_REASON_REQUIRED = "REASON_REQUIRED";

    private static final String USER_ROLES_SQL =
            """
            SELECT r.id AS role_id,
                   r.code AS role_code,
                   r.name AS role_name,
                   r.access AS role_access,
                   r.status AS role_status,
                   r.is_system AS role_system,
                   ur.assigned_by,
                   ur.assigned_at
            FROM identity.user_role ur
            JOIN identity.role r ON r.id = ur.role_id
            WHERE ur.user_id = ?
            ORDER BY r.is_system DESC, r.code ASC
            """;

    private static final String USER_DIRECT_PERMS_SQL =
            """
            SELECT p.id AS perm_id,
                   p.code AS perm_code,
                   p.bit_index,
                   p.name AS perm_name,
                   p.group_code,
                   up.granted_by,
                   up.granted_at,
                   up.reason
            FROM identity.user_permission up
            JOIN identity.permission p ON p.id = up.permission_id
            WHERE up.user_id = ?
            ORDER BY p.bit_index ASC
            """;

    private static final String USER_ACTIVE_ROLE_PERMS_SQL =
            """
            SELECT r.id AS role_id,
                   r.code AS role_code,
                   r.name AS role_name,
                   r.access AS role_access,
                   p.id AS perm_id,
                   p.code AS perm_code,
                   p.bit_index,
                   p.name AS perm_name,
                   p.group_code
            FROM identity.user_role ur
            JOIN identity.role r ON r.id = ur.role_id
            LEFT JOIN identity.role_permission rp ON rp.role_id = r.id
            LEFT JOIN identity.permission p ON p.id = rp.permission_id
            WHERE ur.user_id = ?
              AND r.status = 1
            ORDER BY p.bit_index ASC NULLS LAST, r.is_system DESC, r.code ASC
            """;

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PermissionQueryService permissionQueryService;
    private final PrivilegeGuard privilegeGuard;
    private final AuthzSnapshotService authzSnapshotService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final AppMessages messages;

    public UserAuthzService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository,
            UserRoleRepository userRoleRepository,
            UserPermissionRepository userPermissionRepository,
            PermissionQueryService permissionQueryService,
            PrivilegeGuard privilegeGuard,
            AuthzSnapshotService authzSnapshotService,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            AppMessages messages) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.permissionQueryService = permissionQueryService;
        this.privilegeGuard = privilegeGuard;
        this.authzSnapshotService = authzSnapshotService;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public UserRolesResponse getUserRoles(UUID userId) {
        requireActiveUser(userId);
        return buildUserRolesResponse(userId);
    }

    @Transactional
    public UserRolesResponse updateUserRoles(UUID userId, UpdateUserRolesRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        UserAccount user = requireActiveUserForUpdate(userId);

        Set<UUID> targetRoleIds = new LinkedHashSet<>(request.roleIds());
        Map<UUID, Role> targetRoles = new LinkedHashMap<>();
        if (!targetRoleIds.isEmpty()) {
            for (Role role : roleRepository.findAllById(targetRoleIds)) {
                targetRoles.put(role.getId(), role);
            }
            if (targetRoles.size() != targetRoleIds.size()) {
                throw new NotFoundException("NOT_FOUND", null);
            }
        }

        List<UserRole> existingAssignments = userRoleRepository.findAllByIdUserId(userId);
        Map<UUID, UserRole> existingByRoleId = new LinkedHashMap<>();
        for (UserRole ur : existingAssignments) {
            existingByRoleId.put(ur.getId().getRoleId(), ur);
        }

        List<Role> addedRoles = new ArrayList<>();
        for (UUID roleId : targetRoleIds) {
            if (!existingByRoleId.containsKey(roleId)) {
                Role role = targetRoles.get(roleId);
                if (role.getStatus() != RoleStatus.ACTIVE) {
                    throw new ConflictException(ERROR_ROLE_INACTIVE);
                }
                addedRoles.add(role);
            }
        }

        Map<UUID, Set<Permission>> rolePermsByRoleId = loadPermissionsForRoles(targetRoleIds);
        EnumSet<Permission> addedRolePerms = EnumSet.noneOf(Permission.class);
        for (Role added : addedRoles) {
            addedRolePerms.addAll(rolePermsByRoleId.getOrDefault(added.getId(), Set.of()));
        }
        privilegeGuard.assertCanGrant(actor, addedRolePerms);

        // Compute resulting effective permissions to check SELF_LOCKOUT
        boolean resultingHasManage = targetRoles.values().stream()
                .anyMatch(r -> r.getStatus() == RoleStatus.ACTIVE && r.getAccess() == Access.MANAGE);
        EnumSet<Permission> resultingEffective = EnumSet.noneOf(Permission.class);
        for (Role role : targetRoles.values()) {
            if (role.getStatus() == RoleStatus.ACTIVE) {
                resultingEffective.addAll(rolePermsByRoleId.getOrDefault(role.getId(), Set.of()));
            }
        }
        if (resultingHasManage) {
            resultingEffective.addAll(loadDirectPermissionsEnum(userId));
        }
        privilegeGuard.assertNoSelfLockout(actor, userId, resultingEffective);

        List<String> beforeCodes = loadRoleCodes(existingByRoleId.keySet());
        Instant now = Instant.now(clock);

        userRoleRepository.deleteAllByIdUserId(userId);
        userRoleRepository.flush();

        List<UserRole> toSave = new ArrayList<>(targetRoleIds.size());
        for (UUID roleId : targetRoleIds) {
            UserRole prev = existingByRoleId.get(roleId);
            if (prev != null) {
                toSave.add(new UserRole(userId, roleId, prev.getAssignedBy(), prev.getAssignedAt()));
            } else {
                toSave.add(new UserRole(userId, roleId, actor.id(), now));
            }
        }
        if (!toSave.isEmpty()) {
            userRoleRepository.saveAll(toSave);
            userRoleRepository.flush();
        }

        authzSnapshotService.evict(userId);

        List<String> afterCodes = targetRoleIds.stream()
                .map(id -> targetRoles.get(id).getCode())
                .sorted()
                .toList();
        recordAudit(
                actor,
                "USER_ROLES_CHANGED",
                userId,
                user.getUsername(),
                Map.of("roleCodes", beforeCodes),
                Map.of("roleCodes", afterCodes),
                null);

        return buildUserRolesResponse(userId);
    }

    @Transactional(readOnly = true)
    public UserDirectPermissionsResponse getUserPermissions(UUID userId) {
        requireActiveUser(userId);
        boolean hasManage = hasActiveManageRole(userId);
        return buildUserPermissionsResponse(userId, hasManage);
    }

    @Transactional
    public UserDirectPermissionsResponse updateUserPermissions(
            UUID userId, UpdateUserPermissionsRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        UserAccount user = requireActiveUserForUpdate(userId);

        Set<Permission> requested = RoleService.parsePermissions(request.permissionCodes(), messages);
        List<UserPermission> existingRows = userPermissionRepository.findAllByIdUserId(userId);
        Map<UUID, PermissionEntity> allPermissionsById = new HashMap<>();
        Map<String, PermissionEntity> allPermissionsByCode = new HashMap<>();
        for (PermissionEntity pe : permissionRepository.findAll()) {
            allPermissionsById.put(pe.getId(), pe);
            allPermissionsByCode.put(pe.getCode(), pe);
        }

        Map<Permission, UserPermission> existingByEnum = new LinkedHashMap<>();
        List<String> beforeCodes = new ArrayList<>();
        for (UserPermission up : existingRows) {
            PermissionEntity pe = allPermissionsById.get(up.getId().getPermissionId());
            if (pe != null) {
                beforeCodes.add(pe.getCode());
                Permission.findByCode(pe.getCode()).ifPresent(p -> existingByEnum.put(p, up));
            }
        }
        Collections.sort(beforeCodes);

        ActiveRoleSummary activeRoleSummary = loadActiveRoleSummary(userId);
        boolean hasManage = activeRoleSummary.hasManageRole();
        Set<Permission> rolePerms = activeRoleSummary.rolePermissions();

        // Check if user dropped VIEW of an object while keeping a non-VIEW direct permission (and not covered by role)
        for (Permission p : requested) {
            if (p.kind() != Permission.Kind.VIEW) {
                Permission requiredView = Permission.viewPermissionOf(p.object()).orElse(null);
                if (requiredView != null
                        && existingByEnum.containsKey(requiredView)
                        && !requested.contains(requiredView)
                        && !rolePerms.contains(requiredView)) {
                    throw new ValidationException(
                            PermissionSetNormalizer.ERROR_PERMISSION_REQUIRES_VIEW,
                            null,
                            List.of(new FieldError(
                                    "permissionCodes",
                                    PermissionSetNormalizer.ERROR_PERMISSION_REQUIRES_VIEW,
                                    messages.get("validation.permission.keepView", requiredView.code(), p.code()))));
                }
            }
        }

        // Normalize newly requested non-VIEW permissions by auto-adding VIEW if not already present via role or request
        EnumSet<Permission> normalizedTarget = requested.isEmpty()
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(requested);
        for (Permission p : requested) {
            if (p.kind() != Permission.Kind.VIEW) {
                Permission requiredView = Permission.viewPermissionOf(p.object()).orElse(null);
                if (requiredView != null && !normalizedTarget.contains(requiredView) && !rolePerms.contains(requiredView)) {
                    normalizedTarget.add(requiredView);
                }
            }
        }

        EnumSet<Permission> added = EnumSet.noneOf(Permission.class);
        for (Permission p : normalizedTarget) {
            if (!existingByEnum.containsKey(p)) {
                added.add(p);
            }
        }

        String trimmedReason = request.reason() == null ? null : request.reason().trim();
        if (!added.isEmpty()) {
            if (!hasManage) {
                throw new ConflictException(ERROR_DIRECT_PERMISSION_REQUIRES_MANAGE);
            }
            if (trimmedReason == null || trimmedReason.isEmpty()) {
                throw new ValidationException(
                        "VALIDATION_FAILED",
                        null,
                        List.of(new FieldError(
                                "reason", ERROR_REASON_REQUIRED, messages.get("REASON_REQUIRED"))));
            }
            privilegeGuard.assertCanGrant(actor, added);
        }

        EnumSet<Permission> resultingEffective = EnumSet.noneOf(Permission.class);
        resultingEffective.addAll(rolePerms);
        if (hasManage) {
            resultingEffective.addAll(normalizedTarget);
        }
        privilegeGuard.assertNoSelfLockout(actor, userId, resultingEffective);

        Instant now = Instant.now(clock);
        userPermissionRepository.deleteAllByIdUserId(userId);
        userPermissionRepository.flush();

        List<UserPermission> toSave = new ArrayList<>(normalizedTarget.size());
        List<String> afterCodes = new ArrayList<>(normalizedTarget.size());
        for (Permission p : normalizedTarget) {
            PermissionEntity pe = allPermissionsByCode.get(p.code());
            if (pe == null) {
                throw new IllegalStateException("Permission missing from catalog table: " + p.code());
            }
            UserPermission existing = existingByEnum.get(p);
            if (existing != null) {
                toSave.add(new UserPermission(
                        userId, pe.getId(), existing.getGrantedBy(), existing.getGrantedAt(), existing.getReason()));
            } else {
                toSave.add(new UserPermission(userId, pe.getId(), actor.id(), now, trimmedReason));
            }
            afterCodes.add(p.code());
        }
        Collections.sort(afterCodes);
        if (!toSave.isEmpty()) {
            userPermissionRepository.saveAll(toSave);
            userPermissionRepository.flush();
        }

        authzSnapshotService.evict(userId);

        recordAudit(
                actor,
                "USER_PERMISSIONS_CHANGED",
                userId,
                user.getUsername(),
                Map.of("permissionCodes", beforeCodes),
                Map.of("permissionCodes", afterCodes),
                trimmedReason);

        return buildUserPermissionsResponse(userId, hasManage);
    }

    @Transactional(readOnly = true)
    public EffectivePermissionsResponse getEffectivePermissions(UUID userId) {
        requireActiveUser(userId);

        Map<UUID, EffectivePermBuilder> effectiveByPermId = new LinkedHashMap<>();
        boolean[] hasManageHolder = {false};

        jdbcTemplate.query(
                USER_ACTIVE_ROLE_PERMS_SQL,
                rs -> {
                    Access roleAccess = Access.fromCode(rs.getShort("role_access"));
                    if (roleAccess == Access.MANAGE) {
                        hasManageHolder[0] = true;
                    }
                    UUID permId = rs.getObject("perm_id", UUID.class);
                    if (permId != null) {
                        String code = rs.getString("perm_code");
                        int bitIndex = rs.getInt("bit_index");
                        String name = rs.getString("perm_name");
                        String groupCode = rs.getString("group_code");
                        EffectivePermBuilder builder = effectiveByPermId.computeIfAbsent(
                                permId,
                                id -> new EffectivePermBuilder(
                                        id, code, bitIndex, name, groupCode, resolveGroupName(code, groupCode)));
                        UUID roleId = rs.getObject("role_id", UUID.class);
                        String roleCode = rs.getString("role_code");
                        String roleName = rs.getString("role_name");
                        builder.addRoleSource(roleId, roleCode, roleName);
                    }
                },
                userId);

        boolean hasManage = hasManageHolder[0];
        List<DirectPermRow> directRows = loadDirectPermRows(userId);
        Set<UUID> actorIds = new LinkedHashSet<>();
        for (DirectPermRow row : directRows) {
            if (row.grantedBy() != null) {
                actorIds.add(row.grantedBy());
            }
        }
        Map<UUID, String> usernames = permissionQueryService.resolveActorUsernames(actorIds);

        List<UserDirectPermissionItemResponse> inactiveDirect = new ArrayList<>();
        for (DirectPermRow row : directRows) {
            String grantedByUsername = row.grantedBy() == null ? null : usernames.get(row.grantedBy());
            if (hasManage) {
                EffectivePermBuilder builder = effectiveByPermId.computeIfAbsent(
                        row.permId(),
                        id -> new EffectivePermBuilder(
                                id,
                                row.code(),
                                row.bitIndex(),
                                row.name(),
                                row.groupCode(),
                                resolveGroupName(row.code(), row.groupCode())));
                builder.addDirectSource(row.grantedBy(), grantedByUsername, row.grantedAt(), row.reason());
            } else {
                inactiveDirect.add(new UserDirectPermissionItemResponse(
                        row.permId(),
                        row.code(),
                        row.name(),
                        row.groupCode(),
                        resolveGroupName(row.code(), row.groupCode()),
                        row.grantedBy(),
                        grantedByUsername,
                        row.grantedAt(),
                        row.reason(),
                        false));
            }
        }

        List<EffectivePermissionItemResponse> permissions = effectiveByPermId.values().stream()
                .sorted((a, b) -> Integer.compare(a.bitIndex, b.bitIndex))
                .map(EffectivePermBuilder::build)
                .toList();

        return new EffectivePermissionsResponse(
                userId,
                hasManage ? Access.MANAGE : Access.EXAM,
                hasManage,
                permissions,
                List.copyOf(inactiveDirect));
    }

    private UserAccount requireActiveUser(UUID userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (user.getDeletedAt() != null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        return user;
    }

    private UserAccount requireActiveUserForUpdate(UUID userId) {
        jdbcTemplate.query(
                "SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE",
                rs -> {},
                userId);
        return requireActiveUser(userId);
    }

    private UserRolesResponse buildUserRolesResponse(UUID userId) {
        List<UserRoleItemResponse> roles = jdbcTemplate.query(
                USER_ROLES_SQL,
                (rs, rowNum) -> new UserRoleItemResponse(
                        rs.getObject("role_id", UUID.class),
                        rs.getString("role_code"),
                        rs.getString("role_name"),
                        Access.fromCode(rs.getShort("role_access")),
                        RoleStatus.fromCode(rs.getShort("role_status")),
                        rs.getBoolean("role_system"),
                        rs.getObject("assigned_by", UUID.class),
                        rs.getTimestamp("assigned_at").toInstant()),
                userId);
        Access access = roles.stream()
                .anyMatch(r -> r.status() == RoleStatus.ACTIVE && r.access() == Access.MANAGE)
                ? Access.MANAGE
                : Access.EXAM;
        return new UserRolesResponse(userId, access, roles);
    }

    private UserDirectPermissionsResponse buildUserPermissionsResponse(UUID userId, boolean hasManage) {
        List<DirectPermRow> rows = loadDirectPermRows(userId);
        Set<UUID> actorIds = new LinkedHashSet<>();
        for (DirectPermRow row : rows) {
            if (row.grantedBy() != null) {
                actorIds.add(row.grantedBy());
            }
        }
        Map<UUID, String> usernames = permissionQueryService.resolveActorUsernames(actorIds);
        List<UserDirectPermissionItemResponse> items = rows.stream()
                .map(r -> new UserDirectPermissionItemResponse(
                        r.permId(),
                        r.code(),
                        r.name(),
                        r.groupCode(),
                        resolveGroupName(r.code(), r.groupCode()),
                        r.grantedBy(),
                        r.grantedBy() == null ? null : usernames.get(r.grantedBy()),
                        r.grantedAt(),
                        r.reason(),
                        hasManage))
                .toList();
        return new UserDirectPermissionsResponse(userId, hasManage, items);
    }

    private List<DirectPermRow> loadDirectPermRows(UUID userId) {
        return jdbcTemplate.query(
                USER_DIRECT_PERMS_SQL,
                (rs, rowNum) -> new DirectPermRow(
                        rs.getObject("perm_id", UUID.class),
                        rs.getString("perm_code"),
                        rs.getInt("bit_index"),
                        rs.getString("perm_name"),
                        rs.getString("group_code"),
                        rs.getObject("granted_by", UUID.class),
                        rs.getTimestamp("granted_at").toInstant(),
                        rs.getString("reason")),
                userId);
    }

    private boolean hasActiveManageRole(UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM identity.user_role ur
                    JOIN identity.role r ON r.id = ur.role_id
                    WHERE ur.user_id = ?
                      AND r.status = 1
                      AND r.access = 1
                )
                """,
                Boolean.class,
                userId);
        return Boolean.TRUE.equals(exists);
    }

    private ActiveRoleSummary loadActiveRoleSummary(UUID userId) {
        boolean[] hasManage = {false};
        EnumSet<Permission> perms = EnumSet.noneOf(Permission.class);
        jdbcTemplate.query(
                USER_ACTIVE_ROLE_PERMS_SQL,
                rs -> {
                    if (Access.fromCode(rs.getShort("role_access")) == Access.MANAGE) {
                        hasManage[0] = true;
                    }
                    String code = rs.getString("perm_code");
                    if (code != null) {
                        Permission.findByCode(code).ifPresent(perms::add);
                    }
                },
                userId);
        return new ActiveRoleSummary(hasManage[0], perms);
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

    private Set<Permission> loadDirectPermissionsEnum(UUID userId) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (DirectPermRow row : loadDirectPermRows(userId)) {
            Permission.findByCode(row.code()).ifPresent(result::add);
        }
        return result;
    }

    private List<String> loadRoleCodes(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .sorted()
                .toList();
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
                .target("USER", targetId.toString(), targetLabel)
                .before(before)
                .after(after)
                .reason(reason)
                .actor(actor.id(), actor.username(), null, actorType)
                .build());
    }

    private static String resolveGroupName(String code, String groupCode) {
        return Permission.findByCode(code)
                .map(p -> p.object().displayName())
                .orElseGet(() -> {
                    for (PermissionObject obj : PermissionObject.values()) {
                        if (obj.code().equalsIgnoreCase(groupCode)) {
                            return obj.displayName();
                        }
                    }
                    return groupCode;
                });
    }

    private record DirectPermRow(
            UUID permId,
            String code,
            int bitIndex,
            String name,
            String groupCode,
            UUID grantedBy,
            Instant grantedAt,
            String reason) {
    }

    private record ActiveRoleSummary(boolean hasManageRole, Set<Permission> rolePermissions) {
    }

    private static final class EffectivePermBuilder {
        private final UUID permissionId;
        private final String code;
        private final int bitIndex;
        private final String name;
        private final String groupCode;
        private final String groupName;
        private final List<PermissionSourceResponse> sources = new ArrayList<>();
        private final Set<UUID> seenRoleIds = new HashSet<>();
        private boolean hasDirect;

        private EffectivePermBuilder(
                UUID permissionId, String code, int bitIndex, String name, String groupCode, String groupName) {
            this.permissionId = permissionId;
            this.code = code;
            this.bitIndex = bitIndex;
            this.name = name;
            this.groupCode = groupCode;
            this.groupName = groupName;
        }

        void addRoleSource(UUID roleId, String roleCode, String roleName) {
            if (seenRoleIds.add(roleId)) {
                sources.add(PermissionSourceResponse.role(roleId, roleCode, roleName));
            }
        }

        void addDirectSource(UUID grantedBy, String grantedByUsername, Instant grantedAt, String reason) {
            if (!hasDirect) {
                hasDirect = true;
                sources.add(PermissionSourceResponse.direct(grantedBy, grantedByUsername, grantedAt, reason));
            }
        }

        EffectivePermissionItemResponse build() {
            return new EffectivePermissionItemResponse(
                    permissionId, code, name, groupCode, groupName, List.copyOf(sources));
        }
    }
}
