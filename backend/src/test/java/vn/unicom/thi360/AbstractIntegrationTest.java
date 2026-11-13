package vn.unicom.thi360;

import com.redis.testcontainers.RedisContainer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
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
import vn.unicom.thi360.shared.security.TestSecurity;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"api", "worker", "test"})
@Import(TestSecurity.class)
public abstract class AbstractIntegrationTest {

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
                    .withAdminPassword("admin-test");

    private static volatile boolean keycloakStarted = false;

    static {
        POSTGRES.start();
        REDIS.start();
    }

    protected static synchronized KeycloakContainer ensureKeycloakStarted() {
        if (!keycloakStarted) {
            KEYCLOAK.start();
            keycloakStarted = true;
        }
        return KEYCLOAK;
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("thi360.keycloak.url", () -> keycloakStarted ? KEYCLOAK.getAuthServerUrl() : "http://localhost:8360");
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
