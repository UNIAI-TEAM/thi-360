package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một lựa chọn danh mục (mã + tên theo ngôn ngữ của request)")
public record CatalogOption(
        @Schema(description = "Mã giá trị", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        String name
) {
}
