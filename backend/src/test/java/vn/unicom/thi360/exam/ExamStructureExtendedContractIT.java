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
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-16 rows 3, 4 (saved structure and clone), 5, 6, 7 and 8 through the real HTTP boundary: the structure is
 * saved and read back from the database, and the validator's 422 answers carry the right field errors.
 */
@Transactional
class ExamStructureExtendedContractIT extends AbstractIntegrationTest {
    private static final String EXAM = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";

    @Autowired private ObjectMapper mapper;

    @Test
    void newFieldsAreSavedReadBackAndCopiedByDuplicate() throws Exception {
        JsonNode current = load(EXAM);
        ArrayNode parts = current.path("parts").deepCopy();
        ObjectNode part = (ObjectNode) parts.get(0);
        addNewFields(part);
        save(EXAM, current, parts, current.path("examQuotas")).andExpect(status().isOk());

        assertNewFields(load(EXAM).path("parts").get(0), "after save and read");

        String duplicate = mockMvc.perform(post(EXAM + "/duplicate").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Copy with new fields\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String copyId = mapper.readTree(duplicate).path("id").asText();
        JsonNode copy = load("/api/v1/admin/exams/" + copyId);
        assertNewFields(copy.path("parts").get(0), "after duplicate");
        assertThat(copy.path("parts").get(0).path("id").asText())
                .isNotEqualTo(part.path("id").asText());
    }

    @Test
    void structureWithoutNewFieldsIsSavedWithoutGainingAny() throws Exception {
        JsonNode current = load(EXAM);
        save(EXAM, current, current.path("parts"), current.path("examQuotas")).andExpect(status().isOk());

        JsonNode after = load(EXAM);
        assertThat(after.toString()).doesNotContain("audioRequirement", "displaySegments");
        assertThat(after.findValues("childConditions")).allSatisfy(conditions ->
                assertThat(conditions.findValues("answerType")).isEmpty());
        assertThat(fieldNames(after.path("parts"), new java.util.TreeSet<>()))
                .isEqualTo(fieldNames(current.path("parts"), new java.util.TreeSet<>()));
    }

    private static java.util.Set<String> fieldNames(JsonNode node, java.util.Set<String> names) {
        node.fieldNames().forEachRemaining(names::add);
        node.forEach(child -> fieldNames(child, names));
        return names;
    }

    @Test
    void segmentsWithAGapAnOverlapOrTheWrongTotalAreRejectedAtTheSegment() throws Exception {
        JsonNode current = load(EXAM);
        int count = current.path("parts").get(0).path("questionCount").asInt();
        BigDecimal points = current.path("parts").get(0).path("maxPoints").decimalValue();
        BigDecimal half = points.divide(BigDecimal.valueOf(2));
        int cut = count / 2;

        assertSegments(current, "GAP", segment("P1", 1, cut, half), segment("P2", cut + 2, count, half));
        assertSegments(current, "OVERLAP", segment("P1", 1, cut, half), segment("P2", cut, count, half));
        assertSegments(current, "SHORT", segment("P1", 1, cut, half), segment("P2", cut + 1, count - 1, half));
        assertSegments(current, "POINTS", segment("P1", 1, cut, half),
                segment("P2", cut + 1, count, half.add(BigDecimal.ONE)));
    }

    @Test
    void aMixedSetWhoseTypedConditionsDoNotCoverEveryPositionIsRejected() throws Exception {
        JsonNode current = load(EXAM);
        for (int[][] conditions : new int[][][] {
                {{1, 2}, {4, 8}}, {{1, 3}, {3, 8}}, {{1, 2}, {3, 7}}, {{1, 2}, {3, 9}}}) {
            ArrayNode parts = current.path("parts").deepCopy();
            ObjectNode block = mixedSet(conditions);
            ((ArrayNode) parts.get(0).path("sections").get(0).path("blocks")).add(block);
            save(EXAM, current, parts, current.path("examQuotas"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        }
        assertThat(load(EXAM).path("revision")).isEqualTo(current.path("revision"));
    }

    @Test
    void promptBasisIsAcceptedAtReferenceLevelForExamQuotas() throws Exception {
        JsonNode current = load(EXAM);
        ArrayNode quotas = mapper.createArrayNode().add(examQuota("PROMPT", "REFERENCE", mapper.createArrayNode()));
        save(EXAM, current, current.path("parts"), quotas).andExpect(status().isOk());

        JsonNode quota = load(EXAM).path("examQuotas").get(0);
        assertThat(quota.path("basis").asText()).isEqualTo("PROMPT");
        assertThat(quota.path("enforcement").asText()).isEqualTo("REFERENCE");
    }

    @Test
    void promptBasisWithRequiredIsRejectedForExamQuotas() throws Exception {
        JsonNode current = load(EXAM);
        ArrayNode subjects = mapper.createArrayNode().add("TOAN");
        ArrayNode quotas = mapper.createArrayNode().add(examQuota("PROMPT", "REQUIRED", subjects));
        save(EXAM, current, current.path("parts"), quotas).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
    }

    @Test
    void promptBasisIsAcceptedAtReferenceLevelForTopicQuotas() throws Exception {
        JsonNode current = load(EXAM);
        ArrayNode parts = current.path("parts").deepCopy();
        ((ObjectNode) parts.get(0).path("sections").get(0)).putArray("topicQuotas")
                .add(topicQuota("PROMPT", "REFERENCE"));
        save(EXAM, current, parts, current.path("examQuotas")).andExpect(status().isOk());

        JsonNode quota = load(EXAM).path("parts").get(0).path("sections").get(0).path("topicQuotas").get(0);
        assertThat(quota.path("basis").asText()).isEqualTo("PROMPT");
        assertThat(quota.path("enforcement").asText()).isEqualTo("REFERENCE");
    }

    @Test
    void promptBasisWithRequiredIsRejectedForTopicQuotas() throws Exception {
        JsonNode current = load(EXAM);
        ArrayNode parts = current.path("parts").deepCopy();
        ((ObjectNode) parts.get(0).path("sections").get(0)).putArray("topicQuotas")
                .add(topicQuota("PROMPT", "REQUIRED"));
        save(EXAM, current, parts, current.path("examQuotas")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
    }

    @Test
    void segmentsWithoutThePartTotalsOrWithABlankHeadingAreRejectedAtTheSegment() throws Exception {
        JsonNode current = load(EXAM);
        for (String missing : new String[] {"questionCount", "maxPoints"}) {
            ArrayNode parts = current.path("parts").deepCopy();
            ObjectNode part = (ObjectNode) parts.get(0);
            addSegments(part);
            part.remove(missing);
            assertRejectedWithField(current, parts, "parts[0].displaySegments[1]",
                    "questionCount".equals(missing) ? "SEGMENT_COVERAGE" : "SEGMENT_POINTS");
        }
        ArrayNode parts = current.path("parts").deepCopy();
        ObjectNode part = (ObjectNode) parts.get(0);
        addSegments(part);
        ((ObjectNode) part.path("displaySegments").get(0)).put("name", " ");
        assertRejectedWithField(current, parts, "parts[0].displaySegments[0]", "SEGMENT_INVALID");
    }

    @Test
    void aMixedSetCannotAlsoDeclareAUniformChildTypeOrAnIllegalPositionalType() throws Exception {
        JsonNode current = load(EXAM);
        ArrayNode parts = current.path("parts").deepCopy();
        ObjectNode block = mixedSet(new int[][] {{1, 2}, {3, 8}});
        block.put("childAnswerType", "SINGLE_CHOICE");
        ((ArrayNode) parts.get(0).path("sections").get(0).path("blocks")).add(block);
        save(EXAM, current, parts, current.path("examQuotas")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));

        for (String illegal : new String[] {"QUESTION_SET", "CLOZE"}) {
            ArrayNode other = current.path("parts").deepCopy();
            ObjectNode set = mixedSet(new int[][] {{1, 2}, {3, 8}});
            ((ObjectNode) set.path("childConditions").get(1)).put("answerType", illegal);
            ((ArrayNode) other.get(0).path("sections").get(0).path("blocks")).add(set);
            save(EXAM, current, other, current.path("examQuotas")).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        }
        assertThat(load(EXAM).path("revision")).isEqualTo(current.path("revision"));
    }

    @Test
    void anInvalidNestedShapeOrNegativeOptionsAreRejectedAtTheWriteBoundary() throws Exception {
        JsonNode current = load(EXAM);
        for (String shape : new String[] {"nested statements", "nested options", "outer options"}) {
            ArrayNode parts = current.path("parts").deepCopy();
            ObjectNode block = mixedSet(new int[][] {{1, 2}, {3, 8}});
            switch (shape) {
                case "nested statements" -> ((ObjectNode) block.path("childConditions").get(0).path("itemShape"))
                        .put("statements", -1);
                case "nested options" -> ((ObjectNode) block.path("childConditions").get(1).path("itemShape"))
                        .put("options", -4);
                default -> ((ObjectNode) block.path("itemShape")).put("options", -1);
            }
            ((ArrayNode) parts.get(0).path("sections").get(0).path("blocks")).add(block);
            save(EXAM, current, parts, current.path("examQuotas")).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        }
        assertThat(load(EXAM).path("revision")).isEqualTo(current.path("revision"));
    }

    private void addSegments(ObjectNode part) {
        int count = part.path("questionCount").asInt();
        BigDecimal half = part.path("maxPoints").decimalValue().divide(BigDecimal.valueOf(2));
        int cut = count / 2;
        part.putArray("displaySegments").add(segment("P1", 1, cut, half)).add(segment("P2", cut + 1, count, half));
    }

    private void assertRejectedWithField(JsonNode current, ArrayNode parts, String field, String code)
            throws Exception {
        ResultActions result = save(EXAM, current, parts, current.path("examQuotas"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        JsonNode body = mapper.readTree(result.andReturn().getResponse().getContentAsString());
        assertThat(body.path("fieldErrors")).hasSize(1);
        assertThat(body.path("fieldErrors").get(0).path("field").asText()).isEqualTo(field);
        assertThat(body.path("fieldErrors").get(0).path("code").asText()).isEqualTo(code);
    }

    private void assertSegments(JsonNode current, String reason, ObjectNode... segments) throws Exception {
        ArrayNode parts = current.path("parts").deepCopy();
        ArrayNode list = ((ObjectNode) parts.get(0)).putArray("displaySegments");
        for (ObjectNode segment : segments) list.add(segment);
        ResultActions result = save(EXAM, current, parts, current.path("examQuotas"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        JsonNode body = mapper.readTree(result.andReturn().getResponse().getContentAsString());
        assertThat(body.path("fieldErrors")).as(reason).isNotEmpty();
        for (JsonNode error : body.path("fieldErrors")) {
            assertThat(error.path("field").asText()).as(reason).startsWith("parts[0].displaySegments[");
        }
    }

    private ObjectNode segment(String code, int from, int to, BigDecimal points) {
        return mapper.createObjectNode().put("code", code).put("name", code).put("fromPosition", from)
                .put("toPosition", to).put("commandCount", to - from + 1).put("maxPoints", points);
    }

    private ObjectNode mixedSet(int[][] ranges) {
        ObjectNode block = mapper.createObjectNode().put("id", UUID.randomUUID().toString())
                .put("name", "Bài đọc dùng chung").put("questionType", "QUESTION_SET")
                .put("setCount", 1).put("setSize", 8);
        ArrayNode conditions = block.putArray("childConditions");
        String[] types = {"TRUE_FALSE", "SINGLE_CHOICE"};
        for (int i = 0; i < ranges.length; i++) {
            ObjectNode condition = conditions.addObject().put("from", ranges[i][0]).put("to", ranges[i][1])
                    .put("answerType", types[i]);
            condition.putArray("nodeIds");
            condition.putObject("itemShape").put(i == 0 ? "statements" : "options", 4);
        }
        block.putObject("itemShape").put("options", 4);
        return block;
    }

    private void addNewFields(ObjectNode part) {
        int count = part.path("questionCount").asInt();
        BigDecimal half = part.path("maxPoints").decimalValue().divide(BigDecimal.valueOf(2));
        int cut = count / 2;
        part.putArray("displaySegments").add(segment("P1", 1, cut, half)).add(segment("P2", cut + 1, count, half));
        ObjectNode section = (ObjectNode) part.path("sections").get(0);
        section.put("audioRequirement", "REQUIRED");
        ((ArrayNode) section.path("blocks")).add(mixedSet(new int[][] {{1, 2}, {3, 8}}));
    }

    private void assertNewFields(JsonNode part, String stage) {
        JsonNode segments = part.path("displaySegments");
        assertThat(segments).as(stage + ": displaySegments").hasSize(2);
        assertThat(segments.get(0).path("code").asText()).as(stage).isEqualTo("P1");
        assertThat(segments.get(1).path("toPosition").asInt()).as(stage)
                .isEqualTo(part.path("questionCount").asInt());
        JsonNode section = part.path("sections").get(0);
        assertThat(section.path("audioRequirement").asText()).as(stage + ": audioRequirement").isEqualTo("REQUIRED");
        JsonNode blocks = section.path("blocks");
        JsonNode set = blocks.get(blocks.size() - 1);
        assertThat(set.path("itemShape").path("options").asInt()).as(stage + ": itemShape.options").isEqualTo(4);
        JsonNode conditions = set.path("childConditions");
        assertThat(conditions).as(stage + ": childConditions").hasSize(2);
        assertThat(conditions.get(0).path("answerType").asText()).as(stage).isEqualTo("TRUE_FALSE");
        assertThat(conditions.get(0).path("itemShape").path("statements").asInt()).as(stage).isEqualTo(4);
        assertThat(conditions.get(1).path("answerType").asText()).as(stage).isEqualTo("SINGLE_CHOICE");
        assertThat(conditions.get(1).path("itemShape").path("options").asInt()).as(stage).isEqualTo(4);
        assertThat(conditions.get(1).path("from").asInt()).as(stage).isEqualTo(3);
        assertThat(conditions.get(1).path("to").asInt()).as(stage).isEqualTo(8);
    }

    private ObjectNode examQuota(String basis, String enforcement, ArrayNode subjectCodes) {
        ObjectNode quota = mapper.createObjectNode().put("id", "eq").put("name", "Grade").put("dimension", "GRADE")
                .put("basis", basis).put("enforcement", enforcement);
        quota.putArray("buckets");
        quota.set("subjectCodes", subjectCodes);
        quota.putArray("excludeSubjectCodes");
        return quota;
    }

    private ObjectNode topicQuota(String basis, String enforcement) {
        ObjectNode quota = mapper.createObjectNode().put("id", "tq").put("name", "Topic").put("count", 1)
                .put("basis", basis).put("enforcement", enforcement);
        quota.putArray("nodeIds");
        return quota;
    }

    private JsonNode load(String examUrl) throws Exception {
        return mapper.readTree(mockMvc.perform(get(examUrl + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions save(String examUrl, JsonNode current, JsonNode parts, JsonNode examQuotas)
            throws Exception {
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", current.path("revision").asLong());
        request.set("parts", parts);
        request.set("examQuotas", examQuotas);
        return mockMvc.perform(put(examUrl + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()));
    }
}
