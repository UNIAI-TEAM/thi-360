package vn.unicom.thi360.identity.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public interface AccountDeletionGuard {

    List<Blocker> check(UUID userId);

    @Schema(description = "Nguồn dữ liệu đang chặn xóa tài khoản")
    record Blocker(
            @Schema(description = "Tên module hoặc nguồn chặn", example = "COMMERCE", requiredMode = Schema.RequiredMode.REQUIRED)
            String source,
            @Schema(description = "Mã lý do chặn", example = "USER_HAS_ORDERS", requiredMode = Schema.RequiredMode.REQUIRED)
            String code,
            @Schema(description = "Thông báo chi tiết tiếng Việt", requiredMode = Schema.RequiredMode.REQUIRED)
            String message
    ) {
        public Blocker(String source, String message) {
            this(source, "USER_HAS_TRANSACTIONS", message);
        }
    }
}
