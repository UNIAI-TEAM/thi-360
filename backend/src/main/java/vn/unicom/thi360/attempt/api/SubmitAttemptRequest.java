package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Yêu cầu nộp bài và kết thúc lượt thi (LT §6.3)")
public record SubmitAttemptRequest(
        @Schema(description = "Các câu trả lời cuối cùng trước khi nộp bài", nullable = true)
        List<
                        @NotNull(message = "{validation.attempt.finalAnswer.required}")
                        @Valid
                        FinalAnswerChange>
                finalAnswers) {

    public SubmitAttemptRequest {
        finalAnswers = finalAnswers == null ? List.of() : List.copyOf(finalAnswers);
    }
}
