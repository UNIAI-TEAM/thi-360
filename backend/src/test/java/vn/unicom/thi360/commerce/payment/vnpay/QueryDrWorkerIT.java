package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.expiry.OrderExpiryWorker;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository.ClaimedPayment;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubOrder;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubPayment;
import vn.unicom.thi360.shared.testapi.MutableClock;

/**
 * AC-15, AC-16, kiểm tra migration trên DB có dữ liệu sẵn và điều kiện bật/tắt worker.
 */
@Import(VnpayTestClockConfig.class)
@TestPropertySource(properties = {
    "thi360.commerce.scheduling.enabled=true",
    "thi360.commerce.querydr.interval=PT1H",
    "thi360.commerce.order-expiry.interval=PT1H"
})
class QueryDrWorkerIT extends AbstractIntegrationTest {

    private static final String MIGRATION_VERSION = "202611210400";
    private static final List<String> SCHEMAS = List.of(
            "shared", "identity", "catalog", "audit", "classroom", "questionbank", "exam", "commerce", "attempt");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private QueryDrWorker queryDrWorker;
    @Autowired private OrderExpiryWorker orderExpiryWorker;
    @Autowired private MutableClock mutableClock;

    @MockitoSpyBean
    private VnpayGateway vnpayGateway;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        reset(vnpayGateway);
        mutableClock.setAt(Instant.now());
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(vnpayGateway);
        mutableClock.reset();
        fixtures.cleanup();
    }

    @Test
    void ac15WorkerQueriesExact15mTo24hWindowAndSkipsFinalStates() {
        Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
        mutableClock.setAt(baseNow);

        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.plusSeconds(900), null);

        StubPayment tooYoung = insertAt(order, 1, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(15)).plusSeconds(1));
        StubPayment atMinEdge = insertAt(order, 2, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(15)));
        StubPayment atMaxEdge = insertAt(order, 3, PaymentState.UNKNOWN, baseNow.minus(Duration.ofHours(24)));
        StubPayment tooOld = insertAt(order, 4, PaymentState.UNKNOWN, baseNow.minus(Duration.ofHours(24)).minusSeconds(1));
        StubPayment succeeded = insertAt(order, 5, PaymentState.SUCCEEDED, baseNow.minus(Duration.ofMinutes(20)));
        StubPayment failed = insertAt(order, 6, PaymentState.FAILED, baseNow.minus(Duration.ofMinutes(20)));

        List<String> queriedTxnRefs = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            QueryDrRequest req = inv.getArgument(0);
            queriedTxnRefs.add(req.txnRef());
            return queryResult(req.txnRef(), "00", "01");
        }).when(vnpayGateway).queryDr(any());

        queryDrWorker.pollPendingPayments();

        assertThat(queriedTxnRefs)
                .contains(atMinEdge.txnRef(), atMaxEdge.txnRef())
                .doesNotContain(tooYoung.txnRef(), tooOld.txnRef(), succeeded.txnRef(), failed.txnRef());
        assertThat(paymentRepository.findById(tooOld.id()).orElseThrow().getState())
                .isEqualTo(PaymentState.UNKNOWN);
    }

    @Test
    void ac16ConcurrentQueryDrWorkersAndExpiryWorkerShareCooldownAndRunHttpOutsideTransaction() throws Exception {
        Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
        mutableClock.setAt(baseNow);

        LearnerAccount buyer1 = fixtures.createLearner();
        LearnerAccount buyer2 = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);

        // Ca A: Hai QueryDrWorker chạy đồng thời trên cùng 1 payment -> chỉ đúng 1 lời gọi HTTP trong 5 phút.
        StubOrder o1 = fixtures.insertStubOrder(
                buyer1, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.plusSeconds(900), null);
        StubPayment p1 = insertAt(o1, 1, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(20)));

        AtomicInteger p1Calls = new AtomicInteger();
        AtomicBoolean txActiveDuringHttp = new AtomicBoolean(false);
        CountDownLatch p1FirstInHttp = new CountDownLatch(1);
        CountDownLatch p1AllowReturn = new CountDownLatch(1);

        doAnswer(inv -> {
            QueryDrRequest req = inv.getArgument(0);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                txActiveDuringHttp.set(true);
            }
            if (req.txnRef().equals(p1.txnRef())) {
                if (p1Calls.incrementAndGet() == 1) {
                    p1FirstInHttp.countDown();
                    p1AllowReturn.await(5, TimeUnit.SECONDS);
                }
                return queryResult(req.txnRef(), "00", "01");
            }
            return queryResult(req.txnRef(), "00", "02");
        }).when(vnpayGateway).queryDr(any());

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> w1 = pool.submit(() -> queryDrWorker.pollPendingPayments());
            assertThat(p1FirstInHttp.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> w2 = pool.submit(() -> queryDrWorker.pollPendingPayments());
            w2.get(10, TimeUnit.SECONDS);
            p1AllowReturn.countDown();
            w1.get(10, TimeUnit.SECONDS);
        }

        assertThat(p1Calls.get()).isEqualTo(1);
        assertThat(txActiveDuringHttp.get()).isFalse();

        // Ca B: QueryDrWorker và OrderExpiryWorker chạy đồng thời trên cùng 1 đơn quá hạn -> đúng 1 lời gọi HTTP trong 5 phút.
        StubOrder o2 = fixtures.insertStubOrder(
                buyer2, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.minusSeconds(60), null);
        StubPayment p2 = insertAt(o2, 1, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(20)));

        AtomicInteger p2Calls = new AtomicInteger();
        CountDownLatch p2InHttp = new CountDownLatch(1);
        CountDownLatch p2AllowReturn = new CountDownLatch(1);
        doAnswer(inv -> {
            QueryDrRequest req = inv.getArgument(0);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                txActiveDuringHttp.set(true);
            }
            if (req.txnRef().equals(p2.txnRef())) {
                if (p2Calls.incrementAndGet() == 1) {
                    p2InHttp.countDown();
                    p2AllowReturn.await(5, TimeUnit.SECONDS);
                }
                return queryResult(req.txnRef(), "00", "02");
            }
            return queryResult(req.txnRef(), "00", "01");
        }).when(vnpayGateway).queryDr(any());

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> qWorkerFuture = pool.submit(() -> queryDrWorker.pollPendingPayments());
            assertThat(p2InHttp.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> expWorkerFuture = pool.submit(() -> orderExpiryWorker.expireOrders());
            expWorkerFuture.get(10, TimeUnit.SECONDS);
            p2AllowReturn.countDown();
            qWorkerFuture.get(10, TimeUnit.SECONDS);
        }

        assertThat(p2Calls.get()).isEqualTo(1);
        assertThat(txActiveDuringHttp.get()).isFalse();

        // Ca C: Chiều ngược lại — OrderExpiryWorker vào HTTP trước, QueryDrWorker chạy đồng thời -> vẫn đúng 1 lời gọi.
        LearnerAccount buyer3 = fixtures.createLearner();
        StubOrder o3 = fixtures.insertStubOrder(
                buyer3, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, baseNow.minusSeconds(60), null);
        StubPayment p3 = insertAt(o3, 1, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(20)));

        AtomicInteger p3Calls = new AtomicInteger();
        CountDownLatch p3InHttp = new CountDownLatch(1);
        CountDownLatch p3AllowReturn = new CountDownLatch(1);
        doAnswer(inv -> {
            QueryDrRequest req = inv.getArgument(0);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                txActiveDuringHttp.set(true);
            }
            if (req.txnRef().equals(p3.txnRef())) {
                if (p3Calls.incrementAndGet() == 1) {
                    p3InHttp.countDown();
                    p3AllowReturn.await(5, TimeUnit.SECONDS);
                }
                return queryResult(req.txnRef(), "00", "02");
            }
            return queryResult(req.txnRef(), "00", "01");
        }).when(vnpayGateway).queryDr(any());

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> expFirstFuture = pool.submit(() -> orderExpiryWorker.expireOrders());
            assertThat(p3InHttp.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> qSecondFuture = pool.submit(() -> queryDrWorker.pollPendingPayments());
            qSecondFuture.get(10, TimeUnit.SECONDS);
            p3AllowReturn.countDown();
            expFirstFuture.get(10, TimeUnit.SECONDS);
        }

        assertThat(p3Calls.get()).isEqualTo(1);
        assertThat(txActiveDuringHttp.get()).isFalse();
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o3.id()))
                .isEqualTo("EXPIRED");
    }

    @Test
    void migrationOnPreExistingDataLeavesNextQueryAtNullAndClaimsOnlyOpenPayments() {
        String isolatedDb = "qdr_mig_" + shortToken().toLowerCase();
        jdbc.execute("CREATE DATABASE " + isolatedDb);
        try {
            String url = jdbcUrlFor(isolatedDb);
            DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
            flyway(url, versionBefore(url, MIGRATION_VERSION)).migrate();

            JdbcTemplate isolated = new JdbcTemplate(ds);
            Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
            Instant createdAt = baseNow.minus(Duration.ofMinutes(30));

            UUID orderId = UUID.randomUUID();
            String orderCode = "DHMIG" + shortToken();
            UUID userId = UUID.randomUUID();
            UUID examId = UUID.randomUUID();
            isolated.update("""
                    INSERT INTO commerce.purchase_order
                        (id, code, user_id, buyer_username, buyer_full_name, product_type, exam_id, exam_version_no,
                         product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd, attempts_granted,
                         result_view, payment_method, state, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'u', 'User', 'EXAM', ?, 1, 'P1', 'Exam 1', 100000, 'NONE', 100000, 1,
                            'SCORE', 'VNPAY', 'PENDING_PAYMENT', ?, ?, 0)
                    """, orderId, orderCode, userId, examId, Timestamp.from(createdAt), Timestamp.from(createdAt));

            UUID pCreated = insertIsolatedPayment(isolated, orderId, 1, orderCode + "01", "CREATED", createdAt);
            UUID pUnknown = insertIsolatedPayment(isolated, orderId, 2, orderCode + "02", "UNKNOWN", createdAt);
            UUID pSucceeded = insertIsolatedPayment(isolated, orderId, 3, orderCode + "03", "SUCCEEDED", createdAt);
            UUID pFailed = insertIsolatedPayment(isolated, orderId, 4, orderCode + "04", "FAILED", createdAt);

            // Chạy migration V202611210400 trên dữ liệu có sẵn.
            flyway(url, MIGRATION_VERSION).migrate();

            List<Object> nextQueryValues = isolated.queryForList(
                    "SELECT next_query_at FROM commerce.payment WHERE order_id = ?", Object.class, orderId);
            assertThat(nextQueryValues).hasSize(4).containsOnly((Object) null);

            QueryDrClaimRepository isolatedClaimRepo =
                    new QueryDrClaimRepository(isolated, new JdbcTransactionManager(ds));
            List<ClaimedPayment> claimed = isolatedClaimRepo.claimEligibleForWorker(
                    baseNow, Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofMinutes(5), 20);

            assertThat(claimed).extracting(ClaimedPayment::paymentId)
                    .containsExactlyInAnyOrder(pCreated, pUnknown)
                    .doesNotContain(pSucceeded, pFailed);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + isolatedDb + " WITH (FORCE)");
        }
    }

    @Nested
    @TestPropertySource(properties = "thi360.commerce.scheduling.enabled=false")
    class SchedulingDisabled {

        @Autowired
        private ApplicationContext disabledContext;

        @Test
        void queryDrAndOrderExpiryWorkerBeansAreAbsentWhenSchedulingDisabled() {
            assertThat(disabledContext.getBeansOfType(QueryDrWorker.class)).isEmpty();
            assertThat(disabledContext.getBeansOfType(OrderExpiryWorker.class)).isEmpty();
        }
    }

    private StubPayment insertAt(StubOrder order, int seq, PaymentState state, Instant createdAt) {
        StubPayment p = fixtures.insertStubPayment(order, seq, 200_000L, state);
        jdbc.update(
                "UPDATE commerce.payment SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(createdAt), Timestamp.from(createdAt), p.id());
        return p;
    }

    private static UUID insertIsolatedPayment(
            JdbcTemplate target, UUID orderId, int seq, String txnRef, String state, Instant createdAt) {
        UUID id = UUID.randomUUID();
        Timestamp ts = Timestamp.from(createdAt);
        target.update("""
                INSERT INTO commerce.payment
                    (id, order_id, attempt_seq, txn_ref, vnp_create_date, vnp_expire_date, amount_vnd,
                     state, is_duplicate, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, '20261121100000', '20261121101500', 100000, ?, false, ?, ?, 0)
                """, id, orderId, seq, txnRef, state, ts, ts);
        return id;
    }

    private static QueryDrResult queryResult(String txnRef, String responseCode, String txnStatus) {
        return new QueryDrResult(
                true,
                "resp-" + UUID.randomUUID().toString().substring(0, 8),
                responseCode,
                "Msg",
                txnRef,
                200_000L,
                "NCB",
                "20261121100200",
                "0",
                "01",
                txnStatus,
                "Info",
                null,
                null,
                Map.of("vnp_ResponseCode", responseCode, "vnp_TransactionStatus", txnStatus, "vnp_TxnRef", txnRef));
    }

    private static String jdbcUrlFor(String dbName) {
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + dbName);
    }

    private static String versionBefore(String url, String targetVersion) {
        MigrationVersion target = MigrationVersion.fromVersion(targetVersion);
        MigrationInfo[] all = flyway(url, null).info().all();
        return Arrays.stream(all)
                .map(MigrationInfo::getVersion)
                .filter(v -> v.compareTo(target) < 0)
                .max(MigrationVersion::compareTo)
                .orElseThrow()
                .getVersion();
    }

    private static Flyway flyway(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("shared")
                .schemas(SCHEMAS.toArray(String[]::new))
                .table("flyway_schema_history")
                .locations("classpath:db/migration")
                .placeholders(Map.of("auditAppRole", POSTGRES.getUsername()));
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
