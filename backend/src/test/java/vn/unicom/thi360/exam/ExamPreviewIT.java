package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamPreviewIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private record Subject(UUID id, String code) {}

    private Subject subject() {
        UUID id = UUID.randomUUID();
        String code = "PREVIEW_" + id.toString().replace("-", "");
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Preview subject', 'Preview subject', 0, 1, false, 1, ?)
                """, id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return new Subject(id, code);
    }

    private UUID exam(String parts) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing) values (?, ?, ?, 'Preview', 'FULL', 'SHARED')",
                id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts + "}");
        return id;
    }

    private UUID question(Subject subject, String type, String content, boolean draft) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, working_version_no, points) values (?,?,?,'READY',?,1,?,1)",
                id, "P-" + id, type, subject.id(), draft ? 2 : null);
        String body = type.equals("SHORT_ANSWER")
                ? "{\"type\":\"SHORT_ANSWER\",\"answers\":[\"42\"]}"
                : "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true}]}";
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"content\":\"" + content + "\",\"points\":1,\"body\":" + body + "}");
        if (draft) jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,2,cast(? as jsonb))",
                id, "{\"content\":\"draft edit\",\"points\":1,\"body\":" + body + "}");
        return id;
    }

    private UUID set(Subject subject) {
        UUID parent = UUID.randomUUID();
        UUID single = UUID.randomUUID();
        UUID shortAnswer = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) values (?,?,'QUESTION_SET','READY',?,1,2)",
                parent, "SET-" + parent, subject.id());
        for (int i = 0; i < 2; i++) {
            UUID child = i == 0 ? single : shortAnswer;
            String type = i == 0 ? "SINGLE_CHOICE" : "SHORT_ANSWER";
            jdbc.update("insert into questionbank.question(id, code, type, status, parent_id, position, points) values (?,?,?,'READY',?,?,1)",
                    child, "CHILD-" + child, type, parent, i);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                    child, "{\"content\":\"child\",\"points\":1,\"body\":{\"type\":\"" + type + "\"}}");
        }
        String snapshot = "{\"content\":\"set\",\"points\":2,\"body\":{\"type\":\"QUESTION_SET\",\"passage\":\"passage\",\"children\":[{\"id\":\""
                + single + "\"},{\"id\":\"" + shortAnswer + "\"}]}}";
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))", parent, snapshot);
        return parent;
    }

    private String part(String subject, String blocks, String quotas) {
        return """
                [{"id":"part","order":1,"name":"Part","durationMinutes":30,"questionCount":50,
                  "participation":{"mode":"ALL"},"sections":[{"id":"section","subjectCode":"%s",
                  "questionCount":%s,"answerQuotas":%s,"blocks":%s}],"options":[]}]
                """.formatted(subject, quotas.equals("null") ? "1" : "50", quotas, blocks);
    }

    private JsonNode preview(UUID examId, String body) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview")
                .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GENERATED"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void draftEditNotServed() throws Exception {
        Subject subject = subject();
        UUID question = question(subject, "SINGLE_CHOICE", "serving", true);
        UUID examId = exam(part(subject.code(), "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]", "null"));
        String request = "{\"pathId\":\"default\",\"seed\":123}";
        JsonNode first = preview(examId, request);
        JsonNode item = first.path("paper").path("parts").get(0).path("blocks").get(0).path("items").get(0).path("question");
        assertThat(item.path("questionId").asText()).isEqualTo(question.toString());
        assertThat(item.path("content").asText()).isEqualTo("serving");
        assertThat(item.path("version").asInt()).isEqualTo(1);
        assertThat(preview(examId, request).path("paper")).isEqualTo(first.path("paper"));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and question_id=? and version_no=1 and kind='PREVIEW'", Integer.class, examId, question)).isEqualTo(1);
    }

    @Test
    void mathAnswerQuotas() throws Exception {
        Subject subject = subject();
        for (int i = 0; i < 34; i++) question(subject, "SINGLE_CHOICE", "single", false);
        for (int i = 0; i < 14; i++) question(subject, "SHORT_ANSWER", "short", false);
        UUID set = set(subject);
        String blocks = """
                [{"id":"single","name":"Singles","questionType":null,"questionCount":48},
                 {"id":"set","name":"Set","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2}]
                """;
        String quotas = "{\"unit\":\"COUNT\",\"items\":[{\"answerType\":\"SINGLE_CHOICE\",\"value\":35},{\"answerType\":\"SHORT_ANSWER\",\"value\":15}]}";
        UUID examId = exam(part(subject.code(), blocks, quotas));
        JsonNode result = preview(examId, "{\"pathId\":\"default\",\"seed\":42}");
        List<String> types = new ArrayList<>();
        for (JsonNode block : result.path("paper").path("parts").get(0).path("blocks")) {
            for (JsonNode item : block.path("items")) {
                if ("SET".equals(item.path("kind").asText())) {
                    for (JsonNode child : item.path("children")) types.add(child.path("type").asText());
                } else types.add(item.path("question").path("type").asText());
            }
        }
        assertThat(types.stream().filter("SINGLE_CHOICE"::equals).count()).isEqualTo(35);
        assertThat(types.stream().filter("SHORT_ANSWER"::equals).count()).isEqualTo(15);
        assertThat(result.path("paper").path("subjects").get(0).path("answerQuotas").get(0).path("achieved").asInt()).isEqualTo(35);
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'", Integer.class, examId)).isEqualTo(49);
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and question_id=? and kind='PREVIEW'", Integer.class, examId, set)).isEqualTo(1);
    }

    @Test
    void missingSubjects422AndPermissions() throws Exception {
        String structure = """
                [{"id":"p","order":1,"name":"Choice","participation":{"mode":"CHOICE","choose":1},"sections":[],
                  "options":[{"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":1,
                   "countsByPick":[1],"sections":[{"id":"ly","subjectCode":"LY","choiceGroup":"NATURAL","blocks":[]},
                   {"id":"hoa","subjectCode":"HOA","choiceGroup":"NATURAL","blocks":[]}]}]}]
                """;
        UUID examId = exam(structure);
        String path = "/api/v1/admin/exams/" + examId + "/preview";
        String body = "{\"pathId\":\"science\",\"seed\":1}";
        mockMvc.perform(post(path).header("Origin", "http://localhost:3001").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).with(asUser(Permissions.QUESTION_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
        mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathId\":\"science\",\"seed\":1,\"subjectCodes\":[\"NOT_A_SUBJECT\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
    }

    @Test
    void selectedSubjectsProduceSeededPaper() throws Exception {
        Subject a = subject();
        Subject b = subject();
        Subject c = subject();
        for (Subject subject : List.of(a, b, c)) {
            question(subject, "SINGLE_CHOICE", "first", false);
            question(subject, "SINGLE_CHOICE", "second", false);
        }
        String sections = """
                {"id":"a","subjectCode":"%s","blocks":[{"id":"ga","name":"A","questionType":"SINGLE_CHOICE"}]},
                {"id":"b","subjectCode":"%s","blocks":[{"id":"gb","name":"B","questionType":"SINGLE_CHOICE"}]},
                {"id":"c","subjectCode":"%s","blocks":[{"id":"gc","name":"C","questionType":"SINGLE_CHOICE"}]}
                """.formatted(a.code(), b.code(), c.code());
        UUID examId = exam("""
                [{"id":"p","order":1,"name":"Choice","participation":{"mode":"CHOICE","choose":1},"sections":[],
                  "options":[{"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":2,
                  "countsByPick":[2,1],"sections":[%s]}]}]
                """.formatted(sections));
        String request = "{\"pathId\":\"science\",\"seed\":42,\"subjectCodes\":[\"" + a.code()
                + "\",\"" + b.code() + "\"]}";
        JsonNode paper = preview(examId, request).path("paper");
        assertThat(paper.path("totalQuestions").asInt()).isEqualTo(3);
        assertThat(paper.path("subjects")).hasSize(2);
        assertThat(paper.path("subjects").get(0).path("subjectCode").asText()).isEqualTo(a.code());
        assertThat(paper.path("subjects").get(1).path("subjectCode").asText()).isEqualTo(b.code());
        assertThat(preview(examId, request).path("paper")).isEqualTo(paper);
    }

    @Test
    void infeasiblePreviewDoesNotWriteUsage() throws Exception {
        Subject subject = subject();
        UUID examId = exam(part(subject.code(),
                "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]", "null"));
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathId\":\"default\",\"seed\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREVIEW_GENERATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reason')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'subjectCode')]").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=?",
                Integer.class, examId)).isZero();
    }
}
