package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.application.PermissionCatalogSync;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.identity.spi.Permissions;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.SessionAuthzResolver;

class PermissionCatalogSyncTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PermissionCatalogSync permissionCatalogSync;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void afterStartup_permissionTableMatchesEnumAndBits_andRerunningIsIdempotent_andPreservesExtraRows() {
        List<PermissionEntity> initial = permissionRepository.findAll();
        assertThat(initial).hasSize(Permission.values().length);

        Map<String, PermissionEntity> byCode = initial.stream()
                .collect(Collectors.toMap(PermissionEntity::getCode, p -> p));

        for (Permission expected : Permission.values()) {
            PermissionEntity actual = byCode.get(expected.code());
            assertThat(actual).isNotNull();
            assertThat(actual.getBitIndex()).isEqualTo(expected.bitIndex());
            assertThat(actual.getScope()).isEqualTo(expected.scope());
            assertThat(actual.getGroupCode()).isEqualTo(expected.object().code());
            assertThat(actual.getName()).isEqualTo(expected.displayName());
        }

        long distinctBits = initial.stream().map(PermissionEntity::getBitIndex).distinct().count();
        assertThat(distinctBits).isEqualTo(Permission.values().length);

        // Re-running sync creates no duplicate rows
        permissionCatalogSync.sync();
        assertThat(permissionRepository.count()).isEqualTo(Permission.values().length);

        // An extra legacy row in DB is neither deleted nor assigned a new bit_index
        UUID legacyId = Ids.newId();
        jdbcTemplate.update(
                """
                INSERT INTO identity.permission (id, code, bit_index, scope, name, group_code, description)
                VALUES (?, 'LEGACY.REMOVED_IN_CODE', 999, 1, 'Legacy', 'LEGACY', 'Old permission')
                """,
                legacyId);
        try {
            permissionCatalogSync.sync();

            PermissionEntity legacy = permissionRepository.findByCode("LEGACY.REMOVED_IN_CODE").orElseThrow();
            assertThat(legacy.getBitIndex()).isEqualTo(999);
            assertThat(permissionRepository.count()).isEqualTo(Permission.values().length + 1L);
        } finally {
            jdbcTemplate.update("DELETE FROM identity.permission WHERE id = ?", legacyId);
        }
    }

    @Test
    void fourSystemRolesAreSeededWithSpecDefaultPermissions() {
        assertSystemRole("system_admin", Access.MANAGE, Permission.values().length);
        assertSystemRole("content_admin", Access.MANAGE, 7,
                Permissions.CATALOG_VIEW, Permissions.CATALOG_MODIFY,
                Permissions.QUESTION_VIEW, Permissions.QUESTION_MODIFY,
                Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH);
        assertSystemRole(
                "operations",
                Access.MANAGE,
                5,
                Permissions.USER_VIEW,
                Permissions.USER_MODIFY,
                Permissions.USER_LEARNING_VIEW,
                Permissions.CLASS_VIEW,
                Permissions.CLASS_MODIFY);
        assertSystemRole("student", Access.EXAM, 0);
    }

    @Test
    void permissionsEndpointsDoNotAllowWriteMethods() throws Exception {
        mockMvc.perform(post("/api/v1/admin/permissions")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(404),
                        org.hamcrest.Matchers.is(405))));

        mockMvc.perform(patch("/api/v1/admin/permissions/" + Ids.newId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(404),
                        org.hamcrest.Matchers.is(405))));

        mockMvc.perform(delete("/api/v1/admin/permissions/" + Ids.newId())
                        .with(asPlatformAdmin()))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(404),
                        org.hamcrest.Matchers.is(405))));
    }

    @Test
    void sync_whenExistingPermissionBitIndexDrifts_failsWithoutChangingStoredBit() {
        int originalBit = Permission.AUDIT_VIEW.bitIndex();
        int driftedBit = 998;
        jdbcTemplate.update(
                "UPDATE identity.permission SET bit_index = ? WHERE code = ?",
                driftedBit,
                Permission.AUDIT_VIEW.code());
        try {
            assertThatThrownBy(() -> permissionCatalogSync.sync())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Permission.AUDIT_VIEW.code())
                    .hasMessageContaining("bit_index");

            Integer storedBit = jdbcTemplate.queryForObject(
                    "SELECT bit_index FROM identity.permission WHERE code = ?",
                    Integer.class,
                    Permission.AUDIT_VIEW.code());
            assertThat(storedBit).isEqualTo(driftedBit);
        } finally {
            jdbcTemplate.update(
                    "UPDATE identity.permission SET bit_index = ? WHERE code = ?",
                    originalBit,
                    Permission.AUDIT_VIEW.code());
        }
    }

    private void assertSystemRole(String normalizedCode, Access expectedAccess, int expectedCount, String... expectedCodes) {
        Role role = roleRepository.findByCodeNormalized(normalizedCode).orElseThrow();
        assertThat(role.isSystem()).isTrue();
        assertThat(role.getAccess()).isEqualTo(expectedAccess);

        Set<UUID> permIds = rolePermissionRepository.findAllByIdRoleId(role.getId()).stream()
                .map(rp -> rp.getId().getPermissionId())
                .collect(Collectors.toSet());
        assertThat(permIds).hasSize(expectedCount);

        if (expectedCodes.length > 0) {
            Set<String> actualCodes = permissionRepository.findAllById(permIds).stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            assertThat(actualCodes).containsExactlyInAnyOrder(expectedCodes);
        }
    }

    @Test
    void wave1Db_keepsManuallyRemovedGrant_andGainsFiveNewPermissions_andEvictsAuthzCache() {
        Role admin = roleRepository.findByCodeNormalized("system_admin").orElseThrow();
        UUID adminRoleId = admin.getId();
        String removedCode = Permission.AUDIT_VIEW.code();
        String[] newCodes = {
            Permissions.QUESTION_VIEW, Permissions.QUESTION_MODIFY,
            Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH
        };

        // Người dùng gắn vai trò system_admin + snapshot quyền đã cache trong Redis
        UUID userId = Ids.newId();
        insertUser(userId);
        jdbcTemplate.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_at) VALUES (?, ?, now())",
                userId, adminRoleId);
        String authzKey = SessionAuthzResolver.AUTHZ_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(authzKey, "snapshot");

        UUID auditViewPermissionId = permissionRepository.findByCode(removedCode).orElseThrow().getId();

        try {
            // Trạng thái "wave-1": 13 hàng cũ, một grant cũ bị admin tháo tay
            jdbcTemplate.update(
                    "DELETE FROM identity.role_permission WHERE permission_id IN"
                            + " (SELECT id FROM identity.permission WHERE code IN (?,?,?,?,?))",
                    newCodes[0], newCodes[1], newCodes[2], newCodes[3], newCodes[4]);
            jdbcTemplate.update(
                    "DELETE FROM identity.permission WHERE code IN (?,?,?,?,?)",
                    newCodes[0], newCodes[1], newCodes[2], newCodes[3], newCodes[4]);
            jdbcTemplate.update(
                    "DELETE FROM identity.role_permission WHERE role_id = ? AND permission_id = ?",
                    adminRoleId, auditViewPermissionId);

            permissionCatalogSync.sync();

            // 5 quyền mới được tạo lại với đúng bit_index
            for (String code : newCodes) {
                Permission expected = Permission.fromCode(code);
                PermissionEntity actual = permissionRepository.findByCode(code).orElseThrow();
                assertThat(actual.getBitIndex()).isEqualTo(expected.bitIndex());
            }
            assertThat(permissionRepository.count()).isEqualTo(Permission.values().length);

            // Vai trò hệ thống nhận lại 5 quyền mới (17 link)
            assertThat(rolePermissionRepository.findAllByIdRoleId(adminRoleId)).hasSize(17);
            Set<String> adminCodes = permissionRepository
                    .findAllById(rolePermissionRepository.findAllByIdRoleId(adminRoleId).stream()
                            .map(rp -> rp.getId().getPermissionId())
                            .toList())
                    .stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            assertThat(adminCodes).contains(newCodes);

            // F-07: content_admin (2 quyền cũ) nhận thêm 5 quyền mới theo mặc định của vai trò
            Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
            Set<String> contentCodes = permissionRepository
                    .findAllById(rolePermissionRepository.findAllByIdRoleId(contentAdmin.getId()).stream()
                            .map(rp -> rp.getId().getPermissionId())
                            .toList())
                    .stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            assertThat(contentCodes).contains(newCodes)
                    .contains(Permissions.CATALOG_VIEW, Permissions.CATALOG_MODIFY)
                    .hasSize(7);

            // Grant bị tháo tay KHÔNG bị khôi phục
            assertThat(adminCodes).doesNotContain(removedCode);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM identity.role_permission WHERE role_id = ? AND permission_id = ?",
                    Long.class, adminRoleId, auditViewPermissionId)).isZero();

            // Phiên quyền đã cache bị xóa (authz bump)
            assertThat(redisTemplate.hasKey(authzKey)).isFalse();
        } finally {
            // Khôi phục grant bị xóa để DB nhất quán cho test sau
            if (permissionRepository.findByCode(removedCode).isPresent()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO identity.role_permission (role_id, permission_id, granted_at)
                        SELECT ?, ?, now()
                        WHERE NOT EXISTS (
                            SELECT 1 FROM identity.role_permission WHERE role_id = ? AND permission_id = ?
                        )
                        """,
                        adminRoleId, auditViewPermissionId, adminRoleId, auditViewPermissionId);
            }
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(authzKey);
        }
    }

    @Test
    void dot2Db_retainedPermissionRows_backfillsContentAdminOnce_andEvictsItsCache() {
        // Fixture đợt-2 (R1-1/R1-4): mọi dòng permission còn nguyên, content_admin chỉ còn 2
        // grant danh mục cũ, khóa một lần chưa có -> sync phải gán đủ 7 mặc định một lần duy nhất.
        String upgradeKey = "identity.permission_upgrade.content_admin_defaults";
        String[] addedCodes = {
            Permissions.QUESTION_VIEW, Permissions.QUESTION_MODIFY,
            Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH
        };
        Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        UUID roleId = contentAdmin.getId();

        UUID userId = Ids.newId();
        insertUser(userId);
        jdbcTemplate.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_at) VALUES (?, ?, now())",
                userId, roleId);
        String authzKey = SessionAuthzResolver.AUTHZ_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(authzKey, "snapshot");

        UUID userViewId = permissionRepository.findByCode(Permissions.USER_VIEW).orElseThrow().getId();

        try {
            jdbcTemplate.update("DELETE FROM shared.system_setting WHERE key = ?", upgradeKey);
            jdbcTemplate.update(
                    "DELETE FROM identity.role_permission WHERE role_id = ? AND permission_id IN"
                            + " (SELECT id FROM identity.permission WHERE code IN (?,?,?,?,?))",
                    roleId,
                    addedCodes[0], addedCodes[1], addedCodes[2], addedCodes[3], addedCodes[4]);
            assertThat(rolePermissionRepository.findAllByIdRoleId(roleId)).hasSize(2);

            permissionCatalogSync.sync();

            Set<String> codes = permissionRepository
                    .findAllById(rolePermissionRepository.findAllByIdRoleId(roleId).stream()
                            .map(rp -> rp.getId().getPermissionId())
                            .toList())
                    .stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            assertThat(codes).contains(addedCodes)
                    .contains(Permissions.CATALOG_VIEW, Permissions.CATALOG_MODIFY)
                    .hasSize(7);
            // Khóa ghi kết quả "applied" và snapshot authz của người dùng content_admin bị xóa.
            String marker = jdbcTemplate.queryForObject(
                    "SELECT value::text FROM shared.system_setting WHERE key = ?",
                    String.class, upgradeKey);
            assertThat(marker).contains("\"applied\":true");
            assertThat(redisTemplate.hasKey(authzKey)).isFalse();

            // Chạy lại: không thêm link trùng, không đụng lại khóa.
            permissionCatalogSync.sync();
            assertThat(rolePermissionRepository.findAllByIdRoleId(roleId)).hasSize(7);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM identity.role_permission WHERE role_id = ?",
                    Long.class, roleId)).isEqualTo(7L);

            // Vai trò đã bị tùy biến: gỡ cố ý một grant đã gán + thêm một grant ngoài mặc định
            // -> các lần sync sau không khôi phục, không gỡ (khóa một lần đã chốt).
            UUID examViewId = permissionRepository.findByCode(Permissions.EXAM_VIEW)
                    .orElseThrow().getId();
            jdbcTemplate.update(
                    "DELETE FROM identity.role_permission WHERE role_id = ? AND permission_id = ?",
                    roleId, examViewId);
            jdbcTemplate.update(
                    "INSERT INTO identity.role_permission (role_id, permission_id, granted_at)"
                            + " VALUES (?, ?, now())",
                    roleId, userViewId);
            permissionCatalogSync.sync();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM identity.role_permission"
                            + " WHERE role_id = ? AND permission_id = ?",
                    Long.class, roleId, examViewId)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM identity.role_permission"
                            + " WHERE role_id = ? AND permission_id = ?",
                    Long.class, roleId, userViewId)).isEqualTo(1L);
        } finally {
            // Trả vai trò về đúng 7 mặc định cho các test sau.
            jdbcTemplate.update(
                    "DELETE FROM identity.role_permission WHERE role_id = ? AND permission_id = ?",
                    roleId, userViewId);
            jdbcTemplate.update(
                    """
                    INSERT INTO identity.role_permission (role_id, permission_id, granted_at)
                    SELECT ?, p.id, now() FROM identity.permission p
                    WHERE p.code IN (?,?,?,?,?) AND NOT EXISTS (
                        SELECT 1 FROM identity.role_permission rp
                        WHERE rp.role_id = ? AND rp.permission_id = p.id)
                    """,
                    roleId,
                    addedCodes[0], addedCodes[1], addedCodes[2], addedCodes[3], addedCodes[4],
                    roleId);
            jdbcTemplate.update("DELETE FROM shared.system_setting WHERE key = ?", upgradeKey);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(authzKey);
        }
    }

    private void insertUser(UUID userId) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String userCode = String.format("U%06d", seq);
        java.time.Instant now = java.time.Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId, userCode, "sync-test-" + userId.toString().substring(0, 8),
                ("sync-test-" + userId.toString().substring(0, 8)).toLowerCase(),
                "Sync Test", userId + "@test.local",
                (userId + "@test.local").toLowerCase(),
                java.sql.Timestamp.from(now), userId, java.sql.Timestamp.from(now), userId);
    }
}
