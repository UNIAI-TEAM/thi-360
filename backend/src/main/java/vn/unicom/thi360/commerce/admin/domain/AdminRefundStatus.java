package vn.unicom.thi360.commerce.admin.domain;

/** Trạng thái hoàn tiền suy ra của đơn hàng ở màn quản trị (TM 8.1, 9.1). */
public enum AdminRefundStatus {
    NONE,
    IN_PROGRESS,
    PARTIAL,
    FULL
}
