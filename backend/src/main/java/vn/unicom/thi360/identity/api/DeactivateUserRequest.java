package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu ngừng hoạt động tài khoản")
public record DeactivateUserRequest(
        @Schema(description = "Lý do ngừng hoạt động (bắt buộc)", example = "Vi phạm quy chế thi", requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 500, message = "{validation.reason.size}")
        String reason
) {
}
