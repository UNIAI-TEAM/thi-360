package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Yêu cầu hoàn thành phần hiện tại của lượt thi tuần tự (LT §6.3)")
public record FinishPartRequest(
        @Schema(description = "Các câu trả lời cuối cùng của phần trước khi đóng", nullable = true)
        List<
                        @NotNull(message = "{validation.attempt.finalAnswer.required}")
                        @Valid
                        FinalAnswerChange>
                finalAnswers) {

    public FinishPartRequest {
        finalAnswers = finalAnswers == null ? List.of() : List.copyOf(finalAnswers);
    }
}
