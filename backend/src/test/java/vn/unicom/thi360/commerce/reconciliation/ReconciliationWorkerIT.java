package vn.unicom.thi360.commerce.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.reconciliation.application.ReconciliationService;
import vn.unicom.thi360.commerce.reconciliation.application.ReconciliationWorker;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Kiểm thử tích hợp cho {@link ReconciliationWorker}, {@link ReconciliationService} và API
 * {@code GET /api/v1/admin/reconciliation-reports/latest} (AC-29, AC-30, AC-38).
 */
@TestPropertySource(properties = "thi360.commerce.scheduling.enabled=true")
class ReconciliationWorkerIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private ReconciliationWorker worker;
    @Autowired private ReconciliationService reconciliationService;
    @Autowired private PlatformTransactionManager transactionManager;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM commerce.refund");
        jdbc.update("DELETE FROM commerce.reconciliation_report");
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM commerce.refund");
        jdbc.update("DELETE FROM commerce.reconciliation_report");
        fixtures.cleanup();
    }

    @Test
    void generatesDailyReportWithAllFourBucketsExposesLatestEndpointAndIsIdempotentPerDay() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        // Chưa có báo cáo -> latest trả available=false.
        mockMvc.perform(get("/api/v1/admin/reconciliation-reports/latest")
                        .with(asUser(UUID.randomUUID(), "viewer", Permissions.ORDER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.report").isEmpty());

        // 1. Đơn PAID chưa cấp quyền.
        var paidOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());

        // 2. Payment ở trạng thái UNKNOWN.
        var unkOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, Instant.now().plusSeconds(600), null);
        var unkPayment = fixtures.insertStubPayment(unkOrder, 1, 200_000L, PaymentState.UNKNOWN);

        // 3. Payment thu thừa chưa hoàn đủ + 1 payment thu thừa ĐÃ hoàn đủ (không được đếm).
        var excessOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        fixtures.insertStubPayment(excessOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        var unrefundedDup = fixtures.insertStubPayment(excessOrder, 2, 200_000L, PaymentState.CREATED);
        jdbc.update("""
                UPDATE commerce.payment
                SET state = 'SUCCEEDED', is_duplicate = true, excess_reason = 'AFTER_COUNTED', updated_at = now()
                WHERE id = ?
                """, unrefundedDup.id());

        var refundedExcessOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        fixtures.insertStubPayment(refundedExcessOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        var refundedDup = fixtures.insertStubPayment(refundedExcessOrder, 2, 200_000L, PaymentState.CREATED);
        jdbc.update("""
                UPDATE commerce.payment
                SET state = 'SUCCEEDED', is_duplicate = true, excess_reason = 'AFTER_COUNTED', updated_at = now()
                WHERE id = ?
                """, refundedDup.id());
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state, reason,
                     verified_system_error, requested_by, requested_by_id, requested_at, finished_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 200000, 'API', 'SUCCEEDED', 'Da hoan du', true,
                        'ops', ?, now(), now(), now(), now(), 0)
                """, UUID.randomUUID(), UUID.randomUUID().toString().replace("-", ""),
                "reconcile-seed-" + UUID.randomUUID(), refundedDup.id(), refundedExcessOrder.id(), UUID.randomUUID());

        // 4. Payment Ca C đang FAILED và late_success_review = PENDING (dùng người mua riêng để không trùng PENDING_PAYMENT cùng user+product).
        var lateBuyer = fixtures.createLearner(true, "vi");
        var lateOrder = fixtures.insertStubOrder(
                lateBuyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, Instant.now().plusSeconds(600), null);
        var latePayment = fixtures.insertStubPayment(lateOrder, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("""
                UPDATE commerce.payment
                SET late_success_review = 'PENDING', late_success_at = now()
                WHERE id = ?
                """, latePayment.id());

        worker.runDailyReconciliation();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce.reconciliation_report", Integer.class))
                .isEqualTo(1);

        String latestRaw = mockMvc.perform(get("/api/v1/admin/reconciliation-reports/latest")
                        .with(asUser(UUID.randomUUID(), "viewer", Permissions.ORDER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.report.paidUnfulfilledCount").value(1))
                .andExpect(jsonPath("$.report.unknownPaymentCount").value(1))
                .andExpect(jsonPath("$.report.unrefundedExcessCount").value(1))
                .andExpect(jsonPath("$.report.pendingLateSuccessCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode details = mapper.readTree(latestRaw).path("report").path("details");
        assertThat(details.path("paidUnfulfilledOrders").get(0).path("orderCode").asText()).isEqualTo(paidOrder.code());
        assertThat(details.path("unknownPayments").get(0).path("txnRef").asText()).isEqualTo(unkPayment.txnRef());
        assertThat(details.path("unrefundedExcessPayments").get(0).path("txnRef").asText())
                .isEqualTo(unrefundedDup.txnRef());
        assertThat(details.path("pendingLateSuccessPayments").get(0).path("txnRef").asText())
                .isEqualTo(latePayment.txnRef());

        // Chạy lặp lại trong cùng ngày: bỏ qua idempotent, vẫn chỉ có 1 dòng báo cáo.
        worker.runDailyReconciliation();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce.reconciliation_report", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void skipsWhenAdvisoryLockIsHeldAndConcurrentRunsInsertAtMostOneReport() throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService lockHolder = Executors.newSingleThreadExecutor();
        try {
            Future<?> holderFuture = lockHolder.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    Boolean ok = jdbc.queryForObject(
                            "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ReconciliationService.ADVISORY_LOCK_KEY);
                    assertThat(ok).isTrue();
                    lockAcquired.countDown();
                    try {
                        assertThat(releaseLock.await(15, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                });
            });

            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(reconciliationService.generateDailyReport()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce.reconciliation_report", Integer.class))
                    .isZero();

            releaseLock.countDown();
            holderFuture.get(15, TimeUnit.SECONDS);
        } finally {
            lockHolder.shutdownNow();
        }

        // Sau khi nhả khóa: chạy song song 4 luồng cùng lúc chỉ tạo đúng 1 báo cáo.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(0, 1, 2, 3).stream()
                    .<Future<?>>map(i -> pool.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        reconciliationService.generateDailyReport();
                        return null;
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce.reconciliation_report", Integer.class))
                .isEqualTo(1);
    }

    @Nested
    @TestPropertySource(properties = "thi360.commerce.scheduling.enabled=false")
    class SchedulingDisabled {

        @Autowired
        private ApplicationContext disabledContext;

        @Test
        void reconciliationWorkerBeanIsAbsentWhenSchedulingDisabled() {
            assertThat(disabledContext.getBeansOfType(ReconciliationWorker.class)).isEmpty();
        }
    }
}
