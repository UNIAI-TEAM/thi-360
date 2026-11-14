package vn.unicom.thi360.shared.appearance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Cấu hình bộ phối màu giao diện")
public record AppearancePaletteDto(
        @Schema(
                description = "Mã bộ phối màu cố định hoặc 'custom'",
                example = "thi360-green",
                allowableValues = {
                    "thi360-green",
                    "emerald-gold",
                    "charcoal-teal",
                    "navy-teal",
                    "blue-silver",
                    "sky-orange",
                    "indigo-amber",
                    "indigo-coral",
                    "slate-crimson",
                    "custom"
                },
                requiredMode = Schema.RequiredMode.REQUIRED)
        String presetId,
        @Schema(
                description = "Màu tùy chỉnh; bắt buộc khi presetId = 'custom' và được giữ lại khi chọn bộ cố định",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        AppearanceCustomColorsDto custom
) {
}
