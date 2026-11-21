package vn.unicom.thi360.commerce.order.expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

        for (OrderHandle h : new OrderHandle[] {expiredBy02, expiredBy91}) {
            assertThat(orderState(h.orderId())).isEqualTo("EXPIRED");
            assertThat(redemptionState(h.orderId(), voucher.getId())).isEqualTo("RELEASED");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND from_state = 'PENDING_PAYMENT' AND to_state = 'EXPIRED' AND actor = 'SYSTEM' AND reason = 'ORDER_EXPIRED'",
                    Integer.class, h.orderId())).isEqualTo(1);
        }

        assertThat(orderState(stillPending.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(redemptionState(stillPending.orderId(), voucher.getId())).isEqualTo("RESERVED");
        assertThat(usedCount(voucher.getId())).isEqualTo(1);
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
