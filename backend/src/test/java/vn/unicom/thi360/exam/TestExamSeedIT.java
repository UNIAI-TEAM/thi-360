package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import vn.unicom.thi360.exam.application.ExamBlockRefProjector;
import vn.unicom.thi360.exam.application.ExamCatalogUsageProvider;
import vn.unicom.thi360.exam.application.SolverInputBuilder;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
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
    @Autowired ExamBlockRefProjector projector;
    @Autowired ExamCatalogUsageProvider usageProvider;
    private static final Map<String, String> OLD_PROFILE_SCOPES = Map.of(
            "HSA", "FULL,PART",
            "QDA", "FULL,PART",
            "BCA", "FULL,PART",
            "VACT", "FULL",
            "SPT", "SUBJECT",
            "SP2E", "SUBJECT",
            "THPTQG", "SUBJECT",
            "HSCA", "SUBJECT,PART",
            "VSAT", "SUBJECT,PART");

    private static final Map<String, String> EXPECTED_PROFILE_SCOPES = Map.of(
            "HSA", "FULL,PART,SUBJECT",
            "QDA", "FULL,PART,SUBJECT",
            "BCA", "FULL,PART,SUBJECT",
            "VACT", "FULL,PART,SUBJECT",
            "SPT", "SUBJECT,PART",
            "SP2E", "SUBJECT,PART",
            "THPTQG", "SUBJECT,PART",
            "HSCA", "SUBJECT,PART",
            "VSAT", "SUBJECT,PART");

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
            assertThat(parts.findValues("taskTypeCode")).isEmpty();
            assertThat(parts.findValues("includeDescendants")).isEmpty();
            assertThat(parts.findValues("match")).isEmpty();
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
        for (var entry : Map.of("TEST-QDA-01", 5, "TEST-BCA-01", 4, "TEST-ERR-02", 4).entrySet()) {
            var row = jdbc.queryForMap("""
                    select e.kind_item_id, s.parts::text as document from exam.exam e
                    join exam.exam_structure s on s.exam_id = e.id where e.code = ?
                    """, entry.getKey());
            var document = new ExamStructureCodec(mapper).read((String) row.get("document"));
            var codePart = document.parts().get(2);
            assertThat(codePart.codeSource().source()).as(entry.getKey()).isEqualTo("GLOBAL_LIST");
            assertThat(codePart.options()).as(entry.getKey()).isEmpty();
            var input = solverInputBuilder.build((UUID) row.get("kind_item_id"), document);
            var choice = (SolvePart.Choice) input.parts().get(2);
            assertThat(choice.options()).as(entry.getKey()).hasSize(entry.getValue());
            // The options must be the live Global List combinations, with their subjects in list order.
            Map<String, String> live = new LinkedHashMap<>();
            jdbc.query("""
                    select ci.code as combination, string_agg(sub.code, ',' order by cs.position) as subjects
                    from catalog.combination_version cv
                    join catalog.catalog_item ci on ci.id = cv.item_id
                    join catalog.combination_subject cs on cs.combination_version_id = cv.id
                    join catalog.catalog_item sub on sub.id = cs.subject_item_id
                    where cv.is_current and cv.exam_item_id = ?
                    group by ci.code, ci.value order by ci.value
                    """, rs -> {
                live.put(codePart.id() + ":" + rs.getString("combination"), rs.getString("subjects"));
            }, row.get("kind_item_id"));
            Map<String, String> solved = new LinkedHashMap<>();
            for (var option : choice.options()) {
                var sections = (SolveOption.Sections) option;
                solved.put(sections.optionId(), sections.sections().stream()
                        .map(section -> section.subjectCode()).collect(Collectors.joining(",")));
            }
            assertThat(solved).as(entry.getKey()).isEqualTo(live);
        }
    }

    @Test
    void vactFullChecksLogicAndReasoningTogether() throws Exception {
        JsonNode result = check("TEST-VACT-01");
        assertThat(result.path("status").asText()).as("V-ACT issues: %s", codes(result)).isEqualTo("VALID");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM exam.exam_check_unit u
                JOIN exam.exam e ON e.id = u.exam_id
                JOIN exam.exam_structure s ON s.exam_id = e.id
                WHERE e.code = 'TEST-VACT-01'
                  AND u.section_ids @> ARRAY[
                    s.parts #>> '{parts,2,sections,0,id}',
                    s.parts #>> '{parts,2,sections,1,id}']::text[]
                  AND cardinality(u.section_ids) = 2
                """, Integer.class)).isEqualTo(1);
    }

    /** Spec 4.1: the documented warnings of the valid exams checked below (V-ACT: optional, see below). */
    private static final Map<String, Set<String>> EXPECTED_VALID_CODES = Map.of(
            "TEST-HSA-01", Set.of("EXAM_QUOTA_DEVIATION"),
            "TEST-SPT-01", Set.of("OMITTED_GROUPS_PRESENT"),
            "TEST-SP2E-02", Set.of("EXAM_QUOTA_DEVIATION"),
            "TEST-SP2E-03", Set.of("EXAM_QUOTA_DEVIATION"));

    private static final Set<String> REFERENCE_WARNINGS = Set.of("EXAM_QUOTA_DEVIATION", "DISTRIBUTION_DEVIATION");

    /** The result's complete code set; the reference deviations among them must all be WARNING severity. */
    private static Set<String> exactCodes(SoftAssertions softly, String label, JsonNode result) {
        softly.assertThat(result.path("groups").findValues("issues").stream()
                        .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                        .filter(issue -> REFERENCE_WARNINGS.contains(issue.path("code").asText()))
                        .map(issue -> issue.path("code").asText() + "/" + issue.path("severity").asText())
                        .collect(Collectors.toSet()))
                .as("%s reference deviations are warnings", label)
                .isSubsetOf("EXAM_QUOTA_DEVIATION/WARNING", "DISTRIBUTION_DEVIATION/WARNING");
        return codes(result);
    }

    @Test
    void assumedExamsHaveTheirSpecifiedCheckResults() throws Exception {
        // D11: THPTQG Ngữ văn is not publishable; the seeded template itself carries the flag.
        SoftAssertions softly = new SoftAssertions();
        List<String> valid = List.of("TEST-HSA-01", "TEST-HSA-02", "TEST-HSA-03", "TEST-HSA-04",
                "TEST-QDA-01", "TEST-VACT-01", "TEST-SPT-01", "TEST-SP2E-01", "TEST-SP2E-02",
                "TEST-SP2E-03", "TEST-THPTQG-01", "TEST-THPTQG-03", "TEST-ERR-08");
        for (String code : valid) {
            JsonNode result = check(code);
            softly.assertThat(result.path("status").asText()).as("%s: %s", code, codes(result)).isEqualTo("VALID");
            Set<String> expected = EXPECTED_VALID_CODES.getOrDefault(code, Set.of());
            if ("TEST-VACT-01".equals(code)) {
                // VACT_FULL distribution tables are all REFERENCE (spec 2B 5.1): the ANH section's
                // block matrices and the section matrices are advisory, so the solver's witness may or may
                // not hit them. Every emitted issue must be a WARNING DISTRIBUTION_DEVIATION; none is allowed.
                softly.assertThat(codes(result)).as(code).isSubsetOf("DISTRIBUTION_DEVIATION");
                softly.assertThat(result.path("groups").findValues("issues").stream()
                        .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                        .map(issue -> issue.path("code").asText() + "/" + issue.path("severity").asText())
                        .collect(Collectors.toSet()))
                        .as(code).isSubsetOf("DISTRIBUTION_DEVIATION/WARNING");
            } else {
                softly.assertThat(exactCodes(softly, code, result)).as(code).isEqualTo(expected);
            }
        }

        JsonNode bca = check("TEST-BCA-01");
        softly.assertThat(bca.path("status").asText()).as("TEST-BCA-01").isEqualTo("VALID");
        softly.assertThat(exactCodes(softly, "TEST-BCA-01", bca)).as("TEST-BCA-01")
                .isEqualTo(Set.of("MANUAL_GRADING_REQUIRED", "DISTRIBUTION_DEVIATION", "EXAM_QUOTA_DEVIATION"));

        Map<String, Set<String>> invalid = new LinkedHashMap<>();
        invalid.put("TEST-THPTQG-02", Set.of("TEMPLATE_NOT_PUBLISHABLE", "MANUAL_GRADING_REQUIRED",
                "DISTRIBUTION_DEVIATION"));
        invalid.put("TEST-ERR-01", Set.of("MATRIX_INCOMPLETE"));
        invalid.put("TEST-ERR-02", Set.of("PART_POINTS_MISMATCH", "POINTS_TOTAL_MISMATCH", "MANUAL_GRADING_REQUIRED",
                "DISTRIBUTION_DEVIATION", "EXAM_QUOTA_DEVIATION"));
        // The Math context set carries the rare label; with no slots it is a set shortage.
        invalid.put("TEST-ERR-03", Set.of("SET_SHORTAGE"));
        // 40 + 9 = 49 instead of 50; the solver also cannot satisfy the 50-unit matrix.
        invalid.put("TEST-ERR-04", Set.of("SECTION_TOTAL_MISMATCH", "PART_POINTS_MISMATCH",
                "PATH_TOTAL_MISMATCH", "POINTS_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        // The 32 + 14 + 3 quota totals 49 for a 50-question section.
        invalid.put("TEST-ERR-05", Set.of("ANSWER_QUOTA_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        invalid.put("TEST-ERR-06", Set.of("ANSWER_TYPE_SHORTAGE"));
        invalid.put("TEST-ERR-09", Set.of("ANSWER_QUOTA_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        // FU-BE4a-1: full-count totals are interim; the invalid 17 + 17 + 17 option also leaves the part empty.
        invalid.put("TEST-ERR-10", Set.of("SUBJECT_CHOICE_COUNTS_INVALID", "PART_EMPTY",
                "PART_POINTS_MISMATCH", "PART_TOTAL_MISMATCH", "PATH_TOTAL_MISMATCH",
                "POINTS_TOTAL_MISMATCH"));
        invalid.put("TEST-ERR-11", Set.of("CELL_SHORTAGE"));
        invalid.put("TEST-ERR-12", Set.of("POINTS_TOTAL_MISMATCH"));
        invalid.put("TEST-ERR-13", Set.of("YEARS_UNCONFIRMED", "EXAM_QUOTA_DEVIATION"));
        for (var entry : invalid.entrySet()) {
            JsonNode result = check(entry.getKey());
            softly.assertThat(result.path("status").asText()).as(entry.getKey()).isEqualTo("INVALID");
            softly.assertThat(exactCodes(softly, entry.getKey(), result)).as(entry.getKey())
                    .isEqualTo(entry.getValue());
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
        // The Geography shortage is on the single-choice group of the Geography section, without
        // required/available figures (a group-level shortage).
        String geographyGroup = jdbc.queryForObject("""
                SELECT block->>'id' FROM exam.exam e JOIN exam.exam_structure s ON s.exam_id = e.id
                CROSS JOIN LATERAL jsonb_array_elements(s.parts->'parts') part
                CROSS JOIN LATERAL jsonb_array_elements(part->'options') opt
                CROSS JOIN LATERAL jsonb_array_elements(opt->'sections') sec
                CROSS JOIN LATERAL jsonb_array_elements(sec->'blocks') block
                WHERE e.code = 'TEST-ERR-07' AND sec->>'subjectCode' = 'DIA'
                  AND block->>'questionType' = 'SINGLE_CHOICE'
                """, String.class);
        JsonNode shortage = result.path("groups").findValues("issues").stream()
                .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> "CELL_SHORTAGE".equals(issue.path("code").asText())
                        && "DIA".equals(issue.path("subjectCode").asText()))
                .findFirst().orElseThrow();
        assertThat(shortage.path("blockId").asText("")).isEqualTo(geographyGroup);
        assertThat(shortage.path("required").isMissingNode() || shortage.path("required").isNull()).isTrue();
        assertThat(shortage.path("available").isMissingNode() || shortage.path("available").isNull()).isTrue();
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
                    assertThat(selectionIssue.path("blockId").asText("")).isNotEmpty();
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

    // ---------------------------------------------------------------------------------------------
    // UNI-2701: bài nền TEST-* khớp mẫu BA hiện hành (spec đề test mục 4.4, cột "Mẫu hiện hành");
    // bài lỗi TEST-ERR-* là bài nền cộng đúng một biến đổi (spec đề test mục 4.2).
    // ---------------------------------------------------------------------------------------------

    private static final List<String> BASE_EXAMS = List.of("TEST-HSA-01", "TEST-HSA-02", "TEST-HSA-03",
            "TEST-HSA-04", "TEST-QDA-01", "TEST-BCA-01", "TEST-VACT-01", "TEST-SPT-01", "TEST-SP2E-01",
            "TEST-SP2E-02", "TEST-SP2E-03", "TEST-THPTQG-01", "TEST-THPTQG-02", "TEST-THPTQG-03");

    /** Số câu học viên làm (spec 4.1) = tổng questionCountRequired của các phần. */
    private static final Map<String, Integer> BASE_QUESTION_COUNT = Map.ofEntries(
            Map.entry("TEST-HSA-01", 150), Map.entry("TEST-HSA-02", 50), Map.entry("TEST-HSA-03", 50),
            Map.entry("TEST-HSA-04", 50), Map.entry("TEST-QDA-01", 150), Map.entry("TEST-BCA-01", 81),
            Map.entry("TEST-VACT-01", 120), Map.entry("TEST-SPT-01", 22), Map.entry("TEST-SP2E-01", 22),
            Map.entry("TEST-SP2E-02", 40), Map.entry("TEST-SP2E-03", 50), Map.entry("TEST-THPTQG-01", 22),
            Map.entry("TEST-THPTQG-02", 7), Map.entry("TEST-THPTQG-03", 28));

    /** Điểm tối đa của bài (cột max_score; V-ACT để trống, chỉ có reference_max 1.200). */
    private static final Map<String, String> BASE_MAX_SCORE = Map.ofEntries(
            Map.entry("TEST-HSA-01", "150"), Map.entry("TEST-HSA-02", "50"), Map.entry("TEST-HSA-03", "50"),
            Map.entry("TEST-HSA-04", "50"), Map.entry("TEST-QDA-01", "150"), Map.entry("TEST-BCA-01", "100"),
            Map.entry("TEST-SPT-01", "7"), Map.entry("TEST-SP2E-01", "10"), Map.entry("TEST-SP2E-02", "10"),
            Map.entry("TEST-SP2E-03", "10"), Map.entry("TEST-THPTQG-01", "10"), Map.entry("TEST-THPTQG-02", "10"),
            Map.entry("TEST-THPTQG-03", "10"));

    private static final Map<String, String> ERROR_BASES = Map.ofEntries(
            Map.entry("TEST-ERR-01", "TEST-THPTQG-01"), Map.entry("TEST-ERR-02", "TEST-BCA-01"),
            Map.entry("TEST-ERR-03", "TEST-HSA-02"), Map.entry("TEST-ERR-04", "TEST-HSA-02"),
            Map.entry("TEST-ERR-05", "TEST-HSA-02"), Map.entry("TEST-ERR-06", "TEST-HSA-02"),
            Map.entry("TEST-ERR-07", "TEST-HSA-04"), Map.entry("TEST-ERR-08", "TEST-HSA-04"),
            Map.entry("TEST-ERR-09", "TEST-HSA-04"), Map.entry("TEST-ERR-10", "TEST-HSA-04"),
            Map.entry("TEST-ERR-11", "TEST-THPTQG-01"), Map.entry("TEST-ERR-12", "TEST-THPTQG-01"),
            Map.entry("TEST-ERR-13", "TEST-SP2E-02"));

    /** Cột của bài lỗi phải bằng bài nền (trừ cột đổi chủ ý ở TEST-ERR-13). */
    private static final String EXAM_SHAPE_COLUMNS = """
            e.kind_item_id, e.scope, e.part_code, e.subject_item_id, e.template_code, e.structure_year,
            e.matrix_year, e.years_confirmed, e.timing, e.duration_minutes, e.max_score, e.reference_max,
            e.practice_scoring, e.question_count, e.has_essay, e.has_english_option, e.combination_count""";

    // Chữ ký các mẫu theo spec đề test mục 2.1-2.7 và 4.4; xem signature() cho cách đọc từng dòng.
    private static final String SIG_HSA_MATH = """
            P ALL q=50 on=50 req=50 pts=50
              S TOAN 50 matrix[SEPARATE G:G10=3,G11=20,G12=27 D:NB=2,TH=21,VD=20,VDC=7 C:] quotas SINGLE_CHOICE=32,SHORT_ANSWER=15,QUESTION_SET=3
                B HSA_MATH_SINGLE SINGLE_CHOICE 32 pts 1
                B HSA_MATH_SINGLE SHORT_ANSWER 15 pts 1
                B HSA_MATH_CONTEXT QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
            """;
    private static final String SIG_HSA_LITERATURE = """
            P ALL q=50 on=50 req=50 pts=50
              S VAN 50
                B HSA_VN_ODD_WORD SINGLE_CHOICE 5 pts 1 tags VAN_TU_KHAC_NHOM
                B HSA_VN_WORD_USAGE SINGLE_CHOICE 5 pts 1 tags VAN_TU_SAI
                B HSA_VN_WORD_PAIR SINGLE_CHOICE 5 pts 1 tags VAN_CAP_TU_DIEN
                B HSA_VN_PASSAGE_SINGLE SINGLE_CHOICE 10 pts 1 tags VAN_NGU_LIEU_MOT_CAU
                B HSA_VN_PASSAGE QUESTION_SET 25 set 5x5 pts 1 child SINGLE_CHOICE slots VAN_NGHI_LUAN_XA_HOI/VAN_THONG_TIN/VAN_KHOA_HOC_CHINH_LUAN/VAN_THO/VAN_VAN_XUOI
            """;
    private static final String SIG_HSA_SCIENCE = """
            P CHOICE/1 q=50 on=50 req=50 pts=50
              O SUBJECT_CHOICE SCIENCE choose=3 counts=17,17,16
                S LY 17 matrix[SEPARATE G:G10=5,G11=6,G12=6 D:NB=3,TH=1,VD=13 C:]
                  B HSA_LY_FLEX SINGLE_CHOICE 13 pts 1
                  B HSA_LY_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
                  B HSA_LY_FLEX SHORT_ANSWER 1 pts 1
                S HOA 17 matrix[SEPARATE G:G10=2,G11=8,G12=7 D:NB=4,TH=5,VD=8 C:]
                  B HSA_HOA_FLEX SINGLE_CHOICE 13 pts 1
                  B HSA_HOA_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
                  B HSA_HOA_FLEX SHORT_ANSWER 1 pts 1
                S SINH 17 matrix[SEPARATE G:G10=3,G11=5,G12=9 D:NB=7,TH=7,VD=3 C:]
                  B HSA_SINH_FLEX SINGLE_CHOICE 13 pts 1
                  B HSA_SINH_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
                  B HSA_SINH_FLEX SHORT_ANSWER 1 pts 1
                S SU 17 matrix[SEPARATE G:G10=2,G11=5,G12=10 D:NB=7,TH=8,VD=2 C:]
                  B HSA_SU_FLEX SINGLE_CHOICE 14 pts 1
                  B HSA_SU_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
                S DIA 17 matrix[SEPARATE G:G10=2,G11=4,G12=11 D:NB=5,TH=9,VD=3 C:]
                  B HSA_DIA_FLEX SINGLE_CHOICE 14 pts 1
                  B HSA_DIA_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
            """;
    private static final String SIG_HSA_ENGLISH_OPTION = """
              O SUBJECT ANH choose=- counts=-
                S ANH 50 matrix[SEPARATE G: D: C:]
                  B HSA_EN_SENTENCE SINGLE_CHOICE 10 pts 1 tags ANH_HOAN_THANH_CAU
                  B HSA_EN_SYNONYM SINGLE_CHOICE 2 pts 1 tags ANH_DONG_NGHIA
                  B HSA_EN_ANTONYM SINGLE_CHOICE 2 pts 1 tags ANH_TRAI_NGHIA
                  B HSA_EN_REWRITE SINGLE_CHOICE 4 pts 1 tags ANH_VIET_LAI_CAU
                  B HSA_EN_COMBINE SINGLE_CHOICE 4 pts 1 tags ANH_KET_HOP_CAU
                  B HSA_EN_DIALOGUE SINGLE_CHOICE 4 pts 1 tags ANH_HOI_THOAI
                  B HSA_EN_DIALOGUE_ORDER SINGLE_CHOICE 4 pts 1 tags ANH_SAP_XEP_HOI_THOAI
                  B HSA_EN_CLOZE QUESTION_SET 5 set 1x5 pts 1 child SINGLE_CHOICE tags ANH_DOC_DIEN
                  B HSA_EN_READING QUESTION_SET 5 set 1x5 pts 1 child SINGLE_CHOICE tags ANH_DOC_HIEU_1
                  B HSA_EN_READING_2 QUESTION_SET 5 set 1x5 pts 1 child SINGLE_CHOICE tags ANH_DOC_HIEU_2
                  B HSA_EN_LOGIC SINGLE_CHOICE 5 pts 1 tags ANH_TU_DUY_LOGIC
            """;
    private static final String SIG_HSA_FULL_QUOTAS = "Q GRADE/QUESTION/REFERENCE\n";
    private static final String SIG_QDA_FULL = """
            P ALL q=50 on=50 req=50 pts=50
              S TOAN 50 matrix[SEPARATE G:G10=5,G11=15,G12=30 D: C:] quotas SINGLE_CHOICE=32,SHORT_ANSWER=15,QUESTION_SET=3
                B QDA_TOAN_SINGLE SINGLE_CHOICE 32 pts 1
                B QDA_TOAN_CONTEXT QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
                B QDA_TOAN_SINGLE SHORT_ANSWER 15 pts 1
            P ALL q=50 on=50 req=50 pts=50
              S VAN 50
                B QDA_VAN_ODD_WORD SINGLE_CHOICE 5 pts 1 tags VAN_TU_KHAC_NHOM
                B QDA_VAN_WORD_PAIR SINGLE_CHOICE 5 pts 1 tags VAN_CAP_TU_DIEN
                B QDA_VAN_WORD_USAGE SINGLE_CHOICE 5 pts 1 tags VAN_TU_SAI
                B QDA_VAN_PASSAGE_SINGLE SINGLE_CHOICE 10 pts 1 tags VAN_NGU_LIEU_MOT_CAU
                B QDA_VAN_PASSAGE QUESTION_SET 25 set 5x5 pts 1 child SINGLE_CHOICE slots VAN_THONG_TIN_NGHI_LUAN/VAN_NGHI_LUAN/VAN_THO/VAN_THIEU_NHI/VAN_TRUYEN
            P ALL q=50 on=50 req=50 pts=50 codeSource=GLOBAL_LIST
              pool S LY 25 matrix[SEPARATE G:G10=6,G11=5,G12=14 D: C:]
              pool   B QDA_LY_FLEX SINGLE_CHOICE 21 pts 1
              pool   B QDA_LY_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool   B QDA_LY_FLEX SHORT_ANSWER 1 pts 1
              pool S HOA 25 matrix[SEPARATE G:G10=6,G11=5,G12=14 D:NB=4,TH=20,VD=1 C:]
              pool   B QDA_HOA_FLEX SINGLE_CHOICE 21 pts 1
              pool   B QDA_HOA_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool   B QDA_HOA_FLEX SHORT_ANSWER 1 pts 1
              pool S SINH 25 matrix[SEPARATE G:G10=3,G11=6,G12=16 D: C:]
              pool   B QDA_SINH_FLEX SINGLE_CHOICE 21 pts 1
              pool   B QDA_SINH_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool   B QDA_SINH_FLEX SHORT_ANSWER 1 pts 1
              pool S SU 25 matrix[SEPARATE G:G10=2,G11=6,G12=17 D:NB=8,TH=10,VD=7 C:]
              pool   B QDA_SU_FLEX SINGLE_CHOICE 22 pts 1
              pool   B QDA_SU_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool S DIA 25 matrix[SEPARATE G:G10=4,G11=4,G12=17 D: C:]
              pool   B QDA_DIA_FLEX SINGLE_CHOICE 22 pts 1
              pool   B QDA_DIA_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool S ANH 50
              pool   B QDA_ANH_SENTENCE SINGLE_CHOICE 10 pts 1 tags ANH_HOAN_THANH_CAU
              pool   B QDA_ANH_SYN_ANT SINGLE_CHOICE 4 pts 1 tags ANH_DONG_NGHIA,ANH_TRAI_NGHIA
              pool   B QDA_ANH_DIALOGUE SINGLE_CHOICE 4 pts 1 tags ANH_HOI_THOAI
              pool   B QDA_ANH_NOTICE SINGLE_CHOICE 4 pts 1 tags ANH_THONG_BAO
              pool   B QDA_ANH_REWRITE SINGLE_CHOICE 4 pts 1 tags ANH_VIET_LAI_CAU
              pool   B QDA_ANH_COMBINE SINGLE_CHOICE 4 pts 1 tags ANH_KET_HOP_CAU
              pool   B QDA_ANH_CLOZE QUESTION_SET 5 set 1x5 pts 1 child SINGLE_CHOICE tags ANH_DOC_DIEN
              pool   B QDA_ANH_READING QUESTION_SET 5 set 1x5 pts 1 child SINGLE_CHOICE tags ANH_DOC_HIEU_1
              pool   B QDA_ANH_READING_2 QUESTION_SET 5 set 1x5 pts 1 child SINGLE_CHOICE tags ANH_DOC_HIEU_2
              pool   B QDA_ANH_INFERENCE SINGLE_CHOICE 5 pts 1 tags ANH_SUY_LUAN
            """;
    private static final String SIG_BCA_FULL = """
            P ALL q=1 on=1 req=1 pts=25
              S VAN 1
                B BCA_VAN_ESSAY ESSAY 1 pts 25 tags VAN_NGHI_LUAN_XA_HOI
            P ALL q=65 on=65 req=65 pts=60
              S TOAN 35 matrix[SEPARATE G:G10=4,G11=7,G12=24 D:NB=2,TH=12,VD=15,VDC=6 C:]
                B BCA_TOAN_FLEX SINGLE_CHOICE 27 pts 1
                B BCA_TOAN_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
                B BCA_TOAN_SHORT SHORT_ANSWER 5 pts 1
              S SU 10 matrix[SEPARATE G:G10=1,G11=2,G12=7 D:NB=1,TH=6,VD=3,VDC=0 C:]
                B BCA_SU_FLEX SINGLE_CHOICE 7 pts 1
                B BCA_SU_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE tags SU_TU_LIEU_LICH_SU
              S ANH 20 matrix[SEPARATE G: D:NB=3,TH=9,VD=8,VDC=0 C:]
                B BCA_ANH_PRON SINGLE_CHOICE 1 pts 0.75 tags ANH_PHAT_AM
                B BCA_ANH_STRESS SINGLE_CHOICE 1 pts 0.75 tags ANH_TRONG_AM
                B BCA_ANH_GRAMMAR SINGLE_CHOICE 8 pts 0.75 tags ANH_TU_VUNG_NGU_PHAP
                B BCA_ANH_CLOZE QUESTION_SET 5 set 1x5 pts 0.75 child SINGLE_CHOICE tags ANH_DOC_DIEN
                B BCA_ANH_READING QUESTION_SET 5 set 1x5 pts 0.75 child SINGLE_CHOICE tags ANH_DOC_HIEU
            P ALL q=15 on=15 req=15 pts=15 codeSource=GLOBAL_LIST
              pool S LY 15 matrix[SEPARATE G:G10=2,G11=3,G12=10 D:NB=3,TH=4,VD=7,VDC=1 C:]
              pool   B BCA_LY_FLEX SINGLE_CHOICE 12 pts 1
              pool   B BCA_LY_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool S HOA 15 matrix[SEPARATE G:G10=3,G11=3,G12=9 D:NB=1,TH=7,VD=6,VDC=1 C:]
              pool   B BCA_HOA_FLEX SINGLE_CHOICE 12 pts 1
              pool   B BCA_HOA_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool S SINH 15 matrix[SEPARATE G:G10=2,G11=3,G12=10 D:NB=4,TH=5,VD=4,VDC=2 C:]
              pool   B BCA_SINH_FLEX SINGLE_CHOICE 12 pts 1
              pool   B BCA_SINH_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
              pool S DIA 15 matrix[SEPARATE G:G10=2,G11=1,G12=12 D:NB=2,TH=10,VD=3,VDC=0 C:]
              pool   B BCA_DIA_FLEX SINGLE_CHOICE 12 pts 1
              pool   B BCA_DIA_CLUSTER QUESTION_SET 3 set 1x3 pts 1 child SINGLE_CHOICE
            Q GRADE/QUESTION/REFERENCE;DIFFICULTY/QUESTION/REFERENCE
            """;
    private static final String SIG_SPT_MATH = """
            P ALL q=16 on=16 req=16 pts=4
              S TOAN 16
                B SPT_TOAN_SINGLE SINGLE_CHOICE 16 pts 0.25
            P ALL q=2 on=2 req=2 pts=2
              S TOAN 2
                B SPT_TOAN_TF TRUE_FALSE 2 pts 1
            P ALL q=4 on=4 req=4 pts=1
              S TOAN 4
                B SPT_TOAN_SHORT SHORT_ANSWER 4 pts 0.25
                B SPT_TOAN_ESSAY ESSAY 3 pts - omitted
            """;
    private static final String SIG_SP2E_MATH_2025 = """
            P ALL q=12 on=12 req=12 pts=3
              S TOAN 12
                B SP2E_TOAN_SINGLE SINGLE_CHOICE 12 pts 0.25
            P ALL q=4 on=4 req=4 pts=4
              S TOAN 4
                B SP2E_TOAN_TF TRUE_FALSE 4 pts 1
            P ALL q=6 on=6 req=6 pts=3
              S TOAN 6
                B SP2E_TOAN_SHORT SHORT_ANSWER 6 pts 0.5
            """;
    private static final String SIG_SP2E_MATH_2026 = """
            P ALL q=25 on=25 req=25 pts=6.25
              S TOAN 25
                B SP2E_TOAN_SINGLE SINGLE_CHOICE 20 pts 0.25
                B SP2E_TOAN_MULTI MULTIPLE_CHOICE 5 pts 0.25
            P ALL q=5 on=5 req=5 pts=1.25
              S TOAN 5
                B SP2E_TOAN_DATA_SET SINGLE_CHOICE 5 pts 0.25 tags TOAN_DU_LIEU_TONG_HOP
            P ALL q=10 on=10 req=10 pts=2.5
              S TOAN 10
                B SP2E_TOAN_SHORT SHORT_ANSWER 10 pts 0.25
            Q DIFFICULTY/QUESTION/REFERENCE
            """;
    private static final String SIG_SP2E_ANH_2026 = """
            P ALL q=50 on=50 req=50 pts=10
              S ANH 50 matrix[SEPARATE G: D: C:]
                B SP2E_ANH_GAP_SENT QUESTION_SET 10 set 1x10 pts 0.2 child SINGLE_CHOICE tags ANH_DOC_DIEN_CAU
                B SP2E_ANH_SYN SINGLE_CHOICE 4 pts 0.2 tags ANH_DONG_NGHIA
                B SP2E_ANH_COMB SINGLE_CHOICE 4 pts 0.2 tags ANH_KET_HOP_CAU
                B SP2E_ANH_CLOZE_WORD QUESTION_SET 5 set 1x5 pts 0.2 child SINGLE_CHOICE tags ANH_DIEN_TU
                B SP2E_ANH_CLOZE_PHRASE QUESTION_SET 5 set 1x5 pts 0.2 child SINGLE_CHOICE tags ANH_DIEN_CUM_CAU
                B SP2E_ANH_DIALOGUE SINGLE_CHOICE 4 pts 0.2 tags ANH_GIAO_TIEP
                B SP2E_ANH_ORDER SINGLE_CHOICE 5 pts 0.2 tags ANH_SAP_XEP_DOAN
                B SP2E_ANH_READ_1 QUESTION_SET 5 set 1x5 pts 0.2 child SINGLE_CHOICE tags ANH_DOC_HIEU_1
                B SP2E_ANH_READ_2 QUESTION_SET 8 set 1x8 pts 0.2 child SINGLE_CHOICE tags ANH_DOC_HIEU_2
            Q DIFFICULTY/QUESTION/REFERENCE
            """;
    private static final String SIG_THPTQG_LITERATURE = """
            P ALL q=7 on=7 req=7 pts=10
              S VAN 7
                B THPTQG_VAN_READING QUESTION_SET 5 set 1x5 pts 0.8 child ESSAY matrix[SEPARATE G: D:NB=2,TH=2,VD=1 C:]
                B THPTQG_VAN_PARA ESSAY 1 pts 2
                B THPTQG_VAN_ESSAY ESSAY 1 pts 4
            """;
    private static final String SIG_THPTQG_INFORMATICS = """
            P ALL q=24 on=24 req=24 pts=6
              S INFORMATICS 24
                B THPTQG_INFORMATICS_SINGLE SINGLE_CHOICE 24 pts 0.25
            P CHOICE/1 q=4 on=6 req=4 pts=4
              S INFORMATICS 2
                B THPTQG_INFORMATICS_TF_COMMON TRUE_FALSE 2 pts 1
              O BRANCH CS choose=- counts=-
                S INFORMATICS 2
                  B THPTQG_INFORMATICS_TF_CS TRUE_FALSE 2 pts 1
              O BRANCH ICT choose=- counts=-
                S INFORMATICS 2
                  B THPTQG_INFORMATICS_TF_ICT TRUE_FALSE 2 pts 1
            """;

    /** Bài nền có chữ ký ghi trong spec; THPTQG-01 và V-ACT-01 chỉ so với mẫu (đã giống mẫu từ trước). */
    private static final Map<String, String> DOCUMENTED_SIGNATURES = Map.ofEntries(
            Map.entry("TEST-HSA-01", SIG_HSA_MATH + SIG_HSA_LITERATURE + SIG_HSA_SCIENCE + SIG_HSA_ENGLISH_OPTION
                    + SIG_HSA_FULL_QUOTAS),
            Map.entry("TEST-HSA-02", SIG_HSA_MATH), Map.entry("TEST-HSA-03", SIG_HSA_LITERATURE),
            Map.entry("TEST-HSA-04", SIG_HSA_SCIENCE), Map.entry("TEST-QDA-01", SIG_QDA_FULL),
            Map.entry("TEST-BCA-01", SIG_BCA_FULL), Map.entry("TEST-SPT-01", SIG_SPT_MATH),
            Map.entry("TEST-SP2E-01", SIG_SP2E_MATH_2025), Map.entry("TEST-SP2E-02", SIG_SP2E_MATH_2026),
            Map.entry("TEST-SP2E-03", SIG_SP2E_ANH_2026), Map.entry("TEST-THPTQG-02", SIG_THPTQG_LITERATURE),
            Map.entry("TEST-THPTQG-03", SIG_THPTQG_INFORMATICS));

    private record StoredExam(Map<String, Object> row, JsonNode parts, JsonNode quotas) {}

    private record Diff(String path, JsonNode before, JsonNode after) {
        @Override public String toString() { return path + ": " + before + " -> " + after; }
    }

    private StoredExam storedExam(String code) throws Exception {
        Map<String, Object> row = jdbc.queryForMap("SELECT " + EXAM_SHAPE_COLUMNS + """
                , e.name, s.parts::text AS parts, s.exam_quotas::text AS quotas
                FROM exam.exam e JOIN exam.exam_structure s ON s.exam_id = e.id WHERE e.code = ?
                """, code);
        return new StoredExam(row, mapper.readTree((String) row.get("parts")).path("parts"),
                mapper.readTree((String) row.get("quotas")));
    }

    private StoredExam templateOf(String templateCode) throws Exception {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT code, timing, duration_minutes, max_score, structure_year, matrix_year,
                       structure::text AS structure
                FROM exam.exam_template WHERE code = ?
                """, templateCode);
        JsonNode structure = mapper.readTree((String) row.get("structure"));
        return new StoredExam(row, structure.path("parts"), structure.path("examQuotas"));
    }

    private static String num(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode()
                ? "-" : node.decimalValue().stripTrailingZeros().toPlainString();
    }

    private static String joined(JsonNode array, java.util.function.Function<JsonNode, String> item, String separator) {
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode element : array) values.add(item.apply(element));
        return String.join(separator, values);
    }

    private static String matrixText(JsonNode matrix) {
        if (!matrix.isObject() || matrix.size() == 0) return "";
        return " matrix[" + matrix.path("mode").asText("NONE")
                + " G:" + joined(matrix.path("byGrade"), c -> c.path("code").asText() + "=" + num(c.path("value")), ",")
                + " D:" + joined(matrix.path("byDifficulty"), c -> c.path("code").asText() + "=" + num(c.path("value")), ",")
                + " C:" + joined(matrix.path("cells"), c -> c.path("gradeCode").asText() + "x"
                        + c.path("difficultyCode").asText() + "=" + num(c.path("value")), ",") + "]";
    }

    private static String blockLine(JsonNode block) {
        StringBuilder line = new StringBuilder("B " + block.path("templateGroupCode").asText("null") + " "
                + block.path("questionType").asText("null") + " " + num(block.path("questionCount")));
        if (block.path("setCount").asInt(0) > 0) {
            line.append(" set ").append(block.path("setCount").asInt()).append("x").append(block.path("setSize").asInt());
        }
        line.append(" pts ").append(num(block.path("requiredPointsPerQuestion")));
        if (!block.path("childAnswerType").asText("").isEmpty()) {
            line.append(" child ").append(block.path("childAnswerType").asText());
        }
        if (block.path("tagCodes").size() > 0) {
            line.append(" tags ").append(joined(block.path("tagCodes"), JsonNode::asText, ","));
        }
        if (block.path("slots").size() > 0) {
            line.append(" slots ").append(joined(block.path("slots"),
                    slot -> joined(slot.path("tagCodes"), JsonNode::asText, ","), "/"));
        }
        if (block.path("omitted").asBoolean(false)) line.append(" omitted");
        return line + matrixText(block.path("matrix"));
    }

    private static void sectionLines(List<String> out, JsonNode section, String indent) {
        String line = indent + "S " + section.path("subjectCode").asText("null") + " " + num(section.path("questionCount"))
                + matrixText(section.path("matrix"));
        JsonNode quotaItems = section.path("answerQuotas").path("items");
        if (quotaItems.size() > 0) {
            line += " quotas " + joined(quotaItems, q -> q.path("answerType").asText() + "=" + num(q.path("value")), ",");
        }
        out.add(line);
        for (JsonNode block : section.path("blocks")) out.add(indent + "  " + blockLine(block));
    }

    /** Chữ ký dòng-theo-dòng của cấu trúc: phần, môn, nhóm (mã nhóm mẫu, dạng, số câu, chùm, điểm, nhãn), ma trận. */
    private static List<String> signature(JsonNode parts, JsonNode quotas) {
        List<String> out = new java.util.ArrayList<>();
        for (JsonNode part : parts) {
            JsonNode participation = part.path("participation");
            out.add("P " + participation.path("mode").asText("null")
                    + (participation.path("choose").asInt(0) > 0 ? "/" + participation.path("choose").asInt() : "")
                    + " q=" + num(part.path("questionCount")) + " on=" + num(part.path("questionCountOnPaper"))
                    + " req=" + num(part.path("questionCountRequired")) + " pts=" + num(part.path("maxPoints"))
                    + (part.path("codeSource").isObject()
                            ? " codeSource=" + part.path("codeSource").path("source").asText() : ""));
            for (JsonNode section : part.path("sections")) sectionLines(out, section, "  ");
            for (JsonNode section : part.path("subjectPool")) sectionLines(out, section, "  pool ");
            for (JsonNode option : part.path("options")) {
                JsonNode counts = option.path("countsByPick");
                out.add("  O " + option.path("kind").asText() + " " + option.path("code").asText("null")
                        + " choose=" + num(option.path("chooseSubjects"))
                        + " counts=" + (counts.size() == 0 ? "-" : joined(counts, JsonNode::asText, ",")));
                for (JsonNode section : option.path("sections")) sectionLines(out, section, "    ");
                for (JsonNode block : option.path("blocks")) out.add("    " + blockLine(block));
            }
        }
        if (quotas != null && quotas.size() > 0) {
            out.add("Q " + joined(quotas, q -> q.path("dimension").asText() + "/" + q.path("basis").asText()
                    + "/" + q.path("enforcement").asText(), ";"));
        }
        return out;
    }

    private static final Set<String> CANON_SKIP = Set.of("id", "name", "durationMinutes");

    /**
     * Dạng so sánh: bỏ id, tên, thời lượng phần (nhập khi tạo), giá trị rỗng và giá trị mặc định mà mẫu
     * không ghi ra (v2 ghi rõ mặc định, mẫu thì không), số thập phân về cùng thang.
     */
    private JsonNode canon(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode out = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (CANON_SKIP.contains(entry.getKey())) return;
                JsonNode value = canon(entry.getValue());
                if (value == null || isDefaultValue(entry.getKey(), value)) return;
                out.set(entry.getKey(), value);
            });
            return out.size() == 0 ? null : out;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode out = mapper.createArrayNode();
            for (JsonNode child : node) {
                JsonNode value = canon(child);
                out.add(value == null ? mapper.nullNode() : value);
            }
            return out.size() == 0 ? null : out;
        }
        if (node.isNumber()) return mapper.getNodeFactory().numberNode(node.decimalValue().stripTrailingZeros());
        return node;
    }

    private static boolean isDefaultValue(String key, JsonNode value) {
        return switch (key) {
            case "omitted", "shuffleEditable", "answerTypeLocked" -> value.isBoolean() && !value.asBoolean();
            case "shuffleUnits" -> value.isBoolean() && value.asBoolean();
            case "basis" -> "QUESTION".equals(value.asText());
            case "gradeEnforcement", "difficultyEnforcement" -> "REQUIRED".equals(value.asText());
            default -> false;
        };
    }

    private JsonNode canonDocument(StoredExam stored) {
        com.fasterxml.jackson.databind.node.ObjectNode document = mapper.createObjectNode();
        document.set("parts", stored.parts());
        document.set("examQuotas", stored.quotas());
        JsonNode canonical = canon(document);
        return canonical == null ? mapper.createObjectNode() : canonical;
    }

    private static void diff(JsonNode before, JsonNode after, String path, List<Diff> out) {
        if (before == null && after == null) return;
        if (before == null || after == null) {
            out.add(new Diff(path, before, after));
        } else if (before.isObject() && after.isObject()) {
            Set<String> names = new java.util.TreeSet<>();
            before.fieldNames().forEachRemaining(names::add);
            after.fieldNames().forEachRemaining(names::add);
            for (String name : names) diff(before.get(name), after.get(name), path + "/" + name, out);
        } else if (before.isArray() && after.isArray()) {
            if (before.size() != after.size()) {
                out.add(new Diff(path + "[size]", before, after));
                return;
            }
            for (int i = 0; i < before.size(); i++) diff(before.get(i), after.get(i), path + "[" + i + "]", out);
        } else if (!before.equals(after)) {
            out.add(new Diff(path, before, after));
        }
    }

    private static JsonNode at(JsonNode root, String path) {
        JsonNode node = root;
        for (String token : path.split("/")) {
            if (token.isEmpty()) continue;
            int bracket = token.indexOf('[');
            node = bracket < 0 ? node.path(token)
                    : node.path(token.substring(0, bracket)).path(Integer.parseInt(token.substring(bracket + 1, token.length() - 1)));
        }
        return node;
    }

    /**
     * Spec 4.4: the exam row values that differ from the template row. SPT: the template sets no duration,
     * the exam uses 90; SP2E 2026 runs on the 2025 matrix (confirmed); V-ACT keeps its scale in reference_max.
     */
    private static final Map<String, Map<String, Object>> TEMPLATE_ROW_EXCEPTIONS = Map.of(
            "TEST-SPT-01", Map.of("duration_minutes", 90),
            "TEST-SP2E-02", Map.of("matrix_year", 2025),
            "TEST-VACT-01", java.util.Collections.singletonMap("max_score", null));

    private static String plain(Object value) {
        return value instanceof BigDecimal number ? number.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }

    @Test
    void everyTestExamMatchesTemplate() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (String code : BASE_EXAMS) {
            StoredExam exam = storedExam(code);
            String templateCode = (String) exam.row().get("template_code");
            StoredExam template = templateOf(templateCode);
            String documented = DOCUMENTED_SIGNATURES.get(code);
            if (documented != null) {
                List<String> expected = documented.lines().toList();
                // The BA template itself is what spec 4.4 documents ...
                softly.assertThat(signature(template.parts(), template.quotas()))
                        .as("template %s as documented", templateCode).isEqualTo(expected);
                // ... and the base exam carries it: groups, labels, clusters, matrices, omitted.
                softly.assertThat(signature(exam.parts(), exam.quotas()))
                        .as("%s signature", code).isEqualTo(expected);
            }
            // Raw, value for value: the stored parts (ids, names, minutes, labels, matrices) and exam quotas
            // are the migrated template's, with no normalisation.
            softly.assertThat(jdbc.queryForObject("""
                    SELECT s.parts->'parts' = t.structure->'parts'
                       AND coalesce(s.exam_quotas, '[]'::jsonb) = coalesce(t.structure->'examQuotas', '[]'::jsonb)
                    FROM exam.exam e JOIN exam.exam_structure s ON s.exam_id = e.id
                    JOIN exam.exam_template t ON t.code = e.template_code WHERE e.code = ?
                    """, Boolean.class, code)).as("%s stored parts and exam_quotas equal template %s", code, templateCode)
                    .isTrue();
            // The exam row follows the template row, except the documented rows.
            Map<String, Object> exceptions = TEMPLATE_ROW_EXCEPTIONS.getOrDefault(code, Map.of());
            for (String column : List.of("timing", "duration_minutes", "max_score", "structure_year", "matrix_year")) {
                Object expected = exceptions.containsKey(column) ? exceptions.get(column) : template.row().get(column);
                softly.assertThat(plain(exam.row().get(column)))
                        .as("%s %s %s", code, column, exceptions.containsKey(column)
                                ? "(documented exception)" : "equals template " + templateCode)
                        .isEqualTo(plain(expected));
            }
            softly.assertThat(exam.row().get("years_confirmed")).as("%s years confirmed", code).isEqualTo(true);

            int required = 0;
            for (JsonNode part : exam.parts()) required += part.path("questionCountRequired").asInt();
            softly.assertThat(exam.row().get("question_count")).as("%s question_count", code)
                    .isEqualTo(BASE_QUESTION_COUNT.get(code));
            softly.assertThat(required).as("%s sum of questionCountRequired", code)
                    .isEqualTo(BASE_QUESTION_COUNT.get(code));
            BigDecimal maxScore = (BigDecimal) exam.row().get("max_score");
            if (BASE_MAX_SCORE.containsKey(code)) {
                softly.assertThat(maxScore).as("%s max_score", code).isNotNull();
                if (maxScore != null) {
                    softly.assertThat(maxScore).as("%s max_score", code)
                            .isEqualByComparingTo(new BigDecimal(BASE_MAX_SCORE.get(code)));
                }
            } else {
                softly.assertThat(maxScore).as("%s max_score (V-ACT keeps reference_max only)", code).isNull();
                softly.assertThat((BigDecimal) exam.row().get("reference_max")).as("%s reference_max", code)
                        .isEqualByComparingTo(new BigDecimal("1200"));
            }
        }
        softly.assertAll();
    }

    @Test
    void generatorMatchesBlockMembersOnly() throws Exception {
        UUID examId = jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = 'TEST-HSA-02'", UUID.class);
        // Positive control: blocks that sit in subjectPool, option sections and plain sections keep their refs.
        List<String> controls = List.of("TEST-HSA-04", "TEST-QDA-01", "TEST-BCA-01", "TEST-THPTQG-03");
        Map<String, Map<String, List<Object>>> controlRefs = new LinkedHashMap<>();
        for (String code : controls) {
            controlRefs.put(code, describeSeededRefs(jdbc.queryForObject(
                    "SELECT id FROM exam.exam WHERE code = ?", UUID.class, code)));
            assertThat(controlRefs.get(code)).as("%s has seeded refs", code).isNotEmpty();
        }

        var document = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, examId));
        var part = (com.fasterxml.jackson.databind.node.ObjectNode) document.path("parts").get(0);
        var section = (com.fasterxml.jackson.databind.node.ObjectNode) part.path("sections").get(0);
        var blocks = (com.fasterxml.jackson.databind.node.ArrayNode) section.path("blocks");
        assertThat(blocks.size()).as("HSA-02 blocks").isGreaterThanOrEqualTo(3);
        String kept = blocks.get(0).path("id").asText();
        String reusedBySection = blocks.get(blocks.size() - 1).path("id").asText();
        String reusedByPart = blocks.get(blocks.size() - 2).path("id").asText();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL
                  AND block_id IN (?, ?, ?)""", Integer.class, examId, kept, reusedBySection, reusedByPart))
                .as("seeded refs of the three blocks").isEqualTo(3);

        // An admin removed two groups; the part and the section then got the removed groups' ids.
        blocks.remove(blocks.size() - 1);
        blocks.remove(blocks.size() - 1);
        section.put("id", reusedBySection);
        part.put("id", reusedByPart);
        jdbc.update("UPDATE exam.exam_structure SET parts = CAST(? AS jsonb), structure_hash = 'edited-by-test', "
                + "edited = true WHERE exam_id = ?", document.toString(), examId);

        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/devdata/R__seed_test_exam_data.sql"));

        List<String> remaining = jdbc.queryForList(
                "SELECT block_id FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL", String.class, examId);
        assertThat(remaining).as("a part or section that reuses a removed group's id does not keep its ref")
                .doesNotContain(reusedBySection, reusedByPart).contains(kept);
        assertThat(mapper.readTree(jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?",
                String.class, examId))).as("the edited structure is kept").isEqualTo(document);
        for (String code : controls) {
            assertThat(describeSeededRefs(jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = ?", UUID.class, code)))
                    .as("%s refs after the seed", code).isEqualTo(controlRefs.get(code));
        }
    }

    @Test
    void qdaCodesMatchCatalog() throws Exception {
        UUID qda = UUID.fromString("00000000-0000-7000-8200-000000000102");
        List<Map<String, Object>> catalog = jdbc.queryForList("""
                SELECT i.code, i.name_vi FROM catalog.combination_version v
                JOIN catalog.catalog_item i ON i.id = v.item_id
                WHERE v.is_current AND v.exam_item_id = ? ORDER BY i.value
                """, qda);
        assertThat(catalog.stream().map(row -> (String) row.get("code")).toList())
                .containsExactly("QD01", "QD02", "QD03", "QD04", "QD05");
        Set<String> digits = new java.util.HashSet<>();
        for (Map<String, Object> row : catalog) {
            String code = (String) row.get("code");
            digits.add(code.substring(2));
            // The catalog spells the code in ASCII and the name in Vietnamese: "QD02" / "QĐ02 · ...".
            assertThat((String) row.get("name_vi")).as(code).startsWith("QĐ" + code.substring(2) + " ");
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM catalog.catalog_item i
                WHERE i.group_id = '00000000-0000-7000-8100-000000000007' AND i.code_normalized = 'qd05'
                """, Integer.class)).as("the seed's own QD05 insert must not duplicate the catalog row").isEqualTo(1);

        // Every QDA code the seeded structure writes is a catalog code (ASCII in code fields) and every
        // QDA name it writes uses the catalog's Vietnamese spelling.
        java.util.regex.Pattern token = java.util.regex.Pattern.compile("Q[DĐ]\\d{2}");
        Set<String> codeFields = Set.of("code", "id", "optionId", "pathId", "branch");
        JsonNode structure = storedExam("TEST-QDA-01").parts();
        List<String> violations = new java.util.ArrayList<>();
        collectQdaTokens(structure, null, token, codeFields, digits, violations);
        assertThat(violations).as("QDA codes and names in TEST-QDA-01").isEmpty();
        assertThat(jdbc.queryForList("SELECT description FROM exam.exam WHERE code = 'TEST-QDA-01'", String.class)
                .getFirst()).doesNotContainPattern("QD\\d{2}");
    }

    private static void collectQdaTokens(JsonNode node, String field, java.util.regex.Pattern token,
                                         Set<String> codeFields, Set<String> catalogDigits, List<String> violations) {
        if (node.isTextual()) {
            var matcher = token.matcher(node.asText());
            while (matcher.find()) {
                String found = matcher.group();
                String digits = found.substring(2);
                boolean asciiField = field != null && codeFields.contains(field);
                if (!catalogDigits.contains(digits)) {
                    violations.add(field + "=" + node.asText() + ": no such catalog code");
                } else if (asciiField && !found.startsWith("QD")) {
                    violations.add(field + "=" + node.asText() + ": a code must be written QD" + digits);
                } else if (!asciiField && !found.startsWith("QĐ")) {
                    violations.add(field + "=" + node.asText() + ": a name must be written QĐ" + digits);
                }
            }
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    collectQdaTokens(entry.getValue(), entry.getKey(), token, codeFields, catalogDigits, violations));
        } else if (node.isArray()) {
            for (JsonNode child : node) collectQdaTokens(child, field, token, codeFields, catalogDigits, violations);
        }
    }

    @Test
    void errorFixturesDifferByOneChange() throws Exception {
        assertThat(jdbc.queryForList("SELECT code FROM exam.exam WHERE code LIKE 'TEST-ERR-%' ORDER BY code", String.class))
                .as("every documented error fixture is seeded").containsExactlyElementsOf(
                        ERROR_BASES.keySet().stream().sorted().toList());
        SoftAssertions softly = new SoftAssertions();
        for (var entry : new java.util.TreeMap<>(ERROR_BASES).entrySet()) {
            String code = entry.getKey();
            StoredExam error = storedExam(code);
            StoredExam base = storedExam(entry.getValue());
            StoredExam template = templateOf((String) error.row().get("template_code"));
            JsonNode errorDoc = canonDocument(error);

            // Same exam shape; only the documented change differs (TEST-ERR-13 flips the year confirmation).
            for (String column : EXAM_SHAPE_COLUMNS.replace("e.", "").replace("\n", " ").split(",\\s*")) {
                String name = column.trim();
                if ("TEST-ERR-13".equals(code) && "years_confirmed".equals(name)) {
                    softly.assertThat(error.row().get(name)).as("%s %s", code, name).isEqualTo(false);
                    softly.assertThat(base.row().get(name)).as("%s base %s", entry.getValue(), name).isEqualTo(true);
                } else {
                    softly.assertThat(error.row().get(name)).as("%s %s equals %s", code, name, entry.getValue())
                            .isEqualTo(base.row().get(name));
                }
            }
            // The change is one change from the new base, and from the BA template the base copies.
            for (var origin : Map.of(entry.getValue(), canonDocument(base),
                    "template " + error.row().get("template_code"), canonDocument(template)).entrySet()) {
                List<Diff> diffs = new java.util.ArrayList<>();
                diff(origin.getValue(), errorDoc, "", diffs);
                assertDocumentedChange(softly, code + " vs " + origin.getKey(), code, diffs, errorDoc);
            }
        }
        softly.assertAll();

        // Seeding again does not lose a fixture.
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/devdata/R__seed_test_exam_data.sql"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam WHERE code LIKE 'TEST-ERR-%'", Integer.class))
                .isEqualTo(ERROR_BASES.size());
    }

    private int blockIndex(SoftAssertions softly, String label, List<Diff> diffs, String regex) {
        var pattern = java.util.regex.Pattern.compile(regex);
        Set<String> indexes = new java.util.TreeSet<>();
        for (Diff diff : diffs) {
            var matcher = pattern.matcher(diff.path());
            if (matcher.matches()) {
                indexes.add(matcher.group(1));
            } else {
                softly.assertThat(diff.toString()).as("%s: change outside %s", label, regex).isNull();
            }
        }
        softly.assertThat(indexes).as("%s: one block, one change (diffs %s)", label, diffs).hasSize(1);
        return indexes.isEmpty() ? -1 : Integer.parseInt(indexes.iterator().next());
    }

    private UUID catalogIdByCode(String code) {
        return jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE code = ?", UUID.class, code);
    }

    /** The documented change of each error fixture (spec đề test mục 4.2, cột "Đổi gì"). */
    private void assertDocumentedChange(SoftAssertions softly, String label, String code, List<Diff> diffs, JsonNode doc)
            throws Exception {
        if ("TEST-ERR-13".equals(code)) {
            softly.assertThat(diffs).as("%s: the structure is untouched", label).isEmpty();
            return;
        }
        softly.assertThat(diffs).as("%s: at least one change", label).isNotEmpty();
        if (diffs.isEmpty()) return;
        switch (code) {
            case "TEST-ERR-01" -> {
                int block = blockIndex(softly, label, diffs, "^/parts\\[0]/sections\\[0]/blocks\\[(\\d+)]/matrix.*$");
                JsonNode matrix = at(doc, "/parts[0]/sections[0]/blocks[" + block + "]/matrix");
                softly.assertThat(matrix.path("mode").isMissingNode()).as("%s: no way to apply chosen", label).isTrue();
                softly.assertThat(matrix.path("byGrade").toString()).as("%s: grade 12 = 12", label)
                        .contains("\"G12\"").contains("12");
            }
            case "TEST-ERR-02" -> {
                JsonNode part = doc.path("parts").path(1);
                Set<String> expected = new java.util.TreeSet<>();
                for (int s = 0; s < part.path("sections").size(); s++) {
                    if (!"ANH".equals(part.path("sections").path(s).path("subjectCode").asText())) continue;
                    for (int b = 0; b < part.path("sections").path(s).path("blocks").size(); b++) {
                        expected.add("/parts[1]/sections[" + s + "]/blocks[" + b + "]/requiredPointsPerQuestion");
                    }
                }
                Set<String> changed = new java.util.TreeSet<>();
                for (Diff diff : diffs) changed.add(diff.path());
                softly.assertThat(changed).as("%s: every English group goes 0.75 -> 1", label).isEqualTo(expected);
                for (Diff diff : diffs) {
                    softly.assertThat(num(diff.before())).as("%s before", label).isEqualTo("0.75");
                    softly.assertThat(num(diff.after())).as("%s after", label).isEqualTo("1");
                }
            }
            case "TEST-ERR-03" -> {
                int block = blockIndex(softly, label, diffs, "^/parts\\[0]/sections\\[0]/blocks\\[(\\d+)]/tagCodes$");
                JsonNode group = at(doc, "/parts[0]/sections[0]/blocks[" + block + "]");
                softly.assertThat(group.path("templateGroupCode").asText()).as("%s: the context set", label)
                        .isEqualTo("HSA_MATH_CONTEXT");
                softly.assertThat(diffs).as("%s: one change only", label).hasSize(1);
                softly.assertThat(diffs.getFirst().before()).as("%s: the base group has no labels", label).isNull();
                softly.assertThat(diffs.getFirst().after()).as("%s: tagCodes == [TEST-HIEM]", label)
                        .isEqualTo(canon(mapper.readTree("[\"TEST-HIEM\"]")));
            }
            case "TEST-ERR-04" -> {
                int block = blockIndex(softly, label, diffs, "^/parts\\[0]/sections\\[0]/blocks\\[(\\d+)]/questionCount$");
                softly.assertThat((diffs.getFirst().before() == null ? -1 : diffs.getFirst().before().asInt())
                        - (diffs.getFirst().after() == null ? -1 : diffs.getFirst().after().asInt()))
                        .as("%s: one question fewer (49 for 50)", label).isEqualTo(1);
                softly.assertThat(at(doc, "/parts[0]/sections[0]/blocks[" + block + "]/questionType").asText())
                        .as("%s: a single-question group", label).isEqualTo("SINGLE_CHOICE");
            }
            case "TEST-ERR-05" -> {
                int item = blockIndex(softly, label, diffs,
                        "^/parts\\[0]/sections\\[0]/answerQuotas/items\\[(\\d+)]/value$");
                softly.assertThat(at(doc, "/parts[0]/sections[0]/answerQuotas/items[" + item + "]/answerType").asText())
                        .as("%s: the short-answer quota", label).isEqualTo("SHORT_ANSWER");
                softly.assertThat(num(diffs.getFirst().before()) + "->" + num(diffs.getFirst().after()))
                        .as("%s: 15 -> 14", label).isEqualTo("15->14");
            }
            case "TEST-ERR-06" -> {
                int block = blockIndex(softly, label, diffs,
                        "^/parts\\[0]/sections\\[0]/blocks\\[(\\d+)]/questionType$");
                JsonNode retyped = at(doc, "/parts[0]/sections[0]/blocks[" + block + "]");
                softly.assertThat(retyped.path("templateGroupCode").asText()).as("%s: the Math group", label)
                        .isEqualTo("HSA_MATH_SINGLE");
                softly.assertThat(retyped.path("questionType").asText()).as("%s: retyped", label)
                        .isEqualTo("SINGLE_CHOICE");
                softly.assertThat(diffs.getFirst().before().asText()).as("%s: the short-answer group", label)
                        .isEqualTo("SHORT_ANSWER");
            }
            case "TEST-ERR-07" -> {
                int section = blockIndex(softly, label, diffs,
                        "^/parts\\[0]/options\\[0]/sections\\[(\\d+)]/knowledgeScope.*$");
                softly.assertThat(at(doc, "/parts[0]/options[0]/sections[" + section + "]/subjectCode").asText())
                        .as("%s: Geography", label).isEqualTo("DIA");
                softly.assertThat(at(doc, "/parts[0]/options[0]/sections[" + section + "]/knowledgeScope/nodeIds").toString())
                        .as("%s: the narrow Geography node", label).contains(catalogIdByCode("TEST-DIA-HEP").toString());
            }
            case "TEST-ERR-08" -> {
                // Exactly one change: the Physics short-answer group loses its template group code
                // (HSA_LY_FLEX -> absent); a different valid Physics edit must not pass.
                softly.assertThat(diffs).as("%s: the control differs by one change only", label).hasSize(1);
                java.util.regex.Matcher path = java.util.regex.Pattern
                        .compile("^/parts\\[0]/options\\[0]/sections\\[(\\d+)]/blocks\\[(\\d+)]/templateGroupCode$")
                        .matcher(diffs.getFirst().path());
                softly.assertThat(path.matches()).as("%s: changed path %s", label, diffs.getFirst().path()).isTrue();
                if (path.matches()) {
                    String block = "/parts[0]/options[0]/sections[" + path.group(1) + "]/blocks[" + path.group(2) + "]";
                    softly.assertThat(at(doc, "/parts[0]/options[0]/sections[" + path.group(1) + "]/subjectCode").asText())
                            .as("%s: Physics only", label).isEqualTo("LY");
                    softly.assertThat(at(doc, block + "/questionType").asText()).as("%s: the short-answer group", label)
                            .isEqualTo("SHORT_ANSWER");
                }
                softly.assertThat(diffs.getFirst().before()).as("%s: before", label).isEqualTo(mapper.getNodeFactory().textNode("HSA_LY_FLEX"));
                softly.assertThat(diffs.getFirst().after()).as("%s: after (removed)", label).isNull();
            }
            case "TEST-ERR-09" -> {
                int section = blockIndex(softly, label, diffs,
                        "^/parts\\[0]/options\\[0]/sections\\[(\\d+)]/answerQuotas.*$");
                String base = "/parts[0]/options[0]/sections[" + section + "]";
                softly.assertThat(at(doc, base + "/subjectCode").asText()).as("%s: Chemistry", label).isEqualTo("HOA");
                softly.assertThat(at(doc, base + "/questionCount").asInt()).as("%s: a 17-question subject", label)
                        .isEqualTo(17);
                // Exactly one change: the quota block appears, as 12 single-choice + 4 short-answer (16 for 17).
                softly.assertThat(diffs).as("%s: one change only", label).hasSize(1);
                softly.assertThat(diffs.getFirst().path()).as("%s: changed path", label).isEqualTo(base + "/answerQuotas");
                softly.assertThat(diffs.getFirst().before()).as("%s: the base has no quotas", label).isNull();
                softly.assertThat(diffs.getFirst().after()).as("%s: the typed entries", label)
                        .isEqualTo(canon(mapper.readTree("""
                                {"unit":"COUNT","items":[{"answerType":"SINGLE_CHOICE","value":12},
                                                         {"answerType":"SHORT_ANSWER","value":4}]}""")));
            }
            case "TEST-ERR-10" -> {
                softly.assertThat(diffs).as("%s: one count", label).hasSize(1);
                softly.assertThat(diffs.getFirst().path()).isEqualTo("/parts[0]/options[0]/countsByPick[2]");
                softly.assertThat(num(diffs.getFirst().before()) + "->" + num(diffs.getFirst().after()))
                        .as("%s: 17,17,16 -> 17,17,17", label).isEqualTo("16->17");
            }
            case "TEST-ERR-11" -> {
                int block = blockIndex(softly, label, diffs, "^/parts\\[0]/sections\\[0]/blocks\\[(\\d+)]/matrix.*$");
                JsonNode cells = at(doc, "/parts[0]/sections[0]/blocks[" + block + "]/matrix/cells");
                boolean found = false;
                for (JsonNode cell : cells) {
                    found |= "G12".equals(cell.path("gradeCode").asText()) && "VDC".equals(cell.path("difficultyCode").asText())
                            && cell.path("value").asInt() == 12;
                }
                softly.assertThat(found).as("%s: cell Grade 12 x VDC = 12 in %s", label, cells).isTrue();
            }
            case "TEST-ERR-12" -> {
                int block = blockIndex(softly, label, diffs,
                        "^/parts\\[2]/sections\\[0]/blocks\\[(\\d+)]/requiredPointsPerQuestion$");
                softly.assertThat(at(doc, "/parts[2]/sections[0]/blocks[" + block + "]/questionType").asText())
                        .as("%s: the short-answer group", label).isEqualTo("SHORT_ANSWER");
                softly.assertThat(diffs.getFirst().after()).as("%s: points per question dropped", label).isNull();
            }
            default -> softly.fail("%s: no documented change for %s", label, code);
        }
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

    @Test
    void seededBlockRefsEqualTheProductionProjectionOfTheStoredStructure() throws Exception {
        var exams = jdbc.queryForList("""
                select e.id, e.code, s.parts::text as document from exam.exam e
                join exam.exam_structure s on s.exam_id = e.id where e.code like 'TEST-%' order by e.code
                """);
        assertThat(exams).isNotEmpty();
        SoftAssertions softly = new SoftAssertions();
        for (var exam : exams) {
            softly.assertThat(describeRefs(projectedRefs((UUID) exam.get("id"), (String) exam.get("document"))))
                    .as((String) exam.get("code"))
                    .isEqualTo(describeSeededRefs((UUID) exam.get("id")));
        }
        softly.assertAll();
    }

    @Test
    void seededVactBlocksCarryCompetenciesThatUsageReportsFindOnTheExam() {
        for (String code : List.of("VACT-NN-TV", "VACT-NN-TA", "VACT-TOAN", "VACT-TD-LOGIC", "VACT-TD-SL")) {
            UUID competency = jdbc.queryForObject("""
                    select i.id from catalog.catalog_item i join catalog.catalog_group g on g.id = i.group_id
                    where g.code = 'COMPETENCY' and i.code = ?
                    """, UUID.class, code);
            var usages = usageProvider.findUsages("COMPETENCY", competency);
            assertThat(usages).as(code).hasSize(1);
            assertThat(usages.getFirst().sampleLabels()).as(code).contains("TEST-VACT-01");
        }
    }

    @Test
    void rerunningTheSeedKeepsRefsAlignedWithPriorAndEditedStructures() throws Exception {
        UUID prior = jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = 'TEST-THPTQG-01'", UUID.class);
        UUID edited = jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = 'TEST-HSA-02'", UUID.class);
        // A seeded draft from before the split keeps its stored structure and its own projection.
        String legacy;
        try (var stream = getClass().getResourceAsStream("/db/migration/exam/V202611180652__exam_templates_ba_numbers.sql")) {
            String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String statement = sql.substring(0, sql.indexOf("WHERE code = 'THPTQG_MATH_2025'"));
            statement = statement.substring(statement.lastIndexOf("structure = '") + "structure = '".length());
            legacy = statement.substring(0, statement.lastIndexOf("'::jsonb")).replace("''", "'");
        }
        jdbc.update("UPDATE exam.exam_structure SET parts = CAST(? AS jsonb) WHERE exam_id = ?", legacy, prior);
        jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ?", prior);
        var legacyRefs = projectedRefs(prior, legacy);
        for (var ref : legacyRefs) {
            jdbc.update("INSERT INTO exam.exam_block_ref (id, exam_id, block_id, part_id, knowledge_inherited) "
                    + "VALUES (?, ?, ?, ?, true)", UUID.randomUUID(), prior, ref.getBlockId(), ref.getPartId());
        }
        // A draft whose structure really changed through the API: a block removed and another block's
        // points changed. The API rewrote its refs completely, with its own ids and metadata.
        var apiStructure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", edited)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var editedParts = (com.fasterxml.jackson.databind.node.ArrayNode) apiStructure.path("parts").deepCopy();
        var blockArrays = editedParts.findValues("blocks");
        var shrinking = (com.fasterxml.jackson.databind.node.ArrayNode) blockArrays.stream()
                .filter(array -> array.size() >= 2).findFirst().orElseThrow();
        String removedBlock = shrinking.get(shrinking.size() - 1).path("id").asText();
        shrinking.remove(shrinking.size() - 1);
        var firstBlock = (com.fasterxml.jackson.databind.node.ObjectNode) blockArrays.getFirst().get(0);
        String changedBlock = firstBlock.path("id").asText();
        firstBlock.put("requiredPointsPerQuestion", firstBlock.path("requiredPointsPerQuestion").asInt(1) + 3);
        var update = mapper.createObjectNode().put("expectedRevision", apiStructure.path("revision").asLong());
        update.set("parts", editedParts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", edited).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isOk());
        String editedDocument = jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, edited);
        assertThat(editedDocument).contains(changedBlock).doesNotContain(removedBlock);
        var editedProjection = describeRefs(projectedRefs(edited, editedDocument));
        assertThat(describeSeededRefs(edited)).as("API-written refs are complete").isEqualTo(editedProjection);
        assertThat(editedProjection).containsKey(changedBlock).doesNotContainKey(removedBlock);
        List<Map<String, Object>> editedBefore = jdbc.queryForList(
                "SELECT id, block_id FROM exam.exam_block_ref WHERE exam_id = ? ORDER BY block_id", edited);

        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                new ClassPathResource("db/devdata/R__seed_test_exam_data.sql"));

        assertThat(jdbc.queryForList("SELECT block_id FROM exam.exam_block_ref WHERE exam_id = ? ORDER BY block_id",
                String.class, prior)).containsExactlyElementsOf(
                        legacyRefs.stream().map(ref -> ref.getBlockId()).sorted().toList());
        assertThat(jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?",
                String.class, edited)).as("edited structure is retained").isEqualTo(editedDocument);
        assertThat(jdbc.queryForList("SELECT id, block_id FROM exam.exam_block_ref WHERE exam_id = ? ORDER BY block_id",
                edited)).isEqualTo(editedBefore);
        assertThat(describeSeededRefs(edited)).as("projected refs incl. metadata are retained")
                .isEqualTo(editedProjection);
        for (var exam : jdbc.queryForList("SELECT e.id FROM exam.exam e WHERE e.code LIKE 'TEST-%'", UUID.class)) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM exam.exam_block_ref r
                    WHERE r.exam_id = ? AND r.version_no IS NULL AND NOT EXISTS (
                        SELECT 1 FROM exam.exam_structure s WHERE s.exam_id = r.exam_id
                        AND jsonb_path_exists(s.parts, '$.** ? (@.id == $block)', jsonb_build_object('block', r.block_id)))
                    """, Integer.class, exam)).as("phantom refs of %s", exam).isZero();
        }
    }

    private List<vn.unicom.thi360.exam.domain.ExamBlockRef> projectedRefs(UUID examId, String document) {
        var doc = new ExamStructureCodec(mapper).read(document);
        return projector.project(examId, doc.parts(), new ExamBlockRefProjector.CodeResolver(
                code -> catalogItem("SUBJECT", code), code -> catalogItem("COMPETENCY", code),
                code -> catalogItem("QUESTION_TAG", code)));
    }

    private UUID catalogItem(String group, String code) {
        return jdbc.query("""
                select i.id from catalog.catalog_item i join catalog.catalog_group g on g.id = i.group_id
                where g.code = ? and i.code = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, group, code);
    }

    private static String ids(List<?> values) {
        return values == null ? null : values.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private static Map<String, List<Object>> describeRefs(List<vn.unicom.thi360.exam.domain.ExamBlockRef> refs) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (var ref : refs) {
            result.put(ref.getBlockId(), java.util.Arrays.asList(ref.getPartId(), ref.getOptionCode(),
                    ref.getSectionId(), ref.getSubjectItemId(), ref.getQuestionType(), ids(ref.getAnswerTypes()),
                    ids(ref.getKnowledgeItemIds()), ref.getKnowledgeMode(), ref.isKnowledgeInherited(),
                    ids(ref.getTagItemIds()), ids(ref.getCompetencyItemIds()),
                    ref.getRequiredPoints() == null ? null : ref.getRequiredPoints().stripTrailingZeros().toPlainString(),
                    ref.getCombinationItemId()));
        }
        return new java.util.TreeMap<>(result);
    }

    private Map<String, List<Object>> describeSeededRefs(UUID examId) {
        Map<String, List<Object>> result = new java.util.TreeMap<>();
        jdbc.query("""
                select block_id, part_id, option_code, section_id, subject_item_id, question_type,
                       answer_types::text as answer_types, knowledge_item_ids::text as knowledge,
                       knowledge_mode, knowledge_inherited, tag_item_ids::text as tags,
                       competency_item_ids::text as competencies, required_points, combination_item_id
                from exam.exam_block_ref where exam_id = ? and version_no is null
                """, rs -> {
            BigDecimal points = rs.getBigDecimal("required_points");
            result.put(rs.getString("block_id"), java.util.Arrays.asList(rs.getString("part_id"),
                    rs.getString("option_code"), rs.getString("section_id"), rs.getObject("subject_item_id", UUID.class),
                    rs.getString("question_type"), rs.getString("answer_types"), rs.getString("knowledge"),
                    rs.getString("knowledge_mode"), rs.getBoolean("knowledge_inherited"), rs.getString("tags"),
                    rs.getString("competencies"), points == null ? null : points.stripTrailingZeros().toPlainString(),
                    rs.getObject("combination_item_id", UUID.class)));
        }, examId);
        return result;
    }
}
