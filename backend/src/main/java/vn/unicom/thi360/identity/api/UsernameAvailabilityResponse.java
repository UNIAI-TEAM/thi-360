package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả kiểm tra tính khả dụng của tên đăng nhập")
public record UsernameAvailabilityResponse(
        @Schema(description = "Tên đăng nhập hợp lệ và chưa được sử dụng", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean available,
        @Schema(description = "Mã lý do nếu không khả dụng", example = "USERNAME_TAKEN")
        String reasonCode
) {
}
