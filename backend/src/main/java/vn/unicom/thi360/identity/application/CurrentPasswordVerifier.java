package vn.unicom.thi360.identity.application;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.AuthFailureReason;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.KeycloakAuthException;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.TokenResult;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.RateLimitedException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.KeycloakProperties;
import vn.unicom.thi360.shared.web.AppMessages;

@Component
public class CurrentPasswordVerifier {

    private final KeycloakIdentityClient keycloakClient;
    private final KeycloakProperties keycloakProperties;
    private final AppMessages messages;

    public CurrentPasswordVerifier(
            KeycloakIdentityClient keycloakClient,
            KeycloakProperties keycloakProperties,
            AppMessages messages) {
        this.keycloakClient = Objects.requireNonNull(keycloakClient);
        this.keycloakProperties = Objects.requireNonNull(keycloakProperties);
        this.messages = Objects.requireNonNull(messages);
    }

    public void verify(String username, String currentPassword) {
        TokenResult verified;
        try {
            verified = keycloakClient.passwordGrant(keycloakProperties.realm(), username, currentPassword);
        } catch (KeycloakAuthException ex) {
            if (ex.getReason() == AuthFailureReason.ACCOUNT_LOCKED) {
                throw new RateLimitedException("LOGIN_THROTTLED");
            }
            throw new ValidationException(
                    "WRONG_OLD_PASSWORD",
                    null,
                    List.of(new FieldError(
                            "currentPassword", "WRONG_OLD_PASSWORD", messages.get("WRONG_OLD_PASSWORD"))));
        }
        keycloakClient.logout(keycloakProperties.realm(), verified.refreshToken());
    }
}
