package vn.unicom.thi360.commerce.payment.vnpay;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;

/**
 * Bảng ánh xạ hai tầng kết quả {@code querydr} theo TM §8.4 (AC-5..AC-14, AC-25, AC-26).
 */
public final class QueryDrMapper {

    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public enum DecisionKind {
        SUCCEEDED,
        FAILED,
        UNKNOWN,
        UNKNOWN_NEEDS_REVIEW,
        KEEP_UNCHANGED
    }

    public record QueryDrDecision(
            DecisionKind kind,
            String outcomeTag,
            String reviewReason,
            boolean logConfigError
    ) {
    }

    private QueryDrMapper() {
    }

    /**
     * Đưa ra quyết định xử lý từ {@link QueryDrResult} đã kiểm chữ ký, đối chiếu số tiền,
     * mã tham chiếu và ngưỡng {@code vnp_ExpireDate + graceAfterExpire}.
     */
    public static QueryDrDecision decide(
            QueryDrResult result,
            String expectedTxnRef,
            long expectedAmountVnd,
            String vnpExpireDate,
            Instant now,
            Duration graceAfterExpire) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(graceAfterExpire, "graceAfterExpire must not be null");

        if (result == null || !result.signatureValid()) {
            return new QueryDrDecision(
                    DecisionKind.KEEP_UNCHANGED, "QUERYDR_INVALID_SIGNATURE", null, false);
        }

        String returnedTxnRef = trimToNull(result.txnRef());
        String expectedRef = trimToNull(expectedTxnRef);
        if (returnedTxnRef != null && expectedRef != null && !returnedTxnRef.equals(expectedRef)) {
            return new QueryDrDecision(
                    DecisionKind.UNKNOWN_NEEDS_REVIEW,
                    "QUERYDR_TXN_REF_MISMATCH",
                    "VNPAY_QUERYDR_TXN_REF_MISMATCH",
                    false);
        }

        String responseCode = trimToNull(result.responseCode());
        String txnStatus = trimToNull(result.transactionStatus());

        if ("00".equals(responseCode)) {
            return mapSuccessEnvelope(result, expectedTxnRef, expectedAmountVnd, txnStatus);
        }
        if ("91".equals(responseCode)) {
            return mapNotFoundAfterGrace(vnpExpireDate, now, graceAfterExpire);
        }
        if ("94".equals(responseCode) || "99".equals(responseCode)) {
            return new QueryDrDecision(
                    DecisionKind.KEEP_UNCHANGED, "QUERYDR_RETRYABLE_" + responseCode, null, false);
        }
        if ("02".equals(responseCode) || "03".equals(responseCode) || "97".equals(responseCode)) {
            return new QueryDrDecision(
                    DecisionKind.KEEP_UNCHANGED, "QUERYDR_CONFIG_ERROR_" + responseCode, null, true);
        }
        return new QueryDrDecision(
                DecisionKind.UNKNOWN, "QUERYDR_UNKNOWN_RESPONSE_CODE", null, false);
    }

    private static QueryDrDecision mapSuccessEnvelope(
            QueryDrResult result,
            String expectedTxnRef,
            long expectedAmountVnd,
            String txnStatus) {
        if ("00".equals(txnStatus)) {
            if (result.amountVnd() == null || result.amountVnd() != expectedAmountVnd) {
                return new QueryDrDecision(
                        DecisionKind.UNKNOWN_NEEDS_REVIEW,
                        "QUERYDR_AMOUNT_MISMATCH",
                        "VNPAY_QUERYDR_AMOUNT_MISMATCH",
                        false);
            }
            String returnedTxnRef = trimToNull(result.txnRef());
            String expectedRef = trimToNull(expectedTxnRef);
            if (returnedTxnRef == null || expectedRef == null || !returnedTxnRef.equals(expectedRef)) {
                return new QueryDrDecision(
                        DecisionKind.UNKNOWN_NEEDS_REVIEW,
                        "QUERYDR_TXN_REF_MISMATCH",
                        "VNPAY_QUERYDR_TXN_REF_MISMATCH",
                        false);
            }
            return new QueryDrDecision(DecisionKind.SUCCEEDED, "QUERYDR_SUCCEEDED_00", null, false);
        }
        if ("01".equals(txnStatus)) {
            return new QueryDrDecision(
                    DecisionKind.KEEP_UNCHANGED, "QUERYDR_INCOMPLETE_00_01", null, false);
        }
        if ("02".equals(txnStatus)) {
            return new QueryDrDecision(DecisionKind.FAILED, "QUERYDR_FAILED_00_02", null, false);
        }
        if ("04".equals(txnStatus) || "07".equals(txnStatus)) {
            return new QueryDrDecision(
                    DecisionKind.UNKNOWN_NEEDS_REVIEW,
                    "QUERYDR_REVIEW_00_" + txnStatus,
                    "VNPAY_QUERYDR_STATUS_" + txnStatus,
                    false);
        }
        if ("05".equals(txnStatus) || "06".equals(txnStatus) || "09".equals(txnStatus)) {
            return new QueryDrDecision(
                    DecisionKind.KEEP_UNCHANGED, "QUERYDR_REFUND_STATUS_IGNORED_00_" + txnStatus, null, false);
        }
        return new QueryDrDecision(DecisionKind.UNKNOWN, "QUERYDR_UNKNOWN_TXN_STATUS", null, false);
    }

    private static QueryDrDecision mapNotFoundAfterGrace(
            String vnpExpireDate, Instant now, Duration graceAfterExpire) {
        Instant expireInstant = parseVnpTimestamp(vnpExpireDate);
        if (expireInstant == null) {
            return new QueryDrDecision(
                    DecisionKind.KEEP_UNCHANGED, "QUERYDR_NOT_FOUND_BEFORE_GRACE_91", null, false);
        }
        Instant threshold = expireInstant.plus(graceAfterExpire);
        if (now.isBefore(threshold)) {
            return new QueryDrDecision(
                    DecisionKind.KEEP_UNCHANGED, "QUERYDR_NOT_FOUND_BEFORE_GRACE_91", null, false);
        }
        return new QueryDrDecision(DecisionKind.FAILED, "QUERYDR_NOT_FOUND_FAILED_91", null, false);
    }

    static Instant parseVnpTimestamp(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(trimmed, VNP_TIME_FMT).atZone(GMT_PLUS_7).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
