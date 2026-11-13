package vn.unicom.thi360.shared.appearance.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cấu hình giao diện toàn hệ thống")
public record AppearanceResponse(
        @Schema(
                description = "Bộ phối màu đang áp dụng",
                requiredMode = Schema.RequiredMode.REQUIRED)
        AppearancePaletteDto palette,
        @Schema(
                description = "Chế độ sáng hoặc tối",
                example = "light",
                allowableValues = {"light", "dark"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String mode,
        @Schema(
                description = "Phiên bản cấu hình phục vụ khóa lạc quan và ETag",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long version
) {
}
