package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Bộ đề đã mua chứa bài thi này (TM 16.4)")
public record MyExamBundleRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID bundleId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
}
