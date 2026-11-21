package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Yêu cầu khóa lạc quan cho thao tác công bố hoặc ngừng bán bộ đề")
public record BundleRevisionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.bundle.expectedRevision.notNull}")
        Long expectedRevision) {
}
