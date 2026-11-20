package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.unicom.thi360.shared.api.FieldError;

@Schema(description = "Lỗi 410 PRODUCT_DISCONTINUED: định dạng lỗi chung cộng product, vì code của lỗi chung là mã lỗi")
public record ProductGoneResponse(
        @Schema(example = "PRODUCT_DISCONTINUED", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FieldError> fieldErrors,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String traceId,
        @Schema(description = "Sản phẩm đã ngừng bán", requiredMode = Schema.RequiredMode.REQUIRED) Product product) {

    @Schema(description = "Mã và tên của sản phẩm đã ngừng bán")
    public record Product(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
    }
}
