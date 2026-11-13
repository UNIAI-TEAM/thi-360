package vn.unicom.thi360.shared.appearance.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Yêu cầu cập nhật cấu hình giao diện toàn hệ thống")
public record UpdateAppearanceRequest(
        @Schema(
                description = "Bộ phối màu mới",
                requiredMode = Schema.RequiredMode.REQUIRED)
        AppearancePaletteDto palette,
        @Schema(
                description = "Chế độ sáng hoặc tối",
                example = "light",
                allowableValues = {"light", "dark"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String mode,
        @Schema(
                description = "Phiên bản hiện tại để kiểm tra khóa lạc quan",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long version
) {
}
