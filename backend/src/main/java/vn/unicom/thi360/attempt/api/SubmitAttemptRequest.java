package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Schema(description = "Yêu cầu nộp bài và kết thúc lượt thi (LT §6.3)")
public record SubmitAttemptRequest(
        @Size(max = 500, message = "{validation.attempt.finalAnswers.max}")
        @Schema(description = "Các câu trả lời cuối cùng trước khi nộp bài", nullable = true)
        List<
                        @NotNull(message = "{validation.attempt.finalAnswer.required}")
                        @Valid
                        FinalAnswerChange>
                finalAnswers) {

    public SubmitAttemptRequest {
        // Keep null elements intact so Bean Validation reports @NotNull field errors instead of failing in Jackson.
        finalAnswers =
                finalAnswers == null
                        ? List.of()
                        : Collections.unmodifiableList(new ArrayList<>(finalAnswers));
    }
}
