package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamTemplateTotalsIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void everyStoredTemplateMatchesItsFixture() throws Exception {
        JsonNode expected;
        try (var input = getClass().getResourceAsStream("/templates/expected-totals.json")) {
            assertThat(input).isNotNull();
            expected = mapper.readTree(input);
        }
        Set<String> expectedCodes = new HashSet<>();
        expected.fieldNames().forEachRemaining(expectedCodes::add);
        var rows = jdbc.queryForList("""
                select code, kind_code, scope, part_code, subject_code, build_mode, is_default,
                       timing, duration_minutes, structure::text as document from exam.exam_template
                """);
        assertThat(rows).extracting(row -> row.get("code")).containsExactlyInAnyOrderElementsOf(expectedCodes);
        for (var row : rows) {
            String code = (String) row.get("code");
            JsonNode fixture = expected.path(code);
            assertThat(row.get("kind_code")).as(code).isEqualTo(fixture.path("examKind").asText());
            assertThat(row.get("scope")).as(code).isEqualTo(fixture.path("scope").asText());
            assertThat(row.get("part_code")).as(code).isEqualTo(fixture.path("partCode").asText(null));
            assertThat(row.get("subject_code")).as(code).isEqualTo(fixture.path("subjectCode").asText(null));
            assertThat(row.get("build_mode")).as(code).isEqualTo(fixture.path("buildMode").asText());
            assertThat(row.get("is_default")).as(code).isEqualTo(fixture.path("isDefault").asBoolean());
            JsonNode parts = mapper.readTree((String) row.get("document")).path("parts");
            int questions = 0;
            int minutes = 0;
            boolean hasMinutes = false;
            boolean straightforward = true;
            BigDecimal points = BigDecimal.ZERO;
            for (JsonNode part : parts) {
                straightforward &= part.path("options").isEmpty() && part.path("subjectPool").isEmpty();
                questions += part.path("questionCountRequired").asInt();
                if (part.hasNonNull("maxPoints")) points = points.add(part.path("maxPoints").decimalValue());
                if (part.hasNonNull("durationMinutes")) {
                    hasMinutes = true;
                    minutes += part.path("durationMinutes").asInt();
                }
            }
            if (fixture.hasNonNull("questions")) {
                assertThat(questions).as("%s questions", code).isEqualTo(fixture.path("questions").asInt());
            }
            if (fixture.hasNonNull("maxPoints")) {
                assertThat(points).as("%s max points", code).isEqualByComparingTo(fixture.path("maxPoints").decimalValue());
            }
            if (straightforward && fixture.hasNonNull("questions") && fixture.hasNonNull("maxPoints")) {
                Totals actual = totals(parts);
                assertThat(actual.questions()).as("%s non-omitted block questions", code)
                        .isEqualTo(fixture.path("questions").asInt());
                assertThat(actual.points()).as("%s non-omitted block points", code)
                        .isEqualByComparingTo(fixture.path("maxPoints").decimalValue());
            }
            Integer duration = "SHARED".equals(row.get("timing")) ? (Integer) row.get("duration_minutes")
                    : hasMinutes ? minutes : null;
            Integer expectedDuration = fixture.hasNonNull("durationMinutes") ? fixture.path("durationMinutes").asInt() : null;
            assertThat(duration).as("%s duration", code).isEqualTo(expectedDuration);
        }
    }

    @Test
    void qdaMathPartMatchesRequirementTableIndependently() throws Exception {
        assertFull("QDA_PART_MATH", 50, "50", 80);
    }

    @Test
    void fullExamDeclaredTotalsMatchRequirementTable() throws Exception {
        assertFull("BCA_FULL", 81, "100", 180);
        assertFull("QDA_FULL", 150, "150", 195);
        assertFull("HSA_FULL", 150, "150", 195);
        assertFull("HSA_PART_MATH", 50, "50", 75);
        assertFull("HSA_PART_LITERATURE", 50, "50", 60);
        assertFull("HSA_PART_SCIENCE", 50, "50", 60);
    }

    private void assertFull(String code, int questions, String points, int minutes) throws Exception {
        var row = jdbc.queryForMap("select timing, duration_minutes, structure::text as document from exam.exam_template where code = ?", code);
        JsonNode parts = mapper.readTree((String) row.get("document")).path("parts");
        assertThat(parts).as(code).isNotEmpty();
        int count = 0;
        int partMinutes = 0;
        BigDecimal score = BigDecimal.ZERO;
        for (JsonNode part : parts) {
            count += part.path("questionCountRequired").asInt();
            score = score.add(part.path("maxPoints").decimalValue());
            partMinutes += part.path("durationMinutes").asInt();
        }
        assertThat(count).as("%s required questions", code).isEqualTo(questions);
        assertThat(score).as("%s points", code).isEqualByComparingTo(points);
        int duration = "SHARED".equals(row.get("timing")) ? ((Number) row.get("duration_minutes")).intValue() : partMinutes;
        assertThat(duration).as("%s minutes", code).isEqualTo(minutes);
    }

    @Test
    void sptV1TotalsExcludeOmittedEssayAndLeaveDurationOpen() throws Exception {
        // Requirement table §2.5, independent of the template builder.
        Map<String, int[]> expected = Map.ofEntries(
                Map.entry("SPT_MATH_2025", new int[]{22, 7}),
                Map.entry("SPT_LY_2025", new int[]{19, 7}),
                Map.entry("SPT_HOA_2025", new int[]{26, 8}),
                Map.entry("SPT_VAN_2025", new int[]{10, 3}),
                Map.entry("SPT_ANH_2025", new int[]{32, 8}),
                Map.entry("SPT_SINH_2025", new int[]{19, 7}),
                Map.entry("SPT_SU_2025", new int[]{22, 7}),
                Map.entry("SPT_DIA_2025", new int[]{19, 7}),
                Map.entry("SPT_ECON_LAW_2025", new int[]{19, 7}),
                Map.entry("SPT_TECH_INDUSTRY_2025", new int[]{22, 7}),
                Map.entry("SPT_TECH_AGRICULTURE_2025", new int[]{22, 7}));
        for (var entry : expected.entrySet()) {
            var row = jdbc.queryForMap("select duration_minutes, structure::text as document from exam.exam_template where code = ?", entry.getKey());
            assertThat(row.get("duration_minutes")).as("%s duration", entry.getKey()).isNull();
            JsonNode parts = mapper.readTree((String) row.get("document")).path("parts");
            for (JsonNode part : parts) {
                assertThat(part.path("durationMinutes").isMissingNode() || part.path("durationMinutes").isNull())
                        .as("%s part duration", entry.getKey()).isTrue();
            }
            Totals actual = totals(parts);
            assertThat(actual.questions()).as("%s questions", entry.getKey()).isEqualTo(entry.getValue()[0]);
            assertThat(actual.points()).as("%s points", entry.getKey())
                    .isEqualByComparingTo(BigDecimal.valueOf(entry.getValue()[1]));
        }
    }

    @Test
    void sp2e2026SubjectTotalsMatchRequirementTable() throws Exception {
        Map<String, int[]> expected = Map.of(
                "SP2E_MATH_2026", new int[]{40, 90, 100},
                "SP2E_LY_2026", new int[]{28, 60, 100},
                "SP2E_HOA_2026", new int[]{28, 60, 100},
                "SP2E_SINH_2026", new int[]{28, 60, 100},
                "SP2E_SU_2026", new int[]{32, 60, 125},
                "SP2E_DIA_2026", new int[]{28, 60, 100},
                "SP2E_ANH_2026", new int[]{50, 60, 125},
                "SP2E_ECON_LAW_2026", new int[]{32, 60, 125});
        for (var entry : expected.entrySet()) {
            var row = jdbc.queryForMap("select duration_minutes, structure::text as document from exam.exam_template where code = ?", entry.getKey());
            assertThat(row.get("duration_minutes")).as("%s duration", entry.getKey()).isEqualTo(entry.getValue()[1]);
            Totals actual = totals(mapper.readTree((String) row.get("document")).path("parts"));
            assertThat(actual.questions()).as("%s questions", entry.getKey()).isEqualTo(entry.getValue()[0]);
            assertThat(actual.points()).as("%s raw points", entry.getKey())
                    .isEqualByComparingTo(BigDecimal.valueOf(entry.getValue()[2], 1));
        }
    }

    @Test
    void informaticsTotalsApplyToEachBranch() throws Exception {
        assertInformaticsBranches("SPT_INFORMATICS_2025", 22, "7", 1);
        assertInformaticsBranches("THPTQG_INFORMATICS_2025", 28, "10", 2);
    }

    private void assertInformaticsBranches(String code, int questions, String points, int branchTrueFalse)
            throws Exception {
        String json = jdbc.queryForObject(
                "select structure::text from exam.exam_template where code = ?", String.class, code);
        JsonNode parts = mapper.readTree(json).path("parts");
        var common = mapper.createArrayNode();
        List<JsonNode> branches = new ArrayList<>();
        for (JsonNode part : parts) {
            common.add(part.path("sections"));
            for (JsonNode option : part.path("options")) {
                if ("BRANCH".equals(option.path("kind").asText())) branches.add(option);
            }
        }
        assertThat(branches).as("%s branches", code).hasSize(2);
        for (JsonNode branch : branches) {
            var selected = mapper.createArrayNode().add(common).add(branch);
            Totals actual = totals(selected);
            assertThat(actual.questions()).as("%s %s questions", code, branch.path("code").asText())
                    .isEqualTo(questions);
            assertThat(actual.points()).as("%s %s points", code, branch.path("code").asText())
                    .isEqualByComparingTo(points);
            List<JsonNode> blocks = new ArrayList<>();
            collectBlocks(branch, blocks);
            int trueFalse = blocks.stream().filter(b -> "TRUE_FALSE".equals(b.path("questionType").asText()))
                    .mapToInt(b -> b.path("questionCount").asInt()).sum();
            assertThat(trueFalse).as("%s true/false questions in %s", code, branch.path("code").asText())
                    .isEqualTo(branchTrueFalse);
        }
    }

    @Test
    void defaultSp2eLiteratureCreatesAnExamWithThirtyQuestionsAndSixPoints() throws Exception {
        var rows = jdbc.queryForList("""
                select code from exam.exam_template where kind_code = 'SP2E' and scope = 'SUBJECT'
                and subject_code = 'VAN' and build_mode = 'STANDARD' and is_default
                """, String.class);
        assertThat(rows).hasSize(1);
        String body = "{\"kind\":\"SP2E\",\"scope\":\"SUBJECT\",\"subjectCode\":\"VAN\","
                + "\"name\":\"Literature\",\"durationMinutes\":60,\"code\":\"T-"
                + UUID.randomUUID().toString().substring(0, 8) + "\"}";
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(exam.path("templateCode").asText()).isEqualTo(rows.getFirst());
        Totals totals = totals(exam.path("parts"));
        assertThat(totals.questions()).isEqualTo(30);
        assertThat(totals.points()).isEqualByComparingTo("6");
    }

    private record Totals(int questions, BigDecimal points) { }

    private Totals totals(JsonNode parts) {
        List<JsonNode> blocks = new ArrayList<>();
        collectBlocks(parts, blocks);
        int questions = 0;
        BigDecimal points = BigDecimal.ZERO;
        for (JsonNode block : blocks) {
            if (block.path("omitted").asBoolean()) continue;
            int count = block.hasNonNull("questionCount") ? block.path("questionCount").asInt()
                    : block.path("setCount").asInt() * block.path("setSize").asInt();
            assertThat(block.hasNonNull("requiredPointsPerQuestion")).as("points for %s", block).isTrue();
            questions += count;
            points = points.add(block.path("requiredPointsPerQuestion").decimalValue().multiply(BigDecimal.valueOf(count)));
        }
        return new Totals(questions, points);
    }

    private void collectBlocks(JsonNode node, List<JsonNode> blocks) {
        if (node.isObject()) {
            if (node.has("questionType")) blocks.add(node);
            else node.elements().forEachRemaining(child -> collectBlocks(child, blocks));
        } else if (node.isArray()) {
            node.forEach(child -> collectBlocks(child, blocks));
        }
    }
}
