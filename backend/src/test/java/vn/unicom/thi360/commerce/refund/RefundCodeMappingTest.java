package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.QueryDecision;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.QueryOutcome;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.RefundMappingDecision;
import vn.unicom.thi360.commerce.refund.application.RefundProperties;
import vn.unicom.thi360.commerce.refund.domain.RefundState;
import vn.unicom.thi360.commerce.refund.domain.VnpayCode02Policy;

/**
 * AC-13: kiểm tra ánh xạ toàn bộ mã phản hồi hoàn tiền VNPAY (TM 9.4, Q14, AD-116),
 * bao gồm 3 chính sách cho mã {@code 02}, chữ ký sai, quá thời gian và mã lạ.
 */
class RefundCodeMappingTest {

    private final RefundCodeMapper defaultMapper = new RefundCodeMapper(new RefundProperties());

    @Test
    void maps00ToSucceededWithoutOpsAlert() {
        RefundMappingDecision decision = defaultMapper.mapRefundResult(refundResult(true, "00", "05", "900001", "OK"));
        assertThat(decision.state()).isEqualTo(RefundState.SUCCEEDED);
        assertThat(decision.opsAlert()).isFalse();
        assertThat(decision.responseCode()).isEqualTo("00");
        assertThat(decision.transactionStatus()).isEqualTo("05");
        assertThat(decision.transactionNo()).isEqualTo("900001");
    }

    @Test
    void maps94ToProcessingWithoutOpsAlert() {
        RefundMappingDecision decision = defaultMapper.mapRefundResult(refundResult(true, "94", "05", null, "Duplicate"));
        assertThat(decision.state()).isEqualTo(RefundState.PROCESSING);
        assertThat(decision.opsAlert()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"91", "95", "04", "13", "93"})
    void mapsRejectedCodesToFailedWithoutOpsAlert(String code) {
        RefundMappingDecision decision = defaultMapper.mapRefundResult(refundResult(true, code, "02", null, "Rejected"));
        assertThat(decision.state()).isEqualTo(RefundState.FAILED);
        assertThat(decision.opsAlert()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"97", "03"})
    void mapsConfigAndSignatureErrorCodesToFailedWithOpsAlert(String code) {
        RefundMappingDecision decision = defaultMapper.mapRefundResult(refundResult(true, code, null, null, "Merchant error"));
        assertThat(decision.state()).isEqualTo(RefundState.FAILED);
        assertThat(decision.opsAlert()).isTrue();
    }

    @Test
    void mapsCode02AccordingToQ14Policy() {
        RefundResult code02 = refundResult(true, "02", null, null, "Code 02");

        RefundCodeMapper unknownPolicyMapper = new RefundCodeMapper(
                new RefundProperties(VnpayCode02Policy.UNKNOWN, "127.0.0.1"));
        RefundMappingDecision unknownDecision = unknownPolicyMapper.mapRefundResult(code02);
        assertThat(unknownDecision.state()).isEqualTo(RefundState.UNKNOWN);
        assertThat(unknownDecision.opsAlert()).isFalse();

        RefundCodeMapper merchantErrorMapper = new RefundCodeMapper(
                new RefundProperties(VnpayCode02Policy.MERCHANT_ERROR_FAILED, "127.0.0.1"));
        RefundMappingDecision merchantDecision = merchantErrorMapper.mapRefundResult(code02);
        assertThat(merchantDecision.state()).isEqualTo(RefundState.FAILED);
        assertThat(merchantDecision.opsAlert()).isTrue();

        RefundCodeMapper amountRejectedMapper = new RefundCodeMapper(
                new RefundProperties(VnpayCode02Policy.AMOUNT_REJECTED_FAILED, "127.0.0.1"));
        RefundMappingDecision amountDecision = amountRejectedMapper.mapRefundResult(code02);
        assertThat(amountDecision.state()).isEqualTo(RefundState.FAILED);
        assertThat(amountDecision.opsAlert()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"98", "99", "77", "", "  "})
    void mapsTimeoutUnknownAndUnrecognizedCodesToUnknown(String code) {
        RefundMappingDecision decision = defaultMapper.mapRefundResult(refundResult(true, code, null, null, "Unclear"));
        assertThat(decision.state()).isEqualTo(RefundState.UNKNOWN);
        assertThat(decision.opsAlert()).isFalse();
    }

    @Test
    void mapsInvalidSignatureNullResponseAndTimeoutExceptionToUnknown() {
        RefundMappingDecision invalidSig = defaultMapper.mapRefundResult(refundResult(false, "00", "05", "900001", "Bad sig"));
        assertThat(invalidSig.state()).isEqualTo(RefundState.UNKNOWN);
        assertThat(invalidSig.opsAlert()).isFalse();

        RefundMappingDecision nullResult = defaultMapper.mapRefundResult(null);
        assertThat(nullResult.state()).isEqualTo(RefundState.UNKNOWN);
        assertThat(nullResult.opsAlert()).isFalse();

        RefundMappingDecision timeout = defaultMapper.mapRefundException(new SocketTimeoutException("Read timed out"));
        assertThat(timeout.state()).isEqualTo(RefundState.UNKNOWN);
        assertThat(timeout.opsAlert()).isFalse();
        assertThat(timeout.message()).contains("Read timed out");
    }

    @Test
    void mapsQueryDrResponsesForRefunds() {
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "00", "05")).outcome())
                .isEqualTo(QueryOutcome.SUCCEEDED);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "00", "06")).outcome())
                .isEqualTo(QueryOutcome.SUCCEEDED);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "00", "09")).outcome())
                .isEqualTo(QueryOutcome.FAILED_REJECTED_09);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "00", "02")).outcome())
                .isEqualTo(QueryOutcome.FAILED);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "91", null)).outcome())
                .isEqualTo(QueryOutcome.FAILED);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "95", null)).outcome())
                .isEqualTo(QueryOutcome.FAILED);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "94", null)).outcome())
                .isEqualTo(QueryOutcome.KEEP);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(true, "99", null)).outcome())
                .isEqualTo(QueryOutcome.KEEP);
        assertThat(defaultMapper.mapQueryDrResult(queryResult(false, "00", "05")).outcome())
                .isEqualTo(QueryOutcome.KEEP);

        QueryDecision configErr = defaultMapper.mapQueryDrResult(queryResult(true, "97", null));
        assertThat(configErr.outcome()).isEqualTo(QueryOutcome.KEEP);
        assertThat(configErr.opsAlert()).isTrue();
    }

    private static RefundResult refundResult(
            boolean signatureValid, String responseCode, String txnStatus, String txnNo, String message) {
        return new RefundResult(
                signatureValid, "req-1", responseCode, message, "TXN1", 100_000L, "NCB",
                "20261121100500", txnNo, "02", txnStatus, "Hoan tien", Map.of());
    }

    private static QueryDrResult queryResult(boolean signatureValid, String responseCode, String txnStatus) {
        return new QueryDrResult(
                signatureValid, "req-1", responseCode, "Msg", "TXN1", 100_000L, "NCB",
                "20261121100500", "900001", "02", txnStatus, "Tra cuu", null, null, Map.of());
    }
}
