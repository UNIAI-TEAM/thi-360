package vn.unicom.thi360.commerce.refund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Yêu cầu hoàn tiền qua API VNPAY hoặc ghi nhận thủ công ngoài hệ thống (TM 9.4, C14). */
@Entity
@Table(name = "refund", schema = "commerce")
public class Refund {

    private static final int MAX_MESSAGE_LEN = 1000;

    @Id
    private UUID id;

    @Column(name = "vnp_request_id", updatable = false)
    private String vnpRequestId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private RefundKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private RefundState state;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "verified_system_error", nullable = false, updatable = false)
    private boolean verifiedSystemError;

    @Column(name = "external_ref", updatable = false)
    private String externalRef;

    @Column(name = "gateway_message")
    private String gatewayMessage;

    @Column(name = "mark_failed_evidence")
    private String markFailedEvidence;

    @Column(name = "mark_failed_reason")
    private String markFailedReason;

    @Column(name = "vnp_response_code")
    private String vnpResponseCode;

    @Column(name = "vnp_transaction_status")
    private String vnpTransactionStatus;

    @Column(name = "vnp_transaction_no")
    private String vnpTransactionNo;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private String requestedBy;

    @Column(name = "requested_by_id", updatable = false)
    private UUID requestedById;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Refund() {
    }

    private Refund(
            UUID id,
            String vnpRequestId,
            UUID paymentId,
            UUID orderId,
            long amountVnd,
            RefundKind kind,
            RefundState state,
            String reason,
            boolean verifiedSystemError,
            String externalRef,
            String requestedBy,
            UUID requestedById,
            Instant requestedAt,
            Instant finishedAt,
            String idempotencyKey) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.vnpRequestId = trimToNull(vnpRequestId);
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        this.amountVnd = amountVnd;
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null").trim();
        this.verifiedSystemError = verifiedSystemError;
        this.externalRef = trimToNull(externalRef);
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        this.requestedById = requestedById;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        this.finishedAt = finishedAt;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.createdAt = requestedAt;
        this.updatedAt = requestedAt;
    }

    public static Refund createApiProcessing(
            UUID id,
            String vnpRequestId,
            UUID paymentId,
            UUID orderId,
            long amountVnd,
            String reason,
            String requestedBy,
            UUID requestedById,
            Instant now,
            String idempotencyKey) {
        return new Refund(
                id,
                vnpRequestId,
                paymentId,
                orderId,
                amountVnd,
                RefundKind.API,
                RefundState.PROCESSING,
                reason,
                true,
                null,
                requestedBy,
                requestedById,
                now,
                null,
                idempotencyKey);
    }

    public static Refund createManualSucceeded(
            UUID id,
            UUID paymentId,
            UUID orderId,
            long amountVnd,
            String reason,
            String externalRef,
            String requestedBy,
            UUID requestedById,
            Instant now,
            String idempotencyKey) {
        return new Refund(
                id,
                null,
                paymentId,
                orderId,
                amountVnd,
                RefundKind.MANUAL,
                RefundState.SUCCEEDED,
                reason,
                true,
                externalRef,
                requestedBy,
                requestedById,
                now,
                now,
                idempotencyKey);
    }

    public void markSucceeded(
            String vnpResponseCode,
            String vnpTransactionStatus,
            String vnpTransactionNo,
            String gatewayMessage,
            Instant now) {
        if (this.state != RefundState.PROCESSING && this.state != RefundState.UNKNOWN) {
            throw new IllegalStateException("Cannot mark refund SUCCEEDED from state " + this.state);
        }
        this.state = RefundState.SUCCEEDED;
        applyGatewayMetadata(vnpResponseCode, vnpTransactionStatus, vnpTransactionNo, gatewayMessage);
        if (this.finishedAt == null) {
            this.finishedAt = Objects.requireNonNull(now, "now must not be null");
        }
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markProcessing(
            String vnpResponseCode,
            String vnpTransactionStatus,
            String vnpTransactionNo,
            String gatewayMessage,
            Instant now) {
        if (this.state != RefundState.PROCESSING) {
            throw new IllegalStateException("Cannot mark refund PROCESSING from state " + this.state);
        }
        this.state = RefundState.PROCESSING;
        applyGatewayMetadata(vnpResponseCode, vnpTransactionStatus, vnpTransactionNo, gatewayMessage);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markUnknown(
            String vnpResponseCode,
            String vnpTransactionStatus,
            String vnpTransactionNo,
            String gatewayMessage,
            Instant now) {
        if (this.state != RefundState.PROCESSING && this.state != RefundState.UNKNOWN) {
            throw new IllegalStateException("Cannot mark refund UNKNOWN from state " + this.state);
        }
        this.state = RefundState.UNKNOWN;
        applyGatewayMetadata(vnpResponseCode, vnpTransactionStatus, vnpTransactionNo, gatewayMessage);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markFailedFromGateway(
            String vnpResponseCode,
            String vnpTransactionStatus,
            String vnpTransactionNo,
            String gatewayMessage,
            Instant now) {
        if (this.state != RefundState.PROCESSING && this.state != RefundState.UNKNOWN) {
            throw new IllegalStateException("Cannot mark refund FAILED from state " + this.state);
        }
        this.state = RefundState.FAILED;
        applyGatewayMetadata(vnpResponseCode, vnpTransactionStatus, vnpTransactionNo, gatewayMessage);
        this.finishedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /** Đường duy nhất cho phép chuyển hoàn tiền đã {@code SUCCEEDED} về {@code FAILED} khi {@code querydr} trả {@code 00/09} (TM 9.4). */
    public void markRejected09(
            String vnpResponseCode,
            String vnpTransactionStatus,
            String vnpTransactionNo,
            String gatewayMessage,
            Instant now) {
        if (this.state != RefundState.PROCESSING
                && this.state != RefundState.UNKNOWN
                && this.state != RefundState.SUCCEEDED) {
            throw new IllegalStateException("Cannot mark refund rejected (09) from state " + this.state);
        }
        this.state = RefundState.FAILED;
        applyGatewayMetadata(vnpResponseCode, vnpTransactionStatus, vnpTransactionNo, gatewayMessage);
        this.finishedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void markFailedManually(String markFailedReason, String markFailedEvidence, Instant now) {
        if (this.state != RefundState.UNKNOWN) {
            throw new IllegalStateException("Cannot mark refund FAILED manually from state " + this.state);
        }
        this.state = RefundState.FAILED;
        this.markFailedReason = trimToNull(markFailedReason);
        this.markFailedEvidence = trimToNull(markFailedEvidence);
        this.finishedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void updateGatewayMetadata(
            String vnpResponseCode,
            String vnpTransactionStatus,
            String vnpTransactionNo,
            String gatewayMessage,
            Instant now) {
        applyGatewayMetadata(vnpResponseCode, vnpTransactionStatus, vnpTransactionNo, gatewayMessage);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void applyGatewayMetadata(
            String vnpResponseCode,
            String vnpTransactionStatus,
            String vnpTransactionNo,
            String gatewayMessage) {
        String code = trimToNull(vnpResponseCode);
        if (code != null) {
            this.vnpResponseCode = code;
        }
        String txnStatus = trimToNull(vnpTransactionStatus);
        if (txnStatus != null) {
            this.vnpTransactionStatus = txnStatus;
        }
        String txnNo = trimToNull(vnpTransactionNo);
        if (txnNo != null) {
            this.vnpTransactionNo = txnNo;
        }
        String msg = trimToNull(gatewayMessage);
        if (msg != null) {
            this.gatewayMessage = truncate(msg, MAX_MESSAGE_LEN);
        }
    }

    public boolean isActive() {
        return state == RefundState.PROCESSING || state == RefundState.UNKNOWN;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    public UUID getId() { return id; }
    public String getVnpRequestId() { return vnpRequestId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getOrderId() { return orderId; }
    public long getAmountVnd() { return amountVnd; }
    public RefundKind getKind() { return kind; }
    public RefundState getState() { return state; }
    public String getReason() { return reason; }
    public boolean isVerifiedSystemError() { return verifiedSystemError; }
    public String getExternalRef() { return externalRef; }
    public String getGatewayMessage() { return gatewayMessage; }
    public String getMarkFailedEvidence() { return markFailedEvidence; }
    public String getMarkFailedReason() { return markFailedReason; }
    public String getVnpResponseCode() { return vnpResponseCode; }
    public String getVnpTransactionStatus() { return vnpTransactionStatus; }
    public String getVnpTransactionNo() { return vnpTransactionNo; }
    public String getRequestedBy() { return requestedBy; }
    public UUID getRequestedById() { return requestedById; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
