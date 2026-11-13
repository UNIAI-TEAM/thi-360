package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Quyền hiệu lực của tài khoản kèm nguồn gốc")
public record EffectivePermissionItemResponse(
        @Schema(description = "ID quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID permissionId,
        @Schema(description = "Mã quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên quyền", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Mã nhóm đối tượng", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupCode,
        @Schema(description = "Tên nhóm đối tượng", requiredMode = Schema.RequiredMode.REQUIRED)
        String groupName,
        @Schema(description = "Danh sách nguồn cấp quyền (từ vai trò hoặc trực tiếp)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PermissionSourceResponse> sources
) {
}
