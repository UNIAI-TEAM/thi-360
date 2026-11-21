package vn.unicom.thi360.commerce.order.expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.vnpay.VnpayTestClockConfig;
import vn.unicom.thi360.commerce.payment.vnpay.VnpayTransportException;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.testapi.MutableClock;

/**
 * AC-17, AC-18, AC-19: worker hết hạn đơn kiểm tra {@code querydr} trước khi chuyển {@code EXPIRED},
 * trả voucher, giữ {@code PENDING_PAYMENT} khi chưa rõ, và hoàn tất đơn nếu tra ra {@code 00/00}.
 */
@Import(VnpayTestClockConfig.class)
class OrderExpiryWorkerIT extends AbstractIntegrationTest {

    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private OrderExpiryService orderExpiryService;
    @Autowired private OrderExpiryProperties orderExpiryProperties;
    @Autowired private OutboxRelay outboxRelay;
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
    void ac17ExpiresUnpaidOrdersReleasesVoucherAndLeavesNonExpiredUntouched() throws Exception {
        Instant t0 = mutableClock.instant();
        LearnerAccount b1 = fixtures.createLearner();
        LearnerAccount b2 = fixtures.createLearner();
        LearnerAccount b3 = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        Promotion voucher = fixtures.createVoucher(
                "EXP17", DiscountType.AMOUNT, 50_000L, null,
                t0.minus(1, ChronoUnit.HOURS), t0.plus(1, ChronoUnit.DAYS),
                5, 2, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        OrderHandle expiredBy02 = createOrder(b1, product, voucher.getCode(), 150_000L);
        OrderHandle expiredBy91 = createOrder(b2, product, voucher.getCode(), 150_000L);

        // Đơn 3 đặt muộn hơn 10 phút -> chưa quá hạn khi tua +20 phút từ t0.
        mutableClock.setAt(t0.plus(Duration.ofMinutes(10)));
        OrderHandle stillPending = createOrder(b3, product, voucher.getCode(), 150_000L);

        assertThat(usedCount(voucher.getId())).isEqualTo(3);

        // Tua tới t0 + 30m: expiredBy02 và expiredBy91 đã quá expires_at (15m) và vnp_ExpireDate + 15m (30m);
        // stillPending có expires_at = t0 + 25m? Khoan: stillPending tạo lúc t0+10m nên expires_at = t0+25m;
        // đặt expires_at của stillPending > now để kiểm đơn chưa quá hạn không bị đụng!
        jdbc.update("UPDATE commerce.purchase_order SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(t0.plus(Duration.ofMinutes(40))), stillPending.orderId());
        mutableClock.setAt(t0.plus(Duration.ofMinutes(30)));

        doAnswer(inv -> {
            QueryDrRequest req = inv.getArgument(0);
            if (req.txnRef().equals(expiredBy02.txnRef())) {
                return queryResult(req.txnRef(), "00", "02");
            }
            if (req.txnRef().equals(expiredBy91.txnRef())) {
                return queryResult(req.txnRef(), "91", null);
            }
            throw new AssertionError("Non-expired order should not be queried: " + req.txnRef());
        }).when(vnpayGateway).queryDr(any());

        int expiredCount = orderExpiryService.expirePendingOrders();
        assertThat(expiredCount).isGreaterThanOrEqualTo(2);

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy hết outbox sang audit.audit_event.
        }

        for (OrderHandle h : new OrderHandle[] {expiredBy02, expiredBy91}) {
            assertThat(orderState(h.orderId())).isEqualTo("EXPIRED");
            assertThat(redemptionState(h.orderId(), voucher.getId())).isEqualTo("RELEASED");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND from_state = 'PENDING_PAYMENT' AND to_state = 'EXPIRED' AND actor = 'SYSTEM' AND reason = 'ORDER_EXPIRED'",
                    Integer.class, h.orderId())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ORDER_EXPIRED' AND target_id = ?",
                    Integer.class, h.orderId().toString())).isEqualTo(1);
        }

        assertThat(orderState(stillPending.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(redemptionState(stillPending.orderId(), voucher.getId())).isEqualTo("RESERVED");
        assertThat(usedCount(voucher.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ORDER_EXPIRED' AND target_id = ?",
                Integer.class, stillPending.orderId().toString())).isZero();
    }

    @Test
    void ac18KeepsOrderPendingAndVoucherReservedWhenPaymentUnresolved() throws Exception {
        Instant t0 = mutableClock.instant();
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        Promotion voucher = fixtures.createVoucher(
                "EXP18", DiscountType.AMOUNT, 50_000L, null,
                t0.minus(1, ChronoUnit.HOURS), t0.plus(1, ChronoUnit.DAYS),
                2, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        OrderHandle order = createOrder(buyer, product, voucher.getCode(), 150_000L);
        assertThat(usedCount(voucher.getId())).isEqualTo(1);

        // 1. Quá hạn + querydr trả 94 -> giữ PENDING_PAYMENT, voucher RESERVED.
        mutableClock.setAt(t0.plus(Duration.ofMinutes(20)));
        doReturn(queryResult(order.txnRef(), "94", null)).when(vnpayGateway).queryDr(any());
        orderExpiryService.expirePendingOrders();
        assertThat(orderState(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(redemptionState(order.orderId(), voucher.getId())).isEqualTo("RESERVED");
        assertThat(usedCount(voucher.getId())).isEqualTo(1);

        // 2. Sau cooldown 5 phút, lỗi mạng -> giữ PENDING_PAYMENT, voucher RESERVED.
        mutableClock.setAt(t0.plus(Duration.ofMinutes(25)));
        doThrow(new VnpayTransportException(false, null, "network error")).when(vnpayGateway).queryDr(any());
        orderExpiryService.expirePendingOrders();
        assertThat(orderState(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(redemptionState(order.orderId(), voucher.getId())).isEqualTo("RESERVED");
        assertThat(usedCount(voucher.getId())).isEqualTo(1);

        // 3. Sau cooldown 5 phút, 00/01 -> giữ PENDING_PAYMENT, voucher RESERVED.
        mutableClock.setAt(t0.plus(Duration.ofMinutes(30)));
        doReturn(queryResult(order.txnRef(), "00", "01")).when(vnpayGateway).queryDr(any());
        orderExpiryService.expirePendingOrders();
        assertThat(orderState(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(redemptionState(order.orderId(), voucher.getId())).isEqualTo("RESERVED");
        assertThat(usedCount(voucher.getId())).isEqualTo(1);

        // 3b. Sau cooldown 5 phút, 00/04 -> payment sang UNKNOWN, đơn vẫn giữ PENDING_PAYMENT và voucher vẫn RESERVED (R1-24).
        mutableClock.setAt(t0.plus(Duration.ofMinutes(35)));
        doReturn(queryResult(order.txnRef(), "00", "04")).when(vnpayGateway).queryDr(any());
        orderExpiryService.expirePendingOrders();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.payment WHERE order_id = ?", String.class, order.orderId()))
                .isEqualTo("UNKNOWN");
        assertThat(orderState(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(redemptionState(order.orderId(), voucher.getId())).isEqualTo("RESERVED");
        assertThat(usedCount(voucher.getId())).isEqualTo(1);

        // 4. Lần thanh toán FAILED có late_success_review = PENDING cũng không bị đóng sang EXPIRED.
        LearnerAccount buyer2 = fixtures.createLearner();
        var orderWithPendingReview = fixtures.insertStubOrder(
                buyer2, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, t0.minusSeconds(60), null);
        var failedPendingReview = fixtures.insertStubPayment(
                orderWithPendingReview, 1, 200_000L, PaymentState.FAILED);
        jdbc.update(
                "UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                failedPendingReview.id());
        orderExpiryService.expirePendingOrders();
        assertThat(orderState(orderWithPendingReview.id())).isEqualTo("PENDING_PAYMENT");

        // 5. Đơn đã quá hạn có sẵn payment UNKNOWN (vd quá 24h hoặc trong cooldown) giữ PENDING_PAYMENT & voucher RESERVED (R1-24).
        LearnerAccount buyer3 = fixtures.createLearner();
        mutableClock.setAt(t0);
        OrderHandle preExistingUnknownOrder = createOrder(buyer3, product, voucher.getCode(), 150_000L);
        assertThat(usedCount(voucher.getId())).isEqualTo(2);
        jdbc.update(
                "UPDATE commerce.payment SET state = 'UNKNOWN', created_at = ?, updated_at = ? WHERE order_id = ?",
                Timestamp.from(t0.minus(Duration.ofHours(25))),
                Timestamp.from(t0.minus(Duration.ofHours(25))),
                preExistingUnknownOrder.orderId());
        mutableClock.setAt(t0.plus(Duration.ofMinutes(40)));
        orderExpiryService.expirePendingOrders();
        assertThat(orderState(preExistingUnknownOrder.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(redemptionState(preExistingUnknownOrder.orderId(), voucher.getId())).isEqualTo("RESERVED");
        assertThat(usedCount(voucher.getId())).isEqualTo(2);
    }

    @Test
    void ac19FulfillsExpiredPendingOrderWhenQueryDrReturns0000() throws Exception {
        Instant t0 = mutableClock.instant();
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        Promotion voucher = fixtures.createVoucher(
                "EXP19", DiscountType.AMOUNT, 50_000L, null,
                t0.minus(1, ChronoUnit.HOURS), t0.plus(1, ChronoUnit.DAYS),
                2, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        OrderHandle order = createOrder(buyer, product, voucher.getCode(), 150_000L);
        mutableClock.setAt(t0.plus(Duration.ofMinutes(20)));
        doReturn(queryResult(order.txnRef(), "00", "00")).when(vnpayGateway).queryDr(any());

        int expired = orderExpiryService.expirePendingOrders();
        assertThat(expired).isZero();

        assertThat(orderState(order.orderId())).isEqualTo("FULFILLED");
        assertThat(redemptionState(order.orderId(), voucher.getId())).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.orderId())).isEqualTo(1);
    }

    @Test
    void doesNotQueryAgedOpenPaymentsPast24hAndKeepsOrderPending() {
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        mutableClock.setAt(now);

        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, now.minus(Duration.ofHours(23)), null);
        var agedUnknown = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.UNKNOWN);
        var agedCreated = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);
        Timestamp agedTs = Timestamp.from(now.minus(Duration.ofHours(24)).minusSeconds(1));
        jdbc.update(
                "UPDATE commerce.payment SET created_at = ?, updated_at = ? WHERE id IN (?, ?)",
                agedTs, agedTs, agedUnknown.id(), agedCreated.id());

        int expired = orderExpiryService.expirePendingOrders();
        assertThat(expired).isZero();
        verify(vnpayGateway, never()).queryDr(any());
        assertThat(orderState(order.id())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void doesNotStarveFreshExpiredOrdersBehind500PlusStuckPendingOrders() throws Exception {
        Instant t0 = mutableClock.instant();
        LearnerAccount stuckBuyer = fixtures.createLearner();
        LearnerAccount freshBuyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        Promotion voucher = fixtures.createVoucher(
                "STARV07", DiscountType.AMOUNT, 50_000L, null,
                t0.minus(1, ChronoUnit.HOURS), t0.plus(1, ChronoUnit.DAYS),
                2, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        int stuckCount = 510;
        List<UUID> stuckOrderIds = new ArrayList<>(stuckCount);
        try {
            Timestamp agedPaymentTs = Timestamp.from(t0.minus(Duration.ofHours(25)));
            List<Object[]> orderRows = new ArrayList<>(stuckCount);
            List<Object[]> paymentRows = new ArrayList<>(stuckCount);
            for (int i = 0; i < stuckCount; i++) {
                UUID orderId = Ids.newId();
                stuckOrderIds.add(orderId);
                String orderCode = "STUCK" + String.format("%04d", i) + UUID.randomUUID().toString().substring(0, 6);
                Timestamp expiresTs = Timestamp.from(t0.minus(Duration.ofHours(20)).plusSeconds(i));
                orderRows.add(new Object[] {
                    orderId, orderCode, stuckBuyer.id(), stuckBuyer.username(), stuckBuyer.fullName(),
                    product.examId(), product.versionNo(), "STUCKPROD-" + i, product.name(),
                    product.listPriceVnd(), 200_000L, product.attemptsGranted(),
                    expiresTs, agedPaymentTs, agedPaymentTs
                });
                boolean isUnknownAged = (i % 2 == 0);
                paymentRows.add(new Object[] {
                    Ids.newId(), orderId, orderCode + "01",
                    isUnknownAged ? "UNKNOWN" : "FAILED",
                    isUnknownAged ? null : "PENDING",
                    isUnknownAged ? null : agedPaymentTs,
                    agedPaymentTs, agedPaymentTs
                });
            }
            jdbc.batchUpdate("""
                    INSERT INTO commerce.purchase_order
                        (id, code, user_id, buyer_username, buyer_full_name, product_type, exam_id, exam_version_no,
                         option_codes, product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd,
                         attempts_granted, benefits, result_view, payment_method, state, expires_at,
                         created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, 'EXAM', ?, ?, '{}'::text[], ?, ?, ?, 'NONE', ?, ?,
                            '{}'::jsonb, 'FULL_SOLUTION', 'VNPAY', 'PENDING_PAYMENT', ?, ?, ?, 0)
                    """, orderRows);
            jdbc.batchUpdate("""
                    INSERT INTO commerce.payment
                        (id, order_id, attempt_seq, txn_ref, vnp_create_date, vnp_expire_date, amount_vnd,
                         state, is_duplicate, late_success_review, late_success_at, created_at, updated_at, version)
                    VALUES (?, ?, 1, ?, '20261120100000', '20261120101500', 200000, ?, false, ?, ?, ?, ?, 0)
                    """, paymentRows);

            OrderHandle freshOrder = createOrder(freshBuyer, product, voucher.getCode(), 150_000L);
            assertThat(usedCount(voucher.getId())).isEqualTo(1);

            mutableClock.setAt(t0.plus(Duration.ofMinutes(30)));
            doReturn(queryResult(freshOrder.txnRef(), "00", "02")).when(vnpayGateway).queryDr(any());

            int expiredCount = orderExpiryService.expirePendingOrders();
            assertThat(expiredCount).isGreaterThanOrEqualTo(1);
            assertThat(orderState(freshOrder.orderId())).isEqualTo("EXPIRED");
            assertThat(redemptionState(freshOrder.orderId(), voucher.getId())).isEqualTo("RELEASED");
            assertThat(usedCount(voucher.getId())).isZero();
            assertThat(orderState(stuckOrderIds.getFirst())).isEqualTo("PENDING_PAYMENT");
        } finally {
            CommerceTestFixtures.deleteStubOrdersForUser(jdbc, stuckBuyer.id());
        }
    }

    @Test
    void expiresMoreThanOneBatchUsingKeysetCursorIncludingEqualExpiresAtAcrossBoundary() {
        assertThat(orderExpiryProperties.batchSize()).isEqualTo(20);
        Instant now = mutableClock.instant().truncatedTo(ChronoUnit.MILLIS);
        mutableClock.setAt(now);

        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);

        Instant boundaryExpiresAt = now.minus(Duration.ofMinutes(10));
        Instant actionableCreatedAt = now.minus(Duration.ofHours(1));
        Instant stuckAgedAt = now.minus(Duration.ofHours(25));

        List<UUID> actionableOrderIds = new ArrayList<>(25);
        List<UUID> stuckOrderIds = new ArrayList<>(6);
        List<Object[]> orderRows = new ArrayList<>(31);
        List<Object[]> paymentRows = new ArrayList<>(31);

        try {
            // 25 actionable expired orders: 18 strictly before boundary, 4 sharing exact boundaryExpiresAt
            // (straddling batch boundary indices 19 and 20), and 3 strictly after boundary.
            for (int i = 0; i < 25; i++) {
                UUID orderId = Ids.newId();
                actionableOrderIds.add(orderId);
                String orderCode = "ACTP" + String.format("%02d", i) + UUID.randomUUID().toString().substring(0, 6);
                Instant expiresAt;
                if (i < 18) {
                    expiresAt = boundaryExpiresAt.minusSeconds(100L - i);
                } else if (i < 22) {
                    expiresAt = boundaryExpiresAt;
                } else {
                    expiresAt = boundaryExpiresAt.plusSeconds(i - 21L);
                }
                Timestamp expTs = Timestamp.from(expiresAt);
                Timestamp createdTs = Timestamp.from(actionableCreatedAt);
                orderRows.add(new Object[] {
                    orderId, orderCode, buyer.id(), buyer.username(), buyer.fullName(),
                    product.examId(), product.versionNo(), "ACTPROD-" + i, product.name(),
                    product.listPriceVnd(), 200_000L, product.attemptsGranted(),
                    expTs, createdTs, createdTs
                });
                paymentRows.add(new Object[] {
                    Ids.newId(), orderId, orderCode + "01",
                    "FAILED", null, null,
                    createdTs, createdTs
                });
            }

            // 6 interspersed stuck UNKNOWN > 24h orders before, at, and after boundaryExpiresAt.
            Instant[] stuckExpires = new Instant[] {
                boundaryExpiresAt.minusSeconds(120),
                boundaryExpiresAt.minusSeconds(50),
                boundaryExpiresAt,
                boundaryExpiresAt,
                boundaryExpiresAt.plusSeconds(2),
                boundaryExpiresAt.plusSeconds(30)
            };
            for (int i = 0; i < stuckExpires.length; i++) {
                UUID orderId = Ids.newId();
                stuckOrderIds.add(orderId);
                String orderCode = "STKP" + String.format("%02d", i) + UUID.randomUUID().toString().substring(0, 6);
                Timestamp expTs = Timestamp.from(stuckExpires[i]);
                Timestamp agedTs = Timestamp.from(stuckAgedAt);
                orderRows.add(new Object[] {
                    orderId, orderCode, buyer.id(), buyer.username(), buyer.fullName(),
                    product.examId(), product.versionNo(), "STKPROD-" + i, product.name(),
                    product.listPriceVnd(), 200_000L, product.attemptsGranted(),
                    expTs, agedTs, agedTs
                });
                paymentRows.add(new Object[] {
                    Ids.newId(), orderId, orderCode + "01",
                    "UNKNOWN", null, null,
                    agedTs, agedTs
                });
            }

            jdbc.batchUpdate("""
                    INSERT INTO commerce.purchase_order
                        (id, code, user_id, buyer_username, buyer_full_name, product_type, exam_id, exam_version_no,
                         option_codes, product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd,
                         attempts_granted, benefits, result_view, payment_method, state, expires_at,
                         created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, 'EXAM', ?, ?, '{}'::text[], ?, ?, ?, 'NONE', ?, ?,
                            '{}'::jsonb, 'FULL_SOLUTION', 'VNPAY', 'PENDING_PAYMENT', ?, ?, ?, 0)
                    """, orderRows);
            jdbc.batchUpdate("""
                    INSERT INTO commerce.payment
                        (id, order_id, attempt_seq, txn_ref, vnp_create_date, vnp_expire_date, amount_vnd,
                         state, is_duplicate, late_success_review, late_success_at, created_at, updated_at, version)
                    VALUES (?, ?, 1, ?, '20261120100000', '20261120101500', 200000, ?, false, ?, ?, ?, ?, 0)
                    """, paymentRows);

            int expiredCount = orderExpiryService.expirePendingOrders();
            assertThat(expiredCount).isEqualTo(25);

            for (UUID actionableId : actionableOrderIds) {
                assertThat(orderState(actionableId)).isEqualTo("EXPIRED");
            }
            for (UUID stuckId : stuckOrderIds) {
                assertThat(orderState(stuckId)).isEqualTo("PENDING_PAYMENT");
            }
        } finally {
            CommerceTestFixtures.deleteStubOrdersForUser(jdbc, buyer.id());
        }
    }

    private OrderHandle createOrder(
            LearnerAccount buyer, StoredTestProduct product, String voucherCode, long expectedAmountDue)
            throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "exp-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", voucherCode,
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", product.versionNo(),
                                "expectedAmountDue", expectedAmountDue))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode orderNode = mapper.readTree(raw).path("order");
        UUID orderId = UUID.fromString(orderNode.path("id").asText());
        String txnRef = orderNode.path("payments").get(0).path("txnRef").asText();
        fixtures.trackOrderId(orderId);
        return new OrderHandle(orderId, txnRef);
    }

    private QueryDrResult queryResult(String txnRef, String responseCode, String txnStatus) {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("vnp_ResponseCode", responseCode);
        if (txnStatus != null) {
            raw.put("vnp_TransactionStatus", txnStatus);
        }
        raw.put("vnp_TxnRef", txnRef);
        return new QueryDrResult(
                true,
                "resp-" + UUID.randomUUID().toString().substring(0, 8),
                responseCode,
                "Msg",
                txnRef,
                "00".equals(responseCode) && "00".equals(txnStatus) ? 150_000L : null,
                "NCB",
                VNP_TIME_FMT.format(mutableClock.instant().atZone(GMT_PLUS_7)),
                "00".equals(txnStatus) ? "98000001" : "0",
                "01",
                txnStatus,
                "Info",
                null,
                null,
                raw);
    }

    private String orderState(UUID orderId) {
        return jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId);
    }

    private String redemptionState(UUID orderId, UUID promoId) {
        return jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, orderId, promoId);
    }

    private int usedCount(UUID promoId) {
        Integer count = jdbc.queryForObject(
                "SELECT used_count FROM commerce.promotion WHERE id = ?", Integer.class, promoId);
        return count == null ? 0 : count;
    }

    private record OrderHandle(UUID orderId, String txnRef) {
    }
}
