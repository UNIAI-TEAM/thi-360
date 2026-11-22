package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Vị trí phần hoặc câu đang xem của học viên trong lượt thi (LT §7.1)")
public record AttemptPosition(
        @Schema(description = "Id phần thi đang xem", nullable = true)
        UUID partId,
        @Schema(description = "Id câu hỏi trong lượt đang xem", nullable = true)
        UUID itemId) {}
