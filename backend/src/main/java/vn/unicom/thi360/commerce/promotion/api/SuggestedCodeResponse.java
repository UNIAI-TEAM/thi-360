package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mã voucher gợi ý ngẫu nhiên, duy nhất")
public record SuggestedCodeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code
) {
}
