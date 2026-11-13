package vn.unicom.thi360.shared.appearance.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Yêu cầu khôi phục cấu hình giao diện mặc định")
public record ResetAppearanceRequest(
        @Schema(
                description = "Phiên bản hiện tại để kiểm tra khóa lạc quan",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long version
) {
}
