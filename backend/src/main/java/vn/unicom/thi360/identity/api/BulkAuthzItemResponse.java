package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Kết quả xử lý từng tài khoản trong thao tác gán hàng loạt")
public record BulkAuthzItemResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Tên đăng nhập (null nếu tài khoản không tồn tại)")
        String username,
        @Schema(description = "Kết quả (WILL_CHANGE, NO_CHANGE, SKIPPED)", requiredMode = Schema.RequiredMode.REQUIRED)
        BulkAuthzOutcome outcome,
        @Schema(description = "Danh sách mã vai trò/quyền sẽ thêm hoặc đã thêm", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> added,
        @Schema(description = "Danh sách mã vai trò/quyền sẽ gỡ hoặc đã gỡ", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> removed,
        @Schema(description = "Mã lý do bỏ qua (khi outcome = SKIPPED)")
        String reasonCode
) {
}
