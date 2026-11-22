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
import java.util.HashMap;
import java.util.List;
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
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.AggregatedScore;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.ConflictException;

/** Unit tests for {@link AttemptGradingService} and {@link Attempt#applyGradingResult} (R1-35, R1-36, R1-37, R2-1). */
class AttemptGradingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptRepository attemptRepository;
    private AttemptPartRepository attemptPartRepository;
    private AttemptItemRepository attemptItemRepository;
    private QuestionSourcePort questionSourcePort;
    private PublishedExamPort publishedExamPort;
    private AttemptGradingService service;

    @BeforeEach
    void setUp() {
        attemptRepository = mock(AttemptRepository.class);
        attemptPartRepository = mock(AttemptPartRepository.class);
        attemptItemRepository = mock(AttemptItemRepository.class);
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

    @Test
    void reaggregateLockedAttemptAndRegradeAcceptGradingDetailWithNullValues() {
        // R2-1: JSONB grading_detail containing null values (e.g. AT-5 comment=null) must not throw NPE
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        UUID setParentId = UUID.randomUUID();
        UUID essayChildId = UUID.randomUUID();

        Attempt attempt = newAttempt(attemptId);
        attempt.open(1, NOW.minusSeconds(300), NOW.plusSeconds(600));
        attempt.close(EndReason.SUBMITTED, NOW.minusSeconds(60), NOW.minusSeconds(60));
        ReflectionTestUtils.setField(attempt, "state", AttemptState.PENDING_REVIEW);

        AttemptPart part = AttemptPart.createPending(
                partId, attemptId, "P1", "Phần 1", 0, 30, 1, new BigDecimal("10.00"), 1L);
        part.open(NOW.minusSeconds(300), NOW.plusSeconds(600));
        part.close(PartCloseReason.ATTEMPT_SUBMITTED, NOW.minusSeconds(60));

        AttemptItem setParent = AttemptItem.create(
                setParentId, attemptId, partId, null, "sec-1", null, "blk-1",
                null, 0, 1, 1, UUID.randomUUID(), 1, QuestionType.QUESTION_SET, new BigDecimal("10.00"), List.of());
        AttemptItem essayChild = AttemptItem.create(
                essayChildId, attemptId, partId, null, "sec-1", null, "blk-1",
                setParentId, 1, 2, 1, UUID.randomUUID(), 1, QuestionType.ESSAY, new BigDecimal("10.00"), List.of());

        Map<String, Object> finalizedDetailWithNull = new HashMap<>();
        finalizedDetailWithNull.put("comment", null);
        finalizedDetailWithNull.put("wordCount", 12);
        finalizedDetailWithNull.put("exactFraction", "4");
        finalizedDetailWithNull.put("criteria", List.of(Map.of("id", "c1", "points", 4)));
        essayChild.recordGrading(new BigDecimal("4.00"), ItemOutcome.PARTIAL, finalizedDetailWithNull);

        when(attemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).thenReturn(List.of(part));
        when(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId))
                .thenReturn(List.of(setParent, essayChild));

        AggregatedScore reaggregated = service.reaggregateLockedAttempt(attempt, NOW);
        assertThat(reaggregated.pendingReview()).isFalse();
        assertThat(reaggregated.rawScore()).isEqualByComparingTo("4.00");
        assertThat(setParent.getOutcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(setParent.getEarnedPoints()).isEqualByComparingTo("4.00");
        assertThat(attempt.getState()).isEqualTo(AttemptState.SCORED);

        // Reset set parent to pending so regradeAttempt also exercises toCurrentItemGradeResult + rollup
        setParent.recordGrading(null, ItemOutcome.PENDING_REVIEW, new HashMap<>(Map.of("comment", "reset")));
        ReflectionTestUtils.setField(setParent, "gradingDetail", finalizedDetailWithNull);

        service.regradeAttempt(attemptId);
        assertThat(attempt.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(attempt.getRawScore()).isEqualByComparingTo("4.00");
        assertThat(setParent.getOutcome()).isEqualTo(ItemOutcome.PARTIAL);
        assertThat(setParent.getEarnedPoints()).isEqualByComparingTo("4.00");
        assertThat(setParent.getGradingDetail()).doesNotContainKey("comment");

        // Also cover ItemGradeResult.pendingReview path in toCurrentItemGradeResult during reaggregateLockedAttempt
        UUID pendingAttemptId = UUID.randomUUID();
        UUID pendingPartId = UUID.randomUUID();
        UUID pendingSetId = UUID.randomUUID();
        UUID pendingEssayId = UUID.randomUUID();
        Attempt pendingAttempt = newAttempt(pendingAttemptId);
        pendingAttempt.open(1, NOW.minusSeconds(300), NOW.plusSeconds(600));
        pendingAttempt.close(EndReason.SUBMITTED, NOW.minusSeconds(60), NOW.minusSeconds(60));
        ReflectionTestUtils.setField(pendingAttempt, "state", AttemptState.PENDING_REVIEW);

        AttemptPart pendingPart = AttemptPart.createPending(
                pendingPartId, pendingAttemptId, "P1", "Phần 1", 0, 30, 1, new BigDecimal("10.00"), 1L);
        pendingPart.open(NOW.minusSeconds(300), NOW.plusSeconds(600));
        pendingPart.close(PartCloseReason.ATTEMPT_SUBMITTED, NOW.minusSeconds(60));

        AttemptItem pendingSet = AttemptItem.create(
                pendingSetId, pendingAttemptId, pendingPartId, null, "sec-1", null, "blk-1",
                null, 0, 1, 1, UUID.randomUUID(), 1, QuestionType.QUESTION_SET, new BigDecimal("10.00"), List.of());
        AttemptItem pendingEssay = AttemptItem.create(
                pendingEssayId, pendingAttemptId, pendingPartId, null, "sec-1", null, "blk-1",
                pendingSetId, 1, 2, 1, UUID.randomUUID(), 1, QuestionType.ESSAY, new BigDecimal("10.00"), List.of());
        Map<String, Object> pendingDetailWithNull = new HashMap<>();
        pendingDetailWithNull.put("comment", null);
        pendingDetailWithNull.put("wordCount", 7);
        pendingEssay.recordGrading(null, ItemOutcome.PENDING_REVIEW, pendingDetailWithNull);

        when(attemptPartRepository.findByAttemptIdOrderByOrdAsc(pendingAttemptId)).thenReturn(List.of(pendingPart));
        when(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(pendingAttemptId))
                .thenReturn(List.of(pendingSet, pendingEssay));

        AggregatedScore pendingAgg = service.reaggregateLockedAttempt(pendingAttempt, NOW);
        assertThat(pendingAgg.pendingReview()).isTrue();
        assertThat(pendingSet.getOutcome()).isEqualTo(ItemOutcome.PENDING_REVIEW);
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
