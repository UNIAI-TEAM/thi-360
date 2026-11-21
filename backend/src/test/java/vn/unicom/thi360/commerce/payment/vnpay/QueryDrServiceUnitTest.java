package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.ApplyResult;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier.QueryDrOutcomeAction;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository.ClaimedPayment;
import vn.unicom.thi360.shared.testapi.MutableClock;

/**
 * Kiểm thử đơn vị {@link QueryDrService}: ngắt mạch khi lỗi truyền tải liên tiếp, giới hạn thời gian chu kỳ,
 * ngắt luồng ({@code R1-20}) và phân tách lỗi nội bộ {@code QUERYDR_LOCAL_ERROR} ({@code R1-27}).
 */
class QueryDrServiceUnitTest {

    private static final Instant BASE_NOW = Instant.parse("2026-11-21T08:00:00Z");

    @Test
    void runQueryCycleStopsAfterConsecutiveTransportFailuresAndResetsOnNonTransportResult() {
        QueryDrClaimRepository claimRepo = mock(QueryDrClaimRepository.class);
        VnpayGateway gateway = mock(VnpayGateway.class);
        PaymentOutcomeApplier applier = mock(PaymentOutcomeApplier.class);
        when(applier.applyQueryDrOutcome(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ApplyResult("00", "OK", null));

        List<ClaimedPayment> batch = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            batch.add(sampleClaimed("DH_CIRCUIT_" + i));
        }
        when(claimRepo.claimEligibleForWorker(any(), any(), any(), any(), anyInt()))
                .thenReturn(batch)
                .thenReturn(List.of(sampleClaimed("DH_BATCH2_0")));

        AtomicInteger calls = new AtomicInteger();
        when(gateway.queryDr(any())).thenAnswer(inv -> {
            int n = calls.incrementAndGet();
            if (n == 5) {
                return new QueryDrResult(
                        true, "r5", "00", "Incomplete", "DH_CIRCUIT_4", 200_000L,
                        "NCB", "20261121150000", "0", "01", "01", "Info", null, null, Map.of());
            }
            throw new VnpayTransportException(true, null, "VNPAY querydr timed out");
        });

        QueryDrService service = createService(
                claimRepo, gateway, applier,
                new QueryDrProperties(
                        Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(24),
                        Duration.ofMinutes(5), Duration.ofMinutes(15), 20, Duration.ofMinutes(4), 5),
                Clock.fixed(BASE_NOW, ZoneOffset.UTC));

        // 4 lần timeout -> lần thứ 5 không phải lỗi truyền tải (reset về 0) -> 5 lần timeout tiếp theo -> dừng ở lần thứ 10.
        int queried = service.runQueryCycle();
        assertThat(queried).isEqualTo(10);
        verify(gateway, times(10)).queryDr(any());
        verify(claimRepo, times(1)).claimEligibleForWorker(any(), any(), any(), any(), anyInt());
        verify(claimRepo, times(1)).transitionAgedPaymentsPastMaxAge(
                BASE_NOW.minus(Duration.ofHours(24)), BASE_NOW);
    }

    @Test
    void runQueryCycleStopsWhenCycleBudgetExpiresOrThreadInterrupted() {
        QueryDrClaimRepository claimRepo = mock(QueryDrClaimRepository.class);
        VnpayGateway gateway = mock(VnpayGateway.class);
        PaymentOutcomeApplier applier = mock(PaymentOutcomeApplier.class);
        when(applier.applyQueryDrOutcome(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ApplyResult("00", "OK", null));

        MutableClock mutableClock = new MutableClock(Clock.fixed(BASE_NOW, ZoneOffset.UTC));
        List<ClaimedPayment> batch = List.of(
                sampleClaimed("DH_BUDGET_0"),
                sampleClaimed("DH_BUDGET_1"),
                sampleClaimed("DH_BUDGET_2"));
        when(claimRepo.claimEligibleForWorker(any(), any(), any(), any(), anyInt()))
                .thenReturn(batch)
                .thenReturn(List.of());

        when(gateway.queryDr(any())).thenAnswer(inv -> {
            mutableClock.advance(Duration.ofSeconds(120));
            return new QueryDrResult(
                    true, "r", "00", "Incomplete", "DH_BUDGET_0", 200_000L,
                    "NCB", "20261121150000", "0", "01", "01", "Info", null, null, Map.of());
        });

        QueryDrService budgetService = createService(
                claimRepo, gateway, applier,
                new QueryDrProperties(
                        Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(24),
                        Duration.ofMinutes(5), Duration.ofMinutes(15), 20, Duration.ofSeconds(90), 5),
                mutableClock);

        assertThat(budgetService.runQueryCycle()).isEqualTo(1);
        verify(gateway, times(1)).queryDr(any());

        // Kiểm tra cờ interrupt sẵn trước khi chạy -> thoát ngay, không claim lô nào.
        Thread.currentThread().interrupt();
        try {
            assertThat(budgetService.runQueryCycle()).isZero();
            verify(claimRepo, times(1)).claimEligibleForWorker(any(), any(), any(), any(), anyInt());
        } finally {
            Thread.interrupted();
        }

        // Kiểm tra interrupt phát sinh trong lúc đang xử lý mục đầu tiên -> dừng ngay trước mục kế tiếp.
        QueryDrClaimRepository interruptClaimRepo = mock(QueryDrClaimRepository.class);
        VnpayGateway interruptGateway = mock(VnpayGateway.class);
        when(interruptClaimRepo.claimEligibleForWorker(any(), any(), any(), any(), anyInt()))
                .thenReturn(batch)
                .thenReturn(List.of());
        when(interruptGateway.queryDr(any())).thenAnswer(inv -> {
            Thread.currentThread().interrupt();
            throw new VnpayTransportException(false, null, "VNPAY querydr interrupted");
        });

        QueryDrService interruptService = createService(
                interruptClaimRepo, interruptGateway, applier,
                new QueryDrProperties(null, null, null, null, null, null, null, null),
                Clock.fixed(BASE_NOW, ZoneOffset.UTC));
        try {
            assertThat(interruptService.runQueryCycle()).isEqualTo(1);
            verify(interruptGateway, times(1)).queryDr(any());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void nonTransportRuntimeExceptionLogsAtErrorAndRecordsQueryDrLocalError() {
        QueryDrClaimRepository claimRepo = mock(QueryDrClaimRepository.class);
        VnpayGateway gateway = mock(VnpayGateway.class);
        PaymentOutcomeApplier applier = mock(PaymentOutcomeApplier.class);
        when(applier.applyQueryDrOutcome(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ApplyResult("00", "OK", null));

        ClaimedPayment claimed = sampleClaimed("DH_LOCAL_ERR_01");
        when(gateway.queryDr(any())).thenThrow(new IllegalStateException("local serialization failure"));

        QueryDrService service = createService(
                claimRepo, gateway, applier,
                new QueryDrProperties(null, null, null, null, null, null, null, null),
                Clock.fixed(BASE_NOW, ZoneOffset.UTC));

        Logger logger = (Logger) LoggerFactory.getLogger(QueryDrService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ApplyResult result = service.executeClaimedQuery(claimed);
            assertThat(result.rspCode()).isEqualTo("00");
        } finally {
            logger.detachAppender(appender);
        }

        verify(applier).applyQueryDrOutcome(
                eq(claimed.paymentId()),
                eq(claimed.orderId()),
                eq(QueryDrOutcomeAction.KEEP_UNCHANGED),
                eq("QUERYDR_LOCAL_ERROR"),
                isNull(),
                any(),
                argThat(raw -> "IllegalStateException".equals(raw.get("_error")) && raw.containsKey("_requestId")),
                eq(false));
        verify(applier, never()).applyQueryDrOutcome(
                any(), any(), any(), eq("QUERYDR_TRANSPORT_ERROR"), any(), any(), any(), anyBoolean());

        assertThat(appender.list).anySatisfy(ev -> {
            assertThat(ev.getLevel()).isEqualTo(Level.ERROR);
            assertThat(ev.getFormattedMessage())
                    .contains(claimed.txnRef())
                    .contains("IllegalStateException");
        });
    }

    @Test
    void executeClaimedQueryOverwritesSpoofedRemoteRequestIdWithLocalOutboundId() {
        QueryDrClaimRepository claimRepo = mock(QueryDrClaimRepository.class);
        VnpayGateway gateway = mock(VnpayGateway.class);
        PaymentOutcomeApplier applier = mock(PaymentOutcomeApplier.class);
        when(applier.applyQueryDrOutcome(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ApplyResult("00", "OK", null));

        ClaimedPayment claimed = sampleClaimed("DH_REQID_SPOOF");
        when(gateway.queryDr(any())).thenReturn(new QueryDrResult(
                true, "resp1", "00", "Incomplete", claimed.txnRef(), 200_000L,
                "NCB", "20261121150000", "0", "01", "01", "Info", null, null,
                Map.of("_requestId", "remote-spoof", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "01")));

        QueryDrService service = createService(
                claimRepo, gateway, applier,
                new QueryDrProperties(null, null, null, null, null, null, null, null),
                Clock.fixed(BASE_NOW, ZoneOffset.UTC));

        service.executeClaimedQuery(claimed);

        ArgumentCaptor<QueryDrRequest> reqCaptor = ArgumentCaptor.forClass(QueryDrRequest.class);
        verify(gateway).queryDr(reqCaptor.capture());
        String localRequestId = reqCaptor.getValue().requestId();
        assertThat(localRequestId).matches("[0-9a-f]{32}");

        verify(applier).applyQueryDrOutcome(
                eq(claimed.paymentId()),
                eq(claimed.orderId()),
                eq(QueryDrOutcomeAction.KEEP_UNCHANGED),
                eq("QUERYDR_INCOMPLETE_00_01"),
                isNull(),
                any(),
                argThat(raw -> localRequestId.equals(raw.get("_requestId"))
                        && !"remote-spoof".equals(raw.get("_requestId"))),
                eq(true));
    }

    @Test
    void queryPaymentNowRejectsActiveTransactionBeforeCooldownOrReadQuery() {
        QueryDrClaimRepository claimRepo = mock(QueryDrClaimRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        PurchaseOrderRepository orderRepo = mock(PurchaseOrderRepository.class);
        VnpayGateway gateway = mock(VnpayGateway.class);
        PaymentOutcomeApplier applier = mock(PaymentOutcomeApplier.class);

        QueryDrService service = new QueryDrService(
                claimRepo,
                paymentRepo,
                orderRepo,
                gateway,
                new VnpayClientProperties(null, null, null),
                new QueryDrProperties(null, null, null, null, null, null, null, null),
                applier,
                mock(FulfillmentService.class),
                mock(PlatformTransactionManager.class),
                Clock.fixed(BASE_NOW, ZoneOffset.UTC),
                null);

        boolean prevTxActive = TransactionSynchronizationManager.isActualTransactionActive();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.queryPaymentNow(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(prevTxActive);
        }

        verify(claimRepo, never()).extendCooldownIfOpen(any(), any(), any());
        verifyNoInteractions(paymentRepo, orderRepo, gateway);
    }

    @Test
    void recordLocalFailureRedactsConfiguredHashSecretFromErrorLog() {
        String sentinelSecret = "SENTINEL_HASH_SECRET_R2_11_XYZ";
        QueryDrClaimRepository claimRepo = mock(QueryDrClaimRepository.class);
        VnpayGateway gateway = mock(VnpayGateway.class);
        PaymentOutcomeApplier applier = mock(PaymentOutcomeApplier.class);
        when(applier.applyQueryDrOutcome(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ApplyResult("00", "OK", null));

        ClaimedPayment claimed = sampleClaimed("DH_LOCAL_SECRET_01");
        when(gateway.queryDr(any())).thenThrow(new IllegalStateException("boom " + sentinelSecret));

        VnpayProperties props = new VnpayProperties(
                "SANDBOX", "TEST-TMN", sentinelSecret,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                List.of(), null, null, null);
        QueryDrService service = new QueryDrService(
                claimRepo,
                mock(PaymentRepository.class),
                mock(PurchaseOrderRepository.class),
                gateway,
                new VnpayClientProperties(null, null, null),
                new QueryDrProperties(null, null, null, null, null, null, null, null),
                applier,
                mock(FulfillmentService.class),
                mock(PlatformTransactionManager.class),
                Clock.fixed(BASE_NOW, ZoneOffset.UTC),
                props);

        Logger logger = (Logger) LoggerFactory.getLogger(QueryDrService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.executeClaimedQuery(claimed);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(ev -> {
            assertThat(ev.getLevel()).isEqualTo(Level.ERROR);
            assertThat(ev.getFormattedMessage())
                    .contains(claimed.txnRef())
                    .contains("IllegalStateException")
                    .contains("[REDACTED]")
                    .doesNotContain(sentinelSecret);
            if (ev.getThrowableProxy() != null) {
                assertThat(ev.getThrowableProxy().getMessage()).doesNotContain(sentinelSecret);
            }
        });
    }

    private static QueryDrService createService(
            QueryDrClaimRepository claimRepo,
            VnpayGateway gateway,
            PaymentOutcomeApplier applier,
            QueryDrProperties queryDrProperties,
            Clock clock) {
        return new QueryDrService(
                claimRepo,
                mock(PaymentRepository.class),
                mock(PurchaseOrderRepository.class),
                gateway,
                new VnpayClientProperties(null, null, null),
                queryDrProperties,
                applier,
                mock(FulfillmentService.class),
                mock(PlatformTransactionManager.class),
                clock);
    }

    private static ClaimedPayment sampleClaimed(String txnRef) {
        return new ClaimedPayment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ORD-" + txnRef,
                txnRef,
                "20261121140000",
                "20261121141500",
                200_000L,
                null,
                PaymentState.CREATED);
    }
}
