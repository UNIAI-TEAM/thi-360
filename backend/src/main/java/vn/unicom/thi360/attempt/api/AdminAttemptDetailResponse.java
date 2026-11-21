package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewGradingDetail;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Schema(name = "AdminAttemptDetailResponse", description = "Chi tiết lượt thi cho vận hành kèm các phần, dòng sự kiện và bài làm cuối (LT §12, AC-15)")
public record AdminAttemptDetailResponse(
        UUID attemptId,
        UUID userId,
        String username,
        String userFullName,
        String userEmail,
        String userCode,
        UUID examId,
        int examVersionNo,
        String examName,
        String examKindCode,
        String scope,
        String partCode,
        AttemptTiming timing,
        NavigationMode navigationMode,
        Map<String, AttemptChoiceSummary> choices,
        Integer attemptNo,
        AttemptState state,
        boolean regrading,
        EndReason endReason,
        AbortReason abortReason,
        Instant startedAt,
        Instant deadlineAt,
        Instant finishedAt,
        Instant gradedAt,
        String gradingError,
        String practiceScoring,
        BigDecimal referenceMax,
        BigDecimal maxScore,
        BigDecimal rawScore,
        BigDecimal displayScore,
        String resultView,
        UUID entitlementId,
        UUID grantId,
        UUID editSessionId,
        Instant editSessionSeenAt,
        AttemptPosition lastPosition,
        String faultFlag,
        String faultReason,
        boolean compensated,
        Instant createdAt,
        Instant updatedAt,
        List<AdminAttemptPartView> parts,
        List<AdminAttemptEventView> events,
        List<AdminAttemptItemView> items) {

    @Schema(name = "AdminAttemptPartView")
    public record AdminAttemptPartView(
            UUID partId,
            String partCode,
            String partName,
            int ord,
            PartState state,
            Integer durationMinutes,
            Integer questionCountRequired,
            BigDecimal maxPoints,
            AttemptChoiceSummary choice,
            Instant choiceConfirmedAt,
            Instant choiceDeadlineAt,
            Instant startedAt,
            Instant deadlineAt,
            Instant closedAt,
            PartCloseReason closeReason,
            int generationTries,
            String generationError) {}

    @Schema(name = "AdminAttemptEventView")
    public record AdminAttemptEventView(
            UUID id,
            AttemptEventType type,
            UUID sessionId,
            UUID partId,
            Instant at,
            @Schema(type = "object", additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
            Map<String, Object> data) {}

    @Schema(name = "AdminAttemptItemView")
    public record AdminAttemptItemView(
            UUID itemId,
            UUID partId,
            UUID parentItemId,
            int ord,
            Integer displayNo,
            int unitCount,
            UUID questionId,
            int questionVersionNo,
            QuestionType questionType,
            BigDecimal pointsMax,
            boolean inScope,
            AnswerValue answer,
            long answerSeq,
            Instant answerReceivedAt,
            boolean flagged,
            BigDecimal earnedPoints,
            ItemOutcome outcome,
            ReviewGradingDetail gradingDetail) {}
}
