package vn.unicom.thi360.commerce.reconciliation.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Phản hồi endpoint lấy báo cáo đối soát gần nhất ở đầu màn {@code /orders}. */
public record LatestReconciliationReportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean available,
        ReconciliationReportResponse report
) {
}
