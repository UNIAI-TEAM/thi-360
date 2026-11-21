package vn.unicom.thi360.commerce.refund.application;

import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.refund.domain.RefundState;

/**
 * Ánh xạ mã phản hồi của VNPAY khi gọi {@code refund} và {@code querydr} cho giao dịch hoàn (TM 9.4, Q14, AD-116).
 */
@Component
public class RefundCodeMapper {

    private static final Set<String> REJECTED_CODES = Set.of("91", "95", "04", "13", "93");
    private static final Set<String> CONFIG_OR_SIGNATURE_ERROR_CODES = Set.of("97", "03");

    private final RefundProperties properties;

    public RefundCodeMapper(RefundProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public RefundMappingDecision mapRefundResult(RefundResult result) {
        return mapRefundResult(result, null, null);
    }

    public RefundMappingDecision mapRefundResult(
            RefundResult result, String expectedTxnRef, Long expectedAmountVnd) {
        if (result == null) {
            return new RefundMappingDecision(
                    RefundState.UNKNOWN, false, null, null, null, "Null refund gateway response");
        }
        String code = trimToNull(result.responseCode());
        String txnStatus = trimToNull(result.transactionStatus());
        String txnNo = normalizeTxnNo(result.transactionNo());
        String message = trimToNull(result.message());

        if (!result.signatureValid()) {
            return new RefundMappingDecision(
                    RefundState.UNKNOWN,
                    false,
                    code,
                    txnStatus,
                    txnNo,
                    message != null ? message : "Invalid refund response signature");
        }
        String cleanExpectedTxnRef = trimToNull(expectedTxnRef);
        String resTxnRef = trimToNull(result.txnRef());
        if (cleanExpectedTxnRef != null && resTxnRef != null && !cleanExpectedTxnRef.equals(resTxnRef)) {
            return new RefundMappingDecision(
                    RefundState.UNKNOWN,
                    true,
                    code,
                    txnStatus,
                    txnNo,
                    "Mismatched refund txnRef in gateway response");
        }
        if (("00".equals(code) || "94".equals(code))
                && expectedAmountVnd != null
                && result.amountVnd() != null
                && !expectedAmountVnd.equals(result.amountVnd())) {
            return new RefundMappingDecision(
                    RefundState.UNKNOWN,
                    true,
                    code,
                    txnStatus,
                    txnNo,
                    "Mismatched refund amountVnd in gateway response");
        }
        if (code == null) {
            return new RefundMappingDecision(
                    RefundState.UNKNOWN, false, null, txnStatus, txnNo, message);
        }
        if ("00".equals(code)) {
            return new RefundMappingDecision(
                    RefundState.SUCCEEDED, false, code, txnStatus, txnNo, message);
        }
        if ("94".equals(code)) {
            return new RefundMappingDecision(
                    RefundState.PROCESSING, false, code, txnStatus, txnNo, message);
        }
        if (REJECTED_CODES.contains(code)) {
            return new RefundMappingDecision(
                    RefundState.FAILED, false, code, txnStatus, txnNo, message);
        }
        if (CONFIG_OR_SIGNATURE_ERROR_CODES.contains(code)) {
            return new RefundMappingDecision(
                    RefundState.FAILED, true, code, txnStatus, txnNo, message);
        }
        if ("02".equals(code)) {
            return switch (properties.vnpayCode02Policy()) {
                case UNKNOWN -> new RefundMappingDecision(
                        RefundState.UNKNOWN, false, code, txnStatus, txnNo, message);
                case MERCHANT_ERROR_FAILED -> new RefundMappingDecision(
                        RefundState.FAILED, true, code, txnStatus, txnNo, message);
                case AMOUNT_REJECTED_FAILED -> new RefundMappingDecision(
                        RefundState.FAILED, false, code, txnStatus, txnNo, message);
            };
        }
        // "98", "99", hoặc bất kỳ mã chưa xác định nào khác đều giữ UNKNOWN để tránh hoàn tiền hai lần.
        return new RefundMappingDecision(
                RefundState.UNKNOWN, false, code, txnStatus, txnNo, message);
    }

    public RefundMappingDecision mapRefundException(Throwable ex) {
        String msg = ex == null ? "Gateway call failed" : ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex == null ? "Gateway call failed" : ex.getClass().getSimpleName();
        }
        return new RefundMappingDecision(RefundState.UNKNOWN, false, null, null, null, msg);
    }

    public QueryDecision mapQueryDrResult(QueryDrResult result) {
        return mapQueryDrResult(result, null);
    }

    public QueryDecision mapQueryDrResult(QueryDrResult result, String expectedTxnRef) {
        if (result == null) {
            return new QueryDecision(QueryOutcome.KEEP, false, null, null, null, "Null querydr response");
        }
        String code = trimToNull(result.responseCode());
        String txnStatus = trimToNull(result.transactionStatus());
        String txnNo = normalizeTxnNo(result.transactionNo());
        String message = trimToNull(result.message());

        if (!result.signatureValid()) {
            return new QueryDecision(
                    QueryOutcome.KEEP,
                    false,
                    code,
                    txnStatus,
                    txnNo,
                    message != null ? message : "Invalid querydr response signature");
        }
        String cleanExpectedTxnRef = trimToNull(expectedTxnRef);
        String resTxnRef = trimToNull(result.txnRef());
        if (cleanExpectedTxnRef != null && resTxnRef != null && !cleanExpectedTxnRef.equals(resTxnRef)) {
            return new QueryDecision(
                    QueryOutcome.KEEP,
                    true,
                    code,
                    txnStatus,
                    txnNo,
                    "Mismatched querydr txnRef in gateway response");
        }
        if ("00".equals(code)) {
            if ("05".equals(txnStatus) || "06".equals(txnStatus)) {
                return new QueryDecision(QueryOutcome.SUCCEEDED, false, code, txnStatus, txnNo, message);
            }
            if ("09".equals(txnStatus)) {
                return new QueryDecision(QueryOutcome.FAILED_REJECTED_09, false, code, txnStatus, txnNo, message);
            }
            if ("02".equals(txnStatus)) {
                return new QueryDecision(QueryOutcome.FAILED, false, code, txnStatus, txnNo, message);
            }
            return new QueryDecision(QueryOutcome.KEEP, false, code, txnStatus, txnNo, message);
        }
        if ("91".equals(code) || "95".equals(code)) {
            return new QueryDecision(QueryOutcome.FAILED, false, code, txnStatus, txnNo, message);
        }
        if ("02".equals(code) || "03".equals(code) || "97".equals(code)) {
            return new QueryDecision(QueryOutcome.KEEP, true, code, txnStatus, txnNo, message);
        }
        return new QueryDecision(QueryOutcome.KEEP, false, code, txnStatus, txnNo, message);
    }

    private static String normalizeTxnNo(String raw) {
        String trimmed = trimToNull(raw);
        return (trimmed == null || "0".equals(trimmed)) ? null : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record RefundMappingDecision(
            RefundState state,
            boolean opsAlert,
            String responseCode,
            String transactionStatus,
            String transactionNo,
            String message
    ) {
    }

    public enum QueryOutcome {
        SUCCEEDED,
        FAILED,
        FAILED_REJECTED_09,
        KEEP
    }

    public record QueryDecision(
            QueryOutcome outcome,
            boolean opsAlert,
            String responseCode,
            String transactionStatus,
            String transactionNo,
            String message
    ) {
    }
}
