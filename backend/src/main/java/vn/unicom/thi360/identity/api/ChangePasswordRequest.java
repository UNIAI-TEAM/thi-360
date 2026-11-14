package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu đổi mật khẩu")
public record ChangePasswordRequest(
        @Schema(description = "Mật khẩu hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.password.old.notBlank}")
        String oldPassword,

        @Schema(description = "Mật khẩu mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.password.new.notBlank}")
        String newPassword
) {
}
