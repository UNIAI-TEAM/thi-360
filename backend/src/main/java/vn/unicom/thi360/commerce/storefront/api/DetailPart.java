package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một phần của bài thi, theo thứ tự làm bài")
public record DetailPart(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int order,
        @Schema(description = "Tên phần, nội dung bài thi nên không dịch", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(nullable = true) IntRange questionCount,
        @Schema(nullable = true) IntRange durationMinutes,
        @Schema(nullable = true) DecimalRange maxPoints,
        @Schema(description = "true: phần bắt buộc; false: học viên tự chọn", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean required) {
}
