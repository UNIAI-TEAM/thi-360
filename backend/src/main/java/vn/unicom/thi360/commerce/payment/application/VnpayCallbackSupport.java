package vn.unicom.thi360.commerce.payment.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;

/**
 * Chuẩn hóa tham số VNPAY và ánh xạ mã phản hồi thành {@link PaymentState} theo TM 8.4 (Task PBI-07).
 */
final class VnpayCallbackSupport {

    private static final Set<String> CLEAR_FAILURE_RESPONSE_CODES =
            Set.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79");

    private VnpayCallbackSupport() {
    }

    static Map<String, String> extractVnpParams(Map<String, String> allParams) {
        Map<String, String> result = new TreeMap<>();
        if (allParams != null) {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith("vnp_")) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    static boolean hasValidCallbackShape(Map<String, String> vnpParams) {
        if (vnpParams == null) {
            return false;
        }
        return !StringUtils.hasText(vnpParams.get("vnp_Command"))
                && StringUtils.hasText(vnpParams.get("vnp_ResponseCode"))
                && StringUtils.hasText(vnpParams.get("vnp_TransactionStatus"));
    }

    static boolean matchesScaledAmount(String rawAmount, long expectedVnd) {
        String trimmed = trimToNull(rawAmount);
        if (trimmed == null) {
            return false;
        }
        try {
            long scaled = Long.parseLong(trimmed);
            return scaled > 0 && scaled % 100L == 0 && (scaled / 100L) == expectedVnd;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static PaymentState classifyOutcome(Map<String, String> vnpParams) {
        String responseCode = trimToNull(vnpParams.get("vnp_ResponseCode"));
        String txnStatus = trimToNull(vnpParams.get("vnp_TransactionStatus"));
        if ("00".equals(responseCode) && "00".equals(txnStatus)) {
            return PaymentState.SUCCEEDED;
        }
        if ("00".equals(txnStatus) || "01".equals(txnStatus)) {
            return PaymentState.UNKNOWN;
        }
        if (responseCode != null && CLEAR_FAILURE_RESPONSE_CODES.contains(responseCode)) {
            return PaymentState.FAILED;
        }
        return PaymentState.UNKNOWN;
    }

    static String normalizeVnpTransactionNo(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || "0".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    static Map<String, Object> buildRawEventPayload(Map<String, String> params, String clientIp) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (params != null) {
            raw.putAll(params);
        }
        if (clientIp != null && !clientIp.isBlank()) {
            raw.put("_clientIp", clientIp);
        }
        return raw;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
