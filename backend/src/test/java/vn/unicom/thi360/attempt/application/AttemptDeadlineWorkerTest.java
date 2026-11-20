package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;

/** Unit tests for {@link AttemptDeadlineWorker} multi-batch draining and tick cap (R2-6). */
class AttemptDeadlineWorkerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptRepository attemptRepository;
    private AttemptPartRepository attemptPartRepository;
    private AttemptLifecycleService attemptLifecycleService;
    private AttemptDeadlineWorker worker;

    @BeforeEach
    void setUp() {
        attemptRepository = mock(AttemptRepository.class);
        attemptPartRepository = mock(AttemptPartRepository.class);
        attemptLifecycleService = mock(AttemptLifecycleService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        when(attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(any())).thenReturn(List.of());

        worker =
                new AttemptDeadlineWorker(
                        attemptRepository,
                        attemptPartRepository,
                        attemptLifecycleService,
                        transactionManager,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sweepOverdueDrainsMultipleBatchesAndExcludesAllPriorBatchIds() {
        List<UUID> batch0 = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            batch0.add(UUID.randomUUID());
        }
        UUID failingId = batch0.get(0);
        UUID lockedId = batch0.get(1);
        UUID remainingId = UUID.randomUUID();

        List<Set<UUID>> capturedExcluded = new ArrayList<>();
        when(attemptRepository.findOverdueAttemptIdsExcluding(any(), any(), eq(200)))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Collection<UUID> excluded = inv.getArgument(1);
                            capturedExcluded.add(Set.copyOf(excluded));
                            if (capturedExcluded.size() == 1) {
                                return batch0;
                            }
                            if (capturedExcluded.size() == 2) {
                                return List.of(remainingId);
                            }
                            return List.of();
                        });

        when(attemptRepository.lockInProgressByIdSkipLocked(any()))
                .thenAnswer(
                        inv -> {
                            UUID id = inv.getArgument(0);
                            if (id.equals(lockedId)) {
                                return Optional.empty();
                            }
                            return Optional.of(openAttempt(id));
                        });

        when(attemptLifecycleService.advanceOverdueInLock(any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            Attempt attempt = inv.getArgument(0);
                            if (attempt.getId().equals(failingId)) {
                                throw new RuntimeException("simulated failure");
                            }
                            return true;
                        });

        int progressed = worker.sweepOverdue();

        assertThat(progressed).isEqualTo(199);
        verify(attemptRepository, times(2)).findOverdueAttemptIdsExcluding(any(), any(), eq(200));
        assertThat(capturedExcluded).hasSize(2);
        assertThat(capturedExcluded.get(0)).isEmpty();
        assertThat(capturedExcluded.get(1)).containsExactlyInAnyOrderElementsOf(batch0);
        verify(attemptLifecycleService)
                .advanceOverdueInLock(argThat(a -> remainingId.equals(a.getId())), any(), any());
    }

    @Test
    void sweepOverdueStopsAtMaxBatchesPerTickCap() {
        AtomicInteger batchCounter = new AtomicInteger();
        List<Set<UUID>> capturedExcluded = new ArrayList<>();
        when(attemptRepository.findOverdueAttemptIdsExcluding(any(), any(), eq(200)))
                .thenAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            Collection<UUID> excluded = inv.getArgument(1);
                            capturedExcluded.add(Set.copyOf(excluded));
                            int batchIdx = batchCounter.getAndIncrement();
                            List<UUID> ids = new ArrayList<>(200);
                            for (int i = 0; i < 200; i++) {
                                ids.add(UUID.randomUUID());
                            }
                            assertThat(excluded).hasSize(batchIdx * 200);
                            return ids;
                        });

        when(attemptRepository.lockInProgressByIdSkipLocked(any()))
                .thenAnswer(inv -> Optional.of(openAttempt(inv.getArgument(0))));
        when(attemptLifecycleService.advanceOverdueInLock(any(), any(), any())).thenReturn(true);

        int progressed = worker.sweepOverdue();

        assertThat(progressed).isEqualTo(2000);
        verify(attemptRepository, times(10)).findOverdueAttemptIdsExcluding(any(), any(), eq(200));
        assertThat(capturedExcluded).hasSize(10);
    }

    private static Attempt openAttempt(UUID attemptId) {
        Attempt attempt =
                Attempt.start(
                        attemptId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Worker Unit Exam",
                        UUID.randomUUID(),
                        "HSA",
                        "FULL",
                        null,
                        null,
                        AttemptTiming.PER_PART,
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        1L,
                        UUID.randomUUID(),
                        "SCORE_AND_ANSWERS",
                        null,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        "idem-" + attemptId,
                        NOW.minusSeconds(600));
        attempt.open(1, NOW.minusSeconds(500), null);
        return attempt;
    }
}
