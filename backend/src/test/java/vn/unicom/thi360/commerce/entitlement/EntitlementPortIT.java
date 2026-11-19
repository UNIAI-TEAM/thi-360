package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.GrantSource;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;

class EntitlementPortIT extends EntitlementIntegrationTest {

    /** AC-5: a repeated reserve of one attempt id returns the same grant and changes no count. */
    @Test
    void reserveIsIdempotent() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 3);
        UUID attemptId = newId();

        EntitlementBenefits first = port.reserve(userId, granted.entitlementId(), attemptId);
        EntitlementBenefits second = port.reserve(userId, granted.entitlementId(), attemptId);

        assertThat(second).isEqualTo(first);
        assertThat(first.grantId()).isEqualTo(granted.grantId());
        assertThat(first.examId()).isEqualTo(granted.examId());
        assertThat(count(entitlementRow(granted.entitlementId()), "attempts_reserved")).isEqualTo(1);
        assertThat(count(grantRow(granted.grantId()), "attempts_reserved")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_usage WHERE attempt_id = ?", Long.class,
                attemptId)).isEqualTo(1L);
        assertInvariants(granted.entitlementId());
    }

    /**
     * BE1-3, AD-8 Q2: replaying a successful reserve after the attempt was released returns the original result
     * and changes nothing; the slot it gave back is reserved by a new attempt id.
     */
    @Test
    void reserveReplayAfterReleaseReturnsTheOriginalResultAndChangesNothing() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 1);
        UUID attemptId = newId();
        EntitlementBenefits original = port.reserve(userId, granted.entitlementId(), attemptId);
        port.release(attemptId);
        Map<String, Object> entitlementBefore = writtenColumns("commerce.entitlement", granted.entitlementId());
        Map<String, Object> grantBefore = writtenColumns("commerce.entitlement_grant", granted.grantId());

        EntitlementBenefits replay = port.reserve(userId, granted.entitlementId(), attemptId);

        assertThat(replay).isEqualTo(original);
        assertThat(usageState(attemptId)).isEqualTo("RELEASED");
        assertThat(writtenColumns("commerce.entitlement", granted.entitlementId())).isEqualTo(entitlementBefore);
        assertThat(writtenColumns("commerce.entitlement_grant", granted.grantId())).isEqualTo(grantBefore);
        assertThat(count(entitlementBefore, "attempts_reserved")).isZero();
        assertThat(port.describe(userId, granted.entitlementId()).attemptsLeft()).isEqualTo(1);

        UUID newAttempt = newId();
        assertThat(port.reserve(userId, granted.entitlementId(), newAttempt).grantId()).isEqualTo(granted.grantId());
        assertThat(usageState(newAttempt)).isEqualTo("RESERVED");
        assertThat(usageState(attemptId)).isEqualTo("RELEASED");
        assertThat(count(entitlementRow(granted.entitlementId()), "attempts_reserved")).isEqualTo(1);
        assertInvariants(granted.entitlementId());
    }

    /** An attempt id that already belongs to another entitlement is a caller error, not a replay. */
    @Test
    void reserveWithAnAttemptIdOfAnotherEntitlementIsAConflict() {
        UUID userId = newId();
        GrantOrderResult.Line one = grant(userId, newId(), 1);
        GrantOrderResult.Line other = grant(userId, newId(), 1);
        UUID attemptId = newId();
        port.reserve(userId, one.entitlementId(), attemptId);

        assertThatThrownBy(() -> port.reserve(userId, other.entitlementId(), attemptId))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("CONFLICT"));

        assertThat(count(entitlementRow(other.entitlementId()), "attempts_reserved")).isZero();
        assertInvariants(one.entitlementId());
        assertInvariants(other.entitlementId());
    }

    /** AC-6: confirming a used attempt again changes nothing. */
    @Test
    void confirmUseIsIdempotent() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 2);
        UUID attemptId = newId();
        port.reserve(userId, granted.entitlementId(), attemptId);

        port.confirmUse(attemptId);
        port.confirmUse(attemptId);

        Map<String, Object> entitlement = entitlementRow(granted.entitlementId());
        assertThat(count(entitlement, "attempts_used")).isEqualTo(1);
        assertThat(count(entitlement, "attempts_reserved")).isZero();
        assertThat(count(grantRow(granted.grantId()), "attempts_used")).isEqualTo(1);
        assertThat(usageState(attemptId)).isEqualTo("USED");
        assertInvariants(granted.entitlementId());
    }

    /** AD-8: confirming an attempt that never reserved anything is not found, not a silent success. */
    @Test
    void confirmUseWithoutUsageIsNotFound() {
        assertThatThrownBy(() -> port.confirmUse(newId())).isInstanceOf(NotFoundException.class);
    }

    /** AC-7: releasing an attempt again changes nothing, and releasing one without usage is a no-op. */
    @Test
    void releaseIsIdempotent() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 2);
        UUID attemptId = newId();
        port.reserve(userId, granted.entitlementId(), attemptId);

        port.release(attemptId);
        port.release(attemptId);

        Map<String, Object> entitlement = entitlementRow(granted.entitlementId());
        assertThat(count(entitlement, "attempts_reserved")).isZero();
        assertThat(count(entitlement, "attempts_used")).isZero();
        assertThat(count(entitlement, "attempts_revoked")).isZero();
        assertThat(usageState(attemptId)).isEqualTo("RELEASED");
        assertThat(port.describe(userId, granted.entitlementId()).attemptsLeft()).isEqualTo(2);
        assertInvariants(granted.entitlementId());

        assertThatCode(() -> port.release(newId())).doesNotThrowAnyException();
    }

    /** AC-8: release after USED and confirmUse after RELEASED change no count. */
    @Test
    void outOfOrderCallsChangeNothing() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 3);
        UUID usedAttempt = newId();
        UUID releasedAttempt = newId();
        port.reserve(userId, granted.entitlementId(), usedAttempt);
        port.reserve(userId, granted.entitlementId(), releasedAttempt);
        port.confirmUse(usedAttempt);
        port.release(releasedAttempt);
        Map<String, Object> entitlementBefore = writtenColumns("commerce.entitlement", granted.entitlementId());
        Map<String, Object> grantBefore = writtenColumns("commerce.entitlement_grant", granted.grantId());

        port.release(usedAttempt);
        port.confirmUse(releasedAttempt);

        assertThat(usageState(usedAttempt)).isEqualTo("USED");
        assertThat(usageState(releasedAttempt)).isEqualTo("RELEASED");
        // The rows are identical, version and timestamps included: the calls wrote nothing.
        assertThat(writtenColumns("commerce.entitlement", granted.entitlementId())).isEqualTo(entitlementBefore);
        assertThat(writtenColumns("commerce.entitlement_grant", granted.grantId())).isEqualTo(grantBefore);
        assertThat(count(entitlementBefore, "attempts_used")).isEqualTo(1);
        assertThat(count(entitlementBefore, "attempts_reserved")).isZero();
        assertInvariants(granted.entitlementId());
    }

    /** The columns a port call can change, as plain values (array and jsonb columns do not compare by value). */
    private Map<String, Object> writtenColumns(String table, UUID id) {
        String extra = table.endsWith("entitlement") ? "updated_at" : "revoked_at";
        return jdbc.queryForMap("SELECT attempts_used, attempts_reserved, attempts_revoked, version, " + extra
                + " FROM " + table + " WHERE id = ?", id);
    }

    /** AC-9 (E21): the oldest grant with attempts is used first; a grant with none left is skipped. */
    @Test
    void oldestGrantIsUsedFirst() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line oldest = grant(userId, examId, 1);
        GrantOrderResult.Line middle = grant(userId, examId, 2);
        GrantOrderResult.Line newest = grant(userId, examId, 1);
        UUID entitlementId = oldest.entitlementId();

        // The last attempt of the oldest grant, then the next grant takes over.
        assertThat(port.reserve(userId, entitlementId, newId()).grantId()).isEqualTo(oldest.grantId());
        assertThat(port.reserve(userId, entitlementId, newId()).grantId()).isEqualTo(middle.grantId());
        assertThat(port.reserve(userId, entitlementId, newId()).grantId()).isEqualTo(middle.grantId());
        assertThat(port.reserve(userId, entitlementId, newId()).grantId()).isEqualTo(newest.grantId());

        assertThat(count(grantRow(oldest.grantId()), "attempts_reserved")).isEqualTo(1);
        assertThat(count(grantRow(middle.grantId()), "attempts_reserved")).isEqualTo(2);
        assertThat(count(grantRow(newest.grantId()), "attempts_reserved")).isEqualTo(1);
        assertInvariants(entitlementId);
    }

    /** A refunded grant has nothing to give: reserve draws from the next grant (E21). */
    @Test
    void refundedGrantIsSkippedByReserve() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line refunded = grant(userId, examId, 2);
        GrantOrderResult.Line later = grant(userId, examId, 2);
        UUID entitlementId = refunded.entitlementId();

        grantService.revokeUnused(refunded.grantId());

        EntitlementBenefits reserved = port.reserve(userId, entitlementId, newId());
        assertThat(reserved.grantId()).isEqualTo(later.grantId());
        assertThat(port.describe(userId, entitlementId).attemptsLeft()).isEqualTo(1);
        assertInvariants(entitlementId);
    }

    /**
     * BE1-4 (E21): FIFO follows the database sequence, not {@code granted_at}. The later grant carries the
     * earlier timestamp (a lagging application clock), and describe and reserve still take the first grant.
     */
    @Test
    void grantsAreUsedInGrantOrderWhateverTheirTimestamps() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line first = grant(userId, examId, 1);
        GrantOrderResult.Line second = grant(userId, examId, 1);
        jdbc.update("UPDATE commerce.entitlement_grant SET granted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-03-01T00:00:00Z")), first.grantId());
        jdbc.update("UPDATE commerce.entitlement_grant SET granted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), second.grantId());

        assertThat(port.describe(userId, first.entitlementId()).grantId()).isEqualTo(first.grantId());
        assertThat(port.reserve(userId, first.entitlementId(), newId()).grantId()).isEqualTo(first.grantId());
        assertThat(port.describe(userId, first.entitlementId()).grantId()).isEqualTo(second.grantId());
        assertThat(port.reserve(userId, first.entitlementId(), newId()).grantId()).isEqualTo(second.grantId());
        assertInvariants(first.entitlementId());
    }

    /** AC-10: describe shows the grant the next reserve takes, with attempts left, options, source, code, date. */
    @Test
    void describeMatchesNextReserve() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line oldest = grant(userId, examId, 1, List.of("OPT-A", "OPT-B"), Map.of("ai", true), "FULL", 1);
        GrantOrderResult.Line newest = grant(userId, examId, 2, List.of("OPT-C"), Map.of("ai", false), "SCORE_ONLY", 2);
        UUID entitlementId = oldest.entitlementId();

        EntitlementDescription first = port.describe(userId, entitlementId);
        assertThat(first.examId()).isEqualTo(examId);
        assertThat(first.attemptsLeft()).isEqualTo(3);
        assertThat(first.grantId()).isEqualTo(oldest.grantId());
        assertThat(first.source()).isEqualTo(GrantSource.PURCHASE);
        assertThat(first.orderCode()).isEqualTo(grantRow(oldest.grantId()).get("order_code"));
        assertThat(first.grantedAt()).isEqualTo(
                ((java.sql.Timestamp) grantRow(oldest.grantId()).get("granted_at")).toInstant());
        assertThat(first.optionCodes()).containsExactly("OPT-A", "OPT-B");
        assertThat(port.reserve(userId, entitlementId, newId()).grantId()).isEqualTo(first.grantId());

        EntitlementDescription second = port.describe(userId, entitlementId);
        assertThat(second.attemptsLeft()).isEqualTo(2);
        assertThat(second.grantId()).isEqualTo(newest.grantId());
        assertThat(second.orderCode()).isEqualTo(grantRow(newest.grantId()).get("order_code"));
        assertThat(second.optionCodes()).containsExactly("OPT-C");
        assertThat(port.reserve(userId, entitlementId, newId()).grantId()).isEqualTo(second.grantId());
    }

    /** Nothing left: describe still answers, with no grant. */
    @Test
    void describeWithoutAttemptsLeftHasNoGrant() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line granted = grant(userId, examId, 1);
        port.reserve(userId, granted.entitlementId(), newId());

        EntitlementDescription description = port.describe(userId, granted.entitlementId());

        assertThat(description.examId()).isEqualTo(examId);
        assertThat(description.attemptsLeft()).isZero();
        assertThat(description.grantId()).isNull();
        assertThat(description.source()).isNull();
        assertThat(description.orderCode()).isNull();
        assertThat(description.grantedAt()).isNull();
        assertThat(description.optionCodes()).isEmpty();
    }

    /** AC-11: describe and reserve on another account's entitlement are not found and change nothing. */
    @Test
    void otherAccountGets404() {
        UUID owner = newId();
        UUID stranger = newId();
        GrantOrderResult.Line granted = grant(owner, newId(), 2);
        UUID strangerOwn = grant(stranger, newId(), 1).entitlementId();
        UUID attemptId = newId();

        assertThatThrownBy(() -> port.describe(stranger, granted.entitlementId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> port.reserve(stranger, granted.entitlementId(), attemptId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> port.describe(owner, newId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> port.reserve(owner, newId(), newId())).isInstanceOf(NotFoundException.class);

        assertThat(count(entitlementRow(granted.entitlementId()), "attempts_reserved")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_usage WHERE attempt_id = ?", Long.class,
                attemptId)).isZero();
        assertThat(port.describe(stranger, strangerOwn).attemptsLeft()).isEqualTo(1);
        assertInvariants(granted.entitlementId());
    }

    /** AC-12: with no attempt left, reserve answers 409 NO_ATTEMPTS_LEFT and holds nothing. */
    @Test
    void reserveWithoutAttemptsIsRejected() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 1);
        port.reserve(userId, granted.entitlementId(), newId());
        UUID rejected = newId();

        assertThatThrownBy(() -> port.reserve(userId, granted.entitlementId(), rejected))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NO_ATTEMPTS_LEFT"));

        assertThat(count(entitlementRow(granted.entitlementId()), "attempts_reserved")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_usage WHERE attempt_id = ?", Long.class,
                rejected)).isZero();
        assertInvariants(granted.entitlementId());
    }

    /** AC-14 (E21): the benefits of an attempt stay those of the grant it drew from after a new purchase. */
    @Test
    void benefitsFollowUsedGrant() {
        UUID userId = newId();
        UUID examId = newId();
        GrantOrderResult.Line oldGrant = grant(userId, examId, 1, List.of("OPT-A"), Map.of("ai", true), "FULL", 1);
        UUID usedAttempt = newId();
        port.reserve(userId, oldGrant.entitlementId(), usedAttempt);
        port.confirmUse(usedAttempt);

        GrantOrderResult.Line newGrant = grant(userId, examId, 1, List.of("OPT-B"), Map.of("ai", false), "SCORE_ONLY", 2);
        UUID nextAttempt = newId();
        EntitlementBenefits next = port.reserve(userId, newGrant.entitlementId(), nextAttempt);

        EntitlementBenefits used = port.benefitsOf(usedAttempt);
        assertThat(used.grantId()).isEqualTo(oldGrant.grantId());
        assertThat(used.examId()).isEqualTo(examId);
        assertThat(used.benefits()).containsEntry("ai", true);
        assertThat(used.resultView()).isEqualTo("FULL");
        assertThat(used.optionCodes()).containsExactly("OPT-A");
        assertThat(used.examVersionNo()).isEqualTo(1);

        assertThat(next.grantId()).isEqualTo(newGrant.grantId());
        assertThat(port.benefitsOf(nextAttempt)).isEqualTo(next);
        assertThat(next.benefits()).containsEntry("ai", false);
        assertThat(next.resultView()).isEqualTo("SCORE_ONLY");
        assertThat(next.optionCodes()).containsExactly("OPT-B");
        assertThat(next.examVersionNo()).isEqualTo(2);
        assertInvariants(oldGrant.entitlementId());
    }

    /** AD-8: benefitsOf for an attempt with no usage is not found. */
    @Test
    void benefitsOfWithoutUsageIsNotFound() {
        assertThatThrownBy(() -> port.benefitsOf(newId())).isInstanceOf(NotFoundException.class);
    }
}
