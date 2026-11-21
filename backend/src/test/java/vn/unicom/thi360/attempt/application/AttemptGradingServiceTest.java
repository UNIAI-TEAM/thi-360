package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.shared.api.ConflictException;

/** Unit tests for {@link AttemptGradingService} and {@link Attempt#applyGradingResult} (R1-35, R1-36, R1-37). */
class AttemptGradingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptRepository attemptRepository;
    private QuestionSourcePort questionSourcePort;
    private PublishedExamPort publishedExamPort;
    private AttemptGradingService service;

    @BeforeEach
    void setUp() {
        attemptRepository = mock(AttemptRepository.class);
        AttemptPartRepository attemptPartRepository = mock(AttemptPartRepository.class);
        AttemptItemRepository attemptItemRepository = mock(AttemptItemRepository.class);
        AttemptEventRepository attemptEventRepository = mock(AttemptEventRepository.class);
        questionSourcePort = mock(QuestionSourcePort.class);
        publishedExamPort = mock(PublishedExamPort.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new AttemptGradingService(
                attemptRepository,
                attemptPartRepository,
                attemptItemRepository,
                attemptEventRepository,
                questionSourcePort,
                publishedExamPort,
                txManager,
                clock);
    }

    @Test
    void regradeRejectsUnfinishedAndAbortedAttemptsWithConflict() {
        for (AttemptState state : new AttemptState[] {AttemptState.STARTING, AttemptState.IN_PROGRESS, AttemptState.ABORTED}) {
            UUID attemptId = UUID.randomUUID();
            Attempt attempt = newAttempt(attemptId);
            if (state == AttemptState.IN_PROGRESS) {
                attempt.open(1, NOW.minusSeconds(60), NOW.plusSeconds(600));
            } else if (state == AttemptState.ABORTED) {
                attempt.abort(AbortReason.GENERATION_FAILED, NOW);
            }
            when(attemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));

            assertThatThrownBy(() -> service.regradeAttempt(attemptId))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ATTEMPT_NOT_FINISHED"));
        }
        verifyNoInteractions(questionSourcePort, publishedExamPort);
    }

    @Test
    void applyGradingResultPreservesPriorScoresAndResultJsonbWhenRegradingPendingReview() {
        UUID attemptId = UUID.randomUUID();
        Attempt attempt = newAttempt(attemptId);
        attempt.open(1, NOW.minusSeconds(300), NOW.plusSeconds(600));
        attempt.close(vn.unicom.thi360.attempt.domain.EndReason.SUBMITTED, NOW.minusSeconds(60), NOW.minusSeconds(60));

        Map<String, Object> oldResultJson = Map.of("rawScore", 45.50, "displayScore", 45.50, "pendingReview", false);
        Instant initialGradedAt = NOW.minusSeconds(30);
        attempt.applyGradingResult(
                false,
                new BigDecimal("50.00"),
                new BigDecimal("45.50"),
                new BigDecimal("45.50"),
                oldResultJson,
                initialGradedAt,
                initialGradedAt);

        // Simulate AT-5 reopen setting regrading=true & state=PENDING_REVIEW
        ReflectionTestUtils.setField(attempt, "regrading", true);
        ReflectionTestUtils.setField(attempt, "state", AttemptState.PENDING_REVIEW);

        Map<String, Object> newPendingJson = Map.of("pendingReview", true);
        attempt.applyGradingResult(
                true,
                new BigDecimal("55.00"),
                null,
                null,
                newPendingJson,
                NOW,
                NOW);

        assertThat(attempt.getState()).isEqualTo(AttemptState.PENDING_REVIEW);
        assertThat(attempt.isRegrading()).isTrue();
        assertThat(attempt.getMaxScore()).isEqualByComparingTo("50.00");
        assertThat(attempt.getRawScore()).isEqualByComparingTo("45.50");
        assertThat(attempt.getDisplayScore()).isEqualByComparingTo("45.50");
        assertThat(attempt.getGradedAt()).isEqualTo(initialGradedAt);
        assertThat(attempt.getResult()).isEqualTo(oldResultJson);
    }

    private static Attempt newAttempt(UUID id) {
        return Attempt.start(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Exam",
                UUID.randomUUID(),
                "HSA",
                "FULL",
                null,
                null,
                AttemptTiming.SHARED,
                NavigationMode.SEQUENTIAL,
                Map.of(),
                42L,
                UUID.randomUUID(),
                "FULL_SOLUTION",
                "RAW",
                null,
                new BigDecimal("50.00"),
                null,
                NOW);
    }
}
