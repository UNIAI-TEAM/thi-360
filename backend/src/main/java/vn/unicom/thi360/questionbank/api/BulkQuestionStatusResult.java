package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Kết quả đổi trạng thái hàng loạt; các câu bị bỏ qua kèm lý do")
public record BulkQuestionStatusResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionSummary> updated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Skipped> skipped) {

    @Schema(description = "Câu bị bỏ qua kèm lý do: NOT_FOUND, ALREADY_IN_STATUS hoặc NOT_READY")
    public record Skipped(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID questionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
            String detail) {}
}
