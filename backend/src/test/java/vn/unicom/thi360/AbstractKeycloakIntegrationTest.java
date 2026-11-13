package vn.unicom.thi360;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractKeycloakIntegrationTest extends AbstractIntegrationTest {

    static {
        ensureKeycloakStarted();
    }

    @DynamicPropertySource
    static void registerKeycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("thi360.keycloak.url", KEYCLOAK::getAuthServerUrl);
    }
}
