package vn.unicom.thi360.commerce.refund.domain;

/**
 * Chính sách xử lý mã lỗi {@code 02} khi gọi hoàn tiền VNPAY (TM Q14, AD-116).
 */
public enum VnpayCode02Policy {
    UNKNOWN,
    MERCHANT_ERROR_FAILED,
    AMOUNT_REJECTED_FAILED
}
