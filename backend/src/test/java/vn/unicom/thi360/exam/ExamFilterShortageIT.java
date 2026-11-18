package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamFilterShortageIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;

    @Test
    void matchingShapeShortageReportsFilteredCount() throws Exception {
        var subject = subject();
        question(subject.id(), "MATCHING", "{\"type\":\"MATCHING\",\"left\":[{},{},{},{}],\"right\":[{},{},{},{},{},{}]}");
        question(subject.id(), "MATCHING", "{\"type\":\"MATCHING\",\"left\":[{},{},{},{}],\"right\":[{},{},{},{},{}]}");
        var result = checkResult(subject, 2, "{\"id\":\"b\",\"questionType\":\"MATCHING\",\"questionCount\":2,\"itemShape\":{\"leftItems\":4,\"rightItems\":6}}");
        var issue = findIssue(result, "ITEM_SHAPE_SHORTAGE");
        assertThat(issue.path("available").asLong()).isEqualTo(1);
        assertThat(issue.path("required").asInt()).isEqualTo(2);
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("CELL_SHORTAGE", "SOURCE_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void shortAnswerFormatShortageReportsFilteredCount() throws Exception {
        var subject = subject();
        question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"INTEGER\",\"answers\":[\"1\"]}");
        question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"DECIMAL\",\"answers\":[\"1.2\"]}");
        var issue = check(subject, 2, "{\"id\":\"b\",\"questionType\":\"SHORT_ANSWER\",\"questionCount\":2,\"answerFormats\":[\"INTEGER\"]}", "ANSWER_FORMAT_SHORTAGE");
        assertThat(issue.path("available").asLong()).isEqualTo(1);
        assertThat(issue.path("required").asInt()).isEqualTo(2);
    }

    @Test
    void insufficientUnfilteredPoolDoesNotBlameTheFormatFilter() throws Exception {
        var subject = subject();
        question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"DECIMAL\",\"answers\":[\"1.2\"]}");
        var result = checkResult(subject, 2, "{\"id\":\"b\",\"questionType\":\"SHORT_ANSWER\",\"questionCount\":2,\"answerFormats\":[\"INTEGER\"]}");
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("ANSWER_FORMAT_SHORTAGE");
        assertThat(result.path("groups").findValuesAsText("code")).contains("CELL_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void unrelatedShortBlockRetainsItsGenericIssue() throws Exception {
        var subject = subject();
        question(subject.id(), "SINGLE_CHOICE", "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"correct\":true}]}");
        question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"INTEGER\",\"answers\":[\"1\"]}");
        question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"DECIMAL\",\"answers\":[\"1.2\"]}");
        var result = checkResult(subject, 4, """
                {"id":"generic","questionType":"SINGLE_CHOICE","questionCount":2},
                {"id":"filtered","questionType":"SHORT_ANSWER","questionCount":2,"answerFormats":["INTEGER"]}
                """);
        var issues = result.path("groups").findValues("issues").stream()
                .flatMap(rows -> java.util.stream.StreamSupport.stream(rows.spliterator(), false)).toList();
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.path("blockId").asText()).isEqualTo("generic");
            assertThat(issue.path("code").asText()).isEqualTo("CELL_SHORTAGE");
        });
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.path("blockId").asText()).isEqualTo("filtered");
            assertThat(issue.path("code").asText()).isEqualTo("ANSWER_FORMAT_SHORTAGE");
        });
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.path("issueCount").asInt()).isEqualTo(issues.size());
    }

    @Test
    void genuineMatrixCellShortageOnFilteredBlockIsPreserved() throws Exception {
        var subject = subject();
        for (int i = 0; i < 2; i++) {
            UUID id = question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"INTEGER\",\"answers\":[\"1\"]}");
            jdbc.update("UPDATE questionbank.question SET grade_item_id=?,level_item_id=? WHERE id=?",
                    UUID.fromString("00000000-0000-7000-8200-000000000301"),
                    UUID.fromString("00000000-0000-7000-8200-000000000401"), id);
        }
        var result = checkResult(subject, 2, """
                {"id":"b","questionType":"SHORT_ANSWER","questionCount":2,"answerFormats":["INTEGER"],
                 "matrix":{"mode":"COMBINED","unit":"COUNT","cells":[{"gradeCode":"G12","difficultyCode":"NB","value":2}]}}
                """);
        var issue = findIssue(result, "CELL_SHORTAGE");
        assertThat(issue.path("gradeCode").asText()).isEqualTo("G12");
        assertThat(issue.path("difficultyCode").asText()).isEqualTo("NB");
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("ANSWER_FORMAT_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void missingChildMapidAtPositionReportsNoMatchingSet() throws Exception {
        var subject = subject();
        UUID parent = question(subject.id(), "QUESTION_SET", "{\"type\":\"QUESTION_SET\",\"children\":[]}");
        for (int position = 0; position < 2; position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "CH-" + child, parent, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", child,
                    "{\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"correct\":true}]}}");
        }
        UUID node = jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE group_id=(SELECT id FROM catalog.catalog_group WHERE code='KNOWLEDGE') AND status=1 LIMIT 1", UUID.class);
        var result = checkResult(subject, 2, "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":2,\"setCount\":1,\"setSize\":2,\"childAnswerType\":\"SINGLE_CHOICE\",\"childConditions\":[{\"from\":1,\"to\":1,\"nodeIds\":[\"" + node + "\"]}]}");
        var issue = findIssue(result, "CHILD_CONDITION_SHORTAGE");
        assertThat(issue.path("available").asLong()).isZero();
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("location").path("childPosition").asInt()).isEqualTo(1);
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("SET_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        int issueCount = result.path("groups").findValues("issues").stream().mapToInt(JsonNode::size).sum();
        assertThat(result.path("issueCount").asInt()).isEqualTo(issueCount);
    }

    @Test
    void optionBlocksSuppressGenericFormatShortage() throws Exception {
        var subject = subject();
        question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"INTEGER\",\"answers\":[\"1\"]}");
        question(subject.id(), "SHORT_ANSWER", "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"DECIMAL\",\"answers\":[\"1.2\"]}");
        var result = checkOptionResult(subject, 2, "{\"id\":\"b\",\"questionType\":\"SHORT_ANSWER\",\"questionCount\":2,\"answerFormats\":[\"INTEGER\"]}");
        var issue = findIssue(result, "ANSWER_FORMAT_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(2);
        assertThat(issue.path("available").asInt()).isEqualTo(1);
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("CELL_SHORTAGE", "SOURCE_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void optionBlockChildConditionReportsPositionWithoutGenericShortage() throws Exception {
        var subject = subject();
        UUID parent = question(subject.id(), "QUESTION_SET", "{\"type\":\"QUESTION_SET\",\"children\":[]}");
        for (int position = 0; position < 2; position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "CH-" + child, parent, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", child,
                    "{\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"correct\":true}]}}");
        }
        UUID node = jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE group_id=(SELECT id FROM catalog.catalog_group WHERE code='KNOWLEDGE') AND status=1 LIMIT 1", UUID.class);
        var result = checkOptionResult(subject, 2, "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":2,\"setCount\":1,\"setSize\":2,\"childAnswerType\":\"SINGLE_CHOICE\",\"childConditions\":[{\"from\":1,\"to\":1,\"nodeIds\":[\"" + node + "\"]}]}");
        var issue = findIssue(result, "CHILD_CONDITION_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("available").asInt()).isZero();
        assertThat(issue.path("location").path("childPosition").asInt()).isEqualTo(1);
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("SET_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void flexibleShortageOnOneSubjectKeepsTheValidSubjectChecked() throws Exception {
        var subjectA = subject();
        var subjectB = subject();
        List<UUID> nodes = disjointKnowledgeNodes();
        questionSet(subjectA.id(), 3, nodes.get(0), null, null);
        questionSet(subjectA.id(), 3, nodes.get(1), null, null);
        questionSet(subjectA.id(), 3, nodes.get(1), null, null);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count) VALUES (?,?,?,'Flexible','FULL','SHARED',4)", id, id.toString(), id.toString());
        String document = """
                {"parts":[{"id":"p","questionCount":4,"sections":[
                  {"id":"flex","subjectCode":"%1$s","questionCount":3,"blocks":[
                    {"id":"flexible","questionType":"QUESTION_SET","questionCount":3,
                     "childConditions":[{"from":1,"to":1,"nodeIds":["%3$s"]}]}]},
                  {"id":"short","subjectCode":"%2$s","questionCount":1,"blocks":[
                    {"id":"single","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """.formatted(subjectA.code(), subjectB.code(), nodes.get(0));
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'flex')", id, document);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "flexible", "p", "flex", subjectA.id());
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "single", "p", "short", subjectB.id());
        var result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("CHILD_CONDITION_SHORTAGE");
        var checked = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + id + "/check").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var states = checked.path("sectionStates");
        assertThat(states).anySatisfy(state -> {
            assertThat(state.path("sectionId").asText()).isEqualTo("flex");
            assertThat(state.path("state").asText()).isEqualTo("CHECKED");
        });
        assertThat(states).anySatisfy(state -> {
            assertThat(state.path("sectionId").asText()).isEqualTo("short");
            assertThat(state.path("state").asText()).isEqualTo("INCOMPLETE");
        });
    }

    @Test
    void uncomposableFlexiblePoolStillReportsTheConditionShortage() throws Exception {
        var subject = subject();
        List<UUID> nodes = disjointKnowledgeNodes();
        questionSet(subject.id(), 4, nodes.get(0), null, null);
        questionSet(subject.id(), 3, nodes.get(1), null, null);
        var result = checkResult(subject, 3, """
                {"id":"b","questionType":"QUESTION_SET","questionCount":3,
                 "childConditions":[{"from":1,"to":1,"nodeIds":["%s"]}]}
                """.formatted(nodes.get(0)));
        var issue = findIssue(result, "CHILD_CONDITION_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(3);
        assertThat(issue.path("available").asLong()).isZero();
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void strictParentScopeIsKeptWhenOnlyTheChildConditionIsRelaxed() throws Exception {
        var subject = subject();
        List<UUID> nodes = disjointKnowledgeNodes();
        // Position 1 is outside the scope, position 2 inside: legacy any-child scope matches, strict scope does not.
        questionSet(subject.id(), 2, nodes.get(1), nodes.get(0), null);
        var result = checkResult(subject, 2, """
                {"id":"b","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2,
                 "childAnswerType":"SINGLE_CHOICE",
                 "knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true},
                 "childConditions":[{"from":1,"to":1,"nodeIds":["%1$s"]}]}
                """.formatted(nodes.get(0)));
        var codes = result.path("groups").findValuesAsText("code");
        assertThat(codes).doesNotContain("CHILD_CONDITION_SHORTAGE").contains("SET_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void strictSlotScopeIsKeptWhenOnlyTheChildConditionIsRelaxed() throws Exception {
        var subject = subject();
        List<UUID> nodes = disjointKnowledgeNodes();
        questionSet(subject.id(), 2, nodes.get(1), nodes.get(0), null);
        var result = checkResult(subject, 2, """
                {"id":"b","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2,
                 "childAnswerType":"SINGLE_CHOICE",
                 "slots":[{"index":1,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}}],
                 "childConditions":[{"from":1,"to":1,"nodeIds":["%1$s"]}]}
                """.formatted(nodes.get(0)));
        var codes = result.path("groups").findValuesAsText("code");
        assertThat(codes).doesNotContain("CHILD_CONDITION_SHORTAGE").containsAnyOf("SET_SHORTAGE", "SLOT_SOURCE_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    private List<UUID> disjointKnowledgeNodes() {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        var topic = knowledge.stream().filter(a -> knowledge.stream().anyMatch(b ->
                !b.path().startsWith(a.path()) && !a.path().startsWith(b.path()))).findFirst().orElseThrow();
        var other = knowledge.stream().filter(item -> !item.path().startsWith(topic.path())
                && !topic.path().startsWith(item.path())).findFirst().orElseThrow();
        return List.of(topic.id(), other.id());
    }

    private void questionSet(UUID subject, int children, UUID positionZeroNode,
                             UUID positionOneNode, UUID positionTwoNode) {
        UUID root = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) VALUES (?,?,?,'READY',?,1,1)", root, "Q-" + root, "QUESTION_SET", subject);
        List<UUID> nodes = java.util.Arrays.asList(positionZeroNode, positionOneNode, positionTwoNode);
        var snapshot = new StringBuilder("{\"type\":\"QUESTION_SET\",\"children\":[");
        for (int position = 0; position < children; position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "CH-" + child, root, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", child,
                    "{\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"correct\":true},{\"id\":\"b\",\"correct\":false}]}}");
            UUID node = nodes.get(Math.min(position, nodes.size() - 1));
            if (node != null) {
                jdbc.update("INSERT INTO questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) VALUES (?,?,true)", child, node);
            }
            if (position > 0) snapshot.append(',');
            snapshot.append("{\"id\":\"").append(child).append("\",\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"correct\":true},{\"id\":\"b\",\"correct\":false}]}}");
        }
        snapshot.append("]}");
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", root,
                "{\"body\":" + snapshot + "}");
    }

    private record Subject(UUID id, String code) { }

    private Subject subject() {
        UUID id = UUID.randomUUID();
        String code = "SHORTAGE_" + id.toString().replace("-", "");
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) VALUES (?,?,?,?,(SELECT coalesce(max(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),'Shortage',?)",
                id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return new Subject(id, code);
    }

    private UUID question(UUID subject, String type, String body) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) VALUES (?,?,?,'READY',?,1,1)", id, "Q-" + id, type, subject);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id, "{\"body\":" + body + "}");
        return id;
    }

    private JsonNode check(Subject subject, int count, String block, String code) throws Exception {
        return findIssue(checkResult(subject, count, block), code);
    }

    private JsonNode findIssue(JsonNode result, String code) {
        return result.path("groups").findValues("issues").stream()
                .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> code.equals(issue.path("code").asText())).findFirst().orElseThrow();
    }

    private JsonNode checkResult(Subject subject, int count, String block) throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count) VALUES (?,?,?,'Shortage','FULL','SHARED',?)", id, id.toString(), id.toString(), count);
        String document = "{\"parts\":[{\"id\":\"p\",\"questionCount\":" + count + ",\"sections\":[{\"id\":\"s\",\"subjectCode\":\"" + subject.code() + "\",\"questionCount\":" + count + ",\"blocks\":[" + block + "]}]}]}";
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'shortage')", id, document);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), id, "b", "p", "s", subject.id());
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode checkOptionResult(Subject subject, int count, String block) throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count) VALUES (?,?,?,'Option shortage','FULL','SHARED',?)", id, id.toString(), id.toString(), count);
        String document = "{\"parts\":[{\"id\":\"p\",\"questionCount\":" + count + ",\"participation\":{\"mode\":\"CHOICE\",\"choose\":1},\"options\":[{\"id\":\"option\",\"kind\":\"SUBJECT\",\"code\":\"" + subject.code() + "\",\"blocks\":[" + block + "]}]}]}";
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'option-shortage')", id, document);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,option_code,subject_item_id) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), id, "b", "p", subject.code(), subject.id());
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
