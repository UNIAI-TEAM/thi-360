package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService.ReserveRequest;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.refund.infrastructure.RefundGrantLookupRepository;
import vn.unicom.thi360.commerce.refund.infrastructure.RefundGrantLookupRepository.GrantRef;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử thu hồi quyền dùng, chính sách voucher E23 và luồng BS 14 đầy đủ (AC-19, AC-20, AC-21, AC-24, AC-36, AC-37).
 */
class RefundEntitlementIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionRedemptionService promotionRedemptionService;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private EntitlementGrantService entitlementGrantService;
    @Autowired private RefundGrantLookupRepository refundGrantLookupRepository;
    @Autowired private EntitlementPort entitlementPort;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private OutboxRelay outboxRelay;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @Test
    void fullRefundRevokesOnlyUnusedAttemptsPreservesUsedAndReservedAndWritesAudit() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = insertSucceededPayment(order, 1, 200_000L, false, "90050011");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        UUID entitlementId = jdbc.queryForObject(
                "SELECT id FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                UUID.class, buyer.id(), product.examId());
        UUID usedAttempt = UUID.randomUUID();
        entitlementPort.reserve(buyer.id(), entitlementId, usedAttempt);
        entitlementPort.confirmUse(usedAttempt);

        UUID reservedAttempt = UUID.randomUUID();
        entitlementPort.reserve(buyer.id(), entitlementId, reservedAttempt);

        UUID adminId = UUID.randomUUID();
        createApiRefund(adminId, order.code(), payment.id(), 200_000L);

        Map<String, Object> ent = jdbc.queryForMap(
                "SELECT attempts_total, attempts_used, attempts_reserved, attempts_revoked FROM commerce.entitlement WHERE id = ?",
                entitlementId);
        assertThat(((Number) ent.get("attempts_total")).intValue()).isEqualTo(3);
        assertThat(((Number) ent.get("attempts_used")).intValue()).isEqualTo(1);
        assertThat(((Number) ent.get("attempts_reserved")).intValue()).isEqualTo(1);
        assertThat(((Number) ent.get("attempts_revoked")).intValue()).isEqualTo(1);

        Map<String, Object> grant = jdbc.queryForMap(
                "SELECT attempts_revoked, revoked_at FROM commerce.entitlement_grant WHERE order_id = ?",
                order.id());
        assertThat(((Number) grant.get("attempts_revoked")).intValue()).isEqualTo(1);
        assertThat(grant.get("revoked_at")).isNotNull();

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");

        // Lượt đang reserve trước thời điểm hoàn vẫn confirmUse thành công; lượt mới không thể reserve.
        entitlementPort.confirmUse(reservedAttempt);
        Map<String, Object> entAfterConfirm = jdbc.queryForMap(
                "SELECT attempts_used, attempts_reserved, attempts_revoked FROM commerce.entitlement WHERE id = ?",
                entitlementId);
        assertThat(((Number) entAfterConfirm.get("attempts_used")).intValue()).isEqualTo(2);
        assertThat(((Number) entAfterConfirm.get("attempts_reserved")).intValue()).isZero();
        assertThat(((Number) entAfterConfirm.get("attempts_revoked")).intValue()).isEqualTo(1);

        assertThatThrownBy(() -> entitlementPort.reserve(buyer.id(), entitlementId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ENTITLEMENT_REVOKED' AND target_id = ?",
                Integer.class, order.id().toString())).isEqualTo(1);
    }

    @Test
    void refundingDuplicateExcessPaymentPreservesEntitlementAndOrderState() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        insertSucceededPayment(order, 1, 200_000L, false, "90050021");
        var dupPayment = insertSucceededPayment(order, 2, 200_000L, true, "90050022");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        createApiRefund(UUID.randomUUID(), order.code(), dupPayment.id(), 200_000L);

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_revoked FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT revoked_at FROM commerce.entitlement_grant WHERE order_id = ?",
                Object.class, order.id())).isNull();

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ENTITLEMENT_REVOKED' AND target_id = ?",
                Integer.class, order.id().toString())).isZero();
    }

    @Test
    void partialRefundsKeepAttemptsUntilSumReachesPaidAmountThenRevokeOnce() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = insertSucceededPayment(order, 1, 200_000L, false, "90050031");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        UUID adminId = UUID.randomUUID();
        createApiRefund(adminId, order.code(), payment.id(), 80_000L);

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_revoked FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isZero();

        createApiRefund(adminId, order.code(), payment.id(), 120_000L);

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_revoked FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(3);

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ENTITLEMENT_REVOKED' AND target_id = ?",
                Integer.class, order.id().toString())).isEqualTo(1);
    }

    @Test
    void refundNeverDecrementsVoucherUsedCount() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var voucher = fixtures.createVoucher(
                "E23R", DiscountType.AMOUNT, 50_000L, null, 10, 2, false,
                PromotionScope.ALL, PromotionAudienceType.ALL);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 150_000L, null, Instant.now());
        jdbc.update("""
                UPDATE commerce.purchase_order
                SET promotion_id = ?, promotion_discount_vnd = 50000, discount_applied = 'PROMOTION_ONLY'
                WHERE id = ?
                """, voucher.getId(), order.id());
        var payment = insertSucceededPayment(order, 1, 150_000L, false, "90050041");

        promotionRedemptionService.reserveForOrder(
                buyer.id(), order.id(), order.code(), List.of(new ReserveRequest(voucher.getId(), 50_000L)));
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT used_count FROM commerce.promotion WHERE id = ?", Integer.class, voucher.getId()))
                .isEqualTo(1);

        createApiRefund(UUID.randomUUID(), order.code(), payment.id(), 150_000L);

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT used_count FROM commerce.promotion WHERE id = ?", Integer.class, voucher.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, order.id(), voucher.getId())).isEqualTo("CONFIRMED");
    }

    @Test
    void bs14CanonicalLateSuccessAndRefundOrderBothWaysAndRepeatIpnIdempotent() throws Exception {
        UUID adminId = UUID.randomUUID();

        // Kịch bản A (AC-36): hoàn khoản trùng p1 trước (giữ bài), sau đó hoàn khoản được tính p2 (thu hồi & REFUNDED),
        // và IPN lặp lại của p1 trả 02 không tạo thêm payment hay refund.
        LearnerAccount buyerA = fixtures.createLearner(true, "vi");
        StoredTestProduct productA = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        OrderHandle orderA = createOrderViaApi(buyerA, productA);
        callIpn(orderA.firstTxnRef(), 200_000L, "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        String aTxn2 = retryPayViaApi(buyerA, orderA.orderCode());
        callIpn(aTxn2, 200_000L, "00", "00", "90051002", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        callIpn(orderA.firstTxnRef(), 200_000L, "00", "00", "90051001", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment pA1 = paymentRepository.findByTxnRef(orderA.firstTxnRef()).orElseThrow();
        Payment pA2 = paymentRepository.findByTxnRef(aTxn2).orElseThrow();

        createApiRefund(adminId, orderA.orderCode(), pA1.getId(), 200_000L);
        assertThat(orderState(orderA.orderId())).isEqualTo("FULFILLED");
        assertThat(revokedAttempts(buyerA.id(), productA.examId())).isZero();

        createApiRefund(adminId, orderA.orderCode(), pA2.getId(), 200_000L);
        assertThat(orderState(orderA.orderId())).isEqualTo("REFUNDED");
        assertThat(revokedAttempts(buyerA.id(), productA.examId())).isEqualTo(2);

        callIpn(orderA.firstTxnRef(), 200_000L, "00", "00", "90051001", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderA.orderId()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.refund WHERE order_id = ?", Integer.class, orderA.orderId()))
                .isEqualTo(2);

        // Kịch bản B (AC-37): hoàn khoản được tính p2 trước (đơn -> REFUNDED, thu hồi quyền),
        // rồi hoàn khoản trùng p1 trên đơn đã REFUNDED (thành công, không đổi trạng thái đơn và không thu hồi thêm).
        LearnerAccount buyerB = fixtures.createLearner(true, "vi");
        StoredTestProduct productB = fixtures.insertOnSaleProduct("QDA", 220_000L, 2);
        OrderHandle orderB = createOrderViaApi(buyerB, productB);
        callIpn(orderB.firstTxnRef(), 220_000L, "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        String bTxn2 = retryPayViaApi(buyerB, orderB.orderCode());
        callIpn(bTxn2, 220_000L, "00", "00", "90052002", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        callIpn(orderB.firstTxnRef(), 220_000L, "00", "00", "90052001", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment pB1 = paymentRepository.findByTxnRef(orderB.firstTxnRef()).orElseThrow();
        Payment pB2 = paymentRepository.findByTxnRef(bTxn2).orElseThrow();

        createApiRefund(adminId, orderB.orderCode(), pB2.getId(), 220_000L);
        assertThat(orderState(orderB.orderId())).isEqualTo("REFUNDED");
        assertThat(revokedAttempts(buyerB.id(), productB.examId())).isEqualTo(2);

        createApiRefund(adminId, orderB.orderCode(), pB1.getId(), 220_000L);
        assertThat(orderState(orderB.orderId())).isEqualTo("REFUNDED");
        assertThat(revokedAttempts(buyerB.id(), productB.examId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND to_state = 'REFUNDED'",
                Integer.class, orderB.orderId())).isEqualTo(1);
    }

    @Test
    void multiGrantOrderOrdersGrantRefsByExamIdAscAndRevokesAllGrantsOnFullRefund() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        StoredTestProduct p1 = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        StoredTestProduct p2 = fixtures.insertOnSaleProduct("QDA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, p1, OrderState.PAID, PaymentMethod.VNPAY, 400_000L, null, Instant.now());
        var payment = insertSucceededPayment(order, 1, 400_000L, false, "90053001");

        // Cấp 2 dòng grant cho 2 bài thi trên cùng 1 đơn (mô phỏng đơn bộ) và kiểm tra thứ tự examId ASC.
        entitlementGrantService.grantForOrder(new GrantOrderCommand(
                order.id(),
                order.code(),
                buyer.id(),
                List.of(
                        new GrantLine(p2.examId(), 1, 3, List.of("OPT-A"), Map.of(), "FULL_SOLUTION"),
                        new GrantLine(p1.examId(), 1, 2, List.of("OPT-A"), Map.of(), "FULL_SOLUTION"))));
        List<GrantRef> refs = refundGrantLookupRepository.findGrantRefsByOrderId(order.id());
        assertThat(refs).hasSize(2);
        assertThat(refs.get(0).examId().toString()).isLessThan(refs.get(1).examId().toString());

        createApiRefund(UUID.randomUUID(), order.code(), payment.id(), 400_000L);

        assertThat(revokedAttempts(buyer.id(), p1.examId())).isEqualTo(2);
        assertThat(revokedAttempts(buyer.id(), p2.examId())).isEqualTo(3);
    }

    private CommerceTestFixtures.StubPayment insertSucceededPayment(
            CommerceTestFixtures.StubOrder order, int seq, long amountVnd, boolean duplicate, String txnNo) {
        var payment = fixtures.insertStubPayment(order, seq, amountVnd, PaymentState.CREATED);
        jdbc.update("""
                UPDATE commerce.payment
                SET state = 'SUCCEEDED', vnp_transaction_no = ?, bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now(),
                    is_duplicate = ?, excess_reason = ?
                WHERE id = ?
                """, txnNo, duplicate, duplicate ? "AFTER_COUNTED" : null, payment.id());
        return payment;
    }

    private void createApiRefund(UUID adminId, String orderCode, UUID paymentId, long amountVnd) throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/payments/" + paymentId + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ent-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "amountVnd", amountVnd,
                                "kind", "API",
                                "reason", "Hoan tien kiem tra entitlement",
                                "verifiedSystemError", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));
    }

    private OrderHandle createOrderViaApi(LearnerAccount buyer, StoredTestProduct product) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", product.versionNo(),
                "expectedAmountDue", product.listPriceVnd()));
        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "ord-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = mapper.readTree(raw);
        UUID orderId = UUID.fromString(root.path("order").path("id").asText());
        String orderCode = root.path("order").path("code").asText();
        String firstTxnRef = root.path("order").path("payments").get(0).path("txnRef").asText();
        fixtures.trackOrderId(orderId);
        return new OrderHandle(orderId, orderCode, firstTxnRef);
    }

    private String retryPayViaApi(LearnerAccount buyer, String orderCode) throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "pay-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(raw).path("txnRef").asText();
    }

    private org.springframework.test.web.servlet.ResultActions callIpn(
            String txnRef, long amountVnd, String responseCode, String txStatus,
            String txnNo, String bankCode, String cardType) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", bankCode);
        params.put("vnp_CardType", cardType);
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", txStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        return mockMvc.perform(req).andExpect(status().isOk());
    }

    private String orderState(UUID orderId) {
        return jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId);
    }

    private int revokedAttempts(UUID userId, UUID examId) {
        Integer revoked = jdbc.queryForObject(
                "SELECT attempts_revoked FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, userId, examId);
        return revoked == null ? 0 : revoked;
    }

    private record OrderHandle(UUID orderId, String orderCode, String firstTxnRef) {
    }
}
