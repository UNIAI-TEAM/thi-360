package vn.unicom.thi360.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.Role;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserIdentity;
import vn.unicom.thi360.identity.domain.UserRole;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class ClassroomAccountAccessIT extends AbstractKeycloakIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + "203.0.113.88");
        for (UUID userId : createdUserIds) {
            try {
                keycloakClient.deleteUser(userId);
            } catch (Exception ignored) {
                // ignore if already removed
            }
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(List.of(
                    "authz:" + userId,
                    "user-sess:" + userId,
                    "user-sess-seq:" + userId,
                    "user-rev-gen:" + userId));
        }
        createdUserIds.clear();
    }

    @Test
    void ac01_4_accountWithoutClass_logsInAndHasRolePermissions() throws Exception {
        String sfx = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String opsUsername = "noclass.ops." + sfx;
        UUID opsUserId = provisionUserWithRole(opsUsername, "No Class Ops", "Valid@1234", "operations");

        // Confirm zero class memberships
        Integer opsClasses = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member WHERE user_id = ?",
                Integer.class,
                opsUserId);
        assertThat(opsClasses).isZero();

        MvcResult opsLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + opsUsername + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie opsAccessCookie = opsLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(opsAccessCookie).isNotNull();

        mockMvc.perform(get("/api/v1/me").cookie(opsAccessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("MANAGE"))
                .andExpect(jsonPath("$.permissions", hasItem(Permissions.CLASS_VIEW)))
                .andExpect(jsonPath("$.permissions", hasItem(Permissions.CLASS_MODIFY)))
                .andExpect(jsonPath("$.permissions", hasItem(Permissions.USER_VIEW)));

        mockMvc.perform(get("/api/v1/admin/classes").cookie(opsAccessCookie))
                .andExpect(status().isOk());

        // Student without class also logs in and receives EXAM access
        String studentUsername = "noclass.sv." + sfx;
        UUID studentUserId = provisionUserWithRole(studentUsername, "No Class Student", "Valid@1234", "student");
        Integer studentClasses = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member WHERE user_id = ?",
                Integer.class,
                studentUserId);
        assertThat(studentClasses).isZero();

        MvcResult studentLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + studentUsername + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie studentAccessCookie = studentLogin.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(studentAccessCookie).isNotNull();

        mockMvc.perform(get("/api/v1/me").cookie(studentAccessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("EXAM"))
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    private UUID provisionUserWithRole(
            String username, String fullName, String password, String roleCodeNormalized) {
        UUID keycloakId = keycloakClient.createUser(new CreateUserCommand(
                username, null, fullName, null, null, true, false, false));
        keycloakClient.setPassword(keycloakId, password, false);
        createdUserIds.add(keycloakId);

        Instant now = Instant.now();
        String code = "U" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        UserAccount account = new UserAccount(
                keycloakId,
                code,
                username,
                fullName,
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                now,
                null);
        userAccountRepository.saveAndFlush(account);
        userIdentityRepository.saveAndFlush(new UserIdentity(
                Ids.newId(),
                keycloakId,
                IdentityProvider.LOCAL,
                keycloakId.toString(),
                null,
                now));

        Role role = roleRepository.findByCodeNormalized(roleCodeNormalized).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(keycloakId, role.getId(), null, now));
        return keycloakId;
    }
}
