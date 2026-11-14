package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông tin thành viên trong lớp học")
public record ClassMemberResponse(
        @Schema(description = "ID tài khoản học viên", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Mã tài khoản")
        String userCode,
        @Schema(description = "Tên đăng nhập", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(description = "Họ và tên", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "Email")
        String email,
        @Schema(description = "Số điện thoại")
        String phone,
        @Schema(description = "Trạng thái tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        String userStatus,
        @Schema(description = "Thời điểm vào lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant joinedAt,
        @Schema(description = "ID người thêm vào lớp")
        UUID addedBy,
        @Schema(description = "Tên đăng nhập người thêm vào lớp")
        String addedByUsername
) {
}
