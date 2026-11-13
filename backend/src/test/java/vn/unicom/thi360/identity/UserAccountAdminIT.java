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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
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
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxEventRepository;

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
    }

    @AfterEach
    void cleanup() {
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

        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR", "Simulated KC outage", null))
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
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_ERROR"));

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
