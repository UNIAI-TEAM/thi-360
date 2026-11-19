package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementRowLocker;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

class EntitlementGrantConcurrencyIT extends EntitlementIntegrationTest {

    private static final int ROUNDS = 10;

    @MockitoSpyBean
    private EntitlementRowLocker locker;

    /** AC-1: two orders of the same account and exam granted at the same moment share one entitlement. */
    @Test
    void concurrentGrantsShareOneEntitlement() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID userId = newId();
            UUID examId = newId();

            List<GrantOrderResult> results = runConcurrently(
                    () -> grantService.grantForOrder(order(newId(), userId, line(examId, 2))),
                    () -> grantService.grantForOrder(order(newId(), userId, line(examId, 3))));

            UUID entitlementId = results.get(0).lines().get(0).entitlementId();
            assertThat(results.get(1).lines().get(0).entitlementId()).isEqualTo(entitlementId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?", Long.class,
                    userId, examId)).as("one entitlement row, round %d", round).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.entitlement_grant WHERE entitlement_id = ?", Long.class,
                    entitlementId)).isEqualTo(2L);
            assertThat(count(entitlementRow(entitlementId), "attempts_total")).isEqualTo(5);
            assertCountsEqualGrantSums(entitlementId);
        }
    }

    /**
     * AC-2: two orders of one account holding the same two exams, listed in opposite order, granted together.
     * The spy makes each order pause after its first lock until the other order took its own first lock, which is
     * the interleaving that deadlocks when lines are locked in arrival order. Both must finish.
     */
    @Test
    void multiExamOrdersDoNotDeadlock() throws Exception {
        UUID userId = newId();
        UUID examA = newId();
        UUID examB = newId();
        pauseAfterFirstLockOfEachCaller();

        List<GrantOrderResult> results = runConcurrently(
                () -> grantService.grantForOrder(order(newId(), userId, line(examA, 1), line(examB, 1))),
                () -> grantService.grantForOrder(order(newId(), userId, line(examB, 2), line(examA, 2))));

        assertThat(results).hasSize(2);
        for (UUID examId : List.of(examA, examB)) {
            UUID entitlementId = jdbc.queryForObject(
                    "SELECT id FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?", UUID.class,
                    userId, examId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.entitlement_grant WHERE entitlement_id = ?", Long.class,
                    entitlementId)).isEqualTo(2L);
            assertThat(count(entitlementRow(entitlementId), "attempts_total")).isEqualTo(3);
            assertCountsEqualGrantSums(entitlementId);
        }
    }

    /**
     * Stubs the spy behind the transactional proxy: the stubbing call itself must not run the proxy's
     * {@code MANDATORY} advice. Spring resets the spy after each test.
     */
    private void pauseAfterFirstLockOfEachCaller() {
        CountDownLatch bothHoldAFirstLock = new CountDownLatch(2);
        Set<Long> paused = ConcurrentHashMap.newKeySet();
        EntitlementRowLocker spy = AopTestUtils.getUltimateTargetObject(locker);
        doAnswer(invocation -> {
            Object locked = invocation.callRealMethod();
            if (paused.add(Thread.currentThread().threadId())) {
                bothHoldAFirstLock.countDown();
                bothHoldAFirstLock.await(1500, TimeUnit.MILLISECONDS);
            }
            return locked;
        }).when(spy).lockOrCreatePurchase(any(), any());
    }

    /** Races the calls and requires every one to finish without an exception (no deadlock, no lost insert). */
    @SafeVarargs
    private static <T> List<T> runConcurrently(Callable<T>... tasks) throws Exception {
        List<T> values = new ArrayList<>();
        for (Outcome<T> outcome : raceAll(tasks)) {
            if (outcome.error() != null) {
                throw new AssertionError("a concurrent call failed: " + outcome.error(), outcome.error());
            }
            values.add(outcome.value());
        }
        return values;
    }
}
