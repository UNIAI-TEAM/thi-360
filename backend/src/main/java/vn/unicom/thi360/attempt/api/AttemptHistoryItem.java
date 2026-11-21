package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;

@Schema(
        name = "AttemptHistoryItem",
        description = "Một dòng trong lịch sử luyện thi của học viên (LT §11)")
public record AttemptHistoryItem(
        UUID attemptId,
        UUID examId,
        int examVersionNo,
        String examName,
        String examKindCode,
        String scope,
        String partCode,
        String subjectCode,
        Map<String, Object> choices,
        Integer attemptNo,
        Instant startedAt,
        Instant finishedAt,
        Instant gradedAt,
        AttemptState state,
        boolean regrading,
        String regradingLabel,
        String practiceScoring,
        BigDecimal rawScore,
        BigDecimal displayScore,
        BigDecimal maxScore,
        BigDecimal scaleMax,
        HistoryAction action,
        String resultView,
        String faultFlag,
        String faultReason,
        boolean compensated) {

    @Schema(name = "AttemptHistoryAction")
    public enum HistoryAction {
        CONTINUE,
        VIEW_RESULT
    }
}
