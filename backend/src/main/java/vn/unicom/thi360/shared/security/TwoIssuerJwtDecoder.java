package vn.unicom.thi360.shared.security;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class TwoIssuerJwtDecoder implements JwtDecoder {

    private final KeycloakProperties keycloakProperties;
    private final String appIssuer;
    private final String platformIssuer;
    private final ConcurrentHashMap<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public TwoIssuerJwtDecoder(KeycloakProperties keycloakProperties) {
        this.keycloakProperties = Objects.requireNonNull(keycloakProperties, "keycloakProperties must not be null");
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
        if (token == null || token.isBlank()) {
            throw new BadJwtException("Empty JWT token");
        }

        String issuer = extractUnverifiedIssuer(token);
        if (!appIssuer.equals(issuer) && !platformIssuer.equals(issuer)) {
            throw new BadJwtException("Untrusted JWT issuer");
        }

        JwtDecoder delegate = decoders.computeIfAbsent(issuer, TwoIssuerJwtDecoder::createDecoder);
        Jwt jwt = delegate.decode(token);

        if (platformIssuer.equals(issuer) && !hasPlatformAdminRole(jwt)) {
            throw new BadJwtException("Missing required platform role");
        }
        return jwt;
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

    private static JwtDecoder createDecoder(String issuer) {
        String jwkSetUri = issuer + "/protocol/openid-connect/certs";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
