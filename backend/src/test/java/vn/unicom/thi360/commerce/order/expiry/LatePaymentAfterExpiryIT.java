package vn.unicom.thi360.commerce.order.expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.payment.domain.PaymentEventSource;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrService;
import vn.unicom.thi360.commerce.payment.vnpay.VnpayTestClockConfig;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubOrder;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubPayment;
import vn.unicom.thi360.shared.testapi.MutableClock;

/**
 * AC-20: tiền về muộn sau khi đơn đã {@code EXPIRED} (qua IPN và qua {@code querydr}):
 * thu hồi lại voucher (vượt {@code total_limit} ghi {@code over_limit_count}), cấp quyền theo ảnh chụp
 * kể cả khi sản phẩm đã {@code DISCONTINUED}, giữ {@code needs_review = false}, và ghi {@code order_history}.
 */
@Import(VnpayTestClockConfig.class)
class LatePaymentAfterExpiryIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private OrderExpiryService orderExpiryService;
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
    void latePaymentViaIpnAfterOrderExpiryReclaimsOverLimitVoucherAndGrantsEvenWhenDiscontinued() throws Exception {
        Instant t0 = mutableClock.instant();
        LearnerAccount buyer1 = fixtures.createLearner();
        LearnerAccount buyer2 = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);
        Promotion singleUseVoucher = fixtures.createVoucher(
                "AC20IPN", DiscountType.AMOUNT, 50_000L, null,
                t0.minus(1, ChronoUnit.HOURS), t0.plus(1, ChronoUnit.DAYS),
                1, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        // 1. Tạo đơn của buyer1 dùng voucher giới hạn 1 lượt.
        OrderHandle o1 = createOrder(buyer1, product, singleUseVoucher.getCode(), 150_000L);
        assertThat(promoStat(singleUseVoucher.getId(), "used_count")).isEqualTo(1);

        // 2. Quá hạn thanh toán: worker hỏi querydr trả 00/02 -> đơn sang EXPIRED, voucher RELEASED, used_count=0.
        mutableClock.setAt(t0.plus(Duration.ofMinutes(20)));
        doReturn(signedResult("00", "02", o1.firstTxnRef(), 150_000L, "0")).when(vnpayGateway).queryDr(any());
        orderExpiryService.expirePendingOrders();

        assertThat(orderState(o1.orderId())).isEqualTo("EXPIRED");
        assertThat(redemptionState(o1.orderId(), singleUseVoucher.getId())).isEqualTo("RELEASED");
        assertThat(promoStat(singleUseVoucher.getId(), "used_count")).isZero();

        // 3. buyer2 chiếm lượt voucher duy nhất (used_count=1) và sản phẩm bị đánh dấu DISCONTINUED.
        createOrder(buyer2, product, singleUseVoucher.getCode(), 150_000L);
        assertThat(promoStat(singleUseVoucher.getId(), "used_count")).isEqualTo(1);
        assertThat(promoStat(singleUseVoucher.getId(), "over_limit_count")).isZero();
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 200_000L, 1, "DISCONTINUED");

        // 4. Giao dịch mở nhận IPN 00/00 đến muộn sau EXPIRED -> PAID -> FULFILLED, cấp đúng quyền đã chụp.
        StubPayment lateAttempt = fixtures.insertStubPayment(
                new StubOrder(o1.orderId(), o1.orderCode()), 2, 150_000L, PaymentState.CREATED);

        callIpn(lateAttempt.txnRef(), 150_000L, "99002001")
                .andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(orderState(o1.orderId())).isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1.orderId())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND from_state = 'EXPIRED' AND to_state = 'PAID' AND reason = ?",
                Integer.class, o1.orderId(), "LATE_PAYMENT_RECLAIMED:" + lateAttempt.txnRef())).isEqualTo(1);

        Map<String, Object> grant = jdbc.queryForMap("""
                SELECT attempts, array_to_string(option_codes, ',') AS option_codes_csv,
                       result_view, benefits::text AS benefits_json
                FROM commerce.entitlement_grant
                WHERE order_id = ?
                """, o1.orderId());
        assertThat(grant.get("attempts")).isEqualTo(4);
        assertThat(grant.get("option_codes_csv")).isEqualTo("OPT-A,OPT-B");
        assertThat(grant.get("result_view")).isEqualTo("FULL_SOLUTION");
        assertThat((String) grant.get("benefits_json")).contains("aiExplanation");

        assertThat(redemptionState(o1.orderId(), singleUseVoucher.getId())).isEqualTo("CONFIRMED");
        assertThat(promoStat(singleUseVoucher.getId(), "used_count")).isEqualTo(2);
        assertThat(promoStat(singleUseVoucher.getId(), "over_limit_count")).isEqualTo(1);
    }

    @Test
    void latePaymentViaQueryDrAfterOrderExpiryReclaimsOverLimitVoucherAndGrantsEvenWhenDiscontinued() throws Exception {
        Instant t0 = mutableClock.instant();
        LearnerAccount buyer1 = fixtures.createLearner();
        LearnerAccount buyer2 = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 5);
        Promotion singleUseVoucher = fixtures.createVoucher(
                "AC20QDR", DiscountType.AMOUNT, 50_000L, null,
                t0.minus(1, ChronoUnit.HOURS), t0.plus(1, ChronoUnit.DAYS),
                1, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        // 1. Tạo đơn của buyer1 dùng voucher giới hạn 1 lượt.
        OrderHandle o1 = createOrder(buyer1, product, singleUseVoucher.getCode(), 150_000L);

        // 2. Quá hạn thanh toán: worker đóng đơn sang EXPIRED, voucher RELEASED, used_count=0.
        mutableClock.setAt(t0.plus(Duration.ofMinutes(20)));
        doReturn(signedResult("00", "02", o1.firstTxnRef(), 150_000L, "0")).when(vnpayGateway).queryDr(any());
        orderExpiryService.expirePendingOrders();

        assertThat(orderState(o1.orderId())).isEqualTo("EXPIRED");
        assertThat(redemptionState(o1.orderId(), singleUseVoucher.getId())).isEqualTo("RELEASED");
        assertThat(promoStat(singleUseVoucher.getId(), "used_count")).isZero();

        // 3. buyer2 chiếm lượt voucher duy nhất và sản phẩm bị đánh dấu DISCONTINUED.
        createOrder(buyer2, product, singleUseVoucher.getCode(), 150_000L);
        assertThat(promoStat(singleUseVoucher.getId(), "used_count")).isEqualTo(1);
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 200_000L, 1, "DISCONTINUED");

        // 4. Lần thanh toán ở trạng thái UNKNOWN nhận kết quả querydr 00/00 -> PAID -> FULFILLED.
        StubPayment unknownAttempt = fixtures.insertStubPayment(
                new StubOrder(o1.orderId(), o1.orderCode()), 2, 150_000L, PaymentState.UNKNOWN);
        doReturn(signedResult("00", "00", unknownAttempt.txnRef(), 150_000L, "99002002"))
                .when(vnpayGateway).queryDr(any());

        queryDrService.queryPaymentNow(unknownAttempt.id());

        assertThat(orderState(o1.orderId())).isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1.orderId())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND from_state = 'EXPIRED' AND to_state = 'PAID' AND actor = 'VNPAY_QUERYDR' AND reason = ?",
                Integer.class, o1.orderId(), "LATE_PAYMENT_RECLAIMED:" + unknownAttempt.txnRef())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ? AND attempts = 5",
                Integer.class, o1.orderId())).isEqualTo(1);
        assertThat(redemptionState(o1.orderId(), singleUseVoucher.getId())).isEqualTo("CONFIRMED");
        assertThat(promoStat(singleUseVoucher.getId(), "used_count")).isEqualTo(2);
        assertThat(promoStat(singleUseVoucher.getId(), "over_limit_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = ? AND signature_valid = true",
                Integer.class, unknownAttempt.id(), PaymentEventSource.QUERYDR.name())).isEqualTo(1);
    }

    private OrderHandle createOrder(
            LearnerAccount buyer, StoredTestProduct product, String voucherCode, long expectedAmountDue)
            throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "late-exp-" + UUID.randomUUID())
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
        String orderCode = orderNode.path("code").asText();
        String firstTxnRef = orderNode.path("payments").get(0).path("txnRef").asText();
        fixtures.trackOrderId(orderId);
        return new OrderHandle(orderId, orderCode, firstTxnRef);
    }

    private org.springframework.test.web.servlet.ResultActions callIpn(
            String txnRef, long amountVnd, String txnNo) throws Exception {
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
        return mockMvc.perform(req).andExpect(status().isOk());
    }

    private static QueryDrResult signedResult(
            String responseCode, String txnStatus, String txnRef, long amountVnd, String txnNo) {
        return new QueryDrResult(
                true,
                "resp-" + UUID.randomUUID().toString().substring(0, 8),
                responseCode,
                "Msg",
                txnRef,
                amountVnd,
                "NCB",
                "20261121100200",
                txnNo,
                "01",
                txnStatus,
                "Info",
                null,
                null,
                Map.of(
                        "vnp_ResponseCode", responseCode,
                        "vnp_TransactionStatus", txnStatus,
                        "vnp_TxnRef", txnRef,
                        "vnp_Amount", Long.toString(amountVnd * 100L)));
    }

    private String orderState(UUID orderId) {
        return jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId);
    }

    private String redemptionState(UUID orderId, UUID promoId) {
        return jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, orderId, promoId);
    }

    private int promoStat(UUID promoId, String col) {
        Integer val = jdbc.queryForObject(
                "SELECT " + col + " FROM commerce.promotion WHERE id = ?", Integer.class, promoId);
        return val == null ? 0 : val;
    }

    private record OrderHandle(UUID orderId, String orderCode, String firstTxnRef) {
    }
}
