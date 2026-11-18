package vn.unicom.thi360.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/** BE-16 rows 2, 3, 5, 6, 7, 8: the extended structure contract (H38, H39, H40, H44). */
class ExamStructureExtendedContractTest {
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    @Test
    void documentWithoutNewFieldsReadsAndWritesBackWithoutAddingAny() {
        String old = "{\"parts\":[{\"id\":\"p\",\"order\":1,\"name\":\"I\",\"questionCount\":8,\"sections\":["
                + "{\"id\":\"s\",\"subjectCode\":\"ANH\",\"questionCount\":8,\"blocks\":[{\"id\":\"b\","
                + "\"questionType\":\"QUESTION_SET\",\"setCount\":1,\"setSize\":8,\"shuffleUnits\":true,"
                + "\"shuffleEditable\":false,\"omitted\":false,\"tagCodes\":[],\"slots\":[],"
                + "\"childConditions\":[{\"from\":1,\"to\":2,\"nodeIds\":[]}],\"itemShape\":{\"statements\":4},"
                + "\"answerFormats\":[],\"topicQuotas\":[]}],\"topicQuotas\":[]}],\"options\":[],\"subjectPool\":[]}],"
                + "\"examQuotas\":[]}";
        ExamStructureDocument document = codec.read(old);

        assertThat(document.parts().getFirst().displaySegments()).isEmpty();
        assertThat(document.parts().getFirst().sections().getFirst().audioRequirement()).isNull();
        String written = codec.writeString(document);
        assertThat(written).doesNotContain("audioRequirement", "displaySegments", "answerType", "\"options\":null");
        assertThat(written).contains("\"itemShape\":{\"statements\":4}")
                .contains("\"childConditions\":[{\"from\":1,\"to\":2,\"nodeIds\":[]}]");
        assertThat(codec.writeString(codec.read(written))).isEqualTo(written);
        ExamStructureValidator.validate(document);
    }

    @Test
    void newFieldsRoundTripThroughJson() {
        ExamStructureDocument original = fullDocument();
        String json = codec.writeString(original);

        assertThat(codec.read(json)).isEqualTo(original);
        ExamPart part = codec.read(json).parts().getFirst();
        ExamSection section = part.sections().getFirst();
        assertThat(section.audioRequirement()).isEqualTo(AudioRequirement.REQUIRED);
        assertThat(part.displaySegments()).extracting(DisplaySegment::code).containsExactly("P1", "P2");
        QuestionBlock block = section.blocks().getFirst();
        assertThat(block.childConditions().getFirst().answerType()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(block.childConditions().getFirst().itemShape().statements()).isEqualTo(4);
        assertThat(block.childConditions().get(1).answerType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(block.childConditions().get(1).itemShape().options()).isEqualTo(4);
        assertThat(block.itemShape().options()).isEqualTo(4);
        ExamStructureValidator.validate(original);
    }

    @Test
    void segmentsMustBeContiguousCoverThePartAndSumToItsPoints() {
        assertSegmentRejected(List.of(segment("P1", 1, 8, "50"), segment("P2", 10, 15, "100")), "SEGMENT_GAP");
        assertSegmentRejected(List.of(segment("P1", 1, 9, "50"), segment("P2", 9, 15, "100")), "SEGMENT_OVERLAP");
        assertSegmentRejected(List.of(segment("P1", 1, 9, "50"), segment("P2", 10, 14, "100")), "SEGMENT_COVERAGE");
        assertSegmentRejected(List.of(segment("P1", 1, 9, "50"), segment("P2", 10, 15, "99")), "SEGMENT_POINTS");
        assertThatCode(() -> ExamStructureValidator.validate(documentWithSegments(
                List.of(segment("P1", 1, 9, "50"), segment("P2", 10, 15, "100"))))).doesNotThrowAnyException();
    }

    @Test
    void segmentErrorPointsOnlyAtTheSegment() {
        assertThatThrownBy(() -> ExamStructureValidator.validate(documentWithSegments(
                List.of(segment("P1", 1, 9, "50"), segment("P2", 11, 15, "100")))))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                    assertThat(ex.getFieldErrors()).extracting("field")
                            .containsExactly("parts[0].displaySegments[1]");
                });
    }

    @Test
    void mixedSetNeedsTypedConditionsCoveringEveryPositionOnce() {
        List<ChildCondition> gap = List.of(typed(1, 2, QuestionType.TRUE_FALSE), typed(4, 8, QuestionType.SINGLE_CHOICE));
        List<ChildCondition> overlap = List.of(typed(1, 3, QuestionType.TRUE_FALSE), typed(3, 8, QuestionType.SINGLE_CHOICE));
        List<ChildCondition> short_ = List.of(typed(1, 2, QuestionType.TRUE_FALSE), typed(3, 7, QuestionType.SINGLE_CHOICE));
        List<ChildCondition> beyond = List.of(typed(1, 2, QuestionType.TRUE_FALSE), typed(3, 9, QuestionType.SINGLE_CHOICE));
        for (List<ChildCondition> conditions : List.of(gap, overlap, short_, beyond)) {
            assertThatThrownBy(() -> ExamStructureValidator.validate(documentWith(mixedSet(conditions))))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID"));
        }
        assertThatCode(() -> ExamStructureValidator.validate(documentWith(mixedSet(List.of(
                typed(1, 2, QuestionType.TRUE_FALSE), typed(3, 8, QuestionType.SINGLE_CHOICE))))))
                .doesNotThrowAnyException();
    }

    @Test
    void nodeOnlyConditionsKeepWorkingAndDoNotNeedToCoverTheSet() {
        assertThatCode(() -> ExamStructureValidator.validate(documentWith(mixedSet(
                List.of(new ChildCondition(1, 1, List.of()))))))
                .doesNotThrowAnyException();
    }

    @Test
    void segmentsNeedBothPartTotalsAndErrorStaysOnTheLastSegment() {
        List<DisplaySegment> segments = List.of(segment("P1", 1, 9, "50"), segment("P2", 10, 15, "100"));
        assertThatThrownBy(() -> ExamStructureValidator.validate(documentWithSegments(segments, null,
                new BigDecimal("150")))).isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                    assertThat(ex.getFieldErrors()).extracting("field", "code")
                            .containsExactly(org.assertj.core.groups.Tuple.tuple(
                                    "parts[0].displaySegments[1]", "SEGMENT_COVERAGE"));
                });
        assertThatThrownBy(() -> ExamStructureValidator.validate(documentWithSegments(segments, 15, null)))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex ->
                        assertThat(ex.getFieldErrors()).extracting("field", "code")
                                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                                        "parts[0].displaySegments[1]", "SEGMENT_POINTS")));
        assertThatThrownBy(() -> ExamStructureValidator.validate(documentWithSegments(segments, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> ExamStructureValidator.validate(documentWithSegments(segments, 15,
                new BigDecimal("150")))).doesNotThrowAnyException();
    }

    @Test
    void unsegmentedPartsDoNotNeedTheTotals() {
        assertThatCode(() -> ExamStructureValidator.validate(documentWithSegments(List.of(), null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void everySegmentMemberIsRequiredAndTheHeadingMustNotBeBlank() {
        BigDecimal points = new BigDecimal("150");
        List<DisplaySegment> invalid = List.of(
                new DisplaySegment("P1", null, 1, 15, 15, points),
                new DisplaySegment("P1", " ", 1, 15, 15, points),
                new DisplaySegment(null, "Heading", 1, 15, 15, points),
                new DisplaySegment("P1", "Heading", null, 15, 15, points),
                new DisplaySegment("P1", "Heading", 1, null, 15, points),
                new DisplaySegment("P1", "Heading", 1, 15, null, points),
                new DisplaySegment("P1", "Heading", 1, 15, 15, null),
                new DisplaySegment(null, null, null, null, null, null));
        for (DisplaySegment segment : invalid) {
            assertThatThrownBy(() -> ExamStructureValidator.validate(
                    documentWithSegments(List.of(segment), 15, points)))
                    .as(segment.toString())
                    .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                        assertThat(ex.getFieldErrors()).extracting("field", "code")
                                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                                        "parts[0].displaySegments[0]", "SEGMENT_INVALID"));
                    });
        }
        assertThatCode(() -> ExamStructureValidator.validate(documentWithSegments(
                List.of(new DisplaySegment("P1", "Heading", 1, 15, 15, points)), 15, points)))
                .doesNotThrowAnyException();
    }

    @Test
    void aUniformChildAnswerTypeCannotComeWithTypedPositionalConditions() {
        List<ChildCondition> mix = List.of(typed(1, 2, QuestionType.TRUE_FALSE), typed(3, 8, QuestionType.SINGLE_CHOICE));
        assertRule(documentWith(mixedSet(mix, QuestionType.SINGLE_CHOICE, null)), false);
        assertRule(documentWith(mixedSet(mix, QuestionType.TRUE_FALSE, null)), false);
        assertRule(documentWith(mixedSet(mix, null, null)), true);
        // Old node-only documents keep their uniform type.
        assertRule(documentWith(mixedSet(List.of(new ChildCondition(1, 2, List.of())),
                QuestionType.SINGLE_CHOICE, null)), true);
        assertRule(documentWith(mixedSet(List.of(), QuestionType.SINGLE_CHOICE, null)), true);
    }

    @Test
    void positionalAnswerTypeMustBeLegalAsAQuestionSetChild() {
        for (QuestionType illegal : List.of(QuestionType.QUESTION_SET, QuestionType.CLOZE)) {
            assertRule(documentWith(mixedSet(List.of(typed(1, 8, illegal)))), false);
            assertRule(documentWith(mixedSet(List.of(typed(1, 2, QuestionType.TRUE_FALSE), typed(3, 8, illegal)))),
                    false);
        }
        for (QuestionType legal : List.of(QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE,
                QuestionType.SHORT_ANSWER, QuestionType.TRUE_FALSE, QuestionType.MATCHING, QuestionType.ESSAY)) {
            assertRule(documentWith(mixedSet(List.of(typed(1, 8, legal)))), true);
        }
    }

    @Test
    void negativeNestedShapeAndNegativeOptionsAreRejected() {
        ChildCondition validTail = typed(3, 8, QuestionType.SINGLE_CHOICE);
        for (ItemShape shape : List.of(new ItemShape(-1, null, null, null), new ItemShape(null, -1, null, null),
                new ItemShape(null, null, -1, null), new ItemShape(null, null, null, -1))) {
            ChildCondition bad = new ChildCondition(1, 2, List.of(), QuestionType.TRUE_FALSE, shape);
            assertRule(documentWith(mixedSet(List.of(bad, validTail))), false);
            assertRule(documentWith(mixedSet(List.of(), null, shape)), false);
        }
        assertRule(documentWith(mixedSet(List.of(), null, new ItemShape(null, null, null, 0))), true);
    }

    @Test
    void promptBasisIsReferenceOnlyForExamAndTopicQuotas() {
        for (QuotaEnforcement enforcement : QuotaEnforcement.values()) {
            boolean ok = enforcement == QuotaEnforcement.REFERENCE;
            var examQuota = new ExamQuota("e", "grade", ExamQuota.Dimension.GRADE, List.of(), QuotaBasis.PROMPT,
                    enforcement, List.of("TOAN"), List.of());
            var topic = new TopicQuota("t", "topic", List.of(), 5, QuotaBasis.PROMPT, enforcement,
                    List.of(), List.of(), List.of());
            var section = new ExamSection("s", "TOAN", 2, null, null, null, null, List.of(), List.of(topic));
            var part = new ExamPart("p", 1, "part", null, 2, null, null, null, null, List.of(section), List.of());

            assertRule(new ExamStructureDocument(List.of(), List.of(examQuota)), ok);
            assertRule(new ExamStructureDocument(List.of(part)), ok);
        }
    }

    @Test
    void promptWithoutEnforcementCountsAsRequiredAndIsRejected() {
        var topic = new TopicQuota("t", "topic", List.of(), 5, QuotaBasis.PROMPT, null,
                List.of(), List.of(), List.of());
        var section = new ExamSection("s", "TOAN", 2, null, null, null, null, List.of(), List.of(topic));
        assertRule(new ExamStructureDocument(List.of(new ExamPart("p", 1, "part", null, 2, null, null, null,
                null, List.of(section), List.of()))), false);
    }

    @Test
    void pointsBasisStaysRejectedEvenForReference() {
        var topic = new TopicQuota("t", "topic", List.of(), 5, QuotaBasis.POINTS, QuotaEnforcement.REFERENCE,
                List.of(), List.of(), List.of());
        var section = new ExamSection("s", "TOAN", 2, null, null, null, null, List.of(), List.of(topic));
        assertRule(new ExamStructureDocument(List.of(new ExamPart("p", 1, "part", null, 2, null, null, null,
                null, List.of(section), List.of()))), false);
    }

    private static void assertRule(ExamStructureDocument document, boolean accepted) {
        if (accepted) {
            assertThatCode(() -> ExamStructureValidator.validate(document)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ExamStructureValidator.validate(document))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).startsWith("EXAM_"));
        }
    }

    private static void assertSegmentRejected(List<DisplaySegment> segments, String fieldCode) {
        assertThatThrownBy(() -> ExamStructureValidator.validate(documentWithSegments(segments)))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                    assertThat(ex.getFieldErrors()).extracting("code").containsExactly(fieldCode);
                    assertThat(ex.getFieldErrors()).allSatisfy(error ->
                            assertThat(error.field()).startsWith("parts[0].displaySegments["));
                });
    }

    private static DisplaySegment segment(String code, int from, int to, String points) {
        return new DisplaySegment(code, code, from, to, to - from + 1, new BigDecimal(points));
    }

    private static ChildCondition typed(int from, int to, QuestionType type) {
        return new ChildCondition(from, to, List.of(), type,
                type == QuestionType.TRUE_FALSE ? new ItemShape(4, null, null) : new ItemShape(null, null, null, 4));
    }

    private static QuestionBlock mixedSet(List<ChildCondition> conditions) {
        return mixedSet(conditions, null, null);
    }

    private static QuestionBlock mixedSet(List<ChildCondition> conditions, QuestionType childAnswerType,
            ItemShape itemShape) {
        return new QuestionBlock("b", "Bài đọc", QuestionType.QUESTION_SET, null, 1, 8, null, null,
                childAnswerType, null, false, null, List.of(), null, true, false, List.of(), conditions, itemShape,
                List.of(), false, List.of(), null);
    }

    private static ExamStructureDocument documentWith(QuestionBlock block) {
        ExamSection section = new ExamSection("s", "ANH", 8, null, null, null, null, List.of(block));
        return new ExamStructureDocument(List.of(new ExamPart("p", 1, "part", null, 8, null, null, null, null,
                List.of(section), List.of())));
    }

    private static ExamStructureDocument documentWithSegments(List<DisplaySegment> segments) {
        return documentWithSegments(segments, 15, new BigDecimal("150"));
    }

    private static ExamStructureDocument documentWithSegments(List<DisplaySegment> segments, Integer questionCount,
            BigDecimal maxPoints) {
        ExamSection section = new ExamSection("s", "ANH", 15, null, null, null, null, List.of());
        return new ExamStructureDocument(List.of(new ExamPart("p", 1, "part", null, questionCount, null, null, null,
                maxPoints, List.of(section), List.of(), List.of(), null, segments)));
    }

    static ExamStructureDocument fullDocument() {
        QuestionBlock block = new QuestionBlock("b", "Bài đọc", QuestionType.QUESTION_SET, null, 1, 8, null, null,
                null, null, false, new BigDecimal("6"), List.of(), null, true, false, List.of(),
                List.of(typed(1, 2, QuestionType.TRUE_FALSE), typed(3, 8, QuestionType.SINGLE_CHOICE)),
                new ItemShape(null, null, null, 4), List.of(), false, List.of(), null);
        ExamSection section = new ExamSection("s", "ANH", 8, null, null, null, null, List.of(block), List.of(),
                AudioRequirement.REQUIRED);
        return new ExamStructureDocument(List.of(new ExamPart("p", 1, "Phần", null, 15, null, null, null,
                new BigDecimal("150"), List.of(section), List.of(), List.of(), null,
                List.of(segment("P1", 1, 9, "50"), segment("P2", 10, 15, "100")))));
    }
}
