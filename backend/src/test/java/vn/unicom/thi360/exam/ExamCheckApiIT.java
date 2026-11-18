package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
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
        var latest = mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.examId").value(examId.toString()))
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.issueCount").value(saved.path("issueCount").asInt()))
                .andReturn().getResponse();
        var latestJson = mapper.readTree(latest.getContentAsString());
        assertThat(latestJson.has("sectionStates")).isTrue();
        assertThat(latestJson.path("sectionStates").isNull()).isTrue();
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
    void savedUnrepresentableDraftsReturnActionableSyncIssues() throws Exception {
        for (String[] fixture : draftFixtures()) {
            jdbc.update("UPDATE exam.exam_structure SET parts=?::jsonb WHERE exam_id=?", fixture[0], examId);
            entityManager.clear();
            String response = mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(response).path("groups").findValuesAsText("code")).contains(fixture[1]);
        }
    }

    /** BE-5 restores VALID per 2B H3. */
    @Test
    void seededHsaDevExamChecksOnlyLegacyRemainderIssues() throws Exception {
        UUID seeded = UUID.fromString("00000000-0000-7000-8700-000000000001");
        String response = mockMvc.perform(post("/api/v1/admin/exams/" + seeded + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = mapper.readTree(response);
        assertThat(result.path("status").asText()).isIn("VALID", "INVALID");
        // FU-BE4a-1: PART_TOTAL_MISMATCH and PATH_TOTAL_MISMATCH are interim; BE-5 subtracts the TrimPolicy drop.
        Set<String> allowed = Set.of("REMAINDER_GROUP_INVALID", "PART_POINTS_MISMATCH",
                "POINTS_TOTAL_MISMATCH", "CELL_SHORTAGE", "PART_TOTAL_MISMATCH", "PATH_TOTAL_MISMATCH");
        JsonNode sciencePath = null;
        String sciencePartId = null;
        for (JsonNode path : result.path("paths")) {
            for (JsonNode choice : path.path("choices")) {
                if ("SUBJECT_CHOICE".equals(choice.path("kind").asText())) {
                    assertThat(sciencePath).as("only one SUBJECT_CHOICE path").isNull();
                    sciencePath = path;
                    sciencePartId = choice.path("partId").asText();
                }
            }
        }
        assertThat(sciencePath).as("seeded HSA science path").isNotNull();
        assertThat(sciencePartId).isNotBlank();
        Set<String> scienceIssueKeys = new HashSet<>();
        for (JsonNode issueList : sciencePath.findValues("issues")) {
            for (JsonNode issue : issueList) scienceIssueKeys.add(issue.path("key").asText());
        }
        for (JsonNode path : result.path("paths")) {
            if (path != sciencePath) {
                for (JsonNode issueList : path.findValues("issues")) {
                    assertThat(issueList).as("non-science path must not contain issues").isEmpty();
                }
            }
        }
        for (JsonNode issueList : result.findValues("issues")) {
            for (JsonNode issue : issueList) {
                String code = issue.path("code").asText();
                assertThat(code).isIn(allowed);
                assertThat(scienceIssueKeys).contains(issue.path("key").asText());
                if ("POINTS_TOTAL_MISMATCH".equals(code) || "PATH_TOTAL_MISMATCH".equals(code)) {
                    // Exam-wide totals have no partId; constrain them to the observed H3 overcount.
                    assertThat(issue.path("partId").isNull()).isTrue();
                    assertThat(issue.path("required").decimalValue()).isEqualByComparingTo("150");
                    assertThat(issue.path("available").decimalValue()).isEqualByComparingTo("151");
                } else if ("PART_TOTAL_MISMATCH".equals(code) || "PART_POINTS_MISMATCH".equals(code)) {
                    // FU-BE4a-1: part totals include the untrimmed 17 + 17 + 17 until BE-5 applies the drop.
                    assertThat(issue.path("partId").asText()).isEqualTo(sciencePartId);
                    assertThat(issue.path("required").decimalValue()).isEqualByComparingTo("50");
                    assertThat(issue.path("available").decimalValue()).isEqualByComparingTo("51");
                } else {
                    assertThat(issue.path("partId").asText()).isEqualTo(sciencePartId);
                }
            }
        }
    }

    static String[][] draftFixtures() {
        String prefix = "{\"parts\":[{\"id\":\"p\",\"questionCount\":1,\"sections\":[{\"id\":\"s\",\"subjectCode\":\"MATH\",\"questionCount\":1,";
        String suffix = "}]}]}";
        return new String[][] {
            {prefix + "\"blocks\":[{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":0,\"setCount\":0,\"setSize\":2}]" + suffix, "SET_SIZE_MISMATCH"},
            {prefix + "\"blocks\":[{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":0,\"setCount\":1,\"setSize\":0}]" + suffix, "SET_SIZE_MISMATCH"},
            {prefix + "\"answerQuotas\":{\"unit\":\"COUNT\",\"items\":[{\"answerType\":\"SINGLE_CHOICE\",\"value\":1},{\"answerType\":\"SINGLE_CHOICE\",\"value\":0}]},\"blocks\":[{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]" + suffix, "ANSWER_QUOTA_DUPLICATE"}
        };
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
