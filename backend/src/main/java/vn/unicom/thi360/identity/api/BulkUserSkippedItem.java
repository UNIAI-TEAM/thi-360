package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Tài khoản bị bỏ qua khi xem trước thao tác hàng loạt")
public record BulkUserSkippedItem(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Tên đăng nhập")
        String username,
        @Schema(description = "Mã lý do bỏ qua", example = "USER_HAS_TRANSACTIONS", requiredMode = Schema.RequiredMode.REQUIRED)
        String reasonCode,
        @Schema(description = "Lý do bỏ qua bằng tiếng Việt", requiredMode = Schema.RequiredMode.REQUIRED)
        String reason
) {
}
