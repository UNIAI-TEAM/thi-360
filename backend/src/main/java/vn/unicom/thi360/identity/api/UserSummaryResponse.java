package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;

@Schema(description = "Thông tin tóm tắt tài khoản trong danh sách")
public record UserSummaryResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã tài khoản", example = "U000001", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên đăng nhập", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(description = "Họ và tên", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "Email")
        String email,
        @Schema(description = "Số điện thoại")
        String phone,
        @Schema(description = "Nguồn tạo tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UserSource source,
        @Schema(description = "Trạng thái tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus status,
        @Schema(description = "Bắt buộc đổi mật khẩu ở lần đăng nhập kế tiếp", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean mustChangePassword,
        @Schema(description = "Thời điểm đăng nhập gần nhất")
        Instant lastLoginAt,
        @Schema(description = "Thời điểm xóa mềm nếu đã xóa")
        Instant deletedAt,
        @Schema(description = "Danh sách vai trò của tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserRoleSummary> roles,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version,
        @Schema(description = "Thời điểm tạo", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
}
