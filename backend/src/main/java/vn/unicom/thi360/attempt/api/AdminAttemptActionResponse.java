package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;

@Schema(name = "AdminAttemptActionResponse", description = "Trạng thái lượt thi sau thao tác regrade hoặc đánh dấu hỏng (LT §12)")
public record AdminAttemptActionResponse(
        UUID attemptId,
        AttemptState state,
        BigDecimal rawScore,
        BigDecimal displayScore,
        BigDecimal maxScore,
        Instant gradedAt,
        String faultFlag,
        String faultReason,
        boolean compensated) {}
