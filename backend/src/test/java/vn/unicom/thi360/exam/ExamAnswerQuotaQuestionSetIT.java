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

    private void single(Subject subject, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,?,'READY',?,1,1)", id, "Q-" + id, type, subject.id());
        String body = type.equals("SHORT_ANSWER")
                ? "{\"type\":\"SHORT_ANSWER\",\"answers\":[\"42\"]}"
                : "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},"
                        + "{\"id\":\"b\",\"content\":\"B\"}]}";
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"content\":\"q\",\"points\":1,\"body\":" + body + "}");
    }

    /** One question set with three single-choice children, the shape of the HSA/QDA Toán set group. */
    private void set(Subject subject) {
        UUID parent = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'QUESTION_SET','READY',?,1,3)", parent, "SET-" + parent, subject.id());
        List<String> children = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID child = UUID.randomUUID();
            children.add("{\"id\":\"" + child + "\"}");
            jdbc.update("insert into questionbank.question(id, code, type, status, parent_id, position, points) "
                    + "values (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "CHILD-" + child, parent, i);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                    child, "{\"content\":\"child\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\","
                            + "\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\"}]}}");
        }
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                parent, "{\"content\":\"set\",\"points\":3,\"body\":{\"type\":\"QUESTION_SET\",\"passage\":\"passage\","
                        + "\"children\":[" + String.join(",", children) + "]}}");
    }

    /** An isolated pool of exactly 32 single-choice, 15 short-answer questions and one set of three. */
    private Subject toanPool() {
        Subject subject = subject();
        for (int i = 0; i < 32; i++) single(subject, "SINGLE_CHOICE");
        for (int i = 0; i < 15; i++) single(subject, "SHORT_ANSWER");
        set(subject);
        return subject;
    }

    private JsonNode template(String code) throws Exception {
        return mapper.readTree(jdbc.queryForObject("select structure::text from exam.exam_template where code = ?",
                String.class, code));
    }

    /**
     * The Toán part of a real template, moved onto an isolated subject. Matrices, scopes and the QDA short-answer
     * format filter (the isolated questions carry no answer format) are dropped; answer quotas, block types, counts
     * and the set group stay as the template declares them.
     * {@code quotas} replaces the template's answer quotas when not null.
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
                    block.putArray("answerFormats");
                    block.putArray("topicQuotas");
                }
                sections.add(section);
                return "[" + part + "]";
            }
        }
        throw new AssertionError("No Toán section in template " + templateCode);
    }

    private UUID exam(String parts) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes) "
                + "values (?, ?, ?, 'Quota', 'FULL', 'SHARED', 75)", id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts + "}");
        jdbc.update("update exam.exam set question_count = 50 where id = ?", id);
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

    @ParameterizedTest
    @ValueSource(strings = {"HSA_SUBJECT_TOAN", "QDA_SUBJECT_TOAN"})
    void toanTemplateChecksAndGeneratesWithSetCountedInItsOwnRow(String code) throws Exception {
        Subject subject = toanPool();
        UUID examId = exam(toanPart(code, subject, null));

        assertNoAnswerQuotaIssue(check(examId));

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
        assertNoAnswerQuotaIssue(result);
        JsonNode answers = result.findValue("answerQuotas");
        assertThat(answers).as(result.toString()).isNotNull();
        assertThat(row(answers, "answerType", "QUESTION_SET").path("required").asInt()).isEqualTo(3);
        assertThat(row(answers, "answerType", "QUESTION_SET").path("achieved").asInt()).isEqualTo(3);
        assertThat(row(answers, "answerType", "SINGLE_CHOICE").path("achieved").asInt()).isEqualTo(32);
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

    @Test
    void subjectWithoutTheRowKeepsCountingSetChildrenAsTheirOwnType() throws Exception {
        Subject subject = toanPool();
        UUID examId = exam(toanPart("HSA_SUBJECT_TOAN", subject, WITHOUT_ROW));

        assertNoAnswerQuotaIssue(check(examId));

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

        assertNoAnswerQuotaIssue(check(examId));
        JsonNode response = preview(examId);
        assertThat(response.path("status").asText()).as(response.toString()).isEqualTo("GENERATED");
        assertThat(row(response.path("compliance"), "criterion", "ANSWER_TYPE:SINGLE_CHOICE").path("actual").asInt())
                .isEqualTo(35);
    }
}
