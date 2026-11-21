package vn.unicom.thi360.commerce.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentRetryWorker;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử tiếp tục cấp quyền thủ công {@code POST /api/v1/admin/orders/{code}/fulfill} (TM 9.5, AC-26..AC-28).
 */
@TestPropertySource(properties = "thi360.commerce.scheduling.enabled=true")
class OrderRecoverIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private EntitlementGrantService entitlementGrantService;
    @Autowired private EntitlementPort entitlementPort;
    @Autowired private FulfillmentRetryWorker retryWorker;
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
    void recoversVnpayAndFreePaidOrdersEnforcesPreconditionsValidationAndIdempotency() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var examVnpay = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var examFree = fixtures.insertOnSaleProduct("QDA", 150_000L, 2);
        UUID adminId = UUID.randomUUID();

        // 1. Đơn VNPAY PAID hợp lệ -> cấp quyền, FULFILLED, ghi audit ORDER_FULFILLED_MANUALLY và idempotent replay.
        var vnpOrder = fixtures.insertStubOrder(
                buyer, examVnpay, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        fixtures.insertStubPayment(vnpOrder, 1, 200_000L, PaymentState.SUCCEEDED);

        String recoverKey = "rec-vnp-" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of("reason", "Cap lai sau loi tam thoi"));
        mockMvc.perform(post("/api/v1/admin/orders/" + vnpOrder.code() + "/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", recoverKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andExpect(jsonPath("$.grants.length()").value(1))
                .andExpect(jsonPath("$.grants[0].attempts").value(3));

        // Gửi lại cùng Idempotency-Key -> không cấp thêm lượt.
        mockMvc.perform(post("/api/v1/admin/orders/" + vnpOrder.code() + "/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", recoverKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"));

        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), examVnpay.examId())).isEqualTo(3);

        // Cùng Idempotency-Key nhưng khác body -> 409 IDEMPOTENCY_KEY_REUSED.
        mockMvc.perform(post("/api/v1/admin/orders/" + vnpOrder.code() + "/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", recoverKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "Ly do khac"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy outbox.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ORDER_FULFILLED_MANUALLY' AND target_id = ? AND reason = ?",
                Integer.class, vnpOrder.id().toString(), "Cap lai sau loi tam thoi")).isEqualTo(1);

        // 2. Đơn FREE PAID không cần bản ghi payment -> cấp quyền thành công.
        var freeOrder = fixtures.insertStubOrder(
                buyer, examFree, OrderState.PAID, PaymentMethod.FREE, 0L, null, Instant.now());
        mockMvc.perform(post("/api/v1/admin/orders/" + freeOrder.code() + "/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "rec-free-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "Don 0 dong bi ket"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"));
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), examFree.examId())).isEqualTo(2);

        // 3. Thiếu lý do -> 400 VALIDATION_FAILED; thiếu Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED; đơn không tồn tại -> 404.
        mockMvc.perform(post("/api/v1/admin/orders/" + vnpOrder.code() + "/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "blank-reason-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/orders/" + vnpOrder.code() + "/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        mockMvc.perform(post("/api/v1/admin/orders/DHUNKNOWN000001/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "notfound-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        // 4. Các ca sai điều kiện -> 409 ORDER_RECOVERY_NOT_ALLOWED.
        // (a) Đơn đã FULFILLED
        assertRecoverConflict(adminId, vnpOrder.code());

        // (b) Đơn PENDING_PAYMENT
        var pendingOrder = fixtures.insertStubOrder(
                buyer, examVnpay, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        assertRecoverConflict(adminId, pendingOrder.code());

        // (c) Đơn VNPAY PAID nhưng không có lần thu được tính (chỉ có FAILED hoặc duplicate)
        var noCountedOrder = fixtures.insertStubOrder(
                buyer, examVnpay, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        fixtures.insertStubPayment(noCountedOrder, 1, 200_000L, PaymentState.FAILED);
        assertRecoverConflict(adminId, noCountedOrder.code());

        // (d) Đơn VNPAY PAID nhưng có lần hoàn đang PROCESSING/UNKNOWN
        var activeRefundOrder = fixtures.insertStubOrder(
                buyer, examVnpay, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var activePay = fixtures.insertStubPayment(activeRefundOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        insertRefund(activeRefundOrder.id(), activePay.id(), 200_000L, "UNKNOWN");
        assertRecoverConflict(adminId, activeRefundOrder.code());

        // (e) Đơn VNPAY PAID nhưng đã hoàn đủ khoản được tính
        var fullRefundOrder = fixtures.insertStubOrder(
                buyer, examVnpay, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var fullPay = fixtures.insertStubPayment(fullRefundOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        insertRefund(fullRefundOrder.id(), fullPay.id(), 200_000L, "SUCCEEDED");
        assertRecoverConflict(adminId, fullRefundOrder.code());
    }

    @Test
    void existingGrantOnlySyncsOrderStateAndPreservesAttemptsUsed() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);

        // Mô phỏng grant đã tạo thành công ở lần thử trước nhưng đơn chưa kịp chuyển FULFILLED, và học viên đã dùng 1 lượt.
        var grantedLine = entitlementGrantService.grantForOrder(new GrantOrderCommand(
                order.id(),
                order.code(),
                buyer.id(),
                List.of(new GrantLine(exam.examId(), 1, 4, List.of("OPT-A"), Map.of(), "FULL_SOLUTION"))))
                .lines().getFirst();
        UUID attemptId = UUID.randomUUID();
        entitlementPort.reserve(buyer.id(), grantedLine.entitlementId(), attemptId);
        entitlementPort.confirmUse(attemptId);

        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/fulfill")
                        .with(asUser(UUID.randomUUID(), "ops-sync", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "sync-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "Dong bo trang thai sau loi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andExpect(jsonPath("$.grants.length()").value(1))
                .andExpect(jsonPath("$.grants[0].attemptsUsed").value(1))
                .andExpect(jsonPath("$.grants[0].attemptsLeft").value(3));

        Map<String, Object> ent = jdbc.queryForMap(
                "SELECT attempts_total, attempts_used FROM commerce.entitlement WHERE id = ?",
                grantedLine.entitlementId());
        assertThat(((Number) ent.get("attempts_total")).intValue()).isEqualTo(4);
        assertThat(((Number) ent.get("attempts_used")).intValue()).isEqualTo(1);
    }

    @Test
    void concurrentAdminsAndRetryWorkerGrantExactlyOnce() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 5);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = List.of(
                () -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return callFulfill(UUID.randomUUID(), "admin-1", order.code());
                },
                () -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return callFulfill(UUID.randomUUID(), "admin-2", order.code());
                },
                () -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    retryWorker.retryPendingFulfillments();
                    return 0;
                });

        List<Integer> adminStatuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (int i = 0; i < 2; i++) {
                adminStatuses.add(futures.get(i).get(30, TimeUnit.SECONDS));
            }
            futures.get(2).get(30, TimeUnit.SECONDS);
        }

        // Tất cả phản hồi HTTP admin chỉ có thể là 200 (thắng) hoặc 409 (kẻ đến sau thấy đã FULFILLED), tối đa 1 admin 200.
        assertThat(adminStatuses).allMatch(s -> s == 200 || s == 409);
        assertThat(adminStatuses.stream().filter(s -> s == 200).count()).isLessThanOrEqualTo(1L);

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), exam.examId())).isEqualTo(5);
    }

    private int callFulfill(UUID adminId, String username, String orderCode) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/fulfill")
                        .with(asUser(adminId, username, Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "race-" + username + "-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "Dong thoi"))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void assertRecoverConflict(UUID adminId, String orderCode) throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/fulfill")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "conf-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "Kiem tra dieu kien"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_RECOVERY_NOT_ALLOWED"));
    }

    private void insertRefund(UUID orderId, UUID paymentId, long amountVnd, String state) {
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'API', ?, 'Test recover gate', true, 'ops', ?, now(), now(), now(), 0)
                """,
                UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                "rec-ref-" + UUID.randomUUID(),
                paymentId,
                orderId,
                amountVnd,
                state,
                UUID.randomUUID());
    }
}
