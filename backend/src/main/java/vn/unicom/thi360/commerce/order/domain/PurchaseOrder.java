package vn.unicom.thi360.commerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.DiscountApplied;

/**
 * Đơn hàng chụp đầy đủ thông tin sản phẩm, giá và quyền lợi tại thời điểm tạo (TM 8.1, 16.3).
 */
@Entity
@Table(name = "purchase_order", schema = "commerce")
public class PurchaseOrder {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "buyer_username", nullable = false, updatable = false)
    private String buyerUsername;

    @Column(name = "buyer_full_name", nullable = false, updatable = false)
    private String buyerFullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, updatable = false)
    private ProductType productType;

    @Column(name = "exam_id", updatable = false)
    private UUID examId;

    @Column(name = "exam_version_no", updatable = false)
    private Integer examVersionNo;

    @Column(name = "bundle_id", updatable = false)
    private UUID bundleId;

    @Column(name = "bundle_version_no", updatable = false)
    private Integer bundleVersionNo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "option_codes", nullable = false, updatable = false, columnDefinition = "text[]")
    private String[] optionCodes;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "list_price_vnd", nullable = false, updatable = false)
    private long listPriceVnd;

    @Column(name = "promotion_id", updatable = false)
    private UUID promotionId;

    @Column(name = "promotion_discount_vnd", nullable = false, updatable = false)
    private long promotionDiscountVnd;

    @Column(name = "voucher_id", updatable = false)
    private UUID voucherId;

    @Column(name = "voucher_code", updatable = false)
    private String voucherCode;

    @Column(name = "voucher_discount_vnd", nullable = false, updatable = false)
    private long voucherDiscountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_applied", nullable = false, updatable = false)
    private DiscountApplied discountApplied;

    @Column(name = "amount_due_vnd", nullable = false, updatable = false)
    private long amountDueVnd;

    @Column(name = "attempts_granted", nullable = false, updatable = false)
    private int attemptsGranted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> benefits;

    @Column(name = "result_view", nullable = false, updatable = false)
    private String resultView;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private OrderState state;

    @Column(name = "replaced_by_order_id")
    private UUID replacedByOrderId;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "review_reason")
    private String reviewReason;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "fulfillment_error")
    private String fulfillmentError;

    @Column(name = "fulfillment_tries", nullable = false)
    private int fulfillmentTries;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PurchaseOrder() {
    }

    private PurchaseOrder(
            UUID id,
            String code,
            UUID userId,
            String buyerUsername,
            String buyerFullName,
            ProductType productType,
            UUID examId,
            Integer examVersionNo,
            UUID bundleId,
            Integer bundleVersionNo,
            List<String> optionCodes,
            String productCode,
            String productName,
            long listPriceVnd,
            UUID promotionId,
            long promotionDiscountVnd,
            UUID voucherId,
            String voucherCode,
            long voucherDiscountVnd,
            DiscountApplied discountApplied,
            long amountDueVnd,
            int attemptsGranted,
            Map<String, Object> benefits,
            String resultView,
            PaymentMethod paymentMethod,
            OrderState state,
            Instant expiresAt,
            Instant paidAt,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.buyerUsername = Objects.requireNonNull(buyerUsername, "buyerUsername must not be null");
        this.buyerFullName = Objects.requireNonNull(buyerFullName, "buyerFullName must not be null");
        this.productType = Objects.requireNonNull(productType, "productType must not be null");
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.bundleId = bundleId;
        this.bundleVersionNo = bundleVersionNo;
        this.optionCodes = optionCodes == null ? new String[0] : optionCodes.toArray(String[]::new);
        this.productCode = Objects.requireNonNull(productCode, "productCode must not be null");
        this.productName = Objects.requireNonNull(productName, "productName must not be null");
        this.listPriceVnd = listPriceVnd;
        this.promotionId = promotionId;
        this.promotionDiscountVnd = promotionDiscountVnd;
        this.voucherId = voucherId;
        this.voucherCode = voucherCode;
        this.voucherDiscountVnd = voucherDiscountVnd;
        this.discountApplied = Objects.requireNonNull(discountApplied, "discountApplied must not be null");
        this.amountDueVnd = amountDueVnd;
        this.attemptsGranted = attemptsGranted;
        this.benefits = benefits == null ? Map.of() : Map.copyOf(benefits);
        this.resultView = Objects.requireNonNull(resultView, "resultView must not be null");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.expiresAt = expiresAt;
        this.paidAt = paidAt;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static PurchaseOrder createPendingExamOrder(
            UUID id,
            String code,
            UUID userId,
            String buyerUsername,
            String buyerFullName,
            UUID examId,
            int examVersionNo,
            List<String> optionCodes,
            String productCode,
            String productName,
            long listPriceVnd,
            UUID promotionId,
            long promotionDiscountVnd,
            UUID voucherId,
            String voucherCode,
            long voucherDiscountVnd,
            DiscountApplied discountApplied,
            long amountDueVnd,
            int attemptsGranted,
            Map<String, Object> benefits,
            String resultView,
            Instant expiresAt,
            Instant now) {
        return new PurchaseOrder(
                id, code, userId, buyerUsername, buyerFullName, ProductType.EXAM,
                examId, examVersionNo, null, null, optionCodes, productCode, productName,
                listPriceVnd, promotionId, promotionDiscountVnd, voucherId, voucherCode, voucherDiscountVnd,
                discountApplied, amountDueVnd, attemptsGranted, benefits, resultView,
                PaymentMethod.VNPAY, OrderState.PENDING_PAYMENT, expiresAt, null, now);
    }

    public static PurchaseOrder createFreeExamOrder(
            UUID id,
            String code,
            UUID userId,
            String buyerUsername,
            String buyerFullName,
            UUID examId,
            int examVersionNo,
            List<String> optionCodes,
            String productCode,
            String productName,
            long listPriceVnd,
            UUID promotionId,
            long promotionDiscountVnd,
            UUID voucherId,
            String voucherCode,
            long voucherDiscountVnd,
            DiscountApplied discountApplied,
            int attemptsGranted,
            Map<String, Object> benefits,
            String resultView,
            Instant now) {
        return new PurchaseOrder(
                id, code, userId, buyerUsername, buyerFullName, ProductType.EXAM,
                examId, examVersionNo, null, null, optionCodes, productCode, productName,
                listPriceVnd, promotionId, promotionDiscountVnd, voucherId, voucherCode, voucherDiscountVnd,
                discountApplied, 0L, attemptsGranted, benefits, resultView,
                PaymentMethod.FREE, OrderState.PAID, null, now, now);
    }

    public void markPaid(Instant now) {
        this.state = OrderState.PAID;
        if (this.paidAt == null) {
            this.paidAt = Objects.requireNonNull(now, "now must not be null");
        }
        this.updatedAt = now;
    }

    public void markFulfilled(Instant now) {
        this.state = OrderState.FULFILLED;
        this.fulfilledAt = Objects.requireNonNull(now, "now must not be null");
        this.fulfillmentError = null;
        this.updatedAt = now;
    }

    public void recordFulfillmentFailure(String errorMessage, Instant now) {
        this.fulfillmentTries++;
        this.fulfillmentError = errorMessage == null ? "Fulfillment failed" : truncate(errorMessage, 1000);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markReplaced(UUID replacementOrderId, Instant now) {
        this.state = OrderState.REPLACED;
        this.replacedByOrderId = Objects.requireNonNull(replacementOrderId, "replacementOrderId must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** Chuyển trạng thái sang REPLACED trước khi chèn đơn mới để giải phóng chỉ mục duy nhất đơn chờ (E13). */
    public void beginReplace(Instant now) {
        this.state = OrderState.REPLACED;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** Gắn liên kết sang đơn thay thế sau khi đơn mới đã được lưu vào bảng (đáp ứng khóa ngoại). */
    public void linkReplacement(UUID replacementOrderId, Instant now) {
        this.replacedByOrderId = Objects.requireNonNull(replacementOrderId, "replacementOrderId must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markCancelled(Instant now) {
        this.state = OrderState.CANCELLED;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markRefunded(Instant now) {
        this.state = OrderState.REFUNDED;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void flagNeedsReview(String reason, Instant now) {
        this.needsReview = true;
        this.reviewReason = reason;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public UUID getUserId() { return userId; }
    public String getBuyerUsername() { return buyerUsername; }
    public String getBuyerFullName() { return buyerFullName; }
    public ProductType getProductType() { return productType; }
    public UUID getExamId() { return examId; }
    public Integer getExamVersionNo() { return examVersionNo; }
    public UUID getBundleId() { return bundleId; }
    public Integer getBundleVersionNo() { return bundleVersionNo; }
    public List<String> getOptionCodes() { return List.of(optionCodes); }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public long getListPriceVnd() { return listPriceVnd; }
    public UUID getPromotionId() { return promotionId; }
    public long getPromotionDiscountVnd() { return promotionDiscountVnd; }
    public UUID getVoucherId() { return voucherId; }
    public String getVoucherCode() { return voucherCode; }
    public long getVoucherDiscountVnd() { return voucherDiscountVnd; }
    public DiscountApplied getDiscountApplied() { return discountApplied; }
    public long getAmountDueVnd() { return amountDueVnd; }
    public int getAttemptsGranted() { return attemptsGranted; }
    public Map<String, Object> getBenefits() { return benefits; }
    public String getResultView() { return resultView; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public OrderState getState() { return state; }
    public UUID getReplacedByOrderId() { return replacedByOrderId; }
    public boolean isNeedsReview() { return needsReview; }
    public String getReviewReason() { return reviewReason; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getFulfilledAt() { return fulfilledAt; }
    public String getFulfillmentError() { return fulfillmentError; }
    public int getFulfillmentTries() { return fulfillmentTries; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
