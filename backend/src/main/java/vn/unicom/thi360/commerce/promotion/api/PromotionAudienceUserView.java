package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Học viên được chỉ định áp dụng voucher")
public record PromotionAudienceUserView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(nullable = true) String username,
        @Schema(nullable = true) String fullName
) {
}
