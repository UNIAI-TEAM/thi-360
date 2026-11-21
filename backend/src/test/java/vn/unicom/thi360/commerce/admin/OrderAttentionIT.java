package vn.unicom.thi360.commerce.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.admin.AdminTestClockConfig.AdminMutableClock;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Kiểm thử 6 nhóm dải "Cần xử lý" (TM 9.1, BS §14, AC-4), ranh giới đúng 24 giờ qua {@link AdminMutableClock},
 * và hành vi tiền trùng rời khỏi dải sau khi đã hoàn đủ.
 */
@Import(AdminTestClockConfig.class)
class OrderAttentionIT extends AbstractIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private AdminMutableClock clock;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        clock.fixAt(FIXED_NOW);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @Test
    void countsAndFiltersAllSixAttentionBucketsRespectsExact24hBoundaryAndClearsRefundedDuplicate() throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var buyer3 = fixtures.createLearner();
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        UUID adminId = UUID.randomUUID();

        // 1. LATE_SUCCESS_PENDING
        var oLate = fixtures.insertStubOrder(
                buyer1, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, FIXED_NOW.plusSeconds(600), null);
        var pLate = fixtures.insertStubPayment(oLate, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = ? WHERE id = ?",
                Timestamp.from(FIXED_NOW.minusSeconds(60)), pLate.id());

        // 2. PAID_UNFULFILLED
        var oPaid = fixtures.insertStubOrder(
                buyer1, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, FIXED_NOW);

        // 3. PAYMENT_UNKNOWN_OVER_24H: đặt đúng mốc 24h00m00s trước FIXED_NOW -> CHƯA tính!
        var oUnknown = fixtures.insertStubOrder(
                buyer2, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, FIXED_NOW.plusSeconds(600), null);
        var pUnknown = fixtures.insertStubPayment(oUnknown, 1, 200_000L, PaymentState.UNKNOWN);
        Instant exact24hAgo = FIXED_NOW.minus(Duration.ofHours(24));
        jdbc.update("UPDATE commerce.payment SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(exact24hAgo), Timestamp.from(exact24hAgo), pUnknown.id());

        // 4. DUPLICATE_UNREFUNDED (cộng 1 đơn trùng đã hoàn một phần vẫn còn trong dải; sau đó hoàn đủ sẽ rời dải).
        var oDup = fixtures.insertStubOrder(
                buyer1, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, FIXED_NOW);
        fixtures.insertStubPayment(oDup, 1, 200_000L, PaymentState.SUCCEEDED);
        var pDup = fixtures.insertStubPayment(oDup, 2, 200_000L, PaymentState.CREATED);
        jdbc.update("""
                UPDATE commerce.payment
                SET state = 'SUCCEEDED', is_duplicate = true, excess_reason = 'AFTER_COUNTED'
                WHERE id = ?
                """, pDup.id());
        insertRefund(oDup.id(), pDup.id(), 50_000L, "SUCCEEDED");

        // 5. NEEDS_REVIEW
        var oReview = fixtures.insertStubOrder(
                buyer3, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, FIXED_NOW);
        jdbc.update("UPDATE commerce.purchase_order SET needs_review = true, review_reason = 'DOUBLE_CHARGE_REPLACED_ORDER' WHERE id = ?",
                oReview.id());

        // 6. REFUND_UNKNOWN
        var oRefundUnk = fixtures.insertStubOrder(
                buyer3, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, FIXED_NOW);
        var pRefUnk = fixtures.insertStubPayment(oRefundUnk, 1, 200_000L, PaymentState.SUCCEEDED);
        insertRefund(oRefundUnk.id(), pRefUnk.id(), 200_000L, "UNKNOWN");

        // Kiểm tra tại đúng 24h00m00s: paymentUnknownOver24h == 0, totalAttentionOrders == 5.
        mockMvc.perform(get("/api/v1/admin/orders/attention")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lateSuccessPending").value(1))
                .andExpect(jsonPath("$.paidUnfulfilled").value(1))
                .andExpect(jsonPath("$.paymentUnknownOver24h").value(0))
                .andExpect(jsonPath("$.duplicateUnrefunded").value(1))
                .andExpect(jsonPath("$.needsReview").value(1))
                .andExpect(jsonPath("$.refundUnknown").value(1))
                .andExpect(jsonPath("$.totalAttentionOrders").value(5));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("attention", "PAYMENT_UNKNOWN_OVER_24H"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        // Tua đồng hồ thêm 1 giây -> thành 24h00m01s -> ĐƯỢC tính vào dải PAYMENT_UNKNOWN_OVER_24H!
        clock.advance(Duration.ofSeconds(1));
        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("attention", "PAYMENT_UNKNOWN_OVER_24H"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(oUnknown.code()))
                .andExpect(jsonPath("$.items[0].attentionTypes[0]").value("PAYMENT_UNKNOWN_OVER_24H"))
                .andExpect(jsonPath("$.attention.paymentUnknownOver24h").value(1))
                .andExpect(jsonPath("$.attention.totalAttentionOrders").value(6));

        // Kiểm tra lọc đúng từng loại còn lại.
        assertAttentionFilter(adminId, "LATE_SUCCESS_PENDING", oLate.code());
        assertAttentionFilter(adminId, "PAID_UNFULFILLED", oPaid.code());
        assertAttentionFilter(adminId, "DUPLICATE_UNREFUNDED", oDup.code());
        assertAttentionFilter(adminId, "NEEDS_REVIEW", oReview.code());
        assertAttentionFilter(adminId, "REFUND_UNKNOWN", oRefundUnk.code());

        // Hoàn đủ phần còn lại (150_000 VND) trên tiền trùng -> rời dải DUPLICATE_UNREFUNDED!
        insertRefund(oDup.id(), pDup.id(), 150_000L, "SUCCEEDED");
        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("attention", "DUPLICATE_UNREFUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.attention.duplicateUnrefunded").value(0))
                .andExpect(jsonPath("$.attention.totalAttentionOrders").value(5));

        String dupCheckRaw = mockMvc.perform(get("/api/v1/admin/orders/" + oDup.code())
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode dupDetail = mapper.readTree(dupCheckRaw);
        assertThat(dupDetail.path("attentionTypes")).isEmpty();
    }

    private void assertAttentionFilter(UUID adminId, String attentionType, String expectedCode) throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("attention", attentionType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(expectedCode));
    }

    private void insertRefund(UUID orderId, UUID paymentId, long amountVnd, String state) {
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'API', ?, 'Test attention refund', true, 'ops', ?, now(), now(), now(), 0)
                """,
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                "attn-" + UUID.randomUUID(),
                paymentId,
                orderId,
                amountVnd,
                state,
                UUID.randomUUID());
    }
}
