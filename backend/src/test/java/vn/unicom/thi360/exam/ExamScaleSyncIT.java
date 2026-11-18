package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** FIX-QA F4: a LINEAR exam whose scale equals its reference keeps them equal when the scale is edited. */
@Transactional
class ExamScaleSyncIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager entityManager;

    @Test
    void linearScaleEditMovesTheReferenceAndTheCheckSeesIt() throws Exception {
        JsonNode exam = create("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\",\"durationMinutes\":45");
        assertThat(exam.path("maxScore").decimalValue()).isEqualByComparingTo("300");
        assertThat(exam.path("referenceMax").decimalValue()).isEqualByComparingTo("300");
        JsonNode edited = patchExam(exam, "{\"maxScore\":50");
        assertThat(edited.path("maxScore").decimalValue()).isEqualByComparingTo("50");
        assertThat(edited.path("referenceMax").decimalValue()).isEqualByComparingTo("50");
        JsonNode issue = pointsTotalIssue(edited.path("id").asText());
        assertThat(issue.path("required").decimalValue()).isEqualByComparingTo("50");
        assertThat(issue.path("available").decimalValue()).isEqualByComparingTo("300");
    }

    @Test
    void aReferenceThatDiffersOnPurposeIsKept() throws Exception {
        JsonNode exam = create("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\",\"durationMinutes\":45");
        JsonNode differing = patchExam(exam, "{\"referenceMax\":100");
        assertThat(differing.path("maxScore").decimalValue()).isEqualByComparingTo("300");
        assertThat(differing.path("referenceMax").decimalValue()).isEqualByComparingTo("100");
        JsonNode edited = patchExam(differing, "{\"maxScore\":50");
        assertThat(edited.path("maxScore").decimalValue()).isEqualByComparingTo("50");
        assertThat(edited.path("referenceMax").decimalValue()).isEqualByComparingTo("100");
    }

    @Test
    void aRequestThatSendsTheReferenceIsHonoured() throws Exception {
        JsonNode exam = create("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\",\"durationMinutes\":45");
        JsonNode edited = patchExam(exam, "{\"maxScore\":50,\"referenceMax\":70");
        assertThat(edited.path("maxScore").decimalValue()).isEqualByComparingTo("50");
        assertThat(edited.path("referenceMax").decimalValue()).isEqualByComparingTo("70");
    }

    @Test
    void linearWithoutReferenceStaysWithoutReference() throws Exception {
        JsonNode review = create("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\","
                + "\"buildMode\":\"REVIEW\",\"durationMinutes\":45");
        assertThat(review.path("referenceMax").isNull()).isTrue();
        JsonNode edited = patchExam(review, "{\"maxScore\":40");
        assertThat(edited.path("maxScore").decimalValue()).isEqualByComparingTo("40");
        assertThat(edited.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(edited.path("referenceMax").isNull()).isTrue();
    }

    @Test
    void rawExamKeepsItsReference() throws Exception {
        JsonNode exam = create("{\"kind\":\"HSA\",\"scope\":\"FULL\"");
        assertThat(exam.path("practiceScoring").asText()).isEqualTo("RAW");
        JsonNode edited = patchExam(exam, "{\"maxScore\":120");
        assertThat(edited.path("maxScore").decimalValue()).isEqualByComparingTo("120");
        assertThat(edited.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(edited.path("referenceMax")).isEqualTo(exam.path("referenceMax"));
    }

    private JsonNode create(String head) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(head + ",\"name\":\"Scale\",\"code\":\"S-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** PATCH with the exam's current revision; {@code fields} is an unterminated JSON object. */
    private JsonNode patchExam(JsonNode exam, String fields) throws Exception {
        entityManager.flush();
        entityManager.clear();
        String id = exam.path("id").asText();
        long revision = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();
        return mapper.readTree(mockMvc.perform(patch("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content(fields + ",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode pointsTotalIssue(String id) throws Exception {
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return result.path("groups").findValues("issues").stream()
                .flatMap(rows -> java.util.stream.StreamSupport.stream(rows.spliterator(), false))
                .filter(issue -> "POINTS_TOTAL_MISMATCH".equals(issue.path("code").asText()))
                .findFirst().orElseThrow(() -> new AssertionError("no POINTS_TOTAL_MISMATCH in " + result));
    }
}
