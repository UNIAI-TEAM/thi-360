package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Yêu cầu nhận hoặc tiếp quản phiên sửa bài của lượt thi (LT §7.1, F10)")
public record ClaimSessionRequest(
        @Schema(description = "Id phiên sửa đã lưu trên thiết bị (nếu có)", nullable = true)
        UUID sessionId,
        @Schema(description = "Đặt true để xác nhận tiếp quản phiên từ thiết bị khác", nullable = true)
        Boolean takeover) {}
