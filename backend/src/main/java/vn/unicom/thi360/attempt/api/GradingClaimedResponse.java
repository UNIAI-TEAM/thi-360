package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.shared.api.FieldError;

@Schema(name = "GradingClaimedResponse", description = "Lỗi xung đột khi câu tự luận đang được người chấm khác giữ trong vòng 30 phút")
public record GradingClaimedResponse(
        @Schema(example = "GRADING_CLAIMED", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FieldError> fieldErrors,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String traceId,
        UUID claimedById,
        String claimedByName,
        Instant claimedAt,
        Instant lastActivityAt) {}
