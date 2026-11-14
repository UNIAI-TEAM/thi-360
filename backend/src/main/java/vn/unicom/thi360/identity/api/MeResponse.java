package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.Realm;

@Schema(description = "Thông tin người dùng hiện tại")
public record MeResponse(
        @Schema(description = "ID người dùng (UUID)", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Tên đăng nhập", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        @Schema(description = "Họ và tên hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,

        @Schema(description = "Realm xác thực", requiredMode = Schema.RequiredMode.REQUIRED)
        Realm realm,

        @Schema(description = "Phạm vi truy cập", requiredMode = Schema.RequiredMode.REQUIRED)
        Access access,

        @Schema(description = "Danh sách mã quyền hiệu lực", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> permissions,

        @Schema(description = "Trang chủ mặc định theo vai trò", example = "http://localhost:3000/home", requiredMode = Schema.RequiredMode.REQUIRED)
        String homeUrl
) {
}
