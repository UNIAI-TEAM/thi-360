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
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;
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
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;

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

        // Ca 3: sản phẩm đã DISCONTINUED sau khi đặt đơn vẫn cấp quyền khi tiền về muộn trên đơn CANCELLED (AC-27 / BR-02).
        var buyer4 = fixtures.createLearner();
        var discProduct = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);
        String r3 = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer4))
                        .header("Idempotency-Key", "lc4-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", discProduct.code(),
                                "expectedAccountId", buyer4.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var o3 = mapper.readTree(r3).path("order");
        UUID o3Id = UUID.fromString(o3.path("id").asText());
        String o3Code = o3.path("code").asText();
        String o3Txn = o3.path("payments").get(0).path("txnRef").asText();
        mockMvc.perform(post("/api/v1/me/orders/" + o3Code + "/cancel").with(fixtures.asLearner(buyer4)))
                .andExpect(status().isOk());
        fixtures.updateStorefrontPriceAndVersion(discProduct.examId(), 200_000L, 1, "DISCONTINUED");

        callIpn(o3Txn, 200_000L, "93000013")
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o3Id))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer4.id(), discProduct.examId())).isEqualTo(4);
    }

    @Test
    void flagWhenReplacementAlreadyPaid() throws Exception {
        // O1 REPLACED bởi O2 (cùng dùng voucher total_limit=1); O2 trả trước -> FULFILLED, O2.needs_review=false.
        // Sau đó O1 nhận 00/00 -> O1 trở thành PAID -> FULFILLED (coi như mua thêm), reclaim voucher (over_limit_count=1),
        // payment của O1 được tính (is_duplicate=false) và O1.needs_review=true (DOUBLE_CHARGE_REPLACED_ORDER).
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var sharedOneShot = fixtures.createVoucher(
                "AC48A", DiscountType.AMOUNT, 50_000L, null,
                1, 5, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        String r1 = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "dc1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", sharedOneShot.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var o1 = mapper.readTree(r1).path("order");
        UUID o1Id = UUID.fromString(o1.path("id").asText());
        UUID p1Id = UUID.fromString(o1.path("payments").get(0).path("id").asText());
        String p1Txn = o1.path("payments").get(0).path("txnRef").asText();

        // Thay thế O1 bằng O2 (không voucher, đổi giá trị để ép REPLACED) rồi để buyer khác chiếm lượt voucher.
        String r2 = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "dc2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var o2 = mapper.readTree(r2).path("order");
        UUID o2Id = UUID.fromString(o2.path("id").asText());
        String p2Txn = o2.path("payments").get(0).path("txnRef").asText();

        var otherBuyer = fixtures.createLearner();
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(otherBuyer))
                        .header("Idempotency-Key", "dc-other-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", sharedOneShot.getCode(),
                                "expectedAccountId", otherBuyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated());

        // O2 trả trước.
        callIpn(p2Txn, 200_000L, "93000021").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o2Id))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o2Id))
                .isFalse();

        // O1 nhận tiền muộn sau khi O2 đã trả.
        callIpn(p1Txn, 150_000L, "93000022").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o1Id))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p1Id))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p1Id))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1Id))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT review_reason FROM commerce.purchase_order WHERE id = ?", String.class, o1Id))
                .isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, o1Id, sharedOneShot.getId())).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT over_limit_count FROM commerce.promotion WHERE id = ?", Integer.class, sharedOneShot.getId()))
                .isEqualTo(1);
    }

    @Test
    void flagWhenReplacementPaysLater() throws Exception {
        // O1 REPLACED bởi O2; O1 trả trước khi O2 chưa trả -> FULFILLED với needs_review=false;
        // sau đó O2 trả -> FULFILLED và O1 bị gắn needs_review=true (DOUBLE_CHARGE_REPLACED_ORDER).
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

        callIpn(p1.txnRef(), 200_000L, "93000031").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o1.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1.id()))
                .isFalse();

        callIpn(p2.txnRef(), 200_000L, "93000032").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o2.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT review_reason FROM commerce.purchase_order WHERE id = ?", String.class, o1.id()))
                .isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o2.id()))
                .isFalse();
    }

    @Test
    void noFlagWithoutDoubleCharge() throws Exception {
        // (a) O1 REPLACED bởi O2 đang PENDING_PAYMENT (chưa trả) -> O1 nhận tiền thành FULFILLED, needs_review=false.
        var buyerA = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        var o2Pending = fixtures.insertStubOrder(
                buyerA, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var o1Replaced = fixtures.insertStubOrder(
                buyerA, product, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?",
                o2Pending.id(), o1Replaced.id());
        var p1 = fixtures.insertStubPayment(o1Replaced, 1, 200_000L, PaymentState.CREATED);

        callIpn(p1.txnRef(), 200_000L, "93000041").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o1Replaced.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1Replaced.id()))
                .isFalse();

        // (b) Đơn CANCELLED nhận tiền muộn -> FULFILLED, needs_review=false.
        var buyerB = fixtures.createLearner();
        var cancelledOrder = fixtures.insertStubOrder(
                buyerB, product, OrderState.CANCELLED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var pCancelled = fixtures.insertStubPayment(cancelledOrder, 1, 200_000L, PaymentState.CREATED);

        callIpn(pCancelled.txnRef(), 200_000L, "93000042").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, cancelledOrder.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, cancelledOrder.id()))
                .isFalse();
    }

    @Test
    void marksDuplicatePaymentOnPaidOrFulfilledOrderWithoutGrantingExtra() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var paidOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), Instant.now());
        var pPaid = fixtures.insertStubPayment(paidOrder, 1, 200_000L, PaymentState.CREATED);

        callIpn(pPaid.txnRef(), 200_000L, "93000051").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, pPaid.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, paidOrder.id())).isZero();

        var fulfilledOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.FULFILLED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), Instant.now());
        var pFulfilled = fixtures.insertStubPayment(fulfilledOrder, 1, 200_000L, PaymentState.CREATED);

        callIpn(pFulfilled.txnRef(), 200_000L, "93000052").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, pFulfilled.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, fulfilledOrder.id())).isZero();
    }

    @Test
    void flagsDoubleChargeAcrossThreeOrderReplacementChainAndHandlesCycleSafely() throws Exception {
        // R1-8/9: chuỗi 3 đơn O1 -> O2 -> O3, O3 thu tiền trước rồi O1 nhận IPN muộn -> O1.needs_review=true (DOUBLE_CHARGE_REPLACED_ORDER).
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);

        var o3 = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p3 = fixtures.insertStubPayment(o3, 1, 200_000L, PaymentState.CREATED);

        var o2 = fixtures.insertStubOrder(
                buyer, product, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", o3.id(), o2.id());

        var o1 = fixtures.insertStubOrder(
                buyer, product, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", o2.id(), o1.id());
        var p1 = fixtures.insertStubPayment(o1, 1, 200_000L, PaymentState.CREATED);

        callIpn(p3.txnRef(), 200_000L, "93000061").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o3.id()))
                .isEqualTo("FULFILLED");

        callIpn(p1.txnRef(), 200_000L, "93000062").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o1.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, o1.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT review_reason FROM commerce.purchase_order WHERE id = ?", String.class, o1.id()))
                .isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");

        // R1-31: các truy vấn WITH RECURSIVE có CYCLE dừng an toàn ngay cả khi dữ liệu bị hỏng tạo vòng lặp.
        var c1 = fixtures.insertStubOrder(
                buyer, product, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var c2 = fixtures.insertStubOrder(
                buyer, product, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", c2.id(), c1.id());
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", c1.id(), c2.id());
        assertThat(purchaseOrderRepository.existsPaidInReplacementChainStartingAt(c1.id())).isFalse();
        assertThat(purchaseOrderRepository.findPaidReplacementChainOrderIds(c1.id())).isEmpty();
        assertThat(purchaseOrderRepository.findPaidPredecessorOrderIds(c1.id())).isEmpty();
        assertThat(purchaseOrderRepository.findAllReplacementChainOrderIds(c1.id()))
                .containsExactlyInAnyOrder(c1.id(), c2.id());
        assertThat(purchaseOrderRepository.findAllPredecessorOrderIds(c1.id()))
                .containsExactlyInAnyOrder(c1.id(), c2.id());
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
