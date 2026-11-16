package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamCheckApiIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper mapper;
    private UUID examId;
    private String url;

    @BeforeEach
    void createExam() {
        examId = UUID.randomUUID();
        url = "/api/v1/admin/exams/" + examId + "/check";
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) "
                        + "VALUES (?,?,?,'Check API','FULL','SHARED')", examId, examId.toString(), examId.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,'{\"parts\":[]}'::jsonb,'empty')", examId);
    }

    @Test
    void eachEndpointEnforcesItsPermissionBeforeDoingWork() throws Exception {
        String runUrl = url + "/runs/" + UUID.randomUUID();
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).header("Origin", "http://localhost:3001")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(runUrl)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isForbidden());
        mockMvc.perform(post(url).param("deep", "true").with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isForbidden());
        mockMvc.perform(get(runUrl).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_job WHERE exam_id=?", Integer.class, examId)).isZero();
    }

    @Test
    void synchronousCheckPersistsAndAuditsTheActualResponse() throws Exception {
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(content().string("null"));
        var response = mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.stale").value(false)).andExpect(jsonPath("$.groups.length()").value(8))
                .andReturn().getResponse();
        var saved = mapper.readTree(response.getContentAsString());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.examId").value(examId.toString()))
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.issueCount").value(saved.path("issueCount").asInt()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id=?", Integer.class, examId)).isEqualTo(1);
        entityManager.flush();
        String audit = jdbc.queryForObject("SELECT payload::text FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action'='EXAM.CHECK' AND payload->>'targetId'=?", String.class, examId.toString());
        var event = mapper.readTree(audit);
        assertThat(event.path("actorId").asText()).isNotBlank();
        assertThat(event.path("traceId").asText()).isNotBlank();
        assertThat(event.path("after").path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void openApiDescribesSyncAndBackgroundResults() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var paths = mapper.readTree(response.getContentAsString()).path("paths");
        var check = paths.path("/api/v1/admin/exams/{examId}/check");
        assertThat(check.path("post").path("responses").has("200")).isTrue();
        assertThat(check.path("post").path("responses").has("202")).isTrue();
        assertThat(paths.path("/api/v1/admin/exams/{examId}/check/runs/{runId}").path("get").path("responses").has("200")).isTrue();
    }
}
