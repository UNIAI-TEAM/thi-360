package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Chi tiết nơi đang sử dụng giá trị danh mục theo nguồn")
public record CatalogUsageResponse(
        @Schema(description = "Tên nguồn tham chiếu", requiredMode = Schema.RequiredMode.REQUIRED)
        String source,
        @Schema(description = "Loại đối tượng tham chiếu", requiredMode = Schema.RequiredMode.REQUIRED)
        String objectType,
        @Schema(description = "Số lượng bản ghi đang tham chiếu", requiredMode = Schema.RequiredMode.REQUIRED)
        long count,
        @Schema(description = "Có chặn thao tác ngừng sử dụng hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean blocking,
        @Schema(description = "Một số nhãn ví dụ của đối tượng đang tham chiếu", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> sampleLabels
) {
    public CatalogUsageResponse {
        sampleLabels = sampleLabels == null ? List.of() : List.copyOf(sampleLabels);
    }
}
