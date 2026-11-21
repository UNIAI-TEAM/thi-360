package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "GradingQueueItemView", description = "Một câu tự luận trong hàng chờ chấm (ẩn danh hoàn toàn thông tin học viên)")
public record GradingQueueItemView(
        UUID itemId,
        UUID attemptId,
        UUID examId,
        String examName,
        String examKindCode,
        String partCode,
        String partName,
        Integer displayNo,
        BigDecimal pointsMax,
        Instant submittedAt,
        GradingQueueState state,
        boolean regrading,
        UUID graderId,
        String graderName,
        boolean claimExpired,
        Instant claimedAt,
        Instant lastActivityAt,
        Instant finalizedAt,
        BigDecimal earnedPoints) {}
