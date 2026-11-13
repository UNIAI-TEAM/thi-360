package vn.unicom.thi360.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Định dạng lỗi chuẩn của API")
public record ApiError(
        @Schema(
                description = "Mã lỗi nghiệp vụ hoặc hệ thống",
                example = "VALIDATION_FAILED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(
                description = "Thông báo lỗi tiếng Việt",
                example = "Dữ liệu không hợp lệ",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(
                description = "Danh sách lỗi theo từng trường",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldError> fieldErrors,
        @Schema(
                description = "Mã định danh yêu cầu (X-Request-Id)",
                example = "019320a0-0000-7000-8000-000000000001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String traceId
) {
    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, List.of(), traceId);
    }

    public static ApiError of(String code, String message, List<FieldError> fieldErrors, String traceId) {
        return new ApiError(code, message, fieldErrors, traceId);
    }
}
