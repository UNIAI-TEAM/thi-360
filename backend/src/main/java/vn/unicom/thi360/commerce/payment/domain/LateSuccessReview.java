package vn.unicom.thi360.commerce.payment.domain;

/** Trạng thái đối soát ca tiền về muộn trên lần đã ghi thất bại (BS 14 Ca C). */
public enum LateSuccessReview {
    PENDING,
    ACCEPTED,
    REJECTED,
    AUTO_EXCESS
}
