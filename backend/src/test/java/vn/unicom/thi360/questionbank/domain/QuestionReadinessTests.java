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
}
