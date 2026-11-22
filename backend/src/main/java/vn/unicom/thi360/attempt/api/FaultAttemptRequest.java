package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "FaultAttemptRequest", description = "Yêu cầu đánh dấu lượt thi bị lỗi hệ thống (LT §12, F20)")
public record FaultAttemptRequest(
        @NotBlank(message = "{validation.attempt.admin.reason.notBlank}")
        @Size(max = 1000, message = "{validation.attempt.admin.reason.size}")
        String reason) {}
