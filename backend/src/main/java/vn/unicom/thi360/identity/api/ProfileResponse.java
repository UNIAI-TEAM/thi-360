package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Hồ sơ của tài khoản đang đăng nhập (TM 5.5)")
public record ProfileResponse(
        @Schema(description = "Tên đăng nhập, chỉ đọc", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        @Schema(description = "Họ và tên", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,

        @Schema(types = {"string", "null"}, description = "Email của tài khoản; null khi tài khoản không có email")
        String email,

        @Schema(description = "Email đã xác minh hay chưa", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean emailVerified,

        @Schema(types = {"string", "null"}, description = "Số điện thoại; null khi chưa nhập", example = "0912345678")
        String phone,

        @Schema(types = {"string", "null"}, format = "uuid", description = "Mã ảnh đại diện; null khi chưa có ảnh")
        UUID avatarAssetId,

        @Schema(types = {"string", "null"}, description = "Đường dẫn ảnh đại diện (cần đăng nhập); null khi chưa có ảnh",
                example = "/api/v1/avatars/0192f3a1-0000-7000-8000-000000000001")
        String avatarUrl,

        @Schema(types = {"string", "null"},
                description = "Email mới đang chờ xác nhận (đổi email, TM 5.3); null khi không có yêu cầu đổi email")
        String pendingEmail
) {
}
