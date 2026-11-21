package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

class AttemptGradingFailureIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;
    @MockitoSpyBean private QuestionSourcePort questionSourcePort;

    @BeforeEach
    void clearPendingOutboxBacklog() {
        Mockito.reset(questionSourcePort);
        drainAllOutbox();
    }

    @AfterEach
    void resetSpyAndDrainOutbox() {
        Mockito.reset(questionSourcePort);
        jdbc.update(
                "UPDATE shared.outbox_event SET failed_at = created_at WHERE failed_at IS NULL AND processed_at IS NULL");
    }

    @Test
    void failureDoesNotBlockOutbox() throws Exception {
        UUID failingUser = createStudent("grading-fail-isolated");
        UUID healthyUser = createStudent("grading-healthy");
        GrantOrderResult.Line failingGrant = grant(failingUser, "DS-HSA-PART-MATH-MINI", 2, List.of());
        GrantOrderResult.Line healthyGrant = grant(healthyUser, "DS-HSA-PART-MATH-MINI", 2, List.of());

        Attempt failingAttempt = startOpen(failingUser, failingGrant.entitlementId());
        Attempt healthyAttempt = startOpen(healthyUser, healthyGrant.entitlementId());

        Set<UUID> failingQuestionIds = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(failingAttempt.getId())
                .stream()
                .map(i -> i.getQuestionId())
                .collect(Collectors.toSet());

        UUID failSession = claimSession(failingUser, failingAttempt.getId());
        UUID healthySession = claimSession(healthyUser, healthyAttempt.getId());

        submitOk(failingUser, failingAttempt.getId(), failSession);
        submitOk(healthyUser, healthyAttempt.getId(), healthySession);
        // Drain non-attempt events (e.g. audit) queued before our attempt.finished events, OR handle all in one sweep
        Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<VersionKey> keys = (List<VersionKey>) inv.getArgument(0);
            if (keys.stream().anyMatch(k -> failingQuestionIds.contains(k.questionId()))) {
                throw new IllegalStateException("Simulated question bank outage");
            }
            return inv.callRealMethod();
        }).when(questionSourcePort).loadVersions(anyList());

        outboxRelay.relayBatch(500);

        Attempt stillGrading = attemptRepository.findById(failingAttempt.getId()).orElseThrow();
        Attempt healthyScored = attemptRepository.findById(healthyAttempt.getId()).orElseThrow();

        assertThat(stillGrading.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(healthyScored.getState()).isEqualTo(AttemptState.SCORED);

        Map<String, Object> failOutbox = outboxRowFor(failingAttempt.getId());
        assertThat(((Number) failOutbox.get("attempts")).intValue()).isEqualTo(1);
        assertThat(failOutbox.get("failed_at")).isNull();
    }

    @Test
    void exhaustedRetriesSetGradingError() throws Exception {
        UUID student = createStudent("grading-exhausted");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());

        Set<UUID> failingQuestionIds = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .map(i -> i.getQuestionId())
                .collect(Collectors.toSet());

        submitOk(student, attempt.getId(), sessionId);

        Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<VersionKey> keys = (List<VersionKey>) inv.getArgument(0);
            if (keys.stream().anyMatch(k -> failingQuestionIds.contains(k.questionId()))) {
                throw new IllegalStateException("Persistent snapshot outage");
            }
            return inv.callRealMethod();
        }).when(questionSourcePort).loadVersions(anyList());

        for (int attemptNo = 1; attemptNo <= OutboxRelay.DEFAULT_MAX_ATTEMPTS; attemptNo++) {
            outboxRelay.relayBatch(500);
            Attempt interim = attemptRepository.findById(attempt.getId()).orElseThrow();
            if (attemptNo < OutboxRelay.DEFAULT_MAX_ATTEMPTS) {
                assertThat(interim.getState()).isEqualTo(AttemptState.GRADING);
                mutableClock.advance(Duration.ofMinutes(10));
            }
        }

        Attempt failed = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(failed.getState()).isEqualTo(AttemptState.GRADING_ERROR);
        assertThat(failed.getGradingError()).contains("Persistent snapshot outage");
        assertThat(failed.getRawScore()).isNull();
        assertThat(failed.getDisplayScore()).isNull();
        assertThat(attemptEventRepository.existsByAttemptIdAndType(
                attempt.getId(), AttemptEventType.GRADING_FAILED)).isTrue();

        Map<String, Object> outbox = outboxRowFor(attempt.getId());
        assertThat(((Number) outbox.get("attempts")).intValue()).isEqualTo(OutboxRelay.DEFAULT_MAX_ATTEMPTS);
        assertThat(outbox.get("failed_at")).isNotNull();
    }

    @Test
    void gradingErrorShowsNoScore() throws Exception {
        UUID student = createStudent("grading-error-no-zero");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startOpen(student, grant.entitlementId());
        UUID sessionId = claimSession(student, attempt.getId());

        Set<UUID> failingQuestionIds = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .map(i -> i.getQuestionId())
                .collect(Collectors.toSet());

        submitOk(student, attempt.getId(), sessionId);

        AtomicInteger calls = new AtomicInteger();
        Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<VersionKey> keys = (List<VersionKey>) inv.getArgument(0);
            if (keys.stream().anyMatch(k -> failingQuestionIds.contains(k.questionId()))
                    && calls.incrementAndGet() <= 2) {
                throw new IllegalStateException("Transient outage #" + calls.get());
            }
            return inv.callRealMethod();
        }).when(questionSourcePort).loadVersions(anyList());

        outboxRelay.relayBatch(500);
        mutableClock.advance(Duration.ofMinutes(10));
        outboxRelay.relayBatch(500);

        Attempt mid = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(mid.getState()).isEqualTo(AttemptState.GRADING);
        assertThat(mid.getRawScore()).isNull();
        assertThat(mid.getDisplayScore()).isNull();

        // Third attempt recovers cleanly
        mutableClock.advance(Duration.ofMinutes(10));
        outboxRelay.relayBatch(500);

        Attempt recovered = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(recovered.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(recovered.getRawScore()).isNotNull();
        assertThat(recovered.getDisplayScore()).isNotNull();
    }

    private void submitOk(UUID userId, UUID attemptId, UUID sessionId) throws Exception {
        MvcResult result = performSubmit(
                userId, attemptId, UUID.randomUUID().toString(), sessionId, new SubmitAttemptRequest(List.of()));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private Attempt startOpen(UUID userId, UUID entitlementId) throws Exception {
        return startAndAwaitOpen(userId, UUID.randomUUID().toString(), entitlementId, null);
    }

    private Map<String, Object> outboxRowFor(UUID attemptId) {
        return jdbc.queryForMap(
                """
                        SELECT attempts, last_error, failed_at, processed_at
                        FROM shared.outbox_event
                        WHERE topic = 'attempt.finished'
                          AND CAST(payload AS text) LIKE ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                "%" + attemptId + "%");
    }

    private void drainAllOutbox() {
        int safety = 0;
        while (safety++ < 20 && outboxRelay.relayBatch(500) > 0) {
            // drain backlog
        }
    }
}
