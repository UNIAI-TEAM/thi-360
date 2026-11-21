package vn.unicom.thi360.attempt.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.SegmentResultView;
import vn.unicom.thi360.attempt.application.AttemptSegmentResultBuilder;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Segment;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Pure unit tests for {@link AttemptSegmentResultBuilder} (BS §17.3, AC-6, AC-7).
 */
class AttemptSegmentResultBuilderTest {

    private static final Instant NOW = Instant.parse("2026-11-22T01:00:00Z");

    private final AttemptSegmentResultBuilder builder = new AttemptSegmentResultBuilder();

    @Test
    void returnsNullWhenPublishedVersionEmptyOrNoDisplaySegments() {
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        AttemptPart part = closedPart(partId, attemptId, "p1", "Phần 1", 0, new BigDecimal("10.00"), 2);
        AttemptItem item = gradedItem(attemptId, partId, null, 1, QuestionType.SINGLE_CHOICE, 1,
                new BigDecimal("5.00"), new BigDecimal("5.00"), ItemOutcome.CORRECT, Map.of("exactFraction", "5"));

        assertThat(builder.build(Optional.empty(), List.of(part), List.of(item))).isNull();
        assertThat(builder.build(Optional.of(versionWithParts(List.of())), List.of(part), List.of(item))).isNull();
        assertThat(builder.build(Optional.of(versionWithParts(List.of(partWithSegments("p1", List.of())))),
                List.of(part), List.of(item))).isNull();
    }

    @Test
    void splitQuestionSetClusterAndClozeInsideSegmentCountStrictlyByPosition() {
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        UUID setParentId = UUID.randomUUID();
        UUID outOfScopeParentId = UUID.randomUUID();
        AttemptPart part = closedPart(partId, attemptId, "p-vsat-anh", "Tiếng Anh", 0, new BigDecimal("150.00"), 25);

        List<AttemptItem> items = new ArrayList<>();
        int ord = 1;

        // Positions 1..7: 7 standalone TRUE_FALSE items (pos 1 gets 6, pos 2 gets 2, pos 3..7 blank)
        for (int i = 1; i <= 7; i++) {
            BigDecimal earned = switch (i) {
                case 1 -> new BigDecimal("6.0000");
                case 2 -> new BigDecimal("2.0000");
                default -> BigDecimal.ZERO;
            };
            ItemOutcome outcome = switch (i) {
                case 1 -> ItemOutcome.CORRECT;
                case 2 -> ItemOutcome.PARTIAL;
                default -> ItemOutcome.BLANK;
            };
            items.add(gradedItem(attemptId, partId, null, ord++, QuestionType.TRUE_FALSE, 1,
                    new BigDecimal("6.00"), earned, outcome, Map.of("exactFraction", i == 1 ? "6" : (i == 2 ? "2" : "0"))));
        }

        // In-between out-of-scope parent + child must NOT affect positions!
        items.add(outOfScopeItem(attemptId, partId, outOfScopeParentId, null, ord++, QuestionType.QUESTION_SET));
        items.add(gradedItem(attemptId, partId, outOfScopeParentId, ord++, QuestionType.SINGLE_CHOICE, 1,
                new BigDecimal("6.00"), new BigDecimal("6.00"), ItemOutcome.CORRECT, Map.of()));

        // Shared cluster (positions 8..15): container row + 2 TF children (8..9) + 6 SC children (10..15)
        items.add(gradedItem(attemptId, partId, setParentId, null, ord++, QuestionType.QUESTION_SET, 0,
                new BigDecimal("48.00"), new BigDecimal("27.00"), ItemOutcome.PARTIAL, Map.of()));
        // pos 8 -> +3 PARTIAL
        items.add(gradedItem(attemptId, partId, setParentId, ord++, QuestionType.TRUE_FALSE, 1,
                new BigDecimal("6.00"), new BigDecimal("3.0000"), ItemOutcome.PARTIAL, Map.of("exactFraction", "3")));
        // pos 9 -> +6 CORRECT
        items.add(gradedItem(attemptId, partId, setParentId, ord++, QuestionType.TRUE_FALSE, 1,
                new BigDecimal("6.00"), new BigDecimal("6.0000"), ItemOutcome.CORRECT, Map.of("exactFraction", "6")));
        // pos 10..15 -> 3 CORRECT (+18), 1 WRONG (0), 2 BLANK (0)
        for (int c = 0; c < 6; c++) {
            boolean right = c < 3;
            ItemOutcome out = right ? ItemOutcome.CORRECT : (c == 3 ? ItemOutcome.WRONG : ItemOutcome.BLANK);
            items.add(gradedItem(attemptId, partId, setParentId, ord++, QuestionType.SINGLE_CHOICE, 1,
                    new BigDecimal("6.00"), right ? new BigDecimal("6.0000") : BigDecimal.ZERO, out,
                    Map.of("exactFraction", right ? "6" : "0")));
        }

        // Positions 16..20: 5 MATCHING (pos 16 -> 4.5 PARTIAL, pos 17 -> 6 CORRECT, rest BLANK)
        for (int m = 0; m < 5; m++) {
            BigDecimal earned = m == 0 ? new BigDecimal("4.5000") : (m == 1 ? new BigDecimal("6.0000") : BigDecimal.ZERO);
            ItemOutcome out = m == 0 ? ItemOutcome.PARTIAL : (m == 1 ? ItemOutcome.CORRECT : ItemOutcome.BLANK);
            String frac = m == 0 ? "9/2" : (m == 1 ? "6" : "0");
            items.add(gradedItem(attemptId, partId, null, ord++, QuestionType.MATCHING, 1,
                    new BigDecimal("6.00"), earned, out, Map.of("exactFraction", frac)));
        }

        // Positions 21..25: 1 CLOZE with 5 blanks (3 CORRECT = 18, 1 WRONG, 1 BLANK)
        items.add(gradedItem(attemptId, partId, null, ord++, QuestionType.CLOZE, 5,
                new BigDecimal("30.00"), new BigDecimal("18.0000"), ItemOutcome.PARTIAL,
                Map.of(
                        "exactFraction", "18",
                        "blankResults", List.of(
                                Map.of("id", "a", "outcome", "CORRECT", "pointsEarned", 6.0, "pointsMax", 6.0, "exactFraction", "6"),
                                Map.of("id", "b", "outcome", "CORRECT", "pointsEarned", 6.0, "pointsMax", 6.0, "exactFraction", "6"),
                                Map.of("id", "c", "outcome", "WRONG", "pointsEarned", 0.0, "pointsMax", 6.0, "exactFraction", "0"),
                                Map.of("id", "d", "outcome", "CORRECT", "pointsEarned", 6.0, "pointsMax", 6.0, "exactFraction", "6"),
                                Map.of("id", "e", "outcome", "BLANK", "pointsEarned", 0.0, "pointsMax", 6.0, "exactFraction", "0")))));

        // Unsorted segments in snapshot to verify defensive sorting by fromPosition
        PublishedVersion version = versionWithParts(List.of(partWithSegments("p-vsat-anh", List.of(
                new Segment("P4", "Phần IV", 21, 25, 5, new BigDecimal("30")),
                new Segment("P2", "Phần II", 10, 15, 6, new BigDecimal("36")),
                new Segment("P1", "Phần I", 1, 9, 36, new BigDecimal("54")),
                new Segment("P3", "Phần III", 16, 20, 20, new BigDecimal("30"))))));

        List<SegmentResultView> segments = builder.build(Optional.of(version), List.of(part), items);
        assertThat(segments).hasSize(4);

        SegmentResultView p1 = segments.get(0);
        assertThat(p1.code()).isEqualTo("P1");
        assertThat(p1.name()).isEqualTo("Phần I");
        assertThat(p1.partCode()).isEqualTo("p-vsat-anh");
        assertThat(p1.fromPosition()).isEqualTo(1);
        assertThat(p1.toPosition()).isEqualTo(9);
        assertThat(p1.pendingReview()).isFalse();
        assertThat(p1.score()).isEqualByComparingTo("17.00"); // 6 + 2 + 3 + 6
        assertThat(p1.maxPoints()).isEqualByComparingTo("54.00");
        assertThat(p1.stats().correctCount()).isEqualTo(2);
        assertThat(p1.stats().partialCount()).isEqualTo(2);
        assertThat(p1.stats().blankCount()).isEqualTo(5);
        assertThat(p1.stats().totalCount()).isEqualTo(9);

        SegmentResultView p2 = segments.get(1);
        assertThat(p2.code()).isEqualTo("P2");
        assertThat(p2.score()).isEqualByComparingTo("18.00"); // 3 * 6
        assertThat(p2.maxPoints()).isEqualByComparingTo("36.00");
        assertThat(p2.stats().correctCount()).isEqualTo(3);
        assertThat(p2.stats().wrongCount()).isEqualTo(1);
        assertThat(p2.stats().blankCount()).isEqualTo(2);
        assertThat(p2.stats().totalCount()).isEqualTo(6);

        SegmentResultView p3 = segments.get(2);
        assertThat(p3.code()).isEqualTo("P3");
        assertThat(p3.score()).isEqualByComparingTo("10.50"); // 4.5 + 6
        assertThat(p3.maxPoints()).isEqualByComparingTo("30.00");
        assertThat(p3.stats().correctCount()).isEqualTo(1);
        assertThat(p3.stats().partialCount()).isEqualTo(1);
        assertThat(p3.stats().blankCount()).isEqualTo(3);

        SegmentResultView p4 = segments.get(3);
        assertThat(p4.code()).isEqualTo("P4");
        assertThat(p4.score()).isEqualByComparingTo("18.00");
        assertThat(p4.maxPoints()).isEqualByComparingTo("30.00");
        assertThat(p4.stats().correctCount()).isEqualTo(3);
        assertThat(p4.stats().wrongCount()).isEqualTo(1);
        assertThat(p4.stats().blankCount()).isEqualTo(1);
        assertThat(p4.stats().totalCount()).isEqualTo(5);
    }

    @Test
    void straddlingClozeAttributesEachBlankAccurately() {
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        AttemptPart part = closedPart(partId, attemptId, "p1", "Part 1", 0, new BigDecimal("5.00"), 5);

        AttemptItem sc = gradedItem(attemptId, partId, null, 1, QuestionType.SINGLE_CHOICE, 1,
                BigDecimal.ONE, BigDecimal.ONE, ItemOutcome.CORRECT, Map.of("exactFraction", "1"));
        // 3-blank CLOZE at positions 2, 3, 4 straddling segment 1 (1..2) and segment 2 (3..5)
        AttemptItem cloze = gradedItem(attemptId, partId, null, 2, QuestionType.CLOZE, 3,
                new BigDecimal("3.00"), new BigDecimal("1.5000"), ItemOutcome.PARTIAL,
                Map.of(
                        "exactFraction", "3/2",
                        "blankResults", List.of(
                                Map.of("id", "b1", "outcome", "CORRECT", "pointsEarned", 1.0, "pointsMax", 1.0, "exactFraction", "1"),
                                Map.of("id", "b2", "outcome", "PARTIAL", "pointsEarned", 0.5, "pointsMax", 1.0, "exactFraction", "1/2"),
                                Map.of("id", "b3", "outcome", "WRONG", "pointsEarned", 0.0, "pointsMax", 1.0, "exactFraction", "0"))));
        AttemptItem scEnd = gradedItem(attemptId, partId, null, 3, QuestionType.SINGLE_CHOICE, 1,
                BigDecimal.ONE, BigDecimal.ZERO, ItemOutcome.WRONG, Map.of("exactFraction", "0"));

        // Leave segment maxPoints null so fallback sums item/blank pointsMax
        PublishedVersion version = versionWithParts(List.of(partWithSegments("p1", List.of(
                new Segment("S1", "Seg 1", 1, 2, null, null),
                new Segment("S2", "Seg 2", 3, 5, null, null)))));

        List<SegmentResultView> segments = builder.build(Optional.of(version), List.of(part), List.of(sc, cloze, scEnd));
        assertThat(segments).hasSize(2);

        assertThat(segments.get(0).score()).isEqualByComparingTo("2.00"); // sc(1) + b1(1)
        assertThat(segments.get(0).maxPoints()).isEqualByComparingTo("2.00");
        assertThat(segments.get(0).stats().correctCount()).isEqualTo(2);
        assertThat(segments.get(0).stats().totalCount()).isEqualTo(2);

        assertThat(segments.get(1).score()).isEqualByComparingTo("0.50"); // b2(0.5) + b3(0) + scEnd(0)
        assertThat(segments.get(1).maxPoints()).isEqualByComparingTo("3.00");
        assertThat(segments.get(1).stats().partialCount()).isEqualTo(1);
        assertThat(segments.get(1).stats().wrongCount()).isEqualTo(2);
        assertThat(segments.get(1).stats().totalCount()).isEqualTo(3);
    }

    @Test
    void multiPartLocalPositionsPendingReviewAndEighthPointSumming() {
        UUID attemptId = UUID.randomUUID();
        UUID part1Id = UUID.randomUUID();
        UUID part2Id = UUID.randomUUID();
        UUID part3Id = UUID.randomUUID();

        AttemptPart part1 = closedPart(part1Id, attemptId, "P1", "Part 1", 0, new BigDecimal("0.75"), 3);
        AttemptPart part2 = closedPart(part2Id, attemptId, "P2", "Part 2", 1, new BigDecimal("11.00"), 2);
        AttemptPart part3 = AttemptPart.createPending(
                part3Id, attemptId, "P3", "Part 3", 2, 10, 4, new BigDecimal("4.00"), 1L);
        part3.skip(PartCloseReason.TIME_UP, NOW);

        // Part 1: three 0.125 MC items without exactFraction (relying on persisted 0.1250 BigDecimal)
        // Seg A (1..1): 0.125 -> 0.13; Seg B (2..3): 0.125 + 0.125 = 0.25
        AttemptItem p1i1 = gradedItem(attemptId, part1Id, null, 1, QuestionType.MULTIPLE_CHOICE, 1,
                new BigDecimal("0.25"), new BigDecimal("0.1250"), ItemOutcome.PARTIAL, Map.of());
        AttemptItem p1i2 = gradedItem(attemptId, part1Id, null, 2, QuestionType.MULTIPLE_CHOICE, 1,
                new BigDecimal("0.25"), new BigDecimal("0.1250"), ItemOutcome.PARTIAL, Map.of());
        AttemptItem p1i3 = gradedItem(attemptId, part1Id, null, 3, QuestionType.MULTIPLE_CHOICE, 1,
                new BigDecimal("0.25"), new BigDecimal("0.1250"), ItemOutcome.PARTIAL, Map.of());

        // Part 2 uses part-local positions 1..2 again: pos 1 SC (1.0), pos 2 ESSAY (PENDING_REVIEW)
        AttemptItem p2i1 = gradedItem(attemptId, part2Id, null, 1, QuestionType.SINGLE_CHOICE, 1,
                BigDecimal.ONE, BigDecimal.ONE, ItemOutcome.CORRECT, Map.of("exactFraction", "1"));
        AttemptItem p2i2 = gradedItem(attemptId, part2Id, null, 2, QuestionType.ESSAY, 1,
                new BigDecimal("10.00"), null, ItemOutcome.PENDING_REVIEW, Map.of());

        PublishedVersion version = versionWithParts(List.of(
                partWithSegments("P1", List.of(
                        new Segment("P1A", "P1 A", 1, 1, 1, new BigDecimal("0.25")),
                        new Segment("P1B", "P1 B", 2, 3, 2, new BigDecimal("0.50")))),
                partWithSegments("P2", List.of(
                        new Segment("P2A", "P2 Auto", 1, 1, 1, BigDecimal.ONE),
                        new Segment("P2B", "P2 Essay", 2, 2, 1, BigDecimal.TEN))),
                partWithSegments("P3", List.of(
                        new Segment("P3A", "P3 Skipped", 1, 4, 4, new BigDecimal("4.00"))))));

        List<SegmentResultView> segments = builder.build(
                Optional.of(version),
                List.of(part3, part1, part2), // deliberately unsorted order
                List.of(p1i1, p1i2, p1i3, p2i1, p2i2));

        assertThat(segments).hasSize(5);

        assertThat(segments.get(0).code()).isEqualTo("P1A");
        assertThat(segments.get(0).score()).isEqualByComparingTo("0.13");
        assertThat(segments.get(0).maxPoints()).isEqualByComparingTo("0.25");

        assertThat(segments.get(1).code()).isEqualTo("P1B");
        assertThat(segments.get(1).score()).isEqualByComparingTo("0.25");
        assertThat(segments.get(1).maxPoints()).isEqualByComparingTo("0.50");

        assertThat(segments.get(2).code()).isEqualTo("P2A");
        assertThat(segments.get(2).pendingReview()).isFalse();
        assertThat(segments.get(2).score()).isEqualByComparingTo("1.00");

        assertThat(segments.get(3).code()).isEqualTo("P2B");
        assertThat(segments.get(3).pendingReview()).isTrue();
        assertThat(segments.get(3).score()).isNull();
        assertThat(segments.get(3).stats().pendingReviewCount()).isEqualTo(1);

        assertThat(segments.get(4).code()).isEqualTo("P3A");
        assertThat(segments.get(4).pendingReview()).isFalse();
        assertThat(segments.get(4).score()).isEqualByComparingTo("0.00");
        assertThat(segments.get(4).maxPoints()).isEqualByComparingTo("4.00");
        assertThat(segments.get(4).stats().blankCount()).isEqualTo(4);
        assertThat(segments.get(4).stats().totalCount()).isEqualTo(4);
    }

    @Test
    void clozeFallbackAndSparseBlankResultsRecoverCleanly() {
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        AttemptPart part = closedPart(partId, attemptId, "p1", "Part 1", 0, new BigDecimal("17.00"), 9);

        // 1. Empty blankResults -> uniform split across straddling blanks
        AttemptItem emptyEntriesCloze = gradedItem(attemptId, partId, null, 1, QuestionType.CLOZE, 3,
                new BigDecimal("6.00"), new BigDecimal("3.0000"), ItemOutcome.PARTIAL,
                Map.of("exactFraction", "3", "blankResults", List.of()));

        // 2. Sparse blankResults (3 entries for unitCount = 4), exercising:
        //    - entry 0: null pointsMax (falls back to 8/4 = 2.00), malformed exactFraction ("bad/frac" -> 1.5),
        //      and unrecognized outcome string ("UNKNOWN_STATUS" -> item outcome PARTIAL)
        //    - entry 1: valid CORRECT entry ("3/2" = 1.5)
        //    - entry 2: PENDING_REVIEW blank outcome (segment pendingReview=true, score=null)
        //    - index 3 (pos 7): missing from sparse blankResults -> BLANK with 0 earned (never double-counts)
        Map<String, Object> nullMaxMalformedEntry = new HashMap<>();
        nullMaxMalformedEntry.put("id", "b1");
        nullMaxMalformedEntry.put("outcome", "UNKNOWN_STATUS");
        nullMaxMalformedEntry.put("pointsEarned", 1.5);
        nullMaxMalformedEntry.put("pointsMax", null);
        nullMaxMalformedEntry.put("exactFraction", "bad/frac");

        AttemptItem sparseCloze = gradedItem(attemptId, partId, null, 2, QuestionType.CLOZE, 4,
                new BigDecimal("8.00"), new BigDecimal("3.0000"), ItemOutcome.PARTIAL,
                Map.of(
                        "exactFraction", "3",
                        "blankResults", List.of(
                                nullMaxMalformedEntry,
                                Map.of("id", "b2", "outcome", "CORRECT", "pointsEarned", 1.5, "pointsMax", 2.0, "exactFraction", "3/2"),
                                Map.of("id", "b3", "outcome", "PENDING_REVIEW", "pointsEarned", 0.0, "pointsMax", 2.0, "exactFraction", "0"))));

        // 3. Empty/absent blankResults with item outcome PENDING_REVIEW -> marks straddling segments pending
        AttemptItem pendingCloze = gradedItem(attemptId, partId, null, 3, QuestionType.CLOZE, 2,
                new BigDecimal("3.00"), null, ItemOutcome.PENDING_REVIEW, Map.of());

        PublishedVersion version = versionWithParts(List.of(partWithSegments("p1", List.of(
                new Segment("EMPTY_HEAD", "Empty Head", 1, 1, null, null),
                new Segment("EMPTY_TAIL", "Empty Tail", 2, 3, null, null),
                new Segment("SPARSE_HEAD", "Sparse Head", 4, 5, null, null),
                new Segment("SPARSE_PEND", "Sparse Pending", 6, 6, null, null),
                new Segment("SPARSE_TAIL", "Sparse Tail", 7, 7, null, null),
                new Segment("ITEM_PEND", "Item Pending", 8, 8, null, null),
                new Segment("ITEM_PEND_TAIL", "Item Pending Tail", 9, 9, null, null)))));

        List<SegmentResultView> segments = builder.build(
                Optional.of(version), List.of(part), List.of(emptyEntriesCloze, sparseCloze, pendingCloze));
        assertThat(segments).hasSize(7);

        // Empty blankResults uniform split: 3/3 = 1.00 per blank, max 6/3 = 2.00 per blank
        assertThat(segments.get(0).code()).isEqualTo("EMPTY_HEAD");
        assertThat(segments.get(0).pendingReview()).isFalse();
        assertThat(segments.get(0).score()).isEqualByComparingTo("1.00");
        assertThat(segments.get(0).maxPoints()).isEqualByComparingTo("2.00");
        assertThat(segments.get(0).stats().partialCount()).isEqualTo(1);
        assertThat(segments.get(0).stats().totalCount()).isEqualTo(1);

        assertThat(segments.get(1).code()).isEqualTo("EMPTY_TAIL");
        assertThat(segments.get(1).score()).isEqualByComparingTo("2.00");
        assertThat(segments.get(1).maxPoints()).isEqualByComparingTo("4.00");
        assertThat(segments.get(1).stats().partialCount()).isEqualTo(2);
        assertThat(segments.get(1).stats().totalCount()).isEqualTo(2);

        // Sparse head (pos 4..5): null pointsMax fallback (2.00) + entry 1 max (2.00) = 4.00;
        // malformed exactFraction fallback (1.5) + "3/2" (1.5) = 3.00; unrecognized outcome -> PARTIAL
        assertThat(segments.get(2).code()).isEqualTo("SPARSE_HEAD");
        assertThat(segments.get(2).pendingReview()).isFalse();
        assertThat(segments.get(2).score()).isEqualByComparingTo("3.00");
        assertThat(segments.get(2).maxPoints()).isEqualByComparingTo("4.00");
        assertThat(segments.get(2).stats().partialCount()).isEqualTo(1);
        assertThat(segments.get(2).stats().correctCount()).isEqualTo(1);
        assertThat(segments.get(2).stats().totalCount()).isEqualTo(2);

        // Sparse PENDING_REVIEW blank (pos 6): pendingReview=true, score=null
        assertThat(segments.get(3).code()).isEqualTo("SPARSE_PEND");
        assertThat(segments.get(3).pendingReview()).isTrue();
        assertThat(segments.get(3).score()).isNull();
        assertThat(segments.get(3).maxPoints()).isEqualByComparingTo("2.00");
        assertThat(segments.get(3).stats().pendingReviewCount()).isEqualTo(1);
        assertThat(segments.get(3).stats().totalCount()).isEqualTo(1);

        // Sparse tail (pos 7, index >= blankEntries.size()): 0 earned (no re-split!), BLANK outcome
        assertThat(segments.get(4).code()).isEqualTo("SPARSE_TAIL");
        assertThat(segments.get(4).pendingReview()).isFalse();
        assertThat(segments.get(4).score()).isEqualByComparingTo("0.00");
        assertThat(segments.get(4).maxPoints()).isEqualByComparingTo("2.00");
        assertThat(segments.get(4).stats().blankCount()).isEqualTo(1);
        assertThat(segments.get(4).stats().partialCount()).isZero();
        assertThat(segments.get(4).stats().totalCount()).isEqualTo(1);

        // Absent blankResults with item outcome PENDING_REVIEW (pos 8 & pos 9)
        assertThat(segments.get(5).code()).isEqualTo("ITEM_PEND");
        assertThat(segments.get(5).pendingReview()).isTrue();
        assertThat(segments.get(5).score()).isNull();
        assertThat(segments.get(5).maxPoints()).isEqualByComparingTo("1.50");
        assertThat(segments.get(5).stats().pendingReviewCount()).isEqualTo(1);

        assertThat(segments.get(6).code()).isEqualTo("ITEM_PEND_TAIL");
        assertThat(segments.get(6).pendingReview()).isTrue();
        assertThat(segments.get(6).score()).isNull();
        assertThat(segments.get(6).maxPoints()).isEqualByComparingTo("1.50");
        assertThat(segments.get(6).stats().pendingReviewCount()).isEqualTo(1);
    }

    private static AttemptPart closedPart(
            UUID partId, UUID attemptId, String code, String name, int ord, BigDecimal max, int reqCount) {
        AttemptPart p = AttemptPart.createPending(partId, attemptId, code, name, ord, 60, reqCount, max, 1L);
        p.open(NOW, NOW.plusSeconds(3600));
        p.close(PartCloseReason.ATTEMPT_SUBMITTED, NOW.plusSeconds(120));
        return p;
    }

    private static AttemptItem gradedItem(
            UUID attemptId,
            UUID partId,
            UUID parentId,
            int ord,
            QuestionType type,
            int unitCount,
            BigDecimal max,
            BigDecimal earned,
            ItemOutcome outcome,
            Map<String, Object> detail) {
        return gradedItem(attemptId, partId, UUID.randomUUID(), parentId, ord, type, unitCount, max, earned, outcome, detail);
    }

    private static AttemptItem gradedItem(
            UUID attemptId,
            UUID partId,
            UUID itemId,
            UUID parentId,
            int ord,
            QuestionType type,
            int unitCount,
            BigDecimal max,
            BigDecimal earned,
            ItemOutcome outcome,
            Map<String, Object> detail) {
        AttemptItem item = AttemptItem.create(
                itemId, attemptId, partId, null, "sec-1", null, "blk-1",
                parentId, ord, ord, unitCount, UUID.randomUUID(), 1, type, max, List.of());
        item.recordGrading(earned, outcome, detail);
        return item;
    }

    private static AttemptItem outOfScopeItem(
            UUID attemptId, UUID partId, UUID itemId, UUID parentId, int ord, QuestionType type) {
        AttemptItem item = AttemptItem.create(
                itemId, attemptId, partId, null, "sec-1", null, "blk-1",
                parentId, ord, ord, 0, UUID.randomUUID(), 1, type, BigDecimal.ZERO, List.of());
        org.springframework.test.util.ReflectionTestUtils.setField(item, "inScope", false);
        return item;
    }

    private static PublishedVersion versionWithParts(List<Part> parts) {
        return new PublishedVersion(
                UUID.randomUUID(), 1, "SERVING", "PUBLISHED", NOW,
                "CODE", "Name", UUID.randomUUID(), "VSAT", "SUBJECT", null,
                UUID.randomUUID(), "STANDARD", "SHARED", 60, new BigDecimal("150.00"),
                null, "RAW", null, parts, List.of(), List.of(), List.of(), List.of(), null);
    }

    private static Part partWithSegments(String partId, List<Segment> segments) {
        return new Part(
                partId, 1, "Part", 60, 25, 25, 25, "ALL", null,
                new BigDecimal("150.00"), segments, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
