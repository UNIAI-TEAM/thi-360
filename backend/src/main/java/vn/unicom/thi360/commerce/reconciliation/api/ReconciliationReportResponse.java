package vn.unicom.thi360.commerce.reconciliation.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.commerce.reconciliation.domain.ReconciliationReport;

/** Nội dung một báo cáo đối soát cuối ngày (TM 8.4, BS 14). */
public record ReconciliationReportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate reportDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant generatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int paidUnfulfilledCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unknownPaymentCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unrefundedExcessCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pendingLateSuccessCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> details,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {

    public static ReconciliationReportResponse from(ReconciliationReport entity) {
        return new ReconciliationReportResponse(
                entity.getId(),
                entity.getReportDate(),
                entity.getGeneratedAt(),
                entity.getPaidUnfulfilledCount(),
                entity.getUnknownPaymentCount(),
                entity.getUnrefundedExcessCount(),
                entity.getPendingLateSuccessCount(),
                entity.getDetails(),
                entity.getCreatedAt());
    }
}
