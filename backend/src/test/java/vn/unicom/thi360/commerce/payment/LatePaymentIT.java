package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * E12, AC-23, AC-26, AC-46, AC-47, AC-48:
 * tiền về muộn sau {@code expires_at} (kể cả khi khuyến mãi đã hết hạn), sau {@code CANCELLED},
 * sau {@code REPLACED}, và thu trùng hai chiều gắn {@code needs_review = true, review_reason = DOUBLE_CHARGE_REPLACED_ORDER}.
 */
class LatePaymentIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void acceptsLatePaymentPastExpiresAtAndPastPromoEndAndGrantsEntitlement() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var promo = fixtures.createAutoPromotion(
                "Late Promo", DiscountType.AMOUNT, 50_000L, null,
                Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600),
                5, PromotionScope.ALL);

        String createdRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "late1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var root = mapper.readTree(createdRaw).path("order");
        UUID orderId = UUID.fromString(root.path("id").asText());
        String txnRef = root.path("payments").get(0).path("txnRef").asText();

        // Cho cả đơn lẫn khuyến mãi hết hạn trước khi IPN về muộn!
        jdbc.update("UPDATE commerce.purchase_order SET expires_at = now() - interval '30 minutes' WHERE id = ?", orderId);
        jdbc.update("UPDATE commerce.promotion SET ends_at = now() - interval '10 minutes' WHERE id = ?", promo.getId());

        callIpn(txnRef, 150_000L, "93000001")
                .andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(3);
    }

    @Test
    void handlesCancelledOrderReclaimSuccessAndReclaimFailure() throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var sharedOneShotVoucher = fixtures.createVoucher(
                "LATEC", DiscountType.AMOUNT, 50_000L, null,
                1, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        // Ca 1: buyer1 hủy đơn rồi tiền về muộn khi lượt voucher vẫn còn -> thu hồi thành công -> FULFILLED.
        String r1 = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer1))
                        .header("Idempotency-Key", "lc1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", sharedOneShotVoucher.getCode(),
                                "expectedAccountId", buyer1.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var o1 = mapper.readTree(r1).path("order");
        UUID o1Id = UUID.fromString(o1.path("id").asText());
        String o1Code = o1.path("code").asText();
        String o1Txn = o1.path("payments").get(0).path("txnRef").asText();

        mockMvc.perform(post("/api/v1/me/orders/" + o1Code + "/cancel").with(fixtures.asLearner(buyer1)))
                .andExpect(status().isOk());

        callIpn(o1Txn, 150_000L, "93000011")
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o1Id))
                .isEqualTo("FULFILLED");

        // Ca 2: voucher đã hết lượt; tiền về muộn trên đơn CANCELLED vẫn thu hồi (tăng over_limit_count), chuyển FULFILLED và cấp quyền!
        var buyer3 = fixtures.createLearner();
        var singleUse = fixtures.createVoucher(
                "LATEC2", DiscountType.AMOUNT, 50_000L, null,
                1, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);
        String r2 = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer3))
                        .header("Idempotency-Key", "lc2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", singleUse.getCode(),
                                "expectedAccountId", buyer3.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var o2 = mapper.readTree(r2).path("order");
        UUID o2Id = UUID.fromString(o2.path("id").asText());
        String o2Code = o2.path("code").asText();
        String o2Txn = o2.path("payments").get(0).path("txnRef").asText();
        mockMvc.perform(post("/api/v1/me/orders/" + o2Code + "/cancel").with(fixtures.asLearner(buyer3)))
                .andExpect(status().isOk());

        // buyer2 lấy mất lượt voucher duy nhất ngay trước khi IPN muộn của buyer3 về!
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer2))
                        .header("Idempotency-Key", "lc3-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", singleUse.getCode(),
                                "expectedAccountId", buyer2.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated());

        callIpn(o2Txn, 150_000L, "93000012")
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o2Id))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT over_limit_count FROM commerce.promotion WHERE id = ?", Integer.class, singleUse.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer3.id(), product.examId())).isEqualTo(2);
    }

    @Test
    void flagsDoubleChargeInBothDirectionsAcrossReplacedOrders() throws Exception {
        // Chiều A: O1 REPLACED bởi O2; O2 trả trước (FULFILLED), sau đó O1 nhận 00/00 -> O1 giữ REPLACED, payment excess, cả O1 & O2 needs_review=true.
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        var o2 = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p2 = fixtures.insertStubPayment(o2, 1, 200_000L, PaymentState.CREATED);

        var o1 = fixtures.insertStubOrder(
                buyer, product, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", o2.id(), o1.id());
        var p1 = fixtures.insertStubPayment(o1, 1, 200_000L, PaymentState.CREATED);

        callIpn(p2.txnRef(), 200_000L, "93000021").andExpect(jsonPath("$.RspCode").value("00"));
        callIpn(p1.txnRef(), 200_000L, "93000022").andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o1.id()))
                .isEqualTo("REPLACED");
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p1.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT review_reason FROM commerce.purchase_order WHERE id = ?", String.class, o1.id()))
                .isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
        assertThat(jdbc.queryForObject("SELECT review_reason FROM commerce.purchase_order WHERE id = ?", String.class, o2.id()))
                .isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");

        // Chiều B: O3 REPLACED bởi O4; O3 trả trước khi O4 chưa trả -> O3 thành FULFILLED; sau đó O4 trả -> cả O3 & O4 gắn DOUBLE_CHARGE_REPLACED_ORDER.
        var buyerB = fixtures.createLearner();
        var o4 = fixtures.insertStubOrder(
                buyerB, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p4 = fixtures.insertStubPayment(o4, 1, 200_000L, PaymentState.CREATED);

        var o3 = fixtures.insertStubOrder(
                buyerB, product, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", o4.id(), o3.id());
        var p3 = fixtures.insertStubPayment(o3, 1, 200_000L, PaymentState.CREATED);

        callIpn(p3.txnRef(), 200_000L, "93000031").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o3.id()))
                .isEqualTo("FULFILLED");

        callIpn(p4.txnRef(), 200_000L, "93000032").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT review_reason FROM commerce.purchase_order WHERE id = ?", String.class, o3.id()))
                .isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
        assertThat(jdbc.queryForObject("SELECT review_reason FROM commerce.purchase_order WHERE id = ?", String.class, o4.id()))
                .isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
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
}
