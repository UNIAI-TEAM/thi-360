package vn.unicom.thi360.identity.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.CreateRoleRequest;
import vn.unicom.thi360.identity.api.RoleDetailResponse;
import vn.unicom.thi360.identity.api.RoleSummaryResponse;
import vn.unicom.thi360.identity.api.UpdateRolePermissionsRequest;
import vn.unicom.thi360.identity.api.UpdateRoleRequest;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RolePermission;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.identity.spi.PermissionObject;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;

@Service
public class RoleService {

    public static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("code", "name", "access", "status", "createdAt", "updatedAt");

    private static final String MODULE = "IDENTITY";
    private static final String ERROR_ROLE_CODE_TAKEN = "ROLE_CODE_TAKEN";
    private static final String ERROR_ROLE_ACCESS_LOCKED = "ROLE_ACCESS_LOCKED";
    private static final String ERROR_ROLE_IN_USE = "ROLE_IN_USE";
    private static final String ERROR_ROLE_IS_SYSTEM = "ROLE_IS_SYSTEM";
    private static final String ERROR_PERMISSION_SCOPE_MISMATCH = "PERMISSION_SCOPE_MISMATCH";

    private static final String COUNT_ROLE_USERS_SQL =
            """
            SELECT COUNT(*)
            FROM identity.user_role ur
            JOIN identity.user_account u ON u.id = ur.user_id
            WHERE ur.role_id = ?
              AND u.deleted_at IS NULL
            """;

    private static final String ROLE_PERMISSION_CODES_SQL =
            """
            SELECT p.code
            FROM identity.role_permission rp
            JOIN identity.permission p ON p.id = rp.permission_id
            WHERE rp.role_id = ?
            ORDER BY p.bit_index ASC
            """;

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionSetNormalizer permissionSetNormalizer;
    private final PrivilegeGuard privilegeGuard;
    private final AuthzSnapshotService authzSnapshotService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public RoleService(
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository,
            PermissionSetNormalizer permissionSetNormalizer,
            PrivilegeGuard privilegeGuard,
            AuthzSnapshotService authzSnapshotService,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.permissionSetNormalizer = permissionSetNormalizer;
        this.privilegeGuard = privilegeGuard;
        this.authzSnapshotService = authzSnapshotService;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleSummaryResponse> listRoles(
            String q, Access access, RoleStatus status, PageQuery pageQuery) {
        StringBuilder where = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();

        if (access != null) {
            where.append(" AND r.access = ?");
            params.add(access.code());
        }
        if (status != null) {
            where.append(" AND r.status = ?");
            params.add(status.code());
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            where.append(
                    " AND (LOWER(r.code) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(r.name) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(r.description, '')) LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        String countSql = "SELECT COUNT(*) FROM identity.role r WHERE " + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            return PageResponse.from(new PageImpl<>(List.of(), pageQuery.toPageable(), 0L));
        }

        String orderBy = buildOrderBy(pageQuery.sort());
        String dataSql =
                """
                SELECT r.id,
                       r.code,
                       r.name,
                       r.description,
                       r.access,
                       r.is_system,
                       r.status,
                       r.version,
                       r.created_at,
                       r.updated_at,
                       (SELECT COUNT(*) FROM identity.role_permission rp WHERE rp.role_id = r.id) AS permission_count,
                       (SELECT COUNT(*)
                        FROM identity.user_role ur
                        JOIN identity.user_account u ON u.id = ur.user_id
                        WHERE ur.role_id = r.id
                          AND u.deleted_at IS NULL) AS user_count
                FROM identity.role r
                WHERE\s"""
                        + where
                        + " "
                        + orderBy
                        + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageQuery.size());
        dataParams.add((long) pageQuery.page() * pageQuery.size());

        List<RoleSummaryResponse> items =
                jdbcTemplate.query(dataSql, (rs, rowNum) -> mapSummary(rs), dataParams.toArray());
        return PageResponse.from(new PageImpl<>(items, pageQuery.toPageable(), totalCount));
    }

    @Transactional(readOnly = true)
    public RoleDetailResponse getRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return toDetail(role);
    }

    @Transactional
    public RoleDetailResponse createRole(CreateRoleRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String code = request.code().trim();
        String codeNormalized = Normalizer.normalize(code);
        if (roleRepository.existsByCodeNormalized(codeNormalized)) {
            throw new ConflictException(ERROR_ROLE_CODE_TAKEN);
        }

        Set<Permission> requested = parsePermissions(request.permissionCodes());
        validateScope(request.access(), requested);
        Set<Permission> normalized = permissionSetNormalizer.normalize(requested);
        privilegeGuard.assertCanGrant(actor, normalized);

        Instant now = Instant.now(clock);
        RoleStatus initialStatus = request.status() == null ? RoleStatus.ACTIVE : request.status();
        Role role = new Role(
                Ids.newId(),
                code,
                request.name(),
                request.description(),
                request.access(),
                false,
                initialStatus,
                now,
                actor.id());
        role = roleRepository.saveAndFlush(role);

        List<String> savedCodes = replaceRolePermissions(role.getId(), normalized, actor.id(), now);

        Map<String, Object> after = roleAuditState(role, savedCodes);
        recordAudit(actor, "ROLE_CREATED", role.getId(), role.getCode(), null, after, null);

        return new RoleDetailResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getAccess(),
                role.isSystem(),
                role.getStatus(),
                savedCodes.size(),
                0L,
                savedCodes,
                role.getVersion(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    @Transactional
    public RoleDetailResponse updateRole(UUID roleId, UpdateRoleRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (!Objects.equals(role.getVersion(), request.version())) {
            throw new VersionConflictException();
        }

        long userCount = countRoleUsers(roleId);
        List<String> existingCodes = findRolePermissionCodes(roleId);
        Map<String, Object> before = roleAuditState(role, existingCodes);

        String newCode = null;
        if (request.code() != null) {
            String trimmed = request.code().trim();
            if (trimmed.isEmpty()) {
                throw new ValidationException(List.of(
                        new FieldError("code", "NOT_BLANK", "Mã vai trò không được để trống")));
            }
            if (!trimmed.equals(role.getCode())) {
                if (role.isSystem()) {
                    throw new ConflictException(ERROR_ROLE_IS_SYSTEM);
                }
                String normalized = Normalizer.normalize(trimmed);
                if (roleRepository.existsByCodeNormalizedAndIdNot(normalized, roleId)) {
                    throw new ConflictException(ERROR_ROLE_CODE_TAKEN);
                }
                newCode = trimmed;
            }
        }

        String newName = null;
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ValidationException(List.of(
                        new FieldError("name", "NOT_BLANK", "Tên vai trò không được để trống")));
            }
            newName = request.name();
        }

        Access newAccess = request.access();
        boolean accessChanged = newAccess != null && newAccess != role.getAccess();
        if (accessChanged && (role.isSystem() || userCount > 0)) {
            throw new ConflictException(ERROR_ROLE_ACCESS_LOCKED);
        }

        RoleStatus newStatus = request.status();
        boolean statusChanged = newStatus != null && newStatus != role.getStatus();

        String newDescription = request.description() != null ? request.description() : role.getDescription();
        Instant now = Instant.now(clock);
        role.updateDetails(newCode, newName, newDescription, newAccess, newStatus, now, actor.id());
        role = roleRepository.saveAndFlush(role);

        List<String> afterCodes = existingCodes;
        if (accessChanged && role.getAccess() == Access.EXAM && !existingCodes.isEmpty()) {
            rolePermissionRepository.deleteAllByIdRoleId(roleId);
            rolePermissionRepository.flush();
            afterCodes = List.of();
        }

        if (statusChanged || accessChanged) {
            authzSnapshotService.evictByRole(roleId);
        }

        Map<String, Object> after = roleAuditState(role, afterCodes);
        recordAudit(actor, "ROLE_UPDATED", role.getId(), role.getCode(), before, after, null);

        return new RoleDetailResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getAccess(),
                role.isSystem(),
                role.getStatus(),
                afterCodes.size(),
                userCount,
                afterCodes,
                role.getVersion(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (role.isSystem()) {
            throw new ConflictException(ERROR_ROLE_IS_SYSTEM);
        }
        long userCount = countRoleUsers(roleId);
        if (userCount > 0) {
            throw new ConflictException(ERROR_ROLE_IN_USE);
        }

        List<String> existingCodes = findRolePermissionCodes(roleId);
        Map<String, Object> before = roleAuditState(role, existingCodes);

        rolePermissionRepository.deleteAllByIdRoleId(roleId);
        roleRepository.delete(role);
        roleRepository.flush();

        recordAudit(actor, "ROLE_DELETED", role.getId(), role.getCode(), before, null, null);
    }

    @Transactional
    public RoleDetailResponse updateRolePermissions(UUID roleId, UpdateRolePermissionsRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (request.version() != null && !Objects.equals(role.getVersion(), request.version())) {
            throw new VersionConflictException();
        }

        Set<Permission> requested = parsePermissions(request.permissionCodes());
        validateScope(role.getAccess(), requested);

        List<String> beforeCodes = findRolePermissionCodes(roleId);
        Set<Permission> currentKnown = parseKnownCodesIgnoreObsolete(beforeCodes);
        validateNoViewDroppedWhileDependentRemains(currentKnown, requested);

        Set<Permission> normalized = permissionSetNormalizer.normalize(requested);
        privilegeGuard.assertCanGrant(actor, normalized);

        Instant now = Instant.now(clock);
        List<String> afterCodes = replaceRolePermissions(roleId, normalized, actor.id(), now);

        role.markUpdated(now, actor.id());
        role = roleRepository.saveAndFlush(role);

        authzSnapshotService.evictByRole(roleId);

        recordAudit(
                actor,
                "ROLE_PERMISSIONS_CHANGED",
                role.getId(),
                role.getCode(),
                Map.of("permissionCodes", beforeCodes),
                Map.of("permissionCodes", afterCodes),
                null);

        long userCount = countRoleUsers(roleId);
        return new RoleDetailResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getAccess(),
                role.isSystem(),
                role.getStatus(),
                afterCodes.size(),
                userCount,
                afterCodes,
                role.getVersion(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    static Set<Permission> parsePermissions(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        EnumSet<Permission> parsed = EnumSet.noneOf(Permission.class);
        for (String raw : codes) {
            if (raw == null || raw.isBlank()) {
                throw new ValidationException(List.of(
                        new FieldError("permissionCodes", "INVALID_PERMISSION", "Mã quyền không được để trống")));
            }
            Permission p = Permission.findByCode(raw)
                    .orElseThrow(() -> new ValidationException(List.of(
                            new FieldError("permissionCodes", "UNKNOWN_PERMISSION", "Mã quyền không hợp lệ: " + raw))));
            parsed.add(p);
        }
        return parsed;
    }

    static void validateNoViewDroppedWhileDependentRemains(
            Set<Permission> current, Set<Permission> requested) {
        if (current == null || current.isEmpty() || requested == null || requested.isEmpty()) {
            return;
        }
        for (Permission p : requested) {
            if (p.kind() != Permission.Kind.VIEW) {
                Permission requiredView = Permission.viewPermissionOf(p.object()).orElse(null);
                if (requiredView != null
                        && current.contains(requiredView)
                        && !requested.contains(requiredView)) {
                    throw new ValidationException(
                            PermissionSetNormalizer.ERROR_PERMISSION_REQUIRES_VIEW,
                            null,
                            List.of(new FieldError(
                                    "permissionCodes",
                                    PermissionSetNormalizer.ERROR_PERMISSION_REQUIRES_VIEW,
                                    "Phải giữ quyền " + requiredView.code() + " khi còn quyền " + p.code())));
                }
            }
        }
    }

    private static void validateScope(Access roleAccess, Set<Permission> permissions) {
        if (roleAccess == Access.EXAM && permissions != null) {
            for (Permission p : permissions) {
                if (p.scope() == Access.MANAGE) {
                    throw new ConflictException(ERROR_PERMISSION_SCOPE_MISMATCH);
                }
            }
        }
    }

    private List<String> replaceRolePermissions(
            UUID roleId, Set<Permission> normalized, UUID actorId, Instant now) {
        rolePermissionRepository.deleteAllByIdRoleId(roleId);
        rolePermissionRepository.flush();
        if (normalized.isEmpty()) {
            return List.of();
        }

        Map<String, PermissionEntity> byCode = new LinkedHashMap<>();
        for (PermissionEntity entity : permissionRepository.findAll()) {
            byCode.put(entity.getCode(), entity);
        }

        List<Permission> sorted = normalized.stream()
                .sorted((a, b) -> Integer.compare(a.bitIndex(), b.bitIndex()))
                .toList();
        List<RolePermission> rows = new ArrayList<>(sorted.size());
        List<String> savedCodes = new ArrayList<>(sorted.size());
        for (Permission p : sorted) {
            PermissionEntity entity = byCode.get(p.code());
            if (entity == null) {
                throw new IllegalStateException("Permission missing from catalog table: " + p.code());
            }
            rows.add(new RolePermission(roleId, entity.getId(), actorId, now));
            savedCodes.add(p.code());
        }
        rolePermissionRepository.saveAll(rows);
        rolePermissionRepository.flush();
        return List.copyOf(savedCodes);
    }

    private RoleDetailResponse toDetail(Role role) {
        List<String> permissionCodes = findRolePermissionCodes(role.getId());
        long userCount = countRoleUsers(role.getId());
        return new RoleDetailResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getAccess(),
                role.isSystem(),
                role.getStatus(),
                permissionCodes.size(),
                userCount,
                permissionCodes,
                role.getVersion(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    private long countRoleUsers(UUID roleId) {
        Long count = jdbcTemplate.queryForObject(COUNT_ROLE_USERS_SQL, Long.class, roleId);
        return count == null ? 0L : count;
    }

    private List<String> findRolePermissionCodes(UUID roleId) {
        return jdbcTemplate.query(
                ROLE_PERMISSION_CODES_SQL, (rs, rowNum) -> rs.getString("code"), roleId);
    }

    private static Set<Permission> parseKnownCodesIgnoreObsolete(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (String code : codes) {
            Permission.findByCode(code).ifPresent(result::add);
        }
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
                .target("ROLE", targetId.toString(), targetLabel)
                .before(before)
                .after(after)
                .reason(reason)
                .actor(actor.id(), actor.username(), null, actorType)
                .build());
    }

    private static Map<String, Object> roleAuditState(Role role, List<String> permissionCodes) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", role.getCode());
        state.put("name", role.getName());
        state.put("description", role.getDescription());
        state.put("access", role.getAccess().name());
        state.put("status", role.getStatus().name());
        state.put("system", role.isSystem());
        state.put("permissionCodes", permissionCodes);
        return state;
    }

    private static RoleSummaryResponse mapSummary(ResultSet rs) throws SQLException {
        return new RoleSummaryResponse(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                Access.fromCode(rs.getShort("access")),
                rs.getBoolean("is_system"),
                RoleStatus.fromCode(rs.getShort("status")),
                rs.getLong("permission_count"),
                rs.getLong("user_count"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY r.is_system DESC, r.created_at ASC, r.id ASC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String col = switch (order.getProperty()) {
                case "code" -> "r.code";
                case "name" -> "r.name";
                case "access" -> "r.access";
                case "status" -> "r.status";
                case "createdAt" -> "r.created_at";
                case "updatedAt" -> "r.updated_at";
                default -> null;
            };
            if (col != null) {
                clauses.add(col + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        if (clauses.isEmpty()) {
            return "ORDER BY r.is_system DESC, r.created_at ASC, r.id ASC";
        }
        clauses.add("r.id ASC");
        return "ORDER BY " + String.join(", ", clauses);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
