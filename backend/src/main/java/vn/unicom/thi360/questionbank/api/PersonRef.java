package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Tham chiếu một người dùng (người tạo, người lưu)")
public record PersonRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
