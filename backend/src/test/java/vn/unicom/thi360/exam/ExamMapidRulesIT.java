package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;

/**
 * UNI-2758 end to end through the API (phase 0 AC-1..AC-9): the structure is saved with PUT, checked with POST
 * /check and generated with POST /preview, over a real question bank. The mapid mode and rules ride in the
 * section JSON; nothing else is configured.
 */
@Transactional
class ExamMapidRulesIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final String OPTIONS = "\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\"}]";

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;
    @Autowired QuestionUsageProvider provider;
    @Autowired vn.unicom.thi360.exam.application.ExamCatalogUsageProvider catalogUsage;

    private String subjectCode;
    private UUID subjectId;
    private List<UUID> nodes;

    // ---- AC-1 ------------------------------------------------------------------------------------------------

    @Test
    void allModeKeepsTheRulesInTheDraftAndCompilesNothing() throws Exception {
        arrange();
        for (int i = 0; i < 4; i++) single("SINGLE_CHOICE");
        UUID exam = exam(section(2, "ALL", "[" + count("exact", null, "EXACT", 2, a()) + ","
                + count("group", "g1", "AT_LEAST", 3, a()) + "]", block("g1", "SINGLE_CHOICE", 2)));
        JsonNode check = check(exam);
        assertThat(codes(check)).doesNotContain("MAPID_RULE_CONFLICT");
        assertValid(check);
        JsonNode paper = preview(exam, 1);
        assertThat(questionIds(paper)).hasSize(2);
        assertThat(structure(exam).path("parts").get(0).path("sections").get(0).path("mapidRules")).hasSize(2);
    }

    // ---- AC-2 ------------------------------------------------------------------------------------------------

    @Test
    void aPresentRuleNamesTheMapidWhenNoQuestionCarriesIt() throws Exception {
        arrange();
        for (int i = 0; i < 6; i++) single("SINGLE_CHOICE", nodes.get(1));
        UUID exam = exam(section(3, "MATRIX", "[" + rule("present", "PRESENT", null, a()) + "]",
                block("g1", "SINGLE_CHOICE", 3)));
        JsonNode check = check(exam);
        assertThat(check.path("status").asText()).isEqualTo("INVALID");
        JsonNode issue = issue(check, "TOPIC_QUOTA_INFEASIBLE");
        assertThat(issue.path("ruleId").asText()).isEqualTo("present");
        assertThat(texts(issue.path("nodeIds"))).containsExactly(nodes.get(0).toString());
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("available").asInt()).isZero();
    }

    @Test
    void aPresentRuleIsMetAndReportedInThePreview() throws Exception {
        arrange();
        for (int i = 0; i < 5; i++) single("SINGLE_CHOICE");
        single("SINGLE_CHOICE", nodes.get(0));
        UUID exam = exam(section(3, "MATRIX", "[" + rule("present", "PRESENT", null, a()) + "]",
                block("g1", "SINGLE_CHOICE", 3)));
        assertValid(check(exam));
        JsonNode response = previewResponse(exam, 3);
        JsonNode row = compliance(response, "MAPID:present");
        assertThat(row.path("scope").asText()).isEqualTo("SECTION:section");
        assertThat(row.path("required").asInt()).isEqualTo(1);
        assertThat(row.path("actual").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(carriers(questionIds(response.path("paper")), nodes.get(0))).isGreaterThanOrEqualTo(1);
    }

    // ---- AC-3 ------------------------------------------------------------------------------------------------

    @Test
    void anExactCountOfTheSubjectIsHeldWhateverTheSeed() throws Exception {
        arrange();
        for (int i = 0; i < 10; i++) single("SINGLE_CHOICE", nodes.get(0));
        for (int i = 0; i < 10; i++) single("SINGLE_CHOICE");
        UUID exam = exam(section(6, "MATRIX", "[" + count("exact", null, "EXACT", 2, a()) + "]",
                block("g1", "SINGLE_CHOICE", 6)));
        assertValid(check(exam));
        for (int seed = 1; seed <= 4; seed++) {
            JsonNode response = previewResponse(exam, seed);
            assertThat(carriers(questionIds(response.path("paper")), nodes.get(0))).isEqualTo(2);
            JsonNode row = compliance(response, "MAPID:exact");
            assertThat(row.path("required").asInt()).isEqualTo(2);
            assertThat(row.path("actual").asInt()).isEqualTo(2);
        }
    }

    // ---- AC-4 ------------------------------------------------------------------------------------------------

    @Test
    void exactAndAtLeastInOneGroupAreHeldPerGroup() throws Exception {
        arrange();
        for (int i = 0; i < 8; i++) single("SINGLE_CHOICE", nodes.get(0));
        for (int i = 0; i < 8; i++) single("SINGLE_CHOICE");
        for (int i = 0; i < 8; i++) single("SHORT_ANSWER", nodes.get(0));
        for (int i = 0; i < 8; i++) single("SHORT_ANSWER");
        UUID exam = exam(section(8, "MATRIX", "[" + count("exact", "g1", "EXACT", 1, a()) + ","
                + count("atLeast", "g2", "AT_LEAST", 2, a()) + "]",
                block("g1", "SINGLE_CHOICE", 4), block("g2", "SHORT_ANSWER", 4)));
        assertValid(check(exam));
        for (int seed = 1; seed <= 3; seed++) {
            JsonNode response = previewResponse(exam, seed);
            JsonNode exact = compliance(response, "MAPID:exact");
            assertThat(exact.path("scope").asText()).isEqualTo("BLOCK:g1");
            assertThat(exact.path("actual").asInt()).isEqualTo(1);
            JsonNode atLeast = compliance(response, "MAPID:atLeast");
            assertThat(atLeast.path("scope").asText()).isEqualTo("BLOCK:g2");
            assertThat(atLeast.path("actual").asInt()).isGreaterThanOrEqualTo(2);
            assertThat(carriers(blockQuestionIds(response.path("paper"), "g1"), nodes.get(0))).isEqualTo(1);
        }
    }

    // ---- AC-5 ------------------------------------------------------------------------------------------------

    @Test
    void aMapidOnlyInOneGroupNeverReachesTheOtherGroups() throws Exception {
        arrange();
        for (int i = 0; i < 8; i++) single("SHORT_ANSWER", nodes.get(0));
        for (int i = 0; i < 8; i++) single("SHORT_ANSWER");
        for (int i = 0; i < 12; i++) single("SINGLE_CHOICE", nodes.get(0));
        for (int i = 0; i < 12; i++) single("SINGLE_CHOICE");
        UUID exam = exam(section(6, "MATRIX", "[" + rule("only", "ONLY_IN_GROUP", "g1", a()) + "]",
                block("g1", "SHORT_ANSWER", 3), block("g2", "SINGLE_CHOICE", 3)));
        assertValid(check(exam));
        for (int seed = 1; seed <= 4; seed++) {
            JsonNode response = previewResponse(exam, seed);
            assertThat(carriers(blockQuestionIds(response.path("paper"), "g2"), nodes.get(0))).isZero();
            JsonNode row = compliance(response, "MAPID:only");
            assertThat(row.path("required").asInt()).isZero();
            assertThat(row.path("actual").asInt()).isZero();
        }
    }

    @Test
    void anOnlyInGroupRuleWithACountFillsItsGroup() throws Exception {
        arrange();
        for (int i = 0; i < 8; i++) single("SHORT_ANSWER", nodes.get(0));
        for (int i = 0; i < 8; i++) single("SHORT_ANSWER");
        for (int i = 0; i < 6; i++) single("SINGLE_CHOICE", nodes.get(0));
        for (int i = 0; i < 6; i++) single("SINGLE_CHOICE");
        UUID exam = exam(section(6, "MATRIX", "[{\"id\":\"only\",\"type\":\"ONLY_IN_GROUP\",\"groupId\":\"g1\","
                + "\"comparator\":\"EXACT\",\"count\":2,\"nodeIds\":[\"" + nodes.get(0) + "\"]}]",
                block("g1", "SHORT_ANSWER", 3), block("g2", "SINGLE_CHOICE", 3)));
        JsonNode response = previewResponse(exam, 2);
        assertThat(carriers(blockQuestionIds(response.path("paper"), "g1"), nodes.get(0))).isEqualTo(2);
        assertThat(carriers(blockQuestionIds(response.path("paper"), "g2"), nodes.get(0))).isZero();
        assertThat(compliance(response, "MAPID:only:COUNT").path("actual").asInt()).isEqualTo(2);
    }

    // ---- AC-6 ------------------------------------------------------------------------------------------------

    @Test
    void aGroupSourceOfSeveralMapidsGeneratesWholeSetsEvenWhenOneCodeHasNoSource() throws Exception {
        arrange();
        UUID setA = set(List.of(nodes.get(0)), List.of(nodes.get(0)));
        UUID setB = set(List.of(nodes.get(1)), List.of(nodes.get(1)));
        set(List.of(nodes.get(2)), List.of());
        set(List.of(), List.of());
        // C (nodes[2]) is declared but only half of a set carries it: it has no whole source of its own.
        UUID exam = exam(section(2, "MATRIX", "[" + rule("source", "SOURCE", "g1", a(), b(), c()) + "]",
                setBlock("g1", 1, 2)));
        assertValid(check(exam));
        for (int seed = 1; seed <= 4; seed++) {
            JsonNode paper = preview(exam, seed);
            JsonNode item = paper.path("parts").get(0).path("blocks").get(0).path("items").get(0);
            assertThat(item.path("kind").asText()).isEqualTo("SET");
            assertThat(item.path("setId").asText()).isIn(setA.toString(), setB.toString());
            for (String id : questionIds(paper)) {
                assertThat(carriers(List.of(id), nodes.get(0), nodes.get(1), nodes.get(2))).isEqualTo(1);
            }
        }
    }

    @Test
    void aSetShortageReportsSetsAndSinglesInTheirOwnUnit() throws Exception {
        arrange();
        for (int i = 0; i < 6; i++) single("SINGLE_CHOICE", nodes.get(0));
        set(List.of(nodes.get(1)), List.of(nodes.get(1)), List.of(nodes.get(1)));
        UUID exam = exam(section(3, "MATRIX", "[" + rule("source", "SOURCE", "g1", a()) + "]",
                setBlock("g1", 1, 3)));
        JsonNode check = check(exam);
        assertThat(check.path("status").asText()).isEqualTo("INVALID");
        JsonNode issue = issue(check, "SET_SHORTAGE");
        assertThat(issue.path("blockId").asText()).isEqualTo("g1");
        assertThat(issue.path("requiredSets").asInt()).isEqualTo(1);
        assertThat(issue.path("availableSets").asInt()).isZero();
        assertThat(issue.path("availableSingles").asInt()).isEqualTo(6);
    }

    // ---- AC-7 ------------------------------------------------------------------------------------------------

    @Test
    void aSubjectSourceKeepsEveryQuestionInsideTheChosenMapids() throws Exception {
        arrange();
        for (int i = 0; i < 6; i++) single("SINGLE_CHOICE", nodes.get(0));
        for (int i = 0; i < 6; i++) single("SINGLE_CHOICE", nodes.get(1));
        for (int i = 0; i < 10; i++) single("SINGLE_CHOICE", nodes.get(2));
        for (int i = 0; i < 10; i++) single("SINGLE_CHOICE");
        set(List.of(nodes.get(0)), List.of(nodes.get(2)));
        UUID exam = exam(section(8, "MATRIX", "[" + rule("source", "SOURCE", null, a(), b()) + "]",
                block("g1", "SINGLE_CHOICE", 8)));
        assertValid(check(exam));
        for (int seed = 1; seed <= 4; seed++) {
            List<String> ids = questionIds(preview(exam, seed));
            assertThat(ids).hasSize(8);
            assertThat(carriers(ids, nodes.get(0), nodes.get(1))).isEqualTo(8);
        }
    }

    // ---- AC-8 ------------------------------------------------------------------------------------------------

    @Test
    void aQuestionWithTwoMapidsFillsOneSlotAndTwoRulesNeedTwoQuestions() throws Exception {
        arrange();
        single("SINGLE_CHOICE", nodes.get(0), nodes.get(1));
        for (int i = 0; i < 4; i++) single("SINGLE_CHOICE");
        String section = section(2, "MATRIX", "[" + rule("a", "PRESENT", null, a()) + ","
                + rule("b", "PRESENT", null, b()) + "]", block("g1", "SINGLE_CHOICE", 2));
        assertThat(check(exam(section)).path("status").asText()).isEqualTo("INVALID");
        single("SINGLE_CHOICE", nodes.get(1));
        assertValid(check(exam(section)));
    }

    // ---- AC-9 ------------------------------------------------------------------------------------------------

    @Test
    void conflictingRulesNameBothRulesAndBlockTheCheck() throws Exception {
        arrange();
        for (int i = 0; i < 8; i++) single("SINGLE_CHOICE", nodes.get(0));
        UUID exam = exam(section(4, "MATRIX", "[" + count("subject", null, "EXACT", 2, a()) + ","
                + count("group", "g1", "AT_LEAST", 3, a()) + "]", block("g1", "SINGLE_CHOICE", 4)));
        JsonNode check = check(exam);
        assertThat(check.path("status").asText()).isEqualTo("INVALID");
        JsonNode conflict = issue(check, "MAPID_RULE_CONFLICT");
        assertThat(conflict.path("severity").asText()).isEqualTo("ERROR");
        assertThat(texts(conflict.path("ruleIds"))).containsExactlyInAnyOrder("subject", "group");
        assertThat(conflict.path("sectionId").asText()).isEqualTo("section");
    }

    @Test
    void aSourceRuleDoesNotAcceptASetMixingTwoOfItsCodes() throws Exception {
        arrange();
        set(List.of(nodes.get(1)), List.of(nodes.get(2)));
        String section = section(2, "MATRIX", "[" + rule("source", "SOURCE", "g1", a(), b(), c()) + "]",
                setBlock("g1", 1, 2));
        JsonNode mixed = check(exam(section));
        assertThat(mixed.path("status").asText()).isEqualTo("INVALID");
        JsonNode issue = issue(mixed, "SET_SHORTAGE");
        assertThat(issue.path("requiredSets").asInt()).isEqualTo(1);
        assertThat(issue.path("availableSets").asInt()).isZero();
        set(List.of(nodes.get(1)), List.of(nodes.get(1)));
        assertValid(check(exam(section)));
    }

    @Test
    void aRuleOfAGoneGroupIsAnErrorThatNamesTheRuleAndDoesNotBreakCheckOrPreview() throws Exception {
        arrange();
        for (int i = 0; i < 4; i++) single("SINGLE_CHOICE");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes, question_count) "
                + "values (?, ?, ?, 'Stale', 'FULL', 'SHARED', 60, 2)", id, id.toString(), id.toString());
        String document = "{\"parts\":[{\"id\":\"part\",\"order\":1,\"name\":\"Part\",\"questionCount\":2,"
                + "\"participation\":{\"mode\":\"ALL\"},\"sections\":["
                + section(2, "MATRIX", "[" + rule("stale", "PRESENT", "gone", a()) + "]", block("g1", "SINGLE_CHOICE", 2))
                + "],\"options\":[]}]}";
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'stale')",
                id, document);
        JsonNode check = check(id);
        assertThat(check.path("status").asText()).isEqualTo("INVALID");
        JsonNode issue = issue(check, "MAPID_RULE_GROUP_MISSING");
        assertThat(issue.path("ruleId").asText()).isEqualTo("stale");
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id).with(asUser(Permissions.EXAM_VIEW))
                .contentType(MediaType.APPLICATION_JSON).content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}"))
                .andExpect(status().is2xxSuccessful());
    }

    // ---- overview must not drop the declared rules (review R1-4) ----------------------------------------------

    @Test
    void aPartCountChangeThroughTheOverviewKeepsTheModeAndTheRules() throws Exception {
        arrange();
        UUID exam = exam(section(3, "MATRIX", "[" + count("exact", null, "EXACT", 1, a()) + ","
                + rule("source", "SOURCE", "g1", a(), b()) + "]", block("g1", "SINGLE_CHOICE", 3)));
        long revision = structure(exam).path("revision").asLong();
        String body = "{\"expectedRevision\":" + revision
                + ",\"overview\":[{\"partId\":\"part\",\"questionCount\":3,\"durationMinutes\":45}]}";
        mockMvc.perform(put("/api/v1/admin/exams/{id}/overview", exam).with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        JsonNode saved = structure(exam).path("parts").get(0).path("sections").get(0);
        assertThat(saved.path("mapidMode").asText()).isEqualTo("MATRIX");
        assertThat(saved.path("mapidRules")).extracting(rule -> rule.path("id").asText())
                .containsExactly("exact", "source");
    }

    // ---- Q4: the rules are an API boundary of the HSA kind, not only a UI rollout boundary --------------------

    @Test
    void aKindOtherThanHsaCannotSaveAMapidMode() throws Exception {
        arrange();
        UUID bca = examRow("bca");
        String section = section(3, "MATRIX", "[" + rule("present", "PRESENT", null, a()) + "]",
                block("g1", "SINGLE_CHOICE", 3));
        putStructure(bca, section).andExpect(status().isUnprocessableEntity());
        assertThat(structure(bca).path("parts")).isEmpty();
        String legacy = "{\"id\":\"section\",\"subjectCode\":\"" + subjectCode + "\",\"questionCount\":3,\"blocks\":["
                + block("g1", "SINGLE_CHOICE", 3) + "]}";
        putStructure(bca, legacy).andExpect(status().isOk());
    }

    // ---- rule node references are validated like legacy scopes (review R1-8) ----------------------------------

    @Test
    void aRuleNodeThatIsUnknownInactiveOrNotKnowledgeIsRejectedAtPut() throws Exception {
        arrange();
        UUID exam = examRow("hsa");
        jdbc.update("update catalog.catalog_item set status = 2 where id = ?", nodes.get(2));
        for (String bad : List.of(UUID.randomUUID().toString(), subjectId.toString(), c())) {
            putStructure(exam, section(3, "MATRIX", "[" + rule("o", "ONLY_IN_GROUP", "g1", bad) + "]",
                    block("g1", "SINGLE_CHOICE", 3))).andExpect(status().isUnprocessableEntity());
            putStructure(exam, section(3, "MATRIX", "[" + rule("s", "SOURCE", null, a(), bad) + "]",
                    block("g1", "SINGLE_CHOICE", 3))).andExpect(status().isUnprocessableEntity());
        }
        assertThat(structure(exam).path("parts")).isEmpty();
        putStructure(exam, section(3, "MATRIX", "[" + rule("o", "ONLY_IN_GROUP", "g1", a(), b()) + "]",
                block("g1", "SINGLE_CHOICE", 3))).andExpect(status().isOk());
    }

    @Test
    void aNodeOnlyNamedByARuleCountsAsUsedByTheExam() throws Exception {
        arrange();
        long before = usedBy(nodes.get(1));
        UUID exam = exam(section(3, "MATRIX", "[" + count("c", null, "EXACT", 1, b()) + ","
                + rule("o", "ONLY_IN_GROUP", "g1", c()) + "]", block("g1", "SINGLE_CHOICE", 3)));
        assertThat(usedBy(nodes.get(1))).isEqualTo(before + 1);
        assertThat(catalogUsage.findUsages("KNOWLEDGE", nodes.get(2)).stream()
                .flatMap(usage -> usage.sampleLabels().stream())).contains(exam.toString());
    }

    private long usedBy(UUID node) {
        return catalogUsage.findUsages("KNOWLEDGE", node).stream()
                .mapToLong(vn.unicom.thi360.catalog.spi.CatalogUsage::count).sum();
    }

    @Test
    void everyRuleTypeIsSavedAndReadBack() throws Exception {
        arrange();
        UUID exam = exam(section(8, "MATRIX", "[" + rule("p", "PRESENT", null, a()) + ","
                + count("c", "g1", "EXACT", 1, b()) + ","
                + rule("o", "ONLY_IN_GROUP", "g1", c()) + ","
                + rule("s", "SOURCE", "g2", a(), b(), c()) + "]",
                block("g1", "SINGLE_CHOICE", 4), block("g2", "SHORT_ANSWER", 4)));
        JsonNode rules = structure(exam).path("parts").get(0).path("sections").get(0).path("mapidRules");
        assertThat(rules).extracting(rule -> rule.path("type").asText())
                .containsExactly("PRESENT", "COUNT", "ONLY_IN_GROUP", "SOURCE");
        assertThat(rules.get(3).path("nodeIds")).hasSize(3);
    }

    // ---- usage of a question follows the compiled rules ------------------------------------------------------

    @Test
    void questionUsageFollowsTheSourceAndTheOnlyInGroupRulesOfTheSection() throws Exception {
        arrange();
        UUID carrierA = single("SINGLE_CHOICE", nodes.get(0));
        UUID carrierB = single("SINGLE_CHOICE", nodes.get(1));
        UUID clean = single("SINGLE_CHOICE");
        exam(section(6, "MATRIX", "[" + rule("source", "SOURCE", "g1", a()) + ","
                + rule("only", "ONLY_IN_GROUP", "g1", b()) + "]",
                block("g1", "SINGLE_CHOICE", 3), block("g2", "SINGLE_CHOICE", 3)));
        // g1 draws only from A, g2 never draws B: A is usable in both groups, B nowhere, a clean question in g2 only.
        assertThat(usedBlocks(carrierA)).containsExactlyInAnyOrder("g1", "g2");
        assertThat(usedBlocks(carrierB)).isEmpty();
        assertThat(usedBlocks(clean)).containsExactly("g2");
    }

    private List<String> usedBlocks(UUID question) {
        return provider.examUsagesOf(question).stream().map(QuestionUsageProvider.ExamUsageRef::blockId).toList();
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private void arrange() {
        subjectId = UUID.randomUUID();
        subjectCode = "MAPID_" + subjectId.toString().replace("-", "");
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Mapid subject', 'Mapid subject', 0, 1, false, 1, ?)
                """, subjectId, SUBJECT_GROUP, subjectCode, subjectCode.toLowerCase(Locale.ROOT), SUBJECT_GROUP,
                "/" + subjectId + "/");
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        List<UUID> picked = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (CatalogItemView item : knowledge) {
            boolean disjoint = paths.stream().noneMatch(path -> path.startsWith(item.path()) || item.path().startsWith(path));
            if (disjoint) {
                picked.add(item.id());
                paths.add(item.path());
            }
            if (picked.size() == 3) break;
        }
        assertThat(picked).as("three unrelated knowledge nodes in the seed").hasSize(3);
        nodes = picked;
    }

    private String a() { return nodes.get(0).toString(); }
    private String b() { return nodes.get(1).toString(); }
    private String c() { return nodes.get(2).toString(); }

    private static String rule(String id, String type, String groupId, String... nodeIds) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\""
                + (groupId == null ? "" : ",\"groupId\":\"" + groupId + "\"")
                + ",\"nodeIds\":[\"" + String.join("\",\"", nodeIds) + "\"]}";
    }

    private static String count(String id, String groupId, String comparator, int count, String nodeId) {
        return "{\"id\":\"" + id + "\",\"type\":\"COUNT\""
                + (groupId == null ? "" : ",\"groupId\":\"" + groupId + "\"")
                + ",\"comparator\":\"" + comparator + "\",\"count\":" + count + ",\"nodeIds\":[\"" + nodeId + "\"]}";
    }

    private static String block(String id, String type, int count) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"questionType\":\"" + type + "\",\"questionCount\":" + count + "}";
    }

    private static String setBlock(String id, int sets, int size) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"questionType\":\"QUESTION_SET\",\"questionCount\":"
                + sets * size + ",\"setCount\":" + sets + ",\"setSize\":" + size
                + ",\"childAnswerType\":\"SINGLE_CHOICE\"}";
    }

    private String section(int count, String mode, String rules, String... blocks) {
        return "{\"id\":\"section\",\"subjectCode\":\"" + subjectCode + "\",\"questionCount\":" + count
                + ",\"mapidMode\":\"" + mode + "\",\"mapidRules\":" + rules + ",\"blocks\":[" + String.join(",", blocks) + "]}";
    }

    /** Creates an HSA exam row, then saves the structure through PUT like the web does. */
    private UUID exam(String section) throws Exception {
        UUID id = examRow("hsa");
        putStructure(id, section).andExpect(status().isOk());
        jdbc.update("update exam.exam set question_count = ? where id = ?",
                mapper.readTree(section).path("questionCount").asInt(), id);
        return id;
    }

    private UUID examRow(String kindCode) throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes, kind_item_id) "
                + "values (?, ?, ?, 'Mapid', 'FULL', 'SHARED', 60, (select id from catalog.catalog_item "
                + "where group_id = '00000000-0000-7000-8100-000000000001' and code_normalized = ?))",
                id, id.toString(), id.toString(), kindCode);
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'empty')",
                id, "{\"parts\":[]}");
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions putStructure(UUID id, String section) throws Exception {
        long revision = structure(id).path("revision").asLong();
        int count = mapper.readTree(section).path("questionCount").asInt();
        String parts = "[{\"id\":\"part\",\"order\":1,\"name\":\"Part\",\"questionCount\":" + count
                + ",\"participation\":{\"mode\":\"ALL\"},\"sections\":[" + section + "],\"options\":[]}]";
        String body = "{\"expectedRevision\":" + revision + ",\"parts\":" + parts + "}";
        return mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode structure(UUID id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static void assertValid(JsonNode check) {
        assertThat(check.path("status").asText()).as(issues(check).toString()).isEqualTo("VALID");
    }

    private JsonNode check(UUID id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode previewResponse(UUID id, int seed) throws Exception {
        JsonNode response = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + seed + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(response.path("status").asText()).as(response.path("issues").toString()).isEqualTo("GENERATED");
        return response;
    }

    private JsonNode preview(UUID id, int seed) throws Exception {
        return previewResponse(id, seed).path("paper");
    }

    private static JsonNode compliance(JsonNode response, String criterion) {
        for (JsonNode row : response.path("compliance")) {
            if (criterion.equals(row.path("criterion").asText())) return row;
        }
        throw new AssertionError("Missing compliance row " + criterion + " in " + response.path("compliance"));
    }

    private static List<JsonNode> issues(JsonNode check) {
        return check.path("groups").findValues("issues").stream()
                .flatMap(rows -> StreamSupport.stream(rows.spliterator(), false)).toList();
    }

    private static JsonNode issue(JsonNode check, String code) {
        return issues(check).stream().filter(issue -> code.equals(issue.path("code").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing issue " + code + " in " + codes(check)));
    }

    private static Set<String> codes(JsonNode check) {
        Set<String> codes = new LinkedHashSet<>();
        issues(check).forEach(issue -> codes.add(issue.path("code").asText()));
        return codes;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<String> questionIds(JsonNode paper) {
        List<String> ids = new ArrayList<>();
        for (JsonNode part : paper.path("parts")) for (JsonNode block : part.path("blocks")) collect(block, ids);
        return ids;
    }

    private static List<String> blockQuestionIds(JsonNode paper, String blockId) {
        List<String> ids = new ArrayList<>();
        for (JsonNode part : paper.path("parts")) for (JsonNode block : part.path("blocks")) {
            if (blockId.equals(block.path("blockId").asText())) collect(block, ids);
        }
        return ids;
    }

    private static void collect(JsonNode block, List<String> ids) {
        for (JsonNode item : block.path("items")) {
            if ("SET".equals(item.path("kind").asText())) {
                for (JsonNode child : item.path("children")) ids.add(child.path("questionId").asText());
            } else {
                ids.add(item.path("question").path("questionId").asText());
            }
        }
    }

    /** Questions among {@code ids} that carry at least one of the nodes (or a descendant, as the rules count). */
    private int carriers(List<String> ids, UUID... wanted) {
        List<String> paths = new ArrayList<>();
        for (UUID node : wanted) {
            paths.add(catalog.listActiveGroupItems("KNOWLEDGE").stream().filter(item -> item.id().equals(node))
                    .findFirst().orElseThrow().path());
        }
        int count = 0;
        for (String id : ids) {
            List<String> own = jdbc.queryForList("""
                    select k.path from questionbank.question_knowledge qk
                    join catalog.catalog_item k on k.id = qk.knowledge_item_id
                    where qk.question_id = cast(? as uuid)
                    """, String.class, id);
            if (own.stream().anyMatch(path -> paths.stream().anyMatch(path::startsWith))) count++;
        }
        return count;
    }

    private UUID single(String type, UUID... knowledge) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,?,'READY',?,1,1)", id, "M-" + id, type, subjectId);
        String body = type.equals("SHORT_ANSWER")
                ? "{\"type\":\"SHORT_ANSWER\",\"answers\":[\"42\"]}"
                : "{\"type\":\"SINGLE_CHOICE\"," + OPTIONS + "}";
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"content\":\"q\",\"points\":1,\"body\":" + body + "}");
        boolean primary = true;
        for (UUID node : knowledge) {
            jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) values (?,?,?)",
                    id, node, primary);
            primary = false;
        }
        return id;
    }

    /** A set whose child {@code i} carries the nodes of {@code children.get(i)}. */
    @SafeVarargs
    private UUID set(List<UUID>... children) {
        UUID parent = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'QUESTION_SET','READY',?,1,?)", parent, "S-" + parent, subjectId, children.length);
        StringBuilder snapshot = new StringBuilder("{\"content\":\"set\",\"points\":" + children.length
                + ",\"body\":{\"type\":\"QUESTION_SET\",\"passage\":\"passage\",\"children\":[");
        for (int i = 0; i < children.length; i++) {
            UUID child = UUID.randomUUID();
            jdbc.update("insert into questionbank.question(id, code, type, status, parent_id, position, points) "
                    + "values (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "C-" + child, parent, i);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                    child, "{\"content\":\"child\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\"," + OPTIONS + "}}");
            boolean primary = true;
            for (UUID node : children[i]) {
                jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) values (?,?,?)",
                        child, node, primary);
                primary = false;
            }
            if (i > 0) snapshot.append(',');
            snapshot.append("{\"id\":\"").append(child).append("\",\"body\":{\"type\":\"SINGLE_CHOICE\",")
                    .append(OPTIONS).append("}}");
        }
        snapshot.append("]}}");
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                parent, snapshot.toString());
        return parent;
    }
}
