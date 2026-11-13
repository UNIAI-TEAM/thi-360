package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu xem trước hoặc thực thi thao tác hàng loạt trên tài khoản")
public record BulkUserActionRequest(
        @Schema(description = "Hành động thực hiện", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.bulkUser.action.notNull}")
        UserBulkAction action,
        @Schema(description = "Danh sách ID tài khoản (tối đa 500)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "{validation.userIds.notEmpty}")
        List<UUID> userIds,
        @Schema(description = "Lý do thực hiện (bắt buộc khi áp dụng DEACTIVATE hoặc DELETE)")
        @Size(max = 500, message = "{validation.reason.size}")
        String reason
) {
}
