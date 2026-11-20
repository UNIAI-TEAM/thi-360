package vn.unicom.thi360.attempt.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;

/**
 * Worker sweeping overdue {@code IN_PROGRESS} attempts and parts using
 * {@code FOR UPDATE SKIP LOCKED}, closing each part/attempt strictly at its {@code deadline_at}
 * (LT F11, plan B5, AC-10, AC-17).
 */
@Component
public class AttemptDeadlineWorker {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");
    private static final int MAX_ATTEMPTS_PER_SWEEP = 200;
    private static final int MAX_BATCHES_PER_TICK = 10;
    private static final int ATTEMPT_SWEEP_TX_TIMEOUT_SECONDS = 15;

    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptLifecycleService attemptLifecycleService;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public AttemptDeadlineWorker(
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptLifecycleService attemptLifecycleService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptLifecycleService = Objects.requireNonNull(attemptLifecycleService);
        this.requiresNewTx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx.setTimeout(ATTEMPT_SWEEP_TX_TIMEOUT_SECONDS);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Drains up to {@value #MAX_BATCHES_PER_TICK} batches of {@value #MAX_ATTEMPTS_PER_SWEEP} overdue attempts. */
    public int sweepOverdue() {
        int progressed = 0;
        Set<UUID> attemptedInTick = new HashSet<>();
        for (int batch = 0; batch < MAX_BATCHES_PER_TICK; batch++) {
            Instant now = clock.instant();
            List<UUID> candidateIds =
                    attemptRepository.findOverdueAttemptIdsExcluding(
                            now, attemptedInTick, MAX_ATTEMPTS_PER_SWEEP);
            if (candidateIds.isEmpty()) {
                break;
            }
            for (UUID attemptId : candidateIds) {
                attemptedInTick.add(attemptId);
                try {
                    Boolean mutated = requiresNewTx.execute(status -> processAttemptById(attemptId));
                    if (Boolean.TRUE.equals(mutated)) {
                        progressed++;
                    }
                } catch (RuntimeException ex) {
                    jobLog.atError()
                            .addKeyValue("job", "attempt-deadline")
                            .addKeyValue("attemptId", attemptId)
                            .setCause(ex)
                            .log("Failed to close overdue attempt/part; continuing sweep");
                }
            }
            if (candidateIds.size() < MAX_ATTEMPTS_PER_SWEEP) {
                break;
            }
            if (batch == MAX_BATCHES_PER_TICK - 1) {
                jobLog.atWarn()
                        .addKeyValue("job", "attempt-deadline")
                        .addKeyValue("batches", MAX_BATCHES_PER_TICK)
                        .addKeyValue("attemptedCount", attemptedInTick.size())
                        .addKeyValue("progressedCount", progressed)
                        .log("Attempt deadline worker reached max batches per tick cap");
            }
        }
        return progressed;
    }

    private boolean processAttemptById(UUID attemptId) {
        Optional<Attempt> locked = attemptRepository.lockInProgressByIdSkipLocked(attemptId);
        if (locked.isEmpty()) {
            return false;
        }
        Attempt attempt = locked.get();
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            return false;
        }
        List<AttemptPart> parts =
                attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
        return attemptLifecycleService.advanceOverdueInLock(attempt, parts, clock.instant());
    }

    /** Scheduled runner active in {@code worker} profile unless disabled (plan B5, disabled in {@code test}). */
    @Component
    @Profile("worker")
    @ConditionalOnProperty(
            name = "thi360.attempt.scheduling.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public static class Scheduler {

        private final AttemptDeadlineWorker worker;

        public Scheduler(AttemptDeadlineWorker worker) {
            this.worker = Objects.requireNonNull(worker);
        }

        @Scheduled(fixedDelayString = "${thi360.attempt.scheduling.deadline-interval-ms:10000}")
        public void run() {
            worker.sweepOverdue();
        }
    }
}
