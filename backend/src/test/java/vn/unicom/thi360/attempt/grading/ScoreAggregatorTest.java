package vn.unicom.thi360.attempt.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.AggregatedScore;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.ItemInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.PartInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.SectionInput;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ScoreAggregatorTest {

    @Test
    void setRowNotCountedTwice() {
        UUID partId = UUID.randomUUID();
        UUID setItemId = UUID.randomUUID();

        PartInput part = new PartInput(partId, "P1", "Part 1", 1, PartState.CLOSED, new BigDecimal("2.0"), 2);
        ItemInput parent = new ItemInput(
                setItemId, partId, null, null, "sec-1", "TOAN",
                QuestionType.QUESTION_SET, 0, true, new BigDecimal("2.0"),
                new BigDecimal("2.0"), ItemOutcome.CORRECT, Map.of("exactFraction", "2"));
        ItemInput child1 = new ItemInput(
                UUID.randomUUID(), partId, setItemId, null, "sec-1", "TOAN",
                QuestionType.SINGLE_CHOICE, 1, true, BigDecimal.ONE,
                BigDecimal.ONE, ItemOutcome.CORRECT, Map.of("exactFraction", "1"));
        ItemInput child2 = new ItemInput(
                UUID.randomUUID(), partId, setItemId, null, "sec-1", "TOAN",
                QuestionType.SINGLE_CHOICE, 1, true, BigDecimal.ONE,
                BigDecimal.ZERO, ItemOutcome.WRONG, Map.of("exactFraction", "0"));

        AggregatedScore res = ScoreAggregator.aggregate(
                new ScoreAggregator.Input("HSA", "RAW", null, List.of(part), List.of(parent, child1, child2), List.of()));

        assertThat(res.rawScore()).isEqualByComparingTo("1.00");
        assertThat(res.rawMaxScore()).isEqualByComparingTo("2.00");
        assertThat(res.statistics().correctCount()).isEqualTo(1);
        assertThat(res.statistics().wrongCount()).isEqualTo(1);
        assertThat(res.statistics().totalCount()).isEqualTo(2);
    }

    @Test
    void statisticsPerUnit() {
        UUID partId = UUID.randomUUID();
        PartInput part = new PartInput(partId, "P1", "Part 1", 1, PartState.CLOSED, new BigDecimal("10"), 7);

        ItemInput sc = item(partId, null, QuestionType.SINGLE_CHOICE, 1, BigDecimal.ONE, BigDecimal.ONE, ItemOutcome.CORRECT, "1", null);
        // TRUE_FALSE must count as 1 unit regardless of 4 statements
        ItemInput tf = item(partId, null, QuestionType.TRUE_FALSE, 1, BigDecimal.ONE, new BigDecimal("0.25"), ItemOutcome.PARTIAL, "1/4", null);
        // CLOZE with 3 blanks counts as 3 independent units
        ItemInput cloze = item(
                partId, null, QuestionType.CLOZE, 3, new BigDecimal("3"), new BigDecimal("1"),
                ItemOutcome.PARTIAL, "1",
                Map.of(
                        "exactFraction", "1",
                        "blankResults", List.of(
                                Map.of("id", "b1", "outcome", "CORRECT"),
                                Map.of("id", "b2", "outcome", "WRONG"),
                                Map.of("id", "b3", "outcome", "BLANK"))));
        ItemInput essay = item(partId, null, QuestionType.ESSAY, 1, new BigDecimal("5"), null, ItemOutcome.PENDING_REVIEW, null, null);

        AggregatedScore res = ScoreAggregator.aggregate(
                new ScoreAggregator.Input("HSA", "RAW", null, List.of(part), List.of(sc, tf, cloze, essay), List.of()));

        assertThat(res.pendingReview()).isTrue();
        assertThat(res.rawScore()).isNull();
        assertThat(res.displayScore()).isNull();
        assertThat(res.statistics().correctCount()).isEqualTo(2);
        assertThat(res.statistics().partialCount()).isEqualTo(1);
        assertThat(res.statistics().wrongCount()).isEqualTo(1);
        assertThat(res.statistics().blankCount()).isEqualTo(1);
        assertThat(res.statistics().pendingReviewCount()).isEqualTo(1);
        assertThat(res.statistics().totalCount()).isEqualTo(6);
    }

    @Test
    void rawRoundsOnceHalfUp() {
        UUID partId = UUID.randomUUID();
        PartInput part = new PartInput(partId, "P1", "Part 1", 1, PartState.CLOSED, new BigDecimal("3.0"), 3);

        // 3 items * 1/3 = 1.00 exactly without 0.33 * 3 = 0.99 truncation drift
        List<ItemInput> thirds = List.of(
                item(partId, null, QuestionType.MULTIPLE_CHOICE, 1, BigDecimal.ONE, new BigDecimal("0.3333"), ItemOutcome.PARTIAL, "1/3", null),
                item(partId, null, QuestionType.MULTIPLE_CHOICE, 1, BigDecimal.ONE, new BigDecimal("0.3333"), ItemOutcome.PARTIAL, "1/3", null),
                item(partId, null, QuestionType.MULTIPLE_CHOICE, 1, BigDecimal.ONE, new BigDecimal("0.3333"), ItemOutcome.PARTIAL, "1/3", null));
        AggregatedScore sumOfThirds = ScoreAggregator.aggregate(
                new ScoreAggregator.Input("HSA", "RAW", null, List.of(part), thirds, List.of()));
        assertThat(sumOfThirds.rawScore()).isEqualByComparingTo("1.00");

        // 0.125 -> 0.13 (HALF_UP)
        AggregatedScore halfUp0125 = ScoreAggregator.aggregate(
                new ScoreAggregator.Input("HSA", "RAW", null, List.of(part),
                        List.of(item(partId, null, QuestionType.MULTIPLE_CHOICE, 1, BigDecimal.ONE, new BigDecimal("0.125"), ItemOutcome.PARTIAL, "1/8", null)),
                        List.of()));
        assertThat(halfUp0125.rawScore()).isEqualByComparingTo("0.13");

        // 0.4449 -> 0.44 (not double-rounded 0.4449 -> 0.445 -> 0.45)
        AggregatedScore noDoubleRounding = ScoreAggregator.aggregate(
                new ScoreAggregator.Input("HSA", "RAW", null, List.of(part),
                        List.of(item(partId, null, QuestionType.MULTIPLE_CHOICE, 1, BigDecimal.ONE, new BigDecimal("0.4449"), ItemOutcome.PARTIAL, "4449/10000", null)),
                        List.of()));
        assertThat(noDoubleRounding.rawScore()).isEqualByComparingTo("0.44");
    }

    @Test
    void linearUsesPathScale() {
        UUID partId = UUID.randomUUID();
        record Case(String refMax, int rawEarned, int rawTotal, String expectedDisplay) {}
        for (Case c : List.of(
                new Case("1200", 40, 120, "400.00"),
                new Case("600", 20, 60, "200.00"),
                new Case("300", 10, 30, "100.00"),
                new Case("180", 6, 18, "60.00"),
                new Case("120", 4, 12, "40.00"))) {
            PartInput part = new PartInput(partId, "P1", "Part 1", 1, PartState.CLOSED, new BigDecimal(c.rawTotal()), c.rawTotal());
            List<ItemInput> items = new ArrayList<>();
            for (int i = 0; i < c.rawTotal(); i++) {
                boolean right = i < c.rawEarned();
                items.add(item(
                        partId,
                        "TOAN",
                        QuestionType.SINGLE_CHOICE,
                        1,
                        BigDecimal.ONE,
                        right ? BigDecimal.ONE : BigDecimal.ZERO,
                        right ? ItemOutcome.CORRECT : ItemOutcome.WRONG,
                        right ? "1" : "0",
                        null));
            }
            AggregatedScore res = ScoreAggregator.aggregate(
                    new ScoreAggregator.Input("VACT", "LINEAR", new BigDecimal(c.refMax()), List.of(part), items, List.of()));
            assertThat(res.rawScore()).isEqualByComparingTo(BigDecimal.valueOf(c.rawEarned()));
            assertThat(res.rawMaxScore()).isEqualByComparingTo(BigDecimal.valueOf(c.rawTotal()));
            assertThat(res.scaleMax()).isEqualByComparingTo(c.refMax());
            assertThat(res.displayScore()).isEqualByComparingTo(c.expectedDisplay());
        }
    }

    @Test
    void linearWithoutReferenceMax() {
        UUID partId = UUID.randomUUID();
        PartInput part = new PartInput(partId, "P1", "VACT Review", 1, PartState.CLOSED, new BigDecimal("24"), 24);
        ItemInput i1 = item(partId, "TOAN", QuestionType.SINGLE_CHOICE, 1, new BigDecimal("12"), new BigDecimal("12"), ItemOutcome.CORRECT, "12", null);
        ItemInput i2 = item(partId, "TOAN", QuestionType.SINGLE_CHOICE, 1, new BigDecimal("12"), new BigDecimal("6"), ItemOutcome.PARTIAL, "6", null);

        AggregatedScore res = ScoreAggregator.aggregate(
                new ScoreAggregator.Input("VACT", "LINEAR", null, List.of(part), List.of(i1, i2), List.of()));
        assertThat(res.rawMaxScore()).isEqualByComparingTo("24.00");
        assertThat(res.scaleMax()).isEqualByComparingTo("24.00");
        assertThat(res.displayScore()).isEqualByComparingTo("18.00");
    }

    @Test
    void vactScientificThinkingSubsectionsGroupedBySectionId() {
        UUID partId = UUID.randomUUID();
        PartInput part = new PartInput(partId, "P3", "Tư duy khoa học", 3, PartState.CLOSED, new BigDecimal("30"), 30);
        List<ItemInput> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            boolean right = i < 9;
            items.add(new ItemInput(
                    UUID.randomUUID(), partId, null, null, "sec-logic", "SCIENTIFIC_THINKING",
                    QuestionType.SINGLE_CHOICE, 1, true, BigDecimal.ONE,
                    right ? BigDecimal.ONE : BigDecimal.ZERO,
                    right ? ItemOutcome.CORRECT : ItemOutcome.WRONG,
                    Map.of("exactFraction", right ? "1" : "0")));
        }
        for (int i = 0; i < 18; i++) {
            boolean right = i < 11;
            items.add(new ItemInput(
                    UUID.randomUUID(), partId, null, null, "sec-reasoning", "SCIENTIFIC_THINKING",
                    QuestionType.SINGLE_CHOICE, 1, true, BigDecimal.ONE,
                    right ? BigDecimal.ONE : BigDecimal.ZERO,
                    right ? ItemOutcome.CORRECT : ItemOutcome.WRONG,
                    Map.of("exactFraction", right ? "1" : "0")));
        }
        List<SectionInput> sections = List.of(
                new SectionInput("sec-logic", "SCIENTIFIC_THINKING", 12),
                new SectionInput("sec-reasoning", "SCIENTIFIC_THINKING", 18));

        AggregatedScore res = ScoreAggregator.aggregate(
                new ScoreAggregator.Input("VACT", "LINEAR", new BigDecimal("300"), List.of(part), items, sections));
        assertThat(res.vactComponents()).hasSize(1);
        var sci = res.vactComponents().getFirst();
        assertThat(sci.componentCode()).isEqualTo("SCIENTIFIC_THINKING");
        assertThat(sci.correctCount()).isEqualTo(20);
        assertThat(sci.totalCount()).isEqualTo(30);
        assertThat(sci.subsections()).hasSize(2);
        assertThat(sci.subsections().get(0).sectionId()).isEqualTo("sec-logic");
        assertThat(sci.subsections().get(0).questionCount()).isEqualTo(12);
        assertThat(sci.subsections().get(0).correctCount()).isEqualTo(9);
        assertThat(sci.subsections().get(1).sectionId()).isEqualTo("sec-reasoning");
        assertThat(sci.subsections().get(1).questionCount()).isEqualTo(18);
        assertThat(sci.subsections().get(1).correctCount()).isEqualTo(11);
    }

    private static ItemInput item(
            UUID partId,
            String componentCode,
            QuestionType type,
            int unitCount,
            BigDecimal max,
            BigDecimal earned,
            ItemOutcome outcome,
            String exactFraction,
            Map<String, Object> explicitDetail) {
        Map<String, Object> detail = explicitDetail != null
                ? explicitDetail
                : (exactFraction != null ? Map.of("exactFraction", exactFraction) : Map.of());
        return new ItemInput(
                UUID.randomUUID(),
                partId,
                null,
                null,
                "sec-1",
                componentCode,
                type,
                unitCount,
                true,
                max,
                earned,
                outcome,
                detail);
    }
}
