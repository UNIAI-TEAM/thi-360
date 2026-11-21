package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AnswerValue;

@Schema(description = "Thay đổi câu trả lời của một câu hỏi trong lượt thi (LT §7.1)")
public record AnswerChange(
        @NotNull(message = "{validation.attempt.answers.itemId.required}")
        @Schema(description = "Id câu hỏi trong lượt", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID itemId,
        @Positive(message = "{validation.attempt.answers.seq.positive}")
        @Schema(description = "Số thứ tự tăng dần của câu", requiredMode = Schema.RequiredMode.REQUIRED)
        long seq,
        @Valid
        @Schema(description = "Nội dung câu trả lời; null để xóa đáp án", nullable = true)
        AnswerValue answer) {}
