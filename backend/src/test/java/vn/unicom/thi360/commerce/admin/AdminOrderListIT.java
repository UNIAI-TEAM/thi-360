package vn.unicom.thi360.commerce.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Kiểm thử danh sách đơn hàng quản trị: cột, sắp xếp mặc định mới nhất trước, tìm kiếm {@code q},
 * bộ lọc ({@code examId}, {@code from/to}, {@code paymentStatus}, {@code refundStatus}, {@code paymentMethod}),
 * và suy diễn trạng thái thanh toán / hoàn tiền theo TM 8.1 & 9.1 (AC-2, AC-3).
 */
class AdminOrderListIT extends AbstractIntegrationTest {

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
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @Test
    void listsOrdersNewestFirstWithRequiredColumnsSearchAndFilters() throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var examA = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var examB = fixtures.insertOnSaleProduct("QDA", 150_000L, 1);

        Instant tBase = Instant.parse("2026-11-20T10:00:00Z");

        var o1 = fixtures.insertStubOrder(
                buyer1, examA, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, tBase.plusSeconds(30));
        setOrderTimestamps(o1.id(), tBase);
        var p1 = fixtures.insertStubPayment(o1, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("UPDATE commerce.payment SET vnp_transaction_no = 'VNP99110011' WHERE id = ?", p1.id());

        var o2 = fixtures.insertStubOrder(
                buyer2, examB, OrderState.PAID, PaymentMethod.FREE, 0L, null, tBase.plusSeconds(3600));
        setOrderTimestamps(o2.id(), tBase.plusSeconds(3600));

        var o3 = fixtures.insertStubOrder(
                buyer2, examA, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, tBase.plusSeconds(8100), null);
        setOrderTimestamps(o3.id(), tBase.plusSeconds(7200));
        var p3 = fixtures.insertStubPayment(o3, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET vnp_transaction_no = 'VNP99330033' WHERE id = ?", p3.id());

        UUID adminId = UUID.randomUUID();

        // 1. Mặc định sắp xếp mới nhất trước và trả đủ cột TM 9.1.
        String listRaw = mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("from", tBase.minusSeconds(1).toString())
                        .param("to", tBase.plusSeconds(7201).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].orderId").value(o3.id().toString()))
                .andExpect(jsonPath("$.items[0].code").value(o3.code()))
                .andExpect(jsonPath("$.items[0].buyerUserId").value(buyer2.id().toString()))
                .andExpect(jsonPath("$.items[0].buyerUsername").value(buyer2.username()))
                .andExpect(jsonPath("$.items[0].buyerFullName").value(buyer2.fullName()))
                .andExpect(jsonPath("$.items[0].productType").value("EXAM"))
                .andExpect(jsonPath("$.items[0].examId").value(examA.examId().toString()))
                .andExpect(jsonPath("$.items[0].productCode").value(examA.code()))
                .andExpect(jsonPath("$.items[0].productName").value(examA.name()))
                .andExpect(jsonPath("$.items[0].listPriceVnd").value(200_000L))
                .andExpect(jsonPath("$.items[0].amountDueVnd").value(200_000L))
                .andExpect(jsonPath("$.items[0].paymentMethod").value("VNPAY"))
                .andExpect(jsonPath("$.items[0].orderState").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.items[0].refundStatus").value("NONE"))
                .andExpect(jsonPath("$.items[0].needsReview").value(false))
                .andExpect(jsonPath("$.items[0].attentionTypes").isArray())
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].code").value(o2.code()))
                .andExpect(jsonPath("$.items[2].code").value(o1.code()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(mapper.readTree(listRaw).path("items").size()).isEqualTo(3);

        // 2. Tìm kiếm q không phân biệt hoa thường theo code, username, txnRef, vnpTransactionNo.
        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("q", o1.code().toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(o1.code()));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("q", buyer1.username().toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(o1.code()));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("q", p3.txnRef().toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(o3.code()));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("q", "vnp99110011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(o1.code()));

        // 3. Lọc theo examId (khớp qua purchase_order_item), khoảng thời gian from/to và paymentMethod.
        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("examId", examA.examId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].code").value(o3.code()))
                .andExpect(jsonPath("$.items[1].code").value(o1.code()));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("from", tBase.plusSeconds(3500).toString())
                        .param("to", tBase.plusSeconds(3700).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(o2.code()));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param("paymentMethod", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(o2.code()))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("FREE"));
    }

    @Test
    void derivesPaymentStatusByPriorityAndRefundStatusAcrossAllStates() throws Exception {
        var buyer = fixtures.createLearner();
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        UUID adminId = UUID.randomUUID();
        Instant base = Instant.now().minus(2, ChronoUnit.DAYS);

        // A. Thứ tự ưu tiên paymentStatus (TM 8.1): SUCCEEDED thắng PENDING và FAILED.
        var orderPaySucceeded = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, base);
        fixtures.insertStubPayment(orderPaySucceeded, 1, 200_000L, PaymentState.FAILED);
        fixtures.insertStubPayment(orderPaySucceeded, 2, 200_000L, PaymentState.UNKNOWN);
        fixtures.insertStubPayment(orderPaySucceeded, 3, 200_000L, PaymentState.SUCCEEDED);

        // B. PENDING (CREATED hoặc UNKNOWN) thắng FAILED khi chưa có SUCCEEDED.
        var orderPayPending = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, base.plusSeconds(900), null);
        fixtures.insertStubPayment(orderPayPending, 1, 200_000L, PaymentState.FAILED);
        fixtures.insertStubPayment(orderPayPending, 2, 200_000L, PaymentState.UNKNOWN);

        // C. FAILED khi mọi lần đều thất bại.
        var orderPayFailed = fixtures.insertStubOrder(
                buyer, exam, OrderState.EXPIRED, PaymentMethod.VNPAY, 200_000L, base.plusSeconds(900), null);
        fixtures.insertStubPayment(orderPayFailed, 1, 200_000L, PaymentState.FAILED);

        // D. FREE khi đơn miễn phí không có dòng payment.
        var orderPayFree = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.FREE, 0L, null, base);

        // E. refundStatus: NONE, IN_PROGRESS (PROCESSING/UNKNOWN), PARTIAL, FULL (kể cả khi tổng thu > 1 lần SUCCEEDED).
        var orderRefundPartial = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, base);
        var pPartial = fixtures.insertStubPayment(orderRefundPartial, 1, 200_000L, PaymentState.SUCCEEDED);
        insertRefund(orderRefundPartial.id(), pPartial.id(), 50_000L, "SUCCEEDED");
        insertRefund(orderRefundPartial.id(), pPartial.id(), 50_000L, "FAILED");

        var orderRefundInProgress = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, base);
        var pInProg = fixtures.insertStubPayment(orderRefundInProgress, 1, 200_000L, PaymentState.SUCCEEDED);
        insertRefund(orderRefundInProgress.id(), pInProg.id(), 40_000L, "SUCCEEDED");
        insertRefund(orderRefundInProgress.id(), pInProg.id(), 60_000L, "UNKNOWN");

        var orderRefundFull = fixtures.insertStubOrder(
                buyer, exam, OrderState.REFUNDED, PaymentMethod.VNPAY, 200_000L, null, base);
        var pFull = fixtures.insertStubPayment(orderRefundFull, 1, 200_000L, PaymentState.SUCCEEDED);
        insertRefund(orderRefundFull.id(), pFull.id(), 200_000L, "SUCCEEDED");

        // Kiểm tra lọc theo từng paymentStatus & refundStatus.
        assertFilterReturnsCodes(adminId, "paymentStatus", "SUCCEEDED", List.of(
                orderPaySucceeded.code(), orderRefundPartial.code(),
                orderRefundInProgress.code(), orderRefundFull.code()));
        assertFilterReturnsCodes(adminId, "paymentStatus", "PENDING", List.of(orderPayPending.code()));
        assertFilterReturnsCodes(adminId, "paymentStatus", "FAILED", List.of(orderPayFailed.code()));
        assertFilterReturnsCodes(adminId, "paymentStatus", "FREE", List.of(orderPayFree.code()));

        assertFilterReturnsCodes(adminId, "refundStatus", "NONE", List.of(
                orderPaySucceeded.code(), orderPayPending.code(), orderPayFailed.code(), orderPayFree.code()));
        assertFilterReturnsCodes(adminId, "refundStatus", "IN_PROGRESS", List.of(orderRefundInProgress.code()));
        assertFilterReturnsCodes(adminId, "refundStatus", "PARTIAL", List.of(orderRefundPartial.code()));
        assertFilterReturnsCodes(adminId, "refundStatus", "FULL", List.of(orderRefundFull.code()));
    }

    private void assertFilterReturnsCodes(
            UUID adminId, String paramName, String paramValue, List<String> expectedCodes) throws Exception {
        String raw = mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(adminId, "viewer", Permissions.ORDER_VIEW))
                        .param(paramName, paramValue)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode items = mapper.readTree(raw).path("items");
        List<String> actualCodes = new ArrayList<>();
        for (JsonNode item : items) {
            actualCodes.add(item.path("code").asText());
        }
        assertThat(actualCodes).containsExactlyInAnyOrderElementsOf(expectedCodes);
    }

    private void setOrderTimestamps(UUID orderId, Instant createdAt) {
        jdbc.update("UPDATE commerce.purchase_order SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(createdAt), Timestamp.from(createdAt), orderId);
    }

    private void insertRefund(UUID orderId, UUID paymentId, long amountVnd, String state) {
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'API', ?, 'Test refund', true, 'ops', ?, now(), now(), now(), 0)
                """,
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                "list-it-" + UUID.randomUUID(),
                paymentId,
                orderId,
                amountVnd,
                state,
                UUID.randomUUID());
    }
}
