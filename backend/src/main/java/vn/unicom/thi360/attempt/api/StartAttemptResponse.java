package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.AttemptState;

@Schema(description = "Kết quả khởi tạo hoặc nối lại lượt thi (LT §5.2)")
public record StartAttemptResponse(
        @Schema(description = "Id lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID attemptId,
        @Schema(description = "Trạng thái hiện tại của lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        AttemptState state,
        @Schema(description = "True nếu nối lại lượt đang mở có sẵn; false nếu mới khởi tạo",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean resumed,
        @Schema(description = "Lý do hủy khi state là ABORTED", nullable = true)
        AbortReason abortReason,
        @Schema(description = "Có thể thử bắt đầu lại ngay (true khi ABORTED)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean retryable) {}
