package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2751 (HSA BA vòng 2, ADV:HSA-F:10/11): PATCH /exams/{id} accepts {@code buildMode}; a change is
 * handled like a scope change — the structure, matrix and groups are rebuilt from the create template
 * default for the new mode (confirmReplace required after a structure edit, EXAM_SCOPE_LOCKED when a
 * version is serving) — while id, code, name, description and instructions are kept. A null or
 * unchanged buildMode behaves exactly as the plain info update.
 */
@Transactional
class ExamBuildModeChangeIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void standardToReviewRebuildsAndKeepsTheInfo() throws Exception {
        JsonNode exam = created(subject("HSA", "TOAN", null));
        String id = exam.path("id").asText();
        JsonNode before = detail(id);

        patchInfo(id, "{\"buildMode\":\"REVIEW\",\"confirmReplace\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildMode").value("REVIEW"))
                .andExpect(jsonPath("$.templateCode").value("HSA_REVIEW"));

        JsonNode after = detail(id);
        assertThat(after.path("id").asText()).isEqualTo(id);
        assertThat(after.path("code").asText()).isEqualTo(before.path("code").asText());
        assertThat(after.path("name").asText()).isEqualTo(before.path("name").asText());
        assertThat(after.path("description").asText()).isEqualTo(before.path("description").asText());
        assertThat(after.path("instructions").asText()).isEqualTo(before.path("instructions").asText());
        assertThat(after.path("practiceScoring").asText()).isEqualTo("LINEAR");
        JsonNode part = after.path("parts").get(0);
        assertThat(after.path("parts")).hasSize(1);
        assertThat(part.path("name").asText()).isEqualTo("Review");
        assertThat(part.path("sections").get(0).path("subjectCode").asText()).isEqualTo("TOAN");
        // The whole standard part set was replaced, not patched.
        assertThat(after.path("questionCount").asInt()).isLessThan(before.path("questionCount").asInt());
        assertThat(edited(id)).isFalse();
    }

    @Test
    void reviewToStandardRebuildsFromTheSubjectTemplate() throws Exception {
        JsonNode exam = created(subject("HSA", "TOAN", "REVIEW"));
        String id = exam.path("id").asText();
        JsonNode before = detail(id);
        assertThat(before.path("practiceScoring").asText()).isEqualTo("LINEAR");

        patchInfo(id, "{\"buildMode\":\"STANDARD\",\"confirmReplace\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildMode").value("STANDARD"))
                .andExpect(jsonPath("$.templateCode").value("HSA_SUBJECT_TOAN"))
                .andExpect(jsonPath("$.subjectCode").value("TOAN"));

        JsonNode after = detail(id);
        assertThat(after.path("code").asText()).isEqualTo(before.path("code").asText());
        assertThat(after.path("name").asText()).isEqualTo(before.path("name").asText());
        assertThat(after.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(after.path("maxScore").decimalValue()).isEqualByComparingTo("50");
        assertThat(after.path("parts").get(0).path("name").asText()).isNotEqualTo("Review");
        assertThat(edited(id)).isFalse();
    }

    @Test
    void buildModeChangeAfterAStructureEditRequiresConfirmReplace() throws Exception {
        JsonNode exam = created(subject("HSA", "TOAN", null));
        String id = exam.path("id").asText();
        JsonNode structure = structure(id);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[],\"expectedRevision\":"
                                + structure.path("revision").asLong() + "}"))
                .andExpect(status().isOk());

        patchInfo(id, "{\"buildMode\":\"REVIEW\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_REPLACE_CONFIRM"));
        assertThat(detail(id).path("buildMode").asText()).isEqualTo("STANDARD");

        patchInfo(id, "{\"buildMode\":\"REVIEW\",\"confirmReplace\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode").value("HSA_REVIEW"))
                .andExpect(jsonPath("$.parts[0].name").value("Review"));
    }

    @Test
    void publishedExamRejectsTheChangeAndStaysUntouched() throws Exception {
        JsonNode exam = created(subject("HSA", "TOAN", null));
        String id = exam.path("id").asText();
        markPublished(id);
        String nameBefore = detail(id).path("name").asText();

        patchInfo(id, "{\"buildMode\":\"REVIEW\",\"confirmReplace\":true,"
                        + "\"name\":\"Renamed\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_SCOPE_LOCKED"));

        JsonNode after = detail(id);
        assertThat(after.path("buildMode").asText()).isEqualTo("STANDARD");
        assertThat(after.path("name").asText()).isEqualTo(nameBefore);
        assertThat(after.path("templateCode").asText()).isEqualTo("HSA_SUBJECT_TOAN");
    }

    @Test
    void reviewOnANonSubjectScopeIsRejectedAsOnCreate() throws Exception {
        JsonNode exam = created(part("HSA", "TOAN"));
        String id = exam.path("id").asText();
        assertThat(detail(id).path("scope").asText()).isEqualTo("PART");

        patchInfo(id, "{\"buildMode\":\"REVIEW\",\"confirmReplace\":true}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        assertThat(detail(id).path("buildMode").asText()).isEqualTo("STANDARD");
    }

    @Test
    void nullOrUnchangedBuildModeKeepsThePlainInfoUpdate() throws Exception {
        JsonNode exam = created(subject("HSA", "TOAN", "REVIEW"));
        String id = exam.path("id").asText();
        JsonNode before = detail(id);
        JsonNode partsBefore = structure(id).path("parts").deepCopy();
        long revision = before.path("revision").asLong();

        // No buildMode: a plain info update, structure untouched.
        patchInfo(id, "{\"name\":\"Tên mới\"}")
                .andExpect(status().isOk());
        // Same buildMode: still no rebuild.
        patchInfo(id, "{\"buildMode\":\"REVIEW\",\"description\":\"d2\"}")
                .andExpect(status().isOk());

        JsonNode after = detail(id);
        assertThat(after.path("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(after.path("templateCode").asText()).isEqualTo("HSA_REVIEW");
        assertThat(after.path("name").asText()).isEqualTo("Tên mới");
        assertThat(after.path("description").asText()).isEqualTo("d2");
        assertThat(after.path("revision").asLong()).isGreaterThan(revision);
        assertThat(structure(id).path("parts")).isEqualTo(partsBefore);
    }

    // ------------------------------------------------------------------ helpers

    /** SUBJECT exam of the kind; {@code mode} null means the create default (STANDARD). */
    private org.springframework.test.web.servlet.ResultActions subject(String kind, String subject,
            String mode) throws Exception {
        ObjectNode body = mapper.createObjectNode()
                .put("kind", kind).put("scope", "SUBJECT").put("subjectCode", subject)
                .put("name", "BMC " + kind + " " + subject)
                .put("code", "BM-" + UUID.randomUUID().toString().substring(0, 8))
                .put("description", "mô tả giữ")
                .put("instructions", "<p>Hướng dẫn giữ</p>");
        if (mode != null) body.put("buildMode", mode);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    /** PART exam of the kind (STANDARD is the only allowed mode there). */
    private org.springframework.test.web.servlet.ResultActions part(String kind, String partCode)
            throws Exception {
        ObjectNode body = mapper.createObjectNode()
                .put("kind", kind).put("scope", "PART").put("partCode", partCode)
                .put("name", "BMC " + kind + " PART " + partCode)
                .put("code", "BM-" + UUID.randomUUID().toString().substring(0, 8));
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    /** The PATCH body always carries the current expectedRevision, as the screen does. */
    private org.springframework.test.web.servlet.ResultActions patchInfo(String id, String fields)
            throws Exception {
        String body = "{\"expectedRevision\":" + detail(id).path("revision").asLong()
                + "," + fields.substring(1);
        return mockMvc.perform(patch("/api/v1/admin/exams/{id}", id)
                .with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode detail(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode structure(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode created(org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private boolean edited(String id) {
        entityManager.flush();
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT edited FROM exam.exam_structure WHERE exam_id = ?",
                Boolean.class, UUID.fromString(id)));
    }

    /** Marks the exam as having a serving version without running the publish flow. */
    private void markPublished(String id) {
        entityManager.flush();
        jdbc.update("UPDATE exam.exam SET serving_version = 1 WHERE id = ?", UUID.fromString(id));
        entityManager.clear();
    }
}
