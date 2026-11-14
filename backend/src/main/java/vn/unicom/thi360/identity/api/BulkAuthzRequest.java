package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu xem trước hoặc áp dụng gán/gỡ quyền hàng loạt")
public record BulkAuthzRequest(
        @Schema(description = "Danh sách ID tài khoản (tối đa 500)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "{validation.userIds.notEmpty}")
        @Size(max = 500, message = "{validation.bulk.userIds.size}")
        List<UUID> userIds,
        @Schema(description = "Hành động (GRANT hoặc REVOKE)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.bulkAuthz.action.notNull}")
        BulkAuthzAction action,
        @Schema(description = "Đối tượng áp dụng (ROLE hoặc PERMISSION)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.bulkAuthz.target.notNull}")
        BulkAuthzTarget target,
        @Schema(description = "Danh sách ID vai trò (khi target = ROLE)")
        List<UUID> roleIds,
        @Schema(description = "Danh sách ID quyền (khi target = PERMISSION)")
        List<UUID> permissionIds,
        @Schema(description = "Lý do (bắt buộc khi action = GRANT và target = PERMISSION)")
        @Size(max = 500, message = "{validation.reason.size}")
        String reason
) {
}
