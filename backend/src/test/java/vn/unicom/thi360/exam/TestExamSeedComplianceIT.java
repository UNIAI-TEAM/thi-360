package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2701 AC-2 and AC-6: every seeded {@code TEST-*} exam, on the real check and preview services, gives
 * the result the exam-test spec (docs/specs/2026-11-17-de-test-gia-dinh.md 4.1, 4.2) documents.
 */
@Transactional
class TestExamSeedComplianceIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    /** Status, the documented codes (all must come, nothing else may), and the few codes the spec makes optional. */
    private record Expected(String status, Set<String> codes, Set<String> optionalWarnings) {
        static Expected valid(String... codes) { return new Expected("VALID", Set.of(codes), Set.of()); }
        static Expected invalid(String... codes) { return new Expected("INVALID", Set.of(codes), Set.of()); }
        Expected optional(String... warnings) { return new Expected(status, codes, Set.of(warnings)); }
    }

    private static final Set<String> REFERENCE_WARNINGS = Set.of("EXAM_QUOTA_DEVIATION", "DISTRIBUTION_DEVIATION");

    /** Spec 4.1, column "Kết quả", for the base exams rebuilt on the BA templates (4.4). */
    private static final Map<String, Expected> BASE_EXAMS = new LinkedHashMap<>();
    static {
        // Spec 4.1 "Kết quả": the exact code set, warnings included.
        BASE_EXAMS.put("TEST-HSA-01", Expected.valid("EXAM_QUOTA_DEVIATION"));
        BASE_EXAMS.put("TEST-HSA-02", Expected.valid());
        BASE_EXAMS.put("TEST-HSA-03", Expected.valid());
        BASE_EXAMS.put("TEST-HSA-04", Expected.valid());
        BASE_EXAMS.put("TEST-QDA-01", Expected.valid());
        BASE_EXAMS.put("TEST-BCA-01", Expected.valid("MANUAL_GRADING_REQUIRED", "DISTRIBUTION_DEVIATION",
                "EXAM_QUOTA_DEVIATION"));
        // Spec 4.1: V-ACT "chỉ được có cảnh báo DISTRIBUTION_DEVIATION" (REFERENCE tables): the one optional code.
        BASE_EXAMS.put("TEST-VACT-01", Expected.valid().optional("DISTRIBUTION_DEVIATION"));
        // The template's essay group is omitted: reported as present, and nothing is left to grade by hand.
        BASE_EXAMS.put("TEST-SPT-01", Expected.valid("OMITTED_GROUPS_PRESENT"));
        BASE_EXAMS.put("TEST-SP2E-01", Expected.valid());
        BASE_EXAMS.put("TEST-SP2E-02", Expected.valid("EXAM_QUOTA_DEVIATION"));
        BASE_EXAMS.put("TEST-SP2E-03", Expected.valid("EXAM_QUOTA_DEVIATION"));
        BASE_EXAMS.put("TEST-THPTQG-01", Expected.valid());
        // D11: the Literature template is not publishable; saving a draft stays possible.
        BASE_EXAMS.put("TEST-THPTQG-02", Expected.invalid("TEMPLATE_NOT_PUBLISHABLE", "MANUAL_GRADING_REQUIRED",
                "DISTRIBUTION_DEVIATION"));
        BASE_EXAMS.put("TEST-THPTQG-03", Expected.valid());
    }

    /** Spec 4.2, column "Mã thực tế"; TEST-ERR-07 is checked per selection below. */
    private static final Map<String, Expected> ERROR_FIXTURES = new LinkedHashMap<>();
    static {
        ERROR_FIXTURES.put("TEST-ERR-01", Expected.invalid("MATRIX_INCOMPLETE"));
        ERROR_FIXTURES.put("TEST-ERR-02", Expected.invalid("PART_POINTS_MISMATCH", "POINTS_TOTAL_MISMATCH",
                "MANUAL_GRADING_REQUIRED", "DISTRIBUTION_DEVIATION", "EXAM_QUOTA_DEVIATION"));
        // The Math context set has no slots, so a rare label leaves the whole set unsolvable.
        ERROR_FIXTURES.put("TEST-ERR-03", Expected.invalid("SET_SHORTAGE"));
        ERROR_FIXTURES.put("TEST-ERR-04", Expected.invalid("SECTION_TOTAL_MISMATCH", "PART_POINTS_MISMATCH",
                "PATH_TOTAL_MISMATCH", "POINTS_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        ERROR_FIXTURES.put("TEST-ERR-05", Expected.invalid("ANSWER_QUOTA_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        // The retyped block answers SINGLE_CHOICE, so the SHORT_ANSWER 15 quota is an orphan.
        ERROR_FIXTURES.put("TEST-ERR-06", Expected.invalid("ANSWER_TYPE_SHORTAGE"));
        ERROR_FIXTURES.put("TEST-ERR-07", Expected.invalid("CELL_SHORTAGE"));
        // The valid control: one Physics group renamed, nothing to report.
        ERROR_FIXTURES.put("TEST-ERR-08", Expected.valid());
        ERROR_FIXTURES.put("TEST-ERR-09", Expected.invalid("ANSWER_QUOTA_TOTAL_MISMATCH", "CELL_SHORTAGE"));
        ERROR_FIXTURES.put("TEST-ERR-10", Expected.invalid("SUBJECT_CHOICE_COUNTS_INVALID", "PART_EMPTY",
                "PART_POINTS_MISMATCH", "PART_TOTAL_MISMATCH", "PATH_TOTAL_MISMATCH", "POINTS_TOTAL_MISMATCH"));
        ERROR_FIXTURES.put("TEST-ERR-11", Expected.invalid("CELL_SHORTAGE"));
        ERROR_FIXTURES.put("TEST-ERR-12", Expected.invalid("POINTS_TOTAL_MISMATCH"));
        ERROR_FIXTURES.put("TEST-ERR-13", Expected.invalid("YEARS_UNCONFIRMED", "EXAM_QUOTA_DEVIATION"));
    }

    @Test
    void everyTestExamChecksAsDocumented() throws Exception {
        assertThat(jdbc.queryForList("SELECT code FROM exam.exam WHERE code LIKE 'TEST-%' AND code NOT LIKE 'TEST-ERR-%'",
                String.class)).as("every seeded base exam is documented").containsExactlyInAnyOrderElementsOf(BASE_EXAMS.keySet());
        SoftAssertions softly = new SoftAssertions();
        for (var entry : BASE_EXAMS.entrySet()) {
            assertExpected(softly, entry.getKey(), check(entry.getKey()), entry.getValue());
        }
        softly.assertAll();
    }

    @Test
    void errorFixturesReportExactCodes() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (var entry : ERROR_FIXTURES.entrySet()) {
            JsonNode result = check(entry.getKey());
            assertExpected(softly, entry.getKey(), result, entry.getValue());
        }
        // TEST-ERR-07: Geography is one cell short when it takes 17 questions, so exactly the six
        // triples that contain Geography are invalid, and each reports only that shortage.
        JsonNode narrow = check("TEST-ERR-07");
        Set<String> shortage = Set.of("CELL_SHORTAGE");
        int selections = 0;
        int failures = 0;
        for (JsonNode path : narrow.path("paths")) {
            for (JsonNode selection : path.path("selections")) {
                selections++;
                boolean geography17 = false;
                for (int i = 0; i < selection.path("subjectCodes").size(); i++) {
                    geography17 |= "DIA".equals(selection.path("subjectCodes").get(i).asText())
                            && selection.path("counts").get(i).asInt() == 17;
                }
                if (geography17) failures++;
                softly.assertThat(selection.path("result").asText()).as(selection.toString())
                        .isEqualTo(geography17 ? "INVALID" : "VALID");
                softly.assertThat(selection.path("issues").findValuesAsText("code")).as(selection.toString())
                        .containsExactlyInAnyOrderElementsOf(geography17 ? shortage : Set.of());
            }
        }
        softly.assertThat(selections).as("TEST-ERR-07 selections").isEqualTo(10);
        softly.assertThat(failures).as("TEST-ERR-07 selections with Geography x 17").isEqualTo(6);
        // The shortage is on the Geography single-choice group (a group-level shortage carries no
        // required/available figures), for a 17-question pick.
        List<String> geographyGroups = jdbc.queryForList("""
                SELECT block->>'id' FROM exam.exam e JOIN exam.exam_structure s ON s.exam_id = e.id
                CROSS JOIN LATERAL jsonb_array_elements(s.parts->'parts') part
                CROSS JOIN LATERAL jsonb_array_elements(part->'options') opt
                CROSS JOIN LATERAL jsonb_array_elements(opt->'sections') sec
                CROSS JOIN LATERAL jsonb_array_elements(sec->'blocks') block
                WHERE e.code = 'TEST-ERR-07' AND sec->>'subjectCode' = 'DIA'
                  AND block->>'questionType' = 'SINGLE_CHOICE'
                """, String.class);
        softly.assertThat(geographyGroups).as("TEST-ERR-07 has one Geography single-choice group").hasSize(1);
        List<JsonNode> shortages = new ArrayList<>();
        narrow.path("groups").findValues("issues").forEach(issues -> issues.forEach(issue -> {
            if ("CELL_SHORTAGE".equals(issue.path("code").asText())) shortages.add(issue);
        }));
        for (JsonNode path : narrow.path("paths")) {
            for (JsonNode selection : path.path("selections")) {
                selection.path("issues").forEach(issue -> {
                    if ("CELL_SHORTAGE".equals(issue.path("code").asText())) shortages.add(issue);
                });
            }
        }
        softly.assertThat(shortages).as("TEST-ERR-07 reports the gap").isNotEmpty();
        for (JsonNode gap : shortages) {
            softly.assertThat(gap.path("blockId").asText()).as("TEST-ERR-07 names the Geography single-choice group: %s", gap)
                    .isIn(geographyGroups);
            softly.assertThat(gap.path("required").isMissingNode() || gap.path("required").isNull())
                    .as("TEST-ERR-07 group gap has no required figure: %s", gap).isTrue();
            softly.assertThat(gap.path("available").isMissingNode() || gap.path("available").isNull())
                    .as("TEST-ERR-07 group gap has no available figure: %s", gap).isTrue();
        }
        for (JsonNode path : narrow.path("paths")) {
            for (JsonNode selection : path.path("selections")) {
                for (JsonNode issue : selection.path("issues")) {
                    softly.assertThat(issue.path("subjectCode").asText() + " x" + issue.path("subjectCount").asInt())
                            .as("TEST-ERR-07 %s", issue).isEqualTo("DIA x17");
                }
            }
        }
        // TEST-ERR-11: Grade 12 x VDC asks for 12 where the sample bank holds 4.
        JsonNode cell = check("TEST-ERR-11").path("groups").findValues("issues").stream()
                .flatMap(issues -> StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> "CELL_SHORTAGE".equals(issue.path("code").asText())).findFirst().orElse(null);
        softly.assertThat(cell).as("TEST-ERR-11 cell shortage").isNotNull();
        if (cell != null) {
            softly.assertThat(cell.path("gradeCode").asText() + "x" + cell.path("difficultyCode").asText()
                    + " " + cell.path("required").asInt() + "/" + cell.path("available").asInt())
                    .as("TEST-ERR-11 %s", cell).isEqualTo("G12xVDC 12/4");
        }
        softly.assertAll();
    }

    @Test
    void validTestExamsGeneratePreview() throws Exception {
        List<String> valid = new ArrayList<>();
        BASE_EXAMS.forEach((code, expected) -> {
            if ("VALID".equals(expected.status())) valid.add(code);
        });
        valid.add("TEST-ERR-08");
        SoftAssertions softly = new SoftAssertions();
        for (String code : valid) {
            Map<String, Object> exam = jdbc.queryForMap("""
                    SELECT e.id, e.question_count, e.max_score, e.reference_max,
                           jsonb_array_length(s.parts -> 'parts') AS part_count
                    FROM exam.exam e JOIN exam.exam_structure s ON s.exam_id = e.id WHERE e.code = ?
                    """, code);
            UUID id = (UUID) exam.get("id");
            BigDecimal points = (BigDecimal) (exam.get("max_score") != null ? exam.get("max_score") : exam.get("reference_max"));
            // One preview per valid path; a subject-choice path also names the subjects of one valid pick.
            Map<String, List<String>> paths = new LinkedHashMap<>();
            for (JsonNode path : check(code).path("paths")) {
                if (!"VALID".equals(path.path("result").asText())) continue;
                List<String> subjects = new ArrayList<>();
                for (JsonNode selection : path.path("selections")) {
                    if ("VALID".equals(selection.path("result").asText())) {
                        selection.path("subjectCodes").forEach(subject -> subjects.add(subject.asText()));
                        break;
                    }
                }
                paths.put(path.path("pathId").asText("default"), subjects);
            }
            if (paths.isEmpty()) paths.put("default", List.of());
            for (var path : paths.entrySet()) {
                String pathId = path.getKey();
                var target = mapper.createObjectNode().put("kind", "PATH").put("pathId", pathId);
                if (!path.getValue().isEmpty()) {
                    var subjects = target.putArray("subjectCodes");
                    path.getValue().forEach(subjects::add);
                }
                var request = mapper.createObjectNode().put("seed", 7);
                request.set("target", target);
                JsonNode preview = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                                .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                                .content(request.toString()))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                String label = code + " path " + pathId;
                softly.assertThat(preview.path("status").asText()).as("%s: %s", label, preview.path("issues"))
                        .isEqualTo("GENERATED");
                JsonNode paper = preview.path("paper");
                softly.assertThat(paper.path("totalQuestions").asInt(-1)).as("%s questions", label)
                        .isEqualTo(((Number) exam.get("question_count")).intValue());
                softly.assertThat(paper.path("parts").size()).as("%s parts", label)
                        .isEqualTo(((Number) exam.get("part_count")).intValue());
                softly.assertThat(paper.path("totalPoints").isNumber() ? paper.path("totalPoints").decimalValue() : null)
                        .as("%s points", label).isEqualByComparingTo(points);
            }
        }
        softly.assertAll();
    }

    /**
     * The documented code set, exactly: every documented code is present, nothing else is, and the reference
     * warnings (quota, distribution) carry WARNING severity. Only {@code optionalWarnings} may be absent.
     */
    private void assertExpected(SoftAssertions softly, String code, JsonNode result, Expected expected) {
        Set<String> actual = codes(result);
        softly.assertThat(result.path("status").asText()).as("%s status, codes %s", code, actual)
                .isEqualTo(expected.status());
        Set<String> required = new java.util.TreeSet<>(expected.codes());
        Set<String> allowed = new java.util.TreeSet<>(expected.codes());
        allowed.addAll(expected.optionalWarnings());
        softly.assertThat(actual).as("%s documented codes missing from %s", code, actual).containsAll(required);
        softly.assertThat(actual).as("%s codes beyond the documented ones", code).isSubsetOf(allowed);
        List<String> offending = severities(result).stream()
                .filter(issue -> REFERENCE_WARNINGS.contains(issue.substring(0, issue.indexOf('/')))
                        && !issue.endsWith("/WARNING"))
                .toList();
        softly.assertThat(offending).as("%s: reference deviations are warnings only", code).isEmpty();
    }

    private static Set<String> codes(JsonNode result) {
        return result.path("groups").findValuesAsText("code").stream().collect(Collectors.toSet());
    }

    private static Set<String> severities(JsonNode result) {
        return result.path("groups").findValues("issues").stream()
                .flatMap(issues -> StreamSupport.stream(issues.spliterator(), false))
                .map(issue -> issue.path("code").asText() + "/" + issue.path("severity").asText())
                .collect(Collectors.toSet());
    }

    private JsonNode check(String code) throws Exception {
        UUID id = jdbc.queryForObject("SELECT id FROM exam.exam WHERE code_normalized = ?", UUID.class,
                code.toLowerCase(Locale.ROOT));
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
