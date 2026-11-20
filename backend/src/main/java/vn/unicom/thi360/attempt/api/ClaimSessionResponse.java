package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Phản hồi nhận phiên sửa bài thành công (LT §7.1)")
public record ClaimSessionResponse(
        @Schema(description = "Id phiên sửa đang giữ quyền ghi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sessionId,
        @Schema(description = "Giờ máy chủ hiện tại", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant serverNow) {}
