package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.unicom.thi360.shared.security.TestSecurity.asPlatformAdmin;
import static vn.unicom.thi360.shared.security.TestSecurity.asRole;
import static vn.unicom.thi360.shared.security.TestSecurity.asUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.application.AuthzSnapshotService;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.PlatformAdmin;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RolePermission;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserPermission;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserPermissionRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;

class AuthzApiIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserPermissionRepository userPermissionRepository;

    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private AuthzSnapshotService authzSnapshotService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final java.util.List<UUID> createdUserIds = new java.util.ArrayList<>();
    private final java.util.List<UUID> createdRoleIds = new java.util.ArrayList<>();
    private final java.util.List<UUID> createdPlatformIds = new java.util.ArrayList<>();

    @BeforeEach
    void resetAuditInvocations() {
        clearInvocations(auditRecorder);
    }

    @AfterEach
    void cleanup() {
        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM identity.user_permission WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(AuthzSnapshotService.KEY_PREFIX + userId);
        }
        createdUserIds.clear();
        for (UUID roleId : createdRoleIds) {
            jdbcTemplate.update("DELETE FROM identity.role_permission WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM identity.role WHERE id = ?", roleId);
        }
        createdRoleIds.clear();
        for (UUID pid : createdPlatformIds) {
            platformAdminRepository.deleteById(pid);
        }
        createdPlatformIds.clear();
    }

    @Test
    void listPermissions_and_usersByPermission_andExport() throws Exception {
        PermissionEntity userViewPerm = permissionRepository.findByCode(Permissions.USER_VIEW).orElseThrow();

        UserAccount activeUser = createUser("perm.user.active", UserStatus.ACTIVE, null);
        UserAccount inactiveUser = createUser("perm.user.inactive", UserStatus.INACTIVE, null);
        UserAccount deletedUser = createUser("perm.user.deleted", UserStatus.INACTIVE, Instant.now());

        PlatformAdmin platformAdmin = new PlatformAdmin(
                Ids.newId(), "platform.secret.it", Instant.now());
        platformAdminRepository.save(platformAdmin);
        createdPlatformIds.add(platformAdmin.getId());

        Role roleOne = createCustomRole("PERM_R1_" + suffix(), "Vai trò 1", Access.MANAGE, RoleStatus.ACTIVE);
        Role roleTwo = createCustomRole("PERM_R2_" + suffix(), "Vai trò 2", Access.MANAGE, RoleStatus.ACTIVE);
        Role inactiveRole = createCustomRole("PERM_R_INACT_" + suffix(), "Vai trò ngưng", Access.MANAGE, RoleStatus.INACTIVE);

        rolePermissionRepository.save(new RolePermission(roleOne.getId(), userViewPerm.getId(), platformAdmin.getId(), Instant.now()));
        rolePermissionRepository.save(new RolePermission(roleTwo.getId(), userViewPerm.getId(), platformAdmin.getId(), Instant.now()));
        rolePermissionRepository.save(new RolePermission(inactiveRole.getId(), userViewPerm.getId(), platformAdmin.getId(), Instant.now()));

        // activeUser has roleOne + roleTwo + direct grant -> 3 sources!
        userRoleRepository.save(new UserRole(activeUser.getId(), roleOne.getId(), platformAdmin.getId(), Instant.now()));
        userRoleRepository.save(new UserRole(activeUser.getId(), roleTwo.getId(), platformAdmin.getId(), Instant.now()));
        userPermissionRepository.save(new UserPermission(
                activeUser.getId(), userViewPerm.getId(), platformAdmin.getId(), Instant.now(), "Hỗ trợ kiểm tra"));

        // inactiveUser only has inactiveRole -> MUST NOT appear
        userRoleRepository.save(new UserRole(inactiveUser.getId(), inactiveRole.getId(), platformAdmin.getId(), Instant.now()));
        // deletedUser has roleOne -> MUST NOT appear
        userRoleRepository.save(new UserRole(deletedUser.getId(), roleOne.getId(), platformAdmin.getId(), Instant.now()));

        // Missing one of the two required permissions -> 403
        mockMvc.perform(get("/api/v1/admin/permissions/{id}/users", userViewPerm.getId())
                        .with(asUser(Permissions.PERMISSION_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/permissions/{id}/users", userViewPerm.getId())
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // With both PERMISSION.VIEW and USER.VIEW -> returns activeUser with all 3 sources, no inactiveUser/deletedUser/platformAdmin
        mockMvc.perform(get("/api/v1/admin/permissions/{id}/users", userViewPerm.getId())
                        .param("q", "perm.user.")
                        .with(asUser(Permissions.PERMISSION_VIEW, Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].username").value("perm.user.active"))
                .andExpect(jsonPath("$.items[0].sources.length()").value(3))
                .andExpect(jsonPath("$.items[0].sources[*].type", containsInAnyOrder("ROLE", "ROLE", "DIRECT")))
                .andExpect(jsonPath("$.items[0].sources[?(@.type=='DIRECT')].grantedByUsername").value(hasItem("platform.secret.it")))
                .andExpect(jsonPath("$.items[*].username", not(hasItem("perm.user.inactive"))))
                .andExpect(jsonPath("$.items[*].username", not(hasItem("perm.user.deleted"))))
                .andExpect(jsonPath("$.items[*].username", not(hasItem("platform.secret.it"))));

        // GET /permissions returns userCount >= 1 and roleCount >= 2 for USER.VIEW
        mockMvc.perform(get("/api/v1/admin/permissions")
                        .param("group", "USER")
                        .param("q", "USER.VIEW")
                        .with(asUser(Permissions.PERMISSION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USER.VIEW"))
                .andExpect(jsonPath("$[0].groupName").value("Tài khoản"))
                .andExpect(jsonPath("$[0].roleCount").isNumber())
                .andExpect(jsonPath("$[0].userCount").isNumber());

        // Export .xlsx
        MvcResult exportRes = mockMvc.perform(get("/api/v1/admin/permissions/{id}/users/export", userViewPerm.getId())
                        .param("q", "perm.user.")
                        .param("source", "DIRECT")
                        .with(asUser(Permissions.PERMISSION_VIEW, Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(exportRes.getResponse().getContentAsByteArray()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("perm.user.active");
        }
    }

    @Test
    void roleCrud_normalization_scopeMismatch_accessLocked_inUse_system_andPrivilegeEscalation() throws Exception {
        // 1. Create role without access -> 400
        mockMvc.perform(post("/api/v1/admin/roles")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BAD_ROLE\",\"name\":\"Missing access\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 2. Privilege escalation on role create (actor only has ROLE.MODIFY, tries to grant USER.VIEW) -> 403 PRIVILEGE_ESCALATION
        mockMvc.perform(post("/api/v1/admin/roles")
                        .with(asUser(Permissions.ROLE_VIEW, Permissions.ROLE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "ESC_ROLE_%s",
                                  "name": "Esc Role",
                                  "access": "MANAGE",
                                  "permissionCodes": ["USER.VIEW"]
                                }
                                """.formatted(suffix())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVILEGE_ESCALATION"));

        // 3. Create custom role with USER.MODIFY -> normalizes to [USER.VIEW, USER.MODIFY]
        String roleCode = "CUSTOM_ROLE_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/roles")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Vai trò tùy chỉnh",
                                  "access": "MANAGE",
                                  "permissionCodes": ["USER.MODIFY"]
                                }
                                """.formatted(roleCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(roleCode))
                .andExpect(jsonPath("$.permissionCodes", containsInAnyOrder("USER.VIEW", "USER.MODIFY")))
                .andReturn();
        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID customRoleId = UUID.fromString(createdJson.get("id").asText());
        createdRoleIds.add(customRoleId);
        long version0 = createdJson.get("version").asLong();

        // 4. Duplicate code (case-insensitive) -> 409 ROLE_CODE_TAKEN
        mockMvc.perform(post("/api/v1/admin/roles")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Trùng mã",
                                  "access": "MANAGE"
                                }
                                """.formatted(roleCode.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_CODE_TAKEN"));

        // 5. Wrong version on PATCH -> 412 VERSION_CONFLICT
        mockMvc.perform(patch("/api/v1/admin/roles/{id}", customRoleId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Đổi tên sai version",
                                  "version": 999
                                }
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 6. Change access on role with 0 holders -> succeeds & does not touch any user snapshot
        UserAccount untouchedUser = createUser("snap.untouched." + suffix(), UserStatus.ACTIVE, null);
        Role operationsForSnap = roleRepository.findByCodeNormalized("operations").orElseThrow();
        userRoleRepository.save(new UserRole(untouchedUser.getId(), operationsForSnap.getId(), null, Instant.now()));
        var preSnapshot = authzSnapshotService.load(untouchedUser.getId());
        String untouchedKey = authzSnapshotService.cacheKey(untouchedUser.getId());
        String preSnapshotRaw = redisTemplate.opsForValue().get(untouchedKey);
        assertThat(preSnapshotRaw).isNotBlank();

        MvcResult patchAccessRes = mockMvc.perform(patch("/api/v1/admin/roles/{id}", customRoleId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "access": "EXAM",
                                  "version": %d
                                }
                                """.formatted(version0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("EXAM"))
                .andExpect(jsonPath("$.permissionCount").value(0))
                .andReturn();
        long version1 = objectMapper.readTree(patchAccessRes.getResponse().getContentAsString())
                .get("version").asLong();
        assertThat(redisTemplate.hasKey(untouchedKey)).isTrue();
        assertThat(redisTemplate.opsForValue().get(untouchedKey)).isEqualTo(preSnapshotRaw);
        assertThat(authzSnapshotService.load(untouchedUser.getId()).version()).isEqualTo(preSnapshot.version());

        // 7. PUT /roles/{id}/permissions on EXAM role with MANAGE permission -> 409 PERMISSION_SCOPE_MISMATCH
        mockMvc.perform(put("/api/v1/admin/roles/{id}/permissions", customRoleId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"USER.VIEW\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERMISSION_SCOPE_MISMATCH"));

        // Switch back to MANAGE and set [USER.VIEW, USER.ASSIGN]
        MvcResult patchBackRes = mockMvc.perform(patch("/api/v1/admin/roles/{id}", customRoleId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access\": \"MANAGE\", \"version\": %d}".formatted(version1)))
                .andExpect(status().isOk())
                .andReturn();
        long version2 = objectMapper.readTree(patchBackRes.getResponse().getContentAsString())
                .get("version").asLong();

        mockMvc.perform(put("/api/v1/admin/roles/{id}/permissions", customRoleId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"USER.ASSIGN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCodes", containsInAnyOrder("USER.VIEW", "USER.ASSIGN")));

        // Dropping USER.VIEW while keeping USER.ASSIGN -> 400 PERMISSION_REQUIRES_VIEW
        mockMvc.perform(put("/api/v1/admin/roles/{id}/permissions", customRoleId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"USER.ASSIGN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRES_VIEW"));

        // 8. System role protection: change access -> 409 ROLE_ACCESS_LOCKED, delete -> 409 ROLE_IS_SYSTEM
        Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        mockMvc.perform(patch("/api/v1/admin/roles/{id}", contentAdmin.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access\": \"EXAM\", \"version\": %d}".formatted(contentAdmin.getVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ACCESS_LOCKED"));

        mockMvc.perform(delete("/api/v1/admin/roles/{id}", contentAdmin.getId())
                        .with(asPlatformAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_IS_SYSTEM"));

        // 9. Assign custom role to user -> changing access -> 409 ROLE_ACCESS_LOCKED, delete -> 409 ROLE_IN_USE
        UserAccount holder = createUser("role.holder." + suffix(), UserStatus.ACTIVE, null);
        userRoleRepository.save(new UserRole(holder.getId(), customRoleId, null, Instant.now()));

        Role refreshed = roleRepository.findById(customRoleId).orElseThrow();
        mockMvc.perform(patch("/api/v1/admin/roles/{id}", customRoleId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access\": \"EXAM\", \"version\": %d}".formatted(refreshed.getVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ACCESS_LOCKED"));

        mockMvc.perform(delete("/api/v1/admin/roles/{id}", customRoleId)
                        .with(asPlatformAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));

        // Unassign user, then DELETE succeeds with 204
        jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", holder.getId());
        mockMvc.perform(delete("/api/v1/admin/roles/{id}", customRoleId)
                        .with(asPlatformAdmin()))
                .andExpect(status().isNoContent());
        createdRoleIds.remove(customRoleId);

        verify(auditRecorder, atLeastOnce()).record(argThat(e -> "ROLE_CREATED".equals(e.action())));
        verify(auditRecorder, atLeastOnce()).record(argThat(e -> "ROLE_DELETED".equals(e.action())));
    }

    @Test
    void userRoles_directPermissions_selfLockout_requiresManage_andEffectiveSources() throws Exception {
        UserAccount targetUser = createUser("authz.target." + suffix(), UserStatus.ACTIVE, null);
        Role studentRole = roleRepository.findByCodeNormalized("student").orElseThrow();
        Role contentAdminRole = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        Role operationsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();

        // 1. Put only STUDENT (EXAM) role on targetUser
        mockMvc.perform(put("/api/v1/admin/users/{id}/roles", targetUser.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [\"%s\"]}".formatted(studentRole.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("EXAM"));

        // 2. Direct permission on EXAM-only user -> 409 DIRECT_PERMISSION_REQUIRES_MANAGE
        mockMvc.perform(put("/api/v1/admin/users/{id}/permissions", targetUser.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"AUDIT.VIEW\"], \"reason\": \"test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DIRECT_PERMISSION_REQUIRES_MANAGE"));

        // 3. Assign CONTENT_ADMIN (MANAGE) + OPERATIONS (MANAGE)
        mockMvc.perform(put("/api/v1/admin/users/{id}/roles", targetUser.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [\"%s\", \"%s\"]}"
                                .formatted(contentAdminRole.getId(), operationsRole.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("MANAGE"));

        // 4. Direct permission without reason -> 400 VALIDATION_FAILED
        mockMvc.perform(put("/api/v1/admin/users/{id}/permissions", targetUser.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"USER.ASSIGN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REASON_REQUIRED"));

        // 5. Privilege escalation: actor lacks AUDIT.VIEW -> 403 PRIVILEGE_ESCALATION
        mockMvc.perform(put("/api/v1/admin/users/{id}/permissions", targetUser.getId())
                        .with(asUser(Permissions.USER_VIEW, Permissions.USER_ASSIGN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"AUDIT.VIEW\"], \"reason\": \"try escalate\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVILEGE_ESCALATION"));

        // 6. Grant direct USER.VIEW + USER.ASSIGN -> succeeds and appears in effective-permissions
        mockMvc.perform(put("/api/v1/admin/users/{id}/permissions", targetUser.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"USER.VIEW\", \"USER.ASSIGN\"], \"reason\": \"Ủy quyền tạm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasManageRole").value(true))
                .andExpect(jsonPath("$.permissions.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/users/{id}/effective-permissions", targetUser.getId())
                        .with(asPlatformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveAccess").value("MANAGE"))
                .andExpect(jsonPath("$.permissions[?(@.code=='USER.VIEW')].sources[*].type")
                        .value(containsInAnyOrder("ROLE", "DIRECT")));

        // 7. SELF_LOCKOUT: targetUser (with USER.ASSIGN) tries to remove their own direct USER.ASSIGN
        mockMvc.perform(put("/api/v1/admin/users/{id}/permissions", targetUser.getId())
                        .with(asUser(targetUser.getId(), targetUser.getUsername(),
                                Permissions.USER_VIEW, Permissions.USER_ASSIGN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": []}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));

        // 8. Removing last MANAGE role retains direct permissions in inactiveDirectPermissions (not effective!)
        mockMvc.perform(put("/api/v1/admin/users/{id}/roles", targetUser.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [\"%s\"]}".formatted(studentRole.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("EXAM"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/effective-permissions", targetUser.getId())
                        .with(asPlatformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasManageRole").value(false))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.inactiveDirectPermissions.length()").value(2));

        verify(auditRecorder, atLeastOnce()).record(argThat(e -> "USER_ROLES_CHANGED".equals(e.action())));
        verify(auditRecorder, atLeastOnce()).record(argThat(e -> "USER_PERMISSIONS_CHANGED".equals(e.action())));
    }

    @Test
    void roleEdit_selfLockoutOnDeactivateAndPermissionReplace() throws Exception {
        UserAccount actor = createUser("role.selflock." + suffix(), UserStatus.ACTIVE, null);
        Role adminRole = createCustomRole("SELF_ADMIN_" + suffix(), "Self Admin", Access.MANAGE, RoleStatus.ACTIVE);
        PermissionEntity roleView = permissionRepository.findByCode(Permissions.ROLE_VIEW).orElseThrow();
        PermissionEntity roleModify = permissionRepository.findByCode(Permissions.ROLE_MODIFY).orElseThrow();
        PermissionEntity userView = permissionRepository.findByCode(Permissions.USER_VIEW).orElseThrow();
        PermissionEntity userAssign = permissionRepository.findByCode(Permissions.USER_ASSIGN).orElseThrow();

        rolePermissionRepository.saveAll(List.of(
                new RolePermission(adminRole.getId(), roleView.getId(), null, Instant.now()),
                new RolePermission(adminRole.getId(), roleModify.getId(), null, Instant.now()),
                new RolePermission(adminRole.getId(), userView.getId(), null, Instant.now()),
                new RolePermission(adminRole.getId(), userAssign.getId(), null, Instant.now())));
        userRoleRepository.save(new UserRole(actor.getId(), adminRole.getId(), null, Instant.now()));

        var initialSnap = authzSnapshotService.load(actor.getId());
        assertThat(initialSnap.permissions().hasCode(Permissions.USER_ASSIGN)).isTrue();

        var asSelfActor = asUser(
                actor.getId(),
                actor.getUsername(),
                Permissions.ROLE_VIEW,
                Permissions.ROLE_MODIFY,
                Permissions.USER_VIEW,
                Permissions.USER_ASSIGN);

        // 1. Deactivating the actor's own role providing USER.ASSIGN -> 409 SELF_LOCKOUT
        mockMvc.perform(patch("/api/v1/admin/roles/{id}", adminRole.getId())
                        .with(asSelfActor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\", \"version\": %d}".formatted(adminRole.getVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));

        Role afterDeactivateAttempt = roleRepository.findById(adminRole.getId()).orElseThrow();
        assertThat(afterDeactivateAttempt.getStatus()).isEqualTo(RoleStatus.ACTIVE);
        assertThat(redisTemplate.hasKey(authzSnapshotService.cacheKey(actor.getId()))).isTrue();

        // 2. Replacing permissions without USER.ASSIGN -> 409 SELF_LOCKOUT
        mockMvc.perform(put("/api/v1/admin/roles/{id}/permissions", adminRole.getId())
                        .with(asSelfActor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"ROLE.VIEW\", \"ROLE.MODIFY\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));

        mockMvc.perform(get("/api/v1/admin/roles/{id}", adminRole.getId())
                        .with(asPlatformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCodes", containsInAnyOrder(
                        "ROLE.VIEW", "ROLE.MODIFY", "USER.VIEW", "USER.ASSIGN")));
        assertThat(redisTemplate.hasKey(authzSnapshotService.cacheKey(actor.getId()))).isTrue();

        // 3. When actor holds USER.ASSIGN via another active role, removing USER.ASSIGN from adminRole succeeds
        Role backupRole = createCustomRole("SELF_BACKUP_" + suffix(), "Self Backup", Access.MANAGE, RoleStatus.ACTIVE);
        rolePermissionRepository.saveAll(List.of(
                new RolePermission(backupRole.getId(), userView.getId(), null, Instant.now()),
                new RolePermission(backupRole.getId(), userAssign.getId(), null, Instant.now())));
        userRoleRepository.save(new UserRole(actor.getId(), backupRole.getId(), null, Instant.now()));

        mockMvc.perform(put("/api/v1/admin/roles/{id}/permissions", adminRole.getId())
                        .with(asSelfActor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"ROLE.VIEW\", \"ROLE.MODIFY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCodes", containsInAnyOrder("ROLE.VIEW", "ROLE.MODIFY")));
    }

    @Test
    void deleteRole_whenHeldOnlyBySoftDeletedUser_returnsConflictRoleInUseAndKeepsRoleIntact()
            throws Exception {
        Role customRole = createCustomRole(
                "DEL_SOFT_" + suffix(), "Soft Deleted Holder Role", Access.MANAGE, RoleStatus.ACTIVE);
        UserAccount softDeleted = createUser("soft.holder." + suffix(), UserStatus.INACTIVE, Instant.now());
        userRoleRepository.save(new UserRole(softDeleted.getId(), customRole.getId(), null, Instant.now()));

        mockMvc.perform(delete("/api/v1/admin/roles/{id}", customRole.getId())
                        .with(asPlatformAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));

        assertThat(roleRepository.findById(customRole.getId())).isPresent();
        Integer assignments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity.user_role WHERE role_id = ? AND user_id = ?",
                Integer.class,
                customRole.getId(),
                softDeleted.getId());
        assertThat(assignments).isEqualTo(1);
    }

    private UserAccount createUser(String username, UserStatus status, Instant deletedAt) {
        UUID id = Ids.newId();
        long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        UserAccount ua = new UserAccount(
                id,
                String.format("U%06d", seq),
                username,
                "Test " + username,
                username + "@thi360.test",
                null,
                UserSource.ADMIN_CREATED,
                status,
                false,
                Instant.now(),
                null);
        userAccountRepository.save(ua);
        if (deletedAt != null) {
            jdbcTemplate.update(
                    "UPDATE identity.user_account SET deleted_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(deletedAt),
                    id);
        }
        createdUserIds.add(id);
        return ua;
    }

    private Role createCustomRole(String code, String name, Access access, RoleStatus status) {
        Role role = new Role(Ids.newId(), code, name, null, access, false, status, Instant.now(), null);
        roleRepository.save(role);
        createdRoleIds.add(role.getId());
        return role;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
