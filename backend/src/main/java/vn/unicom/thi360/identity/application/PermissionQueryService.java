package vn.unicom.thi360.identity.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.identity.api.PermissionResponse;
import vn.unicom.thi360.identity.api.PermissionSourceResponse;
import vn.unicom.thi360.identity.api.PermissionSourceType;
import vn.unicom.thi360.identity.api.PermissionUserResponse;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.identity.spi.PermissionObject;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.web.AppMessages;

@Service
public class PermissionQueryService {

    public static final Set<String> ALLOWED_USER_SORT_FIELDS =
            Set.of("username", "fullName", "email", "status", "createdAt");

    private static final int MAX_EXPORT_ROWS = 10_000;

    private static final String LIST_PERMISSIONS_SQL =
            """
            SELECT p.id,
                   p.code,
                   p.bit_index,
                   p.scope,
                   p.name,
                   p.group_code,
                   p.description,
                   (SELECT COUNT(*)
                    FROM identity.role_permission rp
                    WHERE rp.permission_id = p.id) AS role_count,
                   (SELECT COUNT(DISTINCT u.id)
                    FROM identity.user_account u
                    WHERE u.deleted_at IS NULL
                      AND (
                          EXISTS (
                              SELECT 1
                              FROM identity.user_role ur
                              JOIN identity.role r ON r.id = ur.role_id
                              JOIN identity.role_permission rp ON rp.role_id = r.id
                              WHERE ur.user_id = u.id
                                AND r.status = 1
                                AND rp.permission_id = p.id
                          )
                          OR (
                              EXISTS (
                                  SELECT 1
                                  FROM identity.user_permission up
                                  WHERE up.user_id = u.id
                                    AND up.permission_id = p.id
                              )
                              AND EXISTS (
                                  SELECT 1
                                  FROM identity.user_role ur2
                                  JOIN identity.role r2 ON r2.id = ur2.role_id
                                  WHERE ur2.user_id = u.id
                                    AND r2.status = 1
                                    AND r2.access = 1
                              )
                          )
                      )) AS user_count
            FROM identity.permission p
            ORDER BY p.bit_index ASC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PermissionRepository permissionRepository;
    private final PermissionUsersExportWriter exportWriter;
    private final AppMessages messages;

    public PermissionQueryService(
            JdbcTemplate jdbcTemplate,
            PermissionRepository permissionRepository,
            PermissionUsersExportWriter exportWriter,
            AppMessages messages) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionRepository = permissionRepository;
        this.exportWriter = exportWriter;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions(String q, String group, Access scope) {
        String keyword = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        String groupFilter = group == null || group.isBlank() ? null : group.trim().toUpperCase(Locale.ROOT);
        Locale locale = messages.currentLocale();

        List<PermissionResponse> all = jdbcTemplate.query(LIST_PERMISSIONS_SQL, (rs, rowNum) -> mapPermission(rs, locale));
        return all.stream()
                .filter(p -> groupFilter == null || groupFilter.equalsIgnoreCase(p.groupCode()))
                .filter(p -> scope == null || scope == p.scope())
                .filter(p -> keyword == null
                        || containsIgnoreCase(p.code(), keyword)
                        || containsIgnoreCase(p.name(), keyword)
                        || containsIgnoreCase(p.description(), keyword))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionUserResponse> findUsersByPermission(
            UUID permissionId,
            PermissionSourceType source,
            UserStatus status,
            String q,
            PageQuery pageQuery) {
        permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        FilterSql filter = buildUserFilter(permissionId, source, status, q);
        String countSql = "SELECT COUNT(*) FROM identity.user_account u WHERE " + filter.whereClause();
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, filter.params().toArray());
        long totalCount = total == null ? 0L : total;

        if (totalCount == 0L) {
            return PageResponse.from(new PageImpl<>(List.of(), pageQuery.toPageable(), 0L));
        }

        String orderBy = buildOrderBy(pageQuery.sort());
        String dataSql =
                "SELECT u.id, u.code, u.username, u.full_name, u.email, u.status "
                        + "FROM identity.user_account u WHERE "
                        + filter.whereClause()
                        + " "
                        + orderBy
                        + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(filter.params());
        dataParams.add(pageQuery.size());
        dataParams.add((long) pageQuery.page() * pageQuery.size());

        List<UserRow> rows = jdbcTemplate.query(dataSql, (rs, rowNum) -> mapUserRow(rs), dataParams.toArray());
        List<PermissionUserResponse> items = enrichWithSources(permissionId, rows);
        return PageResponse.from(new PageImpl<>(items, pageQuery.toPageable(), totalCount));
    }

    @Transactional(readOnly = true)
    public PermissionExportResult exportUsersByPermission(
            UUID permissionId,
            PermissionSourceType source,
            UserStatus status,
            String q,
            Sort sort) {
        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        FilterSql filter = buildUserFilter(permissionId, source, status, q);
        String orderBy = buildOrderBy(sort);
        String dataSql =
                "SELECT u.id, u.code, u.username, u.full_name, u.email, u.status "
                        + "FROM identity.user_account u WHERE "
                        + filter.whereClause()
                        + " "
                        + orderBy
                        + " LIMIT ?";
        List<Object> dataParams = new ArrayList<>(filter.params());
        dataParams.add(MAX_EXPORT_ROWS);

        List<UserRow> rows = jdbcTemplate.query(dataSql, (rs, rowNum) -> mapUserRow(rs), dataParams.toArray());
        List<PermissionUserResponse> items = enrichWithSources(permissionId, rows);
        byte[] content = exportWriter.write(permission.getCode(), items);
        String safeCode = permission.getCode().toLowerCase(Locale.ROOT).replace('.', '-');
        return new PermissionExportResult("permission-" + safeCode + "-users.xlsx", content);
    }

    private List<PermissionUserResponse> enrichWithSources(UUID permissionId, List<UserRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = rows.stream().map(UserRow::id).toList();
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));

        Map<UUID, List<PermissionSourceResponse>> sourcesByUser = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            sourcesByUser.put(userId, new ArrayList<>());
        }

        // Active role sources
        String roleSql =
                """
                SELECT ur.user_id, r.id AS role_id, r.code AS role_code, r.name AS role_name
                FROM identity.user_role ur
                JOIN identity.role r ON r.id = ur.role_id
                JOIN identity.role_permission rp ON rp.role_id = r.id
                WHERE ur.user_id IN (%s)
                  AND r.status = 1
                  AND rp.permission_id = ?
                ORDER BY r.is_system DESC, r.code ASC
                """.formatted(placeholders);
        List<Object> roleParams = new ArrayList<>(userIds);
        roleParams.add(permissionId);
        jdbcTemplate.query(
                roleSql,
                rs -> {
                    UUID userId = rs.getObject("user_id", UUID.class);
                    UUID roleId = rs.getObject("role_id", UUID.class);
                    String roleCode = rs.getString("role_code");
                    String roleName = rs.getString("role_name");
                    sourcesByUser.get(userId).add(PermissionSourceResponse.role(roleId, roleCode, roleName));
                },
                roleParams.toArray());

        // Direct permission sources (only effective when user holds at least one active MANAGE role)
        String directSql =
                """
                SELECT up.user_id, up.granted_by, up.granted_at, up.reason
                FROM identity.user_permission up
                WHERE up.user_id IN (%s)
                  AND up.permission_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM identity.user_role ur2
                      JOIN identity.role r2 ON r2.id = ur2.role_id
                      WHERE ur2.user_id = up.user_id
                        AND r2.status = 1
                        AND r2.access = 1
                  )
                """.formatted(placeholders);
        List<Object> directParams = new ArrayList<>(userIds);
        directParams.add(permissionId);
        List<DirectRow> directRows = jdbcTemplate.query(
                directSql,
                (rs, rowNum) -> new DirectRow(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("granted_by", UUID.class),
                        rs.getTimestamp("granted_at").toInstant(),
                        rs.getString("reason")),
                directParams.toArray());

        Set<UUID> actorIds = new LinkedHashSet<>();
        for (DirectRow d : directRows) {
            if (d.grantedBy() != null) {
                actorIds.add(d.grantedBy());
            }
        }
        Map<UUID, String> usernamesById = resolveActorUsernames(actorIds);
        for (DirectRow d : directRows) {
            String grantedByUsername = d.grantedBy() == null ? null : usernamesById.get(d.grantedBy());
            sourcesByUser
                    .get(d.userId())
                    .add(PermissionSourceResponse.direct(
                            d.grantedBy(), grantedByUsername, d.grantedAt(), d.reason()));
        }

        List<PermissionUserResponse> result = new ArrayList<>(rows.size());
        for (UserRow row : rows) {
            result.add(new PermissionUserResponse(
                    row.id(),
                    row.code(),
                    row.username(),
                    row.fullName(),
                    row.email(),
                    row.status(),
                    List.copyOf(sourcesByUser.getOrDefault(row.id(), List.of()))));
        }
        return result;
    }

    Map<UUID, String> resolveActorUsernames(Collection<UUID> actorIds) {
        if (actorIds == null || actorIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(actorIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT id, username FROM identity.user_account WHERE id IN (%s)
                UNION ALL
                SELECT id, username FROM identity.platform_admin WHERE id IN (%s)
                """.formatted(placeholders, placeholders);
        List<Object> params = new ArrayList<>(ids.size() * 2);
        params.addAll(ids);
        params.addAll(ids);
        Map<UUID, String> map = new HashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    map.put(rs.getObject("id", UUID.class), rs.getString("username"));
                },
                params.toArray());
        return map;
    }

    private FilterSql buildUserFilter(
            UUID permissionId, PermissionSourceType source, UserStatus status, String q) {
        StringBuilder where = new StringBuilder("u.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();

        String roleCond =
                """
                EXISTS (
                    SELECT 1
                    FROM identity.user_role ur
                    JOIN identity.role r ON r.id = ur.role_id
                    JOIN identity.role_permission rp ON rp.role_id = r.id
                    WHERE ur.user_id = u.id
                      AND r.status = 1
                      AND rp.permission_id = ?
                )
                """;
        String directCond =
                """
                (EXISTS (
                    SELECT 1
                    FROM identity.user_permission up
                    WHERE up.user_id = u.id
                      AND up.permission_id = ?
                ) AND EXISTS (
                    SELECT 1
                    FROM identity.user_role ur2
                    JOIN identity.role r2 ON r2.id = ur2.role_id
                    WHERE ur2.user_id = u.id
                      AND r2.status = 1
                      AND r2.access = 1
                ))
                """;

        if (source == PermissionSourceType.ROLE) {
            where.append(" AND ").append(roleCond);
            params.add(permissionId);
        } else if (source == PermissionSourceType.DIRECT) {
            where.append(" AND ").append(directCond);
            params.add(permissionId);
        } else {
            where.append(" AND (").append(roleCond).append(" OR ").append(directCond).append(")");
            params.add(permissionId);
            params.add(permissionId);
        }

        if (status != null) {
            where.append(" AND u.status = ?");
            params.add(status.code());
        }

        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            where.append(
                    " AND (LOWER(u.username) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(u.full_name) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(u.email, '')) LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        return new FilterSql(where.toString(), params);
    }

    private static String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY u.username ASC, u.id ASC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = switch (order.getProperty()) {
                case "username" -> "u.username";
                case "fullName" -> "u.full_name";
                case "email" -> "u.email";
                case "status" -> "u.status";
                case "createdAt" -> "u.created_at";
                default -> null;
            };
            if (column != null) {
                clauses.add(column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        if (clauses.isEmpty()) {
            return "ORDER BY u.username ASC, u.id ASC";
        }
        clauses.add("u.id ASC");
        return "ORDER BY " + String.join(", ", clauses);
    }

    private PermissionResponse mapPermission(ResultSet rs, Locale locale) throws SQLException {
        String code = rs.getString("code");
        String groupCode = rs.getString("group_code");
        String groupName = resolveGroupName(code, groupCode, locale);
        String dbName = rs.getString("name");
        String dbDescription = rs.getString("description");
        String localizedName = messages.getOrNull(locale, "permission." + code + ".name");
        String localizedDescription = messages.getOrNull(locale, "permission." + code + ".description");
        return new PermissionResponse(
                rs.getObject("id", UUID.class),
                code,
                rs.getInt("bit_index"),
                Access.fromCode(rs.getShort("scope")),
                localizedName != null ? localizedName : dbName,
                groupCode,
                groupName,
                localizedDescription != null ? localizedDescription : dbDescription,
                rs.getLong("role_count"),
                rs.getLong("user_count"));
    }

    private String resolveGroupName(String permissionCode, String groupCode, Locale locale) {
        String localizedGroup = messages.getOrNull(locale, "permission.object." + groupCode);
        if (localizedGroup != null) {
            return localizedGroup;
        }
        return Permission.findByCode(permissionCode)
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

    private static UserRow mapUserRow(ResultSet rs) throws SQLException {
        return new UserRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("email"),
                UserStatus.fromCode(rs.getShort("status")));
    }

    private static boolean containsIgnoreCase(String value, String needleLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record FilterSql(String whereClause, List<Object> params) {
    }

    private record UserRow(
            UUID id, String code, String username, String fullName, String email, UserStatus status) {
    }

    private record DirectRow(UUID userId, UUID grantedBy, Instant grantedAt, String reason) {
    }

    public record PermissionExportResult(String fileName, byte[] content) {
    }
}
