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
                  "buckets":[{"codes":["G12"],"percent":1},{"codes":["G13"],"percent":99}],
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
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G13");
        assertThat(issue.path("required").asInt()).isGreaterThan(0);
        assertThat(issue.path("available").asInt()).isZero();
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
        matrix.putArray("byGrade").addObject().put("code", "G13").put("value", 999);
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        request.set("examQuotas", before.path("examQuotas"));
        save(bca(), request);

        JsonNode result = check(bca());
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(codes(result)).contains("DISTRIBUTION_DEVIATION");
        JsonNode issue = first(result, "DISTRIBUTION_DEVIATION");
        assertThat(issue.path("severity").asText()).isEqualTo("WARNING");
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G13");
        assertThat(issue.path("required").asInt()).isEqualTo(999);
        assertThat(issue.path("available").asInt()).isZero();
        // Required/Actual rows surface on the subject row of every proved path.
        for (var path : result.path("paths")) {
            for (var subject : path.path("subjects")) {
                if (!"s-bca-van".equals(subject.path("sectionId").asText())) continue;
                for (var row : subject.path("byGrade")) {
                    if (!"G13".equals(row.path("code").asText())) continue;
                    assertThat(row.path("required").asInt()).isEqualTo(999);
                    assertThat(row.path("achieved").asInt()).isZero();
                }
            }
        }
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
