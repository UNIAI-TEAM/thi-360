package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import vn.unicom.thi360.questionbank.domain.QuestionBody.ChildQuestion;
import vn.unicom.thi360.questionbank.domain.QuestionBody.ChoiceOption;
import vn.unicom.thi360.questionbank.domain.QuestionBody.ClozeBlank;
import vn.unicom.thi360.questionbank.domain.QuestionBody.EssayCriterion;
import vn.unicom.thi360.questionbank.domain.QuestionBody.MatchingPair;
import vn.unicom.thi360.questionbank.domain.QuestionBody.MatchingSide;
import vn.unicom.thi360.questionbank.domain.QuestionBody.TrueFalseStatement;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator.ClassificationRequirement;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Kiểm tra phần thân 8 dạng ở hai mức lưu và Sẵn sàng (spec 4.3, mục 10): câu Nháp chưa hoàn
 * tất vẫn lưu được; các ca biên điểm âm, thừa chữ số thập phân, đáp án chỉ khoảng trắng,
 * id trùng; câu con cấm (D19); tham chiếu trong thân trỏ tới phần tử không có.
 */
class QuestionBodyValidationTests {

    private static final ClassificationRequirement FULL = new ClassificationRequirement(true, true, true);

    // ------------------------------------------------------------- dữ liệu mẫu

    static QuestionBody singleChoiceBody() {
        return new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", true),
            new ChoiceOption("a2", "<p>B</p>", false),
            new ChoiceOption("a3", "<p>C</p>", false),
            new ChoiceOption("a4", "<p>D</p>", false)));
    }

    static QuestionBody multipleChoiceBody() {
        return new QuestionBody.MultipleChoiceBody(QuestionType.MULTIPLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", true),
            new ChoiceOption("a2", "<p>B</p>", true),
            new ChoiceOption("a3", "<p>C</p>", false)),
            QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING);
    }

    static QuestionBody shortAnswerBody() {
        return new QuestionBody.ShortAnswerBody(
            QuestionType.SHORT_ANSWER, List.of("4", "bốn"), "nhập số");
    }

    static QuestionBody clozeBody() {
        return new QuestionBody.ClozeBody(QuestionType.CLOZE,
            "<p>2 + 2 = [[b1]], 3 + 3 = [[b2]]</p>",
            List.of(
                new ClozeBlank("b1", List.of("4"), new BigDecimal("1")),
                new ClozeBlank("b2", List.of("6"), null)));
    }

    static QuestionBody questionSetBody() {
        return new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>Đọc đoạn sau và trả lời.</p>",
            null,
            List.of(
                new ChildQuestion(QuestionType.SINGLE_CHOICE, "<p>Câu con 1</p>", singleChoiceBody(), null, null, null, null, List.of(), null),
                new ChildQuestion(QuestionType.SHORT_ANSWER, "<p>Câu con 2</p>", shortAnswerBody(), null, new BigDecimal("2"), null, null, List.of(), null)));
    }

    static QuestionBody trueFalseBody() {
        return new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", "<p>Ý 1</p>", true, null),
            new TrueFalseStatement("s2", "<p>Ý 2</p>", false, null),
            new TrueFalseStatement("s3", "<p>Ý 3</p>", true, null),
            new TrueFalseStatement("s4", "<p>Ý 4</p>", false, null)),
            QuestionBody.TrueFalseScoringMethod.COUNT_TABLE,
            List.of(new BigDecimal("0.1"), new BigDecimal("0.25"), new BigDecimal("0.5"), new BigDecimal("1")));
    }

    static QuestionBody matchingBody() {
        return new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l1", "<p>Trái 1</p>"), new MatchingSide("l2", "<p>Trái 2</p>")),
            List.of(new MatchingSide("r1", "<p>Phải 1</p>"), new MatchingSide("r2", "<p>Phải 2</p>"), new MatchingSide("r3", "<p>Phải 3</p>")),
            List.of(
                new MatchingPair("l1", "r1", new BigDecimal("0.5")),
                new MatchingPair("l2", "r2", new BigDecimal("0.5"))),
            false);
    }

    static QuestionBody essayBody() {
        return new QuestionBody.EssayBody(QuestionType.ESSAY,
            "<p>Hướng dẫn chấm chi tiết.</p>",
            List.of(new EssayCriterion("c1", "<p>Lập luận</p>", new BigDecimal("0.5")),
                new EssayCriterion("c2", "<p>Văn phong</p>", new BigDecimal("0.5"))),
            "<p>Bài mẫu</p>", 200);
    }

    static Stream<Arguments> validBodies() {
        return Stream.of(
            Arguments.of(QuestionType.SINGLE_CHOICE, singleChoiceBody()),
            Arguments.of(QuestionType.MULTIPLE_CHOICE, multipleChoiceBody()),
            Arguments.of(QuestionType.SHORT_ANSWER, shortAnswerBody()),
            Arguments.of(QuestionType.CLOZE, clozeBody()),
            Arguments.of(QuestionType.QUESTION_SET, questionSetBody()),
            Arguments.of(QuestionType.TRUE_FALSE, trueFalseBody()),
            Arguments.of(QuestionType.MATCHING, matchingBody()),
            Arguments.of(QuestionType.ESSAY, essayBody()));
    }

    private static QuestionInput input(QuestionType type, QuestionBody body) {
        return new QuestionInput(type, body, BigDecimal.ONE, "<p>Nội dung câu hỏi.</p>", null, List.of(), List.of());
    }

    private static List<String> codes(List<QuestionFieldError> errors) {
        return errors.stream().map(QuestionFieldError::code).toList();
    }

    // ------------------------------------------------------------- hợp lệ cả hai mức

    @ParameterizedTest(name = "{0}: câu đủ điều kiện lưu được và Sẵn sàng được")
    @MethodSource("validBodies")
    void validBodyPassesBothLevels(QuestionType type, QuestionBody body) {
        QuestionInput input = input(type, body);
        assertTrue(QuestionBodyValidator.validateSave(input, null).isEmpty());
        QuestionBodyValidator.ReadinessResult result =
            QuestionBodyValidator.validateReady(input, FULL, true, null);
        assertTrue(result.ready(), () -> "unexpected errors: " + result.errors());
    }

    @ParameterizedTest(name = "{0}: câu Nháp thiếu dữ liệu vẫn lưu được")
    @MethodSource("validBodies")
    void draftWithMissingDataSaves(QuestionType type, QuestionBody body) {
        // Nội dung rỗng và thiếu đáp án vẫn lưu được ở mức Nháp (spec 4.3).
        QuestionInput draft = new QuestionInput(type, body, BigDecimal.ONE, null, null, List.of(), List.of());
        assertTrue(QuestionBodyValidator.validateSave(draft, null).isEmpty());
    }

    @Test
    void draftWithoutOptionsSaves() {
        QuestionInput draft = new QuestionInput(QuestionType.SINGLE_CHOICE,
            new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of()),
            BigDecimal.ONE, "<p>Câu đang soạn</p>", null, List.of(), List.of());
        assertTrue(QuestionBodyValidator.validateSave(draft, null).isEmpty());
    }

    // ------------------------------------------------------------- lỗi mức lưu

    @Test
    void negativePointsRejectedAtSave() {
        QuestionInput input = new QuestionInput(QuestionType.SINGLE_CHOICE, singleChoiceBody(),
            new BigDecimal("-1"), "<p>x</p>", null, List.of(), List.of());
        assertTrue(codes(QuestionBodyValidator.validateSave(input, null)).contains("POINTS_NOT_POSITIVE"));
    }

    @Test
    void tooManyDecimalsRejectedAtSave() {
        QuestionInput input = new QuestionInput(QuestionType.SINGLE_CHOICE, singleChoiceBody(),
            new BigDecimal("0.12345"), "<p>x</p>", null, List.of(), List.of());
        assertTrue(codes(QuestionBodyValidator.validateSave(input, null)).contains("POINTS_TOO_MANY_DECIMALS"));
    }
    @Test
    void pointsMustFitNumericColumnForParentAndComponents() {
        QuestionInput max = new QuestionInput(QuestionType.SINGLE_CHOICE, singleChoiceBody(),
            new BigDecimal("9999.9999"), "content", null, List.of(), List.of());
        assertTrue(QuestionBodyValidator.validateSave(max, null).isEmpty());
        QuestionInput overflow = new QuestionInput(QuestionType.ESSAY,
            new QuestionBody.EssayBody(QuestionType.ESSAY, "guide",
                List.of(new EssayCriterion("c", "criterion", new BigDecimal("10000"))), null, null),
            new BigDecimal("10000"), "content", null, List.of(), List.of());
        List<QuestionFieldError> errors = QuestionBodyValidator.validateSave(overflow, null);
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("POINTS_TOO_LARGE") && e.path().equals("points")));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("POINTS_TOO_LARGE")
            && e.path().equals("body.rubric[0].points")));
    }


    @Test
    void blankPointsIsAllowedAtSave() {
        // Điểm trống (null) là Nháp đang soạn; numeric(8,4) do tầng lưu đặt mặc định 1.
        QuestionInput input = new QuestionInput(QuestionType.SINGLE_CHOICE, singleChoiceBody(),
            null, "<p>x</p>", null, List.of(), List.of());
        assertTrue(QuestionBodyValidator.validateSave(input, null).isEmpty());
    }

    @Test
    void duplicateOptionIdsRejectedAtSave() {
        QuestionBody body = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", true),
            new ChoiceOption("a1", "<p>B</p>", false)));
        List<QuestionFieldError> errors =
            QuestionBodyValidator.validateSave(input(QuestionType.SINGLE_CHOICE, body), null);
        assertTrue(codes(errors).contains("QUESTION_DUPLICATE_ID"));
        assertTrue(errors.stream().anyMatch(e -> e.path().equals("body.options[1].id")));
    }

    @Test
    void duplicateBlankIdsRejectedAtSave() {
        QuestionBody body = new QuestionBody.ClozeBody(QuestionType.CLOZE,
            "<p>x = [[b1]]</p>",
            List.of(new ClozeBlank("b1", List.of("1"), null), new ClozeBlank("b1", List.of("2"), null)));
        assertTrue(codes(QuestionBodyValidator.validateSave(input(QuestionType.CLOZE, body), null))
            .contains("QUESTION_DUPLICATE_ID"));
    }

    @Test
    void bodyTypeMismatchRejectedAtSave() {
        // Phần thân lệch dạng câu (spec 4.5: QUESTION_BODY_TYPE_MISMATCH).
        QuestionInput input = input(QuestionType.MULTIPLE_CHOICE, singleChoiceBody());
        assertTrue(codes(QuestionBodyValidator.validateSave(input, null)).contains("QUESTION_BODY_TYPE_MISMATCH"));
    }

    @Test
    void overLimitCountsRejectedAtSave() {
        QuestionBody tooManyOptions = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE,
            java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> new ChoiceOption("a" + i, "<p>" + i + "</p>", i == 0))
                .toList());
        assertTrue(codes(QuestionBodyValidator.validateSave(
            input(QuestionType.SINGLE_CHOICE, tooManyOptions), null)).contains("QUESTION_OPTIONS_TOO_MANY"));

        QuestionBody tooManyBlanks = new QuestionBody.ClozeBody(QuestionType.CLOZE, "<p>x</p>",
            java.util.stream.IntStream.range(0, 31)
                .mapToObj(i -> new ClozeBlank("b" + i, List.of("1"), null)).toList());
        assertTrue(codes(QuestionBodyValidator.validateSave(
            input(QuestionType.CLOZE, tooManyBlanks), null)).contains("QUESTION_BLANKS_TOO_MANY"));
    }

    @Test
    void clozeMarkerAndBlankMustMatch() {
        // Dấu [[b2]] trong ngữ liệu không có ô; ô b1 không có dấu.
        QuestionBody body = new QuestionBody.ClozeBody(QuestionType.CLOZE,
            "<p>x = [[b2]]</p>",
            List.of(new ClozeBlank("b1", List.of("1"), null)));
        List<QuestionFieldError> errors =
            QuestionBodyValidator.validateSave(input(QuestionType.CLOZE, body), null);
        assertTrue(codes(errors).contains("QUESTION_BLANK_MARKER_MISMATCH"));
    }
    @Test
    void clozeBlankMayWaitForMarkerInDraft() {
        QuestionBody body = new QuestionBody.ClozeBody(QuestionType.CLOZE, "<p>draft</p>",
            List.of(new ClozeBlank("b1", List.of("answer"), BigDecimal.ONE)));
        QuestionInput draft = input(QuestionType.CLOZE, body);
        assertTrue(QuestionBodyValidator.validateSave(draft, null).isEmpty());
        assertTrue(readyErrors(draft).stream().anyMatch(e ->
            e.code().equals("QUESTION_BLANK_MARKER_MISMATCH") && e.path().equals("body.blanks")));
        QuestionBody dangling = new QuestionBody.ClozeBody(QuestionType.CLOZE, "[[missing]]",
            List.of(new ClozeBlank("b1", List.of("answer"), BigDecimal.ONE)));
        assertTrue(QuestionBodyValidator.validateSave(input(QuestionType.CLOZE, dangling), null).stream()
            .anyMatch(e -> e.code().equals("QUESTION_BLANK_MARKER_MISMATCH") && e.path().equals("body.passage")));
    }


    @Test
    void clozeDuplicateMarkerRejected() {
        // Một dấu vị trí xuất hiện hai lần: không còn "đúng một ô và ngược lại".
        QuestionBody body = new QuestionBody.ClozeBody(QuestionType.CLOZE,
            "<p>x = [[b1]], y = [[b1]]</p>",
            List.of(new ClozeBlank("b1", List.of("1"), null)));
        assertTrue(codes(QuestionBodyValidator.validateSave(input(QuestionType.CLOZE, body), null))
            .contains("QUESTION_BLANK_MARKER_MISMATCH"));
    }

    @Test
    void clozeAndSetCannotBeChildren() {
        // D19: không cho Bộ câu hỏi hay Điền khuyết làm câu con.
        QuestionBody setWithCloze = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>p</p>", null, List.of(
            new ChildQuestion(QuestionType.CLOZE, "<p>c</p>", clozeBody(), null, null, null, null, List.of(), null)));
        assertTrue(codes(QuestionBodyValidator.validateSave(
            input(QuestionType.QUESTION_SET, setWithCloze), null)).contains("QUESTION_CHILD_TYPE_NOT_ALLOWED"));

        QuestionBody nestedSet = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>p</p>", null, List.of(
            new ChildQuestion(QuestionType.QUESTION_SET, "<p>c</p>", questionSetBody(), null, null, null, null, List.of(), null)));
        assertTrue(codes(QuestionBodyValidator.validateSave(
            input(QuestionType.QUESTION_SET, nestedSet), null)).contains("QUESTION_CHILD_TYPE_NOT_ALLOWED"));
    }

    @Test
    void matchingPairMustReferenceExistingSide() {
        QuestionBody body = new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l1", "a")),
            List.of(new MatchingSide("r1", "x")),
            List.of(new MatchingPair("l1", "r9", BigDecimal.ONE)),
            false);
        assertTrue(codes(QuestionBodyValidator.validateSave(input(QuestionType.MATCHING, body), null))
            .contains("QUESTION_PAIR_UNKNOWN_RIGHT"));
    }

    @Test
    void matchingOneLeftPairsOnce() {
        QuestionBody body = new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l1", "a")),
            List.of(new MatchingSide("r1", "x"), new MatchingSide("r2", "y")),
            List.of(new MatchingPair("l1", "r1", BigDecimal.ONE), new MatchingPair("l1", "r2", BigDecimal.ONE)),
            true);
        assertTrue(codes(QuestionBodyValidator.validateSave(input(QuestionType.MATCHING, body), null))
            .contains("QUESTION_PAIR_DUPLICATE_LEFT"));
    }

    @Test
    void essayWordLimitMustBePositive() {
        QuestionBody body = new QuestionBody.EssayBody(
            QuestionType.ESSAY, "<p>g</p>", List.of(), null, 0);
        assertTrue(codes(QuestionBodyValidator.validateSave(input(QuestionType.ESSAY, body), null))
            .contains("QUESTION_WORD_LIMIT_INVALID"));
    }

    @Test
    void unresolvedAttachmentRejectedAtSave() {
        // Resolver mặc định trả rỗng: mọi attachmentIds bị từ chối.
        QuestionInput input = new QuestionInput(QuestionType.ESSAY, essayBody(), BigDecimal.ONE,
            "<p>x</p>", null, List.of(), List.of(UUID.randomUUID()));
        assertTrue(codes(QuestionBodyValidator.validateSave(input, null))
            .contains("QUESTION_ATTACHMENT_UNKNOWN"));
    }

    @Test
    void resolvedAttachmentPasses() {
        UUID assetId = UUID.randomUUID();
        QuestionInput input = new QuestionInput(QuestionType.ESSAY, essayBody(), BigDecimal.ONE,
            "<p>x</p>", null, List.of(), List.of(assetId));
        // Resolver nhận biết loại (quyết định 8.6): id này chỉ tra được với ATTACHMENT;
        // IMAGE hay id lạ đều trả rỗng như resolver thật.
        assertTrue(QuestionBodyValidator.validateSave(input, new QuestionAssetResolver() {
            @Override
            public java.util.Optional<String> servingPath(UUID id) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<String> servingPath(UUID id, QuestionAssetKind kind) {
                return kind == QuestionAssetKind.ATTACHMENT && id.equals(assetId)
                        ? java.util.Optional.of("/api/v1/admin/question-assets/" + id)
                        : java.util.Optional.empty();
            }
        }).isEmpty());
    }

    // ------------------------------------------------------------- lỗi mức Sẵn sàng

    @Test
    void singleChoiceNeedsExactlyOneCorrect() {
        QuestionBody none = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", false), new ChoiceOption("a2", "<p>B</p>", false)));
        assertTrue(codes(readyErrors(input(QuestionType.SINGLE_CHOICE, none))).contains("NO_CORRECT_OPTION"));

        QuestionBody many = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", true), new ChoiceOption("a2", "<p>B</p>", true)));
        assertTrue(codes(readyErrors(input(QuestionType.SINGLE_CHOICE, many)))
            .contains("SINGLE_CHOICE_MANY_CORRECT"));
    }

    @Test
    void multipleChoiceNeedsTwoCorrect() {
        QuestionBody one = new QuestionBody.MultipleChoiceBody(QuestionType.MULTIPLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", true), new ChoiceOption("a2", "<p>B</p>", false)),
            QuestionBody.MultipleChoiceRule.PARTIAL);
        assertTrue(codes(readyErrors(input(QuestionType.MULTIPLE_CHOICE, one)))
            .contains("TOO_FEW_CORRECT_OPTIONS"));
    }

    @Test
    void whitespaceOnlyAnswerFailsReady() {
        // Đáp án chỉ khoảng trắng: lưu được, Sẵn sàng thì tính là chưa có đáp án.
        QuestionBody body = new QuestionBody.ShortAnswerBody(
            QuestionType.SHORT_ANSWER, List.of("   "), null);
        QuestionInput input = input(QuestionType.SHORT_ANSWER, body);
        assertTrue(QuestionBodyValidator.validateSave(input, null).isEmpty());
        assertTrue(codes(readyErrors(input)).contains("BLANK_WITHOUT_ANSWER"));
    }

    @Test
    void emptyContentFailsReadyButSaves() {
        QuestionInput input = new QuestionInput(QuestionType.SINGLE_CHOICE, singleChoiceBody(),
            BigDecimal.ONE, "<p>   </p>", null, List.of(), List.of());
        assertTrue(QuestionBodyValidator.validateSave(input, null).isEmpty());
        assertTrue(codes(readyErrors(input)).contains("CONTENT_EMPTY"));
    }

    @Test
    void emptyOptionFailsReady() {
        QuestionBody body = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", true), new ChoiceOption("a2", "<p>  </p>", false)));
        assertTrue(codes(readyErrors(input(QuestionType.SINGLE_CHOICE, body))).contains("OPTION_EMPTY"));
    }

    @Test
    void questionSetNeedsTwoReadyChildren() {
        QuestionBody oneChild = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>p</p>", null, List.of(
            new ChildQuestion(QuestionType.SINGLE_CHOICE, "<p>c</p>", singleChoiceBody(), null, null, null, null, List.of(), null)));
        assertTrue(codes(readyErrors(input(QuestionType.QUESTION_SET, oneChild)))
            .contains("SET_WITHOUT_CHILDREN"));

        // Câu con chưa đạt điều kiện của dạng mình thì bộ không Sẵn sàng được.
        QuestionBody childNotReady = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>p</p>", null, List.of(
            new ChildQuestion(QuestionType.SINGLE_CHOICE, "<p>c</p>", singleChoiceBody(), null, null, null, null, List.of(), null),
            new ChildQuestion(QuestionType.SHORT_ANSWER, "<p>c2</p>",
                new QuestionBody.ShortAnswerBody(QuestionType.SHORT_ANSWER, List.of(), null), null, null, null, null, List.of(), null)));
        List<QuestionFieldError> errors = readyErrors(input(QuestionType.QUESTION_SET, childNotReady));
        assertTrue(errors.stream().anyMatch(
            e -> e.code().equals("BLANK_WITHOUT_ANSWER") && e.path().equals("body.children[1].body.answers")));
    }

    @Test
    void missingComponentScoresAreDraftOnly() {
        QuestionBody statements = new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", "a", true, null),
            new TrueFalseStatement("s2", "b", false, BigDecimal.ONE)),
            QuestionBody.TrueFalseScoringMethod.PER_STATEMENT, List.of());
        QuestionBody allMissing = new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", "a", true, null),
            new TrueFalseStatement("s2", "b", false, null)),
            QuestionBody.TrueFalseScoringMethod.PER_STATEMENT, List.of());
        QuestionBody pairs = new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l1", "a"), new MatchingSide("l2", "b")),
            List.of(new MatchingSide("r1", "x"), new MatchingSide("r2", "y")),
            List.of(new MatchingPair("l1", "r1", BigDecimal.ONE), new MatchingPair("l2", "r2", null)),
            false);
        QuestionBody rubric = new QuestionBody.EssayBody(QuestionType.ESSAY, "guide",
            List.of(new EssayCriterion("c1", "criterion", null)), null, null);
        for (QuestionBody body : List.of(statements, allMissing, pairs, rubric)) {
            QuestionInput draft = input(body.type(), body);
            assertTrue(QuestionBodyValidator.validateSave(draft, null).isEmpty());
            List<QuestionFieldError> errors = readyErrors(draft);
            String expected = switch (body.type()) {
                case TRUE_FALSE -> "body.statements[0].points";
                case MATCHING -> "body.pairs[1].points";
                case ESSAY -> "body.rubric[0].points";
                default -> throw new AssertionError(body.type());
            };
            assertTrue(errors.stream().anyMatch(e -> e.code().equals("POINTS_REQUIRED")
                && e.path().equals(expected)), errors.toString());
            if (body == allMissing) {
                assertTrue(errors.stream().anyMatch(e -> e.path().equals("body.statements[1].points")));
            }
        }
    }

    @Test
    void trueFalsePerStatementTotalMustEqualPoints() {
        QuestionBody body = new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", "a", true, new BigDecimal("0.4")),
            new TrueFalseStatement("s2", "b", false, new BigDecimal("0.4"))),
            QuestionBody.TrueFalseScoringMethod.PER_STATEMENT, List.of());
        assertTrue(codes(readyErrors(input(QuestionType.TRUE_FALSE, body))).contains("SCORE_TABLE_INVALID"));
    }

    @Test
    void trueFalseCountTableRules() {
        // Bảng giảm (0.5 rồi 0.25) hoặc phần tử cuối lệch điểm câu là không hợp lệ.
        QuestionBody decreasing = new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", "a", true, null), new TrueFalseStatement("s2", "b", false, null)),
            QuestionBody.TrueFalseScoringMethod.COUNT_TABLE,
            List.of(new BigDecimal("0.5"), new BigDecimal("0.25")));
        assertTrue(codes(readyErrors(input(QuestionType.TRUE_FALSE, decreasing))).contains("SCORE_TABLE_INVALID"));
    }
    @Test
    void missingParentPointsDefaultsToOneForScoreComparisons() {
        QuestionBody body = new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", "a", true, null), new TrueFalseStatement("s2", "b", false, null)),
            QuestionBody.TrueFalseScoringMethod.COUNT_TABLE, List.of(BigDecimal.ONE, new BigDecimal("2")));
        QuestionInput draft = new QuestionInput(QuestionType.TRUE_FALSE, body, null, "content", null, List.of(), List.of());
        assertTrue(QuestionBodyValidator.validateSave(draft, null).isEmpty());
        assertTrue(readyErrors(draft).stream().anyMatch(e ->
            e.code().equals("SCORE_TABLE_INVALID") && e.path().equals("body.scoreTable")));
    }
    @Test
    void exactToleranceBoundaryIsNotEqual() {
        assertTrue(!QuestionBodyValidator.isValidScoreTable(
            List.of(BigDecimal.ZERO, new BigDecimal("0.001")), 2, new BigDecimal("0.0005")));
        assertTrue(QuestionBodyValidator.isValidScoreTable(
            List.of(BigDecimal.ZERO, new BigDecimal("0.001")), 2, new BigDecimal("0.0006")));
    }



    @Test
    void statementWithoutAnswerFailsReady() {
        QuestionBody body = new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", "a", null, null), new TrueFalseStatement("s2", "b", false, null)),
            QuestionBody.TrueFalseScoringMethod.PER_STATEMENT, List.of());
        assertTrue(codes(readyErrors(input(QuestionType.TRUE_FALSE, body))).contains("STATEMENT_ANSWER_REQUIRED"));
    }

    @Test
    void matchingReadyRules() {
        // Mục trái chưa ghép + lựa chọn dùng lại khi reuseRight tắt + tổng điểm cặp lệch điểm câu.
        QuestionBody body = new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l1", "a"), new MatchingSide("l2", "b")),
            List.of(new MatchingSide("r1", "x")),
            List.of(new MatchingPair("l1", "r1", new BigDecimal("0.4"))),
            false);
        List<String> codes = codes(readyErrors(input(QuestionType.MATCHING, body)));
        assertTrue(codes.contains("QUESTION_LEFT_UNPAIRED"));
        assertTrue(codes.contains("QUESTION_PAIR_POINTS_MISMATCH"));

        QuestionBody reused = new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l1", "a"), new MatchingSide("l2", "b")),
            List.of(new MatchingSide("r1", "x")),
            List.of(new MatchingPair("l1", "r1", new BigDecimal("0.5")),
                new MatchingPair("l2", "r1", new BigDecimal("0.5"))),
            false);
        assertTrue(codes(readyErrors(input(QuestionType.MATCHING, reused))).contains("QUESTION_RIGHT_REUSED"));
    }

    @Test
    void classificationRequiredForReady() {
        QuestionInput input = input(QuestionType.SINGLE_CHOICE, singleChoiceBody());
        QuestionBodyValidator.ReadinessResult missing = QuestionBodyValidator.validateReady(
            input, new ClassificationRequirement(false, false, false), true, null);
        List<String> codes = codes(missing.errors());
        assertTrue(codes.containsAll(List.of("SUBJECT_MISSING", "GRADE_MISSING", "DIFFICULTY_MISSING")));
    }
    @Test
    void nullClassificationAndEmptyMatchingSidesBlockReady() {
        QuestionInput choice = input(QuestionType.SINGLE_CHOICE, singleChoiceBody());
        var classificationErrors = QuestionBodyValidator.validateReady(choice, null, true, null).errors();
        assertTrue(codes(classificationErrors).containsAll(
            List.of("SUBJECT_MISSING", "GRADE_MISSING", "DIFFICULTY_MISSING")));
        QuestionBody matching = new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l", "<p> </p>")), List.of(new MatchingSide("r", "")),
            List.of(new MatchingPair("l", "r", BigDecimal.ONE)), false);
        QuestionInput draft = input(QuestionType.MATCHING, matching);
        assertTrue(QuestionBodyValidator.validateSave(draft, null).isEmpty());
        var errors = readyErrors(draft);
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("MATCHING_SIDE_EMPTY")
            && e.path().equals("body.left[0].content")));
        assertTrue(errors.stream().anyMatch(e -> e.code().equals("MATCHING_SIDE_EMPTY")
            && e.path().equals("body.right[0].content")));
    }


    @Test
    void unbalancedMathFailsReady() {
        QuestionInput input = new QuestionInput(QuestionType.SHORT_ANSWER, shortAnswerBody(),
            BigDecimal.ONE, "<p>Tính \\( x^2 + 1 </p>", null, List.of(), List.of());
        assertTrue(codes(readyErrors(input)).contains("MATH_UNBALANCED"));
    }
    @Test
    void mathIsCheckedInAllHtmlFieldsAndDelimiterOrder() {
        String bad = "<p>\\( x \\[ y \\) z \\]</p>";
        QuestionBody choice = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE,
            List.of(new ChoiceOption("a", bad, true), new ChoiceOption("b", "B", false)));
        QuestionInput question = new QuestionInput(QuestionType.SINGLE_CHOICE, choice, BigDecimal.ONE,
            "<p>\\(x\\) and \\[y\\]</p>", bad, List.of(bad), List.of());
        assertTrue(QuestionBodyValidator.validateSave(question, null).isEmpty());
        var errors = readyErrors(question);
        for (String path : List.of("body.options[0].content", "solution", "commonMistakes[0]")) {
            assertTrue(errors.stream().anyMatch(e -> e.code().equals("MATH_UNBALANCED")
                && e.path().equals(path)), errors.toString());
        }
        assertTrue(errors.stream().noneMatch(e -> e.code().equals("MATH_UNBALANCED") && e.path().equals("content")));
        QuestionBody essay = new QuestionBody.EssayBody(QuestionType.ESSAY, bad,
            List.of(new EssayCriterion("c", bad, BigDecimal.ONE)), bad, null);
        var essayErrors = readyErrors(input(QuestionType.ESSAY, essay));
        for (String path : List.of("body.gradingGuide", "body.sampleAnswer", "body.rubric[0].description")) {
            assertTrue(essayErrors.stream().anyMatch(e -> e.code().equals("MATH_UNBALANCED")
                && e.path().equals(path)), essayErrors.toString());
        }

        // R2-1: các nhánh duyệt MULTIPLE_CHOICE / TRUE_FALSE / MATCHING được kiểm thường trực —
        // câu nháp vẫn lưu, Sẵn sàng chặn đúng đường dẫn; công thức hợp lệ vẫn được chấp nhận.
        QuestionBody multiple = new QuestionBody.MultipleChoiceBody(QuestionType.MULTIPLE_CHOICE,
            List.of(new ChoiceOption("a1", bad, true),
                new ChoiceOption("a2", "<p>\\(y\\)</p>", true),
                new ChoiceOption("a3", "<p>C</p>", false)),
            QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING);
        QuestionInput multipleInput = input(QuestionType.MULTIPLE_CHOICE, multiple);
        assertTrue(QuestionBodyValidator.validateSave(multipleInput, null).isEmpty());
        var multipleErrors = readyErrors(multipleInput);
        assertTrue(multipleErrors.stream().anyMatch(e -> e.code().equals("MATH_UNBALANCED")
            && e.path().equals("body.options[0].content")), multipleErrors.toString());
        assertTrue(multipleErrors.stream().noneMatch(e -> e.code().equals("MATH_UNBALANCED")
            && e.path().equals("body.options[1].content")));

        QuestionBody trueFalse = new QuestionBody.TrueFalseBody(QuestionType.TRUE_FALSE, List.of(
            new TrueFalseStatement("s1", bad, true, null),
            new TrueFalseStatement("s2", "<p>\\(y\\)</p>", false, null),
            new TrueFalseStatement("s3", "<p>Ý 3</p>", true, null),
            new TrueFalseStatement("s4", "<p>Ý 4</p>", false, null)),
            QuestionBody.TrueFalseScoringMethod.COUNT_TABLE,
            List.of(new BigDecimal("0.1"), new BigDecimal("0.25"), new BigDecimal("0.5"), new BigDecimal("1")));
        QuestionInput trueFalseInput = input(QuestionType.TRUE_FALSE, trueFalse);
        assertTrue(QuestionBodyValidator.validateSave(trueFalseInput, null).isEmpty());
        var trueFalseErrors = readyErrors(trueFalseInput);
        assertTrue(trueFalseErrors.stream().anyMatch(e -> e.code().equals("MATH_UNBALANCED")
            && e.path().equals("body.statements[0].content")), trueFalseErrors.toString());
        assertTrue(trueFalseErrors.stream().noneMatch(e -> e.code().equals("MATH_UNBALANCED")
            && e.path().equals("body.statements[1].content")));

        QuestionBody matching = new QuestionBody.MatchingBody(QuestionType.MATCHING,
            List.of(new MatchingSide("l1", bad), new MatchingSide("l2", "<p>\\(y\\)</p>")),
            List.of(new MatchingSide("r1", bad), new MatchingSide("r2", "<p>\\[z\\]</p>"),
                new MatchingSide("r3", "<p>Phải 3</p>")),
            List.of(new MatchingPair("l1", "r1", new BigDecimal("0.5")),
                new MatchingPair("l2", "r2", new BigDecimal("0.5"))),
            false);
        QuestionInput matchingInput = input(QuestionType.MATCHING, matching);
        assertTrue(QuestionBodyValidator.validateSave(matchingInput, null).isEmpty());
        var matchingErrors = readyErrors(matchingInput);
        for (String path : List.of("body.left[0].content", "body.right[0].content")) {
            assertTrue(matchingErrors.stream().anyMatch(e -> e.code().equals("MATH_UNBALANCED")
                && e.path().equals(path)), matchingErrors.toString());
        }
        assertTrue(matchingErrors.stream().noneMatch(e -> e.code().equals("MATH_UNBALANCED")
            && (e.path().equals("body.left[1].content") || e.path().equals("body.right[1].content"))));
    }


    private static List<QuestionFieldError> readyErrors(QuestionInput input) {
        return QuestionBodyValidator.validateReady(input, FULL, true, null).errors();
    }

    @Test
    void errorPathsPointAtFailingField() {
        QuestionBody body = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new ChoiceOption("a1", "<p>A</p>", true), new ChoiceOption("a2", "", false)));
        List<QuestionFieldError> errors = readyErrors(input(QuestionType.SINGLE_CHOICE, body));
        assertEquals("body.options[1].content",
            errors.stream().filter(e -> e.code().equals("OPTION_EMPTY")).findFirst().orElseThrow().path());
    }
}
