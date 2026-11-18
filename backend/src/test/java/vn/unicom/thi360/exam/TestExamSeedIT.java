package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator;
import vn.unicom.thi360.exam.application.SolverInputBuilder;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.questionbank.domain.QuestionInput;
import vn.unicom.thi360.questionbank.domain.QuestionSearchNormalizer;
import vn.unicom.thi360.questionbank.domain.QuestionSnapshot;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Transactional
class TestExamSeedIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;
    @Autowired SolverInputBuilder solverInputBuilder;
    private static final Map<String, String> OLD_PROFILE_SCOPES = Map.of(
            "HSA", "FULL,PART",
            "QDA", "FULL,PART",
            "BCA", "FULL,PART",
            "VACT", "FULL",
            "SPT", "SUBJECT",
            "SP2E", "SUBJECT",
            "THPTQG", "SUBJECT");

    private static final Map<String, String> EXPECTED_PROFILE_SCOPES = Map.of(
            "HSA", "FULL,PART,SUBJECT",
            "QDA", "FULL,PART,SUBJECT",
            "BCA", "FULL,PART,SUBJECT",
            "VACT", "FULL",
            "SPT", "SUBJECT,PART",
            "SP2E", "SUBJECT,PART",
            "THPTQG", "SUBJECT");

    @Test
    void seededHsaMathContextHasKnownChildAnswerType() throws Exception {
        String json = jdbc.queryForObject("""
            SELECT parts::text FROM exam.exam_structure
            WHERE exam_id = '00000000-0000-7000-8700-000000000001'
            """, String.class);
        JsonNode section = mapper.readTree(json).path("parts").get(0).path("sections").get(0);
        JsonNode block = java.util.stream.StreamSupport.stream(section.path("blocks").spliterator(), false)
            .filter(node -> "HSA_MATH_CONTEXT".equals(node.path("templateGroupCode").asText()))
            .findFirst().orElseThrow();
        assertThat(block.path("childAnswerType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(section.path("answerQuotas").path("items").get(0).path("value").asInt()).isEqualTo(35);
        assertThat(section.path("answerQuotas").path("items").get(1).path("value").asInt()).isEqualTo(15);
    }

    @Test
    void seededExamStructuresContainNoLegacyOrderingFields() throws Exception {
        List<String> structures = jdbc.queryForList("SELECT parts::text FROM exam.exam_structure", String.class);
        assertThat(structures).isNotEmpty();
        for (String structure : structures) {
            JsonNode parts = mapper.readTree(structure);
            assertThat(parts.findValues("groupOrder")).isEmpty();
            assertThat(parts.findValues("shuffle")).isEmpty();
        }
    }

    @Test
    void repairMigrationCleansPersistedLegacyRowsWithoutRevisionOrApiLeak() throws Exception {
        UUID subjectId = UUID.fromString("00000000-0000-7000-8200-000000000201");
        UUID setId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID matchingId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID checkRunId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO questionbank.question
                    (id, code, type, status, subject_item_id, current_version_no, points, revision)
                VALUES (?, 'LEGACY-REPAIR-SET', 'QUESTION_SET', 'READY', ?, 1, 2, 41)
                """, setId, subjectId);
        jdbc.update("""
                INSERT INTO questionbank.question
                    (id, code, type, status, parent_id, position, subject_item_id,
                     current_version_no, points, revision)
                VALUES (?, 'LEGACY-REPAIR-SET-CHILD', 'SINGLE_CHOICE', 'READY', ?, 0, ?, 1, 1, 17)
                """, childId, setId, subjectId);
        jdbc.update("""
                INSERT INTO questionbank.question
                    (id, code, type, status, subject_item_id, current_version_no, points, revision)
                VALUES (?, 'LEGACY-REPAIR-MATCHING', 'MATCHING', 'READY', ?, 1, 2, 19)
                """, matchingId, subjectId);

        String childSnapshot = """
                {"content":"legacy child","points":1,
                 "body":{"type":"SINGLE_CHOICE","shuffleOptions":true,
                         "options":[{"id":"a","content":"A","correct":true},
                                    {"id":"b","content":"B","correct":false}]}}
                """;
        String setSnapshot = """
                {"content":"legacy set","points":2,
                 "body":{"type":"QUESTION_SET","passage":"legacy passage","children":[{
                   "id":"%s","type":"SINGLE_CHOICE","content":"legacy child","points":1,
                   "body":{"type":"SINGLE_CHOICE","shuffleOptions":true,
                           "options":[{"id":"a","content":"A","correct":true},
                                      {"id":"b","content":"B","correct":false}]}
                 }]}}
                """.formatted(childId);
        String matchingSnapshot = """
                {"content":"legacy matching","points":2,
                 "body":{"type":"MATCHING","left":[{"id":"l","content":"L"}],
                         "right":[{"id":"r","content":"R"}],
                         "pairs":[{"leftId":"l","rightId":"r","points":2}],
                         "reuseRight":false,"shuffleLeft":true,"shuffleRight":false}}
                """;
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot, ready_at) "
                + "VALUES (?, 1, ?::jsonb, now())", childId, childSnapshot);
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot, ready_at) "
                + "VALUES (?, 1, ?::jsonb, now())", setId, setSnapshot);
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot, ready_at) "
                + "VALUES (?, 1, ?::jsonb, now())", matchingId, matchingSnapshot);
        jdbc.update("""
                INSERT INTO questionbank.question_source_revision(subject_item_id, revision)
                VALUES (?, 73) ON CONFLICT (subject_item_id) DO NOTHING
                """, subjectId);

        String legacyParts = """
                {"parts":[{"id":"p","order":1,"name":"Legacy part","questionCount":1,
                  "participation":{"mode":"ALL","choose":null},"sections":[{
                    "id":"s","subjectCode":"TOAN","questionCount":1,"groupOrder":"SHUFFLE",
                    "blocks":[
                      {"id":"b-null","questionType":"QUESTION_SET","questionCount":1,
                       "setCount":1,"setSize":1,"templateGroupCode":"HSA_MATH_CONTEXT",
                       "childAnswerType":null,"shuffle":"FIXED"},
                      {"id":"b-edited","questionType":"QUESTION_SET","questionCount":1,
                       "setCount":1,"setSize":1,"templateGroupCode":"HSA_MATH_CONTEXT",
                       "childAnswerType":"MULTIPLE_CHOICE","shuffle":"SHUFFLE"}
                    ]}],"options":[]}]}
                """;
        jdbc.update("""
                INSERT INTO exam.exam
                    (id, code, code_normalized, name, scope, timing, status, serving_version,
                     check_status, has_unpublished_changes, revision)
                VALUES (?, 'LEGACY-REPAIR-EXAM', 'legacy-repair-exam', 'Legacy repair',
                        'FULL', 'SHARED', 'PUBLISHED', 1, 'VALID', false, 13)
                """, examId);
        jdbc.update("INSERT INTO exam.exam_structure(exam_id, parts, structure_hash) VALUES (?, ?::jsonb, 'stable')",
                examId, legacyParts);
        jdbc.update("""
                INSERT INTO exam.exam_template
                    (code, kind_code, scope, timing, structure)
                VALUES ('LEGACY_REPAIR_TEMPLATE', 'HSA', 'FULL', 'SHARED', ?::jsonb)
                """, legacyParts);
        jdbc.update("""
                INSERT INTO exam.exam_version
                    (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids,
                     check_run_id, state, status, published_at, published_by)
                VALUES (?, ?, 1, '{}'::jsonb, ?::jsonb, ARRAY['default'], ARRAY['default'],
                        ?, 'SERVING', 'PUBLISHED', now(), ?)
                """, versionId, examId, legacyParts, checkRunId, publisherId);

        List<Map<String, Object>> questionRevisions = jdbc.queryForList(
                "SELECT id::text AS id, revision FROM questionbank.question ORDER BY id::text");
        List<Map<String, Object>> sourceRevisions = jdbc.queryForList(
                "SELECT subject_item_id::text AS id, revision FROM questionbank.question_source_revision "
                        + "ORDER BY subject_item_id::text");
        List<Map<String, Object>> examRevisions = jdbc.queryForList(
                "SELECT id::text AS id, revision FROM exam.exam ORDER BY id::text");
        Map<String, Object> examState = jdbc.queryForMap(
                "SELECT status, check_status, serving_version, has_unpublished_changes, revision "
                        + "FROM exam.exam WHERE id = ?", examId);

        runRepairMigration();

        String repairedSetSnapshot = jdbc.queryForObject(
                "SELECT snapshot::text FROM questionbank.question_version WHERE question_id = ?", String.class, setId);
        String repairedChildSnapshot = jdbc.queryForObject(
                "SELECT snapshot::text FROM questionbank.question_version WHERE question_id = ?", String.class, childId);
        String repairedMatchingSnapshot = jdbc.queryForObject(
                "SELECT snapshot::text FROM questionbank.question_version WHERE question_id = ?",
                String.class, matchingId);
        String repairedTemplate = jdbc.queryForObject(
                "SELECT structure::text FROM exam.exam_template WHERE code = 'LEGACY_REPAIR_TEMPLATE'",
                String.class);
        String repairedStructure = jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, examId);
        String repairedVersion = jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_version WHERE id = ?", String.class, versionId);
        assertNoLegacyKeys(mapper.readTree(repairedSetSnapshot));
        assertNoLegacyKeys(mapper.readTree(repairedChildSnapshot));
        assertNoLegacyKeys(mapper.readTree(repairedMatchingSnapshot));
        assertNoLegacyKeys(mapper.readTree(repairedTemplate));
        assertNoLegacyKeys(mapper.readTree(repairedStructure));
        assertNoLegacyKeys(mapper.readTree(repairedVersion));
        assertHsaMathTypes(mapper.readTree(repairedTemplate));
        assertHsaMathTypes(mapper.readTree(repairedStructure));
        assertThat(jdbc.queryForObject(
                "SELECT structure_hash FROM exam.exam_structure WHERE exam_id = ?", String.class, examId))
                .isEqualTo("stable");
        assertThat(jdbc.queryForMap(
                "SELECT status, check_status, serving_version, has_unpublished_changes, revision "
                        + "FROM exam.exam WHERE id = ?", examId)).isEqualTo(examState);

        JsonNode setResponse = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/{id}", setId)
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode matchingResponse = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/{id}", matchingId)
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode childResponse = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/{id}", childId)
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode templates = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode structureResponse = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", examId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode versionResponse = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/versions/1", examId)
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertNoLegacyKeysInApi(setResponse);
        assertNoLegacyKeysInApi(childResponse);
        // Concrete paths must carry the field: findValues below is vacuous when absent entirely.
        JsonNode standaloneBody = childResponse.path("body");
        assertThat(standaloneBody.path("type").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(standaloneBody.get("shuffleOptions")).as("storage without shuffleOptions reads as true").isNotNull();
        assertThat(standaloneBody.path("shuffleOptions").isBoolean() && standaloneBody.path("shuffleOptions").asBoolean())
                .as("standalone child body shuffleOptions").isTrue();
        JsonNode embeddedBody = setResponse.path("body").path("children").get(0).path("body");
        assertThat(embeddedBody.get("shuffleOptions")).as("embedded child body must expose shuffleOptions").isNotNull();
        assertThat(embeddedBody.path("shuffleOptions").isBoolean() && embeddedBody.path("shuffleOptions").asBoolean())
                .as("set child body shuffleOptions").isTrue();
        assertNoLegacyKeysInApi(matchingResponse);
        assertNoLegacyKeysInApi(structureResponse);
        assertNoLegacyKeysInApi(versionResponse);
        JsonNode templateResponse = null;
        for (JsonNode candidate : templates) {
            if ("LEGACY_REPAIR_TEMPLATE".equals(candidate.path("code").asText())) {
                templateResponse = candidate;
                break;
            }
        }
        assertThat(templateResponse).isNotNull();
        assertNoLegacyKeysInApi(templateResponse);

        runRepairMigration();

        assertThat(jdbc.queryForObject(
                "SELECT snapshot::text FROM questionbank.question_version WHERE question_id = ?",
                String.class, setId)).isEqualTo(repairedSetSnapshot);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot::text FROM questionbank.question_version WHERE question_id = ?",
                String.class, childId)).isEqualTo(repairedChildSnapshot);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot::text FROM questionbank.question_version WHERE question_id = ?",
                String.class, matchingId)).isEqualTo(repairedMatchingSnapshot);
        assertThat(jdbc.queryForObject(
                "SELECT structure::text FROM exam.exam_template WHERE code = 'LEGACY_REPAIR_TEMPLATE'",
                String.class)).isEqualTo(repairedTemplate);
        assertThat(jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, examId))
                .isEqualTo(repairedStructure);
        assertThat(jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_version WHERE id = ?", String.class, versionId))
                .isEqualTo(repairedVersion);
        assertThat(jdbc.queryForList(
                "SELECT id::text AS id, revision FROM questionbank.question ORDER BY id::text"))
                .isEqualTo(questionRevisions);
        assertThat(jdbc.queryForList(
                "SELECT subject_item_id::text AS id, revision FROM questionbank.question_source_revision "
                        + "ORDER BY subject_item_id::text")).isEqualTo(sourceRevisions);
        assertThat(jdbc.queryForList("SELECT id::text AS id, revision FROM exam.exam ORDER BY id::text"))
                .isEqualTo(examRevisions);
    }


    @Test
    void seededCodeSourcePartsProduceSolverPaths() {
        for (var entry : Map.of("TEST-QDA-01", 5, "TEST-BCA-01", 4).entrySet()) {
            var row = jdbc.queryForMap("""
                    select e.kind_item_id, s.parts::text as document from exam.exam e
                    join exam.exam_structure s on s.exam_id = e.id where e.code = ?
                    """, entry.getKey());
            var document = new ExamStructureCodec(mapper).read((String) row.get("document"));
            var input = solverInputBuilder.build((UUID) row.get("kind_item_id"), document);
            var choice = (SolvePart.Choice) input.parts().get(2);
            assertThat(choice.options()).as(entry.getKey()).hasSize(entry.getValue());
        }
    }

    @Test
    void assumedExamsHaveTheirSpecifiedCheckResults() throws Exception {
        // D11: THPTQG Ngữ văn is not publishable. Idempotent once the seeded template itself carries the flag.
        jdbc.update("UPDATE exam.exam_template SET publishable = false WHERE code = 'THPTQG_LITERATURE_2025'");
        SoftAssertions softly = new SoftAssertions();
        List<String> valid = List.of("TEST-HSA-01", "TEST-HSA-02", "TEST-HSA-03", "TEST-HSA-04",
                "TEST-QDA-01", "TEST-VACT-01", "TEST-SPT-01", "TEST-SP2E-01", "TEST-SP2E-02",
                "TEST-SP2E-03", "TEST-THPTQG-01", "TEST-THPTQG-03", "TEST-ERR-08");
        for (String code : valid) {
            JsonNode result = check(code);
            softly.assertThat(result.path("status").asText()).as("%s: %s", code, codes(result)).isEqualTo("VALID");
            Set<String> expected = Set.of("TEST-SPT-01").contains(code)
                    ? Set.of("MANUAL_GRADING_REQUIRED") : Set.of();
            softly.assertThat(codes(result)).as(code).isEqualTo(expected);
        }

        JsonNode bca = check("TEST-BCA-01");
        softly.assertThat(bca.path("status").asText()).as("TEST-BCA-01").isEqualTo("VALID");
        softly.assertThat(codes(bca)).as("TEST-BCA-01").isEqualTo(Set.of("MANUAL_GRADING_REQUIRED"));

        Map<String, Set<String>> invalid = new LinkedHashMap<>();
        invalid.put("TEST-THPTQG-02", Set.of("TEMPLATE_NOT_PUBLISHABLE", "MANUAL_GRADING_REQUIRED"));
        invalid.put("TEST-ERR-01", Set.of("MATRIX_INCOMPLETE"));
        invalid.put("TEST-ERR-02", Set.of("PART_POINTS_MISMATCH", "POINTS_TOTAL_MISMATCH", "MANUAL_GRADING_REQUIRED"));
        invalid.put("TEST-ERR-03", Set.of("SET_SHORTAGE"));
        // 40 + 9 = 49 instead of 50; the solver also cannot satisfy the 50-unit matrix.
        invalid.put("TEST-ERR-04", Set.of("SECTION_TOTAL_MISMATCH", "PART_POINTS_MISMATCH",
                "PATH_TOTAL_MISMATCH", "POINTS_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        // The 35 + 14 quota totals 49 for a 50-question section.
        invalid.put("TEST-ERR-05", Set.of("ANSWER_QUOTA_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        invalid.put("TEST-ERR-06", Set.of("ANSWER_TYPE_SHORTAGE"));
        invalid.put("TEST-ERR-09", Set.of("ANSWER_QUOTA_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        // FU-BE4a-1: full-count totals are interim; the invalid 17 + 17 + 17 option also leaves the part empty.
        invalid.put("TEST-ERR-10", Set.of("SUBJECT_CHOICE_COUNTS_INVALID", "PART_EMPTY",
                "PART_POINTS_MISMATCH", "PART_TOTAL_MISMATCH", "PATH_TOTAL_MISMATCH",
                "POINTS_TOTAL_MISMATCH"));
        invalid.put("TEST-ERR-11", Set.of("CELL_SHORTAGE"));
        invalid.put("TEST-ERR-12", Set.of("POINTS_TOTAL_MISMATCH"));
        invalid.put("TEST-ERR-13", Set.of("YEARS_UNCONFIRMED"));
        for (var entry : invalid.entrySet()) {
            JsonNode result = check(entry.getKey());
            softly.assertThat(result.path("status").asText()).as(entry.getKey()).isEqualTo("INVALID");
            softly.assertThat(codes(result)).as(entry.getKey()).isEqualTo(entry.getValue());
        }
        JsonNode narrow = check("TEST-ERR-07");
        softly.assertThat(narrow.path("status").asText()).as("TEST-ERR-07").isEqualTo("INVALID");
        Set<String> shortageIssues = Set.of("CELL_SHORTAGE");
        softly.assertThat(codes(narrow)).as("TEST-ERR-07").isEqualTo(shortageIssues);
        int failures = 0;
        int selections = 0;
        for (JsonNode path : narrow.path("paths")) {
            for (JsonNode selection : path.path("selections")) {
                selections++;
                JsonNode codes = selection.path("subjectCodes");
                JsonNode counts = selection.path("counts");
                boolean geography17 = false;
                for (int i = 0; i < codes.size(); i++) {
                    geography17 |= "DIA".equals(codes.get(i).asText()) && counts.get(i).asInt() == 17;
                }
                if (geography17) failures++;
                softly.assertThat(selection.path("result").asText()).as(selection.toString())
                        .isEqualTo(geography17 ? "INVALID" : "VALID");
                softly.assertThat(selection.path("issues").findValuesAsText("code"))
                        .as(selection.toString()).containsExactlyInAnyOrderElementsOf(
                                geography17 ? shortageIssues : Set.of());
            }
        }
        softly.assertThat(selections).as("TEST-ERR-07 selections").isEqualTo(10);
        softly.assertThat(failures).as("TEST-ERR-07 selections containing DIA").isEqualTo(6);
        softly.assertAll();
    }

    @Test
    void seededShortagesReportCountsAndAffectedSelections() throws Exception {
        JsonNode cellResult = check("TEST-ERR-11");
        JsonNode cell = cellResult.path("groups").findValues("issues").stream()
                .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> "CELL_SHORTAGE".equals(issue.path("code").asText()))
                .findFirst().orElseThrow();
        assertThat(cell.path("gradeCode").asText()).isEqualTo("G12");
        assertThat(cell.path("difficultyCode").asText()).isEqualTo("VDC");
        assertThat(cell.path("required").asInt(-1)).isEqualTo(12);
        assertThat(cell.path("available").asInt(-1)).isEqualTo(4);

        JsonNode result = check("TEST-ERR-07");
        Set<String> shortageIssues = Set.of("CELL_SHORTAGE");
        assertThat(codes(result)).isEqualTo(shortageIssues);
        JsonNode shortage = result.path("groups").findValues("issues").stream()
                .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> "CELL_SHORTAGE".equals(issue.path("code").asText())
                        && "DIA".equals(issue.path("subjectCode").asText())
                        && issue.path("required").asInt(-1) == 11)
                .findFirst().orElseThrow();
        assertThat(shortage.path("available").asInt(-1)).isEqualTo(10);
        Set<Set<String>> failed = new java.util.HashSet<>();
        int failedCount = 0;
        for (JsonNode path : result.path("paths")) {
            for (JsonNode selection : path.path("selections")) {
                var issues = selection.path("issues").findValuesAsText("code");
                Set<String> subjects = new java.util.HashSet<>();
                for (int i = 0; i < selection.path("subjectCodes").size(); i++) {
                    subjects.add(selection.path("subjectCodes").get(i).asText());
                    assertThat(selection.path("counts").get(i).asInt()).isEqualTo(17);
                }
                assertThat(selection.path("result").asText()).isEqualTo(subjects.contains("DIA") ? "INVALID" : "VALID");
                assertThat(issues).containsExactlyInAnyOrderElementsOf(subjects.contains("DIA") ? shortageIssues : Set.of());
                if (subjects.contains("DIA")) {
                    failedCount++;
                    failed.add(subjects);
                    JsonNode selectionIssue = java.util.stream.StreamSupport.stream(selection.path("issues").spliterator(), false)
                            .filter(issue -> "CELL_SHORTAGE".equals(issue.path("code").asText()))
                            .findFirst().orElseThrow();
                    assertThat(selectionIssue.path("subjectCode").asText()).isEqualTo("DIA");
                    assertThat(selectionIssue.path("subjectCount").asInt()).isEqualTo(17);
                    assertThat(selectionIssue.path("required").asInt()).isEqualTo(11);
                    assertThat(selectionIssue.path("available").asInt()).isEqualTo(10);
                } else {
                    assertThat(issues).doesNotContain("CELL_SHORTAGE");
                }
            }
        }
        assertThat(failedCount).isEqualTo(6);
        assertThat(failed).containsExactlyInAnyOrder(
                Set.of("LY", "HOA", "DIA"), Set.of("LY", "SINH", "DIA"), Set.of("LY", "SU", "DIA"),
                Set.of("HOA", "SINH", "DIA"), Set.of("HOA", "SU", "DIA"), Set.of("SINH", "SU", "DIA"));
    }

    @Test
    void readyPoolsMeetAssumedMinimums() {
        record Pool(String subject, String type, String points, int minimum) {}
        List<Pool> pools = List.of(
                new Pool("TOAN", "SINGLE_CHOICE", "1", 100),
                new Pool("TOAN", "SHORT_ANSWER", "1", 30),
                new Pool("TOAN", "SINGLE_CHOICE", "0.25", 40),
                new Pool("TOAN", "TRUE_FALSE", "1", 8),
                new Pool("TOAN", "SHORT_ANSWER", "0.25", 20),
                new Pool("TOAN", "SHORT_ANSWER", "0.5", 12),
                new Pool("TOAN", "MULTIPLE_CHOICE", "0.25", 10),
                new Pool("TOAN", "ESSAY", "1", 6),
                new Pool("TOAN", "SINGLE_CHOICE", "10", 60),
                new Pool("VAN", "SINGLE_CHOICE", "1", 100),
                new Pool("VAN", "ESSAY", "25", 2),
                new Pool("VAN", "ESSAY", "2", 2),
                new Pool("VAN", "ESSAY", "4", 2),
                new Pool("ANH", "SINGLE_CHOICE", "1", 100),
                new Pool("ANH", "SINGLE_CHOICE", "0.75", 40),
                new Pool("ANH", "SINGLE_CHOICE", "10", 60),
                new Pool("ANH", "SINGLE_CHOICE", "0.2", 60),
                new Pool("ANH", "CLOZE", "2", 2),
                new Pool("VIETNAMESE", "SINGLE_CHOICE", "10", 60),
                new Pool("SCIENTIFIC_THINKING", "SINGLE_CHOICE", "10", 60),
                new Pool("INFORMATICS", "SINGLE_CHOICE", "0.25", 48),
                new Pool("INFORMATICS", "TRUE_FALSE", "1", 8));
        SoftAssertions softly = new SoftAssertions();
        for (Pool pool : pools) {
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM questionbank.question q
                    JOIN catalog.catalog_item subject ON subject.id=q.subject_item_id
                    WHERE q.code LIKE 'TESTQ-%' AND q.parent_id IS NULL AND q.status='READY'
                      AND q.deleted_at IS NULL AND subject.code=? AND q.type=? AND q.points=?
                    """, Integer.class, pool.subject(), pool.type(), new BigDecimal(pool.points()));
            softly.assertThat(count).as(pool.toString()).isGreaterThanOrEqualTo(pool.minimum());
        }
        for (String subject : List.of("LY", "HOA", "SINH", "SU", "DIA")) {
            for (var typeAndMin : Map.of("SINGLE_CHOICE", 50, "SHORT_ANSWER", 8).entrySet()) {
                Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM questionbank.question q
                        JOIN catalog.catalog_item subject ON subject.id=q.subject_item_id
                        WHERE q.code LIKE 'TESTQ-%' AND q.parent_id IS NULL AND q.status='READY'
                          AND q.deleted_at IS NULL AND subject.code=? AND q.type=? AND q.points=1
                        """, Integer.class, subject, typeAndMin.getKey());
                softly.assertThat(count).as("%s %s", subject, typeAndMin.getKey())
                        .isGreaterThanOrEqualTo(typeAndMin.getValue());
            }
        }
        record Sets(String subject, String childType, String points, int size, int minimum) {}
        List<Sets> sets = List.of(
                new Sets("TOAN", "SINGLE_CHOICE", "1", 3, 6),
                new Sets("TOAN", "SINGLE_CHOICE", "0.25", 5, 2),
                new Sets("VAN", "SINGLE_CHOICE", "1", 5, 10),
                new Sets("VAN", "ESSAY", "0.8", 5, 2),
                new Sets("ANH", "SINGLE_CHOICE", "1", 5, 6),
                new Sets("ANH", "SINGLE_CHOICE", "0.2", 5, 4),
                new Sets("LY", "SINGLE_CHOICE", "1", 3, 4),
                new Sets("HOA", "SINGLE_CHOICE", "1", 3, 4),
                new Sets("SINH", "SINGLE_CHOICE", "1", 3, 4),
                new Sets("SU", "SINGLE_CHOICE", "1", 3, 4),
                new Sets("DIA", "SINGLE_CHOICE", "1", 3, 4));
        for (Sets pool : sets) {
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM questionbank.question q
                    JOIN catalog.catalog_item subject ON subject.id=q.subject_item_id
                    WHERE q.code LIKE 'TESTQ-%' AND q.parent_id IS NULL AND q.status='READY'
                      AND q.deleted_at IS NULL AND subject.code=? AND q.type='QUESTION_SET'
                      AND (SELECT count(*) FROM questionbank.question c WHERE c.parent_id=q.id
                           AND c.deleted_at IS NULL)=?
                      AND (SELECT count(*) FROM questionbank.question c WHERE c.parent_id=q.id
                           AND c.deleted_at IS NULL AND c.type=? AND c.points=?)=?
                    """, Integer.class, pool.subject(), pool.size(), pool.childType(),
                    new BigDecimal(pool.points()), pool.size());
            softly.assertThat(count).as(pool.toString()).isGreaterThanOrEqualTo(pool.minimum());
        }
        Integer rareSets = jdbc.queryForObject("""
                SELECT count(*) FROM questionbank.question q
                JOIN questionbank.question_tag t ON t.question_id=q.id
                JOIN catalog.catalog_item tag ON tag.id=t.tag_item_id
                WHERE q.code LIKE 'TESTQ-%' AND q.parent_id IS NULL AND q.status='READY'
                  AND q.deleted_at IS NULL AND tag.code='TEST-HIEM'
                """, Integer.class);
        softly.assertThat(rareSets).as("TEST-HIEM ready sets").isEqualTo(2);
        for (String kind : List.of("SPT", "SP2E", "THPTQG")) {
            String scoring = jdbc.queryForObject("""
                    SELECT p.tf_scoring::text FROM exam.exam_kind_profile p
                    JOIN catalog.catalog_item i ON i.id=p.exam_item_id WHERE i.code=?
                    """, String.class, kind);
            try {
                JsonNode rules = mapper.readTree(scoring);
                softly.assertThat(rules.path("method").asText()).as(kind).isEqualTo("COUNT_TABLE");
                softly.assertThat(rules.path("table").toString()).as(kind).isEqualTo("[0.1,0.25,0.5,1]");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        softly.assertAll();
    }

    @Test
    void generatedQuestionsAreValidAndSearchable() throws Exception {
        var rows = jdbc.queryForList("""
                SELECT q.code, q.type, q.status, q.content_text, q.search_text, v.snapshot::text AS snapshot
                FROM questionbank.question q JOIN questionbank.question_version v
                  ON v.question_id=q.id AND v.version_no=coalesce(q.working_version_no,q.current_version_no)
                WHERE q.code LIKE 'TESTQ-%'
                """);
        assertThat(rows).isNotEmpty();
        for (var row : rows) {
            String code = (String) row.get("code");
            QuestionSnapshot snapshot = mapper.readValue((String) row.get("snapshot"), QuestionSnapshot.class);
            var body = snapshot.body();
            assertThat(row.get("content_text")).as(code)
                    .isEqualTo(QuestionSearchNormalizer.contentText(snapshot.content(), body));
            assertThat(row.get("search_text")).as(code)
                    .isEqualTo(QuestionSearchNormalizer.searchText(code, snapshot.content(), body));
            var input = new QuestionInput(QuestionType.valueOf((String) row.get("type")), body,
                    snapshot.points(), snapshot.content(), snapshot.solution(), snapshot.commonMistakes(), snapshot.attachmentIds());
            assertThat(QuestionBodyValidator.validateSave(input, QuestionAssetResolver.none())).as(code).isEmpty();
            if ("READY".equals(row.get("status"))) {
                var classification = new QuestionBodyValidator.ClassificationRequirement(
                        snapshot.subjectItemId() != null, snapshot.gradeItemId() != null, snapshot.levelItemId() != null);
                assertThat(QuestionBodyValidator.validateReady(input, classification, false, QuestionAssetResolver.none()).errors())
                        .as(code).isEmpty();
            }
        }
    }

    @Test
    void rerunningDevSeedPreservesExistingRowsAndSourceRevisions() {
        Integer questions = jdbc.queryForObject("SELECT count(*) FROM questionbank.question WHERE code LIKE 'TESTQ-%'", Integer.class);
        Integer exams = jdbc.queryForObject("SELECT count(*) FROM exam.exam WHERE code LIKE 'TEST-%'", Integer.class);
        List<Map<String, Object>> revisions = jdbc.queryForList(
                "SELECT subject_item_id, revision FROM questionbank.question_source_revision ORDER BY subject_item_id");
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/devdata/R__seed_test_exam_data.sql"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question WHERE code LIKE 'TESTQ-%'", Integer.class))
                .isEqualTo(questions);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam WHERE code LIKE 'TEST-%'", Integer.class))
                .isEqualTo(exams);
        assertThat(jdbc.queryForList("SELECT subject_item_id, revision FROM questionbank.question_source_revision ORDER BY subject_item_id"))
                .isEqualTo(revisions);
    }

    @Test
    void examKindProfilesSeedScopesPerSpec2B() {
        assertThat(examKindScopes()).containsExactlyInAnyOrderEntriesOf(EXPECTED_PROFILE_SCOPES);
    }

    @Test
    void rerunningExamDevSeedRepairsExistingProfileScopesWithoutChangingOtherFields() {
        jdbc.update("""
                UPDATE exam.exam_kind_profile SET scopes = CASE exam_item_id
                    WHEN '00000000-0000-7000-8200-000000000101' THEN '{FULL,PART}'
                    WHEN '00000000-0000-7000-8200-000000000102' THEN '{FULL,PART}'
                    WHEN '00000000-0000-7000-8400-000000000103' THEN '{FULL,PART}'
                    WHEN '00000000-0000-7000-8400-000000000104' THEN '{FULL}'
                    WHEN '00000000-0000-7000-8400-000000000105' THEN '{SUBJECT}'
                    WHEN '00000000-0000-7000-8400-000000000106' THEN '{SUBJECT}'
                    WHEN '00000000-0000-7000-8400-000000000107' THEN '{SUBJECT}'
                END::text[]
                WHERE exam_item_id IN (
                    '00000000-0000-7000-8200-000000000101',
                    '00000000-0000-7000-8200-000000000102',
                    '00000000-0000-7000-8400-000000000103',
                    '00000000-0000-7000-8400-000000000104',
                    '00000000-0000-7000-8400-000000000105',
                    '00000000-0000-7000-8400-000000000106',
                    '00000000-0000-7000-8400-000000000107')
                """);
        assertThat(examKindScopes()).containsExactlyInAnyOrderEntriesOf(OLD_PROFILE_SCOPES);
        List<Map<String, Object>> before = profileRowsWithoutScopes();

        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/devdata/R__seed_exam_dev_data.sql"));

        assertThat(examKindScopes()).containsExactlyInAnyOrderEntriesOf(EXPECTED_PROFILE_SCOPES);
        assertThat(profileRowsWithoutScopes()).containsExactlyElementsOf(before);
    }

    private Map<String, String> examKindScopes() {
        return jdbc.query(
                "SELECT i.code, array_to_string(p.scopes, ',') FROM exam.exam_kind_profile p"
                        + " JOIN catalog.catalog_item i ON i.id = p.exam_item_id",
                rs -> {
                    Map<String, String> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString(1), rs.getString(2));
                    }
                    return result;
                });
    }

    private List<Map<String, Object>> profileRowsWithoutScopes() {
        return jdbc.queryForList("SELECT * FROM exam.exam_kind_profile ORDER BY exam_item_id").stream()
                .map(row -> {
                    Map<String, Object> withoutScopes = new LinkedHashMap<>(row);
                    withoutScopes.remove("scopes");
                    return withoutScopes;
                })
                .toList();
    }

    @Test
    void examSeedsContainNoAssumedCombinationCodes() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item WHERE code_normalized ~ '^(qda_qd0|kh-)'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item i JOIN catalog.combination_version v ON v.item_id = i.id"
                        + " WHERE i.code_normalized ~ '^(qda_qd0|kh-)'",
                Integer.class)).isZero();
    }

    private void runRepairMigration() {
        ScriptUtils.executeSqlScript(
                DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/migration/exam/V202611171223__repair_legacy_shuffle_data.sql"));
    }

    private static void assertNoLegacyKeys(JsonNode json) {
        for (String key : List.of("shuffleOptions", "shuffleLeft", "shuffleRight",
                "groupOrder", "shuffle", "blockShuffleRule")) {
            assertThat(json.findValues(key)).as(key).isEmpty();
        }
    }

    private static void assertNoLegacyKeysInApi(JsonNode json) {
        for (String key : List.of("shuffleLeft", "shuffleRight",
                "groupOrder", "shuffle", "blockShuffleRule")) {
            assertThat(json.findValues(key)).as(key).isEmpty();
        }
        for (JsonNode value : json.findValues("shuffleOptions")) {
            assertThat(value.isBoolean() && value.asBoolean())
                    .as("shuffleOptions absent in storage reads as true").isTrue();
        }
    }

    private static void assertHsaMathTypes(JsonNode json) {
        assertThat(json.findValues("templateGroupCode").stream().map(JsonNode::asText).toList())
                .contains("HSA_MATH_CONTEXT");
        assertThat(json.findValues("childAnswerType").stream().map(JsonNode::asText).toList())
                .contains("SINGLE_CHOICE", "MULTIPLE_CHOICE");
    }

    private static Set<String> codes(JsonNode result) {
        return result.path("groups").findValuesAsText("code").stream().collect(Collectors.toSet());
    }

    private JsonNode check(String code) throws Exception {
        UUID id = jdbc.queryForObject("select id from exam.exam where code_normalized = ?", UUID.class, code.toLowerCase(java.util.Locale.ROOT));
        String response = mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }
}
