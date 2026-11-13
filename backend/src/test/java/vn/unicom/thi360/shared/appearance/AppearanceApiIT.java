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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.appearance.domain.AppearancePolicy;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
class AppearanceApiIT extends AbstractIntegrationTest {

    @MockitoSpyBean
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private ObjectMapper objectMapper;

    private final AtomicReference<CyclicBarrier> readBarrier = new AtomicReference<>();
    private Set<UUID> priorAuditIds = Set.of();

    @BeforeEach
    void resetAppearanceAndOutboxState() {
        readBarrier.set(null);
        Mockito.reset(jdbcTemplate);
        Mockito.doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            CyclicBarrier barrier = readBarrier.get();
            if (barrier != null) {
                barrier.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(jdbcTemplate).query(
                ArgumentMatchers.eq("SELECT value::text, version FROM shared.system_setting WHERE key = ?"),
                ArgumentMatchers.<RowMapper<Object>>any(),
                ArgumentMatchers.eq(AppearancePolicy.SETTING_KEY));
        jdbcTemplate.update("DELETE FROM shared.system_setting WHERE key = 'appearance'");
        jdbcTemplate.update("DELETE FROM shared.outbox_event WHERE topic = 'audit'");
        priorAuditIds = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM audit.audit_event WHERE action = 'APPEARANCE_UPDATED'",
                UUID.class));
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
                        .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
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
                        .with(asUser(Permissions.USER_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 0 }
                                """))
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
    void resetAppearance_missingOrInvalidVersion_returns400ValidationFailedAndLeavesStateUnchanged()
            throws Exception {
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

        int outboxBefore = countAuditOutboxEvents();

        // Missing body
        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(Permissions.APPEARANCE_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("version")));

        // Empty JSON object {}
        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("version")));

        // Explicit null version
        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": null }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("version")));

        // Negative version
        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": -1 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("version")));

        // Rejected requests leave stored config and audit outbox unchanged
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palette.presetId").value("navy-teal"))
                .andExpect(jsonPath("$.mode").value("dark"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(countAuditOutboxEvents()).isEqualTo(outboxBefore);
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

        // 3. Switch to fixed preset with custom omitted -> stored custom pair is retained
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(actorId, actorUsername, Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": {
                                    "presetId": "emerald-gold"
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

        // Public GET still has retained custom colors
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.palette.presetId").value("emerald-gold"))
                .andExpect(jsonPath("$.palette.custom.primary").value("#1E3A8A"))
                .andExpect(jsonPath("$.palette.custom.accent").value("#F59E0B"))
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.version").value(2));

        // 4. Versioned reset restores default, clears stored custom pair, and bumps version
        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asUser(actorId, actorUsername, Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 2 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palette.presetId").value("thi360-green"))
                .andExpect(jsonPath("$.palette.custom").doesNotExist())
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.version").value(3));

        // 5. Public GET returns reset default with version 3 and no custom pair
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.palette.presetId").value("thi360-green"))
                .andExpect(jsonPath("$.palette.custom").doesNotExist())
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.version").value(3));

        // 6. Relay outbox and verify audit rows written for PUT and reset including actor_roles & actor_type
        int relayed = outboxRelay.relayBatch();
        assertThat(relayed).isGreaterThanOrEqualTo(3);

        List<AuditRow> auditRows = queryAppearanceAuditRows();
        assertThat(auditRows).hasSize(3);

        AuditRow firstAudit = auditRows.get(0);
        assertThat(firstAudit.targetType()).isEqualTo("SYSTEM_SETTING");
        assertThat(firstAudit.targetId()).isEqualTo("appearance");
        assertThat(firstAudit.actorId()).isEqualTo(actorId);
        assertThat(firstAudit.actorUsername()).isEqualTo(actorUsername);
        assertThat(firstAudit.actorType()).isEqualTo(1);
        assertThat(firstAudit.actorRoles())
                .isNotEmpty()
                .contains("ACCESS_MANAGE", "REALM_APP", "PERM_" + Permissions.APPEARANCE_MODIFY);
        assertThat(firstAudit.outcome()).isEqualTo(1);

        JsonNode firstBefore = objectMapper.readTree(firstAudit.beforeJson());
        JsonNode firstAfter = objectMapper.readTree(firstAudit.afterJson());
        assertThat(firstBefore.at("/palette/presetId").asText()).isEqualTo("thi360-green");
        assertThat(firstBefore.get("version").asLong()).isEqualTo(0L);
        assertThat(firstAfter.at("/palette/presetId").asText()).isEqualTo("custom");
        assertThat(firstAfter.at("/palette/custom/primary").asText()).isEqualTo("#1E3A8A");
        assertThat(firstAfter.get("version").asLong()).isEqualTo(1L);

        AuditRow resetAudit = auditRows.get(2);
        assertThat(resetAudit.actorType()).isEqualTo(1);
        assertThat(resetAudit.actorRoles())
                .isNotEmpty()
                .contains("ACCESS_MANAGE", "REALM_APP", "PERM_" + Permissions.APPEARANCE_MODIFY);
        JsonNode resetBefore = objectMapper.readTree(resetAudit.beforeJson());
        JsonNode resetAfter = objectMapper.readTree(resetAudit.afterJson());
        assertThat(resetBefore.at("/palette/presetId").asText()).isEqualTo("emerald-gold");
        assertThat(resetBefore.at("/palette/custom/primary").asText()).isEqualTo("#1E3A8A");
        assertThat(resetBefore.get("version").asLong()).isEqualTo(2L);
        assertThat(resetAfter.at("/palette/presetId").asText()).isEqualTo("thi360-green");
        assertThat(resetAfter.at("/palette/custom").isMissingNode()).isTrue();
        assertThat(resetAfter.get("mode").asText()).isEqualTo("light");
        assertThat(resetAfter.get("version").asLong()).isEqualTo(3L);
    }

    @Test
    void resetAppearance_asPlatformAdmin_recordsPlatformActorTypeAndRoles() throws Exception {
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
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/appearance/reset")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": 1 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        outboxRelay.relayBatch();
        List<AuditRow> auditRows = queryAppearanceAuditRows();
        assertThat(auditRows).hasSize(2);

        AuditRow platformReset = auditRows.get(1);
        assertThat(platformReset.actorUsername()).isEqualTo("platform");
        assertThat(platformReset.actorType()).isEqualTo(2);
        assertThat(platformReset.actorRoles())
                .isNotEmpty()
                .contains("ACCESS_MANAGE", "REALM_MASTER");
    }

    @Test
    void concurrentFirstWrite_absentRow_oneWinsAndOneReturns412Without500() throws Exception {
        List<MvcResult> results = runConcurrentWithReadBarrier(
                () -> mockMvc.perform(put("/api/v1/admin/appearance")
                                .with(asUser(Permissions.APPEARANCE_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "palette": { "presetId": "navy-teal" },
                                          "mode": "dark",
                                          "version": 0
                                        }
                                        """))
                        .andReturn(),
                () -> mockMvc.perform(put("/api/v1/admin/appearance")
                                .with(asUser(Permissions.APPEARANCE_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "palette": { "presetId": "emerald-gold" },
                                          "mode": "light",
                                          "version": 0
                                        }
                                        """))
                        .andReturn());

        assertOneWinnerOne412(results, 1L);
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        outboxRelay.relayBatch();
        List<AuditRow> auditRows = queryAppearanceAuditRows();
        assertThat(auditRows).hasSize(1);
        assertThat(objectMapper.readTree(auditRows.getFirst().afterJson()).get("version").asLong())
                .isEqualTo(1L);
    }

    @Test
    void concurrentExistingRowUpdate_putVsPut_oneWinsAndOneReturns412Without500() throws Exception {
        mockMvc.perform(put("/api/v1/admin/appearance")
                        .with(asUser(Permissions.APPEARANCE_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "palette": { "presetId": "indigo-amber" },
                                  "mode": "light",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        outboxRelay.relayBatch();

        List<MvcResult> results = runConcurrentWithReadBarrier(
                () -> mockMvc.perform(put("/api/v1/admin/appearance")
                                .with(asUser(Permissions.APPEARANCE_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "palette": { "presetId": "slate-crimson" },
                                          "mode": "dark",
                                          "version": 1
                                        }
                                        """))
                        .andReturn(),
                () -> mockMvc.perform(put("/api/v1/admin/appearance")
                                .with(asUser(Permissions.APPEARANCE_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "palette": { "presetId": "sky-orange" },
                                          "mode": "light",
                                          "version": 1
                                        }
                                        """))
                        .andReturn());

        assertOneWinnerOne412(results, 2L);
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        outboxRelay.relayBatch();
        List<AuditRow> auditRows = queryAppearanceAuditRows();
        assertThat(auditRows).hasSize(2);
        assertThat(objectMapper.readTree(auditRows.get(1).afterJson()).get("version").asLong())
                .isEqualTo(2L);
    }

    @Test
    void concurrentExistingRowUpdate_putVsVersionedReset_oneWinsAndOneReturns412Without500()
            throws Exception {
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
        outboxRelay.relayBatch();

        List<MvcResult> results = runConcurrentWithReadBarrier(
                () -> mockMvc.perform(put("/api/v1/admin/appearance")
                                .with(asUser(Permissions.APPEARANCE_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "palette": { "presetId": "emerald-gold" },
                                          "mode": "light",
                                          "version": 1
                                        }
                                        """))
                        .andReturn(),
                () -> mockMvc.perform(post("/api/v1/admin/appearance/reset")
                                .with(asUser(Permissions.APPEARANCE_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "version": 1 }
                                        """))
                        .andReturn());

        assertOneWinnerOne412(results, 2L);
        mockMvc.perform(get("/api/v1/appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        outboxRelay.relayBatch();
        List<AuditRow> auditRows = queryAppearanceAuditRows();
        assertThat(auditRows).hasSize(2);
        assertThat(objectMapper.readTree(auditRows.get(1).afterJson()).get("version").asLong())
                .isEqualTo(2L);
    }

    private List<MvcResult> runConcurrentWithReadBarrier(
            Callable<MvcResult> first,
            Callable<MvcResult> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        readBarrier.set(barrier);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> f1 = pool.submit(first);
            Future<MvcResult> f2 = pool.submit(second);
            return List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
        } finally {
            readBarrier.set(null);
            pool.shutdownNow();
        }
    }

    private void assertOneWinnerOne412(List<MvcResult> results, long expectedWinnerVersion)
            throws Exception {
        List<Integer> statuses = results.stream()
                .map(r -> r.getResponse().getStatus())
                .toList();
        assertThat(statuses).doesNotContain(500);
        assertThat(statuses).containsExactlyInAnyOrder(200, 412);

        MvcResult winner = results.stream()
                .filter(r -> r.getResponse().getStatus() == 200)
                .findFirst()
                .orElseThrow();
        MvcResult loser = results.stream()
                .filter(r -> r.getResponse().getStatus() == 412)
                .findFirst()
                .orElseThrow();

        JsonNode winnerBody = objectMapper.readTree(winner.getResponse().getContentAsString());
        JsonNode loserBody = objectMapper.readTree(loser.getResponse().getContentAsString());
        assertThat(winnerBody.get("version").asLong()).isEqualTo(expectedWinnerVersion);
        assertThat(loserBody.get("code").asText()).isEqualTo("VERSION_CONFLICT");
    }

    private int countAuditOutboxEvents() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.outbox_event WHERE topic = 'audit'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private List<AuditRow> queryAppearanceAuditRows() {
        return jdbcTemplate.query(
                """
                SELECT id, target_type, target_id, actor_id, actor_username, actor_roles, actor_type,
                       outcome, before::text AS before_json, after::text AS after_json
                FROM audit.audit_event
                WHERE action = 'APPEARANCE_UPDATED'
                ORDER BY occurred_at ASC
                """,
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    java.sql.Array rolesSqlArray = rs.getArray("actor_roles");
                    List<String> roles = rolesSqlArray == null
                            ? List.of()
                            : Arrays.asList((String[]) rolesSqlArray.getArray());
                    return new AuditRow(
                            id,
                            rs.getString("target_type"),
                            rs.getString("target_id"),
                            rs.getObject("actor_id", UUID.class),
                            rs.getString("actor_username"),
                            roles,
                            rs.getInt("actor_type"),
                            rs.getInt("outcome"),
                            rs.getString("before_json"),
                            rs.getString("after_json"));
                }).stream()
                .filter(row -> !priorAuditIds.contains(row.id()))
                .toList();
    }

    private record AuditRow(
            UUID id,
            String targetType,
            String targetId,
            UUID actorId,
            String actorUsername,
            List<String> actorRoles,
            int actorType,
            int outcome,
            String beforeJson,
            String afterJson) {
    }
}
