package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier;
import vn.unicom.thi360.commerce.payment.domain.ExcessReason;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentEvent;
import vn.unicom.thi360.commerce.payment.domain.PaymentEventSource;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentEventRepository;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubOrder;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubPayment;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.testapi.MutableClock;

/**
 * AC-5..AC-14, AC-25, AC-26: kiểm thử tích hợp ánh xạ {@code querydr} qua {@link QueryDrService},
 * ghi {@code payment_event} nguồn {@code QUERYDR}, đối chiếu quy tắc đếm BS §14 và chặn hạ trạng thái.
 */
@Import(VnpayTestClockConfig.class)
class QueryDrMappingIT extends AbstractIntegrationTest {

    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentEventRepository paymentEventRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private QueryDrClaimRepository claimRepository;
    @Autowired private QueryDrProperties queryDrProperties;
    @Autowired private VnpayClientProperties clientProperties;
    @Autowired private PaymentOutcomeApplier paymentOutcomeApplier;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private QueryDrService queryDrService;
    @Autowired private MutableClock mutableClock;

    @MockitoSpyBean
    private VnpayGateway vnpayGateway;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        reset(vnpayGateway);
        mutableClock.setAt(Instant.now());
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(vnpayGateway);
        mutableClock.reset();
        fixtures.cleanup();
    }

    @Test
    void ac5SuccessTransitionsToFulfilledAndConcurrentQueryDrPlusIpnCountsOnce() throws Exception {
        // 1. Ca 00/00 qua querydr: payment SUCCEEDED, order FULFILLED, 1 entitlement_grant, payment_event QUERYDR.
        LearnerAccount buyer1 = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        StubOrder o1 = fixtures.insertStubOrder(
                buyer1, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        StubPayment p1 = fixtures.insertStubPayment(o1, 1, 200_000L, PaymentState.CREATED);

        doReturn(signedQueryResult("00", "00", p1.txnRef(), 200_000L, "97000501"))
                .when(vnpayGateway).queryDr(any());

        queryDrService.queryPaymentNow(p1.id());

        Payment savedP1 = paymentRepository.findById(p1.id()).orElseThrow();
        assertThat(savedP1.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(savedP1.isDuplicate()).isFalse();
        assertThat(savedP1.getVnpTransactionNo()).isEqualTo("97000501");
        assertThat(orderState(o1.id())).isEqualTo("FULFILLED");
        assertThat(grantCount(o1.id())).isEqualTo(1);
        assertThat(eventsForPayment(p1.id())).hasSize(1)
                .allMatch(e -> e.getSource() == PaymentEventSource.QUERYDR && e.isSignatureValid());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND actor = 'VNPAY_QUERYDR'",
                Integer.class, o1.id())).isEqualTo(1);

        // 2. Đồng thời querydr và IPN 00/00 trên cùng 1 đơn (2 lần thanh toán p2a/p2b) -> 1 khoản đếm, 1 grant, FULFILLED.
        LearnerAccount buyer2 = fixtures.createLearner();
        StubOrder o2 = fixtures.insertStubOrder(
                buyer2, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        StubPayment p2a = fixtures.insertStubPayment(o2, 1, 200_000L, PaymentState.CREATED);
        StubPayment p2b = fixtures.insertStubPayment(o2, 2, 200_000L, PaymentState.CREATED);

        doReturn(signedQueryResult("00", "00", p2a.txnRef(), 200_000L, "97000502"))
                .when(vnpayGateway).queryDr(any());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> qFuture = pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return queryDrService.queryPaymentNow(p2a.id());
            });
            Future<String> ipnFuture = pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return callSignedIpn(p2b.txnRef(), 200_000L, "97000503");
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            qFuture.get(15, TimeUnit.SECONDS);
            assertThat(ipnFuture.get(15, TimeUnit.SECONDS)).isEqualTo("00");
        }

        List<Payment> o2Payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(o2.id());
        assertThat(o2Payments.stream().filter(p -> p.getState() == PaymentState.SUCCEEDED && !p.isDuplicate()).count())
                .isEqualTo(1L);
        assertThat(o2Payments.stream().filter(p ->
                        p.getState() == PaymentState.SUCCEEDED
                                && p.isDuplicate()
                                && p.getExcessReason() == ExcessReason.AFTER_COUNTED)
                .count()).isEqualTo(1L);
        assertThat(orderState(o2.id())).isEqualTo("FULFILLED");
        assertThat(grantCount(o2.id())).isEqualTo(1);
    }

    @Test
    void ac6AndAc7AndAc8AndAc9AndAc13AndAc14MappingMatrix() {
        Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
        mutableClock.setAt(baseNow);
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        // AC-6: 00/01 giữ CREATED, được claim lại sau cooldown 5 phút.
        StubOrder o6 = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.plusSeconds(900), null);
        StubPayment p6 = insertPaymentCreatedAt(o6, 1, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(20)));
        doReturn(signedQueryResult("00", "01", p6.txnRef(), 200_000L, "0")).when(vnpayGateway).queryDr(any());

        assertThat(queryDrService.runQueryCycle()).isGreaterThanOrEqualTo(1);
        assertThat(paymentRepository.findById(p6.id()).orElseThrow().getState()).isEqualTo(PaymentState.CREATED);
        assertEventRecorded(p6.id(), true, "QUERYDR_INCOMPLETE_00_01");

        // Trước cooldown 4m59s không được claim; đúng 5m00s được claim lại.
        mutableClock.setAt(baseNow.plus(Duration.ofMinutes(5)).minusSeconds(1));
        assertThat(claimRepository.claimEligibleForWorker(
                mutableClock.instant(), Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofMinutes(5), 20))
                .extracting(QueryDrClaimRepository.ClaimedPayment::paymentId)
                .doesNotContain(p6.id());

        mutableClock.setAt(baseNow.plus(Duration.ofMinutes(5)));
        assertThat(claimRepository.claimEligibleForWorker(
                mutableClock.instant(), Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofMinutes(5), 20))
                .extracting(QueryDrClaimRepository.ClaimedPayment::paymentId)
                .contains(p6.id());

        // AC-7: 00/02 -> FAILED, không cấp quyền.
        StubPayment p7 = fixtures.insertStubPayment(o6, 2, 200_000L, PaymentState.CREATED);
        doReturn(signedQueryResult("00", "02", p7.txnRef(), 200_000L, "0")).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(p7.id());
        assertThat(paymentRepository.findById(p7.id()).orElseThrow().getState()).isEqualTo(PaymentState.FAILED);
        assertThat(grantCount(o6.id())).isZero();
        assertEventRecorded(p7.id(), true, "QUERYDR_FAILED_00_02");

        // AC-8: 00/04 và 00/07 -> UNKNOWN, order.needs_review=true, review_reason có giá trị, 0 grants.
        // R1-16, R1-17, R1-25: gọi lặp 00/04 hai lần không tạo thêm order_history hay audit_event PAYMENT_NEEDS_REVIEW.
        for (String status : List.of("04", "07")) {
            LearnerAccount buyer8 = fixtures.createLearner();
            StubOrder o8 = fixtures.insertStubOrder(
                    buyer8, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                    200_000L, baseNow.plusSeconds(900), null);
            StubPayment p8 = fixtures.insertStubPayment(o8, 1, 200_000L, PaymentState.CREATED);
            doReturn(signedQueryResult("00", status, p8.txnRef(), 200_000L, "970008" + status))
                    .when(vnpayGateway).queryDr(any());
            queryDrService.queryPaymentNow(p8.id());
            if ("04".equals(status)) {
                queryDrService.queryPaymentNow(p8.id());
            }

            assertThat(paymentRepository.findById(p8.id()).orElseThrow().getState()).isEqualTo(PaymentState.UNKNOWN);
            Map<String, Object> orderRow = jdbc.queryForMap(
                    "SELECT state, needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", o8.id());
            assertThat(orderRow.get("needs_review")).isEqualTo(true);
            assertThat((String) orderRow.get("review_reason")).isEqualTo("VNPAY_QUERYDR_STATUS_" + status);
            assertThat(grantCount(o8.id())).isZero();
            assertEventRecorded(p8.id(), true, "QUERYDR_REVIEW_00_" + status);

            int expectedPolls = "04".equals(status) ? 2 : 1;
            assertThat(eventsForPayment(p8.id())).hasSize(expectedPolls);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND actor = 'VNPAY_QUERYDR' AND reason = ?",
                    Integer.class, o8.id(), "VNPAY_QUERYDR_STATUS_" + status + ":" + p8.txnRef())).isEqualTo(1);
            while (outboxRelay.relayBatch() > 0) {
                // Đẩy outbox sang audit.audit_event.
            }
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_NEEDS_REVIEW' AND target_id = ?",
                    Integer.class, o8.id().toString())).isEqualTo(1);
        }

        // AC-9: 00/05, 00/06, 00/09 giữ nguyên trạng thái CREATED / UNKNOWN.
        LearnerAccount buyer9 = fixtures.createLearner();
        StubOrder o9 = fixtures.insertStubOrder(
                buyer9, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.plusSeconds(900), null);
        StubPayment p9Created = fixtures.insertStubPayment(o9, 1, 200_000L, PaymentState.CREATED);
        StubPayment p9Unknown = fixtures.insertStubPayment(o9, 2, 200_000L, PaymentState.UNKNOWN);
        for (String refundStatus : List.of("05", "06", "09")) {
            doReturn(signedQueryResult("00", refundStatus, p9Created.txnRef(), 200_000L, "970009" + refundStatus))
                    .when(vnpayGateway).queryDr(any());
            queryDrService.queryPaymentNow(p9Created.id());
            assertThat(paymentRepository.findById(p9Created.id()).orElseThrow().getState())
                    .isEqualTo(PaymentState.CREATED);
            assertEventRecorded(p9Created.id(), true, "QUERYDR_REFUND_STATUS_IGNORED_00_" + refundStatus);

            doReturn(signedQueryResult("00", refundStatus, p9Unknown.txnRef(), 200_000L, "970019" + refundStatus))
                    .when(vnpayGateway).queryDr(any());
            queryDrService.queryPaymentNow(p9Unknown.id());
            assertThat(paymentRepository.findById(p9Unknown.id()).orElseThrow().getState())
                    .isEqualTo(PaymentState.UNKNOWN);
            assertEventRecorded(p9Unknown.id(), true, "QUERYDR_REFUND_STATUS_IGNORED_00_" + refundStatus);
        }

        // AC-13: mã responseCode lạ ("88") và 00 với transactionStatus lạ ("98") -> UNKNOWN, KHÔNG thành FAILED.
        StubPayment p13a = fixtures.insertStubPayment(o9, 3, 200_000L, PaymentState.CREATED);
        StubPayment p13b = fixtures.insertStubPayment(o9, 4, 200_000L, PaymentState.CREATED);
        doReturn(signedQueryResult("88", null, p13a.txnRef(), 200_000L, null)).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(p13a.id());
        assertThat(paymentRepository.findById(p13a.id()).orElseThrow().getState()).isEqualTo(PaymentState.UNKNOWN);
        assertEventRecorded(p13a.id(), true, "QUERYDR_UNKNOWN_RESPONSE_CODE");

        doReturn(signedQueryResult("00", "98", p13b.txnRef(), 200_000L, null)).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(p13b.id());
        assertThat(paymentRepository.findById(p13b.id()).orElseThrow().getState()).isEqualTo(PaymentState.UNKNOWN);
        assertEventRecorded(p13b.id(), true, "QUERYDR_UNKNOWN_TXN_STATUS");
    }

    @Test
    void ac10Response91GraceBoundaryWithMutableClock() {
        Instant expireInstant = Instant.parse("2026-11-21T03:15:00Z"); // 20261121101500 GMT+7
        String vnpExpireDate = VNP_TIME_FMT.format(expireInstant.atZone(GMT_PLUS_7));

        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, expireInstant, null);
        StubPayment pBefore = insertWithExpire(order, 1, vnpExpireDate, PaymentState.CREATED);
        StubPayment pExact = insertWithExpire(order, 2, vnpExpireDate, PaymentState.CREATED);
        StubPayment pAfter = insertWithExpire(order, 3, vnpExpireDate, PaymentState.CREATED);

        doReturn(signedQueryResult("91", null, pBefore.txnRef(), null, null)).when(vnpayGateway).queryDr(any());

        // vnp_ExpireDate + 14m59s -> giữ nguyên CREATED
        mutableClock.setAt(expireInstant.plus(Duration.ofMinutes(15)).minusSeconds(1));
        queryDrService.queryPaymentNow(pBefore.id());
        assertThat(paymentRepository.findById(pBefore.id()).orElseThrow().getState()).isEqualTo(PaymentState.CREATED);
        assertEventRecorded(pBefore.id(), true, "QUERYDR_NOT_FOUND_BEFORE_GRACE_91");

        // Đúng mốc vnp_ExpireDate + 15m00s -> FAILED
        mutableClock.setAt(expireInstant.plus(Duration.ofMinutes(15)));
        doReturn(signedQueryResult("91", null, pExact.txnRef(), null, null)).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(pExact.id());
        assertThat(paymentRepository.findById(pExact.id()).orElseThrow().getState()).isEqualTo(PaymentState.FAILED);
        assertEventRecorded(pExact.id(), true, "QUERYDR_NOT_FOUND_FAILED_91");

        // Sau mốc vnp_ExpireDate + 15m01s -> FAILED
        mutableClock.setAt(expireInstant.plus(Duration.ofMinutes(15)).plusSeconds(1));
        doReturn(signedQueryResult("91", null, pAfter.txnRef(), null, null)).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(pAfter.id());
        assertThat(paymentRepository.findById(pAfter.id()).orElseThrow().getState()).isEqualTo(PaymentState.FAILED);
        assertEventRecorded(pAfter.id(), true, "QUERYDR_NOT_FOUND_FAILED_91");
    }

    @Test
    void ac11AndAc12RetryablesTransportErrorsBadChecksumAndConfigErrorLogs() {
        Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
        mutableClock.setAt(baseNow);
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.plusSeconds(900), null);

        // AC-11: 94, 99 -> giữ nguyên, tra lại sau cooldown.
        StubPayment p94 = insertPaymentCreatedAt(order, 1, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(20)));
        doReturn(signedQueryResult("94", null, p94.txnRef(), null, null)).when(vnpayGateway).queryDr(any());
        queryDrService.runQueryCycle();
        assertThat(paymentRepository.findById(p94.id()).orElseThrow().getState()).isEqualTo(PaymentState.CREATED);
        assertEventRecorded(p94.id(), true, "QUERYDR_RETRYABLE_94");

        // Qua 5m cooldown: trả 99 -> vẫn CREATED.
        mutableClock.setAt(baseNow.plus(Duration.ofMinutes(5)));
        doReturn(signedQueryResult("99", null, p94.txnRef(), null, null)).when(vnpayGateway).queryDr(any());
        queryDrService.runQueryCycle();
        assertThat(paymentRepository.findById(p94.id()).orElseThrow().getState()).isEqualTo(PaymentState.CREATED);
        assertEventRecorded(p94.id(), true, "QUERYDR_RETRYABLE_99");

        // Qua 5m cooldown: lỗi mạng -> signature_valid=false, vẫn CREATED.
        mutableClock.setAt(baseNow.plus(Duration.ofMinutes(10)));
        doThrow(new VnpayTransportException(false, null, "VNPAY querydr network error"))
                .when(vnpayGateway).queryDr(any());
        queryDrService.runQueryCycle();
        assertThat(paymentRepository.findById(p94.id()).orElseThrow().getState()).isEqualTo(PaymentState.CREATED);
        assertEventRecorded(p94.id(), false, "QUERYDR_TRANSPORT_ERROR");

        // Qua 5m cooldown: timeout -> signature_valid=false, vẫn CREATED.
        mutableClock.setAt(baseNow.plus(Duration.ofMinutes(15)));
        doThrow(new VnpayTransportException(true, null, "VNPAY querydr timed out"))
                .when(vnpayGateway).queryDr(any());
        queryDrService.runQueryCycle();
        assertThat(paymentRepository.findById(p94.id()).orElseThrow().getState()).isEqualTo(PaymentState.CREATED);
        assertEventRecorded(p94.id(), false, "QUERYDR_TRANSPORT_TIMEOUT");

        // Qua 5m cooldown: checksum sai -> signature_valid=false, vẫn CREATED, vẫn được claim lại sau 5m nữa.
        mutableClock.setAt(baseNow.plus(Duration.ofMinutes(20)));
        doReturn(new QueryDrResult(
                false, null, null, null, null, null, null, null, null, null, null, null, null, null,
                Map.of("vnp_ResponseCode", "00", "vnp_TransactionStatus", "00")))
                .when(vnpayGateway).queryDr(any());
        queryDrService.runQueryCycle();
        assertThat(paymentRepository.findById(p94.id()).orElseThrow().getState()).isEqualTo(PaymentState.CREATED);
        assertEventRecorded(p94.id(), false, "QUERYDR_INVALID_SIGNATURE");

        mutableClock.setAt(baseNow.plus(Duration.ofMinutes(25)));
        assertThat(claimRepository.claimEligibleForWorker(
                mutableClock.instant(), Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofMinutes(5), 20))
                .extracting(QueryDrClaimRepository.ClaimedPayment::paymentId)
                .contains(p94.id());

        // AC-12: 02, 03, 97 -> giữ nguyên và ghi log mức ERROR.
        Logger logger = (Logger) LoggerFactory.getLogger(QueryDrService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            int seq = 2;
            for (String cfgCode : List.of("02", "03", "97")) {
                StubPayment pCfg = fixtures.insertStubPayment(order, seq++, 200_000L, PaymentState.UNKNOWN);
                doReturn(signedQueryResult(cfgCode, null, pCfg.txnRef(), null, null))
                        .when(vnpayGateway).queryDr(any());
                queryDrService.queryPaymentNow(pCfg.id());

                assertThat(paymentRepository.findById(pCfg.id()).orElseThrow().getState())
                        .isEqualTo(PaymentState.UNKNOWN);
                assertEventRecorded(pCfg.id(), true, "QUERYDR_CONFIG_ERROR_" + cfgCode);
                assertThat(appender.list).anySatisfy(ev -> {
                    assertThat(ev.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(ev.getFormattedMessage())
                            .contains("responseCode=" + cfgCode)
                            .contains(pCfg.txnRef());
                });
            }
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void queryDrFollowsCountingRules() throws Exception {
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        // Ca 1 (AC-25 / BS §14 Ca C): Đơn CHƯA có khoản được tính + lần FAILED nhận querydr 00/00
        // -> giữ FAILED, late_success_review = PENDING, không đặt needs_review trên đơn, 0 grants.
        StubOrder o1 = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        StubPayment p1Failed = fixtures.insertStubPayment(o1, 1, 200_000L, PaymentState.FAILED);

        doReturn(signedQueryResult("00", "00", p1Failed.txnRef(), 200_000L, "97002501"))
                .when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(p1Failed.id());

        Payment p1After = paymentRepository.findById(p1Failed.id()).orElseThrow();
        assertThat(p1After.getState()).isEqualTo(PaymentState.FAILED);
        assertThat(p1After.isDuplicate()).isFalse();
        assertThat(p1After.getLateSuccessReview()).isEqualTo(LateSuccessReview.PENDING);
        assertThat(p1After.getVnpTransactionNo()).isEqualTo("97002501");
        assertThat(orderState(o1.id())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject(
                "SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1.id())).isFalse();
        assertThat(grantCount(o1.id())).isZero();
        assertEventRecorded(p1Failed.id(), true, "LATE_SUCCESS_PENDING_00");

        // Ca 2 (AC-25 / BS §14 Ca B): Đơn ĐÃ có khoản được tính + lần FAILED nhận querydr 00/00
        // -> SUCCEEDED, is_duplicate = true, excess_reason = AFTER_COUNTED, không cấp thêm quyền.
        LearnerAccount buyer2 = fixtures.createLearner();
        StubOrder o2 = fixtures.insertStubOrder(
                buyer2, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        StubPayment p2Failed = fixtures.insertStubPayment(o2, 1, 200_000L, PaymentState.FAILED);
        StubPayment p2Counted = fixtures.insertStubPayment(o2, 2, 200_000L, PaymentState.CREATED);

        assertThat(callSignedIpn(p2Counted.txnRef(), 200_000L, "97002502")).isEqualTo("00");
        assertThat(orderState(o2.id())).isEqualTo("FULFILLED");
        assertThat(grantCount(o2.id())).isEqualTo(1);

        doReturn(signedQueryResult("00", "00", p2Failed.txnRef(), 200_000L, "97002503"))
                .when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(p2Failed.id());

        Payment p2FailedAfter = paymentRepository.findById(p2Failed.id()).orElseThrow();
        assertThat(p2FailedAfter.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(p2FailedAfter.isDuplicate()).isTrue();
        assertThat(p2FailedAfter.getExcessReason()).isEqualTo(ExcessReason.AFTER_COUNTED);
        assertThat(p2FailedAfter.getLateSuccessReview()).isNull();
        assertThat(grantCount(o2.id())).isEqualTo(1);
        assertEventRecorded(p2Failed.id(), true, "EXCESS_AFTER_COUNTED_00");
    }

    @Test
    void lateNegativeQueryKeepsCountedPayment() throws Exception {
        Instant expirePast = mutableClock.instant().minus(Duration.ofMinutes(30));
        String vnpExpirePast = VNP_TIME_FMT.format(expirePast.atZone(GMT_PLUS_7));

        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, expirePast, null);
        StubPayment counted = insertWithExpire(order, 1, vnpExpirePast, PaymentState.CREATED);

        List<QueryDrResult> negativeResults = List.of(
                signedQueryResult("00", "02", counted.txnRef(), 200_000L, "0"),
                signedQueryResult("00", "04", counted.txnRef(), 200_000L, "97002604"),
                signedQueryResult("91", null, counted.txnRef(), null, null),
                signedQueryResult("88", null, counted.txnRef(), null, null));

        // 1. querydr đang bay (in-flight) thì IPN 00/00 về trước đánh dấu SUCCEEDED & FULFILLED;
        //    querydr về sau với 00/02 -> giữ nguyên SUCCEEDED, FULFILLED, needs_review=false, 1 grant, ghi QUERYDR event.
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch proceedQuery = new CountDownLatch(1);
        doAnswer(inv -> {
            inFlight.countDown();
            assertThat(proceedQuery.await(10, TimeUnit.SECONDS)).isTrue();
            return negativeResults.getFirst();
        }).when(vnpayGateway).queryDr(any());

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> qFuture = pool.submit(() -> queryDrService.queryPaymentNow(counted.id()));
            assertThat(inFlight.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(callSignedIpn(counted.txnRef(), 200_000L, "97002600")).isEqualTo("00");
            assertThat(orderState(order.id())).isEqualTo("FULFILLED");

            proceedQuery.countDown();
            qFuture.get(15, TimeUnit.SECONDS);
        }

        // 2. Các lần querydr âm tính / review / 91 sau ân hạn / mã lạ tiếp theo trên khoản đã SUCCEEDED.
        for (int i = 1; i < negativeResults.size(); i++) {
            doReturn(negativeResults.get(i)).when(vnpayGateway).queryDr(any());
            queryDrService.queryPaymentNow(counted.id());
        }

        Payment afterAll = paymentRepository.findById(counted.id()).orElseThrow();
        assertThat(afterAll.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(afterAll.isDuplicate()).isFalse();
        assertThat(afterAll.getVnpTransactionNo()).isEqualTo("97002600");
        assertThat(orderState(order.id())).isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, order.id())).isFalse();
        assertThat(grantCount(order.id())).isEqualTo(1);

        List<PaymentEvent> queryEvents = eventsForPayment(counted.id()).stream()
                .filter(e -> e.getSource() == PaymentEventSource.QUERYDR)
                .toList();
        assertThat(queryEvents).hasSize(4)
                .allMatch(e -> e.getOutcome().startsWith("QUERYDR_LATE_IGNORED:"));

        // 3. Lần đã FAILED không bị ghi đè bởi kết quả âm tính / UNKNOWN muộn (AC-26).
        StubOrder orderFailed = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, expirePast, null);
        StubPayment failedPay = insertWithExpire(orderFailed, 1, vnpExpirePast, PaymentState.FAILED);
        doReturn(signedQueryResult("88", null, failedPay.txnRef(), null, null)).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(failedPay.id());
        assertThat(paymentRepository.findById(failedPay.id()).orElseThrow().getState())
                .isEqualTo(PaymentState.FAILED);
        assertEventRecorded(failedPay.id(), true, "QUERYDR_LATE_IGNORED:QUERYDR_UNKNOWN_RESPONSE_CODE");
    }

    @Test
    void ac4CapturesQueryDrRequestFieldsAndExtendsCooldownOnDemand() {
        Instant baseNow = Instant.parse("2026-11-21T08:15:30Z");
        mutableClock.setAt(baseNow);

        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.plusSeconds(900), null);
        StubPayment payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);
        // Cố tình đặt vnp_create_date khác vnp_expire_date để chứng minh AC-4 đọc vnp_create_date.
        jdbc.update(
                "UPDATE commerce.payment SET vnp_create_date = '20261121144500', vnp_expire_date = '20261121150000' WHERE id = ?",
                payment.id());

        doReturn(signedQueryResult("00", "01", payment.txnRef(), 200_000L, "0")).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(payment.id());

        OffsetDateTime nextQueryAt = jdbc.queryForObject(
                "SELECT next_query_at FROM commerce.payment WHERE id = ?", OffsetDateTime.class, payment.id());
        assertThat(nextQueryAt).isNotNull();
        assertThat(nextQueryAt.toInstant()).isEqualTo(baseNow.plus(queryDrProperties.cooldown()));

        jdbc.update("UPDATE commerce.payment SET vnp_transaction_no = '97004444' WHERE id = ?", payment.id());
        Instant secondCallAt = baseNow.plusSeconds(15);
        mutableClock.setAt(secondCallAt);
        queryDrService.queryPaymentNow(payment.id());

        ArgumentCaptor<QueryDrRequest> captor = ArgumentCaptor.forClass(QueryDrRequest.class);
        verify(vnpayGateway, times(2)).queryDr(captor.capture());
        List<QueryDrRequest> captured = captor.getAllValues();

        QueryDrRequest first = captured.get(0);
        QueryDrRequest second = captured.get(1);
        assertThat(first.transactionDate()).isEqualTo("20261121144500");
        assertThat(first.requestId()).matches("[0-9a-f]{32}");
        assertThat(second.requestId()).matches("[0-9a-f]{32}").isNotEqualTo(first.requestId());
        assertThat(first.createDate()).isEqualTo(VNP_TIME_FMT.format(baseNow.atZone(GMT_PLUS_7)));
        assertThat(second.createDate()).isEqualTo(VNP_TIME_FMT.format(secondCallAt.atZone(GMT_PLUS_7)));
        assertThat(first.ipAddr()).isEqualTo(clientProperties.serverIp());
        assertThat(first.transactionNo()).isNull();
        assertThat(second.transactionNo()).isEqualTo("97004444");

        List<String> rawEvents = jdbc.queryForList(
                "SELECT raw::text FROM commerce.payment_event WHERE payment_id = ?",
                String.class, payment.id());
        assertThat(rawEvents).hasSize(2)
                .anyMatch(raw -> raw.contains(first.requestId()) && raw.contains("_requestId"))
                .anyMatch(raw -> raw.contains(second.requestId()) && raw.contains("_requestId"));
    }

    @Test
    void txnRefMismatchDoesNotAttachForeignVnpTransactionNo() throws Exception {
        LearnerAccount buyerA = fixtures.createLearner();
        LearnerAccount buyerB = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);

        StubOrder orderA = fixtures.insertStubOrder(
                buyerA, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        StubPayment paymentA = fixtures.insertStubPayment(orderA, 1, 200_000L, PaymentState.CREATED);
        assertThat(callSignedIpn(paymentA.txnRef(), 200_000L, "97001111")).isEqualTo("00");
        assertThat(paymentRepository.findById(paymentA.id()).orElseThrow().getVnpTransactionNo())
                .isEqualTo("97001111");

        StubOrder orderB = fixtures.insertStubOrder(
                buyerB, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        StubPayment paymentB = fixtures.insertStubPayment(orderB, 1, 200_000L, PaymentState.CREATED);

        // Phản hồi có chữ ký hợp lệ nhưng mang txnRef và vnp_TransactionNo của A.
        doReturn(signedQueryResult("00", "00", paymentA.txnRef(), 200_000L, "97001111"))
                .when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(paymentB.id());

        Payment savedB = paymentRepository.findById(paymentB.id()).orElseThrow();
        assertThat(savedB.getState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(savedB.getVnpTransactionNo()).isNull();
        Map<String, Object> orderBRow = jdbc.queryForMap(
                "SELECT needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", orderB.id());
        assertThat(orderBRow.get("needs_review")).isEqualTo(true);
        assertThat(orderBRow.get("review_reason")).isEqualTo("VNPAY_QUERYDR_TXN_REF_MISMATCH");
        assertEventRecorded(paymentB.id(), true, "QUERYDR_TXN_REF_MISMATCH");
    }

    @Test
    void nonTransportRuntimeExceptionLogsErrorAndRecordsQueryDrLocalError() {
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        StubPayment payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        doThrow(new IllegalStateException("local serializer failure")).when(vnpayGateway).queryDr(any());

        Logger logger = (Logger) LoggerFactory.getLogger(QueryDrService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            queryDrService.queryPaymentNow(payment.id());
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(paymentRepository.findById(payment.id()).orElseThrow().getState())
                .isEqualTo(PaymentState.CREATED);
        assertEventRecorded(payment.id(), false, "QUERYDR_LOCAL_ERROR");
        assertThat(appender.list).anySatisfy(ev -> {
            assertThat(ev.getLevel()).isEqualTo(Level.ERROR);
            assertThat(ev.getFormattedMessage())
                    .contains(payment.txnRef())
                    .contains("IllegalStateException");
        });
    }

    @Test
    void realGatewayEndToEndVerifiesMalformed0000FailClosedAndPostgresSecretRedaction() throws Exception {
        String sentinelSecret = "dev-only-sentinel-hash-9988776655";
        String sentinelTmn = "DEV-SENTINEL-TMN";
        VnpayRealSigner realSigner = new VnpayRealSigner(new VnpaySigningProperties(null, null, null, null));

        AtomicReference<java.util.function.Function<Map<String, Object>, HttpMock>> responder = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/merchant_webapi/api/transaction", exchange -> {
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            Map<String, Object> reqBody = mapper.readValue(
                    new String(reqBytes, StandardCharsets.UTF_8),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            HttpMock mock = responder.get().apply(reqBody);
            byte[] bytes = mock.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", mock.contentType());
            exchange.sendResponseHeaders(mock.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        String apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/merchant_webapi/api/transaction";
        VnpayProperties realProps = new VnpayProperties(
                "SANDBOX", sentinelTmn, sentinelSecret,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", apiUrl,
                List.of(), null, null, null);
        RealVnpayGateway realGateway = new RealVnpayGateway(
                realProps, clientProperties, realSigner, mapper, mutableClock);
        QueryDrService e2eService = new QueryDrService(
                claimRepository, paymentRepository, purchaseOrderRepository, realGateway,
                clientProperties, queryDrProperties, paymentOutcomeApplier, fulfillmentService,
                transactionManager, mutableClock, realProps);

        try {
            StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 150_000L, 1);

            // 1. R1-1..R1-3: phản hồi 00/00 có chữ ký đúng nhưng vnp_Amount là "15000050", "0", "" -> UNKNOWN + needs_review + 0 grants.
            int amtIdx = 1;
            for (String badAmount : List.of("15000050", "0", "")) {
                String txNo = "9700990" + (amtIdx++);
                LearnerAccount buyer = fixtures.createLearner();
                StubOrder order = fixtures.insertStubOrder(
                        buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                        150_000L, mutableClock.instant().plusSeconds(900), null);
                StubPayment payment = fixtures.insertStubPayment(order, 1, 150_000L, PaymentState.CREATED);

                responder.set(req -> {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("vnp_ResponseId", "resp_bad_amt");
                    resp.put("vnp_Command", "querydr");
                    resp.put("vnp_ResponseCode", "00");
                    resp.put("vnp_Message", "Success");
                    resp.put("vnp_TmnCode", sentinelTmn);
                    resp.put("vnp_TxnRef", payment.txnRef());
                    resp.put("vnp_Amount", badAmount);
                    resp.put("vnp_BankCode", "NCB");
                    resp.put("vnp_PayDate", "20261121100200");
                    resp.put("vnp_TransactionNo", txNo);
                    resp.put("vnp_TransactionType", "01");
                    resp.put("vnp_TransactionStatus", "00");
                    resp.put("vnp_OrderInfo", "Info");
                    resp.put("vnp_SecureHash", VnpaySigner.hmacSha512Hex(
                            realSigner.buildQueryDrResponseSignData(resp), sentinelSecret));
                    try {
                        return new HttpMock(200, "application/json", mapper.writeValueAsString(resp));
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });

                e2eService.queryPaymentNow(payment.id());
                assertThat(paymentRepository.findById(payment.id()).orElseThrow().getState())
                        .isEqualTo(PaymentState.UNKNOWN);
                Map<String, Object> orderRow = jdbc.queryForMap(
                        "SELECT needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", order.id());
                assertThat(orderRow.get("needs_review")).isEqualTo(true);
                assertThat(orderRow.get("review_reason")).isEqualTo("VNPAY_QUERYDR_AMOUNT_MISMATCH");
                assertThat(grantCount(order.id())).isZero();
            }

            // 2. AC-24 + R1-23 + R1-32 + R1-35: máy chủ thù địch lặp lại bí mật trong HTTP 200 JSON (kèm null field) và HTTP 500 HTML.
            LearnerAccount hostileBuyer = fixtures.createLearner();
            StubOrder hostileOrder = fixtures.insertStubOrder(
                    hostileBuyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                    150_000L, mutableClock.instant().plusSeconds(900), null);
            StubPayment hostilePay = fixtures.insertStubPayment(hostileOrder, 1, 150_000L, PaymentState.CREATED);

            responder.set(req -> {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("vnp_ResponseId", "hostile200");
                resp.put("vnp_Command", "querydr");
                resp.put("vnp_ResponseCode", "99");
                resp.put("vnp_Message", "Leak " + sentinelSecret);
                resp.put("vnp_TmnCode", sentinelTmn);
                resp.put("vnp_TxnRef", hostilePay.txnRef());
                resp.put("vnp_PromotionCode", null);
                resp.put("extraLeak", Map.of("nested", sentinelSecret));
                resp.put("vnp_SecureHash", "badhash");
                try {
                    return new HttpMock(200, "application/json", mapper.writeValueAsString(resp));
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            e2eService.queryPaymentNow(hostilePay.id());

            responder.set(req -> new HttpMock(
                    500, "text/html", "<html>Internal Error: " + sentinelSecret + "</html>"));
            e2eService.queryPaymentNow(hostilePay.id());

            List<String> persistedRaws = jdbc.queryForList(
                    "SELECT raw::text FROM commerce.payment_event WHERE payment_id = ? ORDER BY received_at ASC",
                    String.class, hostilePay.id());
            assertThat(persistedRaws).hasSize(2);
            for (String rawText : persistedRaws) {
                assertThat(rawText).isNotBlank()
                        .contains("_requestId")
                        .doesNotContain(sentinelSecret);
            }
            assertThat(persistedRaws.get(0))
                    .contains("[REDACTED]")
                    .doesNotContain("vnp_PromotionCode");
            assertThat(persistedRaws.get(1))
                    .contains("VNPAY querydr HTTP 500");
        } finally {
            realGateway.destroy();
            server.stop(0);
        }
    }

    private record HttpMock(int status, String contentType, String body) {
    }

    private StubPayment insertPaymentCreatedAt(StubOrder order, int seq, PaymentState state, Instant createdAt) {
        StubPayment p = fixtures.insertStubPayment(order, seq, 200_000L, state);
        jdbc.update(
                "UPDATE commerce.payment SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(createdAt), Timestamp.from(createdAt), p.id());
        return p;
    }

    private StubPayment insertWithExpire(StubOrder order, int seq, String vnpExpireDate, PaymentState state) {
        StubPayment p = fixtures.insertStubPayment(order, seq, 200_000L, state);
        jdbc.update("UPDATE commerce.payment SET vnp_expire_date = ? WHERE id = ?", vnpExpireDate, p.id());
        return p;
    }

    private String callSignedIpn(String txnRef, long amountVnd, String txnNo) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        String body = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(body);
        return root.path("RspCode").asText();
    }

    private static QueryDrResult signedQueryResult(
            String responseCode, String txnStatus, String txnRef, Long amountVnd, String txnNo) {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("vnp_ResponseCode", responseCode);
        if (txnStatus != null) {
            raw.put("vnp_TransactionStatus", txnStatus);
        }
        if (txnRef != null) {
            raw.put("vnp_TxnRef", txnRef);
        }
        if (amountVnd != null) {
            raw.put("vnp_Amount", Long.toString(amountVnd * 100L));
        }
        if (txnNo != null) {
            raw.put("vnp_TransactionNo", txnNo);
        }
        return new QueryDrResult(
                true,
                "resp-" + UUID.randomUUID().toString().substring(0, 8),
                responseCode,
                "Message " + responseCode,
                txnRef,
                amountVnd,
                "NCB",
                "20261121100200",
                txnNo,
                "01",
                txnStatus,
                "Kiem tra giao dich",
                null,
                null,
                raw);
    }

    private String orderState(UUID orderId) {
        return jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId);
    }

    private int grantCount(UUID orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private List<PaymentEvent> eventsForPayment(UUID paymentId) {
        return paymentEventRepository.findByPaymentIdOrderByReceivedAtDesc(paymentId);
    }

    private void assertEventRecorded(UUID paymentId, boolean expectedSigValid, String expectedOutcome) {
        List<PaymentEvent> events = eventsForPayment(paymentId);
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getSource()).isEqualTo(PaymentEventSource.QUERYDR);
            assertThat(e.isSignatureValid()).isEqualTo(expectedSigValid);
            assertThat(e.getOutcome()).isEqualTo(expectedOutcome);
        });
    }
}
