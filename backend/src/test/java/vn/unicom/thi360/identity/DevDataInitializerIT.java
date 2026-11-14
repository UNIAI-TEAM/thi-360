package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditQuery;
import vn.unicom.thi360.identity.application.DevDataInitializer;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.domain.UserRoleId;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.outbox.OutboxEventRepository;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.AuthCookieService;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles({"api", "worker", "test", "dev"})
class DevDataInitializerIT extends AbstractKeycloakIntegrationTest {

    private static final Map<String, String> EXPECTED_ROLES = Map.of(
            "sys.admin", "SYSTEM_ADMIN",
            "content.admin", "CONTENT_ADMIN",
            "ops.admin", "OPERATIONS",
            "student01", "STUDENT",
            "student02", "STUDENT",
            "student03", "STUDENT",
            "student04", "STUDENT",
            "student05", "STUDENT");

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ObjectMapper objectMapper;


    @Autowired
    private DevDataInitializer devDataInitializer;

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private AuditQuery auditQuery;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void devProfile_seedsAllB13AccountsIdempotently_andAllowsLoginWithoutPasswordChange() throws Exception {
        assertThat(applicationContext.getBeansOfType(DevDataInitializer.class)).hasSize(1);

        assertSeededState();
        long accountCountBefore = userAccountRepository.count();
        long identityCountBefore = userIdentityRepository.count();
        long userRoleCountBefore = userRoleRepository.count();
        long outboxCountBeforeSecondSeed = outboxEventRepository.count();
        Long classCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_room WHERE deleted_at IS NULL", Long.class);
        Long classMemberCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member", Long.class);
        Long classHistoryCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history", Long.class);

        // Second run must be completely idempotent
        devDataInitializer.seed();

        assertThat(userAccountRepository.count()).isEqualTo(accountCountBefore);
        assertThat(userIdentityRepository.count()).isEqualTo(identityCountBefore);
        assertThat(userRoleRepository.count()).isEqualTo(userRoleCountBefore);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxCountBeforeSecondSeed);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_room WHERE deleted_at IS NULL", Long.class))
                .isEqualTo(classCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member", Long.class))
                .isEqualTo(classMemberCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member_history", Long.class))
                .isEqualTo(classHistoryCountBefore);
        assertSeededState();

        int relayed = 0;
        int batch;
        while ((batch = outboxRelay.relayBatch()) > 0) {
            relayed += batch;
        }
        assertThat(relayed).isGreaterThanOrEqualTo(EXPECTED_ROLES.size() * 2);
        assertSeededAuditEvents();
        assertSeededClassAuditEvents();

        // Restoring a missing role assignment emits only USER_ROLES_CHANGED (no duplicate USER_CREATED)
        UserAccount student05 = userAccountRepository
                .findActiveByUsernameNormalized(Normalizer.normalize("student05"))
                .orElseThrow();
        UUID studentRoleId = roleRepository.findByCodeNormalized("student").orElseThrow().getId();
        userRoleRepository.deleteById(new UserRoleId(student05.getId(), studentRoleId));

        long outboxBeforeRoleRestore = outboxEventRepository.count();
        devDataInitializer.seed();
        assertThat(outboxEventRepository.count()).isEqualTo(outboxBeforeRoleRestore + 1);
        assertThat(outboxRelay.relayBatch()).isEqualTo(1);

        List<AuditEventView> student05Events = auditQuery
                .findByTarget("USER", student05.getId().toString(), new PageQuery(0, 20, Sort.unsorted()))
                .items();
        assertThat(student05Events.stream().filter(e -> "USER_CREATED".equals(e.action()))).hasSize(1);
        assertThat(student05Events.stream().filter(e -> "USER_ROLES_CHANGED".equals(e.action()))).hasSize(2);

        MvcResult opsLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.150")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"ops.admin\",\"password\":\"Thi360@dev\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.redirectTo").value("http://localhost:3001"))
                .andReturn();

        Cookie opsAccessCookie = opsLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(opsAccessCookie).isNotNull();

        MvcResult usersResult = mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "100")
                        .cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode usersBody = objectMapper.readTree(usersResult.getResponse().getContentAsString());
        Map<String, JsonNode> listedByUsername = new HashMap<>();
        for (JsonNode item : usersBody.path("items")) {
            listedByUsername.put(item.path("username").asText(), item);
        }
        for (String seededUsername : EXPECTED_ROLES.keySet()) {
            JsonNode listed = listedByUsername.get(seededUsername);
            assertThat(listed)
                    .as("Seeded user %s must appear in GET /api/v1/admin/users", seededUsername)
                    .isNotNull();
            assertThat(listed.path("status").asText()).isEqualTo("ACTIVE");
            assertThat(listed.path("source").asText()).isEqualTo("ADMIN_CREATED");
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.151")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"student01\",\"password\":\"Thi360@dev\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.redirectTo").value("http://localhost:3000/home"));
        assertSeededClassesViaApi(opsAccessCookie);
    }

    private void assertSeededState() {
        for (Map.Entry<String, String> entry : EXPECTED_ROLES.entrySet()) {
            String username = entry.getKey();
            String roleCode = entry.getValue();

            UUID kcId = keycloakClient.findUserIdByUsername(username)
                    .orElseThrow(() -> new AssertionError("Missing Keycloak user: " + username));
            UserAccount account = userAccountRepository.findActiveByUsernameNormalized(Normalizer.normalize(username))
                    .orElseThrow(() -> new AssertionError("Missing UserAccount: " + username));
            assertThat(account.getId()).isEqualTo(kcId);
            assertThat(account.isMustChangePassword()).isFalse();

            assertThat(userIdentityRepository
                    .findByProviderAndProviderSubject(IdentityProvider.LOCAL, kcId.toString()))
                    .isPresent();

            UUID expectedRoleId = roleRepository.findByCodeNormalized(Normalizer.normalize(roleCode))
                    .orElseThrow()
                    .getId();
            List<UserRole> roles = userRoleRepository.findAllByIdUserId(kcId);
            assertThat(roles).extracting(r -> r.getId().getRoleId()).contains(expectedRoleId);
        }
    }

    private void assertSeededAuditEvents() {
        for (Map.Entry<String, String> entry : EXPECTED_ROLES.entrySet()) {
            String username = entry.getKey();
            String roleCode = entry.getValue();
            UUID userId = userAccountRepository.findActiveByUsernameNormalized(Normalizer.normalize(username))
                    .orElseThrow()
                    .getId();

            List<AuditEventView> events = auditQuery
                    .findByTarget("USER", userId.toString(), new PageQuery(0, 20, Sort.unsorted()))
                    .items();

            assertThat(events)
                    .anySatisfy(ev -> {
                        assertThat(ev.module()).isEqualTo("identity");
                        assertThat(ev.action()).isEqualTo("USER_CREATED");
                        assertThat(ev.actorType()).isEqualTo(AuditActorType.SYSTEM);
                        assertThat(ev.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                        assertThat(ev.targetLabel()).isEqualTo(username);
                        assertThat(ev.after()).containsEntry("username", username);
                    })
                    .anySatisfy(ev -> {
                        assertThat(ev.module()).isEqualTo("identity");
                        assertThat(ev.action()).isEqualTo("USER_ROLES_CHANGED");
                        assertThat(ev.actorType()).isEqualTo(AuditActorType.SYSTEM);
                        assertThat(ev.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                        assertThat(ev.targetLabel()).isEqualTo(username);
                        assertThat(ev.after()).containsKey("roleCodes");
                        @SuppressWarnings("unchecked")
                        List<String> afterRoles = (List<String>) ev.after().get("roleCodes");
                        assertThat(afterRoles).contains(roleCode);
                    });
        }
    }

    private void assertSeededClassesViaApi(Cookie opsAccessCookie) throws Exception {
        MvcResult classesResult = mockMvc.perform(get("/api/v1/admin/classes")
                        .param("size", "100")
                        .cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode classesBody = objectMapper.readTree(classesResult.getResponse().getContentAsString());
        Map<String, JsonNode> byCode = new HashMap<>();
        for (JsonNode item : classesBody.path("items")) {
            byCode.put(item.path("code").asText(), item);
        }

        assertThat(byCode).containsKeys("LOP-A", "LOP-B");
        JsonNode lopA = byCode.get("LOP-A");
        JsonNode lopB = byCode.get("LOP-B");
        assertThat(lopA.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(lopA.path("memberCount").asLong()).isEqualTo(2L);
        assertThat(lopA.path("ownerUsername").asText()).isEqualTo("ops.admin");
        assertThat(lopB.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(lopB.path("memberCount").asLong()).isEqualTo(2L);
        assertThat(lopB.path("ownerUsername").asText()).isEqualTo("ops.admin");

        assertClassMembers(opsAccessCookie, lopA.path("id").asText(), List.of("student01", "student02"));
        assertClassMembers(opsAccessCookie, lopB.path("id").asText(), List.of("student03", "student04"));

        UUID student01Id = userAccountRepository.findActiveByUsernameNormalized("student01").orElseThrow().getId();
        mockMvc.perform(get("/api/v1/admin/users/{id}/classes", student01Id)
                        .cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentClasses[0].classCode").value("LOP-A"))
                .andExpect(jsonPath("$.history[0].classCode").value("LOP-A"))
                .andExpect(jsonPath("$.history[0].action").value("JOIN"))
                .andExpect(jsonPath("$.history[0].reason").value("dev_seed"));
    }

    private void assertClassMembers(Cookie opsAccessCookie, String classId, List<String> expectedUsernames)
            throws Exception {
        MvcResult membersResult = mockMvc.perform(get("/api/v1/admin/classes/{id}/members", classId)
                        .param("size", "100")
                        .cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode membersBody = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        List<String> usernames = new ArrayList<>();
        for (JsonNode item : membersBody.path("items")) {
            usernames.add(item.path("username").asText());
        }
        assertThat(usernames).containsExactlyInAnyOrderElementsOf(expectedUsernames);
    }

    private void assertSeededClassAuditEvents() {
        for (String code : List.of("LOP-A", "LOP-B")) {
            String classId = jdbcTemplate.queryForObject(
                    "SELECT id::text FROM classroom.class_room WHERE code = ? AND deleted_at IS NULL",
                    String.class,
                    code);
            assertThat(classId).isNotBlank();
            List<AuditEventView> events = auditQuery
                    .findByTarget("CLASS", classId, new PageQuery(0, 20, Sort.unsorted()))
                    .items();
            assertThat(events)
                    .anySatisfy(ev -> {
                        assertThat(ev.module()).isEqualTo("CLASSROOM");
                        assertThat(ev.action()).isEqualTo("CLASS_CREATED");
                        assertThat(ev.actorType()).isEqualTo(AuditActorType.SYSTEM);
                        assertThat(ev.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                        assertThat(ev.reason()).isEqualTo("dev_seed");
                    })
                    .anySatisfy(ev -> {
                        assertThat(ev.module()).isEqualTo("CLASSROOM");
                        assertThat(ev.action()).isEqualTo("CLASS_MEMBER_ADDED");
                        assertThat(ev.actorType()).isEqualTo(AuditActorType.SYSTEM);
                        assertThat(ev.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                        assertThat(ev.reason()).isEqualTo("dev_seed");
                    });
        }
    }
}
