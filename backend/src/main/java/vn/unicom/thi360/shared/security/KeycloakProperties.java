package vn.unicom.thi360.shared.security;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
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
        @NotBlank String platformClientSecret,
        @NotBlank @DefaultValue("thi360-mobile") String mobileClientId,
        @NotBlank @DefaultValue("thi360-mobile-dev-only") String mobileClientSecret,
        @DefaultValue("PT168H") Duration mobileOfflineIdleTimeout
) {
    public static final String DEFAULT_MOBILE_CLIENT_ID = "thi360-mobile";
    public static final String DEFAULT_MOBILE_CLIENT_SECRET = "thi360-mobile-dev-only";
    public static final Duration DEFAULT_MOBILE_OFFLINE_IDLE_TIMEOUT = Duration.ofDays(7);

    @ConstructorBinding
    public KeycloakProperties {
        if (mobileOfflineIdleTimeout == null) {
            mobileOfflineIdleTimeout = DEFAULT_MOBILE_OFFLINE_IDLE_TIMEOUT;
        }
        if (mobileOfflineIdleTimeout.isZero() || mobileOfflineIdleTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "thi360.keycloak.mobile-offline-idle-timeout must be a positive duration");
        }
    }

    public KeycloakProperties(
            String url,
            String realm,
            String clientId,
            String clientSecret,
            String platformRealm,
            String platformRole,
            String platformClientId,
            String platformClientSecret) {
        this(
                url,
                realm,
                clientId,
                clientSecret,
                platformRealm,
                platformRole,
                platformClientId,
                platformClientSecret,
                DEFAULT_MOBILE_CLIENT_ID,
                DEFAULT_MOBILE_CLIENT_SECRET,
                DEFAULT_MOBILE_OFFLINE_IDLE_TIMEOUT);
    }
}
