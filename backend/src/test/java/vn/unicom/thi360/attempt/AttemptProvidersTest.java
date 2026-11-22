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
import vn.unicom.thi360.attempt.domain.AbortReason;
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

    @Test
    void resolveScaleMaxUsesResultScaleMaxDistinctFromReferenceAndAttemptMax() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt bigDecimalScale = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Exam BigDecimal Scale", UUID.randomUUID(), "HSA", "FULL", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                101L, UUID.randomUUID(), "FULL_SOLUTION", null,
                null, new BigDecimal("100.00"), "k-bd", now);
        bigDecimalScale.open(1, now.plusSeconds(5), now.plusSeconds(3605));
        bigDecimalScale.close(EndReason.SUBMITTED, now.plusSeconds(500), now.plusSeconds(500));
        bigDecimalScale.applyGradingResult(
                false,
                new BigDecimal("100.00"),
                new BigDecimal("75.00"),
                new BigDecimal("450.00"),
                Map.of("scaleMax", new BigDecimal("600.00")),
                now.plusSeconds(505),
                now.plusSeconds(505));

        Attempt doubleScale = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Exam Double Scale", UUID.randomUUID(), "V_ACT", "FULL", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                102L, UUID.randomUUID(), "FULL_SOLUTION", "LINEAR",
                new BigDecimal("1200.00"), new BigDecimal("100.00"), "k-dbl", now);
        doubleScale.open(2, now.plusSeconds(10), now.plusSeconds(3610));
        doubleScale.close(EndReason.SUBMITTED, now.plusSeconds(600), now.plusSeconds(600));
        doubleScale.applyGradingResult(
                false,
                new BigDecimal("100.00"),
                new BigDecimal("60.00"),
                new BigDecimal("510.30"),
                Map.of("scaleMax", Double.valueOf(850.5)),
                now.plusSeconds(605),
                now.plusSeconds(605));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(bigDecimalScale, doubleScale));

        StudentLearningData data = provider.loadForUser(userId);
        assertThat(data.attempts()).hasSize(2);
        assertThat(data.attempts().get(0).score()).isEqualByComparingTo("450.00");
        assertThat(data.attempts().get(0).maxScore()).isEqualByComparingTo("600.00");
        assertThat(data.attempts().get(1).score()).isEqualByComparingTo("510.30");
        assertThat(data.attempts().get(1).maxScore()).isEqualByComparingTo("850.5");
    }

    @Test
    void resolveScaleMaxFallsBackToLinearReferenceMaxWhenResultScaleMaxMissingOrMalformed() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt emptyResult = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Linear Empty Result", UUID.randomUUID(), "V_ACT", "SUBJECT", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                103L, UUID.randomUUID(), "FULL_SOLUTION", "LINEAR",
                new BigDecimal("1200.00"), new BigDecimal("100.00"), "k-lin-empty", now);
        emptyResult.open(1, now.plusSeconds(5), now.plusSeconds(3605));
        emptyResult.close(EndReason.SUBMITTED, now.plusSeconds(500), now.plusSeconds(500));
        emptyResult.applyGradingResult(
                false,
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("600.00"),
                Map.of(),
                now.plusSeconds(505),
                now.plusSeconds(505));

        Attempt malformedScaleMax = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Linear Malformed ScaleMax", UUID.randomUUID(), "V_ACT", "SUBJECT", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                104L, UUID.randomUUID(), "FULL_SOLUTION", "LINEAR",
                new BigDecimal("1200.00"), new BigDecimal("100.00"), "k-lin-bad", now);
        malformedScaleMax.open(2, now.plusSeconds(10), now.plusSeconds(3610));
        malformedScaleMax.close(EndReason.SUBMITTED, now.plusSeconds(600), now.plusSeconds(600));
        malformedScaleMax.applyGradingResult(
                false,
                new BigDecimal("100.00"),
                new BigDecimal("75.00"),
                new BigDecimal("900.00"),
                Map.of("scaleMax", "not-a-number"),
                now.plusSeconds(605),
                now.plusSeconds(605));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(emptyResult, malformedScaleMax));

        StudentLearningData data = provider.loadForUser(userId);
        assertThat(data.attempts()).hasSize(2);
        assertThat(data.attempts().get(0).maxScore()).isEqualByComparingTo("1200.00");
        assertThat(data.attempts().get(1).maxScore()).isEqualByComparingTo("1200.00");
    }

    @Test
    void resolveScaleMaxFallsBackToAttemptMaxScoreForRawOrZeroReferenceMax() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt rawScoring = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Raw Scoring", UUID.randomUUID(), "HSA", "PART", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                105L, UUID.randomUUID(), "SCORE", "RAW",
                new BigDecimal("1200.00"), new BigDecimal("100.00"), "k-raw", now);
        rawScoring.open(1, now.plusSeconds(5), now.plusSeconds(3605));
        rawScoring.close(EndReason.SUBMITTED, now.plusSeconds(500), now.plusSeconds(500));
        rawScoring.applyGradingResult(
                false,
                new BigDecimal("100.00"),
                new BigDecimal("75.00"),
                new BigDecimal("75.00"),
                Map.of(),
                now.plusSeconds(505),
                now.plusSeconds(505));

        Attempt linearZeroRefMax = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Linear Zero RefMax", UUID.randomUUID(), "HSA", "PART", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                106L, UUID.randomUUID(), "SCORE", "LINEAR",
                BigDecimal.ZERO, new BigDecimal("80.00"), "k-zero-ref", now);
        linearZeroRefMax.open(2, now.plusSeconds(10), now.plusSeconds(3610));
        linearZeroRefMax.close(EndReason.SUBMITTED, now.plusSeconds(600), now.plusSeconds(600));
        linearZeroRefMax.applyGradingResult(
                false,
                new BigDecimal("80.00"),
                new BigDecimal("64.00"),
                new BigDecimal("64.00"),
                Map.of(),
                now.plusSeconds(605),
                now.plusSeconds(605));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(rawScoring, linearZeroRefMax));

        StudentLearningData data = provider.loadForUser(userId);
        assertThat(data.attempts()).hasSize(2);
        assertThat(data.attempts().get(0).maxScore()).isEqualByComparingTo("100.00");
        assertThat(data.attempts().get(1).maxScore()).isEqualByComparingTo("80.00");
    }

    @Test
    void scoredAttemptWithoutDisplayScorePairsRawScoreWithAttemptMaxScore() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt rawFallback = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Scored Without DisplayScore", UUID.randomUUID(), "V_ACT", "FULL", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                107L, UUID.randomUUID(), "SCORE", "LINEAR",
                new BigDecimal("1200.00"), new BigDecimal("100.00"), "k-raw-fallback", now);
        rawFallback.open(1, now.plusSeconds(5), now.plusSeconds(3605));
        rawFallback.close(EndReason.SUBMITTED, now.plusSeconds(500), now.plusSeconds(500));
        rawFallback.applyGradingResult(
                false,
                new BigDecimal("100.00"),
                new BigDecimal("75.00"),
                null,
                Map.of("scaleMax", "1200.00"),
                now.plusSeconds(505),
                now.plusSeconds(505));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(rawFallback));

        StudentLearningData data = provider.loadForUser(userId);
        assertThat(data.attempts()).hasSize(1);
        assertThat(data.attempts().getFirst().status()).isEqualTo(AttemptLearningStatus.SCORED);
        assertThat(data.attempts().getFirst().score()).isEqualByComparingTo("75.00");
        assertThat(data.attempts().getFirst().maxScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void gradingSuppressesScoresAndExcludedStartingAbortedAreFilteredDefensively() {
        AttemptRepository repo = mock(AttemptRepository.class);
        AttemptLearningProvider provider = new AttemptLearningProvider(repo);
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        Attempt grading = Attempt.start(
                UUID.randomUUID(), userId, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Grading Attempt", UUID.randomUUID(), "HSA", "PART", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                108L, UUID.randomUUID(), "SCORE", null,
                null, new BigDecimal("50.00"), "k-grading", now);
        grading.open(1, now.plusSeconds(5), now.plusSeconds(3605));
        grading.close(EndReason.SUBMITTED, now.plusSeconds(500), now.plusSeconds(500));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                userId, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(grading));

        StudentLearningData gradingData = provider.loadForUser(userId);
        assertThat(gradingData.dataAvailable()).isTrue();
        assertThat(gradingData.attempts()).hasSize(1);
        assertThat(gradingData.attempts().getFirst().status()).isEqualTo(AttemptLearningStatus.GRADING);
        assertThat(gradingData.attempts().getFirst().score()).isNull();
        assertThat(gradingData.attempts().getFirst().maxScore()).isNull();

        UUID excludedOnlyUser = UUID.randomUUID();
        Attempt starting = Attempt.start(
                UUID.randomUUID(), excludedOnlyUser, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Starting Attempt", UUID.randomUUID(), "HSA", "PART", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                109L, UUID.randomUUID(), "SCORE", null,
                null, new BigDecimal("50.00"), "k-starting", now);
        Attempt aborted = Attempt.start(
                UUID.randomUUID(), excludedOnlyUser, examId, 1, UUID.randomUUID(), UUID.randomUUID(),
                "Aborted Attempt", UUID.randomUUID(), "HSA", "PART", null, null,
                AttemptTiming.SHARED, NavigationMode.SEQUENTIAL, Map.of(),
                110L, UUID.randomUUID(), "SCORE", null,
                null, new BigDecimal("50.00"), "k-aborted", now);
        aborted.abort(AbortReason.GENERATION_FAILED, now.plusSeconds(30));

        when(repo.findByUserIdAndStateNotInOrderByStartedAtDescCreatedAtDesc(
                excludedOnlyUser, List.of(AttemptState.STARTING, AttemptState.ABORTED)))
                .thenReturn(List.of(starting, aborted));

        StudentLearningData excludedData = provider.loadForUser(excludedOnlyUser);
        assertThat(excludedData.dataAvailable()).isFalse();
        assertThat(excludedData.attempts()).isEmpty();
    }
}
