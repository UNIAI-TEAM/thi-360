package vn.unicom.thi360.attempt.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;

/**
 * Worker sweeping stuck {@link PartState#GENERATING} parts (> 2 minutes) and scheduling automatic
 * retries for {@link PartState#GENERATION_FAILED} parts at 30s, 2m, and 5m backoff delays (LT
 * §6.1, plan B2).
 */
@Component
public class PartGenerationRetryWorker {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    public static final Duration STUCK_GENERATING_AFTER = Duration.ofMinutes(2);
    public static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(5));

    private static final int MAX_PARTS_PER_SWEEP = 200;
    private static final int PART_RETRY_TX_TIMEOUT_SECONDS = 15;

    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptChoiceService attemptChoiceService;
    private final PublishedExamPort publishedExamPort;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    private record CandidatePart(UUID attemptId, UUID partId) {}

    public PartGenerationRetryWorker(
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptChoiceService attemptChoiceService,
            PublishedExamPort publishedExamPort,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptChoiceService = Objects.requireNonNull(attemptChoiceService);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.requiresNewTx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx.setTimeout(PART_RETRY_TX_TIMEOUT_SECONDS);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Sweeps stuck {@code GENERATING} parts and retries eligible {@code GENERATION_FAILED} parts. */
    public int sweep() {
        return failStuckGenerating() + retryFailedParts();
    }

    /** Fails parts stuck in {@link PartState#GENERATING} strictly longer than 2 minutes. */
    public int failStuckGenerating() {
        Instant cutoff = clock.instant().minus(STUCK_GENERATING_AFTER);
        List<CandidatePart> candidates =
                jdbcTemplate.query(
                        """
                        SELECT p.attempt_id, p.id
                        FROM attempt.attempt_part p
                        JOIN attempt.attempt a ON a.id = p.attempt_id
                        WHERE a.state = 'IN_PROGRESS'
                          AND p.state = 'GENERATING'
                          AND p.generating_since IS NOT NULL
                          AND p.generating_since < ?
                        ORDER BY p.generating_since ASC, p.id ASC
                        LIMIT ?
                        """,
                        (rs, n) ->
                                new CandidatePart(
                                        rs.getObject(1, UUID.class),
                                        rs.getObject(2, UUID.class)),
                        Timestamp.from(cutoff),
                        MAX_PARTS_PER_SWEEP);

        int failed = 0;
        for (CandidatePart candidate : candidates) {
            try {
                Boolean processed =
                        requiresNewTx.execute(
                                status ->
                                        failStuckById(
                                                candidate.attemptId(), candidate.partId()));
                if (Boolean.TRUE.equals(processed)) {
                    failed++;
                }
            } catch (RuntimeException ex) {
                jobLog.atError()
                        .addKeyValue("job", "attempt-part-stale-generating")
                        .addKeyValue("attemptId", candidate.attemptId())
                        .addKeyValue("partId", candidate.partId())
                        .setCause(ex)
                        .log("Failed to transition stale GENERATING part; continuing sweep");
            }
        }
        return failed;
    }

    /** Auto-retries {@link PartState#GENERATION_FAILED} parts whose backoff delay has elapsed. */
    public int retryFailedParts() {
        List<CandidatePart> candidates =
                jdbcTemplate.query(
                        """
                        SELECT p.attempt_id, p.id
                        FROM attempt.attempt_part p
                        JOIN attempt.attempt a ON a.id = p.attempt_id
                        WHERE a.state = 'IN_PROGRESS'
                          AND p.state = 'GENERATION_FAILED'
                          AND p.choice_confirmed_at IS NOT NULL
                          AND p.generation_tries BETWEEN 1 AND ?
                        ORDER BY p.choice_confirmed_at ASC, p.id ASC
                        LIMIT ?
                        """,
                        (rs, n) ->
                                new CandidatePart(
                                        rs.getObject(1, UUID.class),
                                        rs.getObject(2, UUID.class)),
                        RETRY_DELAYS.size(),
                        MAX_PARTS_PER_SWEEP);

        int retried = 0;
        for (CandidatePart candidate : candidates) {
            try {
                Boolean processed =
                        requiresNewTx.execute(
                                status ->
                                        retryFailedById(
                                                candidate.attemptId(), candidate.partId()));
                if (Boolean.TRUE.equals(processed)) {
                    retried++;
                }
            } catch (RuntimeException ex) {
                jobLog.atError()
                        .addKeyValue("job", "attempt-part-retry")
                        .addKeyValue("attemptId", candidate.attemptId())
                        .addKeyValue("partId", candidate.partId())
                        .setCause(ex)
                        .log("Failed to schedule part generation retry; continuing sweep");
            }
        }
        return retried;
    }

    private boolean failStuckById(UUID attemptId, UUID partId) {
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
        AttemptPart part = findPart(parts, partId);
        Instant now = clock.instant();
        Instant cutoff = now.minus(STUCK_GENERATING_AFTER);
        if (part == null
                || part.getState() != PartState.GENERATING
                || part.getGeneratingSince() == null
                || !part.getGeneratingSince().isBefore(cutoff)) {
            return false;
        }

        String examCode =
                publishedExamPort
                        .loadVersion(attempt.getExamId(), attempt.getExamVersionNo())
                        .map(PublishedVersion::code)
                        .orElse("");
        attemptChoiceService.recordPartGenerationFailureInLock(
                attempt,
                part,
                examCode,
                "STALE_GENERATING",
                "Part stayed in GENERATING longer than 2 minutes",
                null,
                now);
        return true;
    }

    private boolean retryFailedById(UUID attemptId, UUID partId) {
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
        AttemptPart part = findPart(parts, partId);
        if (part == null
                || part.getState() != PartState.GENERATION_FAILED
                || part.getChoiceConfirmedAt() == null
                || part.getChoice() == null) {
            return false;
        }
        int tries = part.getGenerationTries();
        if (tries < 1 || tries > RETRY_DELAYS.size()) {
            return false;
        }

        Timestamp lastFailedTs =
                jdbcTemplate.queryForObject(
                        """
                        SELECT MAX(at) FROM attempt.attempt_event
                        WHERE attempt_id = ? AND part_id = ? AND type = 'GENERATION_FAILED'
                        """,
                        Timestamp.class,
                        attemptId,
                        partId);
        Instant lastFailedAt =
                lastFailedTs != null ? lastFailedTs.toInstant() : part.getChoiceConfirmedAt();
        Instant now = clock.instant();
        Duration delay = RETRY_DELAYS.get(tries - 1);
        if (now.isBefore(lastFailedAt.plus(delay))) {
            return false;
        }

        attemptChoiceService.startRetryInLock(attempt, part, null, now);
        return true;
    }

    private static AttemptPart findPart(List<AttemptPart> parts, UUID partId) {
        for (AttemptPart part : parts) {
            if (part.getId().equals(partId)) {
                return part;
            }
        }
        return null;
    }

    /** Scheduled runner active in {@code worker} profile unless disabled (disabled in {@code test}). */
    @Component
    @Profile("worker")
    @ConditionalOnProperty(
            name = "thi360.attempt.scheduling.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public static class Scheduler {

        private final PartGenerationRetryWorker worker;

        public Scheduler(PartGenerationRetryWorker worker) {
            this.worker = Objects.requireNonNull(worker);
        }

        @Scheduled(fixedDelayString = "${thi360.attempt.scheduling.part-retry-interval-ms:30000}")
        public void run() {
            worker.sweep();
        }
    }
}
