package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
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
 * Kiểm thử tích hợp cho {@code mark-failed} và ghi nhận hoàn thủ công {@code MANUAL} (AC-16, AC-17, AC-18).
 */
class RefundManualIT extends AbstractIntegrationTest {

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
    void markFailedAllowedOnlyFromUnknownAndRequiresReasonAndEvidence() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        UUID adminId = UUID.randomUUID();

        var orderUnknown = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pUnknown = insertSucceededPayment(orderUnknown, 1, 200_000L, "90040011");

        doThrow(new RuntimeException("Read timed out")).when(vnpayGateway).refund(any());
        String unknownId = createRefund(adminId, orderUnknown.code(), pUnknown.id(), 200_000L, "API", "Timeout", null, "UNKNOWN");

        // Thiếu lý do hoặc chứng từ -> 400 VALIDATION_FAILED.
        mockMvc.perform(post("/api/v1/admin/refunds/" + unknownId + "/mark-failed")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "", "evidenceRef", "TICKET-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/refunds/" + unknownId + "/mark-failed")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "reason", "Xac nhan giao dich khong ton tai tren portal VNPAY",
                                "evidenceRef", "VNPAY-TICKET-2026-01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.markFailedEvidence").value("VNPAY-TICKET-2026-01"));

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'REFUND_FAILED' AND target_id = ?",
                Integer.class, unknownId)).isEqualTo(1);

        // Không được mark-failed từ FAILED, PROCESSING hoặc SUCCEEDED -> 409 REFUND_NOT_UNKNOWN.
        mockMvc.perform(post("/api/v1/admin/refunds/" + unknownId + "/mark-failed")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "Lap lai", "evidenceRef", "TICKET-2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_UNKNOWN"));

        var orderProc = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pProc = insertSucceededPayment(orderProc, 1, 200_000L, "90040012");
        doReturn(refundResult("94", "", pProc.txnRef(), 200_000L, "90040012")).when(vnpayGateway).refund(any());
        String procId = createRefund(adminId, orderProc.code(), pProc.id(), 200_000L, "API", "94", null, "PROCESSING");

        mockMvc.perform(post("/api/v1/admin/refunds/" + procId + "/mark-failed")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "Khong cho", "evidenceRef", "TICKET-3"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_UNKNOWN"));
    }

    @Test
    void manualRefundRequiresPriorFailedApiAndExternalRef() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = insertSucceededPayment(order, 1, 200_000L, "90040021");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        UUID adminId = UUID.randomUUID();
        // Chưa từng có yêu cầu API FAILED -> 409 REFUND_MANUAL_REQUIRES_FAILED_API.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "man-no-fail-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "MANUAL", "Thu cong som", true, "VCB-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_MANUAL_REQUIRES_FAILED_API"));

        // Ghi nhận 1 lần API FAILED (mã 91).
        doReturn(refundResult("91", "09", payment.txnRef(), 200_000L, "90040021")).when(vnpayGateway).refund(any());
        createRefund(adminId, order.code(), payment.id(), 200_000L, "API", "API bi 91", null, "FAILED");

        // Ghi nhận MANUAL thiếu externalRef -> 422 REFUND_EXTERNAL_REF_REQUIRED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "man-no-ref-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "MANUAL", "Thieu ma chung tu", true, "   ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_EXTERNAL_REF_REQUIRED"));

        // Ghi nhận MANUAL hợp lệ -> 201 SUCCEEDED, không gọi VNPAY, thu hồi quyền và chuyển REFUNDED.
        String manualId = createRefund(
                adminId, order.code(), payment.id(), 200_000L, "MANUAL",
                "Chuyen khoan truc tiep cho hoc vien", "VCB-FT-20261121-999", "SUCCEEDED");
        verify(vnpayGateway, times(1)).refund(any());

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_revoked FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId()))
                .isEqualTo(3);

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'REFUND_RECORDED_MANUALLY' AND target_id = ?",
                Integer.class, manualId)).isEqualTo(1);
    }

    @Test
    void manualRefundBlockedWhenActiveUnknownOrProcessingExistsEvenAfterFailedApi() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        UUID adminId = UUID.randomUUID();

        // Kịch bản 1: API #1 FAILED -> API #2 UNKNOWN -> MANUAL phải bị chặn 409 REFUND_ACTIVE_EXISTS.
        var order1 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var p1 = insertSucceededPayment(order1, 1, 200_000L, "90040031");
        doReturn(refundResult("91", "09", p1.txnRef(), 200_000L, "90040031")).when(vnpayGateway).refund(any());
        createRefund(adminId, order1.code(), p1.id(), 200_000L, "API", "Lan 1 fail 91", null, "FAILED");

        doThrow(new RuntimeException("Timeout")).when(vnpayGateway).refund(any());
        createRefund(adminId, order1.code(), p1.id(), 200_000L, "API", "Lan 2 unknown", null, "UNKNOWN");

        mockMvc.perform(post("/api/v1/admin/orders/" + order1.code() + "/payments/" + p1.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "man-blk-unk-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "MANUAL", "Thu cong khi con UNKNOWN", true, "VCB-888")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_ACTIVE_EXISTS"));

        // Kịch bản 2: API #1 FAILED -> API #2 PROCESSING (94) -> MANUAL bị chặn 409 REFUND_ACTIVE_EXISTS.
        var order2 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var p2 = insertSucceededPayment(order2, 1, 200_000L, "90040032");
        doReturn(refundResult("91", "09", p2.txnRef(), 200_000L, "90040032")).when(vnpayGateway).refund(any());
        createRefund(adminId, order2.code(), p2.id(), 200_000L, "API", "Lan 1 fail 91", null, "FAILED");

        doReturn(refundResult("94", "", p2.txnRef(), 200_000L, "90040032")).when(vnpayGateway).refund(any());
        createRefund(adminId, order2.code(), p2.id(), 200_000L, "API", "Lan 2 processing", null, "PROCESSING");

        mockMvc.perform(post("/api/v1/admin/orders/" + order2.code() + "/payments/" + p2.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "man-blk-proc-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "MANUAL", "Thu cong khi con PROCESSING", true, "VCB-999")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_ACTIVE_EXISTS"));
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

    private String createRefund(
            UUID adminId, String orderCode, UUID paymentId, long amountVnd, String kind,
            String reason, String externalRef, String expectedState) throws Exception {
        String raw = mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/payments/" + paymentId + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "man-cr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(amountVnd, kind, reason, true, externalRef)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value(expectedState))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(raw).path("id").asText();
    }

    private String refundBody(
            long amountVnd, String kind, String reason, boolean verifiedSystemError, String externalRef)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amountVnd", amountVnd);
        payload.put("kind", kind);
        payload.put("reason", reason);
        payload.put("verifiedSystemError", verifiedSystemError);
        if (externalRef != null) {
            payload.put("externalRef", externalRef);
        }
        return mapper.writeValueAsString(payload);
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
