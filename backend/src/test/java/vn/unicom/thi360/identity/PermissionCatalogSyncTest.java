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
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;

class PermissionCatalogSyncTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

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
        assertSystemRole("content_admin", Access.MANAGE, 2, Permissions.CATALOG_VIEW, Permissions.CATALOG_MODIFY);
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
}
