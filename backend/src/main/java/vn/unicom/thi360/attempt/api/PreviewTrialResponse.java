package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Phản hồi khi gia hạn TTL xem trước để admin làm thử đề như học viên (AC-23, BS §9). */
@Schema(name = "PreviewTrialResponse")
public record PreviewTrialResponse(
        @Schema(description = "Thời điểm hết hạn mới của phiên xem trước sau khi mở làm thử")
        Instant expiresAt) {}
