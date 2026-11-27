package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.unicom.thi360.shared.api.FieldError;

@Schema(description = "Lỗi 409 khi tài khoản còn điều kiện chặn không thể tự xóa")
public record AccountDeletionBlockedResponse(
        @Schema(description = "Mã lỗi", example = "ACCOUNT_DELETION_BLOCKED", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Thông báo lỗi theo ngôn ngữ yêu cầu", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(description = "Danh sách lỗi theo từng trường", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldError> fieldErrors,
        @Schema(description = "Mã định danh yêu cầu (X-Request-Id)", requiredMode = Schema.RequiredMode.REQUIRED)
        String traceId,
        @Schema(description = "Danh sách điều kiện đang chặn việc xóa", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AccountDeletionBlocker> blockers
) {
    public AccountDeletionBlockedResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
