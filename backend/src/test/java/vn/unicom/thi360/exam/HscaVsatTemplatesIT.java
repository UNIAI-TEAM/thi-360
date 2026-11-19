package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-18 rows 1, 2, 5, 6, 7 and 8 (addendum specification 4 and 5) through {@code GET /exam-templates}: the
 * stored H-SCA and V-SAT templates carry the specification's totals, rows, reference distributions and flags.
 */
@Transactional
class HscaVsatTemplatesIT extends AbstractIntegrationTest {
    private static final List<String> HSCA_SCIENCES = List.of("TOAN", "LY", "HOA", "SINH");
    private static final List<String> VSAT_FULL = List.of("TOAN", "LY", "HOA", "SINH", "SU", "DIA");

    @Autowired private ObjectMapper mapper;

    private final Map<String, JsonNode> templates = new HashMap<>();

    @BeforeEach
    void loadTemplates() throws Exception {
        JsonNode all = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        all.forEach(template -> templates.put(template.path("code").asText(), template));
    }

    @Test
    void everyHscaScienceTemplateHasFortyQuestionsInFourRowsNinetyMinutesAndTenPoints() {
        for (String subject : HSCA_SCIENCES) {
            for (String code : List.of("HSCA_SUBJECT_" + subject, "HSCA_SUBJECT_" + subject + "_SAMPLE")) {
                JsonNode template = template(code);
                Totals totals = totals(template);
                assertThat(totals.questions).as(code).isEqualTo(40);
                assertThat(totals.points).as(code).isEqualByComparingTo("10");
                assertThat(blocks(template).stream().map(b -> b.path("questionCount").asInt()).toList())
                        .as(code + " rows").containsExactly(20, 5, 5, 10);
                assertThat(template.path("durationMinutes").asInt()).as(code).isEqualTo(90);
                assertThat(template.path("maxScore").decimalValue()).as(code).isEqualByComparingTo("10");
                assertThat(template.path("timing").asText()).as(code).isEqualTo("SHARED");
                assertThat(template.path("fixedBlockCounts").asBoolean()).as(code).isTrue();
                assertThat(template.path("fixedCounts").asBoolean()).as(code + " H27 flag stays off").isFalse();
                assertThat(template.path("omittedLocked").asBoolean()).as(code).isFalse();
                assertThat(template.path("scope").asText()).as(code).isEqualTo("SUBJECT");
            }
        }
    }

    @Test
    void hscaGradeAndLevelTablesAndTheSampleVariantMatchSpecification41() {
        int[][] grades = {{0, 14, 26}, {0, 12, 28}, {10, 18, 12}, {5, 5, 30}};
        int[][] levels = {{9, 15, 16}, {11, 14, 15}, {12, 15, 13}, {10, 16, 14}};
        int[][][] sample = {
                {{9, 10, 1}, {0, 1, 4}, {0, 2, 3}, {0, 2, 8}},
                {{11, 9, 0}, {0, 3, 2}, {0, 2, 3}, {0, 0, 10}},
                {{12, 8, 0}, {0, 4, 1}, {0, 2, 3}, {0, 1, 9}},
                {{10, 9, 1}, {0, 4, 1}, {0, 2, 3}, {0, 1, 9}}};
        for (int i = 0; i < HSCA_SCIENCES.size(); i++) {
            String subject = HSCA_SCIENCES.get(i);
            for (String code : List.of("HSCA_SUBJECT_" + subject, "HSCA_SUBJECT_" + subject + "_SAMPLE")) {
                JsonNode quotas = template(code).path("structure").path("examQuotas");
                assertThat(quotas).as(code).hasSize(2);
                assertQuota(quotas.get(0), "GRADE", "QUESTION", List.of("G10", "G11", "G12"), grades[i], code);
                assertQuota(quotas.get(1), "DIFFICULTY", "QUESTION", List.of("NB", "TH", "VD"), levels[i], code);
                assertThat(quotas.get(0).path("subjectCodes").get(0).asText()).as(code).isEqualTo(subject);
            }
            JsonNode plain = template("HSCA_SUBJECT_" + subject);
            JsonNode variant = template("HSCA_SUBJECT_" + subject + "_SAMPLE");
            assertThat(plain.path("isDefault").asBoolean()).as(subject + " default").isTrue();
            assertThat(variant.path("isDefault").asBoolean()).as(subject + " variant is not the default").isFalse();
            assertThat(blocks(plain)).allSatisfy(b -> assertThat(b.has("matrix")).as("plain block matrix").isFalse());
            List<JsonNode> rows = blocks(variant);
            int[] total = new int[3];
            for (int row = 0; row < 4; row++) {
                JsonNode matrix = rows.get(row).path("matrix");
                assertThat(matrix.path("difficultyEnforcement").asText()).isEqualTo("REFERENCE");
                assertThat(matrix.path("gradeEnforcement").asText()).isEqualTo("REFERENCE");
                assertThat(matrix.path("byGrade")).as("grade is off in the sample matrix").isEmpty();
                for (int level = 0; level < 3; level++) {
                    JsonNode value = matrix.path("byDifficulty").get(level);
                    assertThat(value.path("code").asText()).isEqualTo(List.of("NB", "TH", "VD").get(level));
                    assertThat(value.path("value").asInt()).as(subject + " row " + row).isEqualTo(sample[i][row][level]);
                    total[level] += value.path("value").asInt();
                }
                assertThat(sample[i][row][0] + sample[i][row][1] + sample[i][row][2])
                        .as(subject + " row " + row + " covers its row").isEqualTo(rows.get(row).path("questionCount").asInt());
            }
            assertThat(total).as(subject + " sample rows add up to the whole-subject levels").isEqualTo(levels[i]);
        }
    }

    @Test
    void hscaScienceMultipleChoiceBlocksExpectPartialScoring() {
        for (String subject : HSCA_SCIENCES) {
            for (String code : List.of("HSCA_SUBJECT_" + subject, "HSCA_SUBJECT_" + subject + "_SAMPLE")) {
                List<JsonNode> multipleChoice = blocks(template(code)).stream()
                        .filter(b -> "MULTIPLE_CHOICE".equals(b.path("questionType").asText())).toList();
                assertThat(multipleChoice).as(code + " multiple-choice blocks").hasSize(1);
                assertThat(multipleChoice.get(0).path("expectedScoring").path("multipleChoiceRule").asText())
                        .as(code + " D17").isEqualTo("PARTIAL");
            }
        }
    }

    @Test
    void hscaLiteratureIsTwentyQuestionsInThreeSetsAndKeepsTheWritingPartsOmittedAndLocked() {
        JsonNode template = template("HSCA_SUBJECT_VAN");
        assertThat(totals(template).questions).isEqualTo(20);
        assertThat(totals(template).points).isEqualByComparingTo("20");
        assertThat(template.path("maxScore").decimalValue()).isEqualByComparingTo("20");
        assertThat(template.hasNonNull("durationMinutes")).isFalse();
        assertThat(template.path("omittedLocked").asBoolean()).isTrue();
        assertThat(template.path("fixedBlockCounts").asBoolean()).isFalse();
        JsonNode parts = template.path("structure").path("parts");
        assertThat(parts).hasSize(3);
        List<JsonNode> sets = new ArrayList<>();
        parts.get(0).path("sections").get(0).path("blocks").forEach(sets::add);
        assertThat(sets.stream().map(b -> b.path("setCount").asInt() + "x" + b.path("setSize").asInt()).toList())
                .containsExactly("1x5", "1x7", "1x8");
        assertThat(sets.stream().map(b -> b.path("tagCodes").get(0).asText()).toList())
                .containsExactly("VAN_THONG_TIN", "VAN_NGHI_LUAN", "VAN_THO");
        assertThat(sets).allSatisfy(b -> {
            assertThat(b.path("childAnswerType").asText()).isEqualTo("SINGLE_CHOICE");
            assertThat(b.path("itemShape").path("options").asInt()).isEqualTo(4);
        });
        for (int i : new int[] {1, 2}) {
            JsonNode part = parts.get(i);
            assertThat(part.path("questionCount").asInt()).isZero();
            assertThat(part.hasNonNull("maxPoints")).as("an omitted part declares no maxPoints").isFalse();
            JsonNode writing = part.path("sections").get(0).path("blocks");
            assertThat(writing).hasSize(1);
            assertThat(writing.get(0).path("questionType").asText()).isEqualTo("ESSAY");
            assertThat(writing.get(0).path("omitted").asBoolean()).isTrue();
        }
    }

    @Test
    void hscaEnglishHasThirtyFiveListeningQuestionsWithAudioAndFortyReadingQuestionsWithout() {
        JsonNode template = template("HSCA_SUBJECT_ANH");
        assertThat(totals(template).questions).isEqualTo(75);
        assertThat(template.path("maxScore").decimalValue()).isEqualByComparingTo("75");
        assertThat(template.hasNonNull("durationMinutes")).isFalse();
        assertThat(template.path("fixedBlockCounts").asBoolean()).isFalse();
        assertThat(template.path("omittedLocked").asBoolean()).isFalse();
        JsonNode parts = template.path("structure").path("parts");
        assertThat(parts).hasSize(2);
        JsonNode listening = parts.get(0).path("sections").get(0);
        JsonNode reading = parts.get(1).path("sections").get(0);
        assertThat(parts.get(0).path("questionCount").asInt()).isEqualTo(35);
        assertThat(parts.get(1).path("questionCount").asInt()).isEqualTo(40);
        assertThat(listening.path("audioRequirement").asText()).isEqualTo("REQUIRED");
        assertThat(reading.path("audioRequirement").asText()).isEqualTo("FORBIDDEN");
        List<Integer> rows = new ArrayList<>();
        listening.path("blocks").forEach(b -> rows.add(b.path("questionCount").asInt()));
        assertThat(rows).containsExactly(8, 12, 15);
        assertThat(listening.path("blocks").get(0).path("questionType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(listening.path("blocks").get(1).path("setCount").asInt()).isEqualTo(3);
        assertThat(listening.path("blocks").get(1).path("setSize").asInt()).isEqualTo(4);
        assertThat(listening.path("blocks").get(2).path("setCount").asInt()).isEqualTo(3);
        assertThat(listening.path("blocks").get(2).path("setSize").asInt()).isEqualTo(5);
        JsonNode block = reading.path("blocks").get(0);
        assertThat(reading.path("blocks")).hasSize(1);
        assertThat(block.path("setCount").asInt()).isEqualTo(4);
        assertThat(block.path("setSize").asInt()).isEqualTo(10);
        assertThat(block.path("childAnswerType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(block.path("itemShape").path("options").asInt()).isEqualTo(4);
        assertThat(template.path("structure").path("examQuotas")).as("grade and level are off").isEmpty();
    }

    @Test
    void everyVsatSubjectTemplateHasTwentyFiveQuestionsSixtySevenPromptsAndOneHundredFiftyPoints() {
        for (String subject : VSAT_FULL) {
            String code = "VSAT_SUBJECT_" + subject;
            JsonNode template = template(code);
            Totals totals = totals(template);
            assertThat(totals.questions).as(code).isEqualTo(25);
            assertThat(totals.commands).as(code).isEqualTo(67);
            assertThat(totals.points).as(code).isEqualByComparingTo("150");
            assertThat(template.path("maxScore").decimalValue()).as(code).isEqualByComparingTo("150");
            assertThat(template.path("durationMinutes").asInt()).as(code).isEqualTo("TOAN".equals(subject) ? 90 : 60);
            assertThat(template.path("fixedBlockCounts").asBoolean()).as(code).isTrue();
            assertThat(template.path("fixedCounts").asBoolean()).as(code + " H27 flag stays off").isFalse();
            assertThat(template.path("omittedLocked").asBoolean()).as(code).isFalse();
            JsonNode parts = template.path("structure").path("parts");
            assertThat(parts).as(code).hasSize(4);
            int[] questions = {9, 6, 5, 5};
            int[] commands = {36, 6, 20, 5};
            String[] points = {"54", "36", "30", "30"};
            for (int i = 0; i < 4; i++) {
                Totals part = totals(parts.get(i));
                assertThat(part.questions).as(code + " part " + (i + 1)).isEqualTo(questions[i]);
                assertThat(part.commands).as(code + " part " + (i + 1)).isEqualTo(commands[i]);
                assertThat(part.points).as(code + " part " + (i + 1)).isEqualByComparingTo(points[i]);
                assertThat(parts.get(i).path("maxPoints").decimalValue()).isEqualByComparingTo(points[i]);
            }
            JsonNode trueFalse = parts.get(0).path("sections").get(0).path("blocks").get(0);
            assertThat(trueFalse.path("questionType").asText()).isEqualTo("TRUE_FALSE");
            assertThat(trueFalse.path("itemShape").path("statements").asInt()).isEqualTo(4);
            assertThat(trueFalse.path("expectedScoring").path("trueFalseMethod").asText()).isEqualTo("COUNT_TABLE");
            assertThat(trueFalse.path("expectedScoring").path("trueFalseTable").toString()).isEqualTo("[1,2,3,6]");
            JsonNode choice = parts.get(1).path("sections").get(0).path("blocks").get(0);
            assertThat(choice.path("setCount").asInt()).isEqualTo(2);
            assertThat(choice.path("setSize").asInt()).isEqualTo(3);
            assertThat(choice.path("childAnswerType").asText()).isEqualTo("SINGLE_CHOICE");
            JsonNode matching = parts.get(2).path("sections").get(0).path("blocks").get(0);
            assertThat(matching.path("questionType").asText()).isEqualTo("MATCHING");
            assertThat(matching.path("itemShape").path("leftItems").asInt()).isEqualTo(4);
            assertThat(parts.get(3).path("sections").get(0).path("blocks").get(0).path("questionType").asText())
                    .isEqualTo("SHORT_ANSWER");
            assertThat(template.path("structure").path("examQuotas").findValues("basis")).as(code)
                    .allSatisfy(basis -> assertThat(basis.asText()).isEqualTo("PROMPT"));
        }
    }

    @Test
    void vsatLiteratureIsTwentyQuestionsSixtyTwoPromptsAndTheWritingPartIsStoredOmittedAndLocked() {
        JsonNode template = template("VSAT_SUBJECT_VAN");
        Totals totals = totals(template);
        assertThat(totals.questions).isEqualTo(20);
        assertThat(totals.commands).isEqualTo(62);
        assertThat(totals.points).isEqualByComparingTo("120");
        assertThat(template.path("maxScore").decimalValue()).isEqualByComparingTo("120");
        assertThat(template.hasNonNull("durationMinutes")).as("the duration is entered at creation (Q-35)").isFalse();
        assertThat(template.path("omittedLocked").asBoolean()).isTrue();
        assertThat(template.path("fixedBlockCounts").asBoolean()).isTrue();
        JsonNode parts = template.path("structure").path("parts");
        assertThat(parts).hasSize(4);
        JsonNode writing = parts.get(3);
        assertThat(writing.path("questionCount").asInt()).isZero();
        assertThat(writing.path("questionCountOnPaper").asInt()).isZero();
        assertThat(writing.hasNonNull("maxPoints")).as("H49: the writing part declares no maxPoints").isFalse();
        JsonNode blocks = writing.path("sections").get(0).path("blocks");
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).path("questionType").asText()).isEqualTo("ESSAY");
        assertThat(blocks.get(0).path("omitted").asBoolean()).isTrue();
        assertThat(blocks.get(0).path("questionCount").asInt()).isEqualTo(1);
        assertThat(blocks.get(0).path("requiredPointsPerQuestion").decimalValue()).isEqualByComparingTo("30");
        JsonNode poems = parts.get(1).path("sections").get(0).path("blocks").get(0);
        assertThat(poems.path("setCount").asInt()).isEqualTo(2);
        assertThat(poems.path("slots").get(0).path("tagCodes").get(0).asText()).isEqualTo("VAN_THO");
        assertThat(poems.path("slots").get(1).path("tagCodes").get(0).asText()).isEqualTo("VAN_TRUYEN");
    }

    @Test
    void vsatChemistryRunsSixtyMinutesAndOffersTheFiftyMinuteSampleVariant() {
        JsonNode standard = template("VSAT_SUBJECT_HOA");
        JsonNode variant = template("VSAT_SUBJECT_HOA_50");
        assertThat(standard.path("durationMinutes").asInt()).isEqualTo(60);
        assertThat(standard.path("isDefault").asBoolean()).isTrue();
        assertThat(variant.path("durationMinutes").asInt()).isEqualTo(50);
        assertThat(variant.path("isDefault").asBoolean()).isFalse();
        assertThat(variant.path("subjectCode").asText()).isEqualTo("HOA");
        assertThat(variant.path("kindCode").asText()).isEqualTo("VSAT");
        assertThat(totals(variant).points).isEqualByComparingTo("150");
        assertThat(variant.path("fixedBlockCounts").asBoolean()).isTrue();
        long defaults = templates.values().stream().filter(t -> "VSAT".equals(t.path("kindCode").asText())
                && "STANDARD".equals(t.path("buildMode").asText()) && "HOA".equals(t.path("subjectCode").asText())
                && t.path("isDefault").asBoolean()).count();
        assertThat(defaults).as("one default per kind and subject").isEqualTo(1);
    }

    @Test
    void vsatReferenceDistributionsAreCountedByPromptAndTheStrandsAreWaitingForMapping() {
        record Row(String subject, int[] grades, int[] levels, int total) {}
        List<Row> rows = List.of(
                new Row("TOAN", new int[] {0, 17, 50}, new int[] {13, 35, 19}, 67),
                new Row("LY", new int[] {0, 25, 42}, new int[] {19, 32, 16}, 67),
                new Row("HOA", new int[] {0, 23, 44}, new int[] {14, 32, 21}, 67),
                new Row("SINH", new int[] {0, 13, 54}, new int[] {15, 28, 24}, 67),
                new Row("SU", new int[] {6, 2, 59}, new int[] {15, 40, 12}, 67),
                new Row("DIA", null, new int[] {20, 42, 5}, 67),
                new Row("VAN", null, new int[] {12, 33, 17}, 62));
        for (Row row : rows) {
            String code = "VSAT_SUBJECT_" + row.subject;
            JsonNode quotas = template(code).path("structure").path("examQuotas");
            int next = 0;
            if (row.grades != null) {
                assertQuota(quotas.get(next++), "GRADE", "PROMPT", List.of("G10", "G11", "G12"), row.grades, code);
            }
            assertQuota(quotas.get(next++), "DIFFICULTY", "PROMPT", List.of("NB", "TH", "VD"), row.levels, code);
            assertThat(quotas).as(code + " no grade table for a subject without grades").hasSize(next);
            assertThat(java.util.Arrays.stream(row.levels).sum()).as(code + " denominator").isEqualTo(row.total);
            assertThat(totals(template(code)).commands).as(code + " denominator is the template's prompts")
                    .isEqualTo(row.total);
        }
        assertStrands("VSAT_SUBJECT_VAN", List.of(16, 16, 15, 12, 3), 62);
        assertStrands("VSAT_SUBJECT_DIA", List.of(26, 17, 8, 10, 6), 67);
        for (String subject : List.of("TOAN", "LY", "HOA", "SINH", "SU")) {
            assertThat(template("VSAT_SUBJECT_" + subject).path("structure").findValues("topicQuotas"))
                    .as(subject + " has no strands").isEmpty();
        }
        JsonNode geography = template("VSAT_SUBJECT_DIA").path("structure").path("parts").get(1)
                .path("sections").get(0).path("blocks").get(0);
        assertThat(geography.path("slots").get(0).path("tagCodes").get(0).asText()).isEqualTo("DIA_BANG_SO_LIEU");
        assertThat(geography.path("slots").get(1).path("tagCodes").get(0).asText()).isEqualTo("DIA_BIEU_DO");
    }

    @Test
    void thereIsAReviewTemplateForEachNewExaminationKind() {
        for (String kind : List.of("HSCA", "VSAT")) {
            JsonNode review = template(kind + "_REVIEW");
            assertThat(review.path("buildMode").asText()).isEqualTo("REVIEW");
            assertThat(review.path("kindCode").asText()).isEqualTo(kind);
            assertThat(review.hasNonNull("subjectCode")).isFalse();
            assertThat(review.path("isDefault").asBoolean()).isTrue();
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private void assertStrands(String code, List<Integer> counts, int total) {
        JsonNode strands = template(code).path("structure").path("parts").get(0).path("sections").get(0)
                .path("topicQuotas");
        assertThat(strands).as(code).hasSize(5);
        int sum = 0;
        for (int i = 0; i < strands.size(); i++) {
            assertThat(strands.get(i).path("count").asInt()).as(code).isEqualTo(counts.get(i));
            assertThat(strands.get(i).path("basis").asText()).as(code).isEqualTo("PROMPT");
            assertThat(strands.get(i).path("enforcement").asText()).as(code).isEqualTo("REFERENCE");
            assertThat(strands.get(i).path("nodeIds")).as(code + " waits for mapping").isEmpty();
            sum += strands.get(i).path("count").asInt();
        }
        assertThat(sum).as(code).isEqualTo(total);
    }

    private static void assertQuota(JsonNode quota, String dimension, String basis, List<String> codes, int[] counts,
            String code) {
        assertThat(quota.path("dimension").asText()).as(code).isEqualTo(dimension);
        assertThat(quota.path("basis").asText()).as(code + " " + dimension).isEqualTo(basis);
        assertThat(quota.path("enforcement").asText()).as(code + " " + dimension).isEqualTo("REFERENCE");
        assertThat(quota.path("buckets")).as(code + " " + dimension).hasSize(codes.size());
        for (int i = 0; i < codes.size(); i++) {
            assertThat(quota.path("buckets").get(i).path("codes").get(0).asText()).as(code).isEqualTo(codes.get(i));
            assertThat(quota.path("buckets").get(i).path("count").asInt()).as(code + " " + dimension + " " + codes.get(i))
                    .isEqualTo(counts[i]);
        }
    }

    private JsonNode template(String code) {
        JsonNode template = templates.get(code);
        assertThat(template).as("template " + code).isNotNull();
        return template;
    }

    private static List<JsonNode> blocks(JsonNode node) {
        List<JsonNode> blocks = new ArrayList<>();
        collect(node.path("structure").isMissingNode() ? node : node.path("structure"), blocks);
        return blocks;
    }

    private static void collect(JsonNode node, List<JsonNode> blocks) {
        if (node.isObject() && node.has("blocks")) {
            node.path("blocks").forEach(blocks::add);
        }
        if (node.isContainerNode()) node.forEach(child -> collect(child, blocks));
    }

    private record Totals(int questions, int commands, BigDecimal points) {}

    /** Questions, prompts and points of the blocks that are not omitted, as the exam check counts them. */
    private static Totals totals(JsonNode node) {
        int questions = 0;
        int commands = 0;
        BigDecimal points = BigDecimal.ZERO;
        for (JsonNode block : blocks(node)) {
            if (block.path("omitted").asBoolean(false)) continue;
            int count = block.path("questionCount").asInt();
            String type = block.path("questionType").asText();
            int perQuestion = "TRUE_FALSE".equals(type) ? block.path("itemShape").path("statements").asInt(1)
                    : "MATCHING".equals(type) ? block.path("itemShape").path("leftItems").asInt(1) : 1;
            questions += count;
            commands += count * perQuestion;
            points = points.add(block.path("requiredPointsPerQuestion").decimalValue().multiply(BigDecimal.valueOf(count)));
        }
        return new Totals(questions, commands, points);
    }
}
