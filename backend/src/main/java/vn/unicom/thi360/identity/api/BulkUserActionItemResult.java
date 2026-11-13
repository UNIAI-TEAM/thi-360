package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Kết quả xử lý một tài khoản trong thao tác hàng loạt")
public record BulkUserActionItemResult(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Tên đăng nhập")
        String username,
        @Schema(description = "Kết quả xử lý", requiredMode = Schema.RequiredMode.REQUIRED)
        BulkUserItemOutcome outcome,
        @Schema(description = "Mã lý do nếu bị bỏ qua hoặc thất bại")
        String reasonCode,
        @Schema(description = "Thông báo chi tiết nếu bị bỏ qua hoặc thất bại")
        String reason
) {
}
