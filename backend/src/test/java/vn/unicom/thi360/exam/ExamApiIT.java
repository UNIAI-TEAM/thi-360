package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamApiIT extends AbstractIntegrationTest {
    private static final String EXAM = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper mapper;

    @Test
    void createChecksPermissionConflictValidationAndAudit() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_VIEW))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        String result = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(result).path("id").asText();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.outbox_event WHERE topic='audit' "
                        + "AND payload->>'module'='EXAM' AND payload->>'action'='EXAM.CREATE' "
                        + "AND payload->>'targetId'=?", Long.class, id)).isEqualTo(1L);
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_CODE_TAKEN"));
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"FULL\"", "\"SUBJECT\"").replace(code, code + "X")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_SCOPE_NOT_ALLOWED"));
    }

    @Test
    void permissionsAndMissingExam() throws Exception {
        mockMvc.perform(get(EXAM)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(EXAM).with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(EXAM).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("HSA-F-DEV-001"));
        mockMvc.perform(get("/api/v1/admin/exams/" + UUID.randomUUID())
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[],\"expectedRevision\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleRevisionIsRejected() throws Exception {
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[],\"expectedRevision\":9223372036854775807}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("EXAM_REVISION_CONFLICT"));
    }

    @Test
    void instructionsImg422() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code
                + "\",\"instructions\":\"<p>Doc ky</p><img src=\\\"x.png\\\">\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
        long revision = mapper.readTree(mockMvc.perform(get(EXAM)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("revision").asLong();
        mockMvc.perform(patch(EXAM).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"<img src=\\\"x.png\\\" onerror=\\\"pwn()\\\"/>\","
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
    }

    @Test
    void instructionsNonImageViolationRejectedOnCreateAndPatch() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code
                + "\",\"instructions\":\"<p onclick=\\\"alert(1)\\\">Doc ky</p>\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
        long revision = mapper.readTree(mockMvc.perform(get(EXAM)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("revision").asLong();
        mockMvc.perform(patch(EXAM).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"<p>Ok</p><script>alert(1)</script>\","
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_INSTRUCTIONS_UNSAFE"));
    }

    @Test
    void safeInstructionsAreStoredCleaned() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        String html = "<P><STRONG>Doc ky</STRONG> <EM>de bai</EM></P><UL><LI>70 phut</LI></UL>";
        String cleaned = "<p><strong>Doc ky</strong> <em>de bai</em></p><ul><li>70 phut</li></ul>";
        String result = mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\""
                                + code + "\",\"instructions\":\"" + html.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(result).path("id").asText();
        String detail = mockMvc.perform(get("/api/v1/admin/exams/" + id)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(detail).path("instructions").asText()).isEqualTo(cleaned);
    }

    @Test
    void auditCapturesMetadataAndStructureChanges() throws Exception {
        var before = mapper.readTree(mockMvc.perform(get(EXAM).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(patch(EXAM).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated name\",\"expectedRevision\":" + before.path("revision").asLong() + "}"))
                .andExpect(status().isOk());
        entityManager.flush();
        String id = "00000000-0000-7000-8700-000000000001";
        var metadataAudit = mapper.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM shared.outbox_event WHERE topic='audit' "
                        + "AND payload->>'action'='EXAM.UPDATE' AND payload->>'targetId'=?",
                String.class, id));
        assertThat(metadataAudit.path("before").path("name").asText()).isEqualTo(before.path("name").asText());
        assertThat(metadataAudit.path("after").path("name").asText()).isEqualTo("Updated name");

        var structure = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = structure.path("parts").deepCopy();
        var part = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0);
        String partId = part.path("id").asText();
        part.put("name", "Updated part");
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", structure.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        entityManager.flush();
        var structureAudit = mapper.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM shared.outbox_event WHERE topic='audit' "
                        + "AND payload->>'action'='EXAM.STRUCTURE_UPDATE' AND payload->>'targetId'=?",
                String.class, id));
        assertThat(structureAudit.path("before").path("structureHash").asText()).isNotBlank();
        assertThat(structureAudit.path("after").path("structureHash").asText())
                .isNotBlank().isNotEqualTo(structureAudit.path("before").path("structureHash").asText());
        assertThat(structureAudit.path("before").path("changedPartIds").toString()).contains(partId);
        assertThat(structureAudit.path("after").path("changedPartIds").toString()).contains(partId);
    }
}
