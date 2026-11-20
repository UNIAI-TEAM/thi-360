package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;

/** Unit tests for {@link AttemptOverdueCloser} timeout and outer-transaction selection (R2-10). */
class AttemptOverdueCloserTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptRepository attemptRepository;
    private AttemptPartRepository attemptPartRepository;
    private AttemptLifecycleService lifecycleService;
    private PlatformTransactionManager transactionManager;
    private AttemptOverdueCloser closer;

    @BeforeEach
    void setUp() {
        attemptRepository = mock(AttemptRepository.class);
        attemptPartRepository = mock(AttemptPartRepository.class);
        lifecycleService = mock(AttemptLifecycleService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AttemptLifecycleService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(lifecycleService);
        transactionManager = mock(PlatformTransactionManager.class);

        closer =
                new AttemptOverdueCloser(
                        attemptRepository, attemptPartRepository, provider, transactionManager);
    }

    @AfterEach
    void cleanTransactionState() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void requiresNewTxConfiguredWith15SecondTimeoutAndProperPropagations() {
        TransactionTemplate requiresNewTx =
                (TransactionTemplate) ReflectionTestUtils.getField(closer, "requiresNewTx");
        TransactionTemplate requiredTx =
                (TransactionTemplate) ReflectionTestUtils.getField(closer, "requiredTx");

        assertThat(requiresNewTx).isNotNull();
        assertThat(requiresNewTx.getTimeout()).isEqualTo(15);
        assertThat(requiresNewTx.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThat(requiredTx).isNotNull();
        assertThat(requiredTx.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Test
    void selectsRequiresNewInsideReadOnlyOuterTxAndRequiredInsideReadWriteOuterTx() {
        UUID attemptId = UUID.randomUUID();
        Attempt attempt = openAttempt(attemptId);

        when(attemptRepository.hasOverdueDeadline(attemptId, NOW)).thenReturn(true);
        when(attemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId))
                .thenReturn(List.of());

        List<TransactionDefinition> capturedDefs = new ArrayList<>();
        when(transactionManager.getTransaction(any()))
                .thenAnswer(
                        inv -> {
                            capturedDefs.add(inv.getArgument(0));
                            return new SimpleTransactionStatus();
                        });

        // 1. Active read-only outer transaction -> must fall back to requiresNewTx (REQUIRES_NEW, timeout 15s)
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        closer.closeIfOverdue(attemptId, NOW);

        assertThat(capturedDefs).hasSize(1);
        assertThat(capturedDefs.get(0).getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(capturedDefs.get(0).getTimeout()).isEqualTo(15);

        // 2. Active read-write outer transaction -> must reuse outer tx via requiredTx (PROPAGATION_REQUIRED)
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        closer.closeIfOverdue(attemptId, NOW);

        assertThat(capturedDefs).hasSize(2);
        assertThat(capturedDefs.get(1).getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);

        verify(lifecycleService, times(2)).advanceOverdueInLock(attempt, List.of(), NOW);
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
                        "Closer Unit Exam",
                        UUID.randomUUID(),
                        "HSA",
                        "FULL",
                        null,
                        null,
                        AttemptTiming.SHARED,
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
        attempt.open(1, NOW.minusSeconds(500), NOW.minusSeconds(10));
        return attempt;
    }
}
