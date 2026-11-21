package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử tích hợp cho {@code POST /api/v1/admin/refunds/{id}/query} (AC-15).
 */
class RefundQueryIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;
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
    void queryOnUnknownResolvesSucceededRevokesOnceAndRepeat06IsIdempotent() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = insertSucceededPayment(order, 1, 200_000L, "90030011");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        doThrow(new RuntimeException("SocketTimeoutException")).when(vnpayGateway).refund(any());
        UUID adminId = UUID.randomUUID();
        String refundId = createApiRefundExpectState(adminId, order.code(), payment.id(), 200_000L, "UNKNOWN");

        AtomicReference<Boolean> txActiveDuringQuery = new AtomicReference<>();
        doAnswer(inv -> {
            txActiveDuringQuery.set(TransactionSynchronizationManager.isActualTransactionActive());
            return queryDrResult(true, "00", "05", payment.txnRef(), 200_000L, "90030011");
        }).when(vnpayGateway).queryDr(any());

        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/query")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.vnpResponseCode").value("00"))
                .andExpect(jsonPath("$.vnpTransactionStatus").value("05"));

        assertThat(txActiveDuringQuery.get()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_revoked FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId()))
                .isEqualTo(3);

        while (outboxRelay.relayBatch() > 0) {
            // Xử lý outbox.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared.email_message WHERE user_id = ? AND kind = 'REFUND_DONE'",
                Integer.class, buyer.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'REFUND_SUCCEEDED' AND target_id = ?",
                Integer.class, refundId)).isEqualTo(1);

        // Truy vấn lặp lại trả 00/06 -> giữ nguyên SUCCEEDED, cập nhật transactionStatus=06, không nhân bản thư/audit.
        doReturn(queryDrResult(true, "00", "06", payment.txnRef(), 200_000L, "90030011"))
                .when(vnpayGateway).queryDr(any());
        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/query")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.vnpTransactionStatus").value("06"));

        while (outboxRelay.relayBatch() > 0) {
            // Xử lý outbox.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared.email_message WHERE user_id = ? AND kind = 'REFUND_DONE'",
                Integer.class, buyer.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'REFUND_SUCCEEDED' AND target_id = ?",
                Integer.class, refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND to_state = 'REFUNDED'",
                Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    void query0009MarksFailedFlagsOrderNeedsReviewWithoutRegrantAndBlocksFurtherQuery() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = insertSucceededPayment(order, 1, 200_000L, "90030022");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        doThrow(new RuntimeException("Gateway timeout")).when(vnpayGateway).refund(any());
        UUID adminId = UUID.randomUUID();
        String refundId = createApiRefundExpectState(adminId, order.code(), payment.id(), 200_000L, "UNKNOWN");

        doReturn(queryDrResult(true, "00", "09", payment.txnRef(), 200_000L, "90030022"))
                .when(vnpayGateway).queryDr(any());

        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/query")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.vnpTransactionStatus").value("09"));

        Map<String, Object> orderRow = jdbc.queryForMap(
                "SELECT state, needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", order.id());
        assertThat(orderRow.get("state")).isEqualTo("FULFILLED");
        assertThat(orderRow.get("needs_review")).isEqualTo(true);
        assertThat(orderRow.get("review_reason")).isEqualTo("REFUND_REJECTED_09:" + refundId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND reason = ?",
                Integer.class, order.id(), "REFUND_REJECTED_09:" + refundId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isEqualTo(1);

        // Truy vấn lại trên refund đã FAILED -> 409 REFUND_QUERY_NOT_ALLOWED.
        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/query")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_QUERY_NOT_ALLOWED"));
    }

    @Test
    void queryWorksOnProcessingAndRejectsManualRefund() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order1 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var p1 = insertSucceededPayment(order1, 1, 200_000L, "90030033");

        doReturn(refundResult(true, "94", "", p1.txnRef(), 200_000L, "90030033"))
                .when(vnpayGateway).refund(any());
        UUID adminId = UUID.randomUUID();
        String procRefundId = createApiRefundExpectState(adminId, order1.code(), p1.id(), 200_000L, "PROCESSING");

        doReturn(queryDrResult(true, "00", "05", p1.txnRef(), 200_000L, "90030033"))
                .when(vnpayGateway).queryDr(any());
        mockMvc.perform(post("/api/v1/admin/refunds/" + procRefundId + "/query")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));

        // Tạo cặp API FAILED + MANUAL rồi thử gọi /query trên MANUAL -> 409 REFUND_QUERY_NOT_ALLOWED.
        var order2 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var p2 = insertSucceededPayment(order2, 1, 200_000L, "90030044");
        doReturn(refundResult(true, "91", "09", p2.txnRef(), 200_000L, "90030044"))
                .when(vnpayGateway).refund(any());
        createApiRefundExpectState(adminId, order2.code(), p2.id(), 200_000L, "FAILED");

        String manualBody = mapper.writeValueAsString(Map.of(
                "amountVnd", 200_000L,
                "kind", "MANUAL",
                "reason", "Chuyen khoan hoan thu cong",
                "verifiedSystemError", true,
                "externalRef", "VCB-MANUAL-33"));
        String manualRaw = mockMvc.perform(post("/api/v1/admin/orders/" + order2.code()
                        + "/payments/" + p2.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "man-q-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("MANUAL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String manualRefundId = mapper.readTree(manualRaw).path("id").asText();

        mockMvc.perform(post("/api/v1/admin/refunds/" + manualRefundId + "/query")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_QUERY_NOT_ALLOWED"));
    }

    private CommerceTestFixtures.StubPayment insertSucceededPayment(
            CommerceTestFixtures.StubOrder order, int seq, long amountVnd, String txnNo) {
        var payment = fixtures.insertStubPayment(order, seq, amountVnd, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = ?, bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, txnNo, payment.id());
        return payment;
    }

    private String createApiRefundExpectState(
            UUID adminId, String orderCode, UUID paymentId, long amountVnd, String expectedState) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "amountVnd", amountVnd,
                "kind", "API",
                "reason", "Kiem tra querydr",
                "verifiedSystemError", true));
        String raw = mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/payments/" + paymentId + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "q-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value(expectedState))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(raw).path("id").asText();
    }

    private static RefundResult refundResult(
            boolean signatureValid, String responseCode, String txStatus, String txnRef, long amountVnd, String txnNo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseCode", responseCode);
        raw.put("vnp_TransactionStatus", txStatus);
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new RefundResult(
                signatureValid, "req-" + UUID.randomUUID(), responseCode, "Refund " + responseCode,
                txnRef, amountVnd, "NCB", "20261121100500", txnNo, "02", txStatus, "Refund test", raw);
    }

    private static QueryDrResult queryDrResult(
            boolean signatureValid, String responseCode, String txStatus, String txnRef, long amountVnd, String txnNo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseCode", responseCode);
        raw.put("vnp_TransactionStatus", txStatus);
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new QueryDrResult(
                signatureValid, "qreq-" + UUID.randomUUID(), responseCode, "QueryDr " + responseCode,
                txnRef, amountVnd, "NCB", "20261121100500", txnNo, "02", txStatus, "QueryDr test", "", "", raw);
    }
}
