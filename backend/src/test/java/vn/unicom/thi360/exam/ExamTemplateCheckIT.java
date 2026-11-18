package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamTemplateCheckIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;

    @Test
    void reviewTemplatesReportOnlyExpectedStructuralErrors() throws Exception {
        for (String kind : Set.of("HSA", "QDA", "BCA", "SP2E", "SPT")) {
            JsonNode result = check(create(kind, "SUBJECT", "TOAN", "REVIEW", kind + "_REVIEW"));
            var errors = result.path("groups").findValues("issues").stream()
                    .flatMap(list -> java.util.stream.StreamSupport.stream(list.spliterator(), false))
                    .filter(issue -> "ERROR".equals(issue.path("severity").asText()))
                    .map(issue -> issue.path("code").asText()).collect(Collectors.toSet());
            assertThat(errors).as(kind).containsExactlyInAnyOrder("GROUP_ANSWER_TYPE_MISSING", "PART_EMPTY");
        }
    }

    @Test
    void sp2eLiteratureWarnsForOmittedWritingButNotManualGrading() throws Exception {
        JsonNode result = check(create("SP2E", "SUBJECT", "VAN", "STANDARD", "SP2E_VAN_OLD"));
        var slotIssues = result.path("groups").findValues("issues").stream()
                .flatMap(list -> java.util.stream.StreamSupport.stream(list.spliterator(), false))
                .filter(issue -> "SLOT_SOURCE_SHORTAGE".equals(issue.path("code").asText())).toList();
        assertThat(slotIssues).isNotEmpty().allSatisfy(issue ->
                assertThat(issue.path("location").path("slotIndex").asInt()).isPositive());
        var codes = result.path("groups").findValuesAsText("code");
        assertThat(codes).contains("OMITTED_GROUPS_PRESENT").doesNotContain("MANUAL_GRADING_REQUIRED");
    }

    @Test
    void qdaSubjectFailuresNameAffectedCodes() throws Exception {
        JsonNode result = check(create("QDA", "FULL", null, "STANDARD", "QDA_FULL"));
        var issues = result.path("groups").findValues("issues").stream()
                .flatMap(list -> java.util.stream.StreamSupport.stream(list.spliterator(), false))
                .filter(issue -> "ANH".equals(issue.path("subjectCode").asText()))
                .filter(issue -> "ERROR".equals(issue.path("severity").asText())).toList();
        assertThat(issues).isNotEmpty().allSatisfy(issue ->
                assertThat(java.util.stream.StreamSupport.stream(issue.path("affectedCodes").spliterator(), false)
                        .map(JsonNode::asText)).contains("QD05"));
    }

    private String create(String kind, String scope, String subject, String mode, String template) throws Exception {
        var request = mapper.createObjectNode().put("kind", kind).put("scope", scope)
                .put("subjectCode", subject).put("buildMode", mode).put("name", "Template acceptance")
                .put("code", "T-" + UUID.randomUUID().toString().substring(0, 8)).put("durationMinutes", 60);
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String base = "/api/v1/admin/exams/" + exam.path("id").asText();
        var load = mapper.createObjectNode().put("templateCode", template).put("confirmReplace", true)
                .put("expectedRevision", exam.path("revision").asLong());
        mockMvc.perform(post(base + "/structure/template").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(load.toString()))
                .andExpect(status().isOk());
        return base;
    }

    private JsonNode check(String base) throws Exception {
        return mapper.readTree(mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
