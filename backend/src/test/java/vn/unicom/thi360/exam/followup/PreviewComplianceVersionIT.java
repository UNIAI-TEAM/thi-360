package vn.unicom.thi360.exam.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** FU-QA-PREVIEW-COMPLIANCE-VERSION: the compliance of a published-version preview is read from that version. */
@Transactional
class PreviewComplianceVersionIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private static String parts(String subject, String grade, String topicId, String blockId) {
        return """
                [{"id":"part","order":1,"name":"Part","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"section","subjectCode":"%s","questionCount":1,
                    "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE",
                              "byGrade":[{"code":"%s","value":1}]},
                    "topicQuotas":[{"id":"%s","nodeIds":["%s"],"count":1,"enforcement":"REFERENCE"}],
                    "blocks":[{"id":"%s","name":"Group","questionType":"SINGLE_CHOICE","questionCount":1}]}],
                  "options":[]}]
                """.formatted(subject, grade, topicId, UUID.randomUUID(), blockId);
    }

    private String subject() {
        UUID id = UUID.randomUUID();
        String code = "PCV_" + id.toString().replace("-", "");
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Preview subject', 'Preview subject', 0, 1, false, 1, ?)
                """, id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        UUID question = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'SINGLE_CHOICE','READY',?,1,1)", question, "P-" + question, id);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                question, "{\"content\":\"q\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\","
                        + "\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true}]}}");
        return code;
    }

    private JsonNode preview(UUID exam, String body) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> criteria(JsonNode result) {
        List<String> criteria = new ArrayList<>();
        result.path("compliance").forEach(row -> criteria.add(row.path("criterion").asText()));
        return criteria;
    }

    @Test
    void publishedVersionPreviewReadsPublishedStructure() throws Exception {
        String subject = subject();
        String published = parts(subject, "G12", "published-topic", "published-block");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes, question_count) "
                + "values (?, ?, ?, 'Preview', 'FULL', 'SHARED', 60, 1)", id, id.toString(), id.toString());
        // The working copy was edited after publishing: other grade, other topic quota, other group.
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts(subject, "G10", "working-topic", "working-block") + "}");
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, "
                        + "check_run_id, state, status, published_at, published_by) "
                        + "values (?, ?, 1, cast(? as jsonb), cast(? as jsonb), array['default'], array['default'], ?, "
                        + "'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), id, "{\"code\":\"OLD\",\"name\":\"Old\",\"scope\":\"FULL\",\"timing\":\"SHARED\","
                        + "\"durationMinutes\":45,\"practiceScoring\":\"RAW\"}",
                "{\"parts\":" + published + "}", UUID.randomUUID(), UUID.randomUUID());

        JsonNode version = preview(id, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1,\"version\":1}");
        assertThat(version.path("status").asText()).as(version.toString()).isEqualTo("GENERATED");
        assertThat(criteria(version)).contains("GRADE:G12", "TOPIC:published-topic")
                .doesNotContain("GRADE:G10", "TOPIC:working-topic");
        assertThat(version.path("paper").path("parts").get(0).path("blocks").get(0).path("blockId").asText())
                .isEqualTo("published-block");

        JsonNode working = preview(id, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        assertThat(working.path("status").asText()).as(working.toString()).isEqualTo("GENERATED");
        assertThat(criteria(working)).contains("GRADE:G10", "TOPIC:working-topic")
                .doesNotContain("GRADE:G12", "TOPIC:published-topic");
    }
    /** FU-FE4-PREVIEW: the stored preview reports when it expires, so the UI can count the retention down. */
    @Test
    void generatedPreviewReportsItsExpiry() throws Exception {
        String subject = subject();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes, question_count) "
                + "values (?, ?, ?, 'Preview', 'FULL', 'SHARED', 60, 1)", id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts(subject, "G12", "topic", "block") + "}");
        JsonNode generated = preview(id, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        assertThat(generated.path("status").asText()).as(generated.toString()).isEqualTo("GENERATED");
        assertThat(java.time.Duration.between(java.time.Instant.parse(generated.path("generatedAt").asText()),
                java.time.Instant.parse(generated.path("expiresAt").asText())))
                .isEqualTo(java.time.Duration.ofMinutes(30));

        UUID empty = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing) "
                + "values (?, ?, ?, 'Preview', 'FULL', 'SHARED')", empty, empty.toString(), empty.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, '{\"parts\":[]}'::jsonb, 'empty')", empty);
        JsonNode failed = preview(empty, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("expiresAt").isNull() || failed.path("expiresAt").isMissingNode()).isTrue();
    }
}
