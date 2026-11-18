package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-18 D17 through the exam check: an exam created from an H-SCA science template expects partial scoring on its
 * multiple-choice block, so a serving source question scored all-or-nothing is reported as an advisory
 * {@code SCORING_MISMATCH} at that block, naming the question.
 */
@Transactional
class HscaScoringMismatchCheckIT extends AbstractIntegrationTest {
    private static final String SUBJECT_GROUP = "00000000-0000-7000-8100-000000000002";

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anAllOrNothingMultipleChoiceSourceIsReportedAgainstTheHscaMultipleChoiceBlock() throws Exception {
        UUID subject = jdbc.queryForObject(
                "select id from catalog.catalog_item where group_id = ?::uuid and code = 'TOAN'",
                UUID.class, SUBJECT_GROUP);
        String mismatching = seedMultipleChoice(subject, "ALL_OR_NOTHING");
        String matching = seedMultipleChoice(subject, "PARTIAL");

        JsonNode exam = create();
        String examId = exam.path("id").asText();
        JsonNode structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", examId)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode block = null;
        for (JsonNode candidate : structure.path("parts").get(0).path("sections").get(0).path("blocks")) {
            if ("MULTIPLE_CHOICE".equals(candidate.path("questionType").asText())) block = candidate;
        }
        assertThat(block).as("the template's multiple-choice block").isNotNull();
        assertThat(block.path("expectedScoring").path("multipleChoiceRule").asText()).isEqualTo("PARTIAL");
        String blockId = block.path("id").asText();
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", examId)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<JsonNode> warnings = new ArrayList<>();
        for (JsonNode path : result.path("paths")) {
            for (JsonNode issue : path.path("issues")) {
                if ("SCORING_MISMATCH".equals(issue.path("code").asText())) warnings.add(issue);
            }
        }
        assertThat(warnings).as("one warning per path, at the multiple-choice block").isNotEmpty()
                .allSatisfy(issue -> {
                    assertThat(issue.path("severity").asText()).isEqualTo("WARNING");
                    assertThat(issue.path("blockId").asText()).isEqualTo(blockId);
                    List<String> codes = new ArrayList<>();
                    issue.path("questionCodes").forEach(code -> codes.add(code.asText()));
                    assertThat(codes).contains(mismatching).doesNotContain(matching);
                });
    }

    private String seedMultipleChoice(UUID subject, String rule) {
        UUID id = UUID.randomUUID();
        String code = "BE18-MC-" + rule + "-" + id.toString().substring(0, 8);
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, "
                + "points) values (?, ?, 'MULTIPLE_CHOICE', 'READY', ?, 1, 0.25)", id, code, subject);
        jdbc.update("insert into questionbank.question_version(question_id, version_no, snapshot) "
                        + "values (?, 1, ?::jsonb)", id,
                "{\"attachmentIds\":[],\"body\":{\"scoringRule\":\"" + rule + "\",\"options\":["
                        + "{\"content\":\"a\"},{\"content\":\"b\"},{\"content\":\"c\"},{\"content\":\"d\"}]}}");
        return code;
    }

    private JsonNode create() throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", "HSCA").put("scope", "SUBJECT")
                .put("subjectCode", "TOAN").put("durationMinutes", 90).put("name", "BE-18 HSCA TOAN")
                .put("code", "B18-" + UUID.randomUUID().toString().substring(0, 8));
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
