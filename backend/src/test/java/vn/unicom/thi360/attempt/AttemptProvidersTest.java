package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AttemptActiveChecker;
import vn.unicom.thi360.attempt.application.AttemptLearningProvider;
import vn.unicom.thi360.attempt.application.AttemptLearningRow;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.StudentLearningData;

/** Unit tests for {@link AttemptActiveChecker} and {@link AttemptLearningProvider} (AC-32). */
class AttemptProvidersTest {

    @Test
    void activeAttemptCheckerChecksStartingAndInProgressStates() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptActiveChecker checker = new AttemptActiveChecker(repo);
        UUID userId = UUID.randomUUID();

        assertThat(checker.hasActiveAttempt(null)).isFalse();
        verifyNoInteractions(repo);

        when(repo.existsByUserIdAndStateIn(userId, List.of(AttemptState.STARTING, AttemptState.IN_PROGRESS)))
                .thenReturn(true);
        assertThat(checker.hasActiveAttempt(userId)).isTrue();
        verify(repo).existsByUserIdAndStateIn(userId, List.of(AttemptState.STARTING, AttemptState.IN_PROGRESS));
    }

    @Test
    void learningProviderReturnsEmptyWhenNoNonAbortedAttemptsExist() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of());

        StudentLearningData data = provider.loadForUser(userId);
        assertThat(data.dataAvailable()).isFalse();
        assertThat(data.attempts()).isEmpty();
        assertThat(provider.loadForUser(null).dataAvailable()).isFalse();
    }

    @Test
    void learningProviderMapsVisibleAttemptsAndSetsDataAvailableTrue() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt attempt = Attempt.start(
                attemptId, userId, examId, 2, UUID.randomUUID(), UUID.randomUUID(),
                "V-ACT Full", UUID.randomUUID(), "V_ACT", "FULL", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                42L, UUID.randomUUID(), "FULL_SOLUTION", "LINEAR",
                new BigDecimal("1200.0000"), new BigDecimal("1200.0000"), "k", now);
        attempt.open(1, now.plusSeconds(3), now.plusSeconds(9003));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(attempt));

        StudentLearningData data = provider.loadForUser(userId);
        assertThat(data.dataAvailable()).isTrue();
        assertThat(data.attempts()).hasSize(1);
        AttemptLearningRow row = (AttemptLearningRow) data.attempts().getFirst();
        assertThat(row.attemptId()).isEqualTo(attemptId);
        assertThat(row.examId()).isEqualTo(examId);
        assertThat(row.examKindCode()).isEqualTo("V_ACT");
        assertThat(row.attemptNo()).isEqualTo(1);
        assertThat(row.state()).isEqualTo(AttemptState.IN_PROGRESS);
    }
}
