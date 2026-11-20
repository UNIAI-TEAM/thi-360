package vn.unicom.thi360.attempt.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Trạng thái đầy đủ hoặc rút gọn của lượt thi cho giao diện làm bài (LT §7.1)")
public record AttemptStateResponse(
        @Schema(description = "Giờ máy chủ hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant serverNow,
        @Schema(description = "Id lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID attemptId,
        @Schema(description = "Trạng thái lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        AttemptState state,
        @Schema(description = "Lý do hủy khi state là ABORTED", nullable = true)
        AbortReason abortReason,
        @Schema(description = "Có thể bắt đầu lại (true khi ABORTED)", nullable = true)
        Boolean retryable,
        @Schema(nullable = true) UUID examId,
        @Schema(nullable = true) Integer examVersionNo,
        @Schema(nullable = true) String examName,
        @Schema(nullable = true) String examKindCode,
        @Schema(nullable = true) String scope,
        @Schema(nullable = true) String partCode,
        @Schema(nullable = true) AttemptTiming timing,
        @Schema(nullable = true) NavigationMode navigationMode,
        @Schema(nullable = true) Integer attemptNo,
        @Schema(nullable = true) Map<String, Object> choices,
        @Schema(nullable = true) Instant startedAt,
        @Schema(nullable = true) Instant deadlineAt,
        @Schema(nullable = true) Map<String, Object> lastPosition,
        @Schema(nullable = true) BigDecimal maxScore,
        @Schema(description = "Các phần của lượt thi; không có khi STARTING hoặc ABORTED", nullable = true)
        List<AttemptPartStateView> parts) {

    public AttemptStateResponse {
        parts = parts == null ? null : List.copyOf(parts);
    }

    public static AttemptStateResponse starting(UUID attemptId, Instant serverNow) {
        return new AttemptStateResponse(
                serverNow,
                attemptId,
                AttemptState.STARTING,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static AttemptStateResponse aborted(
            UUID attemptId, Instant serverNow, AbortReason abortReason) {
        return new AttemptStateResponse(
                serverNow,
                attemptId,
                AttemptState.ABORTED,
                abortReason,
                Boolean.TRUE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public record AttemptPartStateView(
            UUID partId,
            String partCode,
            String partName,
            int ord,
            PartState state,
            Integer durationMinutes,
            Integer questionCount,
            BigDecimal maxPoints,
            Instant startedAt,
            Instant deadlineAt,
            Instant closedAt,
            Map<String, Object> choice,
            Instant choiceDeadlineAt,
            List<AttemptItemView> items) {
        public AttemptPartStateView {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record AttemptItemView(
            UUID itemId,
            UUID parentItemId,
            Integer displayNo,
            int unitCount,
            QuestionType questionType,
            BigDecimal points,
            String sectionId,
            String componentCode,
            String blockId,
            List<String> optionOrder,
            boolean flagged,
            long answerSeq,
            AnswerValue answer,
            String content,
            AttemptQuestionBody questionBody) {
        public AttemptItemView {
            optionOrder = optionOrder == null ? List.of() : List.copyOf(optionOrder);
        }
    }
}
