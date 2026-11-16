package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.unicom.thi360.questionbank.domain.QuestionFieldError;

@Schema(description = "Kết quả kiểm tra điều kiện Sẵn sàng (lỗi chặn + cảnh báo không chặn)")
public record QuestionReadiness(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ready,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionFieldError> errors,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionFieldError> warnings) {}
