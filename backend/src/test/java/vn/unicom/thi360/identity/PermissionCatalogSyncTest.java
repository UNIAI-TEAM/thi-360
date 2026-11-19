package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

    @Autowired
    private ObjectMapper objectMapper;

    /** BS 7 / plan B3: chín quyền của đợt 3, theo thứ tự bit 18..26. */
    private static final List<String> WAVE3_CODES = List.of(
            Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY,
            Permissions.ORDER_VIEW, Permissions.ORDER_REFUND, Permissions.ORDER_RECOVER,
            Permissions.GRADING_VIEW, Permissions.GRADING_MODIFY,
            Permissions.ATTEMPT_VIEW, Permissions.ATTEMPT_COMPENSATE);

    /** Các grant của content_admin ngoài hai quyền danh mục: 5 của đợt 2 và 3 của đợt 3. */
    private static final String[] CONTENT_ADMIN_ADDED = {
        Permissions.QUESTION_VIEW, Permissions.QUESTION_MODIFY,
        Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH,
        Permissions.GRADING_VIEW, Permissions.GRADING_MODIFY, Permissions.ATTEMPT_VIEW
    };

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
        assertSystemRole("content_admin", Access.MANAGE, 10,
                Permissions.CATALOG_VIEW, Permissions.CATALOG_MODIFY,
                Permissions.QUESTION_VIEW, Permissions.QUESTION_MODIFY,
                Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH,
                Permissions.GRADING_VIEW, Permissions.GRADING_MODIFY, Permissions.ATTEMPT_VIEW);
        assertSystemRole(
                "operations",
                Access.MANAGE,
                11,
                Permissions.USER_VIEW,
                Permissions.USER_MODIFY,
                Permissions.USER_LEARNING_VIEW,
                Permissions.CLASS_VIEW,
                Permissions.CLASS_MODIFY,
                Permissions.PROMOTION_VIEW,
                Permissions.PROMOTION_MODIFY,
                Permissions.ORDER_VIEW,
                Permissions.ORDER_RECOVER,
                Permissions.ATTEMPT_VIEW,
                Permissions.ATTEMPT_COMPENSATE);
        assertSystemRole("student", Access.EXAM, 0);
    }

    @Test
    void freshDb_markerRecordsSkippedAndLaterRemovalIsNeverRestored() throws Exception {
        // R2-1: khóa một lần phải tồn tại trên DB mới với outcome "skipped" (10 mặc định đã có
        // sẵn do seedSystemRoleDefaults chạy trước), và sync sau không được khôi phục một grant
        // bị gỡ cố ý.
        String upgradeKey = "identity.permission_upgrade.content_admin_defaults";
        String[] addedCodes = CONTENT_ADMIN_ADDED;
        Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        UUID roleId = contentAdmin.getId();
        assertThat(rolePermissionRepository.findAllByIdRoleId(roleId)).hasSize(10);

        // Nhánh thử khác xóa khóa một lần; một sync bảo đảm nó tồn tại lại trước khi đọc.
        // Trên DB mới outcome chuẩn vẫn là "skipped" vì 7 mặc định đã đủ.
        permissionCatalogSync.sync();
        String markerJson = jdbcTemplate.queryForObject(
                "SELECT value::text FROM shared.system_setting WHERE key = ?",
                String.class, upgradeKey);
        JsonNode marker = objectMapper.readTree(markerJson);
        assertThat(marker.get("outcome").asText()).isEqualTo("skipped");
        assertThat(marker.get("applied").asBoolean()).isFalse();
        assertThat(marker.get("reason").asText()).isEqualTo("defaults already granted");

        try {
            // Gỡ cố ý 8 grant question/exam/grading/attempt khỏi content_admin rồi chạy lại sync.
            removeGrants(roleId, addedCodes);
            assertThat(rolePermissionRepository.findAllByIdRoleId(roleId)).hasSize(2);

            permissionCatalogSync.sync();

            // Khóa một lần đã chốt: sync không được khôi phục các grant bị gỡ.
            assertThat(rolePermissionRepository.findAllByIdRoleId(roleId)).hasSize(2);
            Set<String> codes = permissionRepository
                    .findAllById(rolePermissionRepository.findAllByIdRoleId(roleId).stream()
                            .map(rp -> rp.getId().getPermissionId())
                            .toList())
                    .stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            assertThat(codes).containsExactlyInAnyOrder(
                    Permissions.CATALOG_VIEW, Permissions.CATALOG_MODIFY);
        } finally {
            // Trả vai trò về đúng 10 mặc định cho các test sau.
            restoreGrants(roleId, addedCodes);
        }
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

            // Vai trò hệ thống nhận lại 5 quyền mới: mọi quyền trừ AUDIT.VIEW bị tháo tay
            assertThat(rolePermissionRepository.findAllByIdRoleId(adminRoleId))
                    .hasSize(Permission.values().length - 1);
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
                    .hasSize(10);

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
    void dot2Db_retainedPermissionRows_backfillsContentAdminOnce_andEvictsItsCache() throws Exception {
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
            removeGrants(roleId, CONTENT_ADMIN_ADDED);
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
            // jsonb trả về text đã chuẩn hóa (": ") nên phải parse JSON, không so text thô.
            String markerJson = jdbcTemplate.queryForObject(
                    "SELECT value::text FROM shared.system_setting WHERE key = ?",
                    String.class, upgradeKey);
            JsonNode marker = objectMapper.readTree(markerJson);
            assertThat(marker.get("outcome").asText()).isEqualTo("applied");
            assertThat(marker.get("applied").asBoolean()).isTrue();
            assertThat(marker.get("granted").asInt()).isEqualTo(5);
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
            // Trả vai trò về đúng 10 mặc định cho các test sau.
            jdbcTemplate.update(
                    "DELETE FROM identity.role_permission WHERE role_id = ? AND permission_id = ?",
                    roleId, userViewId);
            restoreGrants(roleId, CONTENT_ADMIN_ADDED);
            jdbcTemplate.update("DELETE FROM shared.system_setting WHERE key = ?", upgradeKey);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(authzKey);
        }
    }

    @Test
    void freshDb_systemAdminHoldsNineWave3Permissions() {
        for (int i = 0; i < WAVE3_CODES.size(); i++) {
            String code = WAVE3_CODES.get(i);
            PermissionEntity row = permissionRepository.findByCode(code).orElseThrow();
            assertThat(row.getBitIndex()).as("bit of %s", code).isEqualTo(18 + i);
            assertThat(row.getScope()).as("scope of %s", code).isEqualTo(Access.MANAGE);
        }

        assertThat(codesOfRole("system_admin")).containsAll(WAVE3_CODES);
        assertThat(rolePermissionRepository.findAllByIdRoleId(
                roleRepository.findByCodeNormalized("system_admin").orElseThrow().getId()))
                .hasSize(Permission.values().length);
    }

    @Test
    void systemRolesGetAgreedWave3Permissions() {
        assertThat(wave3CodesOfRole("operations")).containsExactlyInAnyOrder(
                Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY,
                Permissions.ORDER_VIEW, Permissions.ORDER_RECOVER,
                Permissions.ATTEMPT_VIEW, Permissions.ATTEMPT_COMPENSATE);
        assertThat(wave3CodesOfRole("content_admin")).containsExactlyInAnyOrder(
                Permissions.GRADING_VIEW, Permissions.GRADING_MODIFY, Permissions.ATTEMPT_VIEW);
        assertThat(wave3CodesOfRole("student")).isEmpty();
        assertThat(wave3CodesOfRole("system_admin")).containsExactlyInAnyOrderElementsOf(WAVE3_CODES);

        // BS 7: ORDER.REFUND chỉ system_admin có.
        List<String> rolesWithRefund = new ArrayList<>();
        for (Role role : roleRepository.findAll()) {
            if (role.isSystem() && codesOfRole(role.getCodeNormalized()).contains(Permissions.ORDER_REFUND)) {
                rolesWithRefund.add(role.getCodeNormalized());
            }
        }
        assertThat(rolesWithRefund).containsExactly("system_admin");
    }

    @Test
    void wave2Db_keepsManuallyRemovedGrant_andGainsNineNewPermissions_andEvictsAuthzCache() {
        Role admin = roleRepository.findByCodeNormalized("system_admin").orElseThrow();
        UUID adminRoleId = admin.getId();
        String removedCode = Permission.AUDIT_VIEW.code();

        UUID userId = Ids.newId();
        insertUser(userId);
        jdbcTemplate.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_at) VALUES (?, ?, now())",
                userId, adminRoleId);
        String authzKey = SessionAuthzResolver.AUTHZ_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(authzKey, "snapshot");
        UUID auditViewPermissionId = permissionRepository.findByCode(removedCode).orElseThrow().getId();

        try {
            // Trạng thái đợt 2: 18 dòng quyền, một grant cũ bị admin tháo tay
            deleteWave3PermissionRows();
            jdbcTemplate.update(
                    "DELETE FROM identity.role_permission WHERE role_id = ? AND permission_id = ?",
                    adminRoleId, auditViewPermissionId);

            permissionCatalogSync.sync();

            for (int i = 0; i < WAVE3_CODES.size(); i++) {
                PermissionEntity actual = permissionRepository.findByCode(WAVE3_CODES.get(i)).orElseThrow();
                assertThat(actual.getBitIndex()).isEqualTo(18 + i);
            }
            assertThat(permissionRepository.count()).isEqualTo(Permission.values().length);

            Set<String> adminCodes = codesOfRole("system_admin");
            assertThat(adminCodes).containsAll(WAVE3_CODES);
            assertThat(adminCodes).hasSize(Permission.values().length - 1);

            // Grant bị tháo tay KHÔNG bị khôi phục
            assertThat(adminCodes).doesNotContain(removedCode);

            // Phiên quyền đã cache bị xóa
            assertThat(redisTemplate.hasKey(authzKey)).isFalse();
        } finally {
            jdbcTemplate.update(
                    """
                    INSERT INTO identity.role_permission (role_id, permission_id, granted_at)
                    SELECT ?, ?, now()
                    WHERE NOT EXISTS (
                        SELECT 1 FROM identity.role_permission WHERE role_id = ? AND permission_id = ?
                    )
                    """,
                    adminRoleId, auditViewPermissionId, adminRoleId, auditViewPermissionId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(authzKey);
        }
    }

    @Test
    void secondSyncChangesNothing() {
        permissionCatalogSync.sync();
        List<Map<String, Object>> permissionsBefore =
                jdbcTemplate.queryForList("SELECT * FROM identity.permission ORDER BY code");
        List<Map<String, Object>> linksBefore = jdbcTemplate.queryForList(
                "SELECT role_id, permission_id, granted_at FROM identity.role_permission"
                        + " ORDER BY role_id, permission_id");
        List<Map<String, Object>> markersBefore = jdbcTemplate.queryForList(
                "SELECT key, value::text AS value FROM shared.system_setting"
                        + " WHERE key LIKE 'identity.permission_upgrade.%' ORDER BY key");

        permissionCatalogSync.sync();

        assertThat(jdbcTemplate.queryForList("SELECT * FROM identity.permission ORDER BY code"))
                .isEqualTo(permissionsBefore);
        assertThat(jdbcTemplate.queryForList(
                "SELECT role_id, permission_id, granted_at FROM identity.role_permission"
                        + " ORDER BY role_id, permission_id"))
                .isEqualTo(linksBefore);
        assertThat(jdbcTemplate.queryForList(
                "SELECT key, value::text AS value FROM shared.system_setting"
                        + " WHERE key LIKE 'identity.permission_upgrade.%' ORDER BY key"))
                .isEqualTo(markersBefore);
    }

    @Test
    void wave2Db_grantsAgreedRolesAndKeepsManualRemovals() {
        UUID operationsId = roleRepository.findByCodeNormalized("operations").orElseThrow().getId();
        UUID contentAdminId = roleRepository.findByCodeNormalized("content_admin").orElseThrow().getId();

        try {
            // DB đợt 2: chưa có chín dòng quyền mới; quản trị viên đã gỡ tay một grant cũ ở mỗi vai trò.
            deleteWave3PermissionRows();
            removeGrants(operationsId, Permissions.CLASS_MODIFY);
            removeGrants(contentAdminId, Permissions.EXAM_PUBLISH);

            permissionCatalogSync.sync();

            assertThat(wave3CodesOfRole("operations")).containsExactlyInAnyOrder(
                    Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY,
                    Permissions.ORDER_VIEW, Permissions.ORDER_RECOVER,
                    Permissions.ATTEMPT_VIEW, Permissions.ATTEMPT_COMPENSATE);
            assertThat(wave3CodesOfRole("content_admin")).containsExactlyInAnyOrder(
                    Permissions.GRADING_VIEW, Permissions.GRADING_MODIFY, Permissions.ATTEMPT_VIEW);
            assertThat(codesOfRole("operations")).doesNotContain(Permissions.CLASS_MODIFY);
            assertThat(codesOfRole("content_admin")).doesNotContain(Permissions.EXAM_PUBLISH);

            // Lần sync sau không thêm lại grant bị gỡ tay.
            permissionCatalogSync.sync();
            assertThat(codesOfRole("operations")).doesNotContain(Permissions.CLASS_MODIFY);
            assertThat(codesOfRole("content_admin")).doesNotContain(Permissions.EXAM_PUBLISH);
        } finally {
            restoreGrants(operationsId, Permissions.CLASS_MODIFY);
            restoreGrants(contentAdminId, Permissions.EXAM_PUBLISH);
        }
    }

    private Set<String> codesOfRole(String normalizedRoleCode) {
        Role role = roleRepository.findByCodeNormalized(normalizedRoleCode).orElseThrow();
        Set<UUID> permissionIds = rolePermissionRepository.findAllByIdRoleId(role.getId()).stream()
                .map(rp -> rp.getId().getPermissionId())
                .collect(Collectors.toSet());
        return permissionRepository.findAllById(permissionIds).stream()
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());
    }

    private Set<String> wave3CodesOfRole(String normalizedRoleCode) {
        Set<String> codes = new HashSet<>(codesOfRole(normalizedRoleCode));
        codes.retainAll(WAVE3_CODES);
        return codes;
    }

    private void deleteWave3PermissionRows() {
        String in = String.join(",", Collections.nCopies(WAVE3_CODES.size(), "?"));
        jdbcTemplate.update(
                "DELETE FROM identity.role_permission WHERE permission_id IN"
                        + " (SELECT id FROM identity.permission WHERE code IN (" + in + "))",
                WAVE3_CODES.toArray());
        jdbcTemplate.update(
                "DELETE FROM identity.permission WHERE code IN (" + in + ")", WAVE3_CODES.toArray());
    }

    private void removeGrants(UUID roleId, String... codes) {
        String in = String.join(",", Collections.nCopies(codes.length, "?"));
        List<Object> args = new ArrayList<>();
        args.add(roleId);
        args.addAll(List.of(codes));
        jdbcTemplate.update(
                "DELETE FROM identity.role_permission WHERE role_id = ? AND permission_id IN"
                        + " (SELECT id FROM identity.permission WHERE code IN (" + in + "))",
                args.toArray());
    }

    private void restoreGrants(UUID roleId, String... codes) {
        String in = String.join(",", Collections.nCopies(codes.length, "?"));
        List<Object> args = new ArrayList<>();
        args.add(roleId);
        args.addAll(List.of(codes));
        args.add(roleId);
        jdbcTemplate.update(
                "INSERT INTO identity.role_permission (role_id, permission_id, granted_at)"
                        + " SELECT ?, p.id, now() FROM identity.permission p WHERE p.code IN (" + in + ")"
                        + " AND NOT EXISTS (SELECT 1 FROM identity.role_permission rp"
                        + " WHERE rp.role_id = ? AND rp.permission_id = p.id)",
                args.toArray());
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
