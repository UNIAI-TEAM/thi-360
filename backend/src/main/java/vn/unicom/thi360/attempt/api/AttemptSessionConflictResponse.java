package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import vn.unicom.thi360.shared.api.FieldError;

@Schema(description = "Lỗi xung đột phiên sửa bài khi lượt đang mở ở phiên khác (LT §7.1, F10)")
public record AttemptSessionConflictResponse(
        @Schema(example = "ATTEMPT_OPEN_ELSEWHERE", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldError> fieldErrors,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String traceId,
        @Schema(description = "Thời điểm hoạt động gần nhất của phiên đang giữ quyền sửa", nullable = true)
        Instant lastSeenAt) {}
