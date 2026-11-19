package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2754: a subject answer-type quota may carry a QUESTION_SET ("Bộ câu hỏi") row. With the row the
 * child units of a QUESTION_SET group count only toward it; without it they keep counting toward
 * their own answer type (compatibility rule), so existing exams behave as before.
 */
@Transactional
class ExamAnswerQuotaQuestionSetIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID TOAN = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final List<String> ANSWER_QUOTA_ISSUES = List.of("ANSWER_TYPE_SHORTAGE",
            "ANSWER_QUOTA_TOTAL_MISMATCH", "ANSWER_QUOTA_DUPLICATE", "SUBJECT_QUOTA_INFEASIBLE", "SET_SHORTAGE");
    private static final String WITH_ROW = """
            {"unit":"COUNT","items":[{"answerType":"SINGLE_CHOICE","value":32},
            {"answerType":"SHORT_ANSWER","value":15},{"answerType":"QUESTION_SET","value":3}]}""";
    private static final String WITHOUT_ROW = """
            {"unit":"COUNT","items":[{"answerType":"SINGLE_CHOICE","value":35},
            {"answerType":"SHORT_ANSWER","value":15}]}""";

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private record Subject(UUID id, String code) { }

    private Subject subject() {
        UUID id = UUID.randomUUID();
        String code = "QS_QUOTA_" + id.toString().replace("-", "");
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Quota subject', 'Quota subject', 0, 1, false, 1, ?)
                """, id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return new Subject(id, code);
    }

    private UUID single(Subject subject, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,?,'READY',?,1,1)", id, "Q-" + id, type, subject.id());
        // INTEGER is accepted by the QDA short-answer block (INTEGER / DECIMAL / FRACTION) and by blocks without a format.
        String body = type.equals("SHORT_ANSWER")
                ? "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"INTEGER\",\"answers\":[\"42\"]}"
                : "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},"
                        + "{\"id\":\"b\",\"content\":\"B\"}]}";
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"content\":\"q\",\"points\":1,\"body\":" + body + "}");
        return id;
    }

    /**
     * One playable question set with {@code size} single-choice children; the Toán set group takes three. The parent
     * snapshot embeds each child with its body, as the check reads it, so the set is a complete playable unit.
     */
    private List<UUID> set(Subject subject, int size) {
        UUID parent = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'QUESTION_SET','READY',?,1,?)", parent, "SET-" + parent, subject.id(), size);
        String childBody = "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},"
                + "{\"id\":\"b\",\"content\":\"B\"}]}";
        List<UUID> ids = new ArrayList<>();
        List<String> children = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            UUID child = UUID.randomUUID();
            ids.add(child);
            children.add("{\"id\":\"" + child + "\",\"content\":\"child\",\"points\":1,\"body\":" + childBody + "}");
            jdbc.update("insert into questionbank.question(id, code, type, status, parent_id, position, points) "
                    + "values (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "CHILD-" + child, parent, i);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                    child, "{\"content\":\"child\",\"points\":1,\"body\":" + childBody + "}");
        }
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                parent, "{\"content\":\"set\",\"points\":" + size + ",\"body\":{\"type\":\"QUESTION_SET\",\"passage\":\"passage\","
                        + "\"children\":[" + String.join(",", children) + "]}}");
        return ids;
    }

    /** One cloze question with two blanks (two counting units). */
    private void cloze(Subject subject) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'CLOZE','READY',?,1,2)", id, "CLOZE-" + id, subject.id());
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"content\":\"cloze\",\"points\":2,\"body\":{\"type\":\"CLOZE\",\"passage\":\"<p>[[a]] and [[b]]</p>\","
                        + "\"blanks\":[{\"id\":\"a\",\"answers\":[\"one\"],\"points\":1},{\"id\":\"b\",\"answers\":[\"two\"],\"points\":1}]}}");
    }

    /** An isolated pool of exactly 32 single-choice, 15 short-answer questions and one set of three. */
    private Subject toanPool() {
        Subject subject = subject();
        for (int i = 0; i < 32; i++) single(subject, "SINGLE_CHOICE");
        for (int i = 0; i < 15; i++) single(subject, "SHORT_ANSWER");
        set(subject, 3);
        return subject;
    }

    /**
     * Retires the seeded Toán stock in this transaction and seeds exactly the 50 units a HSA / QDA Toán template asks
     * for (32 single-choice, 15 short-answer INTEGER, one set of three children), with grades {@code g10 / g11 / g12}
     * and levels NB 2 / TH 21 / VD 20 / VDC 7, so the template's own matrices have exactly matching ready stock.
     */
    private void templateToanStock(int g10, int g11, int g12) {
        Subject toan = new Subject(TOAN, "TOAN");
        jdbc.update("update questionbank.question set status = 'RETIRED' where subject_item_id = ? and parent_id is null "
                + "and status = 'READY'", TOAN);
        List<UUID> units = new ArrayList<>();
        for (int i = 0; i < 32; i++) units.add(single(toan, "SINGLE_CHOICE"));
        for (int i = 0; i < 15; i++) units.add(single(toan, "SHORT_ANSWER"));
        units.addAll(set(toan, 3));
        int[] gradeCounts = {g10, g11, g12};
        int[] levelCounts = {2, 21, 20, 7};
        int grade = 0, level = 0, gradeUsed = 0, levelUsed = 0;
        for (UUID unit : units) {
            while (gradeUsed == gradeCounts[grade]) { grade++; gradeUsed = 0; }
            while (levelUsed == levelCounts[level]) { level++; levelUsed = 0; }
            jdbc.update("update questionbank.question set grade_item_id = ?, level_item_id = ? where id = ?",
                    UUID.fromString("00000000-0000-7000-8200-00000000030" + (grade + 1)),
                    UUID.fromString("00000000-0000-7000-8200-00000000040" + (level + 1)), unit);
            gradeUsed++;
            levelUsed++;
        }
    }

    /** A SUBJECT exam of the given kind with its Toán template loaded through the normal template application. */
    private UUID examFromTemplate(String kind, String template) throws Exception {
        var request = mapper.createObjectNode().put("kind", kind).put("scope", "SUBJECT")
                .put("subjectCode", "TOAN").put("buildMode", "STANDARD").put("name", "Template acceptance")
                .put("code", "T-" + UUID.randomUUID().toString().substring(0, 8)).put("durationMinutes", 60);
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var load = mapper.createObjectNode().put("templateCode", template).put("confirmReplace", true)
                .put("expectedRevision", exam.path("revision").asLong());
        mockMvc.perform(post("/api/v1/admin/exams/" + exam.path("id").asText() + "/structure/template")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(load.toString()))
                .andExpect(status().isOk());
        return UUID.fromString(exam.path("id").asText());
    }

    private JsonNode template(String code) throws Exception {
        return mapper.readTree(jdbc.queryForObject("select structure::text from exam.exam_template where code = ?",
                String.class, code));
    }

    /**
     * The Toán part of a real template, moved onto an isolated subject. Matrices and scopes are dropped (the isolated
     * questions carry no grade or level); answer quotas, block types, counts, answer formats and the set group stay as
     * the template declares them. {@code quotas} replaces the template's answer quotas when not null.
     */
    private String toanPart(String templateCode, Subject subject, String quotas) throws Exception {
        ArrayNode parts = (ArrayNode) template(templateCode).path("parts");
        for (JsonNode candidate : parts) {
            for (JsonNode raw : candidate.path("sections")) {
                if (!"TOAN".equals(raw.path("subjectCode").asText())) continue;
                ObjectNode part = candidate.deepCopy();
                part.put("id", UUID.randomUUID().toString());
                part.put("questionCount", 50);
                part.putObject("participation").put("mode", "ALL");
                part.putArray("options");
                part.remove("displaySegments");
                ArrayNode sections = part.putArray("sections");
                ObjectNode section = raw.deepCopy();
                section.put("id", UUID.randomUUID().toString());
                section.put("subjectCode", subject.code());
                section.put("questionCount", 50);
                section.remove("matrix");
                section.remove("knowledgeScope");
                section.putArray("topicQuotas");
                if (quotas != null) section.set("answerQuotas", mapper.readTree(quotas));
                for (JsonNode blockNode : section.path("blocks")) {
                    ObjectNode block = (ObjectNode) blockNode;
                    block.put("id", UUID.randomUUID().toString());
                    block.remove("matrix");
                    block.remove("knowledgeScope");
                    block.remove("itemShape");
                    block.putArray("topicQuotas");
                }
                sections.add(section);
                return "[" + part + "]";
            }
        }
        throw new AssertionError("No Toán section in template " + templateCode);
    }

    private UUID exam(String parts) {
        return exam(parts, 50);
    }

    private UUID exam(String parts, int questionCount) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes) "
                + "values (?, ?, ?, 'Quota', 'FULL', 'SHARED', 75)", id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts + "}");
        jdbc.update("update exam.exam set question_count = ? where id = ?", questionCount, id);
        return id;
    }

    private JsonNode check(UUID examId) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", examId)
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode preview(UUID examId) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static Set<String> codes(JsonNode result) {
        return new java.util.HashSet<>(result.path("groups").findValuesAsText("code"));
    }

    private static JsonNode row(JsonNode rows, String key, String value) {
        for (JsonNode row : rows) {
            if (value.equals(row.path(key).asText())) return row;
        }
        throw new AssertionError("Missing row " + key + "=" + value + " in " + rows);
    }

    private static boolean hasRow(JsonNode rows, String key, String value) {
        for (JsonNode row : rows) {
            if (value.equals(row.path(key).asText())) return true;
        }
        return false;
    }

    private void assertNoAnswerQuotaIssue(JsonNode result) {
        assertThat(codes(result)).as("check issues: %s", codes(result)).doesNotContainAnyElementsOf(ANSWER_QUOTA_ISSUES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HSA_FULL", "HSA_PART_MATH", "HSA_SUBJECT_TOAN", "QDA_FULL", "QDA_PART_MATH", "QDA_SUBJECT_TOAN"})
    void toanTemplatesCarryTheQuestionSetRow(String code) throws Exception {
        // Was SINGLE_CHOICE 35 / SHORT_ANSWER 15: the 35 counted the three set children. UNI-2754 splits them out.
        int sections = 0;
        for (JsonNode part : template(code).path("parts")) {
            for (JsonNode section : part.path("sections")) {
                if (!"TOAN".equals(section.path("subjectCode").asText())) continue;
                sections++;
                JsonNode quotas = section.path("answerQuotas");
                assertThat(quotas.path("unit").asText()).as(code).isEqualTo("COUNT");
                List<String> items = new ArrayList<>();
                quotas.path("items").forEach(item -> items.add(item.path("answerType").asText() + "=" + item.path("value").asInt()));
                assertThat(items).as(code).containsExactly("SINGLE_CHOICE=32", "SHORT_ANSWER=15", "QUESTION_SET=3");
                JsonNode setGroup = null;
                for (JsonNode block : section.path("blocks")) {
                    if ("QUESTION_SET".equals(block.path("questionType").asText())) setGroup = block;
                }
                assertThat(setGroup).as(code + " set group").isNotNull();
                // BA spec 12 is unchanged: the children are still filtered as SINGLE_CHOICE.
                assertThat(setGroup.path("childAnswerType").asText()).as(code).isEqualTo("SINGLE_CHOICE");
            }
        }
        assertThat(sections).as(code + " Toán sections").isEqualTo(1);
    }

    /**
     * The migrated preset applied through the normal template application, with ready stock that matches its own
     * matrices: the check is VALID, the draft generates, and the set counts only in the Bộ câu hỏi row.
     */
    @ParameterizedTest
    @CsvSource({"HSA,3,20,27", "QDA,5,15,30"})
    void appliedToanTemplateChecksValidAndGeneratesWithSetCountedInItsOwnRow(String kind, int g10, int g11, int g12)
            throws Exception {
        templateToanStock(g10, g11, g12);
        UUID examId = examFromTemplate(kind, kind + "_SUBJECT_TOAN");

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).as("check issues: %s", codes(result)).isEqualTo("VALID");
        assertNoAnswerQuotaIssue(result);
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        assertThat(row(answers, "answerType", "QUESTION_SET").path("required").asInt()).isEqualTo(3);
        assertThat(row(answers, "answerType", "QUESTION_SET").path("achieved").asInt()).isEqualTo(3);

        JsonNode response = preview(examId);
        assertThat(response.path("status").asText()).as(response.toString()).isEqualTo("GENERATED");
        JsonNode compliance = response.path("compliance");
        JsonNode set = row(compliance, "criterion", "ANSWER_TYPE:QUESTION_SET");
        assertThat(set.path("required").asInt()).isEqualTo(3);
        assertThat(set.path("actual").asInt()).isEqualTo(3);
        assertThat(row(compliance, "criterion", "ANSWER_TYPE:SINGLE_CHOICE").path("actual").asInt()).isEqualTo(32);
        assertThat(row(compliance, "criterion", "ANSWER_TYPE:SINGLE_CHOICE").path("required").asInt()).isEqualTo(32);
        assertThat(row(compliance, "criterion", "ANSWER_TYPE:SHORT_ANSWER").path("actual").asInt()).isEqualTo(15);
        JsonNode allocation = response.path("paper").path("subjects").get(0).path("answerQuotas");
        assertThat(row(allocation, "answerType", "QUESTION_SET").path("achieved").asInt()).isEqualTo(3);
        assertThat(row(allocation, "answerType", "SINGLE_CHOICE").path("achieved").asInt()).isEqualTo(32);
    }

    @Test
    void checkReportsTheQuestionSetRowAchievedWhenTheSetGroupHasChildren() throws Exception {
        Subject subject = toanPool();
        UUID examId = exam(toanPart("HSA_SUBJECT_TOAN", subject, WITH_ROW));

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).as("check issues: %s", codes(result)).isEqualTo("VALID");
        assertNoAnswerQuotaIssue(result);
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        assertThat(row(answers, "answerType", "QUESTION_SET").path("required").asInt()).isEqualTo(3);
        assertThat(row(answers, "answerType", "QUESTION_SET").path("achieved").asInt()).isEqualTo(3);
        assertThat(row(answers, "answerType", "SINGLE_CHOICE").path("achieved").asInt()).isEqualTo(32);
    }

    /** A QUESTION_SET quota and a CLOZE quota in one subject: the two blanks stay in the CLOZE row. */
    @Test
    void checkKeepsClozeBlanksOutOfTheQuestionSetRow() throws Exception {
        Subject subject = subject();
        set(subject, 3);
        cloze(subject);
        UUID examId = exam("""
                [{"id":"p","questionCount":5,"participation":{"mode":"ALL"},"sections":[{"id":"s","subjectCode":"%s",
                  "questionCount":5,"answerQuotas":{"unit":"COUNT","items":[
                    {"answerType":"QUESTION_SET","value":3},{"answerType":"CLOZE","value":2}]},
                  "blocks":[
                    {"id":"set","name":"Set","questionType":"QUESTION_SET","questionCount":3,"setCount":1,"setSize":3,
                     "childAnswerType":"SINGLE_CHOICE"},
                    {"id":"cloze","name":"Cloze","questionType":"CLOZE","questionCount":2,"setCount":1,"setSize":2}]}]}]
                """.formatted(subject.code()), 5);

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).as("check issues: %s", codes(result)).isEqualTo("VALID");
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        assertThat(row(answers, "answerType", "QUESTION_SET").path("achieved").asInt()).isEqualTo(3);
        assertThat(row(answers, "answerType", "CLOZE").path("achieved").asInt()).isEqualTo(2);
    }

    @Test
    void questionSetRowWithoutASetGroupIsReportedAsShortage() throws Exception {
        Subject subject = subject();
        for (int i = 0; i < 35; i++) single(subject, "SINGLE_CHOICE");
        for (int i = 0; i < 15; i++) single(subject, "SHORT_ANSWER");
        // The same quotas, but the set group is gone: 35 + 15 standalone questions cannot fill a Bộ câu hỏi row.
        ObjectNode part = (ObjectNode) mapper.readTree(toanPart("HSA_SUBJECT_TOAN", subject, WITH_ROW)).get(0);
        ArrayNode blocks = (ArrayNode) part.path("sections").get(0).path("blocks");
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if ("QUESTION_SET".equals(blocks.get(i).path("questionType").asText())) blocks.remove(i);
        }
        ((ObjectNode) blocks.get(0)).put("questionCount", 35);
        UUID examId = exam("[" + part + "]");

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(codes(result)).as("check issues: %s", codes(result)).contains("ANSWER_TYPE_SHORTAGE");
    }

    /** A set group needs three children; the only set has four, so no eligible set can fill the Bộ câu hỏi row. */
    @Test
    void aWrongSizeSetDoesNotFillTheQuestionSetRow() throws Exception {
        Subject subject = subject();
        for (int i = 0; i < 32; i++) single(subject, "SINGLE_CHOICE");
        for (int i = 0; i < 15; i++) single(subject, "SHORT_ANSWER");
        set(subject, 4);
        UUID examId = exam(toanPart("HSA_SUBJECT_TOAN", subject, WITH_ROW));

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(codes(result)).as("check issues: %s", codes(result))
                .containsAnyOf("SET_SHORTAGE", "ANSWER_TYPE_SHORTAGE", "SUBJECT_QUOTA_INFEASIBLE");
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        // Unknown (null) or capacity-bounded (0 eligible sets): never the raw stock of four wrong-size children.
        assertThat(row(answers, "answerType", "QUESTION_SET").path("achieved").asInt(0)).isLessThan(3);
    }

    /**
     * A flexible set group can supply its three children, but a standalone group in the same section is short, so
     * there is no witness paper. The Bộ câu hỏi row must report the capacity of the flexible group, not a false zero.
     */
    @Test
    void aFlexibleSetGroupThatCanSupplyItsChildrenIsNotReportedAsZeroWithoutAWitness() throws Exception {
        Subject subject = subject();
        set(subject, 3);
        UUID examId = exam("""
                [{"id":"p","questionCount":8,"participation":{"mode":"ALL"},"sections":[{"id":"s","subjectCode":"%s",
                  "questionCount":8,"answerQuotas":{"unit":"COUNT","items":[
                    {"answerType":"SINGLE_CHOICE","value":5},{"answerType":"QUESTION_SET","value":3}]},
                  "blocks":[
                    {"id":"flex","name":"Flex","questionType":"QUESTION_SET","questionCount":3,"childAnswerType":"SINGLE_CHOICE"},
                    {"id":"single","name":"Single","questionType":"SINGLE_CHOICE","questionCount":5}]}]}]
                """.formatted(subject.code()), 8);

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        JsonNode set = row(answers, "answerType", "QUESTION_SET");
        assertThat(set.path("required").asInt()).isEqualTo(3);
        assertThat(set.path("achieved").asInt(-1)).isEqualTo(3);
    }

    /** A one-set group takes one three-child set even when the bank holds two; a quota of six is not reachable. */
    @Test
    void theQuestionSetRowWithoutAWitnessIsBoundedBySetCountOfTheGroup() throws Exception {
        Subject subject = subject();
        set(subject, 3);
        set(subject, 3);
        UUID examId = exam("""
                [{"id":"p","questionCount":6,"participation":{"mode":"ALL"},"sections":[{"id":"s","subjectCode":"%s",
                  "questionCount":6,"answerQuotas":{"unit":"COUNT","items":[{"answerType":"QUESTION_SET","value":6}]},
                  "blocks":[
                    {"id":"set","name":"Set","questionType":"QUESTION_SET","questionCount":3,"setCount":1,"setSize":3,
                     "childAnswerType":"SINGLE_CHOICE"},
                    {"id":"single","name":"Single","questionType":"SINGLE_CHOICE","questionCount":3}]}]}]
                """.formatted(subject.code()), 6);

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        assertThat(row(answers, "answerType", "QUESTION_SET").path("required").asInt()).isEqualTo(6);
        assertThat(row(answers, "answerType", "QUESTION_SET").path("achieved").asInt(-1)).isEqualTo(3);
    }

    /**
     * Two flexible groups each need three children and share the same pool of one three-child set and one
     * oversized four-child set. Only the three-child set is eligible, so the no-witness row reports its three
     * children once, not six from counting the unusable set. R4-2.
     */
    @Test
    void overlappingFlexibleGroupsDoNotCountSharedStockTwice() throws Exception {
        Subject subject = subject();
        set(subject, 3);
        set(subject, 4);
        UUID examId = exam("""
                [{"id":"p","questionCount":6,"participation":{"mode":"ALL"},"sections":[{"id":"s","subjectCode":"%s",
                  "questionCount":6,"answerQuotas":{"unit":"COUNT","items":[{"answerType":"QUESTION_SET","value":6}]},
                  "blocks":[
                    {"id":"flex1","name":"Flex 1","questionType":"QUESTION_SET","questionCount":3,"childAnswerType":"SINGLE_CHOICE"},
                    {"id":"flex2","name":"Flex 2","questionType":"QUESTION_SET","questionCount":3,"childAnswerType":"SINGLE_CHOICE"}]}]}]
                """.formatted(subject.code()), 6);

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        assertThat(row(answers, "answerType", "QUESTION_SET").path("required").asInt()).isEqualTo(6);
        assertThat(row(answers, "answerType", "QUESTION_SET").path("achieved").asInt(-1)).isEqualTo(3);
    }

    /**
     * The no-witness row must never size its work after the authored count: a flexible group that asks for
     * Integer.MAX_VALUE children gets a normal shortage response, and a large count stays bounded by the
     * eligible stock. The quota row must equal the section count or the row set is not rendered at all
     * (COUNT quotas resolve only when they sum to the count), so the authored count itself is the required
     * value. R4-1 / R4-3.
     */
    @Test
    void anUnboundedFlexibleCountStillAnswersTheStructuredShortage() throws Exception {
        Subject empty = subject();
        UUID emptyExam = exam("""
                [{"id":"p","questionCount":%2$d,"participation":{"mode":"ALL"},"sections":[{"id":"s","subjectCode":"%1$s",
                  "questionCount":%2$d,"answerQuotas":{"unit":"COUNT","items":[{"answerType":"QUESTION_SET","value":%2$d}]},
                  "blocks":[
                    {"id":"flex","name":"Flex","questionType":"QUESTION_SET","questionCount":%2$d,"childAnswerType":"SINGLE_CHOICE"}]}]}]
                """.formatted(empty.code(), Integer.MAX_VALUE), Integer.MAX_VALUE);

        JsonNode emptyResult = check(emptyExam);
        assertThat(emptyResult.path("status").asText()).as(emptyResult.toString()).isEqualTo("INVALID");
        JsonNode emptyAnswers = emptyResult.findValue("answerQuotas");
        assertThat(emptyAnswers).as(emptyResult.toString()).isNotNull();
        JsonNode emptyRow = row(emptyAnswers, "answerType", "QUESTION_SET");
        assertThat(emptyRow.path("required").asInt()).isEqualTo(Integer.MAX_VALUE);
        assertThat(emptyRow.path("achieved").asInt(-1)).isEqualTo(0);

        Subject sparse = subject();
        set(sparse, 3);
        UUID sparseExam = exam("""
                [{"id":"p","questionCount":%2$d,"participation":{"mode":"ALL"},"sections":[{"id":"s","subjectCode":"%1$s",
                  "questionCount":%2$d,"answerQuotas":{"unit":"COUNT","items":[{"answerType":"QUESTION_SET","value":%2$d}]},
                  "blocks":[
                    {"id":"flex","name":"Flex","questionType":"QUESTION_SET","questionCount":%2$d,"childAnswerType":"SINGLE_CHOICE"}]}]}]
                """.formatted(sparse.code(), 1_000_000), 1_000_000);

        JsonNode sparseResult = check(sparseExam);
        assertThat(sparseResult.path("status").asText()).as(sparseResult.toString()).isEqualTo("INVALID");
        JsonNode sparseAnswers = sparseResult.findValue("answerQuotas");
        assertThat(sparseAnswers).as(sparseResult.toString()).isNotNull();
        JsonNode sparseRow = row(sparseAnswers, "answerType", "QUESTION_SET");
        assertThat(sparseRow.path("required").asInt()).isEqualTo(1_000_000);
        assertThat(sparseRow.path("achieved").asInt(-1)).isEqualTo(3);
    }

    @Test
    void subjectWithoutTheRowKeepsCountingSetChildrenAsTheirOwnType() throws Exception {
        Subject subject = toanPool();
        UUID examId = exam(toanPart("HSA_SUBJECT_TOAN", subject, WITHOUT_ROW));

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).as("check issues: %s", codes(result)).isEqualTo("VALID");
        assertNoAnswerQuotaIssue(result);

        JsonNode response = preview(examId);
        assertThat(response.path("status").asText()).as(response.toString()).isEqualTo("GENERATED");
        JsonNode compliance = response.path("compliance");
        // 32 standalone + 3 set children, exactly as before the Bộ câu hỏi row existed.
        assertThat(row(compliance, "criterion", "ANSWER_TYPE:SINGLE_CHOICE").path("required").asInt()).isEqualTo(35);
        assertThat(row(compliance, "criterion", "ANSWER_TYPE:SINGLE_CHOICE").path("actual").asInt()).isEqualTo(35);
        assertThat(row(compliance, "criterion", "ANSWER_TYPE:SHORT_ANSWER").path("actual").asInt()).isEqualTo(15);
        assertThat(hasRow(compliance, "criterion", "ANSWER_TYPE:QUESTION_SET")).isFalse();
        JsonNode allocation = response.path("paper").path("subjects").get(0).path("answerQuotas");
        assertThat(row(allocation, "answerType", "SINGLE_CHOICE").path("achieved").asInt()).isEqualTo(35);
    }

    @Test
    void qdaSubjectWithoutTheRowKeepsCountingSetChildrenAsTheirOwnType() throws Exception {
        Subject subject = toanPool();
        UUID examId = exam(toanPart("QDA_SUBJECT_TOAN", subject, WITHOUT_ROW));

        JsonNode result = check(examId);
        assertThat(result.path("status").asText()).as("check issues: %s", codes(result)).isEqualTo("VALID");
        assertNoAnswerQuotaIssue(result);
        JsonNode response = preview(examId);
        assertThat(response.path("status").asText()).as(response.toString()).isEqualTo("GENERATED");
        assertThat(row(response.path("compliance"), "criterion", "ANSWER_TYPE:SINGLE_CHOICE").path("actual").asInt())
                .isEqualTo(35);
    }
}
