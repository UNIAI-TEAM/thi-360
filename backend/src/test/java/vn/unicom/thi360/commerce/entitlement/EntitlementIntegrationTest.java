package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

/**
 * Shared set-up of the entitlement tests: random accounts, exams and orders per test (plan CM-4a "Dữ liệu
 * test"), grants made through {@link EntitlementGrantService}, and the invariant every case ends with.
 */
abstract class EntitlementIntegrationTest extends AbstractIntegrationTest {

    protected static final String RESULT_VIEW_FULL = "FULL";

    @Autowired
    protected EntitlementGrantService grantService;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static UUID newId() {
        return UUID.randomUUID();
    }

    protected static GrantLine line(UUID examId, int attempts) {
        return new GrantLine(examId, 1, attempts, List.of("OPT-A"), Map.of("ai", true), RESULT_VIEW_FULL);
    }

    protected static GrantOrderCommand order(UUID orderId, UUID userId, GrantLine... lines) {
        return new GrantOrderCommand(orderId, "ORD-" + orderId.toString().substring(0, 8), userId, List.of(lines));
    }

    /** Grants {@code attempts} attempts of one exam through a fresh order. */
    protected GrantOrderResult.Line grant(UUID userId, UUID examId, int attempts) {
        return grantService.grantForOrder(order(newId(), userId, line(examId, attempts))).lines().get(0);
    }

    /** Grants one order line with its own snapshot; returns the grant that line produced. */
    protected GrantOrderResult.Line grant(
            UUID userId, UUID examId, int attempts, List<String> optionCodes, Map<String, Object> benefits,
            String resultView, int examVersionNo) {
        return grantService.grantForOrder(order(newId(), userId,
                new GrantLine(examId, examVersionNo, attempts, optionCodes, benefits, resultView))).lines().get(0);
    }

    protected Map<String, Object> entitlementRow(UUID entitlementId) {
        return jdbc.queryForMap("SELECT * FROM commerce.entitlement WHERE id = ?", entitlementId);
    }

    protected Map<String, Object> grantRow(UUID grantId) {
        return jdbc.queryForMap("SELECT * FROM commerce.entitlement_grant WHERE id = ?", grantId);
    }

    protected static int count(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    /**
     * The invariant checked after every case: each counter of the entitlement equals the sum of the same
     * counter over its grants.
     */
    protected void assertCountsEqualGrantSums(UUID entitlementId) {
        Map<String, Object> entitlement = entitlementRow(entitlementId);
        Map<String, Object> sums = jdbc.queryForMap(
                """
                SELECT COALESCE(SUM(attempts), 0) AS attempts_total,
                       COALESCE(SUM(attempts_used), 0) AS attempts_used,
                       COALESCE(SUM(attempts_reserved), 0) AS attempts_reserved,
                       COALESCE(SUM(attempts_revoked), 0) AS attempts_revoked
                FROM commerce.entitlement_grant WHERE entitlement_id = ?
                """,
                entitlementId);
        for (String column : List.of("attempts_total", "attempts_used", "attempts_reserved", "attempts_revoked")) {
            assertThat(count(entitlement, column))
                    .as("entitlement.%s equals the sum over its grants", column)
                    .isEqualTo(count(sums, column));
        }
    }

    /** Result of one racing call: its value, or the exception the call under test threw. */
    record Outcome<T>(T value, Throwable error) {
    }

    /** Runs the calls on separate threads released together, and reports each call's value or exception. */
    protected static <T> List<Outcome<T>> raceAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome<>(task.call(), null);
                    } catch (Exception e) {
                        return new Outcome<>(null, e);
                    }
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    @SafeVarargs
    protected static <T> List<Outcome<T>> raceAll(Callable<T>... tasks) throws Exception {
        return raceAll(List.of(tasks));
    }
}
