package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu cập nhật thông tin tài khoản")
public record UpdateUserRequest(
        @Schema(description = "Họ và tên mới")
        @Size(max = 200, message = "{validation.user.fullName.size}")
        String fullName,
        @Schema(description = "Email mới (đổi email sẽ xóa trạng thái xác minh email)")
        @Size(max = 255, message = "{validation.user.email.size}")
        String email,
        @Schema(description = "Số điện thoại mới")
        @Size(max = 30, message = "{validation.user.phone.size}")
        String phone,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.version.notNull}")
        Long version
) {
}
