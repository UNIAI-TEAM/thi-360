package vn.unicom.thi360;

import com.redis.testcontainers.RedisContainer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import vn.unicom.thi360.shared.security.TestSecurity;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"api", "worker", "test"})
@Import(TestSecurity.class)
public abstract class AbstractIntegrationTest {

    protected static final String TEST_APP_CLIENT_SECRET = "thi360-api-dev-only";
    protected static final String TEST_PLATFORM_CLIENT_SECRET = "thi360-platform-dev-only";
    protected static final String TEST_PLATFORM_PASSWORD = "platform-dev-only";
    protected static final String TEST_MASTER_NO_ROLE_USER = "master-no-role";
    protected static final String TEST_MASTER_NO_ROLE_PASSWORD = "MasterNoRole@123";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("thi360")
                    .withUsername("thi360")
                    .withPassword("thi360-test");

    protected static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    protected static final KeycloakContainer KEYCLOAK =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
                    .withAdminUsername("admin")
                    .withAdminPassword("admin-test")
                    .withEnv("THI360_KC_CLIENT_SECRET", TEST_APP_CLIENT_SECRET)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(resolveRealmImportPath(), 0644),
                            "/opt/keycloak/data/import/thi-360-realm.json");

    private static volatile boolean keycloakStarted = false;

    static {
        POSTGRES.start();
        REDIS.start();
    }

    private static Path resolveRealmImportPath() {
        Path fromBackend = Path.of("..", "infra", "keycloak", "thi-360-realm.json").normalize();
        if (Files.exists(fromBackend)) {
            return fromBackend.toAbsolutePath();
        }
        Path fromRoot = Path.of("infra", "keycloak", "thi-360-realm.json").normalize();
        if (Files.exists(fromRoot)) {
            return fromRoot.toAbsolutePath();
        }
        throw new IllegalStateException("Cannot locate infra/keycloak/thi-360-realm.json");
    }

    protected static synchronized KeycloakContainer ensureKeycloakStarted() {
        if (!keycloakStarted) {
            KEYCLOAK.start();
            bootstrapMasterRealm(KEYCLOAK.getKeycloakAdminClient());
            keycloakStarted = true;
        }
        return KEYCLOAK;
    }

    private static void bootstrapMasterRealm(Keycloak admin) {
        var master = admin.realm("master");

        boolean hasPlatformRole = master.roles().list().stream()
                .anyMatch(r -> "PLATFORM_ADMIN".equals(r.getName()));
        if (!hasPlatformRole) {
            RoleRepresentation role = new RoleRepresentation();
            role.setName("PLATFORM_ADMIN");
            role.setDescription("Thi 360 platform administrator");
            master.roles().create(role);
        }

        List<ClientRepresentation> clients = master.clients().findByClientId("thi360-platform");
        if (clients.isEmpty()) {
            ClientRepresentation client = new ClientRepresentation();
            client.setClientId("thi360-platform");
            client.setName("Thi 360 platform auth");
            client.setEnabled(true);
            client.setProtocol("openid-connect");
            client.setPublicClient(false);
            client.setSecret(TEST_PLATFORM_CLIENT_SECRET);
            client.setStandardFlowEnabled(false);
            client.setDirectAccessGrantsEnabled(true);
            client.setServiceAccountsEnabled(false);
            client.setAttributes(Map.of("access.token.lifespan", "300"));
            master.clients().create(client).close();
        }

        ensureMasterUser(master, "platform", TEST_PLATFORM_PASSWORD, true);
        ensureMasterUser(master, TEST_MASTER_NO_ROLE_USER, TEST_MASTER_NO_ROLE_PASSWORD, false);
    }

    private static void ensureMasterUser(
            org.keycloak.admin.client.resource.RealmResource master,
            String username,
            String password,
            boolean assignPlatformAdmin) {
        List<UserRepresentation> existing = master.users().searchByUsername(username, true);
        String userId;
        if (existing.isEmpty()) {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setFirstName("Master");
            user.setLastName(username);
            try (var response = master.users().create(user)) {
                String path = response.getLocation().getPath();
                userId = path.substring(path.lastIndexOf('/') + 1);
            }
        } else {
            userId = existing.getFirst().getId();
        }

        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setTemporary(false);
        cred.setValue(password);
        master.users().get(userId).resetPassword(cred);

        if (assignPlatformAdmin) {
            RoleRepresentation role = master.roles().get("PLATFORM_ADMIN").toRepresentation();
            master.users().get(userId).roles().realmLevel().add(List.of(role));
        }
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestSecurity.TestSecuritySupport testSecuritySupport;

    protected RequestPostProcessor asUser(String... permissions) {
        return TestSecurity.asUser(testSecuritySupport, permissions);
    }

    protected RequestPostProcessor asRole(String code) {
        return TestSecurity.asRole(testSecuritySupport, code);
    }

    protected RequestPostProcessor asPlatformAdmin() {
        return TestSecurity.asPlatformAdmin(testSecuritySupport);
    }

    protected RequestPostProcessor asStudent() {
        return TestSecurity.asStudent(testSecuritySupport);
    }
}
