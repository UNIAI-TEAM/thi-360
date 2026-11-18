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
    private static final UUID PREVIEW_KNOWLEDGE_LEAF = UUID.fromString("00000000-0000-7000-8200-000000001201");
    private static final String SEEDED_PREVIEW_CODE = "SEED-MATH-G10-NB-SC-001";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private record Subject(UUID id, String code) {}

    private UUID seededPreviewQuestion() {
        UUID id = jdbc.queryForObject("select id from questionbank.question where code = ?",
                UUID.class, SEEDED_PREVIEW_CODE);
        jdbc.update("""
                insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary)
                values (?, ?, true)
                on conflict (question_id, knowledge_item_id) do update set is_primary = excluded.is_primary
                """, id, PREVIEW_KNOWLEDGE_LEAF);
        return id;
    }


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
        jdbc.update("update exam.exam set question_count = (select coalesce(sum((p->>'questionCount')::integer),0) from jsonb_array_elements(cast(? as jsonb)) p) where id = ?", parts, id);
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
                [{"id":"part","order":1,"name":"Part","questionCount":%s,
                  "participation":{"mode":"ALL"},"sections":[{"id":"section","subjectCode":"%s",
                  "questionCount":%s,"answerQuotas":%s,"blocks":%s}],"options":[]}]
                """.formatted(quotas.equals("null") ? "1" : "50", subject, quotas.equals("null") ? "1" : "50", quotas, blocks);
    }

    private JsonNode preview(UUID examId, String body) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview")
                .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GENERATED"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void examTargetGeneratesSeededPreviewWithoutChoice() throws Exception {
        Subject subject = subject();
        question(subject, "SINGLE_CHOICE", "first", false);
        question(subject, "SINGLE_CHOICE", "second", false);
        UUID examId = exam(part(subject.code(),
                "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]", "null"));
        String request = "{\"target\":{\"kind\":\"EXAM\"},\"seed\":123}";
        JsonNode first = preview(examId, request).path("paper");
        assertThat(first.path("totalQuestions").asInt()).isEqualTo(1);
        assertThat(preview(examId, request).path("paper")).isEqualTo(first);
    }

    @Test
    void switchingBcaCombinationRegeneratesOnlyThirdPart() throws Exception {
        Subject first = subject();
        Subject second = subject();
        question(first, "SINGLE_CHOICE", "first", false);
        question(second, "SINGLE_CHOICE", "second", false);
        for (String code : List.of("LY", "HOA", "SINH", "DIA")) {
            UUID subjectId = jdbc.queryForObject("select id from catalog.catalog_item where group_id = ? and code_normalized = ?",
                    UUID.class, SUBJECT_GROUP, code.toLowerCase(Locale.ROOT));
            question(new Subject(subjectId, code), "SINGLE_CHOICE", code, false);
        }
        UUID examId = exam("""
                [{"id":"p1","order":1,"name":"One","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"g1","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                 {"id":"p2","order":2,"name":"Two","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s2","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"g2","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                 {"id":"p3","order":3,"name":"Three","questionCount":1,
                  "codeSource":{"source":"GLOBAL_LIST"},"subjectPool":[
                    {"id":"ly","subjectCode":"LY","questionCount":1,"blocks":[{"id":"gly","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"hoa","subjectCode":"HOA","questionCount":1,"blocks":[{"id":"ghoa","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"sinh","subjectCode":"SINH","questionCount":1,"blocks":[{"id":"gsinh","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"dia","subjectCode":"DIA","questionCount":1,"blocks":[{"id":"gdia","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]
                """.formatted(first.code(), second.code()));
        jdbc.update("update exam.exam set kind_item_id=(select id from catalog.catalog_item where code_normalized='bca' and group_id='00000000-0000-7000-8100-000000000001') where id=?", examId);
        JsonNode initial = preview(examId, "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"p3:CA1\"},\"seed\":5}");
        String secondRequest = "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"p3:CA2\"},\"seed\":6,"
                + "\"basePreviewId\":\"" + initial.path("previewId").asText() + "\",\"regeneratePartIds\":[\"p3\"]}";
        JsonNode regenerated = preview(examId, secondRequest);
        JsonNode oldParts = initial.path("paper").path("parts");
        JsonNode newParts = regenerated.path("paper").path("parts");
        assertThat(newParts.get(0)).isEqualTo(oldParts.get(0));
        assertThat(newParts.get(1)).isEqualTo(oldParts.get(1));
        assertThat(newParts.get(2).path("blocks").get(0).path("subjectCode").asText()).isEqualTo("HOA");
    }

    @Test
    void subjectChoiceSectionGeneratesSeventeenThenDropsOne() throws Exception {
        Subject physics = subject();
        Subject other = subject();
        for (int i = 0; i < 17; i++) question(physics, "SINGLE_CHOICE", "physics", false);
        UUID examId = exam("""
                [{"id":"science-part","order":1,"name":"Science","questionCount":50,
                  "participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"science","kind":"SUBJECT_CHOICE","chooseSubjects":2,"countsByPick":[17,16],
                     "sections":[
                       {"id":"physics","subjectCode":"%s","choiceGroup":"NATURAL",
                        "blocks":[{"id":"physics-group","questionType":"SINGLE_CHOICE","questionCount":17}]},
                       {"id":"other","subjectCode":"%s","choiceGroup":"SOCIAL",
                        "blocks":[{"id":"other-group","questionType":"SINGLE_CHOICE","questionCount":17}]}]}]}]
                """.formatted(physics.code(), other.code()));
        JsonNode result = preview(examId, "{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"physics\","
                + "\"sectionQuestionCount\":16},\"seed\":42}");
        assertThat(result.path("paper").path("totalQuestions").asInt()).isEqualTo(16);
        assertThat(result.path("droppedQuestions")).hasSize(1);
        String dropped = result.path("droppedQuestions").get(0).path("questionId").asText();
        assertThat(result.path("paper").path("parts").get(0).path("blocks").get(0).path("items"))
                .noneMatch(item -> dropped.equals(item.path("question").path("questionId").asText()));
    }

    @Test
    void expiredPreviewReturnsGoneAndKeepsPermissionChecks() throws Exception {
        UUID examId = UUID.randomUUID();
        String url = "/api/v1/admin/exams/" + examId + "/preview";
        String body = "{\"target\":{\"kind\":\"EXAM\"},\"basePreviewId\":\"" + UUID.randomUUID()
                + "\",\"regeneratePartIds\":[\"p1\"]}";
        mockMvc.perform(post(url).header("Origin", "http://localhost:3001").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).with(asUser(Permissions.QUESTION_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PREVIEW_EXPIRED"));
    }
    @Test
    void previewOfSeededChoiceExposesShuffleOptions() throws Exception {
        UUID seededQuestion = seededPreviewQuestion();
        String blocks = """
                [{"id":"g","name":"Group","questionType":"SINGLE_CHOICE","questionCount":1,
                  "knowledgeScope":{"nodeIds":["00000000-0000-7000-8200-000000001201"]}}]
                """;
        UUID examId = exam(part("TOAN", blocks, "null"));
        JsonNode paper = preview(examId, "{\"pathId\":\"default\",\"seed\":123}").path("paper");
        JsonNode question = paper.path("parts").get(0).path("blocks").get(0).path("items").get(0).path("question");
        String code = jdbc.queryForObject("select code from questionbank.question where id = ?",
                String.class, UUID.fromString(question.path("questionId").asText()));
        assertThat(UUID.fromString(question.path("questionId").asText())).isEqualTo(seededQuestion);
        assertThat(code).isEqualTo(SEEDED_PREVIEW_CODE);
        assertThat(question.path("body").path("options")).isNotEmpty();
        assertThat(question.path("body").path("shuffleOptions").asBoolean()).isTrue();
    }

    @Test
    void previewPreservesExplicitShuffleOptionsFalse() throws Exception {
        Subject subject = subject();
        UUID questionId = question(subject, "SINGLE_CHOICE", "keep order", false);
        jdbc.update("""
                update questionbank.question_version
                set snapshot = jsonb_set(snapshot, '{body,shuffleOptions}', 'false'::jsonb)
                where question_id = ? and version_no = 1
                """, questionId);
        UUID examId = exam(part(subject.code(),
                "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]", "null"));
        JsonNode body = preview(examId, "{\"pathId\":\"default\",\"seed\":123}")
                .path("paper").path("parts").get(0).path("blocks").get(0).path("items").get(0)
                .path("question").path("body");
        // Fails if preview rewrites an explicit false back to the default true or drops the field.
        assertThat(body.get("shuffleOptions")).as("explicit false must survive preview").isNotNull();
        assertThat(body.path("shuffleOptions").isBoolean()).isTrue();
        assertThat(body.path("shuffleOptions").asBoolean()).isFalse();
    }
    @Test
    void previewOmitsLegacyMatchingShuffleFlagsFromStoredSnapshot() throws Exception {
        Subject subject = subject();
        UUID questionId = question(subject, "MATCHING", "matching", false);
        jdbc.update("""
                update questionbank.question_version set snapshot = jsonb_set(snapshot, '{body}',
                    '{"type":"MATCHING","left":[],"right":[],"pairs":[],"reuseRight":false,
                      "shuffleLeft":true,"shuffleRight":false}'::jsonb)
                where question_id = ? and version_no = 1
                """, questionId);
        UUID examId = exam(part(subject.code(), "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"MATCHING\",\"questionCount\":1}]", "null"));
        JsonNode paper = preview(examId, "{\"pathId\":\"default\",\"seed\":123}").path("paper");
        JsonNode body = paper.path("parts").get(0).path("blocks").get(0).path("items").get(0).path("question").path("body");
        assertThat(body.path("type").asText()).isEqualTo("MATCHING");
        assertThat(body.has("shuffleLeft")).isFalse();
        assertThat(body.has("shuffleRight")).isFalse();
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
    void invalidSelectedPathNeverGeneratesOrRecordsUsage() throws Exception {
        Subject a = subject();
        Subject b = subject();
        question(a, "SINGLE_CHOICE", "available", false);
        String valid = part(a.code(), "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1,\"requiredPointsPerQuestion\":1}]", "null");
        UUID badPart = exam(valid.replaceFirst("\"questionCount\":1,", "\"questionCount\":2,"));
        UUID badScore = exam(valid);
        jdbc.update("update exam.exam set max_score=10 where id=?", badScore);
        UUID unconfirmed = exam("""
                [{"id":"p","name":"Choice","questionCount":1,"participation":{"mode":"CHOICE","choose":1},"sections":[],
                  "options":[{"id":"pick","kind":"SUBJECT_CHOICE","code":"PICK","expertConfirmed":false,
                    "chooseSubjects":1,"countsByPick":[1],"sections":[
                      {"id":"a","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"ga","questionType":"SINGLE_CHOICE"}]},
                      {"id":"b","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"gb","questionType":"SINGLE_CHOICE"}]}]}]}]
                """.formatted(a.code(), b.code()));
        for (var test : List.of(
                new String[]{badPart.toString(), "{\"pathId\":\"default\",\"seed\":1}", "PART_TOTAL_MISMATCH"},
                new String[]{badScore.toString(), "{\"pathId\":\"default\",\"seed\":1}", "POINTS_TOTAL_MISMATCH"},
                new String[]{unconfirmed.toString(), "{\"pathId\":\"pick\",\"seed\":1,\"subjectCodes\":[\"" + a.code() + "\"]}", "OPTION_UNCONFIRMED"})) {
            mockMvc.perform(post("/api/v1/admin/exams/" + test[0] + "/preview")
                            .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(test[1]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.reason").value("INVALID"))
                    .andExpect(jsonPath("$.issues[?(@.code == '" + test[2] + "')]").isNotEmpty());
            assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=?",
                    Integer.class, UUID.fromString(test[0]))).isZero();
        }
    }

    @Test
    void unrepresentableSelectedDraftReturnsIssuesWithoutUsage() throws Exception {
        for (String[] fixture : ExamCheckApiIT.draftFixtures()) {
            UUID examId = exam(mapper.readTree(fixture[0]).path("parts").toString());
            String url = "/api/v1/admin/exams/" + examId + "/preview";
            mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pathId\":\"default\",\"seed\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.reason").value("INVALID"))
                    .andExpect(jsonPath("$.issues[?(@.code == '" + fixture[1] + "')]").isNotEmpty())
                    .andExpect(jsonPath("$.paper").doesNotExist());
            mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pathId\":\"default\",\"seed\":1,\"subjectCodes\":[\"MATH\"]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
            assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'",
                    Integer.class, examId)).isZero();
        }
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
                {"id":"a","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"ga","name":"A","questionType":"SINGLE_CHOICE"}]},
                {"id":"b","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"gb","name":"B","questionType":"SINGLE_CHOICE"}]},
                {"id":"c","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"gc","name":"C","questionType":"SINGLE_CHOICE"}]}
                """.formatted(a.code(), b.code(), c.code());
        UUID examId = exam("""
                [{"id":"p","order":1,"name":"Choice","questionCount":3,"participation":{"mode":"CHOICE","choose":1},"sections":[],
                  "options":[{"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":2,
                  "countsByPick":[2,1],"sections":[%s]},
                  {"id":"unfinished","kind":"SUBJECT_CHOICE","code":"UNFINISHED","expertConfirmed":false,
                   "chooseSubjects":0,"countsByPick":[],"sections":[]}]}]
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reason").value("INVALID"))
                .andExpect(jsonPath("$.issues[0].code").isString())
                .andExpect(jsonPath("$.issues[?(@.group == 'SOURCE')]").isNotEmpty())
                .andExpect(jsonPath("$.paper").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=?",
                Integer.class, examId)).isZero();
    }

    /**
     * R1-04: one path with two SUBJECT_CHOICE options (one per part); the flat subjectCodes
     * request field is a union — each choice takes the codes that belong to its own sections.
     */
    @Test
    void twoSubjectChoicesUseUnionSubjectCodes() throws Exception {
        Subject a = subject();
        Subject aa = subject();
        Subject b = subject();
        Subject bb = subject();
        for (Subject subject : List.of(a, aa, b, bb)) {
            question(subject, "SINGLE_CHOICE", "first", false);
            question(subject, "SINGLE_CHOICE", "second", false);
            question(subject, "SINGLE_CHOICE", "third", false);
        }
        String choice = """
                {"id":"%s","kind":"SUBJECT_CHOICE","code":"%s","chooseSubjects":1,"countsByPick":[%d],
                  "sections":[{"id":"%s-s1","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"%s-g1","name":"G1","questionType":"SINGLE_CHOICE"}]},
                              {"id":"%s-s2","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"%s-g2","name":"G2","questionType":"SINGLE_CHOICE"}]}]}
                """;
        UUID examId = exam("""
                [{"id":"p1","order":1,"name":"Part1","participation":{"mode":"CHOICE","choose":1},
                  "questionCount":2,"sections":[],"options":[%s]},
                 {"id":"p2","order":2,"name":"Part2","participation":{"mode":"CHOICE","choose":1},
                  "questionCount":1,"sections":[],"options":[%s]}]
                """.formatted(choice.formatted("comboA", "A", 2, "a", a.code(), "a", "a", aa.code(), "a"),
                choice.formatted("comboB", "B", 1, "b", b.code(), "b", "b", bb.code(), "b")));
        String path = "/api/v1/admin/exams/" + examId + "/preview";
        JsonNode paper = preview(examId, "{\"pathId\":\"comboA+comboB\",\"seed\":7,\"subjectCodes\":[\""
                + a.code() + "\",\"" + b.code() + "\"]}").path("paper");
        assertThat(paper.path("totalQuestions").asInt()).isEqualTo(3);
        assertThat(paper.path("subjects")).hasSize(2);
        assertThat(paper.path("subjects").get(0).path("subjectCode").asText()).isEqualTo(a.code());
        assertThat(paper.path("subjects").get(1).path("subjectCode").asText()).isEqualTo(b.code());

        // Invalid splits: missing one choice's code, too many codes for one choice, and
        // a code that belongs to no choice on the path all stay 422.
        for (String codes : List.of("\"" + a.code() + "\"",
                "\"" + a.code() + "\",\"" + aa.code() + "\",\"" + b.code() + "\"",
                "\"" + a.code() + "\",\"NOT_A_SUBJECT\"")) {
            mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pathId\":\"comboA+comboB\",\"seed\":7,\"subjectCodes\":[" + codes + "]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
        }
        // Codes on a path without any SUBJECT_CHOICE are invalid too.
        UUID plain = exam(part(a.code(),
                "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]", "null"));
        mockMvc.perform(post("/api/v1/admin/exams/" + plain + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathId\":\"default\",\"seed\":1,\"subjectCodes\":[\"" + a.code() + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
    }

    @Test
    void publishedPreviewUsesVersionStructureAndDuration() throws Exception {
        Subject subject = subject();
        question(subject, "SINGLE_CHOICE", "old question", false);
        String oldParts = part(subject.code(), "[{\"id\":\"old\",\"name\":\"Old group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1,\"requiredPointsPerQuestion\":1}]", "null");
        UUID id = exam(oldParts);
        UUID actor = UUID.randomUUID();
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by) "
                + "values (?, ?, 1, cast(? as jsonb), cast(? as jsonb), array['default'], array['default'], ?, 'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), id, "{\"code\":\"OLD\",\"name\":\"Old exam\",\"scope\":\"FULL\",\"timing\":\"SHARED\",\"durationMinutes\":45,\"practiceScoring\":\"RAW\"}",
                "{\"parts\":" + oldParts + "}", UUID.randomUUID(), actor);
        jdbc.update("update exam.exam set duration_minutes=90, question_count=0 where id=?", id);
        jdbc.update("update exam.exam_structure set parts='{\"parts\":[]}'::jsonb where exam_id=?", id);
        JsonNode response = preview(id, "{\"pathId\":\"default\",\"seed\":1,\"version\":1}");
        assertThat(response.path("version").asInt()).isEqualTo(1);
        assertThat(response.path("paper").path("totalMinutes").asInt()).isEqualTo(45);
        assertThat(response.path("paper").path("parts").get(0).path("blocks").get(0).path("blockId").asText()).isEqualTo("old");
        String url = "/api/v1/admin/exams/" + id + "/preview";
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pathId\":\"default\",\"seed\":1,\"version\":2}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"));
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pathId\":\"unpublished\",\"seed\":1,\"version\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATH_NOT_FOUND"));
    }

    @Test
    void unrepresentableSelectedChoiceValidatesSubjectSelectionBeforeReturningIssues() throws Exception {
        Subject a = subject();
        Subject b = subject();
        UUID id = exam("""
                [{"id":"p","questionCount":1,"participation":{"mode":"CHOICE","choose":1},"sections":[],
                  "options":[{"id":"choice","kind":"SUBJECT_CHOICE","code":"CHOICE","chooseSubjects":1,
                    "countsByPick":[1],"sections":[
                    {"id":"a","subjectCode":"%s","choiceGroup":"NATURAL",
                     "answerQuotas":{"unit":"COUNT","items":[{"answerType":"SINGLE_CHOICE","value":1},{"answerType":"SINGLE_CHOICE","value":0}]},
                     "blocks":[{"id":"ga","questionType":"SINGLE_CHOICE"}]},
                    {"id":"b","subjectCode":"%s","choiceGroup":"SOCIAL","blocks":[{"id":"gb","questionType":"SINGLE_CHOICE"}]}]}]}]
                """.formatted(a.code(), b.code()));
        String url = "/api/v1/admin/exams/" + id + "/preview";
        String base = "{\"pathId\":\"choice\",\"seed\":1,\"subjectCodes\":";
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "[\"" + a.code() + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reason").value("INVALID"))
                .andExpect(jsonPath("$.issues[?(@.code == 'ANSWER_QUOTA_DUPLICATE')]").isNotEmpty())
                .andExpect(jsonPath("$.paper").doesNotExist());
        for (String subjects : List.of("[]", "[\"" + a.code() + "\",\"" + b.code() + "\"]")) {
            mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                            .content(base + subjects + "}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
        }
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'",
                Integer.class, id)).isZero();
    }

}
