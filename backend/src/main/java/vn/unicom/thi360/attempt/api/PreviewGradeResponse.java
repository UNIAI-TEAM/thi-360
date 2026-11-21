package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.UnitStatsView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewGradingDetail;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Kết quả chấm bài làm thử đề xem trước — hoàn toàn không lưu DB hay trừ quota (AC-17..AC-20). */
@Schema(name = "PreviewGradeResponse")
public record PreviewGradeResponse(
        UUID examId,
        UUID previewId,
        String kindCode,
        BigDecimal rawScore,
        BigDecimal maxScore,
        boolean hasPendingManualGrading,
        UnitStatsView statistics,
        List<PreviewGradedItemView> items) {

    @Schema(name = "PreviewGradedItem")
    public record PreviewGradedItemView(
            UUID questionId,
            int number,
            QuestionType type,
            ItemOutcome outcome,
            BigDecimal earnedPoints,
            BigDecimal maxPoints,
            boolean pendingReview,
            ReviewGradingDetail gradingDetail) {}
}
