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
import java.util.LinkedHashMap;
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
                where build_mode = 'STANDARD'
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
            for (JsonNode part : parts) {
                var single = mapper.createArrayNode().add(part);
                for (Totals path : pathTotals(single, (String) row.get("kind_code"))) {
                    if (path.questions() == 0) continue;
                    if (part.hasNonNull("questionCount")) {
                        assertThat(path.questions()).as("%s part declared count", code)
                                .isEqualTo(part.path("questionCount").asInt());
                    }
                    if (part.hasNonNull("questionCountRequired")) {
                        assertThat(path.questions()).as("%s part required count", code)
                                .isEqualTo(part.path("questionCountRequired").asInt());
                    }
                    if (part.hasNonNull("maxPoints") && path.points() != null) {
                        assertThat(path.points()).as("%s part points", code)
                                .isEqualByComparingTo(part.path("maxPoints").decimalValue());
                    }
                }
            }
            Integer minutes = null;
            for (JsonNode part : parts) {
                if (part.hasNonNull("durationMinutes")) {
                    minutes = (minutes == null ? 0 : minutes) + part.path("durationMinutes").asInt();
                }
            }
            if (fixture.hasNonNull("questions") || fixture.hasNonNull("maxPoints")) {
                List<Totals> paths = pathTotals(parts, (String) row.get("kind_code"));
                assertThat(paths).as("%s paths", code).isNotEmpty();
                for (Totals path : paths) {
                    if (fixture.hasNonNull("questions")) {
                        assertThat(path.questions()).as("%s path questions", code)
                                .isEqualTo(fixture.path("questions").asInt());
                    }
                    if (fixture.hasNonNull("maxPoints")) {
                        assertThat(path.points()).as("%s path points", code).isNotNull();
                        assertThat(path.points()).as("%s path points", code)
                                .isEqualByComparingTo(fixture.path("maxPoints").decimalValue());
                    }
                }
            }
            Integer duration = "SHARED".equals(row.get("timing")) ? (Integer) row.get("duration_minutes") : minutes;
            Integer expectedDuration = fixture.hasNonNull("durationMinutes") ? fixture.path("durationMinutes").asInt() : null;
            assertThat(duration).as("%s duration", code).isEqualTo(expectedDuration);
        }
    }
    @Test
    void hsaScienceChoicesUseFixedSeventeenQuestionMatrices() throws Exception {
        Map<String, List<Integer>> grades = Map.of(
                "LY", List.of(5, 6, 6), "HOA", List.of(2, 8, 7), "SINH", List.of(3, 5, 9),
                "SU", List.of(2, 5, 10), "DIA", List.of(2, 4, 11));
        Map<String, List<Integer>> difficulties = Map.of(
                "LY", List.of(3, 1, 13), "HOA", List.of(4, 5, 8), "SINH", List.of(7, 7, 3),
                "SU", List.of(7, 8, 2), "DIA", List.of(5, 9, 3));
        Set<String> templates = new HashSet<>();
        for (var row : jdbc.queryForList("select code, structure::text as document from exam.exam_template where kind_code = 'HSA' and build_mode = 'STANDARD'")) {
            String code = (String) row.get("code");
            for (JsonNode part : mapper.readTree((String) row.get("document")).path("parts")) {
                for (JsonNode option : part.path("options")) {
                    if (!"SUBJECT_CHOICE".equals(option.path("kind").asText())
                            || !List.of(17, 17, 16).equals(mapper.convertValue(option.path("countsByPick"),
                                    new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>() { }))) continue;
                    templates.add(code);
                    Set<String> subjects = new HashSet<>();
                    for (JsonNode section : option.path("sections")) {
                        String subject = section.path("subjectCode").asText();
                        assertThat(subjects.add(subject)).as("%s duplicate subject", code).isTrue();
                        assertThat(grades).containsKey(subject);
                        assertThat(section.path("questionCount").asInt()).as("%s %s", code, subject).isEqualTo(17);
                        List<Integer> counts = new ArrayList<>();
                        for (JsonNode block : section.path("blocks")) counts.add(block.path("questionCount").asInt(-1));
                        assertThat(counts).as("%s %s group counts", code, subject).containsExactlyElementsOf(
                                Set.of("SU", "DIA").contains(subject) ? List.of(14, 3) : List.of(13, 3, 1));
                        JsonNode matrix = section.path("matrix");
                        assertThat(matrix.path("unit").asText()).isEqualTo("COUNT");
                        assertMatrix(matrix.path("byGrade"), List.of("G10", "G11", "G12"), grades.get(subject));
                        assertMatrix(matrix.path("byDifficulty"), List.of("NB", "TH", "VD"), difficulties.get(subject));
                    }
                    assertThat(subjects).containsExactlyInAnyOrderElementsOf(grades.keySet());
                }
            }
        }
        assertThat(templates).containsExactlyInAnyOrder("HSA_FULL", "HSA_PART_SCIENCE");
    }

    private void assertMatrix(JsonNode values, List<String> codes, List<Integer> counts) {
        assertThat(values.size()).isEqualTo(codes.size());
        for (int i = 0; i < codes.size(); i++) {
            assertThat(values.get(i).path("code").asText()).isEqualTo(codes.get(i));
            assertThat(values.get(i).path("value").asInt()).isEqualTo(counts.get(i));
        }
    }

    @Test
    void preservedAndRequirementScalesAndClocksAreIndependentOfFixtures() {
        for (var row : jdbc.queryForList("""
                select code, kind_code, max_score, duration_minutes
                from exam.exam_template where build_mode = 'STANDARD'
                """)) {
            String code = (String) row.get("code");
            String kind = (String) row.get("kind_code");
            if ("THPTQG".equals(kind)) {
                int duration = code.equals("THPTQG_MATH_2025") ? 90
                        : code.equals("THPTQG_LITERATURE_2025") ? 120 : 50;
                assertThat(row.get("duration_minutes")).as("%s clock", code).isEqualTo(duration);
                assertThat((BigDecimal) row.get("max_score")).as("%s scale", code).isEqualByComparingTo("10");
            } else if ("VACT_FULL".equals(code)) {
                assertThat(row.get("duration_minutes")).isEqualTo(150);
                assertThat((BigDecimal) row.get("max_score")).isEqualByComparingTo("1200");
            } else if ("SP2E".equals(kind)) {
                String scale = "SP2E_VAN_OLD".equals(code) ? "6"
                        : "SP2E_VAN_2026".equals(code) ? "5" : "10";
                assertThat((BigDecimal) row.get("max_score")).as("%s scale", code).isEqualByComparingTo(scale);
            }
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
        var row = jdbc.queryForMap("select timing, duration_minutes, max_score, structure::text as document from exam.exam_template where code = ?", code);
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
        assertThat((BigDecimal) row.get("max_score")).as("%s stored scale", code).isEqualByComparingTo(points);
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
    void pathTotalsEnumerateSubjectPicksAndBranchesWithoutOmittedBlocks() throws Exception {
        JsonNode parts = mapper.readTree("""
                [
                  {"participation":{"mode":"CHOICE","choose":1},"options":[
                    {"kind":"SUBJECT_CHOICE","chooseSubjects":2,"countsByPick":[2,1],"sections":[
                      {"blocks":[{"questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":1}]},
                      {"blocks":[{"questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":1}]},
                      {"blocks":[{"questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":1}]}
                    ]}
                  ]},
                  {"participation":{"mode":"CHOICE","choose":1},"options":[
                    {"kind":"BRANCH","blocks":[{"questionType":"TRUE_FALSE","questionCount":1,"requiredPointsPerQuestion":1}]},
                    {"kind":"BRANCH","blocks":[{"questionType":"TRUE_FALSE","questionCount":1,"requiredPointsPerQuestion":1}]}
                  ],"sections":[{"blocks":[{"questionType":"ESSAY","questionCount":1,"omitted":true}]}]}
                ]
                """);
        List<Totals> paths = pathTotals(parts, "HSA");
        assertThat(paths).hasSize(12);
        assertThat(paths).allSatisfy(path -> {
            assertThat(path.questions()).isEqualTo(4);
            assertThat(path.points()).isEqualByComparingTo("4");
        });
    }

    @Test
    void subjectChoiceTrimsOneSingleQuestion() throws Exception {
        JsonNode option = mapper.readTree("""
                {"kind":"SUBJECT_CHOICE","chooseSubjects":2,"countsByPick":[2,1],"sections":[
                  {"blocks":[{"questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":1}]},
                  {"blocks":[{"questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":1}]},
                  {"blocks":[{"questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":1}]}
                ]}
                """);
        assertThat(optionTotals(option)).hasSize(6).allSatisfy(path -> {
            assertThat(path.questions()).isEqualTo(3);
            assertThat(path.points()).isEqualByComparingTo("3");
        });
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

    private List<Totals> pathTotals(JsonNode parts, String kind) {
        List<Totals> paths = List.of(new Totals(0, BigDecimal.ZERO));
        for (JsonNode part : parts) {
            Totals common = totals(part.path("sections"));
            List<Totals> alternatives;
            if (part.hasNonNull("codeSource")) {
                assertThat(part.path("options")).as("code source cannot also declare options").isEmpty();
                alternatives = codeSourceTotals(part, kind);
            } else if (!part.path("options").isEmpty()) {
                List<List<Totals>> options = new ArrayList<>();
                for (JsonNode option : part.path("options")) options.add(optionTotals(option));
                int choose = "CHOICE".equals(part.path("participation").path("mode").asText())
                        ? part.path("participation").path("choose").asInt() : options.size();
                alternatives = new ArrayList<>();
                chooseOptions(options, 0, choose, new Totals(0, BigDecimal.ZERO), alternatives);
            } else {
                alternatives = List.of(new Totals(0, BigDecimal.ZERO));
            }
            assertThat(alternatives).as("%s part alternatives", kind).isNotEmpty();
            List<Totals> next = new ArrayList<>();
            for (Totals prior : paths) for (Totals alternative : alternatives) {
                next.add(add(add(prior, common), alternative));
            }
            paths = next;
        }
        return paths;
    }

    private List<Totals> codeSourceTotals(JsonNode part, String kind) {
        String branch = part.path("codeSource").path("branch").asText(null);
        var rows = jdbc.queryForList("""
                select v.id, s.code as subject_code
                from catalog.combination_version v
                join catalog.catalog_item k on k.id = v.exam_item_id
                join catalog.combination_subject cs on cs.combination_version_id = v.id
                join catalog.catalog_item s on s.id = cs.subject_item_id
                where k.code = ? and v.is_current and (cast(? as text) is null or v.branch = ?)
                order by v.id, cs.position
                """, kind, branch, branch);
        Map<Object, Totals> combinations = new LinkedHashMap<>();
        for (var row : rows) {
            String subjectCode = (String) row.get("subject_code");
            JsonNode subject = null;
            for (JsonNode candidate : part.path("subjectPool")) {
                if (subjectCode.equals(candidate.path("subjectCode").asText())) subject = candidate;
            }
            assertThat(subject).as("%s %s subject pool", kind, subjectCode).isNotNull();
            combinations.merge(row.get("id"), totals(subject), this::add);
        }
        assertThat(combinations).as("%s catalog combinations, branch %s", kind, branch).isNotEmpty();
        return new ArrayList<>(combinations.values());
    }

    private List<Totals> optionTotals(JsonNode option) {
        if (!"SUBJECT_CHOICE".equals(option.path("kind").asText())) return List.of(totals(option));
        int choose = option.path("chooseSubjects").asInt();
        JsonNode counts = option.path("countsByPick");
        assertThat(counts.size()).as("subject-choice counts").isEqualTo(choose);
        List<Totals> result = new ArrayList<>();
        chooseSubjects(option.path("sections"), counts, new boolean[option.path("sections").size()],
                0, new Totals(0, BigDecimal.ZERO), result);
        return result;
    }

    private void chooseSubjects(JsonNode sections, JsonNode counts, boolean[] used, int position,
            Totals current, List<Totals> result) {
        if (position == counts.size()) {
            result.add(current);
            return;
        }
        for (int i = 0; i < sections.size(); i++) {
            if (used[i]) continue;
            JsonNode section = sections.get(i);
            int selected = counts.get(position).asInt();
            List<JsonNode> blocks = new ArrayList<>();
            collectBlocks(section, blocks);
            Totals configured = totals(section);
            assertThat(selected).isBetween(0, configured.questions());
            BigDecimal points = configured.points();
            if (points != null && selected != configured.questions()) {
                Set<BigDecimal> rates = new HashSet<>();
                for (JsonNode block : blocks) {
                    if (!block.path("omitted").asBoolean() && block.hasNonNull("requiredPointsPerQuestion")) {
                        rates.add(block.path("requiredPointsPerQuestion").decimalValue().stripTrailingZeros());
                    }
                }
                assertThat(rates).as("subject-choice reduction requires a uniform point value").hasSize(1);
                points = rates.iterator().next().multiply(BigDecimal.valueOf(selected));
            }
            used[i] = true;
            chooseSubjects(sections, counts, used, position + 1, add(current, new Totals(selected, points)), result);
            used[i] = false;
        }
    }

    private void chooseOptions(List<List<Totals>> options, int start, int remaining,
            Totals current, List<Totals> result) {
        if (remaining == 0) {
            result.add(current);
            return;
        }
        for (int i = start; i <= options.size() - remaining; i++) {
            for (Totals choice : options.get(i)) chooseOptions(options, i + 1, remaining - 1,
                    add(current, choice), result);
        }
    }

    private Totals add(Totals left, Totals right) {
        return new Totals(left.questions() + right.questions(),
                left.points() == null || right.points() == null ? null : left.points().add(right.points()));
    }

    private Totals totals(JsonNode parts) {
        List<JsonNode> blocks = new ArrayList<>();
        collectBlocks(parts, blocks);
        int questions = 0;
        BigDecimal points = BigDecimal.ZERO;
        for (JsonNode block : blocks) {
            if (block.path("omitted").asBoolean()) continue;
            assertThat(block.hasNonNull("questionCount")
                    || (block.hasNonNull("setCount") && block.hasNonNull("setSize")))
                    .as("count for %s", block).isTrue();
            if (block.hasNonNull("questionCount") && block.hasNonNull("setCount") && block.hasNonNull("setSize")) {
                assertThat(block.path("questionCount").asInt()).as("set cardinality for %s", block)
                        .isEqualTo(block.path("setCount").asInt() * block.path("setSize").asInt());
            }
            int count = block.hasNonNull("questionCount") ? block.path("questionCount").asInt()
                    : block.path("setCount").asInt() * block.path("setSize").asInt();
            questions += count;
            if (!block.hasNonNull("requiredPointsPerQuestion")) {
                points = null;
            } else if (points != null) {
                points = points.add(block.path("requiredPointsPerQuestion").decimalValue().multiply(BigDecimal.valueOf(count)));
            }
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
