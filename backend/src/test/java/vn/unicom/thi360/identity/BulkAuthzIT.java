package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.unicom.thi360.shared.security.TestSecurity.asPlatformAdmin;
import static vn.unicom.thi360.shared.security.TestSecurity.asUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.application.AuthzSnapshotService;
import vn.unicom.thi360.identity.domain.PermissionEntity;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.RolePermission;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserPermission;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserPermissionRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class BulkAuthzIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.111";

    @Autowired
    private KeycloakIdentityClient keycloakClient;

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
    private AuthzSnapshotService authzSnapshotService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdRoleIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanupRateLimit() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

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
    }

    @Test
    void bulkValidation_over500_missingIdempotency_missingReason() throws Exception {
        Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        PermissionEntity userView = permissionRepository.findByCode(Permissions.USER_VIEW).orElseThrow();

        // > 500 users -> 400
        List<UUID> over500 = new ArrayList<>(501);
        for (int i = 0; i < 501; i++) {
            over500.add(Ids.newId());
        }
        String over500Body = objectMapper.writeValueAsString(Map.of(
                "userIds", over500,
                "action", "GRANT",
                "target", "ROLE",
                "roleIds", List.of(contentAdmin.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(over500Body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Missing Idempotency-Key -> 400
        String validBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(Ids.newId()),
                "action", "GRANT",
                "target", "ROLE",
                "roleIds", List.of(contentAdmin.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Missing reason when GRANT + PERMISSION -> 400
        String missingReasonBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(Ids.newId()),
                "action", "GRANT",
                "target", "PERMISSION",
                "permissionIds", List.of(userView.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingReasonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REASON_REQUIRED"));
    }

    @Test
    void bulkPreviewAndApply_allReasonCodes_noChangeFromRole_idempotency_andAudit() throws Exception {
        Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        Role operations = roleRepository.findByCodeNormalized("operations").orElseThrow();
        Role student = roleRepository.findByCodeNormalized("student").orElseThrow();
        Role inactiveRole = createCustomRole("BULK_INACT_" + suffix(), Access.MANAGE, RoleStatus.INACTIVE);
        Role badExamRole = createCustomRole("BULK_BAD_EXAM_" + suffix(), Access.EXAM, RoleStatus.ACTIVE);

        PermissionEntity userView = permissionRepository.findByCode(Permissions.USER_VIEW).orElseThrow();
        PermissionEntity auditView = permissionRepository.findByCode(Permissions.AUDIT_VIEW).orElseThrow();

        // Corrupt badExamRole in DB to have a MANAGE permission to verify PERMISSION_SCOPE_MISMATCH
        rolePermissionRepository.save(new RolePermission(badExamRole.getId(), userView.getId(), null, Instant.now()));

        UserAccount manageUser = createUser("bulk.manage." + suffix(), UserStatus.ACTIVE, null);
        UserAccount examOnlyUser = createUser("bulk.exam." + suffix(), UserStatus.ACTIVE, null);
        UserAccount deletedUser = createUser("bulk.deleted." + suffix(), UserStatus.INACTIVE, Instant.now());
        UserAccount alreadyHasFromRole = createUser("bulk.hasrole." + suffix(), UserStatus.ACTIVE, null);
        UUID missingUserId = Ids.newId();

        userRoleRepository.save(new UserRole(manageUser.getId(), contentAdmin.getId(), null, Instant.now()));
        userRoleRepository.save(new UserRole(examOnlyUser.getId(), student.getId(), null, Instant.now()));
        userRoleRepository.save(new UserRole(alreadyHasFromRole.getId(), operations.getId(), null, Instant.now()));

        // 1. Test USER_NOT_AVAILABLE, DIRECT_PERMISSION_REQUIRES_MANAGE, NO_CHANGE (permission already from role), WILL_CHANGE
        String permGrantBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(
                        manageUser.getId(),
                        examOnlyUser.getId(),
                        alreadyHasFromRole.getId(),
                        deletedUser.getId(),
                        missingUserId),
                "action", "GRANT",
                "target", "PERMISSION",
                "permissionIds", List.of(userView.getId()),
                "reason", "Cấp quyền xem tài khoản hàng loạt"));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(permGrantBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.willChange").value(1))
                .andExpect(jsonPath("$.noChange").value(1))
                .andExpect(jsonPath("$.skipped").value(3))
                .andExpect(jsonPath("$.items[0].outcome").value("WILL_CHANGE"))
                .andExpect(jsonPath("$.items[0].added", containsInAnyOrder("USER.VIEW")))
                .andExpect(jsonPath("$.items[1].outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.items[1].reasonCode").value("DIRECT_PERMISSION_REQUIRES_MANAGE"))
                .andExpect(jsonPath("$.items[2].outcome").value("NO_CHANGE"))
                .andExpect(jsonPath("$.items[3].outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.items[3].reasonCode").value("USER_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.items[4].outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.items[4].reasonCode").value("USER_NOT_AVAILABLE"));

        // 2. Apply twice with same Idempotency-Key -> applies & audits once, skipped users do NOT fail the batch
        String idempotencyKey = "bulk-grant-" + UUID.randomUUID();
        MvcResult firstApply = mockMvc.perform(post("/api/v1/admin/authz/bulk")
                        .with(asPlatformAdmin())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(permGrantBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").isNotEmpty())
                .andExpect(jsonPath("$.willChange").value(1))
                .andExpect(jsonPath("$.noChange").value(1))
                .andExpect(jsonPath("$.skipped").value(3))
                .andReturn();

        MvcResult secondApply = mockMvc.perform(post("/api/v1/admin/authz/bulk")
                        .with(asPlatformAdmin())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(permGrantBody))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(secondApply.getResponse().getContentAsString())
                .isEqualTo(firstApply.getResponse().getContentAsString());

        JsonNode batchJson = objectMapper.readTree(firstApply.getResponse().getContentAsString());
        String batchId = batchJson.get("batchId").asText();

        // Only 1 USER_PERMISSIONS_CHANGED + 1 AUTHZ_BULK_APPLIED despite calling apply twice!
        verify(auditRecorder, times(1)).record(argThat(e ->
                "USER_PERMISSIONS_CHANGED".equals(e.action())
                        && batchId.equals(e.after().get("batchId"))));
        verify(auditRecorder, times(1)).record(argThat(e ->
                "AUTHZ_BULK_APPLIED".equals(e.action())
                        && batchId.equals(e.after().get("batchId"))));

        // alreadyHasFromRole must NOT get a duplicate direct user_permission row
        assertThat(userPermissionRepository.findAllByIdUserId(alreadyHasFromRole.getId())).isEmpty();
        assertThat(userPermissionRepository.findAllByIdUserId(manageUser.getId())).hasSize(1);

        // 3. Test PRIVILEGE_ESCALATION reasonCode (actor only has USER.ASSIGN, tries to grant AUDIT.VIEW)
        String escalateBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(manageUser.getId()),
                "action", "GRANT",
                "target", "PERMISSION",
                "permissionIds", List.of(auditView.getId()),
                "reason", "Try escalate"));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asUser(Permissions.USER_ASSIGN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(escalateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.items[0].reasonCode").value("PRIVILEGE_ESCALATION"));

        // 4. Test ROLE_INACTIVE reasonCode
        String inactiveRoleBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(manageUser.getId()),
                "action", "GRANT",
                "target", "ROLE",
                "roleIds", List.of(inactiveRole.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inactiveRoleBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.items[0].reasonCode").value("ROLE_INACTIVE"));

        // 5. Test PERMISSION_SCOPE_MISMATCH reasonCode
        String badExamRoleBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(manageUser.getId()),
                "action", "GRANT",
                "target", "ROLE",
                "roleIds", List.of(badExamRole.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badExamRoleBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.items[0].reasonCode").value("PERMISSION_SCOPE_MISMATCH"));

        // 6. Test SELF_LOCKOUT reasonCode (actor revokes own role containing USER.ASSIGN)
        UserAccount selfAdmin = createUser("bulk.self." + suffix(), UserStatus.ACTIVE, null);
        Role sysAdmin = roleRepository.findByCodeNormalized("system_admin").orElseThrow();
        userRoleRepository.save(new UserRole(selfAdmin.getId(), sysAdmin.getId(), null, Instant.now()));

        String selfRevokeBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(selfAdmin.getId()),
                "action", "REVOKE",
                "target", "ROLE",
                "roleIds", List.of(sysAdmin.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asUser(selfAdmin.getId(), selfAdmin.getUsername(),
                                Permissions.USER_ASSIGN, Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfRevokeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.items[0].reasonCode").value("SELF_LOCKOUT"));
    }

    @Test
    void bulkPreviewStaleData_applyRecomputes_andBulkRevokeEvictsActiveSession() throws Exception {
        String username = "bulk.live." + suffix();
        UUID kcId = keycloakClient.createUser(new KeycloakIdentityClient.CreateUserCommand(
                username, username + "@thi360.test", "Bulk Live User", "Test", "Valid@1234", true, false, true));
        long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        UserAccount account = new UserAccount(
                kcId,
                String.format("U%06d", seq),
                username,
                "Bulk Live User",
                username + "@thi360.test",
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                null);
        userAccountRepository.save(account);
        createdUserIds.add(kcId);

        Role operations = roleRepository.findByCodeNormalized("operations").orElseThrow();
        Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        userRoleRepository.save(new UserRole(kcId, operations.getId(), null, Instant.now()));
        userRoleRepository.save(new UserRole(kcId, contentAdmin.getId(), null, Instant.now()));

        // Login with real Keycloak session
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                        .header("X-Forwarded-For", TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(atCookie).isNotNull();

        // Verify live user can access /api/v1/admin/roles/{id} before bulk revoke? Wait: user has OPERATIONS (USER.VIEW, USER.MODIFY...)
        // User has MANAGE access, can call GET /api/v1/admin/users/{id}/roles (needs USER.VIEW)
        mockMvc.perform(get("/api/v1/admin/users/{id}/roles", kcId)
                        .cookie(atCookie))
                .andExpect(status().isOk());

        // Preview granting CONTENT_ADMIN (currently NO_CHANGE because user already has it)
        String grantContentBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(kcId),
                "action", "GRANT",
                "target", "ROLE",
                "roleIds", List.of(contentAdmin.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk/preview")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantContentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noChange").value(1));

        // Data changes between preview and apply: remove CONTENT_ADMIN directly
        userRoleRepository.deleteById(new vn.unicom.thi360.identity.domain.UserRoleId(kcId, contentAdmin.getId()));

        // Apply recomputes -> WILL_CHANGE and adds CONTENT_ADMIN back!
        mockMvc.perform(post("/api/v1/admin/authz/bulk")
                        .with(asPlatformAdmin())
                        .header("Idempotency-Key", "stale-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantContentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.willChange").value(1))
                .andExpect(jsonPath("$.items[0].added[0]").value("CONTENT_ADMIN"));

        // Now bulk REVOKE OPERATIONS role -> user loses USER.VIEW -> next request immediately returns 403!
        String revokeOpsBody = objectMapper.writeValueAsString(Map.of(
                "userIds", List.of(kcId),
                "action", "REVOKE",
                "target", "ROLE",
                "roleIds", List.of(operations.getId())));

        mockMvc.perform(post("/api/v1/admin/authz/bulk")
                        .with(asPlatformAdmin())
                        .header("Idempotency-Key", "revoke-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokeOpsBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.willChange").value(1));

        mockMvc.perform(get("/api/v1/admin/users/{id}/roles", kcId)
                        .cookie(atCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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

    private Role createCustomRole(String code, Access access, RoleStatus status) {
        Role role = new Role(Ids.newId(), code, code, null, access, false, status, Instant.now(), null);
        roleRepository.save(role);
        createdRoleIds.add(role.getId());
        return role;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
