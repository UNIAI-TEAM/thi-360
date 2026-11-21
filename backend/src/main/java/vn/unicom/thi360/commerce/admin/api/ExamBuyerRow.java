package vn.unicom.thi360.commerce.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Một tài khoản người mua của bài thi kèm các đơn hợp lệ và bộ đếm lượt thi (TM 9.3). */
public record ExamBuyerRow(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String username,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int purchaseCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ExamBuyerOrderRef> orders,
        Instant lastPaidAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsUsed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsReserved,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsRevoked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int attemptsRemaining
) {
    public ExamBuyerRow {
        orders = orders == null ? List.of() : List.copyOf(orders);
    }
}
