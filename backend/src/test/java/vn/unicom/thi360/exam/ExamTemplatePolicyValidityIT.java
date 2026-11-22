package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckValidity;
import vn.unicom.thi360.identity.spi.Permissions;

/** A saved VALID check must not authorize a publish once the template policy (publishable, fixed_counts) changed. */
@Transactional
class ExamTemplatePolicyValidityIT extends AbstractIntegrationTest {
    private static final String TEMPLATE = "THPTQG_LITERATURE_2025";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ExamCheckValidity validity;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Test
    void savedValidCheckWithoutPolicyIsStaleOncePublishableTurnsFalse() throws Exception {
        flags("publishable = true, fixed_counts = false");
        UUID id = examId("TEST-THPTQG-02");
        assertThat(check(id).path("status").asText()).isEqualTo("VALID");
        assertThat(policy(id)).isNull();
        assertThat(validity.assess(List.of(id)).get(id).status()).isEqualTo("VALID");

        flags("publishable = false");
        assertStaleAndPublishRefused(id);

        JsonNode recheck = check(id);
        assertThat(recheck.path("status").asText()).isEqualTo("INVALID");
        assertThat(recheck.path("groups").findValuesAsText("code")).contains("TEMPLATE_NOT_PUBLISHABLE");
        assertThat(policy(id)).isEqualTo("{\"unpublishable\": true}");
        assertThat(validity.assess(List.of(id)).get(id).status()).isEqualTo("INVALID");
    }

    @Test
    void savedValidCheckWithoutPolicyIsStaleOnceFixedCountsTurnsTrue() throws Exception {
        flags("publishable = true, fixed_counts = false");
        UUID id = examId("TEST-THPTQG-02");
        assertThat(check(id).path("status").asText()).isEqualTo("VALID");

        flags("fixed_counts = true");
        assertStaleAndPublishRefused(id);

        JsonNode recheck = check(id);
        assertThat(mapper.readTree(policy(id)).fieldNames()).toIterable().containsExactlyInAnyOrder("fixedCounts", "lockHash");
        assertThat(mapper.readTree(policy(id)).path("fixedCounts").asBoolean()).isTrue();
        assertThat(validity.assess(List.of(id)).get(id).status()).isEqualTo(recheck.path("status").asText());
    }

    @Test
    void savedValidCheckOfADefaultPolicyTemplateStaysValidAndPublishes() throws Exception {
        UUID id = examId("TEST-THPTQG-01");
        JsonNode result = check(id);
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(policy(id)).isNull();
        assertThat(validity.assess(List.of(id)).get(id).status()).isEqualTo("VALID");
        assertThat(listedAs(id, "VALID")).isTrue();

        mockMvc.perform(publish(id, result)).andExpect(status().isOk());
    }

    @Test
    void twoActiveDefaultTemplatesResolveUnpublishableByYearThenCode() throws Exception {
        flags("publishable = true, fixed_counts = false");
        UUID id = examId("TEST-THPTQG-02");
        assertThat(check(id).path("status").asText()).isEqualTo("VALID");

        // Same year (2025), lower C-collation code ('...2024_OLD' < '...2025'): publishable=true winner keeps it VALID.
        insertDefaultLiterature("THPTQG_LITERATURE_2024_OLD", 2025, false);
        assertThat(validity.assess(List.of(id)).get(id).status()).isEqualTo("VALID");

        // Same year (2025), higher C-collation code ('...2025_Z' > '...2025'): unpublishable winner makes it stale.
        insertDefaultLiterature("THPTQG_LITERATURE_2025_Z", 2025, false);
        assertStaleAndPublishRefused(id);
        JsonNode tieCheck = check(id);
        assertThat(tieCheck.path("status").asText()).isEqualTo("INVALID");
        assertThat(tieCheck.path("groups").findValuesAsText("code")).contains("TEMPLATE_NOT_PUBLISHABLE");

        // Newer structure_year (2026) beats 2025 regardless of code, flipping back to publishable=true -> VALID on recheck.
        insertDefaultLiterature("THPTQG_LITERATURE_2026_A", 2026, true);
        assertStaleAndPublishRefused(id);
        assertThat(check(id).path("status").asText()).isEqualTo("VALID");
    }

    private void insertDefaultLiterature(String code, int structureYear, boolean publishable) {
        jdbc.update("INSERT INTO exam.exam_template (code, kind_code, scope, subject_code, structure_year, matrix_year, "
                + "timing, build_mode, is_default, publishable, structure, active) "
                + "VALUES (?, 'THPTQG', 'SUBJECT', 'VAN', ?, ?, 'PER_PART', 'STANDARD', true, ?, "
                + "'{\"parts\":[]}'::jsonb, true)", code, structureYear, structureYear, publishable);
        entityManager.clear();
    }

    /** The check reads templates through JPA; drop the session cache so it sees the changed flag like a new request. */
    private void flags(String assignments) {
        jdbc.update("UPDATE exam.exam_template SET " + assignments + " WHERE code = ?", TEMPLATE);
        entityManager.clear();
    }

    private void assertStaleAndPublishRefused(UUID id) throws Exception {
        var assessment = validity.assess(List.of(id)).get(id);
        assertThat(assessment.status()).isEqualTo("NEEDS_RECHECK");
        assertThat(assessment.invalidReason()).isEqualTo("CONFIG_CHANGED");
        assertThat(listedAs(id, "NEEDS_RECHECK")).isTrue();
        String body = mockMvc.perform(publish(id, null)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("CHECK_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, id)).isZero();
    }

    private MockHttpServletRequestBuilder publish(UUID id, JsonNode checked) throws Exception {
        JsonNode exam = json(get(url(id)).with(asUser(Permissions.EXAM_VIEW)));
        ExamListingFixture.fill(jdbc, id);
        var body = mapper.createObjectNode().put("expectedRevision", exam.path("revision").asLong());
        var paths = body.putArray("pathIds");
        if (checked == null) paths.add("default");
        else checked.path("paths").forEach(path -> paths.add(path.path("pathId").asText()));
        return post(url(id) + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString());
    }

    private boolean listedAs(UUID id, String checkStatus) throws Exception {
        JsonNode page = json(get("/api/v1/admin/exams?checkStatus=" + checkStatus + "&size=200")
                .with(asUser(Permissions.EXAM_VIEW)));
        return page.findValuesAsText("id").contains(id.toString());
    }

    private JsonNode check(UUID id) throws Exception {
        return json(post(url(id) + "/check").with(asUser(Permissions.EXAM_MODIFY)));
    }

    private String policy(UUID id) {
        return jdbc.queryForObject("SELECT (config_snapshot -> 'templatePolicy')::text FROM exam.exam_check_run"
                + " WHERE exam_id = ?", String.class, id);
    }

    private UUID examId(String code) {
        return jdbc.queryForObject("SELECT id FROM exam.exam WHERE code_normalized = ?", UUID.class,
                code.toLowerCase(Locale.ROOT));
    }

    private static String url(UUID id) {
        return "/api/v1/admin/exams/" + id;
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
