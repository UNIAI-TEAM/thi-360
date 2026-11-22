package vn.unicom.thi360.commerce.order.domain;

/** Trạng thái vòng đời của đơn hàng (TM 8.1). */
public enum OrderState {
    PENDING_PAYMENT,
    PAID,
    FULFILLED,
    EXPIRED,
    REPLACED,
    CANCELLED,
    REFUNDED
}
