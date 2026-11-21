package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "GradingItemDetailResponse", description = "Chi tiết câu tự luận cần chấm theo đúng phiên bản đã phát (ẩn danh học viên)")
public record GradingItemDetailResponse(
        UUID itemId,
        UUID attemptId,
        UUID examId,
        String examName,
        String examKindCode,
        String partCode,
        String partName,
        Integer displayNo,
        Instant submittedAt,
        boolean regrading,
        UUID questionId,
        int questionVersionNo,
        BigDecimal pointsMax,
        GradingQueueState state,
        String content,
        String passage,
        String answerHtml,
        int wordCount,
        Integer wordLimit,
        String gradingGuide,
        List<RubricCriterionView> rubric,
        String sampleAnswer,
        EssayGradeView activeGrade,
        List<EssayGradeView> history) {

    @Schema(name = "GradingRubricCriterionView")
    public record RubricCriterionView(String id, String description, BigDecimal points) {}
}
