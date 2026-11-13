package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Tổng hợp nơi đang sử dụng của một giá trị hoặc nhóm danh mục")
public record CatalogUsagesResponse(
        @Schema(description = "ID giá trị hoặc nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID targetId,
        @Schema(description = "Mã giá trị hoặc nhóm danh mục", requiredMode = Schema.RequiredMode.REQUIRED)
        String targetCode,
        @Schema(description = "Tổng số tham chiếu", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalCount,
        @Schema(description = "Có nguồn nào chặn ngừng sử dụng hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean blocking,
        @Schema(description = "Danh sách nơi đang sử dụng theo từng nguồn", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CatalogUsageResponse> usages
) {
    public CatalogUsagesResponse {
        usages = usages == null ? List.of() : List.copyOf(usages);
    }
}
