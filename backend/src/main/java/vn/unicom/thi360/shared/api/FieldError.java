package vn.unicom.thi360.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chi tiết lỗi của một trường dữ liệu")
public record FieldError(
        @Schema(
                description = "Tên trường dữ liệu",
                example = "username",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String field,
        @Schema(
                description = "Mã lỗi của trường",
                example = "TAKEN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(
                description = "Thông báo lỗi tiếng Việt",
                example = "Tên đăng nhập đã tồn tại",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
