package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.unicom.thi360.attempt.domain.AttemptState;

@Schema(description = "Phản hồi gia hạn phiên sửa bài (LT §7.1)")
public record HeartbeatResponse(
        @Schema(description = "Giờ máy chủ hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant serverNow,
        @Schema(description = "Trạng thái hiện tại của lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        AttemptState state) {}
