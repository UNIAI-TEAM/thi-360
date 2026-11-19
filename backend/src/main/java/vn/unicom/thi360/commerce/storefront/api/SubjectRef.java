package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một môn trong danh mục")
public record SubjectRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameVi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn) {
}
