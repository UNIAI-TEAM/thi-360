package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Khoảng giá trị của các đường đang phục vụ; min bằng max khi mọi đường như nhau")
public record IntRange(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int min,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int max) {
}
