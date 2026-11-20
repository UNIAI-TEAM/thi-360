package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Cảnh báo khuyến mãi tự động bị kẹp ở mức tối thiểu trên một bài thi (BS §10, AC-41)")
public record CapWarning(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String examName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long listPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discountedPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long floorPrice
) {
}
