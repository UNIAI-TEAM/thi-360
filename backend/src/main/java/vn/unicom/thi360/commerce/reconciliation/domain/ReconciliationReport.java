package vn.unicom.thi360.commerce.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Báo cáo đối soát cuối ngày (TM 8.4, BS 14). Mỗi ngày tối đa một bản ghi. */
@Entity
@Table(name = "reconciliation_report", schema = "commerce")
public class ReconciliationReport {

    @Id
    private UUID id;

    @Column(name = "report_date", nullable = false, updatable = false)
    private LocalDate reportDate;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "paid_unfulfilled_count", nullable = false, updatable = false)
    private int paidUnfulfilledCount;

    @Column(name = "unknown_payment_count", nullable = false, updatable = false)
    private int unknownPaymentCount;

    @Column(name = "unrefunded_excess_count", nullable = false, updatable = false)
    private int unrefundedExcessCount;

    @Column(name = "pending_late_success_count", nullable = false, updatable = false)
    private int pendingLateSuccessCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReconciliationReport() {
    }

    public ReconciliationReport(
            UUID id,
            LocalDate reportDate,
            Instant generatedAt,
            int paidUnfulfilledCount,
            int unknownPaymentCount,
            int unrefundedExcessCount,
            int pendingLateSuccessCount,
            Map<String, Object> details) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.reportDate = Objects.requireNonNull(reportDate, "reportDate must not be null");
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        this.paidUnfulfilledCount = paidUnfulfilledCount;
        this.unknownPaymentCount = unknownPaymentCount;
        this.unrefundedExcessCount = unrefundedExcessCount;
        this.pendingLateSuccessCount = pendingLateSuccessCount;
        this.details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.createdAt = generatedAt;
    }

    public UUID getId() { return id; }
    public LocalDate getReportDate() { return reportDate; }
    public Instant getGeneratedAt() { return generatedAt; }
    public int getPaidUnfulfilledCount() { return paidUnfulfilledCount; }
    public int getUnknownPaymentCount() { return unknownPaymentCount; }
    public int getUnrefundedExcessCount() { return unrefundedExcessCount; }
    public int getPendingLateSuccessCount() { return pendingLateSuccessCount; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
