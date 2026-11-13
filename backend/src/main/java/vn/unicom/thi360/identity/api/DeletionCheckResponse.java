package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.unicom.thi360.identity.spi.AccountDeletionGuard;

@Schema(description = "Kết quả kiểm tra điều kiện xóa tài khoản")
public record DeletionCheckResponse(
        @Schema(description = "Có thể xóa mềm tài khoản hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean deletable,
        @Schema(description = "Danh sách nguồn dữ liệu đang chặn xóa", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AccountDeletionGuard.Blocker> blockers
) {
}
