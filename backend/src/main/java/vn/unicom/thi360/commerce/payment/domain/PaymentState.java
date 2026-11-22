package vn.unicom.thi360.commerce.payment.domain;

/** Trạng thái của một lần thanh toán (TM 8.1, 8.4). */
public enum PaymentState {
    CREATED,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
