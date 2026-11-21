package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.zone.ZoneRulesException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.reconciliation.application.ReconciliationProperties;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.QueryDecision;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.QueryOutcome;
import vn.unicom.thi360.commerce.refund.application.RefundCodeMapper.RefundMappingDecision;
import vn.unicom.thi360.commerce.refund.application.RefundProperties;
import vn.unicom.thi360.commerce.refund.domain.Refund;
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

    @Test
    void crossChecksTxnRefAndRefundAmountAndAllowsQueryDrOriginalPaymentAmount() {
        RefundResult okRefund = refundResult(true, "00", "05", "900001", "OK");
        RefundMappingDecision matched = defaultMapper.mapRefundResult(okRefund, "TXN1", 100_000L);
        assertThat(matched.state()).isEqualTo(RefundState.SUCCEEDED);
        assertThat(matched.opsAlert()).isFalse();

        RefundMappingDecision mismatchedTxn = defaultMapper.mapRefundResult(okRefund, "OTHER_TXN", 100_000L);
        assertThat(mismatchedTxn.state()).isEqualTo(RefundState.UNKNOWN);
        assertThat(mismatchedTxn.opsAlert()).isTrue();

        RefundMappingDecision mismatchedAmt = defaultMapper.mapRefundResult(okRefund, "TXN1", 50_000L);
        assertThat(mismatchedAmt.state()).isEqualTo(RefundState.UNKNOWN);
        assertThat(mismatchedAmt.opsAlert()).isTrue();

        QueryDrResult query0005 = queryResult(true, "00", "05");
        QueryDecision queryMismatch = defaultMapper.mapQueryDrResult(query0005, "OTHER_TXN");
        assertThat(queryMismatch.outcome()).isEqualTo(QueryOutcome.KEEP);
        assertThat(queryMismatch.opsAlert()).isTrue();

        // Với hoàn một phần, querydr có thể trả về tổng tiền thanh toán gốc (100_000L != refund 50_000L) nhưng vẫn hợp lệ.
        QueryDecision queryPartialMatch = defaultMapper.mapQueryDrResult(query0005, "TXN1");
        assertThat(queryPartialMatch.outcome()).isEqualTo(QueryOutcome.SUCCEEDED);
        assertThat(queryPartialMatch.opsAlert()).isFalse();
    }

    @Test
    void enforcesRefundEntityTransitionGuardsAndAllowsSucceededToFailedOnlyViaRejected09() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        Refund processing = Refund.createApiProcessing(
                UUID.randomUUID(), "req12345678901234567890123456789", UUID.randomUUID(), UUID.randomUUID(),
                100_000L, "Test", "admin", UUID.randomUUID(), now, "idem-1");
        assertThatThrownBy(() -> processing.markFailedManually("reason", "ev", now))
                .isInstanceOf(IllegalStateException.class);

        processing.markSucceeded("00", "05", "900001", "OK", now);
        assertThat(processing.getState()).isEqualTo(RefundState.SUCCEEDED);

        assertThatThrownBy(() -> processing.markFailedFromGateway("91", null, null, "Fail", now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.markUnknown("99", null, null, "Unk", now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.markProcessing("94", null, null, "Proc", now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.markFailedManually("reason", "ev", now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.markSucceeded("00", "05", "900001", "OK", now))
                .isInstanceOf(IllegalStateException.class);

        // Duy nhất markRejected09 cho phép SUCCEEDED -> FAILED.
        processing.markRejected09("00", "09", "900001", "Rejected 09", now);
        assertThat(processing.getState()).isEqualTo(RefundState.FAILED);

        assertThatThrownBy(() -> processing.markSucceeded("00", "05", "900001", "OK", now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.markUnknown("99", null, null, "Unk", now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.markRejected09("00", "09", "900001", "Again", now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({RefundProperties.class, ReconciliationProperties.class})
    static class PropertiesTestConfig {
    }

    @Test
    void bindsRefundCode02PolicyAndReconciliationDefaultsFromApplicationYmlAndValidatesCronAndZone() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(PropertiesTestConfig.class)
                .withInitializer(new ConfigDataApplicationContextInitializer());

        runner.withPropertyValues("thi360.commerce.refund.vnpay-code02-policy=MERCHANT_ERROR_FAILED")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RefundProperties refundProps = context.getBean(RefundProperties.class);
                    assertThat(refundProps.vnpayCode02Policy()).isEqualTo(VnpayCode02Policy.MERCHANT_ERROR_FAILED);
                    assertThat(new RefundCodeMapper(refundProps)
                                    .mapRefundResult(refundResult(true, "02", null, null, "Code 02"))
                                    .state())
                            .isEqualTo(RefundState.FAILED);
                    ReconciliationProperties reconProps = context.getBean(ReconciliationProperties.class);
                    assertThat(reconProps.cron()).isEqualTo("0 0 2 * * *");
                    assertThat(reconProps.zone()).isEqualTo("Asia/Ho_Chi_Minh");
                });

        runner.withPropertyValues(
                        "thi360.commerce.reconciliation.cron=0 30 3 * * *",
                        "thi360.commerce.reconciliation.zone=UTC")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ReconciliationProperties reconProps = context.getBean(ReconciliationProperties.class);
                    assertThat(reconProps.cron()).isEqualTo("0 30 3 * * *");
                    assertThat(reconProps.zone()).isEqualTo("UTC");
                });

        assertThatThrownBy(() -> new ReconciliationProperties("invalid-cron", "Asia/Ho_Chi_Minh"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReconciliationProperties("0 0 2 * * *", "Invalid/Zone"))
                .isInstanceOf(ZoneRulesException.class);
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
