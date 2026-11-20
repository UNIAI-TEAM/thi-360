package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AnswerValue;

@Schema(description = "Thay đổi câu trả lời cuối cùng gửi kèm khi hoàn thành phần hoặc nộp bài (LT §6.3, §7.1)")
public record FinalAnswerChange(
        @NotNull(message = "{validation.attempt.finalAnswer.itemId.required}")
        @Schema(description = "Id câu hỏi trong lượt", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID itemId,
        @Positive(message = "{validation.attempt.finalAnswer.seq.positive}")
        @Schema(description = "Số thứ tự tăng dần của câu", requiredMode = Schema.RequiredMode.REQUIRED)
        long seq,
        @Valid
        @Schema(description = "Nội dung câu trả lời; null để xóa đáp án", nullable = true)
        AnswerValue answer) {}
