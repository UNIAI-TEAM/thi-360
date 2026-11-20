package vn.unicom.thi360.commerce.payment.domain;

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

/** Một lần thanh toán VNPAY gắn với một đơn hàng (TM 8.1, BS 14). */
@Entity
@Table(name = "payment", schema = "commerce")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "attempt_seq", nullable = false, updatable = false)
    private int attemptSeq;

    @Column(name = "txn_ref", nullable = false, updatable = false)
    private String txnRef;

    @Column(name = "vnp_create_date", nullable = false, updatable = false)
    private String vnpCreateDate;

    @Column(name = "vnp_expire_date", nullable = false, updatable = false)
    private String vnpExpireDate;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private PaymentState state;

    @Column(name = "is_duplicate", nullable = false)
    private boolean duplicate;

    @Enumerated(EnumType.STRING)
    @Column(name = "excess_reason")
    private ExcessReason excessReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_success_review")
    private LateSuccessReview lateSuccessReview;

    @Column(name = "late_success_at")
    private Instant lateSuccessAt;

    @Column(name = "vnp_transaction_no")
    private String vnpTransactionNo;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "pay_date")
    private String payDate;

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Payment() {
    }

    public Payment(
            UUID id,
            UUID orderId,
            int attemptSeq,
            String txnRef,
            String vnpCreateDate,
            String vnpExpireDate,
            long amountVnd,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.attemptSeq = attemptSeq;
        this.txnRef = Objects.requireNonNull(txnRef, "txnRef must not be null");
        this.vnpCreateDate = Objects.requireNonNull(vnpCreateDate, "vnpCreateDate must not be null");
        this.vnpExpireDate = Objects.requireNonNull(vnpExpireDate, "vnpExpireDate must not be null");
        this.amountVnd = amountVnd;
        this.state = PaymentState.CREATED;
        this.duplicate = false;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void markCountedSuccess(
            String vnpTransactionNo, String bankCode, String cardType, String payDate,
            String responseCode, String transactionStatus, Instant now) {
        this.state = PaymentState.SUCCEEDED;
        this.duplicate = false;
        this.excessReason = null;
        applyVnpayMetadata(vnpTransactionNo, bankCode, cardType, payDate, responseCode, transactionStatus);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markExcessSuccess(
            ExcessReason excessReason, String vnpTransactionNo, String bankCode, String cardType,
            String payDate, String responseCode, String transactionStatus, Instant now) {
        this.state = PaymentState.SUCCEEDED;
        this.duplicate = true;
        this.excessReason = Objects.requireNonNull(excessReason, "excessReason must not be null");
        applyVnpayMetadata(vnpTransactionNo, bankCode, cardType, payDate, responseCode, transactionStatus);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markAutoExcessFromPendingReview(Instant now) {
        markAutoExcessFromPendingReview(null, null, null, null, null, null, now);
    }

    public void markAutoExcessFromPendingReview(
            String vnpTransactionNo, String bankCode, String cardType, String payDate,
            String responseCode, String transactionStatus, Instant now) {
        this.state = PaymentState.SUCCEEDED;
        this.duplicate = true;
        this.excessReason = ExcessReason.AFTER_COUNTED;
        this.lateSuccessReview = LateSuccessReview.AUTO_EXCESS;
        applyVnpayMetadata(vnpTransactionNo, bankCode, cardType, payDate, responseCode, transactionStatus);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markLateSuccessPendingReview(
            String vnpTransactionNo, String bankCode, String cardType, String payDate,
            String responseCode, String transactionStatus, Instant now) {
        this.lateSuccessReview = LateSuccessReview.PENDING;
        this.lateSuccessAt = Objects.requireNonNull(now, "now must not be null");
        applyVnpayMetadata(vnpTransactionNo, bankCode, cardType, payDate, responseCode, transactionStatus);
        this.updatedAt = now;
    }

    public void markFailed(
            String vnpTransactionNo, String bankCode, String cardType, String payDate,
            String responseCode, String transactionStatus, Instant now) {
        this.state = PaymentState.FAILED;
        applyVnpayMetadata(vnpTransactionNo, bankCode, cardType, payDate, responseCode, transactionStatus);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markUnknown(
            String vnpTransactionNo, String bankCode, String cardType, String payDate,
            String responseCode, String transactionStatus, Instant now) {
        this.state = PaymentState.UNKNOWN;
        applyVnpayMetadata(vnpTransactionNo, bankCode, cardType, payDate, responseCode, transactionStatus);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void applyVnpayMetadata(
            String vnpTransactionNo, String bankCode, String cardType, String payDate,
            String responseCode, String transactionStatus) {
        if (vnpTransactionNo != null) {
            this.vnpTransactionNo = vnpTransactionNo;
        }
        if (bankCode != null && !bankCode.isBlank()) {
            this.bankCode = bankCode;
        }
        if (cardType != null && !cardType.isBlank()) {
            this.cardType = cardType;
        }
        if (payDate != null && !payDate.isBlank()) {
            this.payDate = payDate;
        }
        if (responseCode != null && !responseCode.isBlank()) {
            this.responseCode = responseCode;
        }
        if (transactionStatus != null && !transactionStatus.isBlank()) {
            this.transactionStatus = transactionStatus;
        }
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public int getAttemptSeq() { return attemptSeq; }
    public String getTxnRef() { return txnRef; }
    public String getVnpCreateDate() { return vnpCreateDate; }
    public String getVnpExpireDate() { return vnpExpireDate; }
    public long getAmountVnd() { return amountVnd; }
    public PaymentState getState() { return state; }
    public boolean isDuplicate() { return duplicate; }
    public ExcessReason getExcessReason() { return excessReason; }
    public LateSuccessReview getLateSuccessReview() { return lateSuccessReview; }
    public Instant getLateSuccessAt() { return lateSuccessAt; }
    public String getVnpTransactionNo() { return vnpTransactionNo; }
    public String getBankCode() { return bankCode; }
    public String getCardType() { return cardType; }
    public String getPayDate() { return payDate; }
    public String getResponseCode() { return responseCode; }
    public String getTransactionStatus() { return transactionStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
