package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.unicom.thi360.shared.api.FieldError;

/**
 * Lỗi của API mã OTP email: định dạng lỗi chung kèm hai trường FS-2 cần. {@code retryAfter} chỉ có ở 429,
 * {@code remainingAttempts} chỉ có ở 422 {@code EMAIL_CODE_INVALID}.
 */
@Schema(description = "Lỗi của API mã OTP email: lỗi chung kèm retryAfter và remainingAttempts")
public record EmailCodeErrorResponse(
        @Schema(description = "Mã lỗi", example = "EMAIL_CODE_INVALID", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Thông báo lỗi theo ngôn ngữ yêu cầu", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(description = "Danh sách lỗi theo từng trường", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldError> fieldErrors,
        @Schema(description = "Mã định danh yêu cầu", requiredMode = Schema.RequiredMode.REQUIRED)
        String traceId,
        @Schema(description = "429: số giây phải chờ trước khi gửi lại", nullable = true)
        Long retryAfter,
        @Schema(description = "422 EMAIL_CODE_INVALID: số lần nhập còn lại", nullable = true)
        Integer remainingAttempts
) {
}
