package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrMapper.DecisionKind;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrMapper.QueryDrDecision;

/**
 * Kiểm thử đơn vị bảng ánh xạ hai tầng {@link QueryDrMapper} theo TM §8.4 (AC-5..AC-13, AC-25).
 */
class QueryDrMapperTest {

    private static final String TXN_REF = "DH261121MAP01";
    private static final long AMOUNT_VND = 100_000L;
    private static final String EXPIRE_DATE_GMT7 = "20261121101500";
    private static final Instant EXPIRE_INSTANT = Instant.parse("2026-11-21T03:15:00Z");
    private static final Duration GRACE = Duration.ofMinutes(15);

    @Test
    void mapsInvalidSignatureToKeepUnchanged() {
        QueryDrResult invalid = result(false, "00", "00", AMOUNT_VND, TXN_REF);
        QueryDrDecision decision = QueryDrMapper.decide(
                invalid, TXN_REF, AMOUNT_VND, EXPIRE_DATE_GMT7, EXPIRE_INSTANT, GRACE);
        assertThat(decision.kind()).isEqualTo(DecisionKind.KEEP_UNCHANGED);
        assertThat(decision.outcomeTag()).isEqualTo("QUERYDR_INVALID_SIGNATURE");
        assertThat(decision.logConfigError()).isFalse();
    }

    @Test
    void mapsResponseCode00TransactionStatusesAndMismatchChecks() {
        QueryDrDecision validSuccess = decide(result(true, "00", "00", AMOUNT_VND, TXN_REF), EXPIRE_INSTANT);
        assertThat(validSuccess.kind()).isEqualTo(DecisionKind.SUCCEEDED);
        assertThat(validSuccess.outcomeTag()).isEqualTo("QUERYDR_SUCCEEDED_00");
        assertThat(validSuccess.reviewReason()).isNull();

        QueryDrDecision missingAmount = decide(result(true, "00", "00", null, TXN_REF), EXPIRE_INSTANT);
        assertThat(missingAmount.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
        assertThat(missingAmount.outcomeTag()).isEqualTo("QUERYDR_AMOUNT_MISMATCH");
        assertThat(missingAmount.reviewReason()).isEqualTo("VNPAY_QUERYDR_AMOUNT_MISMATCH");

        QueryDrDecision missingTxnRef = decide(result(true, "00", "00", AMOUNT_VND, null), EXPIRE_INSTANT);
        assertThat(missingTxnRef.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
        assertThat(missingTxnRef.outcomeTag()).isEqualTo("QUERYDR_TXN_REF_MISMATCH");
        assertThat(missingTxnRef.reviewReason()).isEqualTo("VNPAY_QUERYDR_TXN_REF_MISMATCH");

        QueryDrDecision blankTxnRef = decide(result(true, "00", "00", AMOUNT_VND, "   "), EXPIRE_INSTANT);
        assertThat(blankTxnRef.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
        assertThat(blankTxnRef.outcomeTag()).isEqualTo("QUERYDR_TXN_REF_MISMATCH");
        assertThat(blankTxnRef.reviewReason()).isEqualTo("VNPAY_QUERYDR_TXN_REF_MISMATCH");

        QueryDrDecision bothMissing = decide(result(true, "00", "00", null, null), EXPIRE_INSTANT);
        assertThat(bothMissing.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
        assertThat(bothMissing.outcomeTag()).isEqualTo("QUERYDR_AMOUNT_MISMATCH");

        QueryDrDecision amountMismatch = decide(result(true, "00", "00", 50_000L, TXN_REF), EXPIRE_INSTANT);
        assertThat(amountMismatch.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
        assertThat(amountMismatch.outcomeTag()).isEqualTo("QUERYDR_AMOUNT_MISMATCH");
        assertThat(amountMismatch.reviewReason()).isEqualTo("VNPAY_QUERYDR_AMOUNT_MISMATCH");

        QueryDrDecision txnRefMismatch = decide(result(true, "00", "00", AMOUNT_VND, "OTHER_REF"), EXPIRE_INSTANT);
        assertThat(txnRefMismatch.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
        assertThat(txnRefMismatch.outcomeTag()).isEqualTo("QUERYDR_TXN_REF_MISMATCH");
        assertThat(txnRefMismatch.reviewReason()).isEqualTo("VNPAY_QUERYDR_TXN_REF_MISMATCH");

        assertThat(decide(result(true, "00", "01", AMOUNT_VND, TXN_REF), EXPIRE_INSTANT).kind())
                .isEqualTo(DecisionKind.KEEP_UNCHANGED);

        assertThat(decide(result(true, "00", "02", AMOUNT_VND, TXN_REF), EXPIRE_INSTANT).kind())
                .isEqualTo(DecisionKind.FAILED);

        for (String reviewStatus : new String[] {"04", "07"}) {
            QueryDrDecision d = decide(result(true, "00", reviewStatus, AMOUNT_VND, TXN_REF), EXPIRE_INSTANT);
            assertThat(d.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
            assertThat(d.outcomeTag()).isEqualTo("QUERYDR_REVIEW_00_" + reviewStatus);
            assertThat(d.reviewReason()).isEqualTo("VNPAY_QUERYDR_STATUS_" + reviewStatus);
        }

        for (String refundStatus : new String[] {"05", "06", "09"}) {
            QueryDrDecision d = decide(result(true, "00", refundStatus, AMOUNT_VND, TXN_REF), EXPIRE_INSTANT);
            assertThat(d.kind()).isEqualTo(DecisionKind.KEEP_UNCHANGED);
            assertThat(d.outcomeTag()).isEqualTo("QUERYDR_REFUND_STATUS_IGNORED_00_" + refundStatus);
        }

        assertThat(decide(result(true, "00", "88", AMOUNT_VND, TXN_REF), EXPIRE_INSTANT).kind())
                .isEqualTo(DecisionKind.UNKNOWN);
        assertThat(decide(result(true, "00", null, AMOUNT_VND, TXN_REF), EXPIRE_INSTANT).kind())
                .isEqualTo(DecisionKind.UNKNOWN);
    }

    @Test
    void maps91GraceBoundaryAndConfigRetryableUnknownCodes() {
        Instant beforeThreshold = EXPIRE_INSTANT.plus(GRACE).minusSeconds(1);
        Instant atThreshold = EXPIRE_INSTANT.plus(GRACE);

        assertThat(decide(result(true, "91", null, null, TXN_REF), beforeThreshold).kind())
                .isEqualTo(DecisionKind.KEEP_UNCHANGED);
        assertThat(decide(result(true, "91", null, null, TXN_REF), atThreshold).kind())
                .isEqualTo(DecisionKind.FAILED);

        for (String retryCode : new String[] {"94", "99"}) {
            QueryDrDecision d = decide(result(true, retryCode, null, null, TXN_REF), atThreshold);
            assertThat(d.kind()).isEqualTo(DecisionKind.KEEP_UNCHANGED);
            assertThat(d.logConfigError()).isFalse();
        }

        for (String cfgCode : new String[] {"02", "03", "97"}) {
            QueryDrDecision d = decide(result(true, cfgCode, null, null, TXN_REF), atThreshold);
            assertThat(d.kind()).isEqualTo(DecisionKind.KEEP_UNCHANGED);
            assertThat(d.logConfigError()).isTrue();
            assertThat(d.outcomeTag()).isEqualTo("QUERYDR_CONFIG_ERROR_" + cfgCode);
        }

        assertThat(decide(result(true, "77", null, null, TXN_REF), atThreshold).kind())
                .isEqualTo(DecisionKind.UNKNOWN);
        assertThat(decide(result(true, null, null, null, TXN_REF), atThreshold).kind())
                .isEqualTo(DecisionKind.UNKNOWN);

        for (QueryDrResult mismatched : new QueryDrResult[] {
            result(true, "00", "02", AMOUNT_VND, "OTHER_REF"),
            result(true, "00", "04", AMOUNT_VND, "OTHER_REF"),
            result(true, "00", "07", AMOUNT_VND, "OTHER_REF"),
            result(true, "91", null, null, "OTHER_REF")
        }) {
            QueryDrDecision d = decide(mismatched, atThreshold);
            assertThat(d.kind()).isEqualTo(DecisionKind.UNKNOWN_NEEDS_REVIEW);
            assertThat(d.outcomeTag()).isEqualTo("QUERYDR_TXN_REF_MISMATCH");
            assertThat(d.reviewReason()).isEqualTo("VNPAY_QUERYDR_TXN_REF_MISMATCH");
        }
    }

    private static QueryDrDecision decide(QueryDrResult res, Instant now) {
        return QueryDrMapper.decide(res, TXN_REF, AMOUNT_VND, EXPIRE_DATE_GMT7, now, GRACE);
    }

    private static QueryDrResult result(
            boolean sigValid, String respCode, String txnStatus, Long amountVnd, String txnRef) {
        return new QueryDrResult(
                sigValid, "r1", respCode, "msg", txnRef, amountVnd, "NCB",
                "20261121100100", "90001", "01", txnStatus, "info", null, null, Map.of());
    }
}
