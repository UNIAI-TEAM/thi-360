package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu sắp xếp lại thứ tự giá trị trong nhóm hoặc cùng nút cha")
public record ReorderCatalogItemsRequest(
        @Schema(description = "Danh sách ID giá trị theo thứ tự hiển thị mới", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "{validation.catalog.reorder.itemIds.notEmpty}")
        List<UUID> itemIds
) {
}
