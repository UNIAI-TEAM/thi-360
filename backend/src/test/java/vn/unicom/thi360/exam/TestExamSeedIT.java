package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
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
    void assumedExamsHaveTheirSpecifiedCheckResults() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        List<String> valid = List.of("TEST-HSA-01", "TEST-HSA-02", "TEST-HSA-03", "TEST-HSA-04",
                "TEST-QDA-01", "TEST-BCA-01", "TEST-VACT-01", "TEST-SPT-01", "TEST-SP2E-01",
                "TEST-SP2E-02", "TEST-SP2E-03", "TEST-THPTQG-01", "TEST-THPTQG-02", "TEST-THPTQG-03");
        for (String code : valid) {
            JsonNode result = check(code);
            softly.assertThat(result.path("status").asText()).as("%s: %s", code, codes(result)).isEqualTo("VALID");
            softly.assertThat(result.path("issueCount").asInt()).as(code).isZero();
        }

        Map<String, Set<String>> invalid = new LinkedHashMap<>();
        invalid.put("TEST-ERR-01", Set.of("MATRIX_INCOMPLETE"));
        // Part 2 is 65 instead of 60; the full paper is 105 instead of 100.
        invalid.put("TEST-ERR-02", Set.of("PART_POINTS_MISMATCH", "POINTS_TOTAL_MISMATCH"));
        invalid.put("TEST-ERR-03", Set.of("SET_SHORTAGE"));
        // 40 + 9 = 49 instead of 50; the solver also cannot satisfy the 50-unit matrix.
        invalid.put("TEST-ERR-04", Set.of("SECTION_TOTAL_MISMATCH", "PART_POINTS_MISMATCH",
                "PATH_TOTAL_MISMATCH", "POINTS_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        // The 35 + 14 quota totals 49 for a 50-question section.
        invalid.put("TEST-ERR-05", Set.of("ANSWER_QUOTA_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        invalid.put("TEST-ERR-06", Set.of("ANSWER_TYPE_SHORTAGE"));
        // 6 set children + 11 fixed singles = 17 even when physics receives 16.
        invalid.put("TEST-ERR-08", Set.of("REMAINDER_GROUP_INVALID", "CELL_SHORTAGE",
                "PART_POINTS_MISMATCH", "POINTS_TOTAL_MISMATCH"));
        // 12 + 4 is fixed at 16 but the choice can assign chemistry 17.
        invalid.put("TEST-ERR-09", Set.of("SUBJECT_QUOTA_COUNT_VARIES", "CELL_SHORTAGE"));
        // 17 + 17 + 17 = 51, not 50. Masking the invalid option also leaves the part empty.
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
        softly.assertThat(codes(narrow)).as("TEST-ERR-07").isEqualTo(Set.of("CELL_SHORTAGE"));
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
            }
        }
        softly.assertThat(selections).as("TEST-ERR-07 selections").isEqualTo(12);
        softly.assertThat(failures).as("TEST-ERR-07 failed selections").isEqualTo(3);
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
        JsonNode shortage = result.path("groups").findValues("issues").stream()
                .flatMap(issues -> java.util.stream.StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> "CELL_SHORTAGE".equals(issue.path("code").asText()))
                .findFirst().orElseThrow();
        assertThat(shortage.path("subjectCode").asText()).isEqualTo("DIA");
        assertThat(shortage.path("subjectCount").asInt(-1)).isEqualTo(17);
        assertThat(shortage.path("required").asInt(-1)).isEqualTo(11);
        assertThat(shortage.path("available").asInt(-1)).isEqualTo(10);
        Set<Set<String>> failed = new java.util.HashSet<>();
        int failedCount = 0;
        for (JsonNode path : result.path("paths")) {
            for (JsonNode selection : path.path("selections")) {
                if (!"INVALID".equals(selection.path("result").asText())) continue;
                failedCount++;
                JsonNode selectionIssue = selection.path("issues").get(0);
                assertThat(selectionIssue.path("required").asInt(-1)).isEqualTo(11);
                assertThat(selectionIssue.path("available").asInt(-1)).isEqualTo(10);
                Set<String> subjects = new java.util.HashSet<>();
                for (JsonNode subject : selection.path("subjectCodes")) subjects.add(subject.asText());
                failed.add(subjects);
                int geography = -1;
                for (int i = 0; i < selection.path("subjectCodes").size(); i++) {
                    if ("DIA".equals(selection.path("subjectCodes").get(i).asText())) geography = i;
                }
                assertThat(geography).isGreaterThanOrEqualTo(0);
                assertThat(selection.path("counts").get(geography).asInt()).isEqualTo(17);
            }
        }
        assertThat(failedCount).isEqualTo(3);
        assertThat(failed).containsExactlyInAnyOrder(
                Set.of("LY", "SU", "DIA"), Set.of("HOA", "SU", "DIA"), Set.of("SINH", "SU", "DIA"));
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
