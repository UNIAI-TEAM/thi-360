package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CompensateAttemptRequest", description = "Yêu cầu cấp bù 1 lượt cho lượt thi đã đánh dấu lỗi hệ thống (LT §12, F18)")
public record CompensateAttemptRequest(
        @NotBlank(message = "{validation.attempt.admin.reason.notBlank}")
        @Size(max = 1000, message = "{validation.attempt.admin.reason.size}")
        String reason) {}
