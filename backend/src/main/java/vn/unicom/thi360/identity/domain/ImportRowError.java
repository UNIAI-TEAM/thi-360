package vn.unicom.thi360.identity.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lỗi kiểm tra dữ liệu theo từng dòng và cột")
public record ImportRowError(
        @Schema(description = "Số dòng trong tệp Excel (1-based)", requiredMode = Schema.RequiredMode.REQUIRED)
        int rowNo,
        @Schema(description = "Tên cột bị lỗi", requiredMode = Schema.RequiredMode.REQUIRED)
        String column,
        @Schema(description = "Khóa cột ổn định, không phụ thuộc ngôn ngữ; null với lỗi cấp dòng và với dòng lưu trước khi có trường này",
                allowableValues = {"USERNAME", "PASSWORD", "FULL_NAME", "EMAIL", "PHONE", "CLASS_CODE"},
                types = {"string", "null"})
        String columnKey,
        @Schema(description = "Mã lỗi chuẩn", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Thông báo lỗi chi tiết", requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
