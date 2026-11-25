package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;

class MobileRegisterIT extends AbstractKeycloakIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    private SelfRegistrationSupport support;

    @BeforeEach
    void setUp() {
        support = new SelfRegistrationSupport(mockMvc, jdbcTemplate, keycloakClient, redisTemplate, objectMapper);
    }

    @AfterEach
    void tearDown() {
        support.cleanup();
    }

    @Test
    void verifyReturnsTokens() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "mob.reg." + suffix;
        String email = "mob.reg." + suffix + "@thi360.vn";
        String ip = SelfRegistrationSupport.freshIp();

        MvcResult started = mockMvc.perform(support.request(support.body(username, email), ip)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andReturn();
        UUID registrationId = UUID.fromString(objectMapper
                .readTree(started.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("registrationId")
                .asText());
        String otp = support.latestVerifyCode(email);

        MvcResult verified = mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .cookie(
                                new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "stale.access.token"),
                                new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "stale-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationId", registrationId,
                                "code", otp))))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(verified.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(verified.getResponse().getCookies()).isEmpty();
        JsonNode tokens = objectMapper.readTree(verified.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(tokens.path("accessExpiresIn").asInt()).isPositive();
        assertThat(tokens.path("refreshExpiresIn").asInt()).isEqualTo(604800);
        assertThat(tokens.path("mustChangePassword").asBoolean()).isFalse();

        UUID accountId = support.accountId(username);
        assertThat(accountId).isNotNull();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, source, preferred_locale, email_verified_at, must_change_password"
                        + " FROM identity.user_account WHERE id = ?",
                accountId);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("source")).intValue()).isEqualTo(3);
        assertThat(row.get("preferred_locale")).isEqualTo("en");
        assertThat(row.get("email_verified_at")).isNotNull();
        assertThat(row.get("must_change_password")).isEqualTo(false);
        assertThat(KEYCLOAK.getKeycloakAdminClient()
                        .realm("thi-360")
                        .users()
                        .get(accountId.toString())
                        .toRepresentation()
                        .isEmailVerified())
                .isTrue();

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.realm").value("APP"))
                .andExpect(jsonPath("$.access").value("EXAM"));

        mockMvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", tokens.path("refreshToken").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void invalidAndExpiredCode() throws Exception {
        String suffix = SelfRegistrationSupport.suffix();
        String username = "mob.regerr." + suffix;
        String email = "mob.regerr." + suffix + "@thi360.vn";
        String ip = SelfRegistrationSupport.freshIp();

        MvcResult started = support.startRegistration(support.body(username, email), ip)
                .andExpect(status().isOk())
                .andReturn();
        UUID registrationId = UUID.fromString(objectMapper
                .readTree(started.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("registrationId")
                .asText());
        String otp = support.latestVerifyCode(email);
        String wrongCode = "000000".equals(otp) ? "111111" : "000000";

        MvcResult wrong1 = mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationId", registrationId,
                                "code", wrongCode))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                .andExpect(jsonPath("$.remainingAttempts").value(4))
                .andReturn();
        assertThat(wrong1.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(support.liveAccountCount(username)).isZero();

        mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationId", registrationId,
                                "code", wrongCode))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                .andExpect(jsonPath("$.remainingAttempts").value(3));

        jdbcTemplate.update(
                "UPDATE identity.pending_registration SET code_expires_at = now() - interval '1 minute' WHERE id = ?",
                registrationId);

        MvcResult expired = mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationId", registrationId,
                                "code", otp))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"))
                .andReturn();
        assertThat(expired.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(support.liveAccountCount(username)).isZero();
        assertThat(support.keycloakHasUser(username)).isFalse();

        mockMvc.perform(post("/api/v1/auth/mobile/register/verify")
                        .header(ClientIpResolver.X_FORWARDED_FOR, SelfRegistrationSupport.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationId", UUID.randomUUID(),
                                "code", "123456"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
    }
}
