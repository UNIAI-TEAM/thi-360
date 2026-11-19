package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Khoảng điểm của các đường đang phục vụ; min bằng max khi mọi đường như nhau")
public record DecimalRange(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal min,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal max) {
}
