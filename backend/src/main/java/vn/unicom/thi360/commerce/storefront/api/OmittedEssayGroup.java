package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Nhóm tự luận tạm bỏ qua: tên và điểm phần đó đã mang (TM 10.1, 2B K1)")
public record OmittedEssayGroup(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(nullable = true) BigDecimal points) {
}
