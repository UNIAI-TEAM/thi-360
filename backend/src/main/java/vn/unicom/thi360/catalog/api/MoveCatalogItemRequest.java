package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Yêu cầu chuyển nút cây sang nút cha khác cùng cấp")
public record MoveCatalogItemRequest(
        @Schema(description = "ID nút cha mới cùng cấp với nút cha hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID parentId,
        @Schema(description = "Phiên bản khóa lạc quan (tùy chọn)")
        Long version
) {
}
