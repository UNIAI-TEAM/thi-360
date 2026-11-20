package vn.unicom.thi360.attempt.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Cleans up attempts stuck in {@code STARTING} past {@code stale-starting-after} (default 2 minutes),
 * setting {@code ABORTED(STALE_STARTING)} and releasing the reserved entitlement once via
 * {@code FOR UPDATE SKIP LOCKED} (LT §5.2, AC-10).
 */
@Component
public class AttemptStaleWorker {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");
    private static final int MAX_ATTEMPTS_PER_SWEEP = 200;

    private final AttemptRepository attemptRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final EntitlementPort entitlementPort;
    private final AttemptGenerationProperties properties;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public AttemptStaleWorker(
            AttemptRepository attemptRepository,
            AttemptEventRepository attemptEventRepository,
            EntitlementPort entitlementPort,
            AttemptGenerationProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.entitlementPort = Objects.requireNonNull(entitlementPort);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.requiresNewTx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Aborts any {@code STARTING} attempts created strictly before {@code now - staleStartingAfter}. */
    public int sweepStaleStarting() {
        int aborted = 0;
        for (int i = 0; i < MAX_ATTEMPTS_PER_SWEEP; i++) {
            Boolean processed = requiresNewTx.execute(status -> abortNextStale());
            if (!Boolean.TRUE.equals(processed)) {
                break;
            }
            aborted++;
        }
        return aborted;
    }

    private boolean abortNextStale() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.staleStartingAfter());
        List<Attempt> stale = attemptRepository.findStaleStartingForUpdate(cutoff, 1);
        if (stale.isEmpty()) {
            return false;
        }
        Attempt attempt = stale.getFirst();
        if (attempt.getState() != AttemptState.STARTING) {
            return false;
        }
        attempt.abort(AbortReason.STALE_STARTING, now);
        attemptRepository.saveAndFlush(attempt);
        attemptEventRepository.save(new AttemptEvent(
                Ids.newId(),
                attempt.getId(),
                AttemptEventType.GENERATION_FAILED,
                null,
                null,
                now,
                Map.of(
                        "reason", AbortReason.STALE_STARTING.name(),
                        "examId", attempt.getExamId().toString(),
                        "examVersionNo", attempt.getExamVersionNo())));
        entitlementPort.release(attempt.getId());
        jobLog.atWarn()
                .addKeyValue("job", "attempt-stale-starting")
                .addKeyValue("attemptId", attempt.getId())
                .addKeyValue("examId", attempt.getExamId())
                .addKeyValue("examVersionNo", attempt.getExamVersionNo())
                .addKeyValue("reason", AbortReason.STALE_STARTING.name())
                .log("Aborted stale STARTING attempt and released entitlement");
        return true;
    }

    /** Scheduled runner active in {@code worker} profile unless disabled (plan B5, disabled in {@code test}). */
    @Component
    @Profile("worker")
    @ConditionalOnProperty(
            name = "thi360.attempt.scheduling.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public static class Scheduler {

        private final AttemptStaleWorker worker;

        public Scheduler(AttemptStaleWorker worker) {
            this.worker = Objects.requireNonNull(worker);
        }

        @Scheduled(fixedDelayString = "${thi360.attempt.scheduling.stale-starting-interval-ms:30000}")
        public void run() {
            worker.sweepStaleStarting();
        }
    }
}
