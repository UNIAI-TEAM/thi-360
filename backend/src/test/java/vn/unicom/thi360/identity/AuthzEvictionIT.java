package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static vn.unicom.thi360.shared.security.TestSecurity.asPlatformAdmin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
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
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.PermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RolePermissionRepository;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserPermissionRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.PermissionCatalog;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SessionAuthzResolver;

@Import(TestAuthzEndpoints.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthzEvictionIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.92";

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserPermissionRepository userPermissionRepository;

    @Autowired
    private AuthzSnapshotService authzSnapshotService;

    @Autowired
    private PermissionCatalog permissionCatalog;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanupRateLimit() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @Test
    void ac01_userModifyEndpoint_deniesContentAdminWith403AndMissingCodeLog_andAllowsOperations(
            CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .with(asRole("CONTENT_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(output.getOut()).contains("missingPermissions").contains("USER.MODIFY");

        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .with(asRole("OPERATIONS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value("user-modify"));

        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .with(asStudent()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .with(asPlatformAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void multiCodeRequirePermission_requiresAllCodes(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/admin/__test/permission-users")
                        .with(asUser(Permissions.PERMISSION_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(output.getOut()).contains("USER.VIEW");

        mockMvc.perform(get("/api/v1/admin/__test/permission-users")
                        .with(asUser(Permissions.PERMISSION_VIEW, Permissions.USER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value("permission-users"));
    }

    @Test
    void revokingRole_evictsSnapshot_andNextRequestReturns403WithoutRelogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "revoke.role." + suffix;
        UserAccount user = createUser(username, "Revoke Role");

        Role opsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();
        userRoleRepository.save(new UserRole(user.getId(), opsRole.getId(), user.getId(), Instant.now()));

        // Also attach a direct permission to verify spec §5.6 rule 2 when last MANAGE role is removed
        PermissionEntity auditView = permissionRepository.findByCode(Permissions.AUDIT_VIEW).orElseThrow();
        userPermissionRepository.save(new UserPermission(
                user.getId(), auditView.getId(), user.getId(), Instant.now(), "Test direct permission"));

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie at = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie xsrf = loginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);

        mockMvc.perform(get("/api/v1/me").cookie(at))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("MANAGE"))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItems(
                        Permissions.USER_MODIFY, Permissions.AUDIT_VIEW)));

        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .cookie(at, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001"))
                .andExpect(status().isOk());

        // Revoke the only MANAGE role and evict snapshot
        transactionTemplate.executeWithoutResult(status -> {
            userRoleRepository.deleteAllByIdUserId(user.getId());
            authzSnapshotService.evict(user.getId());
        });
        // Same access token must immediately fail with 403 and /me drops to EXAM + empty permissions
        mockMvc.perform(post("/api/v1/admin/__test/user-modify")
                        .cookie(at, xsrf)
                        .header(AuthCookieService.XSRF_HEADER, xsrf.getValue())
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/me").cookie(at))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("EXAM"))
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void rolePermissionChange_evictsAllHoldersSnapshots() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount userA = createUser("holder.a." + suffix, "Holder A");
        UserAccount userB = createUser("holder.b." + suffix, "Holder B");

        Role customRole = new Role(
                Ids.newId(),
                "CUSTOM_" + suffix,
                "Custom Role " + suffix,
                null,
                Access.MANAGE,
                false,
                RoleStatus.ACTIVE,
                Instant.now(),
                userA.getId());
        roleRepository.save(customRole);

        PermissionEntity userView = permissionRepository.findByCode(Permissions.USER_VIEW).orElseThrow();
        rolePermissionRepository.save(new RolePermission(customRole.getId(), userView.getId(), userA.getId(), Instant.now()));
        userRoleRepository.save(new UserRole(userA.getId(), customRole.getId(), userA.getId(), Instant.now()));
        userRoleRepository.save(new UserRole(userB.getId(), customRole.getId(), userA.getId(), Instant.now()));

        SessionAuthzResolver.AuthzSnapshot snapA = authzSnapshotService.load(userA.getId());
        SessionAuthzResolver.AuthzSnapshot snapB = authzSnapshotService.load(userB.getId());
        assertThat(snapA.permissions().hasCode(Permissions.USER_VIEW)).isTrue();
        assertThat(snapB.permissions().hasCode(Permissions.USER_VIEW)).isTrue();
        assertThat(redisTemplate.hasKey(authzSnapshotService.cacheKey(userA.getId()))).isTrue();
        assertThat(redisTemplate.hasKey(authzSnapshotService.cacheKey(userB.getId()))).isTrue();

        mockMvc.perform(put("/api/v1/admin/roles/{id}/permissions", customRole.getId())
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"CLASS.VIEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCodes[0]").value("CLASS.VIEW"));

        assertThat(redisTemplate.hasKey(authzSnapshotService.cacheKey(userA.getId()))).isFalse();
        assertThat(redisTemplate.hasKey(authzSnapshotService.cacheKey(userB.getId()))).isFalse();

        SessionAuthzResolver.AuthzSnapshot reloadedA = authzSnapshotService.load(userA.getId());
        SessionAuthzResolver.AuthzSnapshot reloadedB = authzSnapshotService.load(userB.getId());
        assertThat(reloadedA.permissions().hasCode(Permissions.USER_VIEW)).isFalse();
        assertThat(reloadedB.permissions().hasCode(Permissions.USER_VIEW)).isFalse();
        assertThat(reloadedA.permissions().hasCode(Permissions.CLASS_VIEW)).isTrue();
        assertThat(reloadedB.permissions().hasCode(Permissions.CLASS_VIEW)).isTrue();
        assertThat(reloadedA.version()).isGreaterThan(snapA.version());
        assertThat(reloadedB.version()).isGreaterThan(snapB.version());
    }

    @Test
    void evict_whenRedisFails_enqueuesOutboxEventAfterCommit_andRetriesUntilDeleted() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount user = createUser("outbox.evict." + suffix, "Outbox Evict");
        Role opsRole = roleRepository.findByCodeNormalized("operations").orElseThrow();

        authzSnapshotService.build(user.getId());
        String key = authzSnapshotService.cacheKey(user.getId());
        assertThat(redisTemplate.hasKey(key)).isTrue();

        // 1. Rolled-back transaction must NOT evict Redis or enqueue outbox fallback
        transactionTemplate.executeWithoutResult(status -> {
            authzSnapshotService.evict(user.getId());
            assertThat(redisTemplate.hasKey(key)).isTrue();
            status.setRollbackOnly();
        });
        assertThat(redisTemplate.hasKey(key)).isTrue();
        Integer beforeFailureCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shared.outbox_event WHERE topic = ? AND payload::text LIKE ?",
                Integer.class,
                AuthzSnapshotService.OUTBOX_TOPIC_EVICT,
                "%" + user.getId() + "%");
        assertThat(beforeFailureCount).isZero();

        // 2. Simulate Redis delete outage during a committed production HTTP mutation (afterCommit -> REQUIRES_NEW outbox)
        try {
            doThrow(new DataAccessResourceFailureException("Simulated Redis outage"))
                    .when(redisTemplate)
                    .delete(argThat((Collection<String> keys) -> keys != null && keys.contains(key)));

            mockMvc.perform(put("/api/v1/admin/users/{id}/roles", user.getId())
                            .with(asPlatformAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleIds\": [\"%s\"]}".formatted(opsRole.getId())))
                    .andExpect(status().isOk());

            Integer pendingAfterCommit = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM shared.outbox_event WHERE topic = ? AND payload::text LIKE ? AND processed_at IS NULL AND attempts = 0",
                    Integer.class,
                    AuthzSnapshotService.OUTBOX_TOPIC_EVICT,
                    "%" + user.getId() + "%");
            assertThat(pendingAfterCommit).isEqualTo(1);
            assertThat(redisTemplate.hasKey(key)).isTrue();

            // 3. Relay retry while Redis is still failing -> increments attempts, stays unprocessed
            outboxRelay.relayBatch();
            Integer retriedPending = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM shared.outbox_event WHERE topic = ? AND payload::text LIKE ? AND processed_at IS NULL AND attempts = 1",
                    Integer.class,
                    AuthzSnapshotService.OUTBOX_TOPIC_EVICT,
                    "%" + user.getId() + "%");
            assertThat(retriedPending).isEqualTo(1);
            assertThat(redisTemplate.hasKey(key)).isTrue();
        } finally {
            reset(redisTemplate);
        }

        // 4. Redis recovers -> next relay run succeeds, marks processed_at, and deletes snapshot key
        jdbcTemplate.update(
                "UPDATE shared.outbox_event SET next_attempt_at = now() WHERE topic = ? AND payload::text LIKE ? AND processed_at IS NULL",
                AuthzSnapshotService.OUTBOX_TOPIC_EVICT,
                "%" + user.getId() + "%");
        int processed = outboxRelay.relayBatch();
        assertThat(processed).isGreaterThanOrEqualTo(1);
        Integer remainingPending = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shared.outbox_event WHERE topic = ? AND payload::text LIKE ? AND processed_at IS NULL",
                Integer.class,
                AuthzSnapshotService.OUTBOX_TOPIC_EVICT,
                "%" + user.getId() + "%");
        assertThat(remainingPending).isZero();
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    private UserAccount createUser(String username, String fullName) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, fullName, "Test", "Valid@1234", true, false, true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        return userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                fullName,
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));
    }
}
