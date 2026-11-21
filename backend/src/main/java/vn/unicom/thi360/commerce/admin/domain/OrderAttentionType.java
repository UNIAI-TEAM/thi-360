package vn.unicom.thi360.commerce.admin.domain;

/** Sáu loại cảnh báo thuộc dải "Cần xử lý" của đơn hàng (TM 9.1, BS §14). */
public enum OrderAttentionType {
    LATE_SUCCESS_PENDING,
    PAID_UNFULFILLED,
    PAYMENT_UNKNOWN_OVER_24H,
    DUPLICATE_UNREFUNDED,
    NEEDS_REVIEW,
    REFUND_UNKNOWN
}
