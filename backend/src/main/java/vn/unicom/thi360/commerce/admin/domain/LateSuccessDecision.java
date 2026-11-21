package vn.unicom.thi360.commerce.admin.domain;

/** Quyết định xử lý đối soát ca tiền về muộn trên lần thanh toán đã ghi thất bại (BS §14). */
public enum LateSuccessDecision {
    ACCEPT,
    REFUND
}
