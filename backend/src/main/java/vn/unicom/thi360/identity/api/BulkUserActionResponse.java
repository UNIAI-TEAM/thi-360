package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Kết quả thực thi thao tác hàng loạt trên tài khoản")
public record BulkUserActionResponse(
        @Schema(description = "ID đợt thao tác", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID batchId,
        @Schema(description = "Hành động đã thực hiện", requiredMode = Schema.RequiredMode.REQUIRED)
        UserBulkAction action,
        @Schema(description = "Tổng số tài khoản gửi lên", requiredMode = Schema.RequiredMode.REQUIRED)
        int total,
        @Schema(description = "Số tài khoản thực hiện thành công", requiredMode = Schema.RequiredMode.REQUIRED)
        int succeeded,
        @Schema(description = "Số tài khoản bị bỏ qua", requiredMode = Schema.RequiredMode.REQUIRED)
        int skipped,
        @Schema(description = "Số tài khoản thất bại", requiredMode = Schema.RequiredMode.REQUIRED)
        int failed,
        @Schema(description = "Kết quả chi tiết từng tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        List<BulkUserActionItemResult> results
) {
}
