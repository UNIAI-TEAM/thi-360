package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;

@Schema(name = "AdminAttemptSummary", description = "Một dòng lượt thi trong danh sách vận hành (LT §12)")
public record AdminAttemptSummary(
        UUID attemptId,
        UUID userId,
        String username,
        String userFullName,
        UUID examId,
        int examVersionNo,
        String examName,
        String examKindCode,
        String scope,
        String partCode,
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
        BigDecimal rawScore,
        BigDecimal displayScore,
        BigDecimal maxScore,
        String faultFlag,
        String faultReason,
        boolean compensated,
        Instant createdAt) {}
