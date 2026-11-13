package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.application.AuthzSnapshotService;
import vn.unicom.thi360.identity.application.ProvisioningReconcilerJob;
import vn.unicom.thi360.identity.domain.PlatformAdmin;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.AccountDeletionGuard;
import vn.unicom.thi360.identity.spi.ActiveAttemptChecker;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.identity.spi.UserDeleted;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxEventRepository;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SessionStore;

@Import(UserAccountAdminIT.FakeGuardsConfig.class)
class UserAccountAdminIT extends AbstractKeycloakIntegrationTest {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProvisioningReconcilerJob provisioningReconcilerJob;

    @Autowired
    private FakeGuardsControl fakeGuardsControl;
    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdKeycloakUserIds = new ArrayList<>();
    private final List<UUID> createdPlatformIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        clearInvocations(auditRecorder);
        fakeGuardsControl.reset();
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + "203.0.113.77");
    }

    @AfterEach
    void cleanup() {
        fakeGuardsControl.reset();
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + "203.0.113.77");
        reset(keycloakClient);
        for (UUID kcId : createdKeycloakUserIds) {
            try {
                keycloakClient.deleteUser(kcId);
            } catch (Exception ignored) {
                // Ignore if already deleted
            }
        }
        createdKeycloakUserIds.clear();

        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_permission WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        createdUserIds.clear();

        for (UUID pid : createdPlatformIds) {
            platformAdminRepository.deleteById(pid);
        }
        createdPlatformIds.clear();
    }

    @Test
    void everyNewEndpoint_returns403WhenMissingRequiredPermission() throws Exception {
        UUID anyId = Ids.newId();

        mockMvc.perform(get("/api/v1/admin/users").with(asUser(Permissions.ROLE_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}", anyId).with(asUser(Permissions.ROLE_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "forbidden.user",
                                  "password": "Valid@1234",
                                  "fullName": "Forbidden User"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}", anyId)
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/username-availability")
                        .param("username", "check.user")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ac01_1_listUsers_includesAllSourcesAndNeverLoggedIn_excludesPlatformAdminAndProvisioning_andSupportsFiltersAndSorts()
            throws Exception {
        String sfx = suffix();
        UserAccount adminCreated = insertUser("u.admin." + sfx, "Admin Created " + sfx, "ac." + sfx + "@thi360.vn",
                "0901000001", UserSource.ADMIN_CREATED, UserStatus.ACTIVE, null, null);
        UserAccount bulkUser = insertUser("u.bulk." + sfx, "Bulk User " + sfx, "bulk." + sfx + "@thi360.vn",
                "0901000002", UserSource.BULK_IMPORT, UserStatus.ACTIVE, Instant.now().minusSeconds(60), null);
        UserAccount selfReg = insertUser("u.self." + sfx, "Self Reg " + sfx, "self." + sfx + "@thi360.vn",
                "0901000003", UserSource.SELF_REGISTERED, UserStatus.INACTIVE, null, null);
        UserAccount googleUser = insertUser("u.google." + sfx, "Google User " + sfx, "google." + sfx + "@thi360.vn",
                "0901000004", UserSource.GOOGLE, UserStatus.ACTIVE, Instant.now().minusSeconds(120), null);
        UserAccount deletedUser = insertUser("u.deleted." + sfx, "Deleted User " + sfx, null,
                null, UserSource.ADMIN_CREATED, UserStatus.INACTIVE, null, Instant.now());
        UserAccount provisioningUser = insertUser("u.prov." + sfx, "Provisioning " + sfx, null,
                null, UserSource.ADMIN_CREATED, UserStatus.PROVISIONING, null, null);

        PlatformAdmin platformAdmin = new PlatformAdmin(Ids.newId(), "platform.hidden." + sfx, Instant.now());
        platformAdminRepository.save(platformAdmin);
        createdPlatformIds.add(platformAdmin.getId());

        Role studentRole = roleRepository.findByCodeNormalized("student").orElseThrow();
        jdbcTemplate.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_at) VALUES (?, ?, now())",
                adminCreated.getId(), studentRole.getId());

        // Filter by q=sfx -> sees all 4 active/inactive non-deleted, non-provisioning accounts, never PLATFORM_ADMIN
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", sfx)
                        .param("size", "50")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[*].username", hasItem(adminCreated.getUsername())))
                .andExpect(jsonPath("$.items[*].username", hasItem(bulkUser.getUsername())))
                .andExpect(jsonPath("$.items[*].username", hasItem(selfReg.getUsername())))
                .andExpect(jsonPath("$.items[*].username", hasItem(googleUser.getUsername())))
                .andExpect(jsonPath("$.items[*].username", not(hasItem(deletedUser.getUsername()))))
                .andExpect(jsonPath("$.items[*].username", not(hasItem(provisioningUser.getUsername()))))
                .andExpect(jsonPath("$.items[*].username", not(hasItem(platformAdmin.getUsername()))));

        // API still returns code and lastLoginAt (even when null)
        mockMvc.perform(get("/api/v1/admin/users/{id}", adminCreated.getId())
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(adminCreated.getCode()))
                .andExpect(jsonPath("$.lastLoginAt").isEmpty());

        // Search by code, phone, email
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", adminCreated.getCode())
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(adminCreated.getId().toString()));

        // Filters: source, status, roleId, neverLoggedIn, includeDeleted, sort
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", sfx)
                        .param("source", "GOOGLE")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(googleUser.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", sfx)
                        .param("status", "INACTIVE")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(selfReg.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", sfx)
                        .param("roleId", studentRole.getId().toString())
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(adminCreated.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", sfx)
                        .param("neverLoggedIn", "true")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", sfx)
                        .param("includeDeleted", "true")
                        .param("sort", "username,asc")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(5));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("q", sfx)
                        .param("sort", "lastLoginAt,desc")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(bulkUser.getId().toString()));

        // classId filter rejected clearly until T10
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("classId", Ids.newId().toString())
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_SUPPORTED"));
    }

    @Test
    void createUser_defaultStudent_provisionsInKeycloakAndDb_omitsPasswordFromResponseAndAudit() throws Exception {
        String sfx = suffix();
        String username = "New.Student." + sfx;
        String secretPwd = "SecretPwd@99";

        MvcResult result = mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "fullName": "Nguyễn Văn An",
                                  "email": "an.%s@thi360.vn",
                                  "phone": "0901234567"
                                }
                                """.formatted(username, secretPwd, sfx)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.source").value("ADMIN_CREATED"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.roles[0].code").value("STUDENT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(secretPwd).doesNotContain("password\"");

        JsonNode json = objectMapper.readTree(body);
        UUID userId = UUID.fromString(json.get("id").asText());
        createdUserIds.add(userId);
        createdKeycloakUserIds.add(userId);

        assertThat(keycloakClient.findUserIdByUsername(username)).contains(userId);

        verify(auditRecorder).record(argThat(event ->
                "USER_CREATED".equals(event.action())
                        && userId.toString().equals(event.targetId())
                        && event.after() != null
                        && !event.after().toString().contains(secretPwd)
                        && !event.after().containsKey("password")));

        // Verify outbox payload has no password
        String outboxJson = outboxEventRepository.findAll().stream()
                .map(e -> e.getPayload())
                .reduce("", String::concat);
        assertThat(outboxJson).doesNotContain(secretPwd);
    }

    @Test
    void createUser_nonStudentRole_requiresUserAssignAndPrivilegeGuard() throws Exception {
        String sfx = suffix();
        Role contentAdmin = roleRepository.findByCodeNormalized("content_admin").orElseThrow();
        Role systemAdmin = roleRepository.findByCodeNormalized("system_admin").orElseThrow();

        // Missing USER.ASSIGN -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "role.noassign.%s",
                                  "password": "Valid@1234",
                                  "fullName": "No Assign",
                                  "roleIds": ["%s"]
                                }
                                """.formatted(sfx, contentAdmin.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Has USER.ASSIGN but lacks SYSTEM_ADMIN's permissions -> 403 PRIVILEGE_ESCALATION
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY, Permissions.USER_ASSIGN, Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "role.escalate.%s",
                                  "password": "Valid@1234",
                                  "fullName": "Escalate",
                                  "roleIds": ["%s"]
                                }
                                """.formatted(sfx, systemAdmin.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVILEGE_ESCALATION"));

        // Has USER.ASSIGN + CONTENT_ADMIN permissions -> 201
        MvcResult okRes = mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(
                                Permissions.USER_MODIFY,
                                Permissions.USER_ASSIGN,
                                Permissions.CATALOG_VIEW,
                                Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "role.ok.%s",
                                  "password": "Valid@1234",
                                  "fullName": "Content Admin User",
                                  "roleIds": ["%s"]
                                }
                                """.formatted(sfx, contentAdmin.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0].code").value("CONTENT_ADMIN"))
                .andReturn();

        UUID createdId = UUID.fromString(objectMapper.readTree(okRes.getResponse().getContentAsString()).get("id").asText());
        createdUserIds.add(createdId);
        createdKeycloakUserIds.add(createdId);
    }

    @Test
    void createUser_nonEmptyClassIds_returns400ClassNotSupported() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "class.reject.%s",
                                  "password": "Valid@1234",
                                  "fullName": "Class Reject",
                                  "classIds": ["%s"]
                                }
                                """.formatted(suffix(), Ids.newId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_SUPPORTED"));
    }

    @Test
    void createUser_passwordConditions_returnPasswordWeak() throws Exception {
        String sfx = suffix();
        List<String> weakPasswords = List.of(
                "abcd1234!",       // missing uppercase
                "ABCD1234!",       // missing lowercase
                "Abcdefgh!",       // missing digit
                "Abcd1234",        // missing special
                "Ab1!xyz",         // shorter than 8
                "weak.user." + sfx // same as username (Wait: "weak.user.xxx" has no upper/digit? use Uppercase same-as-username below)
        );
        for (String pwd : weakPasswords) {
            mockMvc.perform(post("/api/v1/admin/users")
                            .with(asUser(Permissions.USER_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "weak.user.%s",
                                      "password": "%s",
                                      "fullName": "Weak Password User"
                                    }
                                    """.formatted(sfx, pwd)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"));
        }

        // Same as username while meeting complexity rules
        String sameUsername = "Same@123." + sfx;
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "fullName": "Same Password User"
                                }
                                """.formatted(sameUsername, sameUsername.toLowerCase())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"));
    }

    @Test
    void createUser_keycloakFailureAfterStep1_returns502AndLeavesProvisioningRow() throws Exception {
        String sfx = suffix();
        String username = "kc.fail." + sfx;

        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR"))
                .when(keycloakClient)
                .createUser(any());

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Valid@1234",
                                  "fullName": "KC Failure User"
                                }
                                """.formatted(username)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.message").value("Không thể kết nối dịch vụ xác thực, vui lòng thử lại sau"));

        UserAccount provisioning = userAccountRepository.findActiveByUsernameNormalized(username).orElseThrow();
        createdUserIds.add(provisioning.getId());
        assertThat(provisioning.getStatus()).isEqualTo(UserStatus.PROVISIONING);
    }

    @Test
    void createUser_concurrentSameUsername_exactlyOne201AndOne409UsernameTaken() throws Exception {
        String sfx = suffix();
        String usernameA = "Race.User." + sfx;
        String usernameB = "race.user." + sfx;

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> taskA = () -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/admin/users")
                                .with(asUser(Permissions.USER_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "%s",
                                          "password": "Valid@1234",
                                          "fullName": "Race User A"
                                        }
                                        """.formatted(usernameA)))
                        .andReturn();
            };
            Callable<MvcResult> taskB = () -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/admin/users")
                                .with(asUser(Permissions.USER_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "%s",
                                          "password": "Valid@1234",
                                          "fullName": "Race User B"
                                        }
                                        """.formatted(usernameB)))
                        .andReturn();
            };

            Future<MvcResult> fA = pool.submit(taskA);
            Future<MvcResult> fB = pool.submit(taskB);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            MvcResult rA = fA.get(30, TimeUnit.SECONDS);
            MvcResult rB = fB.get(30, TimeUnit.SECONDS);

            List<Integer> statuses = List.of(rA.getResponse().getStatus(), rB.getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);

            MvcResult conflictRes = rA.getResponse().getStatus() == 409 ? rA : rB;
            MvcResult createdRes = rA.getResponse().getStatus() == 201 ? rA : rB;

            JsonNode conflictJson = objectMapper.readTree(conflictRes.getResponse().getContentAsString());
            assertThat(conflictJson.get("code").asText()).isEqualTo("USERNAME_TAKEN");

            UUID createdId = UUID.fromString(
                    objectMapper.readTree(createdRes.getResponse().getContentAsString()).get("id").asText());
            createdUserIds.add(createdId);
            createdKeycloakUserIds.add(createdId);

            Integer activeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM identity.user_account WHERE username_normalized = ? AND deleted_at IS NULL",
                    Integer.class,
                    usernameB);
            assertThat(activeCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void updateUser_patchEmailClearsEmailVerifiedAt_auditsOnlyChangedFields_andChecksOptimisticVersion()
            throws Exception {
        String sfx = suffix();
        String username = "patch.user." + sfx;
        String initialEmail = "old." + sfx + "@thi360.vn";
        String updatedEmail = "new." + sfx + "@thi360.vn";

        MvcResult createdRes = mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Valid@1234",
                                  "fullName": "Before Name",
                                  "email": "%s",
                                  "phone": "0909999888"
                                }
                                """.formatted(username, initialEmail)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createdRes.getResponse().getContentAsString());
        UUID userId = UUID.fromString(createdJson.get("id").asText());
        long version = createdJson.get("version").asLong();
        createdUserIds.add(userId);
        createdKeycloakUserIds.add(userId);

        // Mark email verified in DB
        jdbcTemplate.update("UPDATE identity.user_account SET email_verified_at = now() WHERE id = ?", userId);
        clearInvocations(auditRecorder);

        // PATCH only email & fullName (leave phone unchanged)
        mockMvc.perform(patch("/api/v1/admin/users/{id}", userId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "After Name",
                                  "email": "%s",
                                  "phone": "0909999888",
                                  "version": %d
                                }
                                """.formatted(updatedEmail, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("After Name"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.emailVerifiedAt").isEmpty())
                .andExpect(jsonPath("$.phone").value("0909999888"))
                .andExpect(jsonPath("$.version").value(version + 1));

        UserAccount reloaded = userAccountRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getEmailVerifiedAt()).isNull();

        verify(auditRecorder).record(argThat(event ->
                "USER_UPDATED".equals(event.action())
                        && event.before() != null
                        && event.after() != null
                        && "Before Name".equals(event.before().get("fullName"))
                        && "After Name".equals(event.after().get("fullName"))
                        && initialEmail.equals(event.before().get("email"))
                        && updatedEmail.equals(event.after().get("email"))
                        && event.before().containsKey("emailVerifiedAt")
                        && !event.before().containsKey("phone")
                        && !event.after().containsKey("phone")));

        // Stale version -> 412 VERSION_CONFLICT
        mockMvc.perform(patch("/api/v1/admin/users/{id}", userId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Stale Update",
                                  "version": %d
                                }
                                """.formatted(version)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void usernameAvailability_returnsExpectedStatusAndReasonCodes() throws Exception {
        String sfx = suffix();
        insertUser("Taken.User." + sfx, "Taken User", null, null,
                UserSource.ADMIN_CREATED, UserStatus.ACTIVE, null, null);
        insertUser("Deleted.Free." + sfx, "Deleted Free", null, null,
                UserSource.ADMIN_CREATED, UserStatus.INACTIVE, null, Instant.now());

        mockMvc.perform(get("/api/v1/admin/users/username-availability")
                        .param("username", "valid.new." + sfx)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reasonCode").isEmpty());

        mockMvc.perform(get("/api/v1/admin/users/username-availability")
                        .param("username", "ADMIN")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasonCode").value("USERNAME_RESERVED"));

        mockMvc.perform(get("/api/v1/admin/users/username-availability")
                        .param("username", "ab")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasonCode").value("USERNAME_INVALID"));

        mockMvc.perform(get("/api/v1/admin/users/username-availability")
                        .param("username", "taken.user." + sfx)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasonCode").value("USERNAME_TAKEN"));

        // Soft-deleted username is reusable
        mockMvc.perform(get("/api/v1/admin/users/username-availability")
                        .param("username", "deleted.free." + sfx)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void slice2_everyNewEndpoint_returns403WhenMissingRequiredPermission() throws Exception {
        UUID anyId = Ids.newId();

        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", anyId)
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Violation\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/activate", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", anyId)
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Valid@1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/admin/users/{id}", anyId)
                        .param("reason", "Cleanup")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/deletion-check", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users/{id}/deactivation-check", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/users/bulk-actions/preview")
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DEACTIVATE\",\"userIds\":[\"" + anyId + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/users/bulk-actions")
                        .header("Idempotency-Key", "k-" + suffix())
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DEACTIVATE\",\"userIds\":[\"" + anyId + "\"],\"reason\":\"Test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ac01_5_deactivateRevokesSessions_activateReenables_selfDeactivate409_andPrivilegeGuardEnforced()
            throws Exception {
        String sfx = suffix();
        String username = "life.user." + sfx;
        String password = "Valid@1234";
        UUID userId = createUserViaApi(username, password, "Lifecycle User", "life." + sfx + "@thi360.vn", null);

        // User logs in -> gets valid session cookie and can call /me
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(atCookie).isNotNull();

        mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                .andExpect(status().isOk());

        // Self-deactivate -> 409 SELF_DEACTIVATE
        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", userId)
                        .with(asUser(userId, username, Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Self attempt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_DEACTIVATE"));

        // Deactivate without reason -> 400 VALIDATION_FAILED with stable code REASON_REQUIRED
        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", userId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REASON_REQUIRED"));

        // Deactivate -> INACTIVE, sessions revoked immediately, audit recorded
        clearInvocations(auditRecorder);
        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", userId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vi phạm quy chế\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").value("Vi phạm quy chế"));

        // Existing session cookie now fails with 401
        mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                .andExpect(status().isUnauthorized());

        // Login blocked while inactive
        // Login blocked while inactive (Keycloak user disabled -> 401 INVALID_CREDENTIALS, matches AuthFlowIT)
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verify(auditRecorder).record(argThat(ev ->
                "USER_DEACTIVATED".equals(ev.action())
                        && userId.toString().equals(ev.targetId())
                        && "Vi phạm quy chế".equals(ev.reason())
                        && "ACTIVE".equals(ev.before().get("status"))
                        && "INACTIVE".equals(ev.after().get("status"))));

        // Activate -> ACTIVE, login succeeds again
        clearInvocations(auditRecorder);
        mockMvc.perform(post("/api/v1/admin/users/{id}/activate", userId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").isEmpty());

        verify(auditRecorder).record(argThat(ev ->
                "USER_ACTIVATED".equals(ev.action())
                        && userId.toString().equals(ev.targetId())
                        && "INACTIVE".equals(ev.before().get("status"))
                        && "ACTIVE".equals(ev.after().get("status"))));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());

        // PrivilegeGuard: actor with only USER.MODIFY cannot mutate a SYSTEM_ADMIN user, PLATFORM_ADMIN can
        Role sysAdminRole = roleRepository.findByCodeNormalized("system_admin").orElseThrow();
        UUID sysAdminUserId = createUserViaApi(
                "sysadmin.target." + sfx,
                "Valid@1234",
                "Target SysAdmin",
                null,
                List.of(sysAdminRole.getId()));

        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", sysAdminUserId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Attempt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVILEGE_ESCALATION"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}", sysAdminUserId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Attempt\",\"version\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVILEGE_ESCALATION"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", sysAdminUserId)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Platform override\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void resetPassword_validatesPolicy_revokesSessions_neverReturnsOrAuditsPassword() throws Exception {
        String sfx = suffix();
        String username = "reset.user." + sfx;
        String oldPwd = "OldValid@123";
        String newPwd = "NewSecret@456";
        UUID userId = createUserViaApi(username, oldPwd, "Reset Target", null, null);

        // Login with old password
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + oldPwd + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie oldAt = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);

        // Weak password -> 400 PASSWORD_WEAK
        mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", userId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_WEAK"));

        // Reset password -> revokes session, does not return or audit password
        clearInvocations(auditRecorder);
        MvcResult resetRes = mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", userId)
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "%s",
                                  "mustChangePassword": true,
                                  "reason": "Quên mật khẩu"
                                }
                                """.formatted(newPwd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();

        String responseBody = resetRes.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(newPwd).doesNotContain("\"password\"");

        // Old session revoked
        mockMvc.perform(get("/api/v1/me").cookie(oldAt))
                .andExpect(status().isUnauthorized());

        // New password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"" + newPwd + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        verify(auditRecorder).record(argThat(ev ->
                "USER_PASSWORD_RESET".equals(ev.action())
                        && userId.toString().equals(ev.targetId())
                        && "Quên mật khẩu".equals(ev.reason())
                        && (ev.before() == null || !ev.before().toString().contains(newPwd))
                        && (ev.after() == null || !ev.after().toString().contains(newPwd))));
        String outboxDump = outboxEventRepository.findAll().stream()
                .map(e -> e.getPayload())
                .reduce("", String::concat);
        assertThat(outboxDump).doesNotContain(newPwd);
    }

    @Test
    void ac01_6_guardsBlockDeleteWith409AndFieldErrors_unblockedSoftDeletesAndAllowsRecreateWithNewCode()
            throws Exception {
        String sfx = suffix();
        String username = "del.user." + sfx;
        String email = "del." + sfx + "@thi360.vn";
        UUID userId = createUserViaApi(username, "Valid@1234", "Delete Target", email, null);
        UserAccount created = userAccountRepository.findById(userId).orElseThrow();
        String originalCode = created.getCode();

        // Deactivation check reflects ActiveAttemptChecker SPI
        fakeGuardsControl.setActiveAttempt(true);
        mockMvc.perform(get("/api/v1/admin/users/{id}/deactivation-check", userId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAttempt").value(true));
        fakeGuardsControl.setActiveAttempt(false);
        mockMvc.perform(get("/api/v1/admin/users/{id}/deactivation-check", userId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAttempt").value(false));

        // Deletion guard blocks -> deletion-check false & DELETE 409 USER_HAS_TRANSACTIONS with fieldErrors
        fakeGuardsControl.setBlockers(List.of(
                new AccountDeletionGuard.Blocker("COMMERCE", "USER_HAS_ORDERS", "Có 2 đơn hàng cần đối soát")));

        mockMvc.perform(get("/api/v1/admin/users/{id}/deletion-check", userId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(false))
                .andExpect(jsonPath("$.blockers[0].source").value("COMMERCE"));

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId)
                        .param("reason", "Yêu cầu xóa")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_TRANSACTIONS"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("COMMERCE"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("USER_HAS_ORDERS"));

        // Unblock -> deletion-check true & DELETE 204, Keycloak deleted, outbox UserDeleted relayed cleanly
        fakeGuardsControl.setBlockers(List.of());
        mockMvc.perform(get("/api/v1/admin/users/{id}/deletion-check", userId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());

        fakeGuardsControl.clearDeletedEvents();
        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId)
                        .param("reason", "Người dùng yêu cầu")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isNoContent());

        UserAccount softDeleted = userAccountRepository.findById(userId).orElseThrow();
        assertThat(softDeleted.getDeletedAt()).isNotNull();
        assertThat(softDeleted.getDeleteReason()).isEqualTo("Người dùng yêu cầu");
        assertThat(keycloakClient.findUserIdByUsername(username)).isEmpty();

        outboxRelay.relayBatch();
        assertThat(fakeGuardsControl.deletedEvents())
                .anyMatch(e -> e.userId().equals(userId) && e.username().equals(username));

        // Recreate with exact same username and email succeeds with a new code and id
        UUID recreatedId = createUserViaApi(username, "Valid@1234", "Recreated User", email, null);
        UserAccount recreated = userAccountRepository.findById(recreatedId).orElseThrow();
        assertThat(recreated.getId()).isNotEqualTo(userId);
        assertThat(recreated.getCode()).isNotEqualTo(originalCode);
    }

    @Test
    void bulkActions_previewAndApply_withIdempotencyReplayAndConflict() throws Exception {
        String sfx = suffix();
        UUID u1 = createUserViaApi("bulk.a." + sfx, "Valid@1234", "Bulk A", null, null);
        UUID u2 = createUserViaApi("bulk.b." + sfx, "Valid@1234", "Bulk B", null, null);
        UUID missingId = Ids.newId();

        // Preview DEACTIVATE with u1 (eligible), u2 (eligible), u1 as self (skipped), missingId (skipped)
        mockMvc.perform(post("/api/v1/admin/users/bulk-actions/preview")
                        .with(asUser(u1, "bulk.a." + sfx, Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "DEACTIVATE",
                                  "userIds": ["%s", "%s", "%s"]
                                }
                                """.formatted(u1, u2, missingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.affected").value(1))
                .andExpect(jsonPath("$.skipped.length()").value(2));

        // Apply with Idempotency-Key
        String idemKey = "bulk-deact-" + sfx;
        String applyPayload = """
                {
                  "action": "DEACTIVATE",
                  "userIds": ["%s", "%s", "%s"],
                  "reason": "Khóa hàng loạt"
                }
                """.formatted(u1, u2, missingId);

        MvcResult firstApply = mockMvc.perform(post("/api/v1/admin/users/bulk-actions")
                        .header("Idempotency-Key", idemKey)
                        .with(asUser(u1, "bulk.a." + sfx, Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andReturn();
        String firstBatchId = objectMapper.readTree(firstApply.getResponse().getContentAsString())
                .get("batchId").asText();

        // Replay with same key + same body -> returns exact same batchId without re-calling Keycloak
        clearInvocations(keycloakClient);
        MvcResult replayApply = mockMvc.perform(post("/api/v1/admin/users/bulk-actions")
                        .header("Idempotency-Key", idemKey)
                        .with(asUser(u1, "bulk.a." + sfx, Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(firstBatchId))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andReturn();
        assertThat(replayApply.getResponse().getContentAsString())
                .isEqualTo(firstApply.getResponse().getContentAsString());

        // Reuse same key with different body -> 409 IDEMPOTENCY_KEY_REUSED
        mockMvc.perform(post("/api/v1/admin/users/bulk-actions")
                        .header("Idempotency-Key", idemKey)
                        .with(asUser(u1, "bulk.a." + sfx, Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "ACTIVATE",
                                  "userIds": ["%s"]
                                }
                                """.formatted(u2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void provisioningReconcilerJob_cleansUpStaleProvisioningAndPromotesCompleteAccounts() throws Exception {
        String sfx = suffix();

        // Case 1: Keycloak failed after Tx1 (user does not exist in KC), older than 10 min -> deleted from DB
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", "Simulated outage", null))
                .when(keycloakClient)
                .createUser(any());
        String noKcUsername = "reconcile.nokc." + sfx;
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Valid@1234",
                                  "fullName": "No KC"
                                }
                                """.formatted(noKcUsername)))
                .andExpect(status().isBadGateway());
        reset(keycloakClient);

        UserAccount noKcRow = userAccountRepository.findActiveByUsernameNormalized(noKcUsername).orElseThrow();
        jdbcTemplate.update(
                "UPDATE identity.user_account SET created_at = now() - interval '15 minutes' WHERE id = ?",
                noKcRow.getId());

        // Case 2: Keycloak createUser succeeded, setPassword failed (KC user exists with NO password), > 10 min -> delete both sides
        String partialKcUsername = "reconcile.nopwd." + sfx;
        UUID partialKcId = keycloakClient.createUser(new KeycloakIdentityClient.CreateUserCommand(
                partialKcUsername, null, "Partial", "NoPwd", null, true, false, false));
        createdKeycloakUserIds.add(partialKcId);
        UserAccount partialRow = insertUser(
                partialKcUsername, "Partial NoPwd", null, null,
                UserSource.ADMIN_CREATED, UserStatus.PROVISIONING, null, null);
        jdbcTemplate.update(
                "UPDATE identity.user_account SET created_at = now() - interval '15 minutes' WHERE id = ?",
                partialRow.getId());

        // Case 3: Keycloak createUser + setPassword succeeded, Tx2 crashed (KC user HAS password), > 10 min -> promote to ACTIVE
        String readyKcUsername = "reconcile.ready." + sfx;
        UUID readyKcId = keycloakClient.createUser(new KeycloakIdentityClient.CreateUserCommand(
                readyKcUsername, null, "Ready", "Promote", "Valid@1234", true, false, false));
        createdKeycloakUserIds.add(readyKcId);
        createdUserIds.add(readyKcId);
        UserAccount readyRow = insertUser(
                readyKcUsername, "Ready Promote", null, null,
                UserSource.ADMIN_CREATED, UserStatus.PROVISIONING, null, null);
        jdbcTemplate.update(
                "UPDATE identity.user_account SET created_at = now() - interval '15 minutes' WHERE id = ?",
                readyRow.getId());

        // Case 4: Fresh PROVISIONING row (< 10 min) -> left untouched
        String freshUsername = "reconcile.fresh." + sfx;
        UserAccount freshRow = insertUser(
                freshUsername, "Fresh Row", null, null,
                UserSource.ADMIN_CREATED, UserStatus.PROVISIONING, null, null);

        ProvisioningReconcilerJob.ReconcileResult result = provisioningReconcilerJob.reconcile();
        assertThat(result.deleted()).isGreaterThanOrEqualTo(2);
        assertThat(result.promoted()).isGreaterThanOrEqualTo(1);
        assertThat(result.failed()).isZero();

        assertThat(userAccountRepository.findById(noKcRow.getId())).isEmpty();
        assertThat(userAccountRepository.findById(partialRow.getId())).isEmpty();
        assertThat(keycloakClient.findUserIdByUsername(partialKcUsername)).isEmpty();

        UserAccount promoted = userAccountRepository.findById(readyKcId).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userAccountRepository.findById(freshRow.getId())).isPresent();
    }

    private UUID createUserViaApi(
            String username, String password, String fullName, String email, List<UUID> roleIds)
            throws Exception {
        String emailPart = email == null ? "" : ",\"email\":\"" + email + "\"";
        String rolesPart = roleIds == null || roleIds.isEmpty()
                ? ""
                : ",\"roleIds\":" + objectMapper.writeValueAsString(roleIds);
        MvcResult res = mockMvc.perform(post("/api/v1/admin/users")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "fullName": "%s"%s%s
                                }
                                """.formatted(username, password, fullName, emailPart, rolesPart)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdUserIds.add(id);
        createdKeycloakUserIds.add(id);
        return id;
    }

    @TestConfiguration
    static class FakeGuardsConfig {

        @Bean
        FakeGuardsControl fakeGuardsControl() {
            return new FakeGuardsControl();
        }

        @Bean
        AccountDeletionGuard testAccountDeletionGuard(FakeGuardsControl control) {
            return userId -> control.getBlockers();
        }

        @Bean
        ActiveAttemptChecker testActiveAttemptChecker(FakeGuardsControl control) {
            return userId -> control.isActiveAttempt();
        }
    }

    static class FakeGuardsControl {
        private final AtomicReference<List<AccountDeletionGuard.Blocker>> blockers =
                new AtomicReference<>(List.of());
        private final AtomicBoolean activeAttempt = new AtomicBoolean(false);
        private final CopyOnWriteArrayList<UserDeleted> deletedEvents = new CopyOnWriteArrayList<>();

        void setBlockers(List<AccountDeletionGuard.Blocker> next) {
            blockers.set(next == null ? List.of() : List.copyOf(next));
        }

        List<AccountDeletionGuard.Blocker> getBlockers() {
            return blockers.get();
        }

        void setActiveAttempt(boolean next) {
            activeAttempt.set(next);
        }

        boolean isActiveAttempt() {
            return activeAttempt.get();
        }

        @EventListener
        void onUserDeleted(UserDeleted event) {
            deletedEvents.add(event);
        }

        List<UserDeleted> deletedEvents() {
            return List.copyOf(deletedEvents);
        }

        void clearDeletedEvents() {
            deletedEvents.clear();
        }

        void reset() {
            blockers.set(List.of());
            activeAttempt.set(false);
            deletedEvents.clear();
        }
    }

    private UserAccount insertUser(
            String username,
            String fullName,
            String email,
            String phone,
            UserSource source,
            UserStatus status,
            Instant lastLoginAt,
            Instant deletedAt) {
        UUID id = Ids.newId();
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        UserAccount ua = new UserAccount(
                id,
                String.format("U%06d", seq),
                username,
                fullName,
                email,
                phone,
                source,
                status,
                false,
                Instant.now(),
                null);
        userAccountRepository.saveAndFlush(ua);
        if (lastLoginAt != null) {
            jdbcTemplate.update(
                    "UPDATE identity.user_account SET last_login_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(lastLoginAt),
                    id);
        }
        if (deletedAt != null) {
            jdbcTemplate.update(
                    "UPDATE identity.user_account SET deleted_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(deletedAt),
                    id);
        }
        createdUserIds.add(id);
        return userAccountRepository.findById(id).orElseThrow();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
