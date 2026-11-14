package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.identity.domain.ImportRowError;
import vn.unicom.thi360.identity.domain.ImportRowStatus;

@Schema(description = "Chi tiết một dòng trong đợt cấp tài khoản hàng loạt (không bao giờ trả mật khẩu)")
public record ImportRowResponse(
        @Schema(description = "ID dòng", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Số dòng trong tệp Excel (1-based)", requiredMode = Schema.RequiredMode.REQUIRED)
        int rowNo,
        @Schema(description = "Tên đăng nhập")
        String username,
        @Schema(description = "Họ và tên")
        String fullName,
        @Schema(description = "Email")
        String email,
        @Schema(description = "Số điện thoại")
        String phone,
        @Schema(description = "Mã lớp")
        String classCode,
        @Schema(description = "Có phải dòng để trống mật khẩu (hệ thống tự sinh)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean passwordGenerated,
        @Schema(description = "Trạng thái dòng", requiredMode = Schema.RequiredMode.REQUIRED)
        ImportRowStatus status,
        @Schema(description = "Danh sách lỗi dữ liệu của dòng", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ImportRowError> errors,
        @Schema(description = "ID tài khoản đã tạo nếu thành công")
        UUID createdUserId,
        @Schema(description = "Mã lỗi kỹ thuật nếu tạo thất bại")
        String failureCode,
        @Schema(description = "Nguyên nhân thất bại nếu tạo thất bại")
        String failureReason,
        @Schema(description = "Thời điểm xử lý dòng")
        Instant processedAt
) {
}
