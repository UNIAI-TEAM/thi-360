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
}
