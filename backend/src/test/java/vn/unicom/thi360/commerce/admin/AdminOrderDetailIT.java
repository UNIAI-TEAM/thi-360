package vn.unicom.thi360.commerce.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử chi tiết đơn hàng quản trị {@code GET /api/v1/admin/orders/{code}} (TM 9.2, AC-5).
 */
class AdminOrderDetailIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private SecurityProperties securityProperties;

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
    void returnsCompleteSnapshotPaymentsRefundsGrantsHistoryAndAuditEventsAnd404OnUnknownCode() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90088002', bank_code = 'NCB', card_type = 'ATM',
                    pay_date = '20261121100500', response_code = '00', transaction_status = '00'
                WHERE id = ?
                """, p2.id());

        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        // Tạo 1 lần hoàn một phần 60_000 VND (SUCCEEDED) và 1 lần hoàn đang PROCESSING 40_000 VND trên p2.
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p2.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "dtl-ref-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "amountVnd", 60_000L,
                                "kind", "API",
                                "reason", "Hoan mot phan chi tiet",
                                "verifiedSystemError", true))))
                .andExpect(status().isCreated());

        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 40000, 'API', 'PROCESSING', 'Dang cho 94', true, 'ops-admin', ?,
                        now(), now(), now(), 0)
                """,
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                "dtl-ref-proc-" + UUID.randomUUID(),
                p2.id(),
                order.id(),
                adminId);

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy sự kiện ENTITLEMENT_GRANTED vào audit.audit_event.
        }

        String raw = mockMvc.perform(get("/api/v1/admin/orders/" + order.code().toLowerCase())
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.id().toString()))
                .andExpect(jsonPath("$.code").value(order.code()))
                .andExpect(jsonPath("$.buyer.userId").value(buyer.id().toString()))
                .andExpect(jsonPath("$.buyer.username").value(buyer.username()))
                .andExpect(jsonPath("$.buyer.fullName").value(buyer.fullName()))
                .andExpect(jsonPath("$.productType").value("EXAM"))
                .andExpect(jsonPath("$.examId").value(exam.examId().toString()))
                .andExpect(jsonPath("$.productCode").value(exam.code()))
                .andExpect(jsonPath("$.productName").value(exam.name()))
                .andExpect(jsonPath("$.listPriceVnd").value(200_000L))
                .andExpect(jsonPath("$.amountDueVnd").value(200_000L))
                .andExpect(jsonPath("$.attemptsGranted").value(3))
                .andExpect(jsonPath("$.paymentMethod").value("VNPAY"))
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.refundStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].id").value(p1.id().toString()))
                .andExpect(jsonPath("$.payments[0].attemptSeq").value(1))
                .andExpect(jsonPath("$.payments[0].refundableAmountVnd").value(0L))
                .andExpect(jsonPath("$.payments[1].id").value(p2.id().toString()))
                .andExpect(jsonPath("$.payments[1].attemptSeq").value(2))
                .andExpect(jsonPath("$.payments[1].vnpTransactionNo").value("90088002"))
                .andExpect(jsonPath("$.payments[1].refundedAmountVnd").value(60_000L))
                .andExpect(jsonPath("$.payments[1].inProgressRefundAmountVnd").value(40_000L))
                .andExpect(jsonPath("$.payments[1].refundableAmountVnd").value(100_000L))
                .andExpect(jsonPath("$.payments[1].refunds.length()").value(2))
                .andExpect(jsonPath("$.grants.length()").value(1))
                .andExpect(jsonPath("$.grants[0].attempts").value(3))
                .andExpect(jsonPath("$.grants[0].attemptsLeft").value(3))
                .andExpect(jsonPath("$.grants[0].grantedBy").value("SYSTEM"))
                .andExpect(jsonPath("$.orderHistory.length()").value(1))
                .andExpect(jsonPath("$.orderHistory[0].toState").value("FULFILLED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = mapper.readTree(raw);
        assertThat(root.path("auditEvents").size()).isGreaterThanOrEqualTo(1);
        assertThat(root.path("auditEvents").get(0).path("action").asText()).isEqualTo("ENTITLEMENT_GRANTED");

        // Mã đơn không tồn tại -> 404 ORDER_NOT_FOUND.
        mockMvc.perform(get("/api/v1/admin/orders/DHUNKNOWN999999")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
