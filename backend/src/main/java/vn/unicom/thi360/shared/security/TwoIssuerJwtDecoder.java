package vn.unicom.thi360.shared.security;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class TwoIssuerJwtDecoder implements JwtDecoder {

    private final KeycloakProperties keycloakProperties;
    private final Clock clock;
    private final String appIssuer;
    private final String platformIssuer;
    private final ConcurrentHashMap<String, JwtDecoder> decoders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JwtDecoder> expiryTolerantDecoders = new ConcurrentHashMap<>();
    public TwoIssuerJwtDecoder(KeycloakProperties keycloakProperties) {
        this(keycloakProperties, Clock.systemUTC());
    }

    @Autowired
    public TwoIssuerJwtDecoder(KeycloakProperties keycloakProperties, Clock clock) {
        this.keycloakProperties = Objects.requireNonNull(keycloakProperties, "keycloakProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        String baseUrl = keycloakProperties.url().endsWith("/")
                ? keycloakProperties.url().substring(0, keycloakProperties.url().length() - 1)
                : keycloakProperties.url();
        this.appIssuer = baseUrl + "/realms/" + keycloakProperties.realm();
        this.platformIssuer = baseUrl + "/realms/" + keycloakProperties.platformRealm();
    }

    public String appIssuer() {
        return appIssuer;
    }

    public String platformIssuer() {
        return platformIssuer;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        return decodeInternal(token, true);
    }

    public Jwt decodeAllowingExpired(String token) throws JwtException {
        return decodeInternal(token, false);
    }

    private Jwt decodeInternal(String token, boolean validateTimestamps) throws JwtException {
        if (token == null || token.isBlank()) {
            throw new BadJwtException("Empty JWT token");
        }

        String issuer = extractUnverifiedIssuer(token);
        String expectedClientId = resolveExpectedClientId(issuer);
        if (expectedClientId == null) {
            throw new BadJwtException("Untrusted JWT issuer");
        }

        ConcurrentHashMap<String, JwtDecoder> targetMap = validateTimestamps ? decoders : expiryTolerantDecoders;
        JwtDecoder delegate = targetMap.computeIfAbsent(
                issuer,
                key -> createDecoder(key, expectedClientId, validateTimestamps));
        Jwt jwt = delegate.decode(token);

        if (platformIssuer.equals(issuer) && !hasPlatformAdminRole(jwt)) {
            throw new BadJwtException("Missing required platform role");
        }
        return jwt;
    }

    private String resolveExpectedClientId(String issuer) {
        if (appIssuer.equals(issuer)) {
            return keycloakProperties.clientId();
        }
        if (platformIssuer.equals(issuer)) {
            return keycloakProperties.platformClientId();
        }
        return null;
    }

    private boolean hasPlatformAdminRole(Jwt jwt) {
        Object realmAccessObj = jwt.getClaim("realm_access");
        if (!(realmAccessObj instanceof Map<?, ?> realmAccess)) {
            return false;
        }
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return false;
        }
        String requiredRole = keycloakProperties.platformRole();
        for (Object role : roles) {
            if (requiredRole.equals(String.valueOf(role))) {
                return true;
            }
        }
        return false;
    }

    private static String extractUnverifiedIssuer(String token) {
        try {
            String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            if (issuer == null || issuer.isBlank()) {
                throw new BadJwtException("Missing JWT issuer");
            }
            return issuer;
        } catch (ParseException ex) {
            throw new BadJwtException("Malformed JWT token", ex);
        }
    }

    private JwtDecoder createDecoder(String issuer, String expectedClientId, boolean validateTimestamps) {
        String jwkSetUri = issuer + "/protocol/openid-connect/certs";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        if (validateTimestamps) {
            JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
            timestampValidator.setClock(clock);
            validators.add(timestampValidator);
        }
        validators.add(new JwtIssuerValidator(issuer));
        validators.add(audienceValidator(expectedClientId));
        validators.add(authorizedPartyValidator(expectedClientId));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> {
            List<String> audiences = jwt.getAudience();
            if (audiences != null && audiences.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "JWT audience does not contain expected client",
                    null));
        };
    }

    private static OAuth2TokenValidator<Jwt> authorizedPartyValidator(String expectedClientId) {
        return jwt -> {
            String azp = jwt.getClaimAsString("azp");
            if (expectedClientId.equals(azp)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "JWT azp does not match expected authorized client",
                    null));
        };
    }
}
