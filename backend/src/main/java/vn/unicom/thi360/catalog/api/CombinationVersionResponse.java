package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Phiên bản cấu hình của mã tổ hợp môn")
public record CombinationVersionResponse(
        @Schema(description = "ID phiên bản tổ hợp", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "ID giá trị mã tổ hợp", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID itemId,
        @Schema(description = "Mã tổ hợp", requiredMode = Schema.RequiredMode.REQUIRED)
        String itemCode,
        @Schema(description = "Tên tiếng Việt mã tổ hợp", requiredMode = Schema.RequiredMode.REQUIRED)
        String itemNameVi,
        @Schema(description = "Tên tiếng Anh mã tổ hợp")
        String itemNameEn,
        @Schema(description = "Số thứ tự phiên bản", requiredMode = Schema.RequiredMode.REQUIRED)
        int versionNo,
        @Schema(description = "ID giá trị kỳ thi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID examItemId,
        @Schema(description = "Mã kỳ thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String examCode,
        @Schema(description = "Tên tiếng Việt kỳ thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String examNameVi,
        @Schema(description = "Tên tiếng Anh kỳ thi")
        String examNameEn,
        @Schema(description = "Trạng thái hiện tại của kỳ thi", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus examStatus,
        @Schema(description = "Danh sách môn học theo thứ tự", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CombinationSubjectResponse> subjects,
        @Schema(description = "Nguồn quy ước")
        String sourceNote,
        @Schema(description = "Có phải phiên bản hiện hành hay không", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isCurrent,
        @Schema(description = "ID người tạo phiên bản")
        UUID createdBy,
        @Schema(description = "Thời điểm tạo phiên bản", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {
    public CombinationVersionResponse {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
    }
}
