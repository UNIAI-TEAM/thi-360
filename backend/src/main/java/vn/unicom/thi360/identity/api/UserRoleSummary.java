package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.shared.security.Access;

@Schema(description = "Thông tin tóm tắt vai trò của tài khoản")
public record UserRoleSummary(
        @Schema(description = "ID vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Phạm vi truy cập của vai trò", requiredMode = Schema.RequiredMode.REQUIRED)
        Access access
) {
}
