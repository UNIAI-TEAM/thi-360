package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Kết quả xem trước hoặc áp dụng phân quyền hàng loạt")
public record BulkAuthzResponse(
        @Schema(description = "Mã lô áp dụng (null ở bước xem trước)")
        UUID batchId,
        @Schema(description = "Số tài khoản sẽ thay đổi / đã thay đổi", requiredMode = Schema.RequiredMode.REQUIRED)
        int willChange,
        @Schema(description = "Số tài khoản không đổi", requiredMode = Schema.RequiredMode.REQUIRED)
        int noChange,
        @Schema(description = "Số tài khoản bị bỏ qua", requiredMode = Schema.RequiredMode.REQUIRED)
        int skipped,
        @Schema(description = "Chi tiết từng tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        List<BulkAuthzItemResponse> items
) {
}
