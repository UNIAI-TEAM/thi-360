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
import org.springframework.test.util.ReflectionTestUtils;
import vn.unicom.thi360.attempt.application.AttemptActiveChecker;
import vn.unicom.thi360.attempt.application.AttemptLearningProvider;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.AttemptItem;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.AttemptLearningStatus;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.StudentLearningData;

/** Unit tests for {@link AttemptActiveChecker} and {@link AttemptLearningProvider} (AC-32, LP). */
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
    void learningProviderMapsVisibleAttemptsAndGatesScoresStrictlyToScored() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();
        UUID inProgressId = UUID.randomUUID();
        UUID scoredLinearId = UUID.randomUUID();
        UUID regradingPendingId = UUID.randomUUID();
        UUID gradingErrorId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt inProgress = Attempt.start(
                inProgressId, userId, examId, 2, UUID.randomUUID(), UUID.randomUUID(),
                "V-ACT Full", UUID.randomUUID(), "V_ACT", "FULL", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                42L, UUID.randomUUID(), "FULL_SOLUTION", "LINEAR",
                new BigDecimal("1200.0000"), new BigDecimal("100.0000"), "k1", now);
        inProgress.open(1, now.plusSeconds(3), now.plusSeconds(9003));

        Attempt scoredLinear = Attempt.start(
                scoredLinearId, userId, examId, 2, UUID.randomUUID(), UUID.randomUUID(),
                "V-ACT Full", UUID.randomUUID(), "V_ACT", "FULL", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                43L, UUID.randomUUID(), "FULL_SOLUTION", "LINEAR",
                new BigDecimal("1200.0000"), new BigDecimal("100.0000"), "k2", now);
        scoredLinear.open(2, now.plusSeconds(10), now.plusSeconds(9010));
        scoredLinear.close(EndReason.SUBMITTED, now.plusSeconds(600), now.plusSeconds(600));
        scoredLinear.applyGradingResult(
                false,
                new BigDecimal("100.0000"),
                new BigDecimal("75.0000"),
                new BigDecimal("900.0000"),
                Map.of("scaleMax", "1200.00"),
                now.plusSeconds(605),
                now.plusSeconds(605));

        Attempt regradingPending = Attempt.start(
                regradingPendingId, userId, examId, 2, UUID.randomUUID(), UUID.randomUUID(),
                "BCA Full", UUID.randomUUID(), "BCA", "FULL", null, null,
                AttemptTiming.PER_PART, NavigationMode.SEQUENTIAL, Map.of(),
                44L, UUID.randomUUID(), "SCORE_AND_ANSWERS", null,
                null, new BigDecimal("100.0000"), "k3", now);
        regradingPending.open(3, now.plusSeconds(20), now.plusSeconds(9020));
        regradingPending.close(EndReason.SUBMITTED, now.plusSeconds(700), now.plusSeconds(700));
        // Previously scored, now back to PENDING_REVIEW under regrade (score columns still non-null)
        regradingPending.applyGradingResult(
                false,
                new BigDecimal("100.0000"),
                new BigDecimal("45.50"),
                new BigDecimal("45.50"),
                Map.of(),
                now.plusSeconds(705),
                now.plusSeconds(705));
        ReflectionTestUtils.setField(regradingPending, "regrading", true);
        regradingPending.applyGradingResult(
                true,
                new BigDecimal("100.0000"),
                null,
                null,
                Map.of(),
                now.plusSeconds(710),
                now.plusSeconds(710));
        assertThat(regradingPending.getDisplayScore()).isEqualByComparingTo("45.50");

        Attempt gradingError = Attempt.start(
                gradingErrorId, userId, examId, 2, UUID.randomUUID(), UUID.randomUUID(),
                "HSA Math", UUID.randomUUID(), "HSA", "PART", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                45L, UUID.randomUUID(), "SCORE", null,
                null, new BigDecimal("50.0000"), "k4", now);
        gradingError.open(4, now.plusSeconds(30), now.plusSeconds(3630));
        gradingError.close(EndReason.SUBMITTED, now.plusSeconds(800), now.plusSeconds(800));
        gradingError.markGradingError("Internal error", now.plusSeconds(805));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(inProgress, scoredLinear, regradingPending, gradingError));

        StudentLearningData data = provider.loadForUser(userId);
        assertThat(data.dataAvailable()).isTrue();
        assertThat(data.attempts()).hasSize(4);

        AttemptItem row0 = data.attempts().get(0);
        assertThat(row0.attemptId()).isEqualTo(inProgressId);
        assertThat(row0.examId()).isEqualTo(examId);
        assertThat(row0.examKindCode()).isEqualTo("V_ACT");
        assertThat(row0.attemptNo()).isEqualTo(1);
        assertThat(row0.status()).isEqualTo(AttemptLearningStatus.IN_PROGRESS);
        assertThat(row0.score()).isNull();
        assertThat(row0.maxScore()).isNull();

        AttemptItem row1 = data.attempts().get(1);
        assertThat(row1.attemptId()).isEqualTo(scoredLinearId);
        assertThat(row1.status()).isEqualTo(AttemptLearningStatus.SCORED);
        assertThat(row1.score()).isEqualByComparingTo("900.0000");
        assertThat(row1.maxScore()).isEqualByComparingTo("1200.00");

        AttemptItem row2 = data.attempts().get(2);
        assertThat(row2.attemptId()).isEqualTo(regradingPendingId);
        assertThat(row2.status()).isEqualTo(AttemptLearningStatus.PENDING_REVIEW);
        assertThat(row2.score()).isNull();
        assertThat(row2.maxScore()).isNull();

        AttemptItem row3 = data.attempts().get(3);
        assertThat(row3.attemptId()).isEqualTo(gradingErrorId);
        assertThat(row3.status()).isEqualTo(AttemptLearningStatus.GRADING_ERROR);
        assertThat(row3.score()).isNull();
        assertThat(row3.maxScore()).isNull();
    }
}
