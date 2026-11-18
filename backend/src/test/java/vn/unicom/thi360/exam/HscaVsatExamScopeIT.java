package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-18 rows 3, 4, 6, 9, 10, 11, 12 and 16 through the HTTP boundary: creating exams of the H-SCA and V-SAT
 * kinds by scope, reloading their subject template onto a part exam, review exams, and the display-segment rule of
 * the structure PUT (H40 rule 4).
 */
@Transactional
class HscaVsatExamScopeIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;

    // ----------------------------------------------------------------------------------------- row 3 and 6

    @Test
    void hscaLiteratureWritingPartsAreNotPartExamsButTheReadingPartIs() throws Exception {
        for (String writing : List.of("P2", "P3")) {
            create("HSCA", "PART", writing, "VAN", 45, null).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_PART_NOT_FOUND"));
        }
        JsonNode reading = created(create("HSCA", "PART", "P1", "VAN", 45, null));
        assertThat(reading.path("questionCount").asInt()).isEqualTo(20);
        assertThat(reading.path("maxScore").decimalValue()).isEqualByComparingTo("20");
        assertThat(reading.path("parts")).hasSize(1);
        assertThat(reading.path("parts").get(0).path("sections").get(0).path("blocks")).hasSize(3);
    }

    @Test
    void sp2eLiteratureWritingPartKeepsBehavingAsBeforeBecauseItsTemplateHasNoLockFlag() throws Exception {
        JsonNode writing = created(create("SP2E", "PART", "P2", "VAN", 30, null));
        assertThat(writing.path("parts")).hasSize(1);
        assertThat(writing.path("questionCount").asInt()).isZero();
        assertThat(writing.path("parts").get(0).path("sections").get(0).path("blocks").get(0).path("omitted").asBoolean())
                .isTrue();
    }

    @Test
    void vsatLiteratureFullExamPassesThePartChecksAndItsWritingPartIsNotAPartExam() throws Exception {
        JsonNode exam = created(create("VSAT", "SUBJECT", null, "VAN", 90, null));
        assertThat(exam.path("questionCount").asInt()).isEqualTo(20);
        assertThat(exam.path("maxScore").decimalValue()).isEqualByComparingTo("120");
        assertThat(exam.path("parts")).hasSize(4);
        JsonNode check = check(exam.path("id").asText());
        assertThat(check.path("groups").findValuesAsText("code"))
                .doesNotContain("PART_EMPTY", "PART_TOTAL_MISMATCH", "PART_POINTS_MISMATCH");

        create("VSAT", "PART", "P4", "VAN", 30, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_NOT_FOUND"));
        for (String open : List.of("P1", "P2", "P3")) {
            created(create("VSAT", "PART", open, "VAN", 30, null));
        }
    }

    // ------------------------------------------------------------------------------------------------ row 4

    @Test
    void hscaEnglishCreatesAs75QuestionSubjectAndAsListeningOrReadingPart() throws Exception {
        JsonNode subject = created(create("HSCA", "SUBJECT", null, "ANH", 90, null));
        assertThat(subject.path("questionCount").asInt()).isEqualTo(75);
        assertThat(subject.path("maxScore").decimalValue()).isEqualByComparingTo("75");
        assertThat(subject.path("parts")).hasSize(2);
        JsonNode stored = structure(subject.path("id").asText());
        assertThat(audio(stored, 0)).isEqualTo("REQUIRED");
        assertThat(audio(stored, 1)).isEqualTo("FORBIDDEN");

        JsonNode listening = created(create("HSCA", "PART", "P1", "ANH", 40, null));
        assertThat(listening.path("questionCount").asInt()).isEqualTo(35);
        assertThat(listening.path("maxScore").decimalValue()).isEqualByComparingTo("35");
        assertThat(audio(structure(listening.path("id").asText()), 0)).isEqualTo("REQUIRED");
        JsonNode reading = created(create("HSCA", "PART", "P2", "ANH", 40, null));
        assertThat(reading.path("questionCount").asInt()).isEqualTo(40);
        assertThat(reading.path("maxScore").decimalValue()).isEqualByComparingTo("40");
        assertThat(audio(structure(reading.path("id").asText()), 0)).isEqualTo("FORBIDDEN");
    }

    // ------------------------------------------------------------------------------------------------ row 9

    @Test
    void aPartExamOfAnOrdinarySubjectNeedsADurationAndThenHoldsOnlyThatPart() throws Exception {
        for (String[] choice : new String[][] {{"HSCA", "P1", "TOAN"}, {"VSAT", "P2", "TOAN"}}) {
            create(choice[0], "PART", choice[1], choice[2], null, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("durationMinutes"))
                    .andExpect(jsonPath("$.fieldErrors[0].code").value("EXAM_DURATION_REQUIRED"));
        }
        JsonNode hsca = created(create("HSCA", "PART", "P1", "TOAN", 30, null));
        assertThat(hsca.path("parts")).hasSize(1);
        assertThat(hsca.path("parts").get(0).path("name").asText()).contains("Trắc nghiệm");
        assertThat(hsca.path("questionCount").asInt()).isEqualTo(25);
        assertThat(hsca.path("maxScore").decimalValue()).isEqualByComparingTo("6.25");
        assertThat(hsca.path("durationMinutes").asInt()).isEqualTo(30);
        JsonNode vsat = created(create("VSAT", "PART", "P2", "TOAN", 30, null));
        assertThat(vsat.path("parts")).hasSize(1);
        assertThat(vsat.path("questionCount").asInt()).isEqualTo(6);
        assertThat(vsat.path("maxScore").decimalValue()).isEqualByComparingTo("36");
    }

    // ----------------------------------------------------------------------------------------------- row 10

    @Test
    void vsatMathPartTwoTakesThreeSetsWithoutAnyLockMessageAndTheTotalRowFollows() throws Exception {
        JsonNode exam = created(create("VSAT", "PART", "P2", "TOAN", 30, null));
        String id = exam.path("id").asText();
        JsonNode current = structure(id);
        ArrayNode parts = current.path("parts").deepCopy();
        ObjectNode part = (ObjectNode) parts.get(0);
        ObjectNode section = (ObjectNode) part.path("sections").get(0);
        ObjectNode block = (ObjectNode) section.path("blocks").get(0);
        assertThat(block.path("setCount").asInt()).isEqualTo(2);
        block.put("setCount", 3).put("questionCount", 9);
        section.put("questionCount", 9);
        part.put("questionCount", 9).put("questionCountOnPaper", 9).put("questionCountRequired", 9).put("maxPoints", 54);
        save(id, current, parts).andExpect(status().isOk());

        assertThat(check(id).path("groups").findValuesAsText("code"))
                .doesNotContain("BLOCK_COUNT_LOCKED", "STRUCTURE_COUNT_LOCKED");
        JsonNode summary = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure/summary", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode total = summary.path("parts").get(0);
        assertThat(total.path("allocated").asInt()).as("questions").isEqualTo(9);
        assertThat(total.path("commandCount").asInt()).as("prompts").isEqualTo(9);
        assertThat(total.path("maxPoints").decimalValue()).as("points").isEqualByComparingTo("54");
    }

    // ----------------------------------------------------------------------------------------------- row 11

    @Test
    void aPartExamOfTheNewKindsReloadsItsSubjectTemplateWithoutATemplateMismatch() throws Exception {
        for (String[] choice : new String[][] {
                {"HSCA", "P1", "TOAN", "HSCA_SUBJECT_TOAN"}, {"HSCA", "P2", "ANH", "HSCA_SUBJECT_ANH"},
                {"VSAT", "P2", "TOAN", "VSAT_SUBJECT_TOAN"}, {"VSAT", "P3", "DIA", "VSAT_SUBJECT_DIA"}}) {
            JsonNode exam = created(create(choice[0], "PART", choice[1], choice[2], 30, null));
            loadTemplate(exam, choice[3]).andExpect(status().isOk())
                    .andExpect(jsonPath("$.templateCode").value(choice[3]));
            JsonNode reloaded = structure(exam.path("id").asText());
            assertThat(reloaded.path("parts")).as(choice[3]).hasSize(1);
            assertThat(reloaded.path("parts").get(0).path("name").asText()).as(choice[3])
                    .isEqualTo(exam.path("parts").get(0).path("name").asText());
        }
    }

    @Test
    void aPartExamOfAKindThatFindsItsTemplatesByKindStillCannotLoadASubjectTemplate() throws Exception {
        JsonNode exam = created(create("VACT", "PART", "LOGIC", null, 30, null));
        loadTemplate(exam, "VACT_SUBJECT_TOAN").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_MISMATCH"));
        JsonNode hsca = created(create("HSCA", "PART", "P1", "TOAN", 30, null));
        loadTemplate(hsca, "VSAT_SUBJECT_TOAN").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_MISMATCH"));
        loadTemplate(hsca, "HSCA_SUBJECT_LY").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_MISMATCH"));
    }

    // ----------------------------------------------------------------------------------------------- row 12

    @Test
    void reviewExamsOfBothNewKindsAreCreatedForTheChosenSubject() throws Exception {
        for (String[] choice : new String[][] {{"HSCA", "LY"}, {"VSAT", "DIA"}}) {
            JsonNode review = created(create(choice[0], "SUBJECT", null, choice[1], 45, "REVIEW"));
            assertThat(review.path("buildMode").asText()).isEqualTo("REVIEW");
            assertThat(review.path("templateCode").asText()).isEqualTo(choice[0] + "_REVIEW");
            assertThat(review.path("subjectCode").asText()).isEqualTo(choice[1]);
            assertThat(review.path("practiceScoring").asText()).isEqualTo("LINEAR");
            JsonNode stored = structure(review.path("id").asText());
            assertThat(stored.path("parts").get(0).path("sections").get(0).path("subjectCode").asText())
                    .isEqualTo(choice[1]);
        }
        create("HSCA", "PART", "P1", "TOAN", 30, "REVIEW").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
    }

    // ----------------------------------------------------------------------------------------------- row 16

    @Test
    void displaySegmentsOnAPartExamAreRejectedAtEachSegment() throws Exception {
        JsonNode exam = created(create("HSCA", "PART", "P1", "TOAN", 30, null));
        assertSegmentsRejected(exam.path("id").asText());
    }

    @Test
    void displaySegmentsOnAReviewExamAreRejectedAtEachSegment() throws Exception {
        JsonNode exam = created(create("HSCA", "SUBJECT", null, "TOAN", 45, "REVIEW"));
        assertSegmentsRejected(exam.path("id").asText());
    }

    @Test
    void aSubjectExamKeepsItsDisplaySegments() throws Exception {
        JsonNode exam = created(create("HSCA", "SUBJECT", null, "TOAN", 90, null));
        String id = exam.path("id").asText();
        JsonNode current = structure(id);
        ArrayNode parts = current.path("parts").deepCopy();
        addSegments((ObjectNode) parts.get(0));
        save(id, current, parts).andExpect(status().isOk());

        JsonNode segments = structure(id).path("parts").get(0).path("displaySegments");
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).path("toPosition").asInt()).isEqualTo(12);
        assertThat(segments.get(1).path("toPosition").asInt()).isEqualTo(25);
    }

    private void assertSegmentsRejected(String id) throws Exception {
        JsonNode current = structure(id);
        long revision = current.path("revision").asLong();
        ArrayNode parts = current.path("parts").deepCopy();
        ObjectNode part = (ObjectNode) parts.get(0);
        if (!part.hasNonNull("questionCount")) part.put("questionCount", 25);
        if (!part.hasNonNull("maxPoints")) part.put("maxPoints", new java.math.BigDecimal("6.25"));
        int count = part.path("questionCount").asInt();
        java.math.BigDecimal half = part.path("maxPoints").decimalValue().divide(java.math.BigDecimal.valueOf(2));
        ArrayNode segments = part.putArray("displaySegments");
        segments.add(segment("S1", 1, count / 2, half)).add(segment("S2", count / 2 + 1, count, half));

        String body = save(id, current, parts).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"))
                .andReturn().getResponse().getContentAsString();
        JsonNode errors = mapper.readTree(body).path("fieldErrors");
        assertThat(errors).hasSize(2);
        for (int i = 0; i < 2; i++) {
            assertThat(errors.get(i).path("field").asText()).isEqualTo("parts[0].displaySegments[" + i + "]");
            assertThat(errors.get(i).path("code").asText()).isEqualTo("SEGMENT_NOT_ALLOWED");
            assertThat(errors.get(i).path("message").asText()).isNotBlank().isNotEqualTo("SEGMENT_NOT_ALLOWED");
        }
        JsonNode after = structure(id);
        assertThat(after.path("revision").asLong()).as("nothing was saved").isEqualTo(revision);
        assertThat(after.path("parts").get(0).path("displaySegments")).isEmpty();
    }

    // ------------------------------------------------------------ ADV:BE-18:1 điểm 2

    @Test
    void aVsatPartExamDropsTheStrandQuotasButTheSubjectExamKeepsThem() throws Exception {
        JsonNode subject = created(create("VSAT", "SUBJECT", null, "DIA", 60, null));
        JsonNode quotas = structure(subject.path("id").asText())
                .path("parts").get(0).path("sections").get(0).path("topicQuotas");
        assertThat(quotas).hasSize(5);
        assertThat(quotas.findValuesAsText("basis")).containsOnly("PROMPT");

        JsonNode part = created(create("VSAT", "PART", "P1", "DIA", 30, null));
        assertThat(structure(part.path("id").asText())
                .path("parts").get(0).path("sections").get(0).path("topicQuotas")).isEmpty();
    }

    @Test
    void anOlderKindPartExamKeepsItsNonPromptTopicQuotas() throws Exception {
        JsonNode exam = created(create("SP2E", "PART", "P1", "TOAN", 30, null));
        String id = exam.path("id").asText();
        JsonNode current = structure(id);
        ArrayNode parts = current.path("parts").deepCopy();
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        section.putArray("topicQuotas").add(mapper.createObjectNode()
                .put("id", "sp2e-part-quota").put("name", "Chỉ tiêu tham khảo").put("count", 10)
                .put("basis", "QUESTION").put("enforcement", "REFERENCE"));
        save(id, current, parts).andExpect(status().isOk());

        JsonNode quotas = structure(id).path("parts").get(0).path("sections").get(0).path("topicQuotas");
        assertThat(quotas).hasSize(1);
        assertThat(quotas.get(0).path("id").asText()).isEqualTo("sp2e-part-quota");
    }

    @Test
    void aVsatPartExamStillSavesItsStructureWithoutStrandQuotas() throws Exception {
        JsonNode exam = created(create("VSAT", "PART", "P1", "DIA", 30, null));
        String id = exam.path("id").asText();
        JsonNode current = structure(id);
        ArrayNode parts = current.path("parts").deepCopy();
        ((ObjectNode) parts.get(0)).put("name", "Phần I đã đổi tên");
        save(id, current, parts).andExpect(status().isOk());
        assertThat(structure(id).path("parts").get(0).path("name").asText())
                .isEqualTo("Phần I đã đổi tên");
    }


    // ------------------------------------------------------------------------------------------------ helpers

    private void addSegments(ObjectNode part) {
        part.putArray("displaySegments").add(segment("S1", 1, 12, new java.math.BigDecimal("3")))
                .add(segment("S2", 13, 25, new java.math.BigDecimal("3.25")));
    }

    private ObjectNode segment(String code, int from, int to, java.math.BigDecimal points) {
        return mapper.createObjectNode().put("code", code).put("name", code).put("fromPosition", from)
                .put("toPosition", to).put("commandCount", to - from + 1).put("maxPoints", points);
    }

    private static String audio(JsonNode structure, int part) {
        return structure.path("parts").get(part).path("sections").get(0).path("audioRequirement").asText();
    }

    private ResultActions create(String kind, String scope, String partCode, String subjectCode, Integer duration,
            String buildMode) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", kind).put("scope", scope)
                .put("name", "BE-18 " + kind + " " + scope).put("code", "B18-" + UUID.randomUUID().toString().substring(0, 8));
        if (partCode != null) body.put("partCode", partCode);
        if (subjectCode != null) body.put("subjectCode", subjectCode);
        if (duration != null) body.put("durationMinutes", duration);
        if (buildMode != null) body.put("buildMode", buildMode);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private JsonNode created(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode structure(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions save(String id, JsonNode current, JsonNode parts) throws Exception {
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", current.path("revision").asLong());
        request.set("parts", parts);
        request.set("examQuotas", current.path("examQuotas"));
        return mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()));
    }

    private JsonNode check(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions loadTemplate(JsonNode exam, String templateCode) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("templateCode", templateCode).put("confirmReplace", true)
                .put("expectedRevision", exam.path("revision").asLong());
        return mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", exam.path("id").asText())
                .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }
}
