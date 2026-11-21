package vn.unicom.thi360.commerce.payment.domain;

/** Nguồn ghi nhận sự kiện thanh toán (TM 8.1). */
public enum PaymentEventSource {
    IPN,
    RETURN,
    QUERYDR,
    REFUND_API
}
