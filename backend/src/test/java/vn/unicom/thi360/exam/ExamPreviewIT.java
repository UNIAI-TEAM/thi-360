package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final UUID PREVIEW_KNOWLEDGE_OVERLAP = UUID.fromString("00000000-0000-7000-8200-000000001202");
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
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes) values (?, ?, ?, 'Preview', 'FULL', 'SHARED', 60)",
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

    private record TopicSet(UUID parent, UUID firstChild, UUID secondChild) {}

    private TopicSet topicSet(Subject subject, UUID firstNode, UUID secondNode) {
        UUID parent = UUID.randomUUID();
        UUID firstChild = UUID.randomUUID();
        UUID secondChild = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'QUESTION_SET','READY',?,1,2)", parent, "SET-" + parent, subject.id());
        for (int i = 0; i < 2; i++) {
            UUID child = i == 0 ? firstChild : secondChild;
            jdbc.update("insert into questionbank.question(id, code, type, status, parent_id, position, points) "
                    + "values (?,?,?,'READY',?,?,1)", child, "CHILD-" + child, "SINGLE_CHOICE", parent, i);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                    child, "{\"content\":\"child\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\","
                            + "\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\"}]}}");
        }
        jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) values (?, ?, true)",
                firstChild, firstNode);
        jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) values (?, ?, false)",
                firstChild, secondNode);
        jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) values (?, ?, true)",
                secondChild, secondNode);
        String snapshot = "{\"content\":\"set\",\"points\":2,\"body\":{\"type\":\"QUESTION_SET\",\"passage\":\"passage\","
                + "\"children\":[{\"id\":\"" + firstChild + "\"},{\"id\":\"" + secondChild + "\"}]}}";
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                parent, snapshot);
        return new TopicSet(parent, firstChild, secondChild);
    }

    private UUID questionWithKnowledge(Subject subject, String content, UUID... nodes) {
        UUID id = question(subject, "SINGLE_CHOICE", content, false);
        for (UUID node : nodes) {
            jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) "
                    + "values (?, ?, true)", id, node);
        }
        return id;
    }

    private static List<String> topicAssignments(JsonNode question) {
        List<String> ids = new ArrayList<>();
        question.path("assignedTopicQuotaIds").forEach(id -> ids.add(id.asText()));
        return ids;
    }

    private static Map<String, List<String>> topicAssignmentsByQuestion(JsonNode paper) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (JsonNode part : paper.path("parts")) {
            for (JsonNode block : part.path("blocks")) {
                for (JsonNode item : block.path("items")) {
                    if ("SET".equals(item.path("kind").asText())) {
                        for (JsonNode child : item.path("children")) {
                            result.put(child.path("questionId").asText(), topicAssignments(child));
                        }
                    } else {
                        JsonNode question = item.path("question");
                        result.put(question.path("questionId").asText(), topicAssignments(question));
                    }
                }
            }
        }
        return result;
    }

    private static JsonNode compliance(JsonNode response, String criterion) {
        for (JsonNode row : response.path("compliance")) {
            if (criterion.equals(row.path("criterion").asText())) return row;
        }
        throw new AssertionError("Missing compliance row " + criterion);
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
    void emptyExamTargetFailsWithoutUsageOrAudit() throws Exception {
        UUID examId = exam("[]");
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.issues[0].location.pathId").value("default"))
                .andExpect(jsonPath("$.paper").doesNotExist())
                .andExpect(jsonPath("$.previewId").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'",
                Integer.class, examId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit.audit_event where action='EXAM.PREVIEW' and target_id=?",
                Integer.class, examId.toString())).isZero();
    }

    @Test
    void switchingBcaCombinationRegeneratesOnlyThirdPart() throws Exception {
        Subject first = subject();
        Subject second = subject();
        for (Subject subject : List.of(first, second)) {
            for (int i = 0; i < 3; i++) {
                UUID id = question(subject, "SINGLE_CHOICE", "retained", false);
                jdbc.update("""
                        update questionbank.question_version set snapshot=jsonb_set(snapshot,'{body}',
                          '{"type":"SINGLE_CHOICE","options":[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}]}'::jsonb)
                        where question_id=? and version_no=1
                        """, id);
            }
        }
        for (String code : List.of("LY", "HOA", "SINH", "DIA")) {
            UUID subjectId = jdbc.queryForObject("select id from catalog.catalog_item where group_id = ? and code_normalized = ?",
                    UUID.class, SUBJECT_GROUP, code.toLowerCase(Locale.ROOT));
            question(new Subject(subjectId, code), "SINGLE_CHOICE", code, false);
        }
        UUID examId = exam("""
                [{"id":"p1","order":1,"name":"One","questionCount":3,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":3,
                    "blocks":[{"id":"g1","questionType":"SINGLE_CHOICE","questionCount":3,"shuffleUnits":true}]}]},
                 {"id":"p2","order":2,"name":"Two","questionCount":3,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s2","subjectCode":"%s","questionCount":3,
                    "blocks":[{"id":"g2","questionType":"SINGLE_CHOICE","questionCount":3,"shuffleUnits":true}]}]},
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
    void retainedScienceKeepsItsOriginalSubjectAllocation() throws Exception {
        Subject a = subject();
        Subject b = subject();
        Subject c = subject();
        Subject other = subject();
        for (Subject science : List.of(a, b, c)) {
            for (int i = 0; i < 17; i++) question(science, "SINGLE_CHOICE", "science", false);
        }
        question(other, "SINGLE_CHOICE", "other", false);
        UUID examId = exam("""
                [{"id":"science-part","order":1,"name":"Science","questionCount":50,
                  "participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"science","kind":"SUBJECT_CHOICE","chooseSubjects":3,"countsByPick":[17,17,16],
                     "sections":[
                       {"id":"a","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"ga","questionType":"SINGLE_CHOICE","questionCount":17}]},
                       {"id":"b","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"gb","questionType":"SINGLE_CHOICE","questionCount":17}]},
                       {"id":"c","subjectCode":"%s","choiceGroup":"NATURAL","blocks":[{"id":"gc","questionType":"SINGLE_CHOICE","questionCount":17}]}]}]},
                 {"id":"other-part","order":2,"name":"Other","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"other","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"other-group","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]
                """.formatted(a.code(), b.code(), c.code(), other.code()));
        String target = "\"target\":{\"kind\":\"PATH\",\"pathId\":\"science\",\"subjectCodes\":[\""
                + a.code() + "\",\"" + b.code() + "\",\"" + c.code() + "\"]}";
        JsonNode initial = preview(examId, "{" + target + ",\"seed\":1}");
        JsonNode regenerated = preview(examId, "{" + target + ",\"seed\":5,\"basePreviewId\":\""
                + initial.path("previewId").asText() + "\",\"regeneratePartIds\":[\"other-part\"]}");
        assertThat(regenerated.path("paper").path("parts").get(0))
                .isEqualTo(initial.path("paper").path("parts").get(0));
        assertThat(regenerated.path("droppedQuestions").get(0).path("questionId"))
                .isEqualTo(initial.path("droppedQuestions").get(0).path("questionId"));
        for (JsonNode allocation : regenerated.path("paper").path("subjects")) {
            String code = allocation.path("subjectCode").asText();
            if (code.equals(other.code())) continue;
            JsonNode block = null;
            for (JsonNode candidate : regenerated.path("paper").path("parts").get(0).path("blocks")) {
                if (code.equals(candidate.path("subjectCode").asText())) block = candidate;
            }
            assertThat(block).isNotNull();
            assertThat(allocation.path("questionCount").asInt()).isEqualTo(block.path("questionCount").asInt());
        }
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
    void regenerationExcludesRetainedQuestionsFromOverlappingSources() throws Exception {
        Subject subject = subject();
        UUID first = question(subject, "SINGLE_CHOICE", "one", false);
        UUID second = question(subject, "SINGLE_CHOICE", "two", false);
        UUID examId = exam("""
                [{"id":"p1","order":1,"name":"One","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"g1","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                 {"id":"p2","order":2,"name":"Two","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s2","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"g2","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]
                """.formatted(subject.code(), subject.code()));
        JsonNode initial = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        String retained = initial.path("paper").path("parts").get(0).path("blocks").get(0)
                .path("items").get(0).path("question").path("questionId").asText();
        UUID alternative = first.toString().equals(retained) ? second : first;
        jdbc.update("update questionbank.question set status='DRAFT' where id=?", alternative);
        int usageBefore = jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'",
                Integer.class, examId);
        int auditBefore = jdbc.queryForObject("select count(*) from audit.audit_event where action='EXAM.PREVIEW' and target_id=?",
                Integer.class, examId.toString());
        String request = "{\"target\":{\"kind\":\"EXAM\"},\"seed\":5,\"basePreviewId\":\""
                + initial.path("previewId").asText() + "\",\"regeneratePartIds\":[\"p2\"]}";
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.paper").doesNotExist()).andExpect(jsonPath("$.previewId").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'",
                Integer.class, examId)).isEqualTo(usageBefore);
        assertThat(jdbc.queryForObject("select count(*) from audit.audit_event where action='EXAM.PREVIEW' and target_id=?",
                Integer.class, examId.toString())).isEqualTo(auditBefore);
        jdbc.update("update questionbank.question set status='READY' where id=?", alternative);
        JsonNode regenerated = preview(examId, request).path("paper").path("parts");
        String retainedAgain = regenerated.get(0).path("blocks").get(0).path("items").get(0).path("question").path("questionId").asText();
        String fresh = regenerated.get(1).path("blocks").get(0).path("items").get(0).path("question").path("questionId").asText();
        assertThat(retainedAgain).isEqualTo(retained);
        assertThat(fresh).isEqualTo(alternative.toString()).isNotEqualTo(retained);
    }

    @Test
    void existingPreviewCannotCrossExamOrVersion() throws Exception {
        Subject subject = subject();
        question(subject, "SINGLE_CHOICE", "available", false);
        String parts = part(subject.code(), "[{\"id\":\"g\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]", "null");
        UUID examA = exam(parts);
        UUID examB = exam(parts);
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by) "
                        + "values (?, ?, 1, cast(? as jsonb), cast(? as jsonb), array['default'], array['default'], ?, 'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), examA, "{\"code\":\"OLD\",\"name\":\"Old\",\"scope\":\"FULL\",\"timing\":\"SHARED\"}",
                "{\"parts\":" + parts + "}", UUID.randomUUID(), UUID.randomUUID());
        JsonNode initial = preview(examA, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        String base = "\"target\":{\"kind\":\"EXAM\"},\"seed\":5,\"basePreviewId\":\""
                + initial.path("previewId").asText() + "\",\"regeneratePartIds\":[\"part\"]";
        int usageA = jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'", Integer.class, examA);
        int auditA = jdbc.queryForObject("select count(*) from audit.audit_event where action='EXAM.PREVIEW' and target_id=?", Integer.class, examA.toString());
        for (String[] attempt : List.of(new String[]{examB.toString(), "{" + base + "}"},
                new String[]{examA.toString(), "{" + base + ",\"version\":1}"})) {
            mockMvc.perform(post("/api/v1/admin/exams/" + attempt[0] + "/preview").with(asUser(Permissions.EXAM_VIEW))
                            .contentType(MediaType.APPLICATION_JSON).content(attempt[1]))
                    .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PREVIEW_EXPIRED"));
        }
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'", Integer.class, examA)).isEqualTo(usageA);
        assertThat(jdbc.queryForObject("select count(*) from audit.audit_event where action='EXAM.PREVIEW' and target_id=?", Integer.class, examA.toString())).isEqualTo(auditA);
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'", Integer.class, examB)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit.audit_event where action='EXAM.PREVIEW' and target_id=?", Integer.class, examB.toString())).isZero();
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
    void hsaMathTopicAssignmentIsExposedAndCounted() throws Exception {
        UUID subjectId = jdbc.queryForObject("select id from catalog.catalog_item where group_id=? and code_normalized='toan'",
                UUID.class, SUBJECT_GROUP);
        UUID questionId = question(new Subject(subjectId, "TOAN"), "SINGLE_CHOICE", "topic", false);
        jdbc.update("update questionbank.question set points=123.4567 where id=?", questionId);
        jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) values (?, ?, true)",
                questionId, PREVIEW_KNOWLEDGE_LEAF);
        UUID examId = exam("""
                [{"id":"math-part","order":1,"name":"Math","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"math","subjectCode":"TOAN","questionCount":1,
                    "topicQuotas":[{"id":"math-topic","nodeIds":["%s"],"count":1}],
                    "blocks":[{"id":"math-group","questionType":"SINGLE_CHOICE","questionCount":1,
                               "requiredPointsPerQuestion":123.4567}]}]}]
                """.formatted(PREVIEW_KNOWLEDGE_LEAF));
        jdbc.update("update exam.exam set kind_item_id=(select id from catalog.catalog_item where code_normalized='hsa' and group_id='00000000-0000-7000-8100-000000000001') where id=?", examId);
        JsonNode result = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        JsonNode question = result.path("paper").path("parts").get(0).path("blocks").get(0)
                .path("items").get(0).path("question");
        assertThat(question.path("questionId").asText()).isEqualTo(questionId.toString());
        assertThat(question.path("assignedTopicQuotaIds")).hasSize(1);
        assertThat(question.path("assignedTopicQuotaIds").get(0).asText()).isEqualTo("math-topic");
        assertThat(result.path("compliance")).anyMatch(row -> "TOPIC:math-topic".equals(row.path("criterion").asText())
                && "QUESTION".equals(row.path("basis").asText()) && row.path("actual").asInt() == 1);
    }

    @Test
    void questionSetPreviewProjectsTopicAssignmentsPerChildAndCompliance() throws Exception {
        Subject subject = subject();
        TopicSet set = topicSet(subject, PREVIEW_KNOWLEDGE_LEAF, PREVIEW_KNOWLEDGE_OVERLAP);
        UUID examId = exam("""
                [{"id":"part","order":1,"name":"Part","questionCount":2,"participation":{"mode":"ALL"},
                  "sections":[{"id":"section","subjectCode":"%s","questionCount":2,
                    "topicQuotas":[{"id":"topic-a","nodeIds":["%s"],"count":1},
                                   {"id":"topic-b","nodeIds":["%s"],"count":1}],
                    "blocks":[{"id":"set-group","questionType":"QUESTION_SET","questionCount":2,
                               "setCount":1,"setSize":2}]}]}]
                """.formatted(subject.code(), PREVIEW_KNOWLEDGE_LEAF, PREVIEW_KNOWLEDGE_OVERLAP));

        JsonNode response = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        JsonNode children = response.path("paper").path("parts").get(0).path("blocks").get(0)
                .path("items").get(0).path("children");
        assertThat(children).hasSize(2);
        assertThat(children.get(0).path("questionId").asText()).isEqualTo(set.firstChild().toString());
        assertThat(children.get(1).path("questionId").asText()).isEqualTo(set.secondChild().toString());
        assertThat(topicAssignments(children.get(0))).containsExactly("topic-a");
        assertThat(topicAssignments(children.get(1))).containsExactly("topic-b");
        assertThat(compliance(response, "TOPIC:topic-a").path("actual").asInt()).isEqualTo(1);
        assertThat(compliance(response, "TOPIC:topic-b").path("actual").asInt()).isEqualTo(1);
    }

    @Test
    void trimmedTopicAssignmentsSurviveRedisPartRetention() throws Exception {
        Subject trimmed = subject();
        Subject second = subject();
        Subject third = subject();
        Subject other = subject();
        for (int i = 0; i < 17; i++) {
            questionWithKnowledge(trimmed, "trimmed-" + i, PREVIEW_KNOWLEDGE_LEAF);
            question(second, "SINGLE_CHOICE", "second-" + i, false);
            question(third, "SINGLE_CHOICE", "third-" + i, false);
        }
        question(other, "SINGLE_CHOICE", "other", false);
        UUID examId = exam("""
                [{"id":"science-part","order":1,"name":"Science","questionCount":50,
                  "participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"science","kind":"SUBJECT_CHOICE","chooseSubjects":3,"countsByPick":[17,17,16],
                     "sections":[
                       {"id":"trimmed","subjectCode":"%s","choiceGroup":"NATURAL",
                        "topicQuotas":[{"id":"trim-topic","nodeIds":["%s"],"count":1}],
                        "blocks":[{"id":"trim-group","questionType":"SINGLE_CHOICE","questionCount":17}]},
                       {"id":"second","subjectCode":"%s","choiceGroup":"SOCIAL",
                        "blocks":[{"id":"second-group","questionType":"SINGLE_CHOICE","questionCount":17}]},
                       {"id":"third","subjectCode":"%s","choiceGroup":"SOCIAL",
                        "blocks":[{"id":"third-group","questionType":"SINGLE_CHOICE","questionCount":17}]}]}]},
                 {"id":"other-part","order":2,"name":"Other","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"other","subjectCode":"%s","questionCount":1,
                    "blocks":[{"id":"other-group","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]
                """.formatted(trimmed.code(), PREVIEW_KNOWLEDGE_LEAF, second.code(), third.code(), other.code()));
        String target = "\"target\":{\"kind\":\"PATH\",\"pathId\":\"science\",\"subjectCodes\":[\""
                + trimmed.code() + "\",\"" + second.code() + "\",\"" + third.code() + "\"]}";
        JsonNode initial = preview(examId, "{" + target + ",\"seed\":1}");
        Map<String, List<String>> initialAssignments = topicAssignmentsByQuestion(initial.path("paper"));
        JsonNode dropped = initial.path("droppedQuestions").get(0);
        List<String> droppedAssignments = topicAssignments(dropped);
        int droppedContribution = droppedAssignments.contains("trim-topic") ? 1 : 0;
        JsonNode initialCompliance = compliance(initial, "TOPIC:trim-topic");
        int assignedInitial = (int) initialAssignments.values().stream()
                .filter(ids -> ids.contains("trim-topic")).count();
        assertThat(initialCompliance.path("required").asInt()).isEqualTo(Math.max(0, 1 - droppedContribution));
        assertThat(initialCompliance.path("actual").asInt()).isEqualTo(assignedInitial);

        JsonNode regenerated = preview(examId, "{" + target + ",\"seed\":5,\"basePreviewId\":\""
                + initial.path("previewId").asText() + "\",\"regeneratePartIds\":[\"other-part\"]}");
        Map<String, List<String>> regeneratedAssignments = topicAssignmentsByQuestion(regenerated.path("paper"));
        assertThat(regeneratedAssignments).isEqualTo(initialAssignments);
        assertThat(regenerated.path("droppedQuestions").get(0).path("questionId"))
                .isEqualTo(dropped.path("questionId"));
        assertThat(topicAssignments(regenerated.path("droppedQuestions").get(0))).isEqualTo(droppedAssignments);
        JsonNode regeneratedCompliance = compliance(regenerated, "TOPIC:trim-topic");
        assertThat(regeneratedCompliance.path("required").asInt()).isEqualTo(initialCompliance.path("required").asInt());
        assertThat(regeneratedCompliance.path("actual").asInt()).isEqualTo(assignedInitial);
    }
    @Test
    void hsaEnglishReferenceGradeAppearsInCompliance() throws Exception {
        UUID subjectId = jdbc.queryForObject("select id from catalog.catalog_item where group_id=? and code_normalized='anh'",
                UUID.class, SUBJECT_GROUP);
        UUID questionId = question(new Subject(subjectId, "ANH"), "SINGLE_CHOICE", "english", false);
        jdbc.update("update questionbank.question set grade_item_id='00000000-0000-7000-8200-000000000303', points=123.4567 where id=?", questionId);
        UUID examId = exam("""
                [{"id":"english-part","order":1,"name":"English","questionCount":1,"participation":{"mode":"ALL"},
                  "sections":[{"id":"english","subjectCode":"ANH","questionCount":1,
                    "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE","basis":"QUESTION",
                              "byGrade":[{"code":"G12","value":1}]},
                    "blocks":[{"id":"english-group","questionType":"SINGLE_CHOICE","questionCount":1,
                               "requiredPointsPerQuestion":123.4567}]}]}]
                """);
        jdbc.update("update exam.exam set kind_item_id=(select id from catalog.catalog_item where code_normalized='hsa' and group_id='00000000-0000-7000-8100-000000000001') where id=?", examId);
        JsonNode result = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}");
        assertThat(result.path("compliance")).anyMatch(row -> "GRADE:G12".equals(row.path("criterion").asText())
                && "REFERENCE".equals(row.path("enforcement").asText())
                && row.path("required").asInt() == 1 && row.path("actual").asInt() == 1);
    }

    @Test
    void selectedOptionBlockReportsReferenceCompliance() throws Exception {
        Subject subject = subject();
        UUID questionId = question(subject, "SINGLE_CHOICE", "option question", false);
        jdbc.update("update questionbank.question set grade_item_id='00000000-0000-7000-8200-000000000303' where id=?", questionId);
        UUID examId = exam("""
                [{"id":"p","order":1,"name":"Choice","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                  "options":[{"id":"option","kind":"SUBJECT","code":"%s","blocks":[
                    {"id":"option-block","questionType":"SINGLE_CHOICE","questionCount":1,
                     "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE","basis":"QUESTION",
                               "byGrade":[{"code":"G12","value":1}]}}]}]}]
                """.formatted(subject.code()));
        JsonNode result = preview(examId, "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"option\"},\"seed\":1}");
        assertThat(result.path("compliance")).anyMatch(row -> "BLOCK:option-block".equals(row.path("scope").asText())
                && "GRADE:G12".equals(row.path("criterion").asText())
                && "REFERENCE".equals(row.path("enforcement").asText())
                && row.path("required").asInt() == 1 && row.path("actual").asInt() == 1);
    }

    @Test
    void previewReturnsAuthoredAndShuffledOptionOrders() throws Exception {
        Subject subject = subject();
        UUID shuffled = question(subject, "SINGLE_CHOICE", "shuffle", false);
        UUID kept = question(subject, "SINGLE_CHOICE", "keep", false);
        for (UUID id : List.of(shuffled, kept)) {
            String body = """
                    {"type":"SINGLE_CHOICE","shuffleOptions":%s,"options":[
                      {"id":"a","content":"A"},{"id":"b","content":"B"},{"id":"c","content":"C"},
                      {"id":"d","content":"D"},{"id":"e","content":"E"},{"id":"f","content":"F"}]}
                    """.formatted(!id.equals(kept));
            jdbc.update("update questionbank.question_version set snapshot=jsonb_set(snapshot,'{body}',cast(? as jsonb)) where question_id=? and version_no=1", body, id);
        }
        UUID examId = exam("""
                [{"id":"p","order":1,"name":"Part","questionCount":2,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s","subjectCode":"%s","questionCount":2,
                    "blocks":[{"id":"g","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]
                """.formatted(subject.code()));
        JsonNode items = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":41}")
                .path("paper").path("parts").get(0).path("blocks").get(0).path("items");
        for (JsonNode item : items) {
            JsonNode question = item.path("question");
            assertThat(question.path("originalOptionOrder")).hasSize(6);
            assertThat(question.path("originalNumber").isInt()).isTrue();
            if (kept.toString().equals(question.path("questionId").asText())) {
                assertThat(question.path("optionOrder")).isEqualTo(question.path("originalOptionOrder"));
            } else {
                assertThat(question.path("questionId").asText()).isEqualTo(shuffled.toString());
                assertThat(question.path("optionOrder")).isNotEqualTo(question.path("originalOptionOrder"));
            }
        }
    }

    @Test
    void clozePreviewRetainsPassageAndAnswerPositions() throws Exception {
        Subject subject = subject();
        UUID id = question(subject, "CLOZE", "reading fill", false);
        jdbc.update("update questionbank.question set points=2 where id=?", id);
        String body = """
                {"type":"CLOZE","passage":"<p>[[a]] and [[b]]</p>","blanks":[
                  {"id":"a","answers":["one"],"points":1},
                  {"id":"b","answers":["two"],"points":1}]}
                """;
        jdbc.update("update questionbank.question_version set snapshot=jsonb_set(snapshot,'{body}',cast(? as jsonb)) where question_id=? and version_no=1", body, id);
        UUID examId = exam("""
                [{"id":"p","order":1,"name":"Part","questionCount":2,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s","subjectCode":"%s","questionCount":2,
                    "blocks":[{"id":"g","questionType":"CLOZE","questionCount":2,"setCount":1,"setSize":2}]}]}]
                """.formatted(subject.code()));
        JsonNode paper = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}").path("paper");
        JsonNode question = paper.path("parts").get(0).path("blocks").get(0).path("items").get(0).path("question");
        assertThat(paper.path("totalQuestions").asInt()).isEqualTo(2);
        assertThat(question.path("body").path("passage").asText()).isEqualTo("<p>[[a]] and [[b]]</p>");
        assertThat(question.path("body").path("blanks").get(0).path("id").asText()).isEqualTo("a");
        assertThat(question.path("body").path("blanks").get(1).path("id").asText()).isEqualTo("b");
    }

    @Test
    void previewOfSeededChoiceExposesShuffleOptions() throws Exception {
        UUID seededQuestion = seededPreviewQuestion();
        String blocks = """
                [{"id":"g","name":"Group","questionType":"SINGLE_CHOICE","questionCount":1,
                  "knowledgeScope":{"nodeIds":["00000000-0000-7000-8200-000000001201"]}}]
                """;
        UUID examId = exam(part("TOAN", blocks, "null"));
        JsonNode paper = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":123}").path("paper");
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
        JsonNode body = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":123}")
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
        JsonNode paper = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":123}").path("paper");
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
        String request = "{\"target\":{\"kind\":\"EXAM\"},\"seed\":123}";
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
        question(a, "SINGLE_CHOICE", "available", false);
        String valid = part(a.code(), "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1,\"requiredPointsPerQuestion\":1}]", "null");
        UUID badPart = exam(valid.replaceFirst("\"questionCount\":1,", "\"questionCount\":2,"));
        mockMvc.perform(post("/api/v1/admin/exams/" + badPart + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reason").value("INVALID"))
                .andExpect(jsonPath("$.paper").doesNotExist())
                .andExpect(jsonPath("$.issues[0].code").value("PART_TOTAL_MISMATCH"))
                .andExpect(jsonPath("$.issues[0].location.partId").value("part"));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=?",
                Integer.class, badPart)).isZero();
    }

    @Test
    void unrepresentableSelectedDraftReturnsIssuesWithoutUsage() throws Exception {
        for (String[] fixture : ExamCheckApiIT.draftFixtures()) {
            UUID examId = exam(mapper.readTree(fixture[0]).path("parts").toString());
            String url = "/api/v1/admin/exams/" + examId + "/preview";
            mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.reason").value("INVALID"))
                    .andExpect(jsonPath("$.issues[?(@.code == '" + fixture[1] + "')]").isNotEmpty())
                    .andExpect(jsonPath("$.paper").doesNotExist());
            mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"default\",\"subjectCodes\":[\"MATH\"]},\"seed\":1}"))
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
        JsonNode result = preview(examId, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":42}");
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
        String body = "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"science\"},\"seed\":1}";
        mockMvc.perform(post(path).header("Origin", "http://localhost:3001").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).with(asUser(Permissions.QUESTION_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
        mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"science\",\"subjectCodes\":[\"NOT_A_SUBJECT\"]},\"seed\":1}"))
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
        String request = "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"science\",\"subjectCodes\":[\"" + a.code()
                + "\",\"" + b.code() + "\"]},\"seed\":42}";
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
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}"))
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
        JsonNode paper = preview(examId, "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"comboA+comboB\",\"subjectCodes\":[\""
                + a.code() + "\",\"" + b.code() + "\"]},\"seed\":7}").path("paper");
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
                            .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"comboA+comboB\",\"subjectCodes\":[" + codes + "]},\"seed\":7}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
        }
        // Codes on a path without any SUBJECT_CHOICE are invalid too.
        UUID plain = exam(part(a.code(),
                "[{\"id\":\"g\",\"name\":\"Group\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]", "null"));
        mockMvc.perform(post("/api/v1/admin/exams/" + plain + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"default\",\"subjectCodes\":[\"" + a.code() + "\"]},\"seed\":1}"))
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
                UUID.randomUUID(), id, "{\"code\":\"OLD\",\"name\":\"Old exam\",\"scope\":\"FULL\",\"timing\":\"SHARED\",\"durationMinutes\":45,\"practiceScoring\":\"RAW\","
                        + "\"examQuotas\":[{\"id\":\"old-grade\",\"dimension\":\"GRADE\",\"basis\":\"QUESTION\",\"enforcement\":\"REFERENCE\",\"buckets\":[{\"codes\":[\"G12\"],\"count\":1}]}]}",
                "{\"parts\":" + oldParts + "}", UUID.randomUUID(), actor);
        jdbc.update("update exam.exam set duration_minutes=90, question_count=0 where id=?", id);
        jdbc.update("update exam.exam_structure set parts='{\"parts\":[]}'::jsonb where exam_id=?", id);
        JsonNode response = preview(id, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1,\"version\":1}");
        assertThat(response.path("version").asInt()).isEqualTo(1);
        assertThat(response.path("paper").path("totalMinutes").asInt()).isEqualTo(45);
        assertThat(response.path("paper").path("parts").get(0).path("blocks").get(0).path("blockId").asText()).isEqualTo("old");
        assertThat(response.path("compliance")).anyMatch(row -> "EXAM:old-grade:G12".equals(row.path("criterion").asText())
                && "REFERENCE".equals(row.path("enforcement").asText()) && row.path("required").asInt() == 1);
        String url = "/api/v1/admin/exams/" + id + "/preview";
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1,\"version\":2}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"));
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"unpublished\"},\"seed\":1,\"version\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATH_NOT_FOUND"));
    }

    /**
     * R4-3 + ADV:CR-4:21: the ambiguous-id rule applies only at the draft check boundary. A frozen published version
     * whose snapshot carries the legacy collision (option "b" holds a real section named "a:blocks", the holder name of
     * option "a"'s direct blocks) still previews as it was published, while the equivalent draft is refused: its
     * preview fails and its check reports the one exam-level collision error.
     */
    @Test
    void aFrozenLegacyVersionWithTheBlocksHolderIdStillPreviewsWhileTheEquivalentDraftIsRefused() throws Exception {
        Subject first = subject();
        Subject second = subject();
        question(first, "SINGLE_CHOICE", "legacy first", false);
        question(second, "SINGLE_CHOICE", "legacy second", false);
        String parts = """
                [{"id":"p","order":1,"name":"Choice","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                  "options":[
                    {"id":"a","kind":"SUBJECT","code":"%s","blocks":[{"id":"ga","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"b","kind":"SUBJECT","code":"%s","sections":[{"id":"a:blocks","subjectCode":"%s",
                      "questionCount":1,"blocks":[{"id":"gb","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}]
                """.formatted(first.code(), second.code(), second.code());
        UUID id = exam(parts);
        // The legacy frozen version, written directly as old code did: the snapshot holds the collision id.
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by) "
                        + "values (?, ?, 1, cast(? as jsonb), cast(? as jsonb), array['b'], array['b'], ?, 'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), id, "{\"code\":\"OLD\",\"name\":\"Old\",\"scope\":\"FULL\",\"timing\":\"SHARED\",\"durationMinutes\":45,\"practiceScoring\":\"RAW\"}",
                "{\"parts\":" + parts + "}", UUID.randomUUID(), UUID.randomUUID());

        JsonNode frozen = preview(id, "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"b\"},\"seed\":1,\"version\":1}");
        assertThat(frozen.path("version").asInt()).isEqualTo(1);
        assertThat(frozen.path("paper").path("parts").get(0).path("blocks").get(0).path("blockId").asText()).isEqualTo("gb");

        // The equivalent draft: the preview of the path fails on the ambiguous id, and a fresh check marks the path.
        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/preview").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"b\"},\"seed\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.issues[?(@.code == 'STORED_ID_COLLISION')]").isNotEmpty());
        JsonNode check = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        java.util.List<JsonNode> collisions = check.path("groups").findValues("issues").stream()
                .flatMap(list -> java.util.stream.StreamSupport.stream(list.spliterator(), false))
                .filter(issue -> "STORED_ID_COLLISION".equals(issue.path("code").asText())).toList();
        assertThat(collisions).as("the draft check reports the one exam-level collision: %s", check.path("groups")).hasSize(1);
        assertThat(collisions.getFirst().path("blockId").asText()).isEqualTo("a:blocks");
        assertThat(check.path("paths")).as("no path of the draft is certified: %s", check.path("paths")).isEmpty();
    }

    /**
     * R5-3: the mirrored legacy fixture. "b" is listed BEFORE "a" and stores a real section named "a:blocks" with a
     * two-point block; "a" holds one direct block worth one point; the frozen maxScore is one. The frozen version
     * previews path "a" with its own one-point block, as it did before the draft guard existed (the later holder wins
     * the id for a version), instead of reading the two-point section for it. The equivalent draft preview is refused
     * with the one exam-level collision error.
     */
    @Test
    void aFrozenLegacyVersionPreviewsTheDirectBlocksOptionWithItsOwnScoring() throws Exception {
        Subject first = subject();
        Subject second = subject();
        jdbc.update("update questionbank.question set points=1 where id=?", question(first, "SINGLE_CHOICE", "legacy first", false));
        jdbc.update("update questionbank.question set points=2 where id=?", question(second, "SINGLE_CHOICE", "legacy second", false));
        String parts = """
                [{"id":"p","order":1,"name":"Choice","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                  "options":[
                    {"id":"b","kind":"SUBJECT","code":"%s","sections":[{"id":"a:blocks","subjectCode":"%s",
                      "questionCount":1,"blocks":[{"id":"gb","questionType":"SINGLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":2}]}]},
                    {"id":"a","kind":"SUBJECT","code":"%s","blocks":[{"id":"ga","questionType":"SINGLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":1}]}]}]
                """.formatted(second.code(), second.code(), first.code());
        UUID id = exam(parts);
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by) "
                        + "values (?, ?, 1, cast(? as jsonb), cast(? as jsonb), array['a'], array['a'], ?, 'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), id, "{\"code\":\"OLD\",\"name\":\"Old\",\"scope\":\"FULL\",\"timing\":\"SHARED\",\"durationMinutes\":45,\"practiceScoring\":\"RAW\",\"maxScore\":1}",
                "{\"parts\":" + parts + "}", UUID.randomUUID(), UUID.randomUUID());

        JsonNode frozen = preview(id, "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"a\"},\"seed\":1,\"version\":1}");
        assertThat(frozen.path("version").asInt()).isEqualTo(1);
        assertThat(frozen.path("paper").path("parts").get(0).path("blocks").get(0).path("blockId").asText()).isEqualTo("ga");

        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/preview").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"a\"},\"seed\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.issues[?(@.code == 'STORED_ID_COLLISION')]").isNotEmpty())
                .andExpect(jsonPath("$.paper").doesNotExist());
    }

    /**
     * R5-2: a normal draft (no stored id equals a holder name) whose authored option "p3:CA1" is invalid (an unsized
     * single-choice block among its direct blocks) shares its id with the option the code-source part "p3" generates
     * for combination CA1. Previewing the safe sibling with the generated option must not inherit the authored
     * option's error (an option-scoped issue belongs to its own part "p"), while previewing the authored option
     * itself reports it.
     */
    @Test
    void anAuthoredOptionIdEqualToAGeneratedIdDoesNotFailAnotherPartsPreview() throws Exception {
        Subject safe = subject();
        question(safe, "SINGLE_CHOICE", "safe", false);
        for (String code : List.of("LY", "HOA", "SINH", "DIA")) {
            UUID subjectId = jdbc.queryForObject("select id from catalog.catalog_item where group_id = ? and code_normalized = ?",
                    UUID.class, SUBJECT_GROUP, code.toLowerCase(Locale.ROOT));
            question(new Subject(subjectId, code), "SINGLE_CHOICE", code, false);
        }
        UUID examId = exam("""
                [{"id":"p","order":1,"name":"Choice","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                  "options":[
                    {"id":"p3:CA1","kind":"SUBJECT","code":"X","blocks":[{"id":"x-rest","questionType":"SINGLE_CHOICE"}]},
                    {"id":"safe","kind":"SUBJECT","code":"S","sections":[{"id":"safe-sec","subjectCode":"%s","questionCount":1,
                      "blocks":[{"id":"g-safe","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]},
                 {"id":"p3","order":2,"name":"Three","questionCount":1,
                  "codeSource":{"source":"GLOBAL_LIST"},"subjectPool":[
                    {"id":"ly","subjectCode":"LY","questionCount":1,"blocks":[{"id":"gly","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"hoa","subjectCode":"HOA","questionCount":1,"blocks":[{"id":"ghoa","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"sinh","subjectCode":"SINH","questionCount":1,"blocks":[{"id":"gsinh","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"dia","subjectCode":"DIA","questionCount":1,"blocks":[{"id":"gdia","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]
                """.formatted(safe.code()));
        jdbc.update("update exam.exam set kind_item_id=(select id from catalog.catalog_item where code_normalized='bca' and group_id='00000000-0000-7000-8100-000000000001') where id=?", examId);

        JsonNode generated = preview(examId, "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"safe+p3:CA1\"},\"seed\":1}");
        assertThat(generated.path("paper").path("parts")).as("the safe path previews: %s", generated).isNotEmpty();

        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"p3:CA1+p3:CA1\"},\"seed\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.issues[?(@.code == 'SECTION_TOTAL_MISMATCH')]").isNotEmpty());
    }

    @Test
    void publishedSectionPreviewRequiresAPathContainingThatSection() throws Exception {
        Subject allowed = subject();
        Subject denied = subject();
        UUID expected = question(allowed, "SINGLE_CHOICE", "published section", false);
        question(denied, "SINGLE_CHOICE", "unpublished section", false);
        String parts = """
                [{"id":"p","order":1,"name":"Choice","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                  "options":[
                    {"id":"allowed","kind":"SUBJECT","code":"A","sections":[{"id":"s-allowed","subjectCode":"%s",
                      "questionCount":1,"blocks":[{"id":"ga","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                    {"id":"denied","kind":"SUBJECT","code":"B","sections":[{"id":"s-denied","subjectCode":"%s",
                      "questionCount":1,"blocks":[{"id":"gb","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}]
                """.formatted(allowed.code(), denied.code());
        UUID id = exam(parts);
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by) "
                        + "values (?, ?, 1, cast(? as jsonb), cast(? as jsonb), array['allowed'], array['allowed'], ?, 'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), id, "{\"code\":\"OLD\",\"name\":\"Old\",\"scope\":\"FULL\",\"timing\":\"SHARED\",\"durationMinutes\":45,\"practiceScoring\":\"RAW\"}",
                "{\"parts\":" + parts + "}", UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("update exam.exam_structure set parts='{\"parts\":[]}'::jsonb where exam_id=?", id);
        JsonNode response = preview(id, "{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"s-allowed\"},\"seed\":1,\"version\":1}");
        assertThat(response.path("paper").path("parts").get(0).path("blocks").get(0)
                .path("items").get(0).path("question").path("questionId").asText()).isEqualTo(expected.toString());
        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/preview").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"s-denied\"},\"seed\":1,\"version\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATH_NOT_FOUND"));
    }

    @Test
    void publishedCombinationUsesSnapshottedSubjectsInsteadOfCurrentCatalog() throws Exception {
        UUID chemistryId = jdbc.queryForObject("select id from catalog.catalog_item where group_id=? and code_normalized='hoa'",
                UUID.class, SUBJECT_GROUP);
        UUID chemistryQuestion = question(new Subject(chemistryId, "HOA"), "SINGLE_CHOICE", "chemistry", false);
        jdbc.update("update questionbank.question set points=123.4567 where id=?", chemistryQuestion);
        String parts = """
                [{"id":"p3","order":1,"name":"Science","questionCount":1,"codeSource":{"source":"GLOBAL_LIST"},
                  "subjectPool":[
                    {"id":"physics","subjectCode":"LY","questionCount":1,
                     "blocks":[{"id":"g-physics","questionType":"SINGLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":123.4567}]},
                    {"id":"chemistry","subjectCode":"HOA","questionCount":1,
                     "blocks":[{"id":"g-chemistry","questionType":"SINGLE_CHOICE","questionCount":1,"requiredPointsPerQuestion":123.4567}]}]}]
                """;
        UUID id = exam(parts);
        String config = """
                {"code":"OLD","name":"Old","scope":"FULL","timing":"SHARED","durationMinutes":45,
                 "practiceScoring":"RAW","combinations":[{"partId":"p3","code":"CA1","name":"CA1",
                 "subjects":[{"code":"HOA","position":0}]}]}
                """;
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by) "
                        + "values (?, ?, 1, cast(? as jsonb), cast(? as jsonb), array['p3:CA1'], array['p3:CA1'], ?, 'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), id, config, "{\"parts\":" + parts + "}", UUID.randomUUID(), UUID.randomUUID());
        JsonNode result = preview(id, "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"p3:CA1\"},\"seed\":1,\"version\":1}");
        assertThat(result.path("paper").path("parts").get(0).path("blocks").get(0).path("subjectCode").asText()).isEqualTo("HOA");
        JsonNode section = preview(id, "{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"chemistry\"},\"seed\":1,\"version\":1}");
        assertThat(section.path("paper").path("totalQuestions").asInt()).isEqualTo(1);
        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/preview").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"physics\"},\"seed\":1,\"version\":1}"))
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
        String base = "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"choice\",\"subjectCodes\":";
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "[\"" + a.code() + "\"]},\"seed\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reason").value("INVALID"))
                .andExpect(jsonPath("$.issues[?(@.code == 'ANSWER_QUOTA_DUPLICATE')]").isNotEmpty())
                .andExpect(jsonPath("$.paper").doesNotExist());
        for (String subjects : List.of("[]", "[\"" + a.code() + "\",\"" + b.code() + "\"]")) {
            mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                            .content(base + subjects + "},\"seed\":1}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("PREVIEW_SUBJECTS_INVALID"));
        }
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where exam_id=? and kind='PREVIEW'",
                Integer.class, id)).isZero();
    }

}
