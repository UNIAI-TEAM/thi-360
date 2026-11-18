package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * R1-6 acceptance on the real seeded templates (BE-13): the unpublishable THPTQG Ngữ văn under every
 * build scope and the fixed-count locks of VACT_FULL, exercised through create, duplicate, PUT structure
 * and check/publish over HTTP. Plan section 2 rows 1-6 plus ADV:BE-15:2/3 (BE-15).
 */
@Transactional
class ExamCheckRealTemplatesIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    // ---- rows 1-2: real THPTQG Ngữ văn, every build scope (D11) ----------------------------------------

    @Test
    void thptqgLiteratureStandardPartAndReviewAllCarryThePublishableBan() throws Exception {
        String[] bodies = {
                "{\"kind\":\"THPTQG\",\"scope\":\"SUBJECT\",\"subjectCode\":\"VAN\","
                        + "\"name\":\"Van subject\",\"code\":\"%s\"}",
                "{\"kind\":\"THPTQG\",\"scope\":\"PART\",\"partCode\":\"P1\",\"subjectCode\":\"VAN\","
                        + "\"durationMinutes\":120,\"name\":\"Van part\",\"code\":\"%s\"}",
                "{\"kind\":\"THPTQG\",\"scope\":\"SUBJECT\",\"subjectCode\":\"VAN\","
                        + "\"buildMode\":\"REVIEW\",\"name\":\"Van review\",\"code\":\"%s\"}",
        };
        for (String body : bodies) {
            JsonNode created = create(body);
            String base = "/api/v1/admin/exams/" + created.path("id").asText();

            JsonNode saved = saveDraft(base);
            assertThat(saved.path("revision")).isNotNull();

            JsonNode check = check(base);
            assertThat(check.path("status").asText()).as(body).isEqualTo("INVALID");
            JsonNode issue = issue(check, "TEMPLATE_NOT_PUBLISHABLE");
            assertThat(issue.path("subjectCode").asText()).isEqualTo("VAN");

            JsonNode refused = publishRefused(base);
            assertThat(refused.path("code").asText()).isEqualTo("PATH_NOT_VALID");
            assertThat(versionCount(created.path("id").asText())).isZero();
        }
    }

    @Test
    void seededTestThptqg02FailsWithTheTemplateBanWithoutAnOverride() throws Exception {
        // The seed itself marks THPTQG_LITERATURE_2025 publishable=false; no SQL override here on purpose.
        assertThat(jdbc.queryForObject(
                "SELECT publishable FROM exam.exam_template WHERE code = 'THPTQG_LITERATURE_2025'",
                Boolean.class)).isFalse();
        UUID id = jdbc.queryForObject(
                "SELECT id FROM exam.exam WHERE code_normalized = 'test-thptqg-02'", UUID.class);
        assertThat(id).isNotNull();
        String base = "/api/v1/admin/exams/" + id;

        JsonNode check = check(base);
        assertThat(check.path("status").asText()).isEqualTo("INVALID");
        assertThat(codes(check)).contains("TEMPLATE_NOT_PUBLISHABLE");

        JsonNode refused = publishRefused(base);
        assertThat(refused.path("code").asText()).isEqualTo("PATH_NOT_VALID");
        assertThat(versionCount(id.toString())).isZero();
    }

    // ---- rows 4-6: real VACT_FULL / VACT_PART_LOGIC -----------------------------------------------------

    @Test
    void vactFullLogicResizeIsSavedAndLockedAtTheLogicSection() throws Exception {
        String base = createVact("FULL", null, null, "VACT_FULL");
        JsonNode structure = structure(base);
        ObjectNode edited = structure.deepCopy();
        String logicId = logicSection(edited).path("id").asText();
        resize(logicSection(edited), 10, 5, 2);

        assertThat(putStructure(base, structure.path("revision").asLong(), edited).path("revision")).isNotNull();

        JsonNode check = check(base);
        assertThat(check.path("status").asText()).isEqualTo("INVALID");
        ObjectNode lock = lockAt(check, logicId);
        assertThat(lock.path("required").asInt()).isEqualTo(12);
        assertThat(lock.path("available").asInt()).isEqualTo(10);
    }

    @Test
    void vactFullDifficultyOnlyEditIsNotLocked() throws Exception {
        String base = createVact("FULL", null, null, "VACT_FULL");
        JsonNode structure = structure(base);
        ObjectNode edited = structure.deepCopy();
        ArrayNode byDifficulty = (ArrayNode) logicSection(edited).at("/matrix/byDifficulty");
        ((ObjectNode) byDifficulty.get(0)).put("value", 3);   // NB 1 -> 3
        ((ObjectNode) byDifficulty.get(1)).put("value", 2);   // TH 4 -> 2; total stays 12

        assertThat(putStructure(base, structure.path("revision").asLong(), edited).path("revision")).isNotNull();
        assertThat(codes(check(base))).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    @Test
    void vactPartLogicResizeIsSavedWithoutALock() throws Exception {
        String base = createVact("PART", "LOGIC", null, "VACT_PART_LOGIC");
        JsonNode structure = structure(base);
        ObjectNode edited = structure.deepCopy();
        ObjectNode section = (ObjectNode) edited.at("/parts/0/sections/0");
        assertThat(section.path("questionCount").asInt()).isEqualTo(12);
        resize(section, 10, 5, 2);

        assertThat(putStructure(base, structure.path("revision").asLong(), edited).path("revision")).isNotNull();
        // fixed_counts=false on the real template: a count edit is a normal edit, never a lock.
        assertThat(codes(check(base))).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    // ---- ADV:BE-15:2 cases and ADV:BE-15:3 tag case on a real VACT_FULL duplicate -----------------------

    @Test
    void duplicatedVactFullReorderedWithSwappedCountsLocksTheCopiedLogic() throws Exception {
        String base = duplicate(createVact("FULL", null, null, "VACT_FULL"));
        JsonNode structure = structure(base);
        ObjectNode edited = structure.deepCopy();
        String logicId = logicSection(edited).path("id").asText();
        resize(logicSection(edited), 18, 6, 3);
        resize(reasoningSection(edited), 12, 4, 3);
        swapThinkingSections(edited);

        assertThat(putStructure(base, structure.path("revision").asLong(), edited).path("revision")).isNotNull();

        JsonNode check = check(base);
        ObjectNode lock = lockAt(check, logicId);
        assertThat(lock.path("required").asInt()).isEqualTo(12);
        assertThat(lock.path("available").asInt()).isEqualTo(18);
        assertThat(lock.path("location").path("sectionId").asText()).isEqualTo(logicId);
    }

    @Test
    void duplicatedVactFullUnchangedOrOnlyReorderedIsNotLocked() throws Exception {
        String base = duplicate(createVact("FULL", null, null, "VACT_FULL"));
        JsonNode structure = structure(base);
        assertThat(codes(check(base))).doesNotContain("STRUCTURE_COUNT_LOCKED");

        ObjectNode edited = structure.deepCopy();
        swapThinkingSections(edited);
        assertThat(putStructure(base, structure.path("revision").asLong(), edited).path("revision")).isNotNull();
        assertThat(codes(check(base))).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    @Test
    void duplicatedVactFullWithoutTheLogicCompetencyFilterLocksAtThatSection() throws Exception {
        String base = duplicate(createVact("FULL", null, null, "VACT_FULL"));
        JsonNode structure = structure(base);
        ObjectNode edited = structure.deepCopy();
        ObjectNode logic = logicSection(edited);
        String logicId = logic.path("id").asText();
        logic.remove("knowledgeScope");

        assertThat(putStructure(base, structure.path("revision").asLong(), edited).path("revision")).isNotNull();

        JsonNode check = check(base);
        ObjectNode lock = lockAt(check, logicId);
        assertThat(lock.path("required").asInt()).isEqualTo(12);
        assertThat(lock.path("location").path("sectionId").asText()).isEqualTo(logicId);
    }

    @Test
    void duplicatedVactFullWithAQuestionTagOnTheLogicGroupIsNotLocked() throws Exception {
        // ADV:BE-15:3: QUESTION_TAG is a refinement, not part of the section identity.
        String base = duplicate(createVact("FULL", null, null, "VACT_FULL"));
        JsonNode structure = structure(base);
        ObjectNode edited = structure.deepCopy();
        ((ObjectNode) logicSection(edited).at("/blocks/0")).putArray("tagCodes").add("TD_XEP_HANG");

        assertThat(putStructure(base, structure.path("revision").asLong(), edited).path("revision")).isNotNull();
        assertThat(codes(check(base))).doesNotContain("STRUCTURE_COUNT_LOCKED");
    }

    // ---- HTTP helpers ---------------------------------------------------------------------------------

    private JsonNode create(String body) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("T-" + UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** Creates an exam whose default template is {@code expectedTemplate} and asserts the provenance. */
    private String createVact(String scope, String partCode, String subject, String expectedTemplate)
            throws Exception {
        StringBuilder request = new StringBuilder("{\"kind\":\"VACT\",\"scope\":\"").append(scope).append('"');
        if (partCode != null) request.append(",\"partCode\":\"").append(partCode).append('"');
        if (subject != null) request.append(",\"subjectCode\":\"").append(subject).append('"');
        if (!"FULL".equals(scope)) request.append(",\"durationMinutes\":60");
        request.append(",\"name\":\"V-ACT\",\"code\":\"%s\"}");
        JsonNode created = create(request.toString());
        assertThat(created.path("templateCode").asText()).isEqualTo(expectedTemplate);
        return "/api/v1/admin/exams/" + created.path("id").asText();
    }

    private String duplicate(String base) throws Exception {
        JsonNode copy = json(post(base + "/duplicate").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Copy\"}"), 201);
        String id = copy.path("id").asText();
        assertThat(id).isNotEqualTo(base.substring(base.lastIndexOf('/') + 1));
        return "/api/v1/admin/exams/" + id;
    }

    private JsonNode structure(String base) throws Exception {
        return json(get(base + "/structure").with(asUser(Permissions.EXAM_VIEW)), 200);
    }

    /** Saves the current structure back unchanged: the draft write path must stay open for these drafts. */
    private JsonNode saveDraft(String base) throws Exception {
        JsonNode structure = structure(base);
        return putStructure(base, structure.path("revision").asLong(), structure.deepCopy());
    }

    private JsonNode putStructure(String base, long revision, ObjectNode structure) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", revision);
        body.set("parts", structure.path("parts"));
        if (structure.has("examQuotas")) body.set("examQuotas", structure.path("examQuotas"));
        return json(put(base + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 200);
    }

    private JsonNode check(String base) throws Exception {
        return json(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
    }

    /** A valid publish request (every reported path): refusal must come from the check, not the request. */
    private JsonNode publishRefused(String base) throws Exception {
        JsonNode exam = json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200);
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", exam.path("revision").asLong());
        ArrayNode pathIds = body.putArray("pathIds");
        check(base).path("paths").forEach(path -> pathIds.add(path.path("pathId").asText()));
        return json(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422);
    }

    private JsonNode json(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return mapper.readTree(mockMvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    // ---- structure helpers ----------------------------------------------------------------------------

    /** Sections of the "Tư duy khoa học" part: Logic (VACT-TD-LOGIC) and Suy luận (VACT-TD-SL). */
    private static ArrayNode thinkingSections(ObjectNode structure) {
        for (JsonNode part : structure.path("parts")) {
            for (JsonNode section : part.path("sections")) {
                if (section.at("/knowledgeScope/competencyCodes/0").asText("").startsWith("VACT-TD-")) {
                    return (ArrayNode) part.path("sections");
                }
            }
        }
        throw new AssertionError("VACT_FULL thinking part not found");
    }

    private static ObjectNode sectionOf(ObjectNode structure, String competency) {
        for (JsonNode section : thinkingSections(structure)) {
            if (competency.equals(section.at("/knowledgeScope/competencyCodes/0").asText())) {
                return (ObjectNode) section;
            }
        }
        throw new AssertionError(competency + " section not found");
    }

    private static ObjectNode logicSection(ObjectNode structure) {
        return sectionOf(structure, "VACT-TD-LOGIC");
    }

    private static ObjectNode reasoningSection(ObjectNode structure) {
        return sectionOf(structure, "VACT-TD-SL");
    }

    private static void swapThinkingSections(ObjectNode structure) {
        ArrayNode sections = thinkingSections(structure);
        JsonNode first = sections.remove(0);
        sections.add(first);
    }

    /** Resizes a QUESTION_SET section so the declared count, block count and set shape stay consistent. */
    private static void resize(ObjectNode section, int questions, int setCount, int setSize) {
        section.put("questionCount", questions);
        ObjectNode block = (ObjectNode) section.at("/blocks/0");
        block.put("questionCount", questions).put("setCount", setCount).put("setSize", setSize);
        scale((ArrayNode) section.at("/matrix/byDifficulty"), questions);
    }

    private static void scale(ArrayNode values, int total) {
        int old = 0;
        for (JsonNode value : values) old += value.path("value").asInt();
        if (old == 0) return;
        int assigned = 0;
        for (int i = 0; i < values.size(); i++) {
            int value = i == values.size() - 1 ? total - assigned
                    : (int) Math.round(total * values.get(i).path("value").asDouble() / old);
            ((ObjectNode) values.get(i)).put("value", value);
            assigned += value;
        }
    }

    // ---- issue helpers --------------------------------------------------------------------------------

    private static List<String> codes(JsonNode result) {
        List<String> codes = new ArrayList<>();
        for (JsonNode list : result.path("groups").findValues("issues")) {
            for (JsonNode issue : list) codes.add(issue.path("code").asText());
        }
        return codes;
    }

    private static JsonNode issue(JsonNode result, String code) {
        return StreamSupport.stream(result.path("groups").findValues("issues").spliterator(), false)
                .flatMap(list -> StreamSupport.stream(list.spliterator(), false))
                .filter(node -> code.equals(node.path("code").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " missing in " + codes(result)));
    }

    private static ObjectNode lockAt(JsonNode result, String sectionId) {
        return (ObjectNode) StreamSupport.stream(result.path("groups").findValues("issues").spliterator(), false)
                .flatMap(list -> StreamSupport.stream(list.spliterator(), false))
                .filter(node -> "STRUCTURE_COUNT_LOCKED".equals(node.path("code").asText()))
                .filter(node -> sectionId.equals(node.path("location").path("sectionId").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no lock at " + sectionId + " in " + codes(result)));
    }

    private long versionCount(String examId) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?::uuid",
                Long.class, examId);
    }
}
