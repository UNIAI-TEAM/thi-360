package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.CompensationResult;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.GrantSource;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;

class EntitlementCompensationIT extends EntitlementIntegrationTest {

    /**
     * AC-18: one COMPENSATION grant of one attempt, with no order, carrying the benefits of the grant the faulted
     * attempt used (not those of a later purchase).
     */
    @Test
    void compensationCopiesBenefits() {
        UUID userId = newId();
        UUID examId = newId();
        UUID adminId = newId();
        GrantOrderResult.Line firstGrant =
                grant(userId, examId, 1, List.of("OPT-A", "OPT-B"), Map.of("ai", true), "FULL", 3);
        UUID faultedAttempt = newId();
        port.reserve(userId, firstGrant.entitlementId(), faultedAttempt);
        port.confirmUse(faultedAttempt);
        GrantOrderResult.Line laterGrant =
                grant(userId, examId, 2, List.of("OPT-C"), Map.of("ai", false), "SCORE_ONLY", 4);
        UUID entitlementId = firstGrant.entitlementId();

        CompensationResult result = port.compensate(faultedAttempt, adminId, "paper generation failed");

        assertThat(result.entitlementId()).isEqualTo(entitlementId);
        assertThat(result.grantId()).isNotIn(firstGrant.grantId(), laterGrant.grantId());
        Map<String, Object> grant = grantRow(result.grantId());
        assertThat(grant.get("kind")).isEqualTo("COMPENSATION");
        assertThat(grant.get("order_id")).isNull();
        assertThat(grant.get("order_code")).isNull();
        assertThat(count(grant, "attempts")).isEqualTo(1);
        assertThat(grant.get("compensation_attempt_id")).isEqualTo(faultedAttempt);
        assertThat(grant.get("exam_id")).isEqualTo(examId);
        assertThat(grant.get("result_view")).isEqualTo("FULL");
        assertThat(count(grant, "exam_version_no")).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT array_to_string(option_codes, ',') FROM commerce.entitlement_grant WHERE id = ?",
                String.class, result.grantId())).isEqualTo("OPT-A,OPT-B");
        assertThat(jdbc.queryForObject(
                "SELECT benefits ->> 'ai' FROM commerce.entitlement_grant WHERE id = ?", String.class,
                result.grantId())).isEqualTo("true");
        assertThat(grant.get("granted_by")).isEqualTo(adminId.toString());
        assertThat(grant.get("reason")).isEqualTo("paper generation failed");
        assertThat(grant.get("revoked_at")).isNull();
        // The faulted attempt's own grant is untouched; the entitlement gained exactly one attempt.
        assertThat(count(grantRow(firstGrant.grantId()), "attempts")).isEqualTo(1);
        assertThat(count(entitlementRow(entitlementId), "attempts_total")).isEqualTo(4);
        assertThat(count(entitlementRow(entitlementId), "attempts_used")).isEqualTo(1);
        assertInvariants(entitlementId);
    }

    /** The compensation attempt is an ordinary attempt: it is described and reserved as COMPENSATION, last in line. */
    @Test
    void compensationAttemptIsUsableAfterTheOlderGrants() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 1, List.of("OPT-A"), Map.of("ai", true), "FULL", 1);
        UUID faultedAttempt = newId();
        port.reserve(userId, granted.entitlementId(), faultedAttempt);
        port.confirmUse(faultedAttempt);

        CompensationResult result = port.compensate(faultedAttempt, newId(), "fault");

        EntitlementDescription description = port.describe(userId, granted.entitlementId());
        assertThat(description.attemptsLeft()).isEqualTo(1);
        assertThat(description.grantId()).isEqualTo(result.grantId());
        assertThat(description.source()).isEqualTo(GrantSource.COMPENSATION);
        assertThat(description.orderCode()).isNull();
        assertThat(description.optionCodes()).containsExactly("OPT-A");
        UUID retry = newId();
        EntitlementBenefits benefits = port.reserve(userId, granted.entitlementId(), retry);
        assertThat(benefits.grantId()).isEqualTo(result.grantId());
        assertThat(benefits.benefits()).containsEntry("ai", true);
        assertInvariants(granted.entitlementId());
    }

    /** AC-19: a second compensation of the same attempt is 409 ALREADY_COMPENSATED, one at a time or together. */
    @Test
    void secondCompensationIsRejected() throws Exception {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 2);
        UUID faultedAttempt = newId();
        port.reserve(userId, granted.entitlementId(), faultedAttempt);
        port.confirmUse(faultedAttempt);
        port.compensate(faultedAttempt, newId(), "first");

        assertThatThrownBy(() -> port.compensate(faultedAttempt, newId(), "second"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ALREADY_COMPENSATED"));

        assertThat(compensationGrants(granted.entitlementId())).isEqualTo(1L);
        assertThat(count(entitlementRow(granted.entitlementId()), "attempts_total")).isEqualTo(3);
        assertInvariants(granted.entitlementId());

        for (int round = 0; round < 10; round++) {
            UUID racingUser = newId();
            GrantOrderResult.Line racing = grant(racingUser, newId(), 1);
            UUID racingAttempt = newId();
            port.reserve(racingUser, racing.entitlementId(), racingAttempt);
            port.confirmUse(racingAttempt);

            List<Outcome<CompensationResult>> outcomes = raceAll(
                    () -> port.compensate(racingAttempt, newId(), "request one"),
                    () -> port.compensate(racingAttempt, newId(), "request two"));

            assertThat(outcomes.stream().filter(o -> o.value() != null)).as("round %d winners", round).hasSize(1);
            assertThat(outcomes.stream().filter(o -> o.error() != null).findFirst().orElseThrow().error())
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ALREADY_COMPENSATED"));
            assertThat(compensationGrants(racing.entitlementId())).isEqualTo(1L);
            assertThat(count(entitlementRow(racing.entitlementId()), "attempts_total")).isEqualTo(2);
            assertInvariants(racing.entitlementId());
        }
    }

    /**
     * AC-20: a fully refunded grant cannot be compensated, even when every attempt of it was used before the
     * refund (nothing was revoked then, only {@code revoked_at} marks the refund).
     */
    @Test
    void refundedGrantCannotBeCompensated() {
        UUID userId = newId();
        GrantOrderResult.Line allUsed = grant(userId, newId(), 1);
        UUID usedAttempt = newId();
        port.reserve(userId, allUsed.entitlementId(), usedAttempt);
        port.confirmUse(usedAttempt);
        assertThat(grantService.revokeUnused(allUsed.grantId())).isZero();

        assertThatThrownBy(() -> port.compensate(usedAttempt, newId(), "late"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GRANT_REFUNDED"));

        assertThat(compensationGrants(allUsed.entitlementId())).isZero();
        assertThat(count(entitlementRow(allUsed.entitlementId()), "attempts_total")).isEqualTo(1);
        assertInvariants(allUsed.entitlementId());

        // A grant refunded with attempts still unused behaves the same.
        GrantOrderResult.Line partlyUsed = grant(userId, newId(), 3);
        UUID otherAttempt = newId();
        port.reserve(userId, partlyUsed.entitlementId(), otherAttempt);
        port.confirmUse(otherAttempt);
        assertThat(grantService.revokeUnused(partlyUsed.grantId())).isEqualTo(2);

        assertThatThrownBy(() -> port.compensate(otherAttempt, newId(), "late"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GRANT_REFUNDED"));
        assertThat(compensationGrants(partlyUsed.entitlementId())).isZero();
        assertInvariants(partlyUsed.entitlementId());
    }

    /** An attempt compensated before the refund still answers ALREADY_COMPENSATED afterwards. */
    @Test
    void compensatedThenRefundedAnswersAlreadyCompensated() {
        UUID userId = newId();
        GrantOrderResult.Line granted = grant(userId, newId(), 1);
        UUID attemptId = newId();
        port.reserve(userId, granted.entitlementId(), attemptId);
        port.confirmUse(attemptId);
        port.compensate(attemptId, newId(), "fault");
        grantService.revokeUnused(granted.grantId());

        assertThatThrownBy(() -> port.compensate(attemptId, newId(), "again"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ALREADY_COMPENSATED"));
        assertInvariants(granted.entitlementId());
    }

    /** AD-8: compensating an attempt with no usage is not found. */
    @Test
    void compensationWithoutUsageIsNotFound() {
        assertThatThrownBy(() -> port.compensate(newId(), newId(), "no such attempt"))
                .isInstanceOf(NotFoundException.class);
    }

    private long compensationGrants(UUID entitlementId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_grant WHERE entitlement_id = ? AND kind = 'COMPENSATION'",
                Long.class, entitlementId);
    }
}
