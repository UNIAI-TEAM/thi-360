package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.identity.domain.ImportBatchStatus;

@Schema(description = "Thông tin đợt cấp tài khoản hàng loạt")
public record ImportBatchResponse(
        @Schema(description = "ID đợt cấp tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Trạng thái xử lý của đợt", requiredMode = Schema.RequiredMode.REQUIRED)
        ImportBatchStatus status,
        @Schema(description = "Tên tệp gốc đã tải lên")
        String originalFilename,
        @Schema(description = "Bắt buộc đổi mật khẩu ở lần đăng nhập đầu", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean mustChangePassword,
        @Schema(description = "Tổng số dòng dữ liệu", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalRows,
        @Schema(description = "Số dòng hợp lệ", requiredMode = Schema.RequiredMode.REQUIRED)
        int validRows,
        @Schema(description = "Số dòng lỗi dữ liệu", requiredMode = Schema.RequiredMode.REQUIRED)
        int invalidRows,
        @Schema(description = "Số tài khoản đã tạo thành công", requiredMode = Schema.RequiredMode.REQUIRED)
        int createdRows,
        @Schema(description = "Số dòng tạo tài khoản thất bại", requiredMode = Schema.RequiredMode.REQUIRED)
        int failedRows,
        @Schema(description = "Đợt còn dòng lỗi dữ liệu hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasErrors,
        @Schema(description = "Có thể xác nhận chạy hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean canConfirm,
        @Schema(description = "Đợt có dòng để trống mật khẩu (hệ thống tự sinh)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasGeneratedPasswords,
        @Schema(description = "Tệp nguồn lỗi còn khả dụng hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean sourceAvailable,
        @Schema(description = "Tệp kết quả còn khả dụng hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean resultAvailable,
        @Schema(description = "Thời điểm xác nhận")
        Instant confirmedAt,
        @Schema(description = "Thời điểm bắt đầu xử lý")
        Instant startedAt,
        @Schema(description = "Thời điểm hoàn tất xử lý")
        Instant completedAt,
        @Schema(description = "Thời điểm tạo", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version
) {
}
