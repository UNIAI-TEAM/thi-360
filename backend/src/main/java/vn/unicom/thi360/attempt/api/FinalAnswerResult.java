package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Kết quả xử lý từng câu trong finalAnswers khi hoàn thành phần hoặc nộp bài (LT §6.3)")
public record FinalAnswerResult(
        @Schema(description = "Id câu hỏi trong lượt", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID itemId,
        @Schema(description = "Số thứ tự đã gửi", requiredMode = Schema.RequiredMode.REQUIRED)
        long seq,
        @Schema(description = "Trạng thái lưu đáp án", requiredMode = Schema.RequiredMode.REQUIRED)
        FinalAnswerStatus status,
        @Schema(description = "Mã lý do từ chối khi status là REJECTED", nullable = true)
        String reason) {}
