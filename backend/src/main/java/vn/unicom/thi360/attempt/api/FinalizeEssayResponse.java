package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;

@Schema(name = "FinalizeEssayResponse", description = "Kết quả hoàn tất chấm câu tự luận và tổng hợp điểm lượt thi")
public record FinalizeEssayResponse(
        EssayGradeView grade,
        UUID attemptId,
        AttemptState attemptState,
        BigDecimal rawScore,
        BigDecimal displayScore,
        BigDecimal maxScore,
        Instant gradedAt) {}
