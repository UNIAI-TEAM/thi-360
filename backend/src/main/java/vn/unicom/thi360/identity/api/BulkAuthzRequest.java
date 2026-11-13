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
        @NotEmpty(message = "Danh sách tài khoản không được để trống")
        @Size(max = 500, message = "Số lượng tài khoản tối đa là 500")
        List<UUID> userIds,
        @Schema(description = "Hành động (GRANT hoặc REVOKE)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Hành động không được để trống")
        BulkAuthzAction action,
        @Schema(description = "Đối tượng áp dụng (ROLE hoặc PERMISSION)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Đối tượng áp dụng không được để trống")
        BulkAuthzTarget target,
        @Schema(description = "Danh sách ID vai trò (khi target = ROLE)")
        List<UUID> roleIds,
        @Schema(description = "Danh sách ID quyền (khi target = PERMISSION)")
        List<UUID> permissionIds,
        @Schema(description = "Lý do (bắt buộc khi action = GRANT và target = PERMISSION)")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
