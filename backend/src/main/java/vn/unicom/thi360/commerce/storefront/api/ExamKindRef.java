package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kỳ thi của sản phẩm")
public record ExamKindRef(
        @Schema(description = "Mã kỳ thi trong danh mục", example = "HSA", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameVi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn) {
}
