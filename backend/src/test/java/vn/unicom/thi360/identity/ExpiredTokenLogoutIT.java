package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.CtxCookieSigner;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.KeycloakProperties;
import vn.unicom.thi360.shared.security.LoginThrottle;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.SessionStore;
import vn.unicom.thi360.shared.security.TwoIssuerJwtDecoder;

class ExpiredTokenLogoutIT extends AbstractIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-13T12:00:00Z");

    private static HttpServer server;
    private static RSAKey signingKey;
    private static RSAKey rogueKey;
    private static KeycloakProperties props;
    private static TwoIssuerJwtDecoder decoder;

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PlatformAdminRepository platformAdminRepository;
    @Autowired
    private SessionStore sessionStore;
    @Autowired
    private LoginThrottle loginThrottle;
    @Autowired
    private CtxCookieSigner ctxCookieSigner;
    @Autowired
    private AuthCookieService authCookieService;
    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private PasswordPolicy passwordPolicy;
    @Autowired
    private AuditRecorder auditRecorder;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startJwkServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("expired-logout-kid").generate();
        rogueKey = new RSAKeyGenerator(2048).keyID("rogue-kid").generate();
        byte[] jwksBytes = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/app-realm/protocol/openid-connect/certs", ex -> writeJson(ex, jwksBytes));
        server.createContext("/realms/master-realm/protocol/openid-connect/certs", ex -> writeJson(ex, jwksBytes));
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        props = new KeycloakProperties(
                baseUrl,
                "app-realm",
                "app-client",
                "app-secret",
                "master-realm",
                "PLATFORM_ADMIN",
                "platform-client",
                "platform-secret");
        decoder = new TwoIssuerJwtDecoder(props, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @AfterAll
    static void stopJwkServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void expiredSignedAccessTokenLogout_revokesOwnedSessionAndClearsCookies_whileRogueTokenLeavesSessionIntact()
            throws Exception {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        AuthService authService = new AuthService(
                userAccountRepository,
                platformAdminRepository,
                new KeycloakIdentityClient(props, objectMapper, RestClient.builder()),
                props,
                decoder,
                sessionStore,
                loginThrottle,
                ctxCookieSigner,
                authCookieService,
                currentUserProvider,
                passwordPolicy,
                auditRecorder,
                transactionTemplate,
                clock,
                null);

        UUID userId = UUID.randomUUID();
        String sid = "expired-sid-" + UUID.randomUUID();
        sessionStore.createSession(sid, userId, Realm.APP);

        try {
            JWTClaimsSet expiredClaims = new JWTClaimsSet.Builder()
                    .issuer(decoder.appIssuer())
                    .subject(userId.toString())
                    .audience("app-client")
                    .claim("azp", "app-client")
                    .claim("sid", sid)
                    .claim("preferred_username", "expired.user")
                    .issueTime(Date.from(FIXED_NOW.minusSeconds(600)))
                    .expirationTime(Date.from(FIXED_NOW.minusSeconds(120)))
                    .build();

            String expiredSigned = sign(signingKey, expiredClaims);
            assertThatThrownBy(() -> decoder.decode(expiredSigned)).isInstanceOf(JwtException.class);

            String rogueSigned = sign(rogueKey, expiredClaims);
            String unsigned = new PlainJWT(expiredClaims).serialize();

            MockHttpServletResponse rogueResp = new MockHttpServletResponse();
            authService.logout(null, rogueSigned, rogueResp);
            assertThat(sessionStore.findSession(sid)).isPresent();

            MockHttpServletResponse unsignedResp = new MockHttpServletResponse();
            authService.logout(null, unsigned, unsignedResp);
            assertThat(sessionStore.findSession(sid)).isPresent();

            MockHttpServletResponse validResp = new MockHttpServletResponse();
            authService.logout(null, expiredSigned, validResp);
            assertThat(sessionStore.findSession(sid)).isEmpty();
            assertThat(validResp.getHeaders("Set-Cookie"))
                    .anyMatch(h -> h.startsWith(AuthCookieService.ACCESS_TOKEN_COOKIE + "=;") && h.contains("Max-Age=0"))
                    .anyMatch(h -> h.startsWith(AuthCookieService.REFRESH_TOKEN_COOKIE + "=;") && h.contains("Max-Age=0"));
        } finally {
            sessionStore.deleteAllSessions(userId);
        }
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
