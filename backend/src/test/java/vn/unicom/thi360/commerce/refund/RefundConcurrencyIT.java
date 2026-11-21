package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
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
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử đồng thời và ranh giới giao dịch 2 pha của hoàn tiền API (AC-10, AC-14).
 */
class RefundConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private PlatformTransactionManager transactionManager;

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
    void tenConcurrentRefundRequestsCreateAtMostOneAndNeverExceedPaidAmount() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90020011', bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, payment.id());

        doAnswer(inv -> {
            Thread.sleep(40L);
            return inv.callRealMethod();
        }).when(vnpayGateway).refund(any());

        int threads = 10;
        UUID adminId = UUID.randomUUID();
        String payload = mapper.writeValueAsString(Map.of(
                "amountVnd", 150_000L,
                "kind", "API",
                "reason", "Hoan dong thoi 10 luong",
                "verifiedSystemError", true));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final String idemKey = "conc-" + i + "-" + UUID.randomUUID();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                                    + "/payments/" + payment.id() + "/refunds")
                                    .with(asUser(adminId, "conc-admin", Permissions.ORDER_REFUND))
                                    .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                                    .header("Idempotency-Key", idemKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1L);
            assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(threads - 1L);
        } finally {
            pool.shutdownNow();
        }

        Integer succeededCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.refund WHERE payment_id = ? AND state = 'SUCCEEDED'",
                Integer.class, payment.id());
        Long succeededSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_vnd), 0) FROM commerce.refund WHERE payment_id = ? AND state = 'SUCCEEDED'",
                Long.class, payment.id());
        assertThat(succeededCount).isEqualTo(1);
        assertThat(succeededSum).isEqualTo(150_000L).isLessThanOrEqualTo(200_000L);
    }

    @Test
    void gatewayCallExecutesOutsideDbTransactionAndTimeoutLeavesUnknownUnlocked() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90020022', bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, payment.id());

        AtomicReference<Boolean> txActiveDuringGateway = new AtomicReference<>();
        doAnswer(inv -> {
            txActiveDuringGateway.set(TransactionSynchronizationManager.isActualTransactionActive());
            throw new RuntimeException("Read timed out");
        }).when(vnpayGateway).refund(any());

        UUID adminId = UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of(
                "amountVnd", 200_000L,
                "kind", "API",
                "reason", "Kiem tra timeout ngoai DB TX",
                "verifiedSystemError", true));

        String raw = mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "timeout-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("UNKNOWN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refundId = mapper.readTree(raw).path("id").asText();

        assertThat(txActiveDuringGateway.get()).isFalse();

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT signature_valid, outcome, raw->>'error' AS err FROM commerce.payment_event WHERE payment_id = ? AND source = 'REFUND_API'",
                payment.id());
        assertThat(event.get("signature_valid")).isEqualTo(false);
        assertThat(event.get("outcome")).isEqualTo("REFUND_GATEWAY_ERROR");
        assertThat(event.get("err")).isEqualTo("Read timed out");

        // Dòng DB không bị giữ khóa: có thể mark-failed ngay lập tức.
        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/mark-failed")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "reason", "Xac nhan timeout tu doi soat",
                                "evidenceRef", "TICKET-TIMEOUT-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }

    @Test
    void concurrentQueryMarkingFailedWhileRefundGatewayInFlightReturns00FlagsNeedsReviewAndPersistsEvent()
            throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90020033', bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, payment.id());

        CountDownLatch enteredRefundGateway = new CountDownLatch(1);
        CountDownLatch releaseRefundGateway = new CountDownLatch(1);
        doAnswer(inv -> {
            enteredRefundGateway.countDown();
            assertThat(releaseRefundGateway.await(15, TimeUnit.SECONDS)).isTrue();
            return refundResult("00", "05", payment.txnRef(), 200_000L, "90020033");
        }).when(vnpayGateway).refund(any());

        doReturn(queryDrResult("91", null, payment.txnRef(), 200_000L, "90020033"))
                .when(vnpayGateway).queryDr(any());

        UUID adminId = UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of(
                "amountVnd", 200_000L,
                "kind", "API",
                "reason", "Race refund vs query",
                "verifiedSystemError", true));

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Integer> createFuture = pool.submit(() -> mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                            + "/payments/" + payment.id() + "/refunds")
                            .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                            .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                            .header("Idempotency-Key", "race-ref-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus());

            assertThat(enteredRefundGateway.await(10, TimeUnit.SECONDS)).isTrue();
            UUID refundId = jdbc.queryForObject(
                    "SELECT id FROM commerce.refund WHERE payment_id = ?", UUID.class, payment.id());

            // Trong lúc refund() đang gọi mạng, /query trả 91 và chuyển refund PROCESSING -> FAILED.
            mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/query")
                            .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                            .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAILED"));

            // Nhả latch để refund() hoàn tất với mã 00/05.
            releaseRefundGateway.countDown();
            assertThat(createFuture.get(20, TimeUnit.SECONDS)).isEqualTo(201);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'REFUND_API' AND outcome = 'REFUND_SUCCEEDED_00'",
                    Integer.class, payment.id())).isEqualTo(1);
            Map<String, Object> orderRow = jdbc.queryForMap(
                    "SELECT needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", order.id());
            assertThat(orderRow.get("needs_review")).isEqualTo(true);
            assertThat(orderRow.get("review_reason")).isEqualTo("REFUND_LATE_SUCCESS_AFTER_FAILED:" + refundId);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND reason = ?",
                    Integer.class, order.id(), "REFUND_LATE_SUCCESS_AFTER_FAILED:" + refundId)).isEqualTo(1);
        }
    }

    @Test
    void concurrentMarkFailedWhileQueryDrInFlightPreservesForensicEventAndFlagsNeedsReviewOn00()
            throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        UUID adminId = UUID.randomUUID();

        // Kịch bản 1: mark-failed commit trong lúc queryDr đang gọi mạng rồi trả 00/05 -> không rollback, lưu QUERYDR event & gắn needs_review.
        var order00 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var p00 = fixtures.insertStubPayment(order00, 1, 200_000L, PaymentState.SUCCEEDED);
        UUID refund00Id = insertUnknownRefund(order00.id(), p00.id(), 200_000L);

        CountDownLatch enteredQuery00 = new CountDownLatch(1);
        CountDownLatch releaseQuery00 = new CountDownLatch(1);
        doAnswer(inv -> {
            enteredQuery00.countDown();
            assertThat(releaseQuery00.await(15, TimeUnit.SECONDS)).isTrue();
            return queryDrResult("00", "05", p00.txnRef(), 200_000L, "90020044");
        }).when(vnpayGateway).queryDr(any());

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Integer> queryFuture = pool.submit(() -> mockMvc.perform(post("/api/v1/admin/refunds/" + refund00Id + "/query")
                            .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                            .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                    .andReturn()
                    .getResponse()
                    .getStatus());

            assertThat(enteredQuery00.await(10, TimeUnit.SECONDS)).isTrue();
            mockMvc.perform(post("/api/v1/admin/refunds/" + refund00Id + "/mark-failed")
                            .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                            .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "reason", "Mark failed trong luc queryDr dang chay",
                                    "evidenceRef", "EV-RACE-MF-00"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAILED"));

            releaseQuery00.countDown();
            assertThat(queryFuture.get(20, TimeUnit.SECONDS)).isEqualTo(200);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'QUERYDR' AND outcome = 'REFUND_QUERY_SUCCEEDED_00'",
                    Integer.class, p00.id())).isEqualTo(1);
            Map<String, Object> orderRow = jdbc.queryForMap(
                    "SELECT needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", order00.id());
            assertThat(orderRow.get("needs_review")).isEqualTo(true);
            assertThat(orderRow.get("review_reason")).isEqualTo("REFUND_QUERY_SUCCEEDED_AFTER_FAILED:" + refund00Id);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND reason = ?",
                    Integer.class, order00.id(), "REFUND_QUERY_SUCCEEDED_AFTER_FAILED:" + refund00Id)).isEqualTo(1);
        }

        // Kịch bản 2: mark-failed commit trong lúc queryDr đang gọi mạng rồi trả 91 -> trả 409 nhưng vẫn giữ lại QUERYDR payment_event.
        Mockito.reset(vnpayGateway);
        var order91 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var p91 = fixtures.insertStubPayment(order91, 1, 200_000L, PaymentState.SUCCEEDED);
        UUID refund91Id = insertUnknownRefund(order91.id(), p91.id(), 200_000L);

        CountDownLatch enteredQuery91 = new CountDownLatch(1);
        CountDownLatch releaseQuery91 = new CountDownLatch(1);
        doAnswer(inv -> {
            enteredQuery91.countDown();
            assertThat(releaseQuery91.await(15, TimeUnit.SECONDS)).isTrue();
            return queryDrResult("91", null, p91.txnRef(), 200_000L, "90020045");
        }).when(vnpayGateway).queryDr(any());

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Integer> queryFuture = pool.submit(() -> mockMvc.perform(post("/api/v1/admin/refunds/" + refund91Id + "/query")
                            .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                            .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                    .andReturn()
                    .getResponse()
                    .getStatus());

            assertThat(enteredQuery91.await(10, TimeUnit.SECONDS)).isTrue();
            mockMvc.perform(post("/api/v1/admin/refunds/" + refund91Id + "/mark-failed")
                            .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                            .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "reason", "Mark failed trong luc queryDr tra 91",
                                    "evidenceRef", "EV-RACE-MF-91"))))
                    .andExpect(status().isOk());

            releaseQuery91.countDown();
            assertThat(queryFuture.get(20, TimeUnit.SECONDS)).isEqualTo(409);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'QUERYDR' AND outcome = 'REFUND_QUERY_FAILED_91'",
                    Integer.class, p91.id())).isEqualTo(1);
        }
    }

    @Test
    void markFailedReturns409Not412WhenConcurrentTxTransitionsUnknownToSucceededBeforeLock() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.SUCCEEDED);
        UUID refundId = insertUnknownRefund(order.id(), payment.id(), 200_000L);

        CountDownLatch orderLocked = new CountDownLatch(1);
        CountDownLatch proceedUpdateAndCommit = new CountDownLatch(1);
        UUID adminId = UUID.randomUUID();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> lockHolder = pool.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbc.queryForObject(
                                "SELECT id FROM commerce.purchase_order WHERE id = ? FOR UPDATE",
                                UUID.class, order.id());
                        orderLocked.countDown();
                        try {
                            assertThat(proceedUpdateAndCommit.await(15, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        jdbc.update("""
                                UPDATE commerce.refund
                                SET state = 'SUCCEEDED', finished_at = now(), updated_at = now(), version = version + 1
                                WHERE id = ?
                                """, refundId);
                    }));

            assertThat(orderLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<org.springframework.mock.web.MockHttpServletResponse> markFailedFuture = pool.submit(() -> mockMvc
                    .perform(post("/api/v1/admin/refunds/" + refundId + "/mark-failed")
                            .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                            .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "reason", "Race mark-failed vs query",
                                    "evidenceRef", "EV-L1-CACHE"))))
                    .andReturn()
                    .getResponse());

            // Chờ thread mark-failed vượt qua findOrderIdById() và chạm khóa dòng purchase_order FOR UPDATE.
            long deadline = System.currentTimeMillis() + 5_000L;
            while (System.currentTimeMillis() < deadline) {
                Integer waiting = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM pg_stat_activity
                        WHERE wait_event_type = 'Lock' AND query LIKE '%purchase_order%' AND state = 'active'
                        """, Integer.class);
                if (waiting != null && waiting > 0) {
                    break;
                }
                Thread.sleep(25L);
            }
            proceedUpdateAndCommit.countDown();
            lockHolder.get(15, TimeUnit.SECONDS);

            var resp = markFailedFuture.get(15, TimeUnit.SECONDS);
            assertThat(resp.getStatus()).isEqualTo(409);
            assertThat(mapper.readTree(resp.getContentAsString()).path("code").asText())
                    .isEqualTo("REFUND_NOT_UNKNOWN");
        }
    }

    private UUID insertUnknownRefund(UUID orderId, UUID paymentId, long amountVnd) {
        UUID refundId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'API', 'UNKNOWN', 'Race test refund', true, 'ops', ?, now(), now(), now(), 0)
                """,
                refundId,
                UUID.randomUUID().toString().replace("-", ""),
                "conc-unk-" + UUID.randomUUID(),
                paymentId,
                orderId,
                amountVnd,
                UUID.randomUUID());
        return refundId;
    }

    private static RefundResult refundResult(
            String responseCode, String txStatus, String txnRef, long amountVnd, String txnNo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (responseCode != null) {
            raw.put("vnp_ResponseCode", responseCode);
        }
        if (txStatus != null) {
            raw.put("vnp_TransactionStatus", txStatus);
        }
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new RefundResult(
                true, "req-" + UUID.randomUUID(), responseCode, "Mock " + responseCode,
                txnRef, amountVnd, "NCB", "20261121100500", txnNo, "02", txStatus, "Refund test", raw);
    }

    private static QueryDrResult queryDrResult(
            String responseCode, String txStatus, String txnRef, long amountVnd, String txnNo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (responseCode != null) {
            raw.put("vnp_ResponseCode", responseCode);
        }
        if (txStatus != null) {
            raw.put("vnp_TransactionStatus", txStatus);
        }
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new QueryDrResult(
                true, "qreq-" + UUID.randomUUID(), responseCode, "QueryDr " + responseCode,
                txnRef, amountVnd, "NCB", "20261121100500", txnNo, "02", txStatus, "QueryDr test", "", "", raw);
    }
}
