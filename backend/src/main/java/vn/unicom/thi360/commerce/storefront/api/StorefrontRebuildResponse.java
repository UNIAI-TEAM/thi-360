package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả dựng lại bảng chiếu: số bài đã ghi, giữ nguyên, không bán được và lỗi")
public record StorefrontRebuildResponse(
        @Schema(description = "Số bài đã công bố hoặc ngừng bán được xét", requiredMode = Schema.RequiredMode.REQUIRED)
        int examined,
        @Schema(description = "Số dòng đã ghi mới hoặc cập nhật", requiredMode = Schema.RequiredMode.REQUIRED)
        int written,
        @Schema(description = "Số dòng đã đúng sẵn, không đổi", requiredMode = Schema.RequiredMode.REQUIRED)
        int unchanged,
        @Schema(description = "Số bài thiếu giá bán hoặc số lượt, không tạo dòng", requiredMode = Schema.RequiredMode.REQUIRED)
        int notSellable,
        @Schema(description = "Số bài dựng lỗi; các bài khác vẫn được dựng", requiredMode = Schema.RequiredMode.REQUIRED)
        int failed) {
}
