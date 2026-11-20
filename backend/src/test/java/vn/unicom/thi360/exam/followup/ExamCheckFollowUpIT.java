package vn.unicom.thi360.exam.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.application.ExamCheckSnapshotReader;
import vn.unicom.thi360.identity.spi.Permissions;

/** FU: check rules shared with the summary, option-level locks, omit lock shapes, template change before save. */
@Transactional
class ExamCheckFollowUpIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager entityManager;
    @Autowired private ExamCheckSnapshotReader snapshots;
    @Autowired private ExamCheckResultStore store;

    private void template(String code, String parts, boolean fixedCounts, boolean omittedLocked) {
        jdbc.update("INSERT INTO exam.exam_template (code, kind_code, scope, timing, structure, fixed_counts, omitted_locked) "
                + "VALUES (?, 'HSA', 'FULL', 'PER_PART', cast(? as jsonb), ?, ?)", code,
                "{\"parts\":" + parts + "}", fixedCounts, omittedLocked);
    }

    private UUID exam(String templateCode, String parts) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,template_code) "
                + "VALUES (?,?,?,'Follow-up','FULL','PER_PART',?)", id, id.toString(), id.toString(), templateCode);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,cast(? as jsonb),'fu')",
                id, "{\"parts\":" + parts + "}");
        entityManager.clear();
        return id;
    }

    private JsonNode check(UUID id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** The issues of one code, each once: the result repeats an issue in its group list and in its path list. */
    private static List<JsonNode> issues(JsonNode result, String code) {
        java.util.Map<String, JsonNode> found = new java.util.LinkedHashMap<>();
        for (JsonNode node : result.findParents("code")) {
            if (code.equals(node.path("code").asText())) {
                found.putIfAbsent(node.path("sectionId").asText() + "|" + node.path("blockId").asText(), node);
            }
        }
        return new ArrayList<>(found.values());
    }

    private static String newCode() {
        return "FU-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** R6-1: a set whose questionCount disagrees with setCount x setSize next to a remainder group. */
    @Test
    void summaryAndCheckerShareRemainderRule() throws Exception {
        // The set draws 2 x 3 = 6 (what the solver draws), so the remainder group takes 12 - 6 = 6.
        String parts = """
                [{"id":"p","order":1,"name":"P","questionCount":12,"participation":{"mode":"ALL"},"options":[],
                  "sections":[{"id":"s","subjectCode":"TOAN","questionCount":12,"blocks":[
                    {"id":"set","name":"Set","questionType":"QUESTION_SET","childAnswerType":"SINGLE_CHOICE",
                     "questionCount":10,"setCount":2,"setSize":3},
                    {"id":"rest","name":"Rest","questionType":"SINGLE_CHOICE",
                     "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REQUIRED","difficultyEnforcement":"REQUIRED",
                       "byGrade":[{"code":"G10","value":3},{"code":"G11","value":3}],
                       "byDifficulty":[{"code":"NB","value":3},{"code":"TH","value":3}]}}]}]}]
                """;
        UUID id = exam(null, parts);
        JsonNode summary = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + id + "/structure/summary")
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode rest = null;
        for (JsonNode block : summary.findParents("blockId")) {
            if ("rest".equals(block.path("blockId").asText())) rest = block;
        }
        assertThat(rest).isNotNull();
        assertThat(rest.path("allocated").asInt()).isEqualTo(6);

        JsonNode result = check(id);
        assertThat(issues(result, "MATRIX_TOTAL_MISMATCH"))
                .as("the checker gives the remainder group the same 6 questions: %s", result)
                .isEmpty();
    }

    private static String optionBlocks(int count) {
        return """
                [{"id":"p","order":1,"name":"P","questionCount":5,"participation":{"mode":"CHOICE","choose":1},"sections":[],
                  "options":[{"id":"o1","kind":"SUBJECT","code":"EN","blocks":[
                    {"id":"b","name":"B","questionType":"SINGLE_CHOICE","questionCount":%d}]}]}]
                """.formatted(count);
    }

    /** OPTION-BLOCKS: groups that sit directly under an option are compared with the template for the count lock. */
    @Test
    void optionBlocksComparedForCountLock() throws Exception {
        String code = newCode();
        template(code, optionBlocks(5), true, false);

        JsonNode same = check(exam(code, optionBlocks(5)));
        assertThat(issues(same, "STRUCTURE_COUNT_LOCKED")).isEmpty();

        JsonNode changed = check(exam(code, optionBlocks(6)));
        List<JsonNode> locked = issues(changed, "STRUCTURE_COUNT_LOCKED");
        assertThat(locked).as(changed.toString()).isNotEmpty();
        assertThat(locked).anySatisfy(issue -> {
            assertThat(issue.path("sectionId").asText()).isEqualTo("o1:blocks");
            assertThat(issue.path("required").asInt()).isEqualTo(5);
            assertThat(issue.path("available").asInt()).isEqualTo(6);
        });
    }

    private static String shape(String kind, boolean omitted, String ids, String code) {
        String block = "{\"id\":\"%s\",\"name\":\"W\",\"questionType\":\"ESSAY\",\"questionCount\":1,\"omitted\":%b%s}"
                .formatted(ids, omitted, code == null ? "" : ",\"templateGroupCode\":\"" + code + "\"");
        String keep = "{\"id\":\"%s-keep\",\"name\":\"K\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}"
                .formatted(ids);
        return switch (kind) {
            case "POOL" -> """
                    [{"id":"p","order":1,"name":"P","questionCount":1,"codeSource":{"source":"GLOBAL_LIST"},
                      "subjectPool":[{"id":"pool","subjectCode":"TOAN","questionCount":2,"blocks":[%s,%s]}]}]
                    """.formatted(keep, block);
            case "OPTION_BLOCKS" -> """
                    [{"id":"p","order":1,"name":"P","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                      "options":[{"id":"o","kind":"SUBJECT","code":"EN","blocks":[%s,%s]}]}]
                    """.formatted(keep, block);
            case "LATER_OPTION_SECTION" -> """
                    [{"id":"p","order":1,"name":"P","questionCount":1,"participation":{"mode":"CHOICE","choose":1},
                      "options":[{"id":"o1","kind":"SUBJECT","code":"EN","sections":[
                          {"id":"s1","subjectCode":"TOAN","questionCount":1,"blocks":[%s]}]},
                        {"id":"o2","kind":"SUBJECT","code":"FR","sections":[
                          {"id":"s2","subjectCode":"TOAN","questionCount":1,"blocks":[%s]},
                          {"id":"s3","subjectCode":"TOAN","questionCount":2,"blocks":[%s]}]}]}]
                    """.formatted(keep.replace(ids + "-keep", ids + "-k1"), keep, block);
            default -> throw new IllegalArgumentException(kind);
        };
    }

    /**
     * QA-OMIT-LOCK-SHAPES: the server refuses a re-enabled locked group (ExamCheckRules.reenabledBlocks) in every shape
     * a template can place an omitted group: subject pool, option groups, and a section that is not the first of its
     * option; found by id, and by template group code when the code identifies one template group.
     */
    @Test
    void omitLockMatchesReenabledBlocks() throws Exception {
        for (String kind : List.of("POOL", "OPTION_BLOCKS", "LATER_OPTION_SECTION")) {
            String code = newCode();
            template(code, shape(kind, true, "w", null), false, true);
            JsonNode kept = check(exam(code, shape(kind, true, "w", null)));
            assertThat(issues(kept, "OMITTED_BLOCK_LOCKED")).as("%s still omitted", kind).isEmpty();
            JsonNode enabled = check(exam(code, shape(kind, false, "w", null)));
            assertThat(issues(enabled, "OMITTED_BLOCK_LOCKED")).as("%s re-enabled: %s", kind, enabled)
                    .anySatisfy(issue -> assertThat(issue.path("blockId").asText()).isEqualTo("w"));
        }
        // A copy with regenerated ids is found by its template group code while the code names one template group.
        String unique = newCode();
        template(unique, shape("OPTION_BLOCKS", true, "w", "WRITING"), false, true);
        assertThat(issues(check(exam(unique, shape("OPTION_BLOCKS", false, "regenerated", "WRITING"))),
                "OMITTED_BLOCK_LOCKED")).hasSize(1);
        // An ambiguous code matches nothing (never picked arbitrarily), but the id still does.
        String ambiguous = newCode();
        String twin = shape("OPTION_BLOCKS", true, "w", "WRITING")
                .replace("{\"id\":\"w-keep\",\"name\":\"K\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}",
                        "{\"id\":\"w-keep\",\"name\":\"K\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1,"
                                + "\"templateGroupCode\":\"WRITING\"}");
        template(ambiguous, twin, false, true);
        assertThat(issues(check(exam(ambiguous, twin.replace("\"omitted\":true", "\"omitted\":false"))),
                "OMITTED_BLOCK_LOCKED")).hasSize(1);
        assertThat(issues(check(exam(ambiguous, twin.replace("\"id\":\"w\"", "\"id\":\"copy\"")
                .replace("\"omitted\":true", "\"omitted\":false"))), "OMITTED_BLOCK_LOCKED")).isEmpty();
    }

    private boolean save(ExamCheckResultStore.Input input) {
        return store.save(input, "VALID", 0, Instant.now(), 1, mapper.createObjectNode(), mapper.createObjectNode());
    }

    /** R2-3/R2-4: a template that changes between reading the check input and saving the result is detected. */
    @Test
    void templateChangeBetweenCheckAndSaveDetected() throws Exception {
        String code = newCode();
        template(code, optionBlocks(5), false, false);
        UUID unchanged = exam(code, optionBlocks(5));
        assertThat(save(snapshots.read(unchanged).persistenceInput())).isTrue();

        // A lock turned on after the check read its input: the result was solved without it and must not be saved.
        UUID flagged = exam(code, optionBlocks(5));
        var flagInput = snapshots.read(flagged).persistenceInput();
        jdbc.update("UPDATE exam.exam_template SET fixed_counts = true WHERE code = ?", code);
        assertThat(save(flagInput)).isFalse();
        assertThat(jdbc.queryForObject("SELECT check_status FROM exam.exam WHERE id = ?", String.class, flagged))
                .isEqualTo("UNCHECKED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id = ?", Integer.class, flagged))
                .isZero();
        // The same input read after the change carries the new policy and saves.
        assertThat(save(snapshots.read(flagged).persistenceInput())).isTrue();

        // In-place change of the locked counts while the flag stays true.
        UUID edited = exam(code, optionBlocks(5));
        var editedInput = snapshots.read(edited).persistenceInput();
        jdbc.update("UPDATE exam.exam_template SET structure = cast(? as jsonb) WHERE code = ?",
                "{\"parts\":" + optionBlocks(7) + "}", code);
        assertThat(save(editedInput)).isFalse();
        assertThat(save(snapshots.read(edited).persistenceInput())).isTrue();
    }

    // ---- AC-2: flexible diagnostic, SP2E statement points, BE-13 R3-1 --------------------------------------------

    private record Subject(UUID id, String code) { }

    private Subject subject() {
        UUID id = UUID.randomUUID();
        String code = "FU_" + id.toString().replace("-", "");
        UUID group = UUID.fromString("00000000-0000-7000-8100-000000000002");
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) VALUES (?,?,?,?,"
                + "(SELECT coalesce(max(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),'FU',?)",
                id, group, code, code.toLowerCase(java.util.Locale.ROOT), group, "/" + id + "/");
        return new Subject(id, code);
    }

    private void trueFalse(Subject subject, String method, String points, String statements) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'TRUE_FALSE','READY',?,1,?)", id, "Q-" + id, subject.id(), new java.math.BigDecimal(points));
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id,
                "{\"body\":{\"type\":\"TRUE_FALSE\",\"scoringMethod\":\"" + method + "\","
                        + ("COUNT_TABLE".equals(method) ? "\"scoreTable\":[0.1,0.25,0.5,1]," : "")
                        + "\"statements\":" + statements + "}}");
    }

    private static String statements(String... points) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < points.length; i++) {
            out.append(i == 0 ? "" : ",").append("{\"id\":\"s").append(i).append("\",\"answer\":")
                    .append(i % 2 == 0).append(",\"points\":").append(points[i]).append("}");
        }
        return out.append("]").toString();
    }

    private static final String EVEN = statements("0.2", "0.2", "0.2", "0.2");
    private static final String UNEVEN = statements("0.1", "0.3", "0.2", "0.2");

    private UUID kind(String code) {
        return jdbc.queryForObject("SELECT i.id FROM catalog.catalog_item i JOIN catalog.catalog_group g ON g.id = i.group_id "
                + "WHERE g.code = 'EXAM' AND i.code = ?", UUID.class, code);
    }

    /** A FULL exam of one subject and one True/False block, checked through the API. */
    private JsonNode checkBlock(String kindCode, Subject subject, int count, String block) throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,kind_item_id,scope,timing,question_count,duration_minutes) "
                + "VALUES (?,?,?,'FU',?,'FULL','SHARED',?,60)", id, id.toString(), id.toString(), kind(kindCode), count);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'fu')", id, """
                {"parts":[{"id":"p","order":1,"name":"P","questionCount":%d,"sections":[
                  {"id":"s","subjectCode":"%s","questionCount":%d,"blocks":[%s]}]}]}
                """.formatted(count, subject.code(), count, block));
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "b", "p", "s", subject.id());
        entityManager.clear();
        return check(id);
    }

    private static String perStatementBlock(int count) {
        return "{\"id\":\"b\",\"questionType\":\"TRUE_FALSE\",\"questionCount\":" + count
                + ",\"requiredPointsPerQuestion\":0.8,\"expectedScoring\":{\"trueFalseMethod\":\"PER_STATEMENT\"}}";
    }

    /**
     * FU-BE9-2: a True/False question of an SP2E block scored per statement must split its points 0.2 a statement. The
     * source filter leaves an uneven question out (the paper stays valid while enough even questions remain) and reports
     * a shortage only when too few remain.
     */
    @Test
    void sp2eTrueFalseStatementPoints() throws Exception {
        Subject enough = subject();
        trueFalse(enough, "PER_STATEMENT", "0.8", EVEN);
        trueFalse(enough, "PER_STATEMENT", "0.8", EVEN);
        for (int i = 0; i < 3; i++) trueFalse(enough, "PER_STATEMENT", "0.8", UNEVEN);
        JsonNode valid = checkBlock("SP2E", enough, 2, perStatementBlock(2));
        assertThat(valid.path("status").asText()).as(valid.toString()).isEqualTo("VALID");

        Subject short_ = subject();
        trueFalse(short_, "PER_STATEMENT", "0.8", EVEN);
        for (int i = 0; i < 3; i++) trueFalse(short_, "PER_STATEMENT", "0.8", UNEVEN);
        JsonNode invalid = checkBlock("SP2E", short_, 2, perStatementBlock(2));
        assertThat(invalid.path("status").asText()).as(invalid.toString()).isEqualTo("INVALID");
        assertThat(invalid.findValuesAsText("code")).anyMatch(code -> code.endsWith("SHORTAGE"));
    }

    /** A COUNT_TABLE block of an SP2E exam keeps the approved table: uneven statements are still chosen, checked and valid. */
    @Test
    void sp2eCountTableBlockUnaffected() throws Exception {
        Subject subject = subject();
        for (int i = 0; i < 3; i++) trueFalse(subject, "COUNT_TABLE", "1", UNEVEN);
        JsonNode result = checkBlock("SP2E", subject, 2, "{\"id\":\"b\",\"questionType\":\"TRUE_FALSE\",\"questionCount\":2,"
                + "\"requiredPointsPerQuestion\":1,\"expectedScoring\":{\"trueFalseMethod\":\"COUNT_TABLE\","
                + "\"trueFalseTable\":[0.1,0.25,0.5,1]}}");
        assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("VALID");
    }

    /** The same uneven questions stay usable in another kind (THPTQG), even in a block declared per statement. */
    @Test
    void unevenStatementsAllowedOutsideSp2e() throws Exception {
        Subject subject = subject();
        for (int i = 0; i < 3; i++) trueFalse(subject, "PER_STATEMENT", "0.8", UNEVEN);
        JsonNode result = checkBlock("THPTQG", subject, 2, perStatementBlock(2));
        assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("VALID");
    }

    @Autowired private vn.unicom.thi360.exam.application.SolverInputBuilder builder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private vn.unicom.thi360.questionbank.spi.QuestionSourcePort source;

    private UUID flexibleSet(Subject subject, int children, UUID positionZeroNode) {
        UUID root = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'QUESTION_SET','READY',?,1,1)", root, "Q-" + root, subject.id());
        StringBuilder snapshot = new StringBuilder("{\"type\":\"QUESTION_SET\",\"children\":[");
        String child = "\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"correct\":true},{\"id\":\"b\",\"correct\":false}]}";
        for (int position = 0; position < children; position++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) "
                    + "VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1)", id, "CH-" + id, root, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                    id, "{" + child + "}");
            jdbc.update("INSERT INTO questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) VALUES (?,?,true)",
                    id, positionZeroNode);
            snapshot.append(position > 0 ? "," : "").append("{\"id\":\"").append(id).append("\",").append(child).append("}");
        }
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", root,
                "{\"body\":" + snapshot.append("]}") + "}");
        return root;
    }

    private List<UUID> twoDisjointNodes() {
        List<vn.unicom.thi360.catalog.spi.CatalogItemView> all = catalog().listActiveGroupItems("KNOWLEDGE");
        var first = all.stream().filter(a -> all.stream().anyMatch(b -> !b.path().startsWith(a.path())
                && !a.path().startsWith(b.path()))).findFirst().orElseThrow();
        var second = all.stream().filter(item -> !item.path().startsWith(first.path()) && !first.path().startsWith(item.path()))
                .findFirst().orElseThrow();
        return List.of(first.id(), second.id());
    }

    @Autowired private vn.unicom.thi360.catalog.spi.CatalogLookup catalogLookup;

    private vn.unicom.thi360.catalog.spi.CatalogLookup catalog() {
        return catalogLookup;
    }

    /**
     * FU-BE12-1: the optional flexible filter-shortage diagnostic stops at the check deadline and answers nothing, so the
     * base shortage stays; before the deadline it blames the relaxed condition as before.
     */
    @Test
    void flexibleDiagnosticStopsAtDeadline() throws Exception {
        Subject subject = subject();
        List<UUID> nodes = twoDisjointNodes();
        flexibleSet(subject, 4, nodes.get(0));
        flexibleSet(subject, 3, nodes.get(1));
        var document = new vn.unicom.thi360.exam.domain.ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","order":1,"name":"P","questionCount":3,"sections":[
                  {"id":"s","subjectCode":"%s","questionCount":3,"blocks":[
                    {"id":"b","questionType":"QUESTION_SET","questionCount":3,
                     "childConditions":[{"from":1,"to":1,"nodeIds":["%s"]}]}]}]}]}
                """.formatted(subject.code(), nodes.get(0)));
        var input = builder.build(null, document);
        var group = ((vn.unicom.thi360.exam.domain.solver.SolvePart.Fixed) input.parts().getFirst())
                .sections().getFirst().groups().getFirst();
        assertThat(input.candidates().filterCounts(group, 3, () -> false)).singleElement()
                .extracting(count -> count.dimension())
                .isEqualTo(vn.unicom.thi360.exam.application.SolverInputBuilder.SolverCandidates.FilterDimension.CHILD_CONDITION);

        org.mockito.Mockito.clearInvocations(source);
        java.util.concurrent.atomic.AtomicInteger polls = new java.util.concurrent.atomic.AtomicInteger();
        // The deadline passes after the first look: nothing is counted and no relaxed pool is read.
        assertThat(input.candidates().filterCounts(group, 3, () -> polls.incrementAndGet() > 1)).isEmpty();
        org.mockito.Mockito.verify(source, org.mockito.Mockito.times(1))
                .findCandidates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.clearInvocations(source);
        assertThat(input.candidates().filterCounts(group, 3, () -> true)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(source);
    }

    /**
     * FU-BE12-2: a large configured flexible target with a positional filter and almost no supply is INVALID with
     * SET_SHORTAGE, and no filter is blamed for a pool that could not compose the target either way.
     */
    @Test
    void largeFlexibleTargetReportsSetShortage() throws Exception {
        Subject subject = subject();
        List<UUID> nodes = twoDisjointNodes();
        flexibleSet(subject, 3, nodes.get(1));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count) "
                + "VALUES (?,?,?,'Large','FULL','SHARED',1000)", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'large')", id, """
                {"parts":[{"id":"p","questionCount":1000,"sections":[
                  {"id":"s","subjectCode":"%s","questionCount":1000,"blocks":[
                    {"id":"b","questionType":"QUESTION_SET","questionCount":1000,
                     "childConditions":[{"from":1,"to":1,"nodeIds":["%s"]}]}]}]}]}
                """.formatted(subject.code(), nodes.get(0)));
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "b", "p", "s", subject.id());
        entityManager.clear();
        JsonNode result = check(id);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.findValuesAsText("code")).contains("SET_SHORTAGE").doesNotContain("CHILD_CONDITION_SHORTAGE");
    }

    /**
     * BE-13 R3-1 (FU-BE13-3): a V-ACT exam scored RAW that loads a review template keeps its scoring and the audit
     * says scoringReset = false; a LINEAR one is reset as before.
     */
    @Test
    void be13R31Regression() throws Exception {
        JsonNode exam = mapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\",\"durationMinutes\":45,"
                                + "\"name\":\"R31\",\"code\":\"R31-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = exam.path("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"practiceScoring\":\"RAW\",\"expectedRevision\":" + exam.path("revision").asLong() + "}"))
                .andExpect(status().isOk());
        long revision = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andReturn().getResponse().getContentAsString())
                .path("revision").asLong();
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"VACT_REVIEW\",\"confirmReplace\":true,\"overviewPolicy\":\"USE_TEMPLATE\","
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        JsonNode loaded = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}", id)
                .with(asUser(Permissions.EXAM_VIEW))).andReturn().getResponse().getContentAsString());
        assertThat(loaded.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(jdbc.queryForList("SELECT payload->'after'->>'scoringReset' FROM shared.outbox_event "
                + "WHERE topic='audit' AND payload->>'action'='EXAM.TEMPLATE_LOAD' AND payload->>'targetId'=?", String.class, id))
                .containsExactly("false");
    }
}
