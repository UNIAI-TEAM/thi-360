package vn.unicom.thi360.commerce.pricing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu báo giá sản phẩm kèm voucher tùy chọn (TM 10.2)")
public record PriceQuoteRequest(
        @Schema(description = "Mã sản phẩm", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.priceQuote.code.notBlank}")
        String code,
        @Schema(description = "Mã voucher tùy chọn", nullable = true)
        String voucherCode,
        @Schema(description = "Phiên bản sản phẩm học viên đã thấy", nullable = true)
        Integer seenVersionNo,
        @Schema(description = "Giá cuối học viên đã thấy", nullable = true)
        Long seenPrice
) {
}
