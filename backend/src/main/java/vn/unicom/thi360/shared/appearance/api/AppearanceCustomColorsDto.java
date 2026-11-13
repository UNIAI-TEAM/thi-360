package vn.unicom.thi360.shared.appearance.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cặp màu tùy chỉnh gồm màu chính và màu nhấn")
public record AppearanceCustomColorsDto(
        @Schema(
                description = "Mã màu chính định dạng #RRGGBB",
                example = "#059669",
                pattern = "^#[0-9A-Fa-f]{6}$",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String primary,
        @Schema(
                description = "Mã màu nhấn định dạng #RRGGBB",
                example = "#F59223",
                pattern = "^#[0-9A-Fa-f]{6}$",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String accent
) {
}
