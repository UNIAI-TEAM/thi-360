package vn.unicom.thi360.attempt.grading;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.domain.grading.ExactRational;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicies;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicy;
import vn.unicom.thi360.attempt.domain.grading.ItemGradeResult;
import vn.unicom.thi360.attempt.domain.grading.ItemGrader;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.AggregatedScore;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.ItemInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.PartInput;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Unit tests for H-SCA half-credit multiple-choice grading policy (BS §17.5, AC-1..AC-3).
 *
 * <p>Exercises {@link GradingPolicies#forKind(String)}, {@link ItemGrader#grade}, and
 * {@link ScoreAggregator#aggregate} directly so assertions validate real end-to-end grading behavior.
 */
class HalfCreditMultipleChoiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void anyWrongIsZero() {
        GradingPolicy hsca = GradingPolicies.forKind("HSCA");
        for (BigDecimal points : List.of(new BigDecimal("0.25"), new BigDecimal("1.00"))) {
            for (int totalCorrect : List.of(2, 3, 4)) {
                JsonNode snapshot = multipleChoiceSnapshot(points, "PARTIAL", totalCorrect, 2);

                // Wrong option only
                assertWrongZero(snapshot, points, hsca, List.of("w1"));
                // Incomplete correct + 1 wrong
                assertWrongZero(snapshot, points, hsca, List.of("c1", "w1"));
                // All correct + 1 wrong
                List<String> allPlusWrong = new ArrayList<>(correctIds(totalCorrect));
                allPlusWrong.add("w1");
                assertWrongZero(snapshot, points, hsca, allPlusWrong);
                // Duplicate wrong option + duplicate correct option
                assertWrongZero(snapshot, points, hsca, List.of("c1", "c1", "w2", "w2"));
            }
        }
    }

    @Test
    void incompleteIsHalf() {
        for (String hscaVariant : List.of("HSCA", " hsca ", "Hsca")) {
            GradingPolicy hsca = GradingPolicies.forKind(hscaVariant);
            for (BigDecimal points : List.of(new BigDecimal("0.25"), new BigDecimal("1.00"))) {
                // 2 correct options: 1 of 2 -> half
                JsonNode twoCorrect = multipleChoiceSnapshot(points, "PARTIAL", 2, 2);
                assertHalfCredit(twoCorrect, points, hsca, List.of("c1"));
                assertHalfCredit(twoCorrect, points, hsca, List.of("c2"));
                // Duplicate selected id must not masquerade as 2 distinct options
                assertHalfCredit(twoCorrect, points, hsca, List.of("c1", "c1"));

                // 3 correct options: 1 of 3 -> half
                JsonNode threeCorrect = multipleChoiceSnapshot(points, "PARTIAL", 3, 1);
                assertHalfCredit(threeCorrect, points, hsca, List.of("c1"));

                // 4 correct options: 1 of 4, 2 of 4, 3 of 4 -> all half
                JsonNode fourCorrect = multipleChoiceSnapshot(points, "PARTIAL", 4, 1);
                assertHalfCredit(fourCorrect, points, hsca, List.of("c1"));
                assertHalfCredit(fourCorrect, points, hsca, List.of("c1", "c3"));
                assertHalfCredit(fourCorrect, points, hsca, List.of("c1", "c2", "c4"));
            }
        }
    }

    @Test
    void twoOfThreeIsStillHalf() {
        GradingPolicy hsca = GradingPolicies.forKind("HSCA");

        JsonNode onePoint = multipleChoiceSnapshot(new BigDecimal("1.00"), "PARTIAL", 3, 1);
        ItemGradeResult resOne = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                onePoint,
                new AnswerValue.MultipleChoice(List.of("c1", "c2")),
                new BigDecimal("1.00"),
                hsca);
        assertThat(resOne.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(resOne.exactEarnedPoints()).isEqualTo(ExactRational.of(1, 2));
        assertThat(resOne.earnedPoints()).isEqualByComparingTo("0.5000");
        assertThat(resOne.gradingDetail()).containsEntry("exactFraction", "1/2");

        JsonNode quarterPoint = multipleChoiceSnapshot(new BigDecimal("0.25"), "PARTIAL", 3, 1);
        ItemGradeResult resQuarter = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                quarterPoint,
                new AnswerValue.MultipleChoice(List.of("c2", "c3")),
                new BigDecimal("0.25"),
                hsca);
        assertThat(resQuarter.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(resQuarter.exactEarnedPoints()).isEqualTo(ExactRational.of(1, 8));
        assertThat(resQuarter.earnedPoints()).isEqualByComparingTo("0.1250");
        assertThat(resQuarter.gradingDetail()).containsEntry("exactFraction", "1/8");
    }

    @Test
    void completeIsFull() {
        GradingPolicy hsca = GradingPolicies.forKind("HSCA");
        for (BigDecimal points : List.of(new BigDecimal("0.25"), new BigDecimal("1.00"))) {
            for (int totalCorrect : List.of(2, 3, 4)) {
                JsonNode snapshot = multipleChoiceSnapshot(points, "PARTIAL", totalCorrect, 2);
                List<String> allCorrect = correctIds(totalCorrect);
                List<String> reversedWithDup = new ArrayList<>(allCorrect.reversed());
                reversedWithDup.add(allCorrect.getFirst());

                ItemGradeResult res = ItemGrader.grade(
                        QuestionType.MULTIPLE_CHOICE,
                        snapshot,
                        new AnswerValue.MultipleChoice(reversedWithDup),
                        points,
                        hsca);
                assertThat(res.outcome()).isEqualTo(ItemOutcome.CORRECT);
                assertThat(res.exactEarnedPoints()).isEqualTo(ExactRational.of(points));
                assertThat(res.earnedPoints()).isEqualByComparingTo(points);
            }
        }
    }

    @Test
    void noChoiceIsBlank() {
        GradingPolicy hsca = GradingPolicies.forKind("HSCA");
        JsonNode snapshot = multipleChoiceSnapshot(new BigDecimal("0.25"), "PARTIAL", 3, 1);

        for (AnswerValue blankAnswer : new AnswerValue[] {
                null,
                new AnswerValue.MultipleChoice(List.of()),
                new AnswerValue.MultipleChoice(List.of("", "  "))
        }) {
            ItemGradeResult res = ItemGrader.grade(
                    QuestionType.MULTIPLE_CHOICE,
                    snapshot,
                    blankAnswer,
                    new BigDecimal("0.25"),
                    hsca);
            assertThat(res.outcome()).isEqualTo(ItemOutcome.BLANK);
            assertThat(res.exactEarnedPoints()).isEqualTo(ExactRational.ZERO);
            assertThat(res.earnedPoints()).isEqualByComparingTo("0.0000");
        }

        // ALL_OR_NOTHING question under H-SCA policy still gives 0 WRONG for partial selection
        JsonNode allOrNothing = multipleChoiceSnapshot(new BigDecimal("0.25"), "ALL_OR_NOTHING", 3, 1);
        ItemGradeResult partialOnAllOrNothing = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                allOrNothing,
                new AnswerValue.MultipleChoice(List.of("c1", "c2")),
                new BigDecimal("0.25"),
                hsca);
        assertThat(partialOnAllOrNothing.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(partialOnAllOrNothing.earnedPoints()).isEqualByComparingTo("0.0000");
    }

    @Test
    void otherKindsStayProportional() {
        JsonNode snapshot = multipleChoiceSnapshot(new BigDecimal("3.00"), "PARTIAL", 3, 1);
        List<String> kinds = new ArrayList<>(List.of(
                "HSA", "QDA", "VACT", "BCA", "SPT", "SP2E", "THPTQG", "VSAT", "UNKNOWN"));
        kinds.add(null);

        for (String kind : kinds) {
            GradingPolicy policy = GradingPolicies.forKind(kind);
            assertThat(policy.multipleChoicePartialMode())
                    .as("kind=%s", kind)
                    .isEqualTo(GradingPolicy.MultipleChoicePartialMode.PROPORTIONAL);

            ItemGradeResult twoOfThree = ItemGrader.grade(
                    QuestionType.MULTIPLE_CHOICE,
                    snapshot,
                    new AnswerValue.MultipleChoice(List.of("c1", "c2")),
                    new BigDecimal("3.00"),
                    policy);
            assertThat(twoOfThree.outcome()).isEqualTo(ItemOutcome.PARTIAL);
            assertThat(twoOfThree.exactEarnedPoints()).isEqualTo(ExactRational.of(2, 1));
            assertThat(twoOfThree.earnedPoints()).isEqualByComparingTo("2.0000");

            ItemGradeResult oneOfThree = ItemGrader.grade(
                    QuestionType.MULTIPLE_CHOICE,
                    snapshot,
                    new AnswerValue.MultipleChoice(List.of("c1")),
                    new BigDecimal("3.00"),
                    policy);
            assertThat(oneOfThree.outcome()).isEqualTo(ItemOutcome.PARTIAL);
            assertThat(oneOfThree.exactEarnedPoints()).isEqualTo(ExactRational.ONE);
            assertThat(oneOfThree.earnedPoints()).isEqualByComparingTo("1.0000");

            ItemGradeResult anyWrong = ItemGrader.grade(
                    QuestionType.MULTIPLE_CHOICE,
                    snapshot,
                    new AnswerValue.MultipleChoice(List.of("c1", "c2", "w1")),
                    new BigDecimal("3.00"),
                    policy);
            assertThat(anyWrong.outcome()).isEqualTo(ItemOutcome.WRONG);
            assertThat(anyWrong.earnedPoints()).isEqualByComparingTo("0.0000");
        }
    }

    @Test
    void eighthPointKeptUntilFinalRounding() {
        GradingPolicy hsca = GradingPolicies.forKind("HSCA");
        BigDecimal quarter = new BigDecimal("0.25");
        JsonNode threeCorrect = multipleChoiceSnapshot(quarter, "PARTIAL", 3, 1);

        ItemGradeResult graded = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                threeCorrect,
                new AnswerValue.MultipleChoice(List.of("c1", "c2")),
                quarter,
                hsca);
        assertThat(graded.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(graded.exactEarnedPoints()).isEqualTo(ExactRational.of(1, 8));
        assertThat(graded.earnedPoints()).isEqualByComparingTo("0.1250");
        assertThat(graded.earnedPoints().scale()).isEqualTo(4);
        assertThat(graded.gradingDetail()).containsEntry("exactFraction", "1/8");

        // Verify rounding once HALF_UP for 1, 2, 3, 4 eighth-point items, BOTH with and without exactFraction
        assertRoundedSum(graded, 1, "0.13", true);
        assertRoundedSum(graded, 2, "0.25", true);
        assertRoundedSum(graded, 3, "0.38", true);
        assertRoundedSum(graded, 4, "0.50", true);

        assertRoundedSum(graded, 1, "0.13", false);
        assertRoundedSum(graded, 2, "0.25", false);
        assertRoundedSum(graded, 3, "0.38", false);
        assertRoundedSum(graded, 4, "0.50", false);

        // All-half 40-question H-SCA exam (40 * 0.125 = 5.00 / 10.00)
        assertRoundedSum(graded, 40, "5.00", false);

        // All-blank 40-question H-SCA exam (0.00 / 10.00)
        UUID partId = UUID.randomUUID();
        List<ItemInput> allBlank = new ArrayList<>(40);
        for (int i = 0; i < 40; i++) {
            allBlank.add(new ItemInput(
                    UUID.randomUUID(), partId, null, null, "sec-1", "TOAN",
                    QuestionType.MULTIPLE_CHOICE, 1, true, quarter,
                    BigDecimal.ZERO, ItemOutcome.BLANK, Map.of()));
        }
        AggregatedScore blankRes = ScoreAggregator.aggregate(new ScoreAggregator.Input(
                "HSCA",
                "RAW",
                null,
                List.of(new PartInput(partId, "P1", "Phần 1", 1, PartState.CLOSED, new BigDecimal("10.00"), 40)),
                allBlank,
                List.of()));
        assertThat(blankRes.rawScore()).isEqualByComparingTo("0.00");
        assertThat(blankRes.displayScore()).isEqualByComparingTo("0.00");
        assertThat(blankRes.statistics().blankCount()).isEqualTo(40);
    }

    private static void assertRoundedSum(
            ItemGradeResult graded, int count, String expectedScore, boolean includeExactFraction) {
        UUID partId = UUID.randomUUID();
        Map<String, Object> detail = includeExactFraction ? graded.gradingDetail() : Map.of();
        List<ItemInput> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new ItemInput(
                    UUID.randomUUID(),
                    partId,
                    null,
                    null,
                    "sec-1",
                    "TOAN",
                    QuestionType.MULTIPLE_CHOICE,
                    1,
                    true,
                    graded.pointsMax(),
                    graded.earnedPoints(),
                    graded.outcome(),
                    detail));
        }
        BigDecimal partMax = graded.pointsMax().multiply(BigDecimal.valueOf(count));
        AggregatedScore aggregated = ScoreAggregator.aggregate(new ScoreAggregator.Input(
                "HSCA",
                "RAW",
                null,
                List.of(new PartInput(partId, "P1", "Phần 1", 1, PartState.CLOSED, partMax, count)),
                items,
                List.of()));
        assertThat(aggregated.rawScore())
                .as("count=%d includeExactFraction=%s", count, includeExactFraction)
                .isEqualByComparingTo(expectedScore);
        assertThat(aggregated.displayScore()).isEqualByComparingTo(expectedScore);
        assertThat(aggregated.statistics().partialCount()).isEqualTo(count);
    }

    private static void assertWrongZero(
            JsonNode snapshot, BigDecimal points, GradingPolicy policy, List<String> optionIds) {
        ItemGradeResult res = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(optionIds),
                points,
                policy);
        assertThat(res.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(res.exactEarnedPoints()).isEqualTo(ExactRational.ZERO);
        assertThat(res.earnedPoints()).isEqualByComparingTo("0.0000");
    }

    private static void assertHalfCredit(
            JsonNode snapshot, BigDecimal points, GradingPolicy policy, List<String> optionIds) {
        ItemGradeResult res = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(optionIds),
                points,
                policy);
        ExactRational expectedExact = ExactRational.of(points).multiply(ExactRational.of(1, 2));
        assertThat(res.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(res.exactEarnedPoints()).isEqualTo(expectedExact);
        assertThat(res.earnedPoints()).isEqualByComparingTo(expectedExact.toBigDecimal(4));
    }

    private static List<String> correctIds(int totalCorrect) {
        List<String> ids = new ArrayList<>(totalCorrect);
        for (int i = 1; i <= totalCorrect; i++) {
            ids.add("c" + i);
        }
        return ids;
    }

    private static JsonNode multipleChoiceSnapshot(
            BigDecimal points, String scoringRule, int totalCorrect, int totalWrong) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "MULTIPLE_CHOICE");
        root.put("points", points);

        List<Map<String, Object>> options = new ArrayList<>(totalCorrect + totalWrong);
        for (int i = 1; i <= totalCorrect; i++) {
            options.add(Map.of("id", "c" + i, "content", "Correct " + i, "correct", true));
        }
        for (int i = 1; i <= totalWrong; i++) {
            options.add(Map.of("id", "w" + i, "content", "Wrong " + i, "correct", false));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scoringRule", scoringRule);
        body.put("options", options);
        root.put("body", body);
        return MAPPER.valueToTree(root);
    }
}
