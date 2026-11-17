package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.unicom.thi360.questionbank.domain.QuestionBody.EssayCriterion;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator.ClassificationRequirement;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator.ReadinessResult;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Các ca Sẵn sàng đặc biệt của spec: câu Tự luận có hướng dẫn chấm mà không có tiêu chí vẫn đạt
 * (D20); câu chưa gắn MAPID vẫn Sẵn sàng được (D20); thiếu kiến thức chính chỉ là cảnh báo.
 */
class QuestionReadinessTests {

    private static final ClassificationRequirement FULL = new ClassificationRequirement(true, true, true);

    private static QuestionInput essay(String guide, List<EssayCriterion> rubric) {
        return new QuestionInput(QuestionType.ESSAY,
            new QuestionBody.EssayBody(QuestionType.ESSAY, guide, rubric, null, null),
            BigDecimal.ONE, "<p>Hãy phân tích bài thơ.</p>", null, List.of(), List.of());
    }

    @Test
    void essayGuideWithoutCriteriaReady() {
        // D20: rubric tùy chọn — có hướng dẫn chấm là đủ Sẵn sàng.
        ReadinessResult result = QuestionBodyValidator.validateReady(
            essay("<p>Chấm theo ý chính.</p>", List.of()), FULL, true, null);
        assertTrue(result.ready(), () -> "errors: " + result.errors());
    }

    @Test
    void essayWithoutGuideNotReady() {
        ReadinessResult result = QuestionBodyValidator.validateReady(
            essay(null, List.of()), FULL, true, null);
        assertFalse(result.ready());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("NO_GRADING_GUIDE")));
    }

    @Test
    void essayGuideWithMatchingRubricReady() {
        ReadinessResult result = QuestionBodyValidator.validateReady(
            essay("<p>Chấm theo ý.</p>", List.of(new EssayCriterion("c1", "<p>Ý</p>", BigDecimal.ONE))),
            FULL, true, null);
        assertTrue(result.ready(), () -> "errors: " + result.errors());
    }

    @Test
    void essayGuideWithMismatchedRubricFails() {
        ReadinessResult result = QuestionBodyValidator.validateReady(
            essay("<p>Chấm theo ý.</p>", List.of(new EssayCriterion("c1", "<p>Ý</p>", new BigDecimal("0.5")))),
            FULL, true, null);
        assertFalse(result.ready());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("RUBRIC_TOTAL_MISMATCH")));
    }

    @Test
    void readyWithoutMapid() {
        // D20: chưa gắn MAPID (không có kiến thức chính) vẫn Sẵn sàng — chỉ là cảnh báo,
        // câu sẽ không vào nguồn của khối có lọc MAPID.
        QuestionInput input = new QuestionInput(QuestionType.SINGLE_CHOICE,
            QuestionBodyValidationTests.singleChoiceBody(),
            BigDecimal.ONE, "<p>Nội dung</p>", null, List.of(), List.of());
        ReadinessResult result = QuestionBodyValidator.validateReady(input, FULL, false, null);
        assertTrue(result.ready(), () -> "errors: " + result.errors());
        assertTrue(result.warnings().stream().anyMatch(e -> e.code().equals("PRIMARY_KNOWLEDGE_MISSING")));
    }
    @Test
    void imageOnlyContentNotEmpty() {
        String image = "<img data-asset-id='00000000-0000-0000-0000-000000000001'>";
        QuestionInput input = new QuestionInput(QuestionType.SINGLE_CHOICE,
            QuestionBodyValidationTests.singleChoiceBody(), BigDecimal.ONE, image, null, List.of(), List.of());
        ReadinessResult result = QuestionBodyValidator.validateReady(input, FULL, true, null);
        assertTrue(result.ready(), () -> "errors: " + result.errors());
        assertFalse(QuestionBodyValidator.isBlankHtml(image));
        assertTrue(QuestionBodyValidator.isBlankHtml("<p> </p>"));
    }

    // --------------------------------------------------------- spec 6 (UNI-2638)

    private static QuestionInput input(QuestionType type, QuestionBody body, String content) {
        return new QuestionInput(type, body, BigDecimal.ONE, content, null, List.of(), List.of());
    }

    private static QuestionBody setBody(String passage, List<QuestionBody.ChildQuestion> children) {
        return new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET, passage, null, children);
    }

    private static QuestionBody.ChildQuestion child(String content) {
        return new QuestionBody.ChildQuestion(QuestionType.SHORT_ANSWER, content,
            QuestionBodyValidationTests.shortAnswerBody(), null, null, null, null, List.of(), null);
    }

    private static boolean has(ReadinessResult result, String code, String path) {
        return result.errors().stream()
            .anyMatch(e -> e.code().equals(code) && e.path().equals(path));
    }

    @Test
    void blankInStemFlagsOutsideSetPassage() {
        String stem = "<p>Điền <span class=\"blank\" data-blank=\"1\"></span> vào chỗ trống.</p>";
        ReadinessResult result = QuestionBodyValidator.validateReady(
            input(QuestionType.SINGLE_CHOICE,
                QuestionBodyValidationTests.singleChoiceBody(), stem),
            FULL, true, null);
        assertTrue(has(result, "BLANK_OUTSIDE_SET_PASSAGE", "content"));
    }

    @Test
    void blankInSetPassageMatchingChildrenPasses() {
        String passage = "<p>Điền <span class=\"blank\" data-blank=\"1\"></span> và "
            + "<span class=\"blank\" data-blank=\"2\"></span>.</p>";
        ReadinessResult result = QuestionBodyValidator.validateReady(
            input(QuestionType.QUESTION_SET,
                setBody(passage, List.of(child("<p>Câu 1</p>"), child("<p>Câu 2</p>"))),
                "<p>Đọc đoạn sau.</p>"),
            FULL, true, null);
        assertTrue(result.errors().stream()
            .noneMatch(e -> e.code().startsWith("BLANK_")), () -> "errors: " + result.errors());
    }

    @Test
    void setContentEqualToPassageIsCheckedOnlyAsPassage() {
        // Trình soạn web gửi content = body.passage cho câu chùm (ED-4 E2E).
        String passage = "<p>Điền <span class=\"blank\" data-blank=\"1\"></span> và "
            + "<span class=\"blank\" data-blank=\"2\"></span>.</p>";
        ReadinessResult ok = QuestionBodyValidator.validateReady(
            input(QuestionType.QUESTION_SET,
                setBody(passage, List.of(child("<p>Câu 1</p>"), child("<p>Câu 2</p>"))),
                passage),
            FULL, true, null);
        assertTrue(ok.errors().stream().noneMatch(e -> e.code().startsWith("BLANK_")),
            () -> "errors: " + ok.errors());

        ReadinessResult mismatch = QuestionBodyValidator.validateReady(
            input(QuestionType.QUESTION_SET,
                setBody(passage, List.of(child("<p>Câu 1</p>"))),
                passage),
            FULL, true, null);
        assertTrue(has(mismatch, "BLANK_CHILD_MISMATCH", "body.passage"));
        assertFalse(mismatch.errors().stream().anyMatch(e -> e.path().equals("content")),
            () -> "errors: " + mismatch.errors());

        ReadinessResult differing = QuestionBodyValidator.validateReady(
            input(QuestionType.QUESTION_SET,
                setBody("<p>Đoạn khác.</p>", List.of(child("<p>Câu 1</p>"))),
                "<p>Điền <span class=\"blank\" data-blank=\"1\"></span>.</p>"),
            FULL, true, null);
        assertTrue(has(differing, "BLANK_OUTSIDE_SET_PASSAGE", "content"));
    }

    @Test
    void blankCountMismatchInSet() {
        String passage = "<p><span class=\"blank\" data-blank=\"1\"></span>"
            + "<span class=\"blank\" data-blank=\"2\"></span>"
            + "<span class=\"blank\" data-blank=\"3\"></span></p>";
        ReadinessResult result = QuestionBodyValidator.validateReady(
            input(QuestionType.QUESTION_SET,
                setBody(passage, List.of(child("<p>Câu 1</p>"))),
                "<p>Đọc đoạn sau.</p>"),
            FULL, true, null);
        assertTrue(has(result, "BLANK_CHILD_MISMATCH", "body.passage"));
    }

    @Test
    void blankInsideChildContentIsOutsideSetPassage() {
        String childContent = "<p>Điền <span class=\"blank\" data-blank=\"1\"></span>.</p>";
        ReadinessResult result = QuestionBodyValidator.validateReady(
            input(QuestionType.QUESTION_SET,
                setBody("<p>Đoạn đọc.</p>", List.of(child(childContent))),
                "<p>Đọc đoạn sau.</p>"),
            FULL, true, null);
        assertTrue(has(result, "BLANK_OUTSIDE_SET_PASSAGE", "body.children[0].content"));
    }

    @Test
    void underlineLabelsDuplicateAndMisplaced() {
        String stem = "<p><u data-label=\"A\">x</u> <u data-label=\"C\">y</u> "
            + "<u data-label=\"A\">z</u></p>";
        ReadinessResult result = QuestionBodyValidator.validateReady(
            input(QuestionType.MULTIPLE_CHOICE,
                QuestionBodyValidationTests.multipleChoiceBody(), stem),
            FULL, true, null);
        assertTrue(has(result, "UNDERLINE_LABEL_DUPLICATE", "content"));
        assertTrue(has(result, "UNDERLINE_LABEL_MISPLACED", "content"));
    }

    @Test
    void underlineLabelsInShortAnswerChildrenAreMisplaced() {
        ReadinessResult result = QuestionBodyValidator.validateReady(
            input(QuestionType.QUESTION_SET,
                setBody("<p>Đoạn đọc.</p>", List.of(
                    child("<p><u data-label=\"B\">x</u></p>"),
                    child("<p><u data-label=\"A\">y</u></p>"))),
                "<p>Đọc đoạn sau.</p>"),
            FULL, true, null);
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.code().equals("UNDERLINE_LABEL_MISPLACED")
                && e.path().equals("body.children[1].content")), () -> "errors: " + result.errors());
    }

    @Test
    void imageWithoutAltWarnsNotFails() {
        String image = "<img data-asset-id=\"00000000-0000-0000-0000-000000000001\">";
        ReadinessResult result = QuestionBodyValidator.validateReady(
            input(QuestionType.SINGLE_CHOICE,
                QuestionBodyValidationTests.singleChoiceBody(), image),
            FULL, true, null);
        assertTrue(result.warnings().stream()
            .anyMatch(e -> e.code().equals("IMAGE_ALT_MISSING") && e.path().equals("content")));
        assertTrue(result.errors().stream()
            .noneMatch(e -> e.code().equals("IMAGE_ALT_MISSING")));
    }
    @Test
    void optionReferencesWarnOnlyWhenShuffled() {
        List<String> positive = List.of("A và B", "A, B và C", "cả A", "phương án A",
            "đáp án A", "câu A", "A hoặc B", "A and B", "both A", "option A",
            "tất cả các phương án trên", "tất cả đều đúng", "cả hai", "cả ba",
            "không có đáp án nào", "đáp án khác", "các phương án trên",
            "all of the above", "none of the above", "both of the above", "neither",
            "<strong>CẢ A</strong> và B");
        List<String> negative = List.of("Asia", "vitamin A", "Phương án đúng là số dương",
            "A", "B", "C là số dương", "optionally", "câu chuyện", "không có đáp số");
        for (String text : positive) {
            assertTrue(optionWarnings(text, true).stream().anyMatch(w -> w.path().equals("body.options[0].content")), text);
            assertTrue(optionWarnings(text, false).isEmpty(), text);
        }
        for (String text : negative) {
            assertTrue(optionWarnings(text, true).isEmpty(), text);
        }
    }

    private static List<QuestionFieldError> optionWarnings(String text, boolean shuffle) {
        var body = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new QuestionBody.ChoiceOption("a", text, true),
            new QuestionBody.ChoiceOption("b", "other", false)), shuffle);
        return QuestionBodyValidator.validateReady(input(QuestionType.SINGLE_CHOICE, body, "Question"),
            FULL, true, vn.unicom.thi360.questionbank.spi.QuestionAssetResolver.none()).warnings().stream()
            .filter(w -> w.code().equals("OPTION_REFERS_TO_OTHERS")).toList();
    }

    @Test
    void multipleChoiceReferenceWarnsWithoutBlockingReady() {
        var options = List.of(new QuestionBody.ChoiceOption("a", "A và B", true),
            new QuestionBody.ChoiceOption("b", "other", true));
        for (boolean shuffle : List.of(true, false)) {
            var body = new QuestionBody.MultipleChoiceBody(QuestionType.MULTIPLE_CHOICE, options,
                QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING, shuffle);
            var result = QuestionBodyValidator.validateReady(input(QuestionType.MULTIPLE_CHOICE, body, "Question"),
                FULL, true, vn.unicom.thi360.questionbank.spi.QuestionAssetResolver.none());
            assertTrue(result.ready());
            assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("OPTION_REFERS_TO_OTHERS")
                && w.path().equals("body.options[0].content")) == shuffle);
        }
    }

    @Test
    void optionReferenceInSetChildHasNestedPath() {
        var choice = new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, List.of(
            new QuestionBody.ChoiceOption("a", "A và B", true),
            new QuestionBody.ChoiceOption("b", "other", false)), true);
        var nested = new QuestionBody.ChildQuestion(QuestionType.SINGLE_CHOICE, "Question",
            choice, null, null, null, null, List.of(), null);
        var set = setBody("Passage", List.of(nested, child("Question")));
        var result = QuestionBodyValidator.validateReady(input(QuestionType.QUESTION_SET, set, "Passage"),
            FULL, true, vn.unicom.thi360.questionbank.spi.QuestionAssetResolver.none());
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("OPTION_REFERS_TO_OTHERS")
            && w.path().equals("body.children[0].body.options[0].content")));
    }
}
