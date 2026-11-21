package vn.unicom.thi360.commerce.refund.domain;

/** Trạng thái yêu cầu hoàn tiền (TM 9.4). */
public enum RefundState {
    PROCESSING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
