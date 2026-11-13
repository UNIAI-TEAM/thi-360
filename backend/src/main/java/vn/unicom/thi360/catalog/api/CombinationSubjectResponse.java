package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Môn học trong một phiên bản mã tổ hợp")
public record CombinationSubjectResponse(
        @Schema(description = "ID giá trị môn học", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID subjectItemId,
        @Schema(description = "Mã môn học", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên tiếng Việt của môn học", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh của môn học")
        String nameEn,
        @Schema(description = "Trạng thái hiện tại của môn học", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus status,
        @Schema(description = "Thứ tự môn trong tổ hợp", requiredMode = Schema.RequiredMode.REQUIRED)
        int position
) {
}
