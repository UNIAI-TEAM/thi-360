package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Schema(description = "Giá trị liên kết kế thừa từ nút tổ tiên")
public record CatalogInheritedRefResponse(
        @Schema(description = "ID giá trị được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID refItemId,
        @Schema(description = "ID nhóm của giá trị được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID refGroupId,
        @Schema(description = "Mã nhóm của giá trị được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String refGroupCode,
        @Schema(description = "Tên tiếng Việt nhóm liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String refGroupNameVi,
        @Schema(description = "Tên tiếng Anh nhóm liên kết")
        String refGroupNameEn,
        @Schema(description = "Mã giá trị được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên tiếng Việt giá trị được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String nameVi,
        @Schema(description = "Tên tiếng Anh giá trị được liên kết")
        String nameEn,
        @Schema(description = "Trạng thái giá trị được liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogStatus status,
        @Schema(description = "ID nút tổ tiên đang giữ liên kết này", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID fromItemId,
        @Schema(description = "Mã nút tổ tiên đang giữ liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String fromItemCode,
        @Schema(description = "Tên tiếng Việt nút tổ tiên đang giữ liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        String fromItemNameVi,
        @Schema(description = "Tên tiếng Anh nút tổ tiên đang giữ liên kết")
        String fromItemNameEn
) {
}
