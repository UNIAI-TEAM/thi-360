package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.shared.api.ConflictException;

/**
 * BE1-1 and BE1-2: the start transaction of the attempt module calls {@code describe} (LT 5.2 step 1) and then
 * {@code reserve} in the same transaction, while other transactions commit on the entitlement in between.
 */
class EntitlementPrepareThenReserveIT extends EntitlementIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    /** A repurchase committed between describe and reserve neither fails the reservation nor breaks the counts. */
    @Test
    void repurchaseBetweenDescribeAndReserveStillReserves() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line first = grant(userId, examId, 1);
        UUID attemptId = newId();

        EntitlementBenefits reserved = new TransactionTemplate(transactionManager).execute(status -> {
            EntitlementDescription described = port.describe(userId, first.entitlementId());
            assertThat(described.attemptsLeft()).isEqualTo(1);
            assertThat(described.grantId()).isEqualTo(first.grantId());
            assertThat(entityManager.unwrap(Session.class).getStatistics().getEntityCount())
                    .as("describe leaves no entity in the caller's persistence context").isZero();

            commitInAnotherTransaction(() -> grant(userId, examId, 2));
            return port.reserve(userId, first.entitlementId(), attemptId);
        });

        assertThat(reserved.grantId()).isEqualTo(first.grantId());
        assertThat(count(entitlementRow(first.entitlementId()), "attempts_total")).isEqualTo(3);
        assertThat(count(entitlementRow(first.entitlementId()), "attempts_reserved")).isEqualTo(1);
        assertThat(usageState(attemptId)).isEqualTo("RESERVED");
        assertInvariants(first.entitlementId());
    }

    /** The last attempt is taken elsewhere and a repurchase follows: reserve draws from the new grant. */
    @Test
    void attemptTakenAndRepurchasedBetweenDescribeAndReserveUsesTheNewGrant() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line first = grant(userId, examId, 1);
        UUID[] newGrant = new UUID[1];

        EntitlementBenefits reserved = new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(port.describe(userId, first.entitlementId()).grantId()).isEqualTo(first.grantId());
            commitInAnotherTransaction(() -> {
                port.reserve(userId, first.entitlementId(), newId());
                newGrant[0] = grant(userId, examId, 2).grantId();
            });
            return port.reserve(userId, first.entitlementId(), newId());
        });

        assertThat(reserved.grantId()).isEqualTo(newGrant[0]);
        assertThat(count(entitlementRow(first.entitlementId()), "attempts_reserved")).isEqualTo(2);
        assertInvariants(first.entitlementId());
    }

    /** A full refund of the only grant between describe and reserve is NO_ATTEMPTS_LEFT, not a version error. */
    @Test
    void refundBetweenDescribeAndReserveIsNoAttemptsLeft() {
        UUID userId = newId();
        GrantOrderResult.Line only = grant(userId, newId(), 2);
        UUID attemptId = newId();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(port.describe(userId, only.entitlementId()).attemptsLeft()).isEqualTo(2);
            commitInAnotherTransaction(() -> grantService.revokeUnused(only.grantId()));
            return port.reserve(userId, only.entitlementId(), attemptId);
        })).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_ATTEMPTS_LEFT"));

        assertThat(count(entitlementRow(only.entitlementId()), "attempts_reserved")).isZero();
        assertThat(count(entitlementRow(only.entitlementId()), "attempts_revoked")).isEqualTo(2);
        assertInvariants(only.entitlementId());
    }

    /**
     * Attempts left and the grant always come as a pair: zero with no grant, positive with a grant, through
     * reserve, release, full refund and repurchase.
     */
    @Test
    void attemptsLeftAndGrantAlwaysAgree() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line first = grant(userId, examId, 1);
        UUID entitlementId = first.entitlementId();
        UUID attemptId = newId();

        assertDescription(userId, entitlementId, 1, first.grantId());
        port.reserve(userId, entitlementId, attemptId);
        assertDescription(userId, entitlementId, 0, null);
        port.release(attemptId);
        assertDescription(userId, entitlementId, 1, first.grantId());
        grantService.revokeUnused(first.grantId());
        assertDescription(userId, entitlementId, 0, null);
        GrantOrderResult.Line second = grant(userId, examId, 2);
        assertDescription(userId, entitlementId, 2, second.grantId());
        assertInvariants(entitlementId);
    }

    private void assertDescription(UUID userId, UUID entitlementId, int attemptsLeft, UUID grantId) {
        EntitlementDescription description = port.describe(userId, entitlementId);
        assertThat(description.attemptsLeft()).isEqualTo(attemptsLeft);
        assertThat(description.grantId()).isEqualTo(grantId);
        assertThat(description.optionCodes().isEmpty()).isEqualTo(grantId == null);
    }

    /** Runs {@code work} on another thread, outside the caller's transaction, and waits for its commit. */
    private static void commitInAnotherTransaction(Runnable work) {
        CompletableFuture.runAsync(work).join();
    }
}
