package vn.unicom.thi360.shared.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "thi360.keycloak")
public record KeycloakProperties(
        @NotBlank String url,
        @NotBlank String realm,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String platformRealm,
        @NotBlank String platformRole,
        @NotBlank String platformClientId,
        @NotBlank String platformClientSecret
) {
}
