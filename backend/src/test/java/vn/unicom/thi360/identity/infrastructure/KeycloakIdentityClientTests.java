package vn.unicom.thi360.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import vn.unicom.thi360.shared.security.KeycloakProperties;

class KeycloakIdentityClientTests {

    @Test
    void matchesMixedCaseKeycloakEmailAgainstNormalizedLocalEmail() {
        UUID userId = UUID.fromString("7da2a220-615f-4ce8-8ac5-e6fd0cd8cd85");
        UserRepresentation representation = new UserRepresentation();
        representation.setUsername("registered-user");
        representation.setEmail("Person.Example@Example.COM");

        UserResource userResource = mock(UserResource.class);
        when(userResource.toRepresentation()).thenReturn(representation);
        UsersResource usersResource = mock(UsersResource.class);
        when(usersResource.get(userId.toString())).thenReturn(userResource);
        RealmResource realmResource = mock(RealmResource.class);
        when(realmResource.users()).thenReturn(usersResource);
        Keycloak keycloak = mock(Keycloak.class);
        when(keycloak.realm("users")).thenReturn(realmResource);

        KeycloakProperties properties = new KeycloakProperties(
                "http://localhost", "users", "client", "secret", "platform", "role", "platform-client", "platform-secret");
        KeycloakIdentityClient client = new KeycloakIdentityClient(properties, new ObjectMapper(), RestClient.builder());
        ReflectionTestUtils.setField(client, "adminClient", keycloak);

        assertThat(client.matchesRegistrationIdentity(userId, "registered-user", "person.example@example.com"))
                .isTrue();
    }
}
