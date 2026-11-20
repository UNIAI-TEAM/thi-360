package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

/**
 * Integration tests for shared deadline expiry, lazy overdue close across read/finish/submit,
 * and exact deadline boundary semantics (AC-7, AC-11, F9, F11).
 */
class AttemptTimingIT extends AbstractAttemptDsIT {

    @Test
    void sharedDeadlineSkipsUnopenedParts() throws Exception {
        UUID userId = createStudent("timing-shared-skip");
        GrantOrderResult.Line line = grant(userId, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt =
                startAndAwaitOpen(
                        userId, "bca-skip", line.entitlementId(), new PreChoiceInput("CA2", null));
        Instant sharedDeadline = attempt.getDeadlineAt();
        assertThat(sharedDeadline).isNotNull();

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);

        // Finish Part 0 so Part 1 is OPEN and Part 2 is PENDING
        mutableClock.advance(Duration.ofMinutes(1));
        assertThat(
                        performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);

        // Advance past shared deadline -> trigger lazy close -> Part 1 CLOSED(TIME_UP), Part 2 SKIPPED(TIME_UP)
        mutableClock.setAt(sharedDeadline.plus(Duration.ofMinutes(2)));
        MvcResult stateRes = performGetState(userId, attempt.getId());
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(stateRes).path("state").asText()).isEqualTo("GRADING");

        Attempt closedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closedAttempt.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(closedAttempt.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(closedAttempt.getFinishedAt()).isEqualTo(sharedDeadline);

        List<AttemptPart> updatedParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(updatedParts.get(0).getState()).isEqualTo(PartState.CLOSED);
        assertThat(updatedParts.get(0).getCloseReason()).isEqualTo(PartCloseReason.FINISHED);

        assertThat(updatedParts.get(1).getState()).isEqualTo(PartState.CLOSED);
        assertThat(updatedParts.get(1).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(updatedParts.get(1).getClosedAt()).isEqualTo(sharedDeadline);

        assertThat(updatedParts.get(2).getState()).isEqualTo(PartState.SKIPPED);
        assertThat(updatedParts.get(2).getCloseReason()).isEqualTo(PartCloseReason.TIME_UP);
        assertThat(updatedParts.get(2).getClosedAt()).isEqualTo(sharedDeadline);

        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countOutboxEvents(attempt.getId(), "attempt.finished")).isEqualTo(1);
    }

    @Test
    void anyRequestClosesOverdueFirst() throws Exception {
        // 1a. GET /me/attempts/{id} lazily closes overdue PER_PART Part 0 at exact part0.deadlineAt
        UUID u1 = createStudent("lazy-get-perpart");
        GrantOrderResult.Line hsaLine = grant(u1, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt hsaAttempt = startAndAwaitOpen(u1, "lazy-get-pp", hsaLine.entitlementId(), null);
        List<AttemptPart> hsaParts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsaAttempt.getId());
        Instant p0Deadline = hsaParts.get(0).getDeadlineAt();

        mutableClock.setAt(p0Deadline.plus(Duration.ofMinutes(2)));
        MvcResult getPerPartRes = performGetState(u1, hsaAttempt.getId());
        assertThat(getPerPartRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode getPerPartJson = json(getPerPartRes);
        assertThat(getPerPartJson.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(getPerPartJson.path("parts").get(0).path("state").asText()).isEqualTo("CLOSED");
        assertThat(Instant.parse(getPerPartJson.path("parts").get(0).path("closedAt").asText()))
                .isEqualTo(p0Deadline);
        assertThat(getPerPartJson.path("parts").get(1).path("state").asText()).isEqualTo("OPEN");
        assertThat(Instant.parse(getPerPartJson.path("parts").get(1).path("startedAt").asText()))
                .isEqualTo(p0Deadline);

        // 1b. GET /me/attempts/{id} lazily closes overdue SHARED attempt at exact attempt.deadlineAt
        UUID u2 = createStudent("lazy-get-shared");
        GrantOrderResult.Line singleLine = grant(u2, "DS-HSA-PART-MATH-MINI", 3, List.of());
        Attempt sharedAttempt = startAndAwaitOpen(u2, "lazy-get-sh", singleLine.entitlementId(), null);
        Instant sharedDeadline =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(sharedAttempt.getId()).getFirst().getDeadlineAt();

        mutableClock.setAt(sharedDeadline.plus(Duration.ofMinutes(5)));
        MvcResult getSharedRes = performGetState(u2, sharedAttempt.getId());
        assertThat(getSharedRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(getSharedRes).path("state").asText()).isEqualTo("GRADING");
        Attempt reloadedShared = attemptRepository.findById(sharedAttempt.getId()).orElseThrow();
        assertThat(reloadedShared.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(reloadedShared.getFinishedAt()).isEqualTo(sharedDeadline);

        // 2. POST .../parts/{partId}/finish lazily closes overdue part with closed_at = deadline_at, TIME_UP
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        UUID u1b = createStudent("lazy-fin-perpart");
        GrantOrderResult.Line hsaLine2 = grant(u1b, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt hsa2 = startAndAwaitOpen(u1b, "lazy-fin-pp", hsaLine2.entitlementId(), null);
        List<AttemptPart> hsa2Parts =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsa2.getId());
        Instant hsa2P0Deadline = hsa2Parts.get(0).getDeadlineAt();

        mutableClock.setAt(hsa2P0Deadline.plus(Duration.ofMinutes(3)));
        MvcResult finOverdueRes =
                performFinishPart(u1b, hsa2.getId(), hsa2Parts.get(0).getId(), null, null);
        assertThat(finOverdueRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode finJson = json(finOverdueRes);
        assertThat(finJson.path("closedPart").path("state").asText()).isEqualTo("CLOSED");
        assertThat(finJson.path("closedPart").path("closeReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(finJson.path("closedPart").path("closedAt").asText()))
                .isEqualTo(hsa2P0Deadline);
        assertThat(finJson.path("nextPart").path("state").asText()).isEqualTo("OPEN");
        assertThat(Instant.parse(finJson.path("nextPart").path("startedAt").asText()))
                .isEqualTo(hsa2P0Deadline);

        // 2b. Even when finish on last part throws 409 ATTEMPT_PART_IS_LAST, lazy close REQUIRES_NEW still commits!
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt single2 = startAndAwaitOpen(u2, "lazy-fin-last", singleLine.entitlementId(), null);
        AttemptPart single2Part =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(single2.getId()).getFirst();
        mutableClock.setAt(single2Part.getDeadlineAt().plus(Duration.ofMinutes(5)));
        MvcResult finLastRes =
                performFinishPart(u2, single2.getId(), single2Part.getId(), null, null);
        assertThat(finLastRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(finLastRes).path("code").asText()).isEqualTo("ATTEMPT_PART_IS_LAST");
        Attempt single2Closed = attemptRepository.findById(single2.getId()).orElseThrow();
        assertThat(single2Closed.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(single2Closed.getEndReason()).isEqualTo(EndReason.TIME_UP);
        assertThat(single2Closed.getFinishedAt()).isEqualTo(single2Part.getDeadlineAt());

        // 3. POST .../submit lazily closes overdue attempt with finished_at = deadline_at, TIME_UP
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt single3 = startAndAwaitOpen(u2, "lazy-sub", singleLine.entitlementId(), null);
        Instant single3Deadline =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(single3.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(single3Deadline.plus(Duration.ofMinutes(5)));
        MvcResult subRes = performSubmit(u2, single3.getId(), "lazy-sub-key", null, null);
        assertThat(subRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode subJson = json(subRes);
        assertThat(subJson.path("state").asText()).isEqualTo("GRADING");
        assertThat(subJson.path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(subJson.path("finishedAt").asText())).isEqualTo(single3Deadline);
    }

    @Test
    void deadlineBoundaryOneSecondBeforeAtAndAfter() throws Exception {
        UUID userId = createStudent("deadline-boundary");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        // 1. At deadline - 1s -> accepted as SUBMITTED
        Attempt aBefore = startAndAwaitOpen(userId, "bnd-before", line.entitlementId(), null);
        Instant dBefore =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aBefore.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(dBefore.minusSeconds(1));
        MvcResult resBefore = performSubmit(userId, aBefore.getId(), "bnd-k-1", null, null);
        assertThat(resBefore.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resBefore).path("endReason").asText()).isEqualTo("SUBMITTED");
        assertThat(Instant.parse(json(resBefore).path("finishedAt").asText()))
                .isEqualTo(dBefore.minusSeconds(1));

        // 2. At exact deadline -> already closed as TIME_UP at deadline_at
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt aExact = startAndAwaitOpen(userId, "bnd-exact", line.entitlementId(), null);
        Instant dExact =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aExact.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(dExact);
        MvcResult resExact = performSubmit(userId, aExact.getId(), "bnd-k-2", null, null);
        assertThat(resExact.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resExact).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(resExact).path("finishedAt").asText())).isEqualTo(dExact);

        // 3. At deadline + 1s -> closed as TIME_UP at deadline_at
        mutableClock.setAt(AttemptTestFixtures.DEFAULT_CLOCK_START);
        Attempt aAfter = startAndAwaitOpen(userId, "bnd-after", line.entitlementId(), null);
        Instant dAfter =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(aAfter.getId()).getFirst().getDeadlineAt();
        mutableClock.setAt(dAfter.plusSeconds(1));
        MvcResult resAfter = performSubmit(userId, aAfter.getId(), "bnd-k-3", null, null);
        assertThat(resAfter.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resAfter).path("endReason").asText()).isEqualTo("TIME_UP");
        assertThat(Instant.parse(json(resAfter).path("finishedAt").asText())).isEqualTo(dAfter);
    }
}
