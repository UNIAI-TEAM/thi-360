package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.unicom.thi360.commerce.order.domain.OrderState;

/** Thông tin tóm tắt một đơn mua của người mua theo bài thi (TM 9.3). */
public record ExamBuyerOrderRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountDueVnd,
        Instant paidAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {
}
