package vn.unicom.thi360.commerce.payment.vnpay;

/**
 * Ngoại lệ giao tiếp HTTP với VNPAY ({@code querydr} / {@code refund}): lỗi mạng, quá thời gian,
 * mã HTTP ngoài 2xx hoặc phản hồi không phải JSON hợp lệ. Thông điệp luôn an toàn, không chứa bí mật.
 */
public class VnpayTransportException extends RuntimeException {

    private final boolean timeout;
    private final Integer httpStatus;
    private final String safeDetail;

    public VnpayTransportException(boolean timeout, Integer httpStatus, String safeDetail) {
        super(safeDetail);
        this.timeout = timeout;
        this.httpStatus = httpStatus;
        this.safeDetail = safeDetail;
    }

    public VnpayTransportException(boolean timeout, Integer httpStatus, String safeDetail, Throwable cause) {
        super(safeDetail, cause);
        this.timeout = timeout;
        this.httpStatus = httpStatus;
        this.safeDetail = safeDetail;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getSafeDetail() {
        return safeDetail;
    }
}
