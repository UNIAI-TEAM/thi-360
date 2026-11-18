package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void reviewWithoutScaleTakesTheEnteredScaleAsBothMaximums() throws Exception {
        for (String kind : new String[] {"VACT", "HSA"}) {
            JsonNode review = create("{\"kind\":\"" + kind + "\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\","
                    + "\"buildMode\":\"REVIEW\",\"durationMinutes\":45");
            assertThat(review.path("maxScore").isNull()).as(kind).isTrue();
            assertThat(review.path("referenceMax").isNull()).as(kind).isTrue();
            assertThat(review.path("practiceScoring").asText()).as(kind).isEqualTo("LINEAR");
            JsonNode edited = patchExam(review, "{\"maxScore\":40");
            assertThat(edited.path("maxScore").decimalValue()).as(kind).isEqualByComparingTo("40");
            assertThat(edited.path("referenceMax").decimalValue()).as(kind).isEqualByComparingTo("40");
            JsonNode again = patchExam(edited, "{\"maxScore\":25");
            assertThat(again.path("maxScore").decimalValue()).as(kind).isEqualByComparingTo("25");
            assertThat(again.path("referenceMax").decimalValue()).as(kind).isEqualByComparingTo("25");
        }
    }

    @Test
    void loadingATemplateAcrossStandardAndReviewResetsTheScaleToFreshCreateValues() throws Exception {
        JsonNode exam = create("{\"kind\":\"HSA\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\"");
        String standardTemplate = exam.path("templateCode").asText();
        assertThat(exam.path("buildMode").asText()).isEqualTo("STANDARD");
        assertThat(exam.path("practiceScoring").asText()).isEqualTo("RAW");
        JsonNode review = loadTemplate(exam, "HSA_REVIEW");
        assertThat(review.path("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(review.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(review.path("maxScore").isNull()).isTrue();
        assertThat(review.path("referenceMax").isNull()).isTrue();
        JsonNode scaled = patchExam(review, "{\"maxScore\":40");
        assertThat(scaled.path("referenceMax").decimalValue()).isEqualByComparingTo("40");
        JsonNode back = loadTemplate(scaled, standardTemplate);
        assertThat(back.path("buildMode").asText()).isEqualTo("STANDARD");
        assertThat(back.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(back.path("maxScore")).isEqualTo(exam.path("maxScore"));
        assertThat(back.path("referenceMax")).isEqualTo(exam.path("referenceMax"));
    }

    @Test
    void loadingStandardTemplateWithoutProfileRejectsWithoutChangingReview() throws Exception {
        JsonNode review = create("{\"kind\":\"HSA\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                + "\"buildMode\":\"REVIEW\"");
        String id = review.path("id").asText();
        entityManager.flush();
        // A restored or repaired database can keep the exam and its catalog item without the profile row.
        entityManager.createNativeQuery("DELETE FROM exam.exam_kind_profile WHERE exam_item_id = "
                + "(SELECT kind_item_id FROM exam.exam WHERE id = :id)").setParameter("id", UUID.fromString(id))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        JsonNode before = detail(id);
        JsonNode structureBefore = structure(id);
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"HSA_SUBJECT_TOAN\",\"confirmReplace\":true,"
                                + "\"overviewPolicy\":\"USE_TEMPLATE\",\"expectedRevision\":"
                                + structureBefore.path("revision").asLong() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_SCOPE_NOT_ALLOWED"));
        entityManager.flush();
        entityManager.clear();
        JsonNode after = detail(id);
        assertThat(after.path("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(after.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(after.path("maxScore").isNull()).isTrue();
        assertThat(after.path("referenceMax").isNull()).isTrue();
        assertThat(after.path("templateCode")).isEqualTo(before.path("templateCode"));
        assertThat(after.path("revision")).isEqualTo(before.path("revision"));
        assertThat(structure(id)).isEqualTo(structureBefore);
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

    @Test
    void aReviewCannotBeSwitchedToRawScoring() throws Exception {
        JsonNode review = create("{\"kind\":\"HSA\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                + "\"buildMode\":\"REVIEW\",\"maxScore\":40");
        assertThat(review.path("practiceScoring").asText()).isEqualTo("LINEAR");
        patchRejected(review, "{\"practiceScoring\":\"RAW\"");
        assertReviewKept(review.path("id").asText());
        // Also when the same request changes the scope but stays a review (SUBJECT keeps the build mode).
        patchRejected(review, "{\"subjectCode\":\"ANH\",\"practiceScoring\":\"RAW\",\"confirmReplace\":true");
        assertReviewKept(review.path("id").asText());
    }

    @Test
    void rawStaysAllowedWhenTheRequestMakesTheExamStandard() throws Exception {
        JsonNode review = create("{\"kind\":\"HSA\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                + "\"buildMode\":\"REVIEW\",\"maxScore\":40");
        JsonNode standard = patchExam(review, "{\"scope\":\"FULL\",\"practiceScoring\":\"RAW\","
                + "\"confirmReplace\":true");
        assertThat(standard.path("buildMode").asText()).isEqualTo("STANDARD");
        assertThat(standard.path("practiceScoring").asText()).isEqualTo("RAW");
    }

    private void assertReviewKept(String id) throws Exception {
        entityManager.flush();
        entityManager.clear();
        JsonNode kept = detail(id);
        assertThat(kept.path("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(kept.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(kept.path("maxScore").decimalValue()).isEqualByComparingTo("40");
        assertThat(kept.path("referenceMax").decimalValue()).isEqualByComparingTo("40");
    }

    /** PATCH with the current revision that must be refused as an invalid request; {@code fields} is unterminated. */
    private void patchRejected(JsonNode exam, String fields) throws Exception {
        entityManager.flush();
        entityManager.clear();
        String id = exam.path("id").asText();
        mockMvc.perform(patch("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content(fields + ",\"expectedRevision\":" + detail(id).path("revision").asLong() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
    }

    private JsonNode loadTemplate(JsonNode exam, String templateCode) throws Exception {
        entityManager.flush();
        entityManager.clear();
        String id = exam.path("id").asText();
        long revision = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"" + templateCode + "\",\"confirmReplace\":true,"
                                + "\"overviewPolicy\":\"USE_TEMPLATE\",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
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

    @Test
    void convertingRawToLinearKeepsTheStoredReference() throws Exception {
        JsonNode exam = create("{\"kind\":\"HSA\",\"scope\":\"FULL\"");
        assertThat(exam.path("practiceScoring").asText()).isEqualTo("RAW");
        // A RAW exam whose scale equals its reference: reachable by sending the reference alone.
        JsonNode equal = patchExam(exam, "{\"referenceMax\":" + exam.path("maxScore").decimalValue().toPlainString());
        assertThat(equal.path("referenceMax").decimalValue())
                .isEqualByComparingTo(equal.path("maxScore").decimalValue());
        JsonNode converted = patchExam(equal, "{\"practiceScoring\":\"LINEAR\",\"confirmScoring\":true,"
                + "\"maxScore\":120");
        assertThat(converted.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(converted.path("maxScore").decimalValue()).isEqualByComparingTo("120");
        assertThat(converted.path("referenceMax").decimalValue())
                .isEqualByComparingTo(equal.path("referenceMax").decimalValue());
        // Once the exam is LINEAR with scale equal to reference, a later scale edit moves both.
        JsonNode synced = patchExam(patchExam(converted, "{\"referenceMax\":120"), "{\"maxScore\":90");
        assertThat(synced.path("referenceMax").decimalValue()).isEqualByComparingTo("90");
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
