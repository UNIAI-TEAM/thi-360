package vn.unicom.thi360.commerce.payment.gateway;

import java.util.Map;

/**
 * Cổng giao tiếp VNPAY 2.1.0 (plan B9, TM 8.4): gồm đủ bốn thao tác {@code pay}, {@code verify},
 * {@code queryDr}, {@code refund}. CM-3a cài bản giả lập ({@link MockVnpayGateway}), CM-3b cài bản thật.
 */
public interface VnpayGateway {

    /** Dựng URL chuyển hướng tới cổng thanh toán VNPAY. */
    String pay(PayRequest request);

    /** Kiểm tra chữ ký {@code vnp_SecureHash} của tham số IPN hoặc Return URL. */
    boolean verify(Map<String, String> params);

    /** Tra cứu trạng thái giao dịch ({@code querydr}). */
    QueryDrResult queryDr(QueryDrRequest request);

    /** Gửi yêu cầu hoàn tiền giao dịch ({@code refund}). */
    RefundResult refund(RefundRequest request);

    record PayRequest(
            String txnRef,
            String orderCode,
            long amountVnd,
            String orderInfo,
            String locale,
            String returnUrl,
            String ipAddr,
            String createDate,
            String expireDate
    ) {
    }

    record QueryDrRequest(
            String requestId,
            String txnRef,
            String transactionDate,
            String createDate,
            String ipAddr,
            String orderInfo,
            String transactionNo
    ) {
    }

    record QueryDrResult(
            boolean signatureValid,
            String responseId,
            String responseCode,
            String message,
            String txnRef,
            Long amountVnd,
            String bankCode,
            String payDate,
            String transactionNo,
            String transactionType,
            String transactionStatus,
            String orderInfo,
            String promotionCode,
            String promotionAmount,
            Map<String, Object> rawResponse
    ) {
        public QueryDrResult {
            rawResponse = rawResponse == null ? Map.of() : Map.copyOf(rawResponse);
        }
    }

    record RefundRequest(
            String requestId,
            String transactionType,
            String txnRef,
            long amountVnd,
            String transactionNo,
            String transactionDate,
            String createBy,
            String createDate,
            String ipAddr,
            String orderInfo
    ) {
    }

    record RefundResult(
            boolean signatureValid,
            String responseId,
            String responseCode,
            String message,
            String txnRef,
            Long amountVnd,
            String bankCode,
            String payDate,
            String transactionNo,
            String transactionType,
            String transactionStatus,
            String orderInfo,
            Map<String, Object> rawResponse
    ) {
        public RefundResult {
            rawResponse = rawResponse == null ? Map.of() : Map.copyOf(rawResponse);
        }
    }
}
