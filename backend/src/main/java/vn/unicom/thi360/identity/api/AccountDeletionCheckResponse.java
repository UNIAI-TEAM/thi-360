package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Kết quả kiểm tra điều kiện tự xóa tài khoản học viên")
public record AccountDeletionCheckResponse(
        @Schema(description = "Tài khoản có thể xóa ngay lúc này hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean canDelete,
        @Schema(description = "Danh sách điều kiện đang chặn việc xóa", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AccountDeletionBlocker> blockers,
        @Schema(description = "Tổng số lượt thi còn dùng được của mọi quyền thi", requiredMode = Schema.RequiredMode.REQUIRED)
        int unusedAttempts,
        @Schema(description = "Có lượt thi đã nộp đang chờ chấm hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean pendingGrading
) {
    public AccountDeletionCheckResponse {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
