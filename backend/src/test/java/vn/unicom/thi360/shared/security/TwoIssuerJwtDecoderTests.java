package vn.unicom.thi360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class TwoIssuerJwtDecoderTests {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-13T12:00:00Z");

    private static HttpServer server;
    private static RSAKey signingKey;
    private static RSAKey wrongKey;
    private static TwoIssuerJwtDecoder decoder;
    private static String appIssuer;
    private static String platformIssuer;

    @BeforeAll
    static void startJwkServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
        wrongKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
        byte[] jwksBytes = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/app-realm/protocol/openid-connect/certs", exchange -> writeJson(exchange, jwksBytes));
        server.createContext("/realms/master-realm/protocol/openid-connect/certs", exchange -> writeJson(exchange, jwksBytes));
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        KeycloakProperties props = new KeycloakProperties(
                baseUrl,
                "app-realm",
                "app-client",
                "app-secret",
                "master-realm",
                "PLATFORM_ADMIN",
                "platform-client",
                "platform-secret");
        decoder = new TwoIssuerJwtDecoder(props, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        appIssuer = decoder.appIssuer();
        platformIssuer = decoder.platformIssuer();
    }

    @AfterAll
    static void stopJwkServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void validTokensPassDecode_andExpiredTokensPassOnlyDecodeAllowingExpired() throws Exception {
        String validApp = sign(signingKey, claims(appIssuer, "app-client", "app-client", FIXED_NOW.plusSeconds(60), null));
        Jwt decodedApp = decoder.decode(validApp);
        assertThat(decodedApp.getIssuer().toString()).isEqualTo(appIssuer);

        String expiredApp = sign(signingKey, claims(appIssuer, "app-client", "app-client", FIXED_NOW.minusSeconds(120), null));
        assertThatThrownBy(() -> decoder.decode(expiredApp)).isInstanceOf(JwtException.class);
        Jwt decodedExpiredApp = decoder.decodeAllowingExpired(expiredApp);
        assertThat(decodedExpiredApp.getClaimAsString("sid")).isEqualTo("sid-123");

        String expiredMaster = sign(signingKey, claims(
                platformIssuer,
                "platform-client",
                "platform-client",
                FIXED_NOW.minusSeconds(120),
                Map.of("roles", List.of("PLATFORM_ADMIN"))));
        assertThatThrownBy(() -> decoder.decode(expiredMaster)).isInstanceOf(JwtException.class);
        assertThat(decoder.decodeAllowingExpired(expiredMaster).getSubject()).isNotBlank();
    }

    @Test
    void unsignedBadSignatureWrongAudienceWrongAzpUnknownIssuerAndMissingRole_areRejectedByBothDecoders() throws Exception {
        JWTClaimsSet appClaims = claims(appIssuer, "app-client", "app-client", FIXED_NOW.plusSeconds(60), null);
        String unsigned = new PlainJWT(appClaims).serialize();
        String badSignature = sign(wrongKey, appClaims);
        String wrongAudience = sign(signingKey, claims(appIssuer, "other-client", "app-client", FIXED_NOW.plusSeconds(60), null));
        String wrongAzp = sign(signingKey, claims(appIssuer, "app-client", "other-client", FIXED_NOW.plusSeconds(60), null));
        String unknownIssuer = sign(signingKey, claims("http://127.0.0.1/realms/evil", "app-client", "app-client", FIXED_NOW.plusSeconds(60), null));
        String missingRole = sign(signingKey, claims(
                platformIssuer,
                "platform-client",
                "platform-client",
                FIXED_NOW.plusSeconds(60),
                Map.of("roles", List.of("OTHER_ROLE"))));

        for (String token : List.of(unsigned, badSignature, wrongAudience, wrongAzp, unknownIssuer, missingRole)) {
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder.decodeAllowingExpired(token)).isInstanceOf(JwtException.class);
        }
    }

    private static JWTClaimsSet claims(
            String issuer,
            String audience,
            String azp,
            Instant expiresAt,
            Map<String, Object> realmAccess) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .audience(audience)
                .claim("azp", azp)
                .claim("sid", "sid-123")
                .issueTime(Date.from(FIXED_NOW.minusSeconds(300)))
                .expirationTime(Date.from(expiresAt));
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        }
        return builder.build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
