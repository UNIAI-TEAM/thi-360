package vn.unicom.thi360.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class AuditPlatformLoginIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_IP = "203.0.113.70";

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanupRateLimitBucket() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @Test
    void platformAdminLogin_emitsLoginAuditEventReadableViaAdminAuditEndpoint() throws Exception {
        Instant beforeLogin = Instant.now().minusSeconds(5);

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"platform\",\"password\":\"" + TEST_PLATFORM_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value("/"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        Cookie atCookie = loginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(atCookie).isNotNull();

        MvcResult meRes = mockMvc.perform(get("/api/v1/me").cookie(atCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("platform"))
                .andExpect(jsonPath("$.realm").value("MASTER"))
                .andExpect(jsonPath("$.access").value("MANAGE"))
                .andReturn();

        String platformId = objectMapper.readTree(meRes.getResponse().getContentAsString()).get("id").asText();

        int relayedTotal = 0;
        int batch;
        while ((batch = outboxRelay.relayBatch()) > 0) {
            relayedTotal += batch;
        }
        assertThat(relayedTotal).isGreaterThanOrEqualTo(1);

        MvcResult auditRes = mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("action", "LOGIN")
                        .cookie(atCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(auditRes.getResponse().getContentAsString());
        assertThat(root.get("totalItems").asLong()).isGreaterThanOrEqualTo(1);

        List<JsonNode> matching = new ArrayList<>();
        for (JsonNode item : root.get("items")) {
            if (platformId.equals(item.path("targetId").asText())
                    && "PLATFORM_ADMIN".equals(item.path("targetType").asText())
                    && !Instant.parse(item.path("occurredAt").asText()).isBefore(beforeLogin)) {
                matching.add(item);
            }
        }

        assertThat(matching).isNotEmpty();
        JsonNode event = matching.getFirst();
        assertThat(event.get("action").asText()).isEqualTo("LOGIN");
        assertThat(event.get("module").asText()).isEqualTo("identity");
        assertThat(event.get("actorId").asText()).isEqualTo(platformId);
        assertThat(event.get("actorUsername").asText()).isEqualTo("platform");
        assertThat(event.get("actorType").asText()).isEqualTo("PLATFORM");
        assertThat(event.get("targetType").asText()).isEqualTo("PLATFORM_ADMIN");
        assertThat(event.get("targetId").asText()).isEqualTo(platformId);
        assertThat(event.get("targetLabel").asText()).isEqualTo("platform");
        assertThat(event.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(event.path("after").path("realm").asText()).isEqualTo("MASTER");

        MvcResult byActorRes = mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("actorId", platformId)
                        .param("action", "LOGIN")
                        .cookie(atCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode byActorRoot = objectMapper.readTree(byActorRes.getResponse().getContentAsString());
        assertThat(byActorRoot.get("totalItems").asLong()).isGreaterThanOrEqualTo(1);
        boolean foundByActorId = false;
        for (JsonNode item : byActorRoot.get("items")) {
            if (event.get("id").asText().equals(item.path("id").asText())) {
                foundByActorId = true;
                assertThat(item.path("actorId").asText()).isEqualTo(platformId);
                assertThat(item.path("actorUsername").asText()).isEqualTo("platform");
                assertThat(item.path("actorType").asText()).isEqualTo("PLATFORM");
                break;
            }
        }
        assertThat(foundByActorId).isTrue();
    }
}
