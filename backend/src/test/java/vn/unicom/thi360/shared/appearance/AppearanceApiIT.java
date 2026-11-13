package vn.unicom.thi360.shared.appearance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.appearance.application.AppearanceService;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

class AppearanceApiIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetAppearanceAndOutboxState() {
        jdbcTemplate.update("DELETE FROM shared.system_setting WHERE key = 'appearance'");
        jdbcTemplate.update("DELETE FROM shared.outbox_event WHERE topic = 'audit'");
        redisTemplate.delete(AppearanceService.CACHE_KEY);
    }

    @Test
    void getAppearance_withoutLogin_returnsDefaultWithCacheControlAndETag() throws Exception {
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=60")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.palette.presetId").value("thi360-green"))
                .andExpect(jsonPath("$.palette.custom").doesNotExist())
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getAppearance_withMatchingIfNoneMatch_returns304AndInvalidatesAfterSave() throws Exception {
        mockMvc.perform(get("/api/v1/appearance")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"0\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=60")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(content().string(""));

        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "navy-teal" },
                                  "mode": "dark",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/appearance")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.palette.presetId").value("navy-teal"))
                .andExpect(jsonPath("$.mode").value("dark"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/appearance")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"1\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(content().string(""));
    }

    @Test
    void putAndReset_withoutCookie_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "emerald-gold" },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void putAndReset_withoutPermission_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "emerald-gold" },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "emerald-gold" },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void putAppearance_badHex_returns400ValidationFailedWithFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": {
                                    "presetId": "custom",
                                    "custom": {
                                      "primary": "123456",
                                      "accent": "#ZZZZZZ"
                                    }
                                  },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("palette.custom.primary")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("palette.custom.accent")));
    }

    @Test
    void putAppearance_unknownPreset_returns400ValidationFailedWithFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "neon-pink" },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("palette.presetId")));

        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "custom" },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("palette.custom")));
    }

    @Test
    void putAndReset_staleVersion_returns412VersionConflict() throws Exception {
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "indigo-amber" },
                                  "mode": "light",
                                  "version": 1
                                }
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "indigo-amber" },
                                  "mode": "dark",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "slate-crimson" },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void saveCustomAndPreset_thenGet_returnsNewValueAndWritesAuditRow_andResetRestoresDefault()
            throws Exception {
        UUID actorId = Ids.newId();
        String actorUsername = "appearance.admin";

        // 1. Save custom colors with custom preset
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(actorId, actorUsername, Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": {
                                    "presetId": "custom",
                                    "custom": {
                                      "primary": "#1E3A8A",
                                      "accent": "#F59E0B"
                                    }
                                  },
                                  "mode": "dark",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palette.presetId").value("custom"))
                .andExpect(jsonPath("$.palette.custom.primary").value("#1E3A8A"))
                .andExpect(jsonPath("$.palette.custom.accent").value("#F59E0B"))
                .andExpect(jsonPath("$.mode").value("dark"))
                .andExpect(jsonPath("$.version").value(1));

        // 2. Public GET returns saved value
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.palette.presetId").value("custom"))
                .andExpect(jsonPath("$.palette.custom.primary").value("#1E3A8A"))
                .andExpect(jsonPath("$.palette.custom.accent").value("#F59E0B"))
                .andExpect(jsonPath("$.mode").value("dark"))
                .andExpect(jsonPath("$.version").value(1));

        // 3. Switch to fixed preset while retaining custom colors
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(actorId, actorUsername, Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": {
                                    "presetId": "emerald-gold",
                                    "custom": {
                                      "primary": "#1E3A8A",
                                      "accent": "#F59E0B"
                                    }
                                  },
                                  "mode": "light",
                                  "version": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palette.presetId").value("emerald-gold"))
                .andExpect(jsonPath("$.palette.custom.primary").value("#1E3A8A"))
                .andExpect(jsonPath("$.palette.custom.accent").value("#F59E0B"))
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.version").value(2));

        // 4. Reset restores default and bumps version
        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(actorId, actorUsername, Permissions.APPEARANCE_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palette.presetId").value("thi360-green"))
                .andExpect(jsonPath("$.palette.custom").doesNotExist())
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.version").value(3));

        // 5. Public GET returns reset default with version 3
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.palette.presetId").value("thi360-green"))
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.version").value(3));

        // 6. Relay outbox and verify audit rows written for PUT and reset
        int relayed = outboxRelay.relayBatch();
        assertThat(relayed).isGreaterThanOrEqualTo(3);

        List<Map<String, Object>> auditRows = jdbcTemplate.queryForList(
                """
                SELECT action, target_type, target_id, actor_id, actor_username, outcome,
                       before::text AS before_json, after::text AS after_json
                FROM audit.audit_event
                WHERE action = 'APPEARANCE_UPDATED' AND actor_id = ?
                ORDER BY occurred_at ASC
                """,
                actorId);

        assertThat(auditRows).hasSize(3);

        Map<String, Object> firstAudit = auditRows.get(0);
        assertThat(firstAudit.get("target_type")).isEqualTo("SYSTEM_SETTING");
        assertThat(firstAudit.get("target_id")).isEqualTo("appearance");
        assertThat(firstAudit.get("actor_username")).isEqualTo(actorUsername);
        assertThat(((Number) firstAudit.get("outcome")).intValue()).isEqualTo(1);

        JsonNode firstBefore = objectMapper.readTree((String) firstAudit.get("before_json"));
        JsonNode firstAfter = objectMapper.readTree((String) firstAudit.get("after_json"));
        assertThat(firstBefore.at("/palette/presetId").asText()).isEqualTo("thi360-green");
        assertThat(firstBefore.get("version").asLong()).isEqualTo(0L);
        assertThat(firstAfter.at("/palette/presetId").asText()).isEqualTo("custom");
        assertThat(firstAfter.at("/palette/custom/primary").asText()).isEqualTo("#1E3A8A");
        assertThat(firstAfter.get("version").asLong()).isEqualTo(1L);

        Map<String, Object> resetAudit = auditRows.get(2);
        JsonNode resetBefore = objectMapper.readTree((String) resetAudit.get("before_json"));
        JsonNode resetAfter = objectMapper.readTree((String) resetAudit.get("after_json"));
        assertThat(resetBefore.at("/palette/presetId").asText()).isEqualTo("emerald-gold");
        assertThat(resetBefore.get("version").asLong()).isEqualTo(2L);
        assertThat(resetAfter.at("/palette/presetId").asText()).isEqualTo("thi360-green");
        assertThat(resetAfter.get("mode").asText()).isEqualTo("light");
        assertThat(resetAfter.get("version").asLong()).isEqualTo(3L);
    }
}
