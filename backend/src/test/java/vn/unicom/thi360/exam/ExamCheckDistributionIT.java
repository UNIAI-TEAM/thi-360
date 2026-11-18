package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamCheckDistributionIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void bcaReferenceExamQuotaDeviationWarnsButStaysPublishable() throws Exception {
        JsonNode before = structure(bca());
        var request = mapper.createObjectNode();
        request.set("parts", before.path("parts"));
        request.put("expectedRevision", before.path("revision").asLong());
        request.set("examQuotas", mapper.readTree("""
                [{"id":"q-dev","name":"Impossible grade share","dimension":"GRADE",
                  "buckets":[{"codes":["G12"],"percent":1},{"codes":["G10"],"percent":99}],
                  "basis":"QUESTION","enforcement":"REFERENCE"}]
                """));
        save(bca(), request);

        JsonNode result = check(bca());
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(codes(result)).contains("EXAM_QUOTA_DEVIATION", "MANUAL_GRADING_REQUIRED");
        // Warnings never block publish: the MATRIX group reports WARN, never FAIL.
        assertThat(result.path("groups")).anyMatch(group ->
                "MATRIX".equals(group.path("group").asText()) && "WARN".equals(group.path("status").asText()));
        JsonNode issue = first(result, "EXAM_QUOTA_DEVIATION");
        assertThat(issue.path("severity").asText()).isEqualTo("WARNING");
        assertThat(issue.path("required").asInt()).isNotEqualTo(issue.path("available").asInt());
    }

    @Test
    void referenceGradeMatrixDeviationReportsRequiredAndActual() throws Exception {
        JsonNode before = structure(bca());
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) before.path("parts").deepCopy();
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        ObjectNode matrix = section.putObject("matrix");
        matrix.put("mode", "SEPARATE");
        matrix.put("unit", "COUNT");
        matrix.put("gradeEnforcement", "REFERENCE");
        matrix.put("difficultyEnforcement", "REFERENCE");
        matrix.putArray("byGrade").addObject().put("code", "G10").put("value", 1);
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        request.set("examQuotas", before.path("examQuotas"));
        save(bca(), request);

        JsonNode result = check(bca());
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(codes(result)).contains("DISTRIBUTION_DEVIATION");
        JsonNode issue = first(result, "DISTRIBUTION_DEVIATION");
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G10");
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("available").asInt()).isZero();
        // Required/Actual rows surface on the subject row of every proved path.
        for (var path : result.path("paths")) {
            for (var subject : path.path("subjects")) {
                if (!"s-bca-van".equals(subject.path("sectionId").asText())) continue;
                for (var row : subject.path("byGrade")) {
                    if (!"G10".equals(row.path("code").asText())) continue;
                    assertThat(row.path("required").asInt()).isEqualTo(1);
                    assertThat(row.path("achieved").asInt()).isZero();
                }
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outOfRangeChildBoundsAreLocatedInOrdinaryAndDeepChecks() throws Exception {
        UUID id = UUID.randomUUID();
        String url = "/api/v1/admin/exams/" + id + "/check";
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) VALUES (?,?,?,'Range','FULL','SHARED')",
                id, id.toString(), id.toString());
        try {
            String parts = """
                    {"parts":[{"id":"p","questionCount":1,"sections":[{"id":"s","subjectCode":"MATH",
                    "questionCount":1,"blocks":[{"id":"b","questionType":"QUESTION_SET","questionCount":1,
                    "setCount":1,"setSize":1,"childConditions":[{"from":1,"to":21}]}]}]}]}
                    """;
            jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (? ,?::jsonb,'range')", id, parts);
            assertIncomplete(check(id));
            String accepted = mockMvc.perform(post(url).param("deep", "true").with(asUser(Permissions.EXAM_MODIFY)))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            UUID runId = UUID.fromString(mapper.readTree(accepted).path("runId").asText());
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT state FROM exam.exam_check_job WHERE id=?", String.class, runId))
                            .isEqualTo("DONE"));
            String completed = mockMvc.perform(get(url + "/runs/" + runId).with(asUser(Permissions.EXAM_VIEW)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertIncomplete(mapper.readTree(completed).path("result"));
        } finally {
            jdbc.update("DELETE FROM exam.exam WHERE id=?", id);
        }
    }

    private void assertIncomplete(JsonNode result) {
        JsonNode issue = first(result, "SECTION_INCOMPLETE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(issue.path("location").path("partId").asText()).isEqualTo("p");
        assertThat(issue.path("location").path("sectionId").asText()).isEqualTo("s");
        assertThat(issue.path("location").path("blockId").asText()).isEqualTo("b");
    }

    private UUID bca() {
        return jdbc.queryForObject("select id from exam.exam where code = 'TEST-BCA-01'", UUID.class);
    }

    private JsonNode structure(UUID id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void save(UUID id, ObjectNode request) throws Exception {
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
    }

    private JsonNode check(UUID id) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private static Set<String> codes(JsonNode result) {
        return result.path("groups").findValuesAsText("code").stream().collect(Collectors.toSet());
    }

    private static JsonNode first(JsonNode result, String code) {
        for (var group : result.path("groups")) {
            for (var issue : group.path("issues")) {
                if (code.equals(issue.path("code").asText())) return issue;
            }
        }
        throw new AssertionError("missing issue " + code);
    }
}
