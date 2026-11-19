package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.shared.api.ConflictException;

class EntitlementConcurrencyIT extends EntitlementIntegrationTest {

    private static final int ROUNDS = 15;

    /** AC-13: two reserves race for the last attempt; one gets it, the other 409 NO_ATTEMPTS_LEFT. */
    @Test
    void lastAttemptGoesToOneRequest() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID userId = newId();
            GrantOrderResult.Line granted = grant(userId, newId(), 1);
            UUID entitlementId = granted.entitlementId();
            UUID first = newId();
            UUID second = newId();

            List<Outcome<EntitlementBenefits>> outcomes = raceAll(
                    () -> port.reserve(userId, entitlementId, first),
                    () -> port.reserve(userId, entitlementId, second));

            assertThat(outcomes.stream().filter(o -> o.value() != null)).as("round %d winners", round).hasSize(1);
            Outcome<EntitlementBenefits> loser =
                    outcomes.stream().filter(o -> o.error() != null).findFirst().orElse(null);
            assertThat(loser).as("one reserve must fail in round %d", round).isNotNull();
            assertThat(loser.error()).isInstanceOfSatisfying(ConflictException.class,
                    e -> assertThat(e.getCode()).isEqualTo("NO_ATTEMPTS_LEFT"));
            Object entitlement = entitlementRow(entitlementId).get("attempts_reserved");
            assertThat(((Number) entitlement).intValue()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.entitlement_usage u JOIN commerce.entitlement_grant g"
                            + " ON g.id = u.grant_id WHERE g.entitlement_id = ?", Long.class, entitlementId))
                    .isEqualTo(1L);
            assertInvariants(entitlementId);
        }
    }

    /** More requests than attempts: exactly as many succeed as there are attempts, the rest are refused. */
    @Test
    void manyRequestsNeverReserveMoreThanTheAttemptsLeft() throws Exception {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line granted = grant(userId, examId, 2);
        grant(userId, examId, 1);
        UUID entitlementId = granted.entitlementId();
        List<Callable<EntitlementBenefits>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID attemptId = newId();
            tasks.add(() -> port.reserve(userId, entitlementId, attemptId));
        }

        List<Outcome<EntitlementBenefits>> outcomes = raceAll(tasks);

        assertThat(outcomes.stream().filter(o -> o.value() != null)).hasSize(3);
        assertThat(outcomes.stream().filter(o -> o.error() != null)).hasSize(5).allSatisfy(o ->
                assertThat(o.error()).isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NO_ATTEMPTS_LEFT")));
        assertThat(((Number) entitlementRow(entitlementId).get("attempts_reserved")).intValue()).isEqualTo(3);
        assertInvariants(entitlementId);
    }

    /** AC-5 under concurrency: the same attempt id reserved twice at once is one reservation. */
    @Test
    void sameAttemptReservedTwiceAtOnceHoldsOneAttempt() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID userId = newId();
            GrantOrderResult.Line granted = grant(userId, newId(), 3);
            UUID attemptId = newId();

            List<Outcome<EntitlementBenefits>> outcomes = raceAll(
                    () -> port.reserve(userId, granted.entitlementId(), attemptId),
                    () -> port.reserve(userId, granted.entitlementId(), attemptId));

            assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
            assertThat(outcomes.get(0).value()).isEqualTo(outcomes.get(1).value());
            assertThat(((Number) entitlementRow(granted.entitlementId()).get("attempts_reserved")).intValue())
                    .as("round %d", round).isEqualTo(1);
            assertInvariants(granted.entitlementId());
        }
    }
}
