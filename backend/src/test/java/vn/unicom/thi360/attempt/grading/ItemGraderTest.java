package vn.unicom.thi360.attempt.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.grading.ExactRational;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicies;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicy;
import vn.unicom.thi360.attempt.domain.grading.ItemGradeResult;
import vn.unicom.thi360.attempt.domain.grading.ItemGrader;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ItemGraderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void singleChoiceRows() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "SINGLE_CHOICE",
                  "points": 2.0,
                  "body": {
                    "options": [
                      {"id": "opt-a", "content": "A", "correct": false},
                      {"id": "opt-b", "content": "B", "correct": true},
                      {"id": "opt-c", "content": "C", "correct": false}
                    ]
                  }
                }
                """);

        ItemGradeResult correct = ItemGrader.grade(
                QuestionType.SINGLE_CHOICE,
                snapshot,
                new AnswerValue.SingleChoice("opt-b"),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(correct.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(correct.earnedPoints()).isEqualByComparingTo("2.0");

        ItemGradeResult wrong = ItemGrader.grade(
                QuestionType.SINGLE_CHOICE,
                snapshot,
                new AnswerValue.SingleChoice("opt-a"),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(wrong.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(wrong.earnedPoints()).isEqualByComparingTo("0");

        ItemGradeResult blank = ItemGrader.grade(
                QuestionType.SINGLE_CHOICE,
                snapshot,
                null,
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(blank.outcome()).isEqualTo(ItemOutcome.BLANK);
        assertThat(blank.earnedPoints()).isEqualByComparingTo("0");
    }

    @Test
    void multipleChoiceAllOrNothingRows() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "MULTIPLE_CHOICE",
                  "points": 2.0,
                  "body": {
                    "scoringRule": "ALL_OR_NOTHING",
                    "options": [
                      {"id": "o1", "content": "1", "correct": true},
                      {"id": "o2", "content": "2", "correct": true},
                      {"id": "o3", "content": "3", "correct": false}
                    ]
                  }
                }
                """);

        ItemGradeResult exact = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of("o2", "o1")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(exact.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(exact.earnedPoints()).isEqualByComparingTo("2.0");

        ItemGradeResult missingOne = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of("o1")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(missingOne.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(missingOne.earnedPoints()).isEqualByComparingTo("0");

        ItemGradeResult withExtraWrong = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of("o1", "o2", "o3")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(withExtraWrong.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(withExtraWrong.earnedPoints()).isEqualByComparingTo("0");

        ItemGradeResult empty = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of()),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(empty.outcome()).isEqualTo(ItemOutcome.BLANK);
        assertThat(empty.earnedPoints()).isEqualByComparingTo("0");
    }

    @Test
    void multipleChoicePartialRows() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "MULTIPLE_CHOICE",
                  "points": 1.0,
                  "body": {
                    "scoringRule": "PARTIAL",
                    "options": [
                      {"id": "o1", "content": "1", "correct": true},
                      {"id": "o2", "content": "2", "correct": true},
                      {"id": "o3", "content": "3", "correct": true},
                      {"id": "o4", "content": "4", "correct": false}
                    ]
                  }
                }
                """);

        ItemGradeResult oneOfThree = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of("o1")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(oneOfThree.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(oneOfThree.exactEarnedPoints()).isEqualTo(ExactRational.of(1, 3));
        assertThat(oneOfThree.gradingDetail()).containsEntry("exactFraction", "1/3");

        ItemGradeResult twoOfThree = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of("o1", "o3")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(twoOfThree.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(twoOfThree.exactEarnedPoints()).isEqualTo(ExactRational.of(2, 3));

        ItemGradeResult allThree = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of("o1", "o2", "o3")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(allThree.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(allThree.earnedPoints()).isEqualByComparingTo("1.0");

        ItemGradeResult anyWrongCancelsCredit = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE,
                snapshot,
                new AnswerValue.MultipleChoice(List.of("o1", "o2", "o4")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(anyWrongCancelsCredit.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(anyWrongCancelsCredit.earnedPoints()).isEqualByComparingTo("0");
    }

    @Test
    void shortAnswerNormalizationRows() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "SHORT_ANSWER",
                  "points": 1.0,
                  "body": {
                    "answers": ["  Hà   Nội ", "3,14", "0.5"]
                  }
                }
                """);

        // NFD + multi-space + upper-case matches NFC trimmed answer
        String nfd = "  HA\u0300  \u00A0 NO\u0302\u0323I  ";
        ItemGradeResult nfdMatch = ItemGrader.grade(
                QuestionType.SHORT_ANSWER,
                snapshot,
                new AnswerValue.ShortAnswer(nfd),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(nfdMatch.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(nfdMatch.earnedPoints()).isEqualByComparingTo("1");

        // Decimal comma vs dot does not cross-convert
        ItemGradeResult dotVsComma = ItemGrader.grade(
                QuestionType.SHORT_ANSWER,
                snapshot,
                new AnswerValue.ShortAnswer("3.14"),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(dotVsComma.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(dotVsComma.earnedPoints()).isEqualByComparingTo("0");

        // Fraction 1/2 does not cross-convert to 0.5
        ItemGradeResult fractionVsDecimal = ItemGrader.grade(
                QuestionType.SHORT_ANSWER,
                snapshot,
                new AnswerValue.ShortAnswer("1/2"),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(fractionVsDecimal.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(fractionVsDecimal.earnedPoints()).isEqualByComparingTo("0");

        // Whitespace-only counts as BLANK
        ItemGradeResult blank = ItemGrader.grade(
                QuestionType.SHORT_ANSWER,
                snapshot,
                new AnswerValue.ShortAnswer("   \t \u00A0 "),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(blank.outcome()).isEqualTo(ItemOutcome.BLANK);
        assertThat(blank.earnedPoints()).isEqualByComparingTo("0");
    }

    @Test
    void clozeRows() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "CLOZE",
                  "points": 1.0,
                  "body": {
                    "blanks": [
                      {"id": "b1", "answers": ["alpha"]},
                      {"id": "b2", "answers": ["beta"]},
                      {"id": "b3", "answers": ["gamma"]}
                    ]
                  }
                }
                """);

        ItemGradeResult partial = ItemGrader.grade(
                QuestionType.CLOZE,
                snapshot,
                new AnswerValue.Cloze(Map.of("b1", " ALPHA ", "b2", "wrong")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(partial.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(partial.exactEarnedPoints()).isEqualTo(ExactRational.of(1, 3));
        assertThat(partial.gradingDetail().get("blankResults")).asList().hasSize(3);

        ItemGradeResult allBlank = ItemGrader.grade(
                QuestionType.CLOZE,
                snapshot,
                new AnswerValue.Cloze(Map.of("b1", "  ")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(allBlank.outcome()).isEqualTo(ItemOutcome.BLANK);

        ItemGradeResult allWrong = ItemGrader.grade(
                QuestionType.CLOZE,
                snapshot,
                new AnswerValue.Cloze(Map.of("b1", "x", "b2", "y", "b3", "z")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(allWrong.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(allWrong.earnedPoints()).isEqualByComparingTo("0");

        ItemGradeResult allCorrect = ItemGrader.grade(
                QuestionType.CLOZE,
                snapshot,
                new AnswerValue.Cloze(Map.of("b1", "alpha", "b2", "beta", "b3", "gamma")),
                BigDecimal.ONE,
                GradingPolicy.standard());
        assertThat(allCorrect.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(allCorrect.earnedPoints()).isEqualByComparingTo("1");

        // R1-46: unequal per-blank points + explicit per-blank outcome & pointsEarned assertions
        JsonNode weightedSnapshot = MAPPER.readTree("""
                {
                  "type": "CLOZE",
                  "points": 4.0,
                  "body": {
                    "blanks": [
                      {"id": "b1", "points": 0.5, "answers": ["one"]},
                      {"id": "b2", "points": 1.5, "answers": ["two"]},
                      {"id": "b3", "points": 2.0, "answers": ["three"]}
                    ]
                  }
                }
                """);
        ItemGradeResult weightedPartial = ItemGrader.grade(
                QuestionType.CLOZE,
                weightedSnapshot,
                new AnswerValue.Cloze(Map.of("b1", "one", "b2", "wrong")),
                new BigDecimal("4.0"),
                GradingPolicy.standard());
        assertThat(weightedPartial.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(weightedPartial.earnedPoints()).isEqualByComparingTo("0.50");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blankResults =
                (List<Map<String, Object>>) weightedPartial.gradingDetail().get("blankResults");
        assertThat(blankResults).hasSize(3);
        assertThat(blankResults.get(0)).containsEntry("id", "b1").containsEntry("outcome", "CORRECT");
        assertThat((BigDecimal) blankResults.get(0).get("pointsEarned")).isEqualByComparingTo("0.5");
        assertThat(blankResults.get(1)).containsEntry("id", "b2").containsEntry("outcome", "WRONG");
        assertThat((BigDecimal) blankResults.get(1).get("pointsEarned")).isEqualByComparingTo("0");
        assertThat(blankResults.get(2)).containsEntry("id", "b3").containsEntry("outcome", "BLANK");
        assertThat((BigDecimal) blankResults.get(2).get("pointsEarned")).isEqualByComparingTo("0");

        // R1-38: clamp earned to effectiveMax when blank point sum exceeds effectiveMax
        ItemGradeResult clampedCloze = ItemGrader.grade(
                QuestionType.CLOZE,
                weightedSnapshot,
                new AnswerValue.Cloze(Map.of("b1", "one", "b2", "two", "b3", "three")),
                new BigDecimal("3.0"),
                GradingPolicy.standard());
        assertThat(clampedCloze.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(clampedCloze.earnedPoints()).isEqualByComparingTo("3.00");
    }

    @Test
    void questionSetRows() {
        ItemGradeResult c1 = ItemGradeResult.graded(
                ExactRational.of(1, 1), BigDecimal.ONE, ItemOutcome.CORRECT, Map.of());
        ItemGradeResult c2 = ItemGradeResult.graded(
                ExactRational.of(1, 2), BigDecimal.ONE, ItemOutcome.PARTIAL, Map.of());
        ItemGradeResult rolledUp = ItemGrader.rollUpQuestionSet(List.of(c1, c2), new BigDecimal("2.0"));
        assertThat(rolledUp.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(rolledUp.exactEarnedPoints()).isEqualTo(ExactRational.of(3, 2));
        assertThat(rolledUp.earnedPoints()).isEqualByComparingTo("1.50");

        ItemGradeResult blank1 = ItemGradeResult.graded(
                ExactRational.ZERO, BigDecimal.ONE, ItemOutcome.BLANK, Map.of());
        ItemGradeResult blankSet = ItemGrader.rollUpQuestionSet(List.of(blank1), BigDecimal.ONE);
        assertThat(blankSet.outcome()).isEqualTo(ItemOutcome.BLANK);

        ItemGradeResult essayPending = ItemGradeResult.pendingReview(new BigDecimal("5.0"), Map.of());
        ItemGradeResult pendingSet = ItemGrader.rollUpQuestionSet(
                List.of(c1, essayPending), new BigDecimal("6.0"));
        assertThat(pendingSet.outcome()).isEqualTo(ItemOutcome.PENDING_REVIEW);
        assertThat(pendingSet.pendingReview()).isTrue();
    }

    @Test
    void trueFalsePerStatementRows() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "TRUE_FALSE",
                  "points": 2.0,
                  "body": {
                    "scoringMethod": "PER_STATEMENT",
                    "statements": [
                      {"id": "s1", "content": "1", "answer": true,  "points": 0.5},
                      {"id": "s2", "content": "2", "answer": false, "points": 0.5},
                      {"id": "s3", "content": "3", "answer": true,  "points": 1.0}
                    ]
                  }
                }
                """);

        // Null statement never counts as matching 'false'
        ItemGradeResult oneNullOneMatch = ItemGrader.grade(
                QuestionType.TRUE_FALSE,
                snapshot,
                new AnswerValue.TrueFalse(Map.of("s1", true)),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(oneNullOneMatch.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(oneNullOneMatch.earnedPoints()).isEqualByComparingTo("0.5");

        ItemGradeResult allFalseNoMatch = ItemGrader.grade(
                QuestionType.TRUE_FALSE,
                snapshot,
                new AnswerValue.TrueFalse(Map.of("s1", false, "s2", true, "s3", false)),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(allFalseNoMatch.outcome()).isEqualTo(ItemOutcome.WRONG);
        assertThat(allFalseNoMatch.earnedPoints()).isEqualByComparingTo("0");

        ItemGradeResult allNull = ItemGrader.grade(
                QuestionType.TRUE_FALSE,
                snapshot,
                new AnswerValue.TrueFalse(new java.util.HashMap<>()),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(allNull.outcome()).isEqualTo(ItemOutcome.BLANK);

        ItemGradeResult allCorrect = ItemGrader.grade(
                QuestionType.TRUE_FALSE,
                snapshot,
                new AnswerValue.TrueFalse(Map.of("s1", true, "s2", false, "s3", true)),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(allCorrect.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(allCorrect.earnedPoints()).isEqualByComparingTo("2.0");
    }

    @Test
    void trueFalseCountTableRows() throws Exception {
        JsonNode defaultTableSnapshot = MAPPER.readTree("""
                {
                  "type": "TRUE_FALSE",
                  "points": 1.0,
                  "body": {
                    "scoringMethod": "COUNT_TABLE",
                    "statements": [
                      {"id": "a", "content": "a", "answer": true},
                      {"id": "b", "content": "b", "answer": false},
                      {"id": "c", "content": "c", "answer": true},
                      {"id": "d", "content": "d", "answer": false}
                    ]
                  }
                }
                """);

        assertThat(ItemGrader.grade(
                QuestionType.TRUE_FALSE, defaultTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true)), BigDecimal.ONE, GradingPolicy.standard())
                .earnedPoints()).isEqualByComparingTo("0.10");

        assertThat(ItemGrader.grade(
                QuestionType.TRUE_FALSE, defaultTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true, "b", false)), BigDecimal.ONE, GradingPolicy.standard())
                .earnedPoints()).isEqualByComparingTo("0.25");

        assertThat(ItemGrader.grade(
                QuestionType.TRUE_FALSE, defaultTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true, "b", false, "c", true)), BigDecimal.ONE, GradingPolicy.standard())
                .earnedPoints()).isEqualByComparingTo("0.50");

        ItemGradeResult fourOfFour = ItemGrader.grade(
                QuestionType.TRUE_FALSE, defaultTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true, "b", false, "c", true, "d", false)),
                BigDecimal.ONE, GradingPolicy.standard());
        assertThat(fourOfFour.earnedPoints()).isEqualByComparingTo("1.00");
        assertThat(fourOfFour.outcome()).isEqualTo(ItemOutcome.CORRECT);

        ItemGradeResult zeroMatched = ItemGrader.grade(
                QuestionType.TRUE_FALSE, defaultTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", false)),
                BigDecimal.ONE, GradingPolicy.standard());
        assertThat(zeroMatched.earnedPoints()).isEqualByComparingTo("0");
        assertThat(zeroMatched.outcome()).isEqualTo(ItemOutcome.WRONG);

        // R1-46: all-blank COUNT_TABLE returns BLANK
        ItemGradeResult allBlank = ItemGrader.grade(
                QuestionType.TRUE_FALSE, defaultTableSnapshot,
                new AnswerValue.TrueFalse(Map.of()),
                BigDecimal.ONE, GradingPolicy.standard());
        assertThat(allBlank.earnedPoints()).isEqualByComparingTo("0");
        assertThat(allBlank.outcome()).isEqualTo(ItemOutcome.BLANK);

        // R1-46: explicit custom scoreTable (e.g. V-SAT [1, 2, 3, 6])
        JsonNode customTableSnapshot = MAPPER.readTree("""
                {
                  "type": "TRUE_FALSE",
                  "points": 6.0,
                  "body": {
                    "scoringMethod": "COUNT_TABLE",
                    "scoreTable": [1, 2, 3, 6],
                    "statements": [
                      {"id": "a", "content": "a", "answer": true},
                      {"id": "b", "content": "b", "answer": false},
                      {"id": "c", "content": "c", "answer": true},
                      {"id": "d", "content": "d", "answer": false}
                    ]
                  }
                }
                """);
        ItemGradeResult vsatTwo = ItemGrader.grade(
                QuestionType.TRUE_FALSE, customTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true, "b", false)),
                new BigDecimal("6.0"), GradingPolicy.standard());
        assertThat(vsatTwo.earnedPoints()).isEqualByComparingTo("2.0");
        assertThat(vsatTwo.outcome()).isEqualTo(ItemOutcome.PARTIAL);

        // R1-38: effectiveMax clamp classifies matchedCount==count with earned >= effectiveMax as CORRECT
        ItemGradeResult clampedCountTable = ItemGrader.grade(
                QuestionType.TRUE_FALSE, customTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true, "b", false, "c", true, "d", false)),
                new BigDecimal("4.0"), GradingPolicy.standard());
        assertThat(clampedCountTable.earnedPoints()).isEqualByComparingTo("4.0");
        assertThat(clampedCountTable.outcome()).isEqualTo(ItemOutcome.CORRECT);

        // R2-2: matchedCount < count with earned >= effectiveMax stays PARTIAL, never CORRECT
        ItemGradeResult clampedPartialGreater = ItemGrader.grade(
                QuestionType.TRUE_FALSE, customTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true, "b", false, "c", true)),
                new BigDecimal("2.0"), GradingPolicy.standard());
        assertThat(clampedPartialGreater.earnedPoints()).isEqualByComparingTo("2.0");
        assertThat(clampedPartialGreater.outcome()).isEqualTo(ItemOutcome.PARTIAL);

        ItemGradeResult clampedPartialEqual = ItemGrader.grade(
                QuestionType.TRUE_FALSE, customTableSnapshot,
                new AnswerValue.TrueFalse(Map.of("a", true, "b", false, "c", true)),
                new BigDecimal("3.0"), GradingPolicy.standard());
        assertThat(clampedPartialEqual.earnedPoints()).isEqualByComparingTo("3.0");
        assertThat(clampedPartialEqual.outcome()).isEqualTo(ItemOutcome.PARTIAL);

        // R1-38: missing scoreTable with statementCount != 4 fails fast
        JsonNode invalidCountTableSnapshot = MAPPER.readTree("""
                {
                  "type": "TRUE_FALSE",
                  "points": 1.0,
                  "body": {
                    "scoringMethod": "COUNT_TABLE",
                    "statements": [
                      {"id": "a", "content": "a", "answer": true},
                      {"id": "b", "content": "b", "answer": false},
                      {"id": "c", "content": "c", "answer": true}
                    ]
                  }
                }
                """);
        assertThatThrownBy(() -> ItemGrader.grade(
                        QuestionType.TRUE_FALSE, invalidCountTableSnapshot,
                        new AnswerValue.TrueFalse(Map.of("a", true)),
                        BigDecimal.ONE, GradingPolicy.standard()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scoreTable");
    }

    @Test
    void matchingRows() throws Exception {
        JsonNode noReuseSnapshot = MAPPER.readTree("""
                {
                  "type": "MATCHING",
                  "points": 2.0,
                  "body": {
                    "reuseRight": false,
                    "pairs": [
                      {"leftId": "l1", "rightId": "r1", "points": 1.0},
                      {"leftId": "l2", "rightId": "r2", "points": 1.0}
                    ]
                  }
                }
                """);

        ItemGradeResult partialNoReuse = ItemGrader.grade(
                QuestionType.MATCHING,
                noReuseSnapshot,
                new AnswerValue.Matching(Map.of("l1", "r1")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(partialNoReuse.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(partialNoReuse.earnedPoints()).isEqualByComparingTo("1.0");

        // Duplicate right rejected under reuseRight=false
        ItemGradeResult dupRight = ItemGrader.grade(
                QuestionType.MATCHING,
                noReuseSnapshot,
                new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r1")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(dupRight.earnedPoints()).isEqualByComparingTo("0");
        assertThat(dupRight.outcome()).isEqualTo(ItemOutcome.WRONG);

        JsonNode reuseSnapshot = MAPPER.readTree("""
                {
                  "type": "MATCHING",
                  "points": 2.0,
                  "body": {
                    "reuseRight": true,
                    "pairs": [
                      {"leftId": "l1", "rightId": "r1", "points": 1.0},
                      {"leftId": "l2", "rightId": "r1", "points": 1.0}
                    ]
                  }
                }
                """);
        ItemGradeResult reuseBoth = ItemGrader.grade(
                QuestionType.MATCHING,
                reuseSnapshot,
                new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r1")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(reuseBoth.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(reuseBoth.earnedPoints()).isEqualByComparingTo("2.0");

        // R1-53: stray/unknown leftId does not corrupt valid pairs or turn blank into WRONG
        ItemGradeResult onlyStray = ItemGrader.grade(
                QuestionType.MATCHING,
                noReuseSnapshot,
                new AnswerValue.Matching(Map.of("stray", "r1")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(onlyStray.outcome()).isEqualTo(ItemOutcome.BLANK);
        assertThat(onlyStray.earnedPoints()).isEqualByComparingTo("0");

        ItemGradeResult validWithStrayDuplicate = ItemGrader.grade(
                QuestionType.MATCHING,
                noReuseSnapshot,
                new AnswerValue.Matching(Map.of("l1", "r1", "stray", "r1")),
                new BigDecimal("2.0"),
                GradingPolicy.standard());
        assertThat(validWithStrayDuplicate.outcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(validWithStrayDuplicate.earnedPoints()).isEqualByComparingTo("1.0");

        // R1-38: clamp earned to effectiveMax when pair points sum above effectiveMax
        ItemGradeResult clampedMatching = ItemGrader.grade(
                QuestionType.MATCHING,
                noReuseSnapshot,
                new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r2")),
                new BigDecimal("1.5"),
                GradingPolicy.standard());
        assertThat(clampedMatching.outcome()).isEqualTo(ItemOutcome.CORRECT);
        assertThat(clampedMatching.earnedPoints()).isEqualByComparingTo("1.50");

        // R2-3: matchedCount < pairCount with earned >= effectiveMax stays PARTIAL, never CORRECT
        ItemGradeResult clampedPartialGreater = ItemGrader.grade(
                QuestionType.MATCHING,
                noReuseSnapshot,
                new AnswerValue.Matching(Map.of("l1", "r1")),
                new BigDecimal("0.8"),
                GradingPolicy.standard());
        assertThat(clampedPartialGreater.earnedPoints()).isEqualByComparingTo("0.80");
        assertThat(clampedPartialGreater.outcome()).isEqualTo(ItemOutcome.PARTIAL);

        ItemGradeResult clampedPartialEqual = ItemGrader.grade(
                QuestionType.MATCHING,
                noReuseSnapshot,
                new AnswerValue.Matching(Map.of("l1", "r1")),
                new BigDecimal("1.0"),
                GradingPolicy.standard());
        assertThat(clampedPartialEqual.earnedPoints()).isEqualByComparingTo("1.00");
        assertThat(clampedPartialEqual.outcome()).isEqualTo(ItemOutcome.PARTIAL);
    }

    @Test
    void essayRows() {
        for (String blankHtml : List.of(
                "",
                "   ",
                "<p> </p>",
                "<p>&nbsp;</p>",
                "<p><br></p>",
                "<div>\u00A0 </div>",
                "<p>&#8203;</p>",
                "<p>\uFEFF</p>",
                "<p>\u200C\u200D\u2060</p>")) {
            ItemGradeResult blank = ItemGrader.grade(
                    QuestionType.ESSAY,
                    MAPPER.createObjectNode(),
                    new AnswerValue.Essay(blankHtml, 0),
                    new BigDecimal("25.0"),
                    GradingPolicy.standard());
            assertThat(blank.outcome()).as("blank html: " + blankHtml).isEqualTo(ItemOutcome.BLANK);
            assertThat(blank.earnedPoints()).isEqualByComparingTo("0");
            assertThat(blank.pendingReview()).isFalse();
        }

        ItemGradeResult nullAnswer = ItemGrader.grade(
                QuestionType.ESSAY,
                MAPPER.createObjectNode(),
                null,
                new BigDecimal("25.0"),
                GradingPolicy.standard());
        assertThat(nullAnswer.outcome()).isEqualTo(ItemOutcome.BLANK);
        assertThat(nullAnswer.pendingReview()).isFalse();

        ItemGradeResult nonBlank = ItemGrader.grade(
                QuestionType.ESSAY,
                MAPPER.createObjectNode(),
                new AnswerValue.Essay("<p>Bài viết nghị luận</p>", 4),
                new BigDecimal("25.0"),
                GradingPolicy.standard());
        assertThat(nonBlank.outcome()).isEqualTo(ItemOutcome.PENDING_REVIEW);
        assertThat(nonBlank.earnedPoints()).isNull();
        assertThat(nonBlank.pendingReview()).isTrue();
    }

    @Test
    void pureFunction() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "MULTIPLE_CHOICE",
                  "points": 3.0,
                  "body": {
                    "scoringRule": "PARTIAL",
                    "options": [
                      {"id": "a", "correct": true},
                      {"id": "b", "correct": true},
                      {"id": "c", "correct": false}
                    ]
                  }
                }
                """);
        String beforeJson = snapshot.toString();
        AnswerValue answer = new AnswerValue.MultipleChoice(List.of("a"));

        ItemGradeResult first = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE, snapshot, answer, new BigDecimal("3.0"), GradingPolicy.standard());
        ItemGradeResult second = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE, snapshot, answer, new BigDecimal("3.0"), GradingPolicy.standard());

        assertThat(first).isEqualTo(second);
        assertThat(snapshot.toString()).isEqualTo(beforeJson);
    }

    @Test
    void nonHscaKindsKeepProportionalPartial() throws Exception {
        JsonNode snapshot = MAPPER.readTree("""
                {
                  "type": "MULTIPLE_CHOICE",
                  "points": 2.0,
                  "body": {
                    "scoringRule": "PARTIAL",
                    "options": [
                      {"id": "a", "correct": true},
                      {"id": "b", "correct": true},
                      {"id": "c", "correct": false}
                    ]
                  }
                }
                """);
        for (String kind : List.of("HSA", "QDA", "VACT", "BCA", "SPT", "SP2E", "THPTQG")) {
            GradingPolicy policy = GradingPolicies.forKind(kind);
            assertThat(policy.multipleChoicePartialMode())
                    .as("kind " + kind)
                    .isEqualTo(GradingPolicy.MultipleChoicePartialMode.PROPORTIONAL);
            ItemGradeResult res = ItemGrader.grade(
                    QuestionType.MULTIPLE_CHOICE,
                    snapshot,
                    new AnswerValue.MultipleChoice(List.of("a")),
                    new BigDecimal("2.0"),
                    policy);
            assertThat(res.outcome()).isEqualTo(ItemOutcome.PARTIAL);
            assertThat(res.earnedPoints()).isEqualByComparingTo("1.0");
        }
    }
}
