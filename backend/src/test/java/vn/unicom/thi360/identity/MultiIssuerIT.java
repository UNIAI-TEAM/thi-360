package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;

class MultiIssuerIT extends AbstractKeycloakIntegrationTest {

    private static final String ALT_REALM = "thi-360-alt";

    static {
        ensureAltRealmExists();
    }

    private static void ensureAltRealmExists() {
        Keycloak admin = KEYCLOAK.getKeycloakAdminClient();
        boolean exists = admin.realms().findAll().stream()
                .anyMatch(r -> ALT_REALM.equals(r.getRealm()));
        if (exists) {
            return;
        }
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(ALT_REALM);
        realm.setEnabled(true);
        realm.setLoginWithEmailAllowed(true);
        realm.setDuplicateEmailsAllowed(false);
        realm.setPasswordPolicy("length(8) and maxLength(64) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername");
        admin.realms().create(realm);
        var userProfile = admin.realm(ALT_REALM).users().userProfile();
        UPConfig profile = userProfile.getConfiguration();
        for (UPAttribute attr : profile.getAttributes()) {
            if (!"username".equals(attr.getName())) {
                attr.setRequired(null);
            }
        }
        userProfile.update(profile);
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId("thi360-api");
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setPublicClient(false);
        client.setSecret(TEST_APP_CLIENT_SECRET);
        client.setStandardFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(true);
        client.setServiceAccountsEnabled(true);
        admin.realm(ALT_REALM).clients().create(client).close();

        String serviceAccountId = admin.realm(ALT_REALM).clients()
                .findByClientId("thi360-api").getFirst().getId();
        var saUser = admin.realm(ALT_REALM).clients().get(serviceAccountId).getServiceAccountUser();
        ClientRepresentation realmMgmt = admin.realm(ALT_REALM).clients()
                .findByClientId("realm-management").getFirst();
        List<RoleRepresentation> roles = List.of(
                admin.realm(ALT_REALM).clients().get(realmMgmt.getId()).roles().get("manage-users").toRepresentation(),
                admin.realm(ALT_REALM).clients().get(realmMgmt.getId()).roles().get("view-users").toRepresentation(),
                admin.realm(ALT_REALM).clients().get(realmMgmt.getId()).roles().get("query-users").toRepresentation());
        admin.realm(ALT_REALM).users().get(saUser.getId())
                .roles().clientLevel(realmMgmt.getId()).add(roles);
    }

    @DynamicPropertySource
    static void overrideRealmProperty(DynamicPropertyRegistry registry) {
        registry.add("thi360.keycloak.realm", () -> ALT_REALM);
    }

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void platformAdminOnMasterRealm_logsIn_meReturnsMasterManage_andDoesNotTouchUserAccount() throws Exception {
        long userCountBefore = userAccountRepository.count();

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.110")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"platform\",\"password\":\"" + TEST_PLATFORM_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value("/"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        assertThat(userAccountRepository.count()).isEqualTo(userCountBefore);

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        MvcResult meRes = mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("platform"))
                .andExpect(jsonPath("$.realm").value("MASTER"))
                .andExpect(jsonPath("$.access").value("MANAGE"))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andReturn();

        String idStr = com.jayway.jsonpath.JsonPath.read(meRes.getResponse().getContentAsString(), "$.id");
        UUID platformId = UUID.fromString(idStr);
        assertThat(platformAdminRepository.findById(platformId)).isPresent();
        assertThat(userDirectory.findById(platformId))
                .isPresent()
                .get()
                .satisfies(summary -> assertThat(summary.platformAdmin()).isTrue());
    }

    @Test
    void masterUserWithoutPlatformAdminRole_isRejectedWithInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + TEST_MASTER_NO_ROLE_USER
                                + "\",\"password\":\"" + TEST_MASTER_NO_ROLE_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void switchingKeycloakRealmProperty_createsAndAuthenticatesUserInConfiguredRealm() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "altuser." + suffix;
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, null, "Alt", "Realm", "Valid@1234", true, false, true));

        assertThat(KEYCLOAK.getKeycloakAdminClient().realm(ALT_REALM).users().searchByUsername(username, true))
                .hasSize(1);
        assertThat(KEYCLOAK.getKeycloakAdminClient().realm("thi-360").users().searchByUsername(username, true))
                .isEmpty();

        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        userAccountRepository.save(new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Alt Realm",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                userId));

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.112")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + username + "\",\"password\":\"Valid@1234\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.realm").value("APP"))
                .andExpect(jsonPath("$.access").value("EXAM"));
    }
}
