package vn.unicom.thi360.attempt.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;

/**
 * Lazy overdue closer invoked before reads and commands so overdue parts and attempts close at
 * their exact deadline even before the deadline worker sweeps them (LT F11, plan B1, AC-11).
 */
@Component
public class AttemptOverdueCloser implements OverdueAttemptCloser {

    private static final int OVERDUE_CLOSE_TIMEOUT_SECONDS = 15;

    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final ObjectProvider<AttemptLifecycleService> lifecycleServiceProvider;
    private final TransactionTemplate requiredTx;
    private final TransactionTemplate requiresNewTx;

    @Autowired
    public AttemptOverdueCloser(
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            ObjectProvider<AttemptLifecycleService> lifecycleServiceProvider,
            PlatformTransactionManager transactionManager) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.lifecycleServiceProvider = Objects.requireNonNull(lifecycleServiceProvider);
        PlatformTransactionManager txManager = Objects.requireNonNull(transactionManager);
        this.requiredTx = new TransactionTemplate(txManager);
        this.requiredTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx.setTimeout(OVERDUE_CLOSE_TIMEOUT_SECONDS);
    }

    @Override
    public void closeIfOverdue(UUID attemptId, java.time.Instant now) {
        if (attemptId == null || now == null) {
            return;
        }
        // Scalar query avoids loading entities into any outer Hibernate L1 cache before locking.
        if (!attemptRepository.hasOverdueDeadline(attemptId, now)) {
            return;
        }
        // Reuse an active read-write outer transaction (getState, prepare, start) to avoid
        // opening a second HikariCP connection; use a bounded REQUIRES_NEW tx otherwise.
        boolean reuseOuterTx =
                TransactionSynchronizationManager.isActualTransactionActive()
                        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        TransactionTemplate tx = reuseOuterTx ? requiredTx : requiresNewTx;
        tx.executeWithoutResult(
                status -> {
                    // Blocking FOR UPDATE so synchronous caller never sees stale IN_PROGRESS past deadline.
                    Optional<Attempt> locked = attemptRepository.findByIdForUpdate(attemptId);
                    if (locked.isEmpty()) {
                        return;
                    }
                    Attempt attempt = locked.get();
                    if (attempt.getState() != AttemptState.IN_PROGRESS) {
                        return;
                    }
                    List<AttemptPart> parts =
                            attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
                    lifecycleServiceProvider.getObject().advanceOverdueInLock(attempt, parts, now);
                });
    }
}
