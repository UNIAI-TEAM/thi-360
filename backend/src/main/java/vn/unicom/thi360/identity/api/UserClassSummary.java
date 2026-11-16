package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Lớp hiện tại của tài khoản trong danh sách")
public record UserClassSummary(
        @Schema(description = "ID lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Mã lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        String name
) {
}
