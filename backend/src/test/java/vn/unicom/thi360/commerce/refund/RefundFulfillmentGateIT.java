package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentRetryWorker;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử cổng chặn cấp quyền khi đơn có hoàn tiền (AC-22, AC-23, TM 8.5 bước 1)
 * dùng bean thật {@code RefundProgressImpl} cùng {@link FulfillmentService} và {@link FulfillmentRetryWorker}.
 */
@TestPropertySource(properties = "thi360.commerce.scheduling.enabled=true")
class RefundFulfillmentGateIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private FulfillmentRetryWorker retryWorker;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private OutboxRelay outboxRelay;

    @MockitoSpyBean
    private VnpayGateway vnpayGateway;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        Mockito.reset(vnpayGateway);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(vnpayGateway);
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @Test
    void paidOrderFullyRefundedTransitionsToRefundedWithoutGrantingAndExitsChecking() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var countedPayment = insertSucceededPayment(order, 1, 200_000L, false, "90060011");

        // Trước khi hoàn: đơn PAID chưa cấp quyền hiển thị CHECKING với học viên.
        assertThat(queryExamAccessState(buyer, product.code())).isEqualTo("CHECKING");

        // Hoàn đủ khoản được tính khi đơn đang PAID -> chuyển thẳng sang REFUNDED.
        UUID adminId = UUID.randomUUID();
        createApiRefund(adminId, order.code(), countedPayment.id(), 200_000L, "SUCCEEDED");

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");

        // Cả fulfill trực tiếp lẫn worker đều không được cấp quyền.
        assertThat(fulfillmentService.fulfill(order.id())).isFalse();
        retryWorker.retryPendingFulfillments();

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isZero();
        assertThat(queryExamAccessState(buyer, product.code())).isEqualTo("NONE");
    }

    @Test
    void directSucceededFullRefundOnStillPaidOrderTransitionsToRefundedWithoutGrantingInFulfillAndWorker()
            throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var productDirect = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var paidDirect = fixtures.insertStubOrder(
                buyer, productDirect, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pDirect = insertSucceededPayment(paidDirect, 1, 200_000L, false, "90060091");

        // Chèn thẳng dòng refund SUCCEEDED đủ số tiền trong khi đơn vẫn ở trạng thái PAID -> đi qua nhánh isFullyRefunded() của FulfillmentService.
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at, finished_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 200000, 'API', 'SUCCEEDED', 'Direct seeded full refund', true,
                        'ops', ?, now(), now(), now(), now(), 0)
                """,
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                "gate-direct-1-" + UUID.randomUUID(),
                pDirect.id(),
                paidDirect.id(),
                UUID.randomUUID());

        assertThat(fulfillmentService.fulfill(paidDirect.id())).isFalse();
        assertThat(orderState(paidDirect.id())).isEqualTo("REFUNDED");
        assertThat(grantCount(paidDirect.id())).isZero();
        assertThat(queryExamAccessState(buyer, productDirect.code())).isEqualTo("NONE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND from_state = 'PAID' AND to_state = 'REFUNDED' AND reason = 'REFUNDED_BEFORE_FULFILLMENT'",
                Integer.class, paidDirect.id())).isEqualTo(1);

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ORDER_REFUNDED_BEFORE_FULFILLMENT' AND target_id = ?",
                Integer.class, paidDirect.id().toString())).isEqualTo(1);

        // Kiểm tra đường worker retryPendingFulfillments() trên đơn PAID có refund SUCCEEDED trực tiếp.
        var productWorker = fixtures.insertOnSaleProduct("QDA", 200_000L, 2);
        var paidWorker = fixtures.insertStubOrder(
                buyer, productWorker, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pWorker = insertSucceededPayment(paidWorker, 1, 200_000L, false, "90060092");
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at, finished_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 200000, 'API', 'SUCCEEDED', 'Worker seeded full refund', true,
                        'ops', ?, now(), now(), now(), now(), 0)
                """,
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                "gate-direct-2-" + UUID.randomUUID(),
                pWorker.id(),
                paidWorker.id(),
                UUID.randomUUID());

        retryWorker.retryPendingFulfillments();
        assertThat(orderState(paidWorker.id())).isEqualTo("REFUNDED");
        assertThat(grantCount(paidWorker.id())).isZero();
        assertThat(queryExamAccessState(buyer, productWorker.code())).isEqualTo("NONE");
    }

    @Test
    void activeRefundOnCountedPaymentHoldsPaidWhilePartialOrDuplicateRefundAllowsFulfillment() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var productUnknown = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var productProc = fixtures.insertOnSaleProduct("QDA", 200_000L, 2);
        var productPartial = fixtures.insertOnSaleProduct("BCA", 200_000L, 2);
        var productDup = fixtures.insertOnSaleProduct("VACT", 200_000L, 2);
        UUID adminId = UUID.randomUUID();

        // Kịch bản 1: UNKNOWN trên khoản được tính -> giữ PAID, không cấp quyền.
        var orderUnknown = fixtures.insertStubOrder(
                buyer, productUnknown, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pUnknown = insertSucceededPayment(orderUnknown, 1, 200_000L, false, "90060021");
        doThrow(new RuntimeException("Timeout")).when(vnpayGateway).refund(any());
        createApiRefund(adminId, orderUnknown.code(), pUnknown.id(), 200_000L, "UNKNOWN");

        assertThat(fulfillmentService.fulfill(orderUnknown.id())).isFalse();
        assertThat(orderState(orderUnknown.id())).isEqualTo("PAID");
        assertThat(grantCount(orderUnknown.id())).isZero();

        // Kịch bản 2: PROCESSING (94) trên khoản được tính -> giữ PAID, không cấp quyền.
        var orderProc = fixtures.insertStubOrder(
                buyer, productProc, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pProc = insertSucceededPayment(orderProc, 1, 200_000L, false, "90060022");
        doReturn(refundResult("94", "", pProc.txnRef(), 200_000L, "90060022")).when(vnpayGateway).refund(any());
        createApiRefund(adminId, orderProc.code(), pProc.id(), 200_000L, "PROCESSING");

        assertThat(fulfillmentService.fulfill(orderProc.id())).isFalse();
        assertThat(orderState(orderProc.id())).isEqualTo("PAID");
        assertThat(grantCount(orderProc.id())).isZero();

        // Kịch bản 3: Hoàn một phần SUCCEEDED (không còn active) -> vẫn được cấp quyền.
        Mockito.reset(vnpayGateway);
        var orderPartial = fixtures.insertStubOrder(
                buyer, productPartial, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pPartial = insertSucceededPayment(orderPartial, 1, 200_000L, false, "90060023");
        createApiRefund(adminId, orderPartial.code(), pPartial.id(), 50_000L, "SUCCEEDED");

        assertThat(fulfillmentService.fulfill(orderPartial.id())).isTrue();
        assertThat(orderState(orderPartial.id())).isEqualTo("FULFILLED");
        assertThat(grantCount(orderPartial.id())).isEqualTo(1);

        // Kịch bản 4: Hoàn đang UNKNOWN trên khoản TRÙNG (is_duplicate=true) không chặn cấp quyền.
        var orderDup = fixtures.insertStubOrder(
                buyer, productDup, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        insertSucceededPayment(orderDup, 1, 200_000L, false, "90060024");
        var pDup = insertSucceededPayment(orderDup, 2, 200_000L, true, "90060025");
        doThrow(new RuntimeException("Timeout dup")).when(vnpayGateway).refund(any());
        createApiRefund(adminId, orderDup.code(), pDup.id(), 200_000L, "UNKNOWN");

        assertThat(fulfillmentService.fulfill(orderDup.id())).isTrue();
        assertThat(orderState(orderDup.id())).isEqualTo("FULFILLED");
        assertThat(grantCount(orderDup.id())).isEqualTo(1);

        // Chuyển refund UNKNOWN của orderUnknown sang FAILED qua /mark-failed -> worker mở khóa và cấp quyền FULFILLED.
        UUID unknownRefundId = jdbc.queryForObject(
                "SELECT id FROM commerce.refund WHERE payment_id = ?", UUID.class, pUnknown.id());
        mockMvc.perform(post("/api/v1/admin/refunds/" + unknownRefundId + "/mark-failed")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "reason", "Xac nhan VNPAY chua hoan",
                                "evidenceRef", "EV-GATE-UNBLOCK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));

        retryWorker.retryPendingFulfillments();
        assertThat(orderState(orderUnknown.id())).isEqualTo("FULFILLED");
        assertThat(grantCount(orderUnknown.id())).isEqualTo(1);
        assertThat(orderState(orderProc.id())).isEqualTo("PAID");
    }

    private String queryExamAccessState(CommerceTestFixtures.LearnerAccount buyer, String productCode) throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(fixtures.asLearner(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("codes", List.of(productCode)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = mapper.readTree(raw);
        return root.path("items").get(0).path("state").asText();
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

    private void createApiRefund(
            UUID adminId, String orderCode, UUID paymentId, long amountVnd, String expectedState) throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/payments/" + paymentId + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "gate-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "amountVnd", amountVnd,
                                "kind", "API",
                                "reason", "Kiem tra gate cap quyen",
                                "verifiedSystemError", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value(expectedState));
    }

    private String orderState(UUID orderId) {
        return jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId);
    }

    private int grantCount(UUID orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private static RefundResult refundResult(
            String responseCode, String txStatus, String txnRef, long amountVnd, String txnNo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseCode", responseCode);
        raw.put("vnp_TransactionStatus", txStatus);
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new RefundResult(
                true, "req-" + UUID.randomUUID(), responseCode, "Mock " + responseCode,
                txnRef, amountVnd, "NCB", "20261121100500", txnNo, "02", txStatus, "Refund test", raw);
    }
}
