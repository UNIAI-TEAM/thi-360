package vn.unicom.thi360.commerce.admin.domain;

/** Trạng thái thanh toán suy ra của đơn hàng ở màn quản trị (TM 8.1, 9.1). */
public enum AdminPaymentStatus {
    SUCCEEDED,
    PENDING,
    FAILED,
    FREE
}
