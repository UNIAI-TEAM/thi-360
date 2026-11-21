package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrClaimRepository.ClaimedPayment;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubOrder;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubPayment;

/**
 * Kiểm thử tích hợp {@link QueryDrClaimRepository} trên PostgreSQL thật:
 * biên 15 phút / 24 giờ, loại trừ trạng thái kết thúc, cooldown 5 phút, {@code SKIP LOCKED} và {@code REQUIRES_NEW}.
 */
class QueryDrClaimRepositoryIT extends AbstractIntegrationTest {

    private static final Duration MIN_AGE = Duration.ofMinutes(15);
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private QueryDrClaimRepository claimRepository;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(
                jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void claimsExactBoundariesRespectsCooldownAndSkipsFinalStates() {
        Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 100_000L, 1);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                100_000L, baseNow.plusSeconds(900), null);

        StubPayment tooYoung = insertAt(order, 1, PaymentState.CREATED, baseNow.minus(MIN_AGE).plusSeconds(1));
        StubPayment atMinEdge = insertAt(order, 2, PaymentState.CREATED, baseNow.minus(MIN_AGE));
        StubPayment atMaxEdge = insertAt(order, 3, PaymentState.UNKNOWN, baseNow.minus(MAX_AGE));
        StubPayment tooOld = insertAt(order, 4, PaymentState.UNKNOWN, baseNow.minus(MAX_AGE).minusSeconds(1));
        StubPayment succeeded = insertAt(order, 5, PaymentState.SUCCEEDED, baseNow.minus(Duration.ofHours(1)));
        StubPayment failed = insertAt(order, 6, PaymentState.FAILED, baseNow.minus(Duration.ofHours(1)));

        List<ClaimedPayment> claimed = claimRepository.claimEligibleForWorker(
                baseNow, MIN_AGE, MAX_AGE, COOLDOWN, 20);

        List<UUID> claimedIds = claimed.stream().map(ClaimedPayment::paymentId).toList();
        assertThat(claimedIds)
                .contains(atMinEdge.id(), atMaxEdge.id())
                .doesNotContain(tooYoung.id(), tooOld.id(), succeeded.id(), failed.id());

        OffsetDateTime minEdgeNextQuery = jdbc.queryForObject(
                "SELECT next_query_at FROM commerce.payment WHERE id = ?",
                OffsetDateTime.class,
                atMinEdge.id());
        assertThat(minEdgeNextQuery).isNotNull();
        assertThat(minEdgeNextQuery.toInstant()).isEqualTo(baseNow.plus(COOLDOWN));

        // Trước mốc cooldown 1 giây (+4m59s): atMinEdge/atMaxEdge vẫn đang cooldown,
        // còn tooYoung (19m58s) đã qua 15m nên được nhận và đặt cooldown mới (+9m59s).
        List<ClaimedPayment> beforeCooldown = claimRepository.claimEligibleForWorker(
                baseNow.plus(COOLDOWN).minusSeconds(1), MIN_AGE, MAX_AGE, COOLDOWN, 20);
        assertThat(beforeCooldown.stream().map(ClaimedPayment::paymentId))
                .contains(tooYoung.id())
                .doesNotContain(atMinEdge.id(), atMaxEdge.id());

        // Đúng mốc cooldown (+5m): atMinEdge (20m) hết cooldown nên nhận lại được;
        // atMaxEdge (24h05m) quá 24h nên rời cửa sổ; tooYoung vẫn đang trong cooldown từ lượt trên.
        List<ClaimedPayment> atCooldown = claimRepository.claimEligibleForWorker(
                baseNow.plus(COOLDOWN), MIN_AGE, MAX_AGE, COOLDOWN, 20);
        assertThat(atCooldown.stream().map(ClaimedPayment::paymentId))
                .contains(atMinEdge.id())
                .doesNotContain(atMaxEdge.id(), tooOld.id(), tooYoung.id());
    }

    @Test
    void claimOpenForOrderClaimsNonCooldownRowsAndSkipsLockedRows() throws Exception {
        Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 120_000L, 1);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                120_000L, baseNow.minusSeconds(60), null);

        StubPayment lockedPay = insertAt(order, 1, PaymentState.CREATED, baseNow.minus(Duration.ofMinutes(20)));
        StubPayment freePay = insertAt(order, 2, PaymentState.UNKNOWN, baseNow.minus(Duration.ofMinutes(18)));
        StubPayment failedPay = insertAt(order, 3, PaymentState.FAILED, baseNow.minus(Duration.ofMinutes(16)));

        CountDownLatch lockedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        try (ExecutorService exec = Executors.newSingleThreadExecutor()) {
            Future<?> locker = exec.submit(() -> new TransactionTemplate(txManager).executeWithoutResult(status -> {
                jdbc.queryForObject(
                        "SELECT id FROM commerce.payment WHERE id = ? FOR UPDATE",
                        UUID.class,
                        lockedPay.id());
                lockedLatch.countDown();
                try {
                    if (!releaseLatch.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for releaseLatch");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));

            assertThat(lockedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            List<ClaimedPayment> firstClaim = claimRepository.claimOpenForOrder(
                    order.id(), baseNow, MAX_AGE, COOLDOWN);
            releaseLatch.countDown();
            locker.get(10, TimeUnit.SECONDS);

            assertThat(firstClaim).extracting(ClaimedPayment::paymentId)
                    .containsExactly(freePay.id())
                    .doesNotContain(lockedPay.id(), failedPay.id());
            assertThat(firstClaim.getFirst().orderCode()).isEqualTo(order.code());

            // Sau khi nhả khóa, lockedPay chưa có cooldown nên nhận được; freePay đang trong cooldown nên bỏ qua
            List<ClaimedPayment> secondClaim = claimRepository.claimOpenForOrder(
                    order.id(), baseNow.plusSeconds(10), MAX_AGE, COOLDOWN);
            assertThat(secondClaim).extracting(ClaimedPayment::paymentId)
                    .containsExactly(lockedPay.id());
        }
    }

    @Test
    void claimOpenForOrderEnforces24hMaxAgeAndTransitionsAgedCreatedToUnknown() {
        Instant baseNow = Instant.parse("2026-11-21T12:00:00Z");
        LearnerAccount buyer = fixtures.createLearner();
        StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 120_000L, 1);
        StubOrder agedOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                120_000L, baseNow.minusSeconds(60), null);

        StubPayment oldUnknown = insertAt(agedOrder, 1, PaymentState.UNKNOWN, baseNow.minus(MAX_AGE).minusSeconds(1));
        StubPayment oldCreated = insertAt(agedOrder, 2, PaymentState.CREATED, baseNow.minus(MAX_AGE).minusSeconds(1));
        jdbc.update(
                "UPDATE commerce.payment SET next_query_at = ? WHERE id IN (?, ?)",
                Timestamp.from(baseNow.minusSeconds(30)),
                oldUnknown.id(),
                oldCreated.id());

        assertThat(claimRepository.claimOpenForOrder(agedOrder.id(), baseNow, MAX_AGE, COOLDOWN)).isEmpty();

        StubPayment exactBoundary = insertAt(agedOrder, 3, PaymentState.CREATED, baseNow.minus(MAX_AGE));
        assertThat(claimRepository.claimOpenForOrder(agedOrder.id(), baseNow, MAX_AGE, COOLDOWN))
                .extracting(ClaimedPayment::paymentId)
                .containsExactly(exactBoundary.id());

        int updated = claimRepository.transitionAgedPaymentsPastMaxAge(baseNow.minus(MAX_AGE), baseNow);
        assertThat(updated).isGreaterThanOrEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, oldCreated.id()))
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject(
                "SELECT next_query_at FROM commerce.payment WHERE id = ?", OffsetDateTime.class, oldCreated.id()))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, oldUnknown.id()))
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject(
                "SELECT next_query_at FROM commerce.payment WHERE id = ?", OffsetDateTime.class, oldUnknown.id()))
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.payment WHERE id = ?", String.class, exactBoundary.id()))
                .isEqualTo("CREATED");
    }

    private StubPayment insertAt(StubOrder order, int seq, PaymentState state, Instant createdAt) {
        StubPayment p = fixtures.insertStubPayment(order, seq, 100_000L, state);
        jdbc.update(
                "UPDATE commerce.payment SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                p.id());
        return p;
    }
}
