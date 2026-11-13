package vn.unicom.thi360.shared.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Service
public class OutboxRelay {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");
    public static final int DEFAULT_BATCH_SIZE = 200;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
    public static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private enum StepResult {
        PROCESSED,
        FAILED,
        EMPTY
    }

    private final OutboxEventRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final Map<String, OutboxHandler> handlers = new ConcurrentHashMap<>();

    public OutboxRelay(
            OutboxEventRepository repository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.repository = repository;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = template;
        this.clock = clock;
    }

    public void register(String topic, OutboxHandler handler) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        handlers.put(topic, handler);
    }

    public void unregister(String topic) {
        if (topic != null) {
            handlers.remove(topic);
        }
    }

    public int relayBatch() {
        return relayBatch(DEFAULT_BATCH_SIZE, DEFAULT_MAX_ATTEMPTS);
    }

    public int relayBatch(int batchSize) {
        return relayBatch(batchSize, DEFAULT_MAX_ATTEMPTS);
    }

    public int relayBatch(int batchSize, int maxAttempts) {
        int safeBatchSize = Math.max(1, batchSize);
        int safeMaxAttempts = Math.max(1, maxAttempts);
        String jobId = Ids.newId().toString();
        String workerTraceId = Ids.newId().toString();

        String prevTraceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String prevJob = MDC.get("job");
        String prevJobId = MDC.get("jobId");
        MDC.put(TraceIdFilter.MDC_TRACE_ID, workerTraceId);
        MDC.put("job", "outbox");
        MDC.put("jobId", jobId);

        try {
            int processedCount = 0;
            for (int i = 0; i < safeBatchSize; i++) {
                StepResult step = requiresNewTx.execute(
                        status -> processNextAvailable(safeMaxAttempts, workerTraceId));
                if (step == null || step == StepResult.EMPTY) {
                    break;
                }
                if (step == StepResult.PROCESSED) {
                    processedCount++;
                }
            }
            if (processedCount > 0) {
                jobLog.atInfo()
                        .addKeyValue("processed", processedCount)
                        .log("Outbox batch processed");
            }
            return processedCount;
        } finally {
            restoreMdc(TraceIdFilter.MDC_TRACE_ID, prevTraceId);
            restoreMdc("job", prevJob);
            restoreMdc("jobId", prevJobId);
        }
    }

    private StepResult processNextAvailable(int maxAttempts, String workerTraceId) {
        Instant now = Instant.now(clock);
        List<OutboxEvent> batch = repository.findNextPendingBatchForUpdate(1, maxAttempts, now);
        if (batch.isEmpty()) {
            return StepResult.EMPTY;
        }
        OutboxEvent event = batch.getFirst();
        String eventTraceId = (event.getTraceId() != null && !event.getTraceId().isBlank())
                ? event.getTraceId()
                : workerTraceId;
        MDC.put(TraceIdFilter.MDC_TRACE_ID, eventTraceId);
        try {
            OutboxHandler handler = handlers.get(event.getTopic());
            if (handler == null) {
                String msg = "No OutboxHandler registered for topic: " + event.getTopic();
                event.markFailedPermanently(msg, now);
                repository.save(event);
                jobLog.atError()
                        .addKeyValue("eventId", event.getId())
                        .addKeyValue("topic", event.getTopic())
                        .addKeyValue("attempts", event.getAttempts())
                        .log(msg);
                return StepResult.FAILED;
            }

            try {
                handler.handle(event);
                event.markProcessed(now);
                repository.save(event);
                jobLog.atInfo()
                        .addKeyValue("eventId", event.getId())
                        .addKeyValue("topic", event.getTopic())
                        .log("Outbox event processed");
                return StepResult.PROCESSED;
            } catch (Exception ex) {
                String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                Duration backoff = computeBackoff(event.getAttempts() + 1);
                event.recordFailure(errorMsg, now, maxAttempts, backoff);
                repository.save(event);
                if (event.getFailedAt() != null) {
                    jobLog.atError()
                            .setCause(ex)
                            .addKeyValue("eventId", event.getId())
                            .addKeyValue("topic", event.getTopic())
                            .addKeyValue("attempts", event.getAttempts())
                            .log("Outbox event failed permanently");
                } else {
                    jobLog.atWarn()
                            .setCause(ex)
                            .addKeyValue("eventId", event.getId())
                            .addKeyValue("topic", event.getTopic())
                            .addKeyValue("attempts", event.getAttempts())
                            .addKeyValue("nextAttemptAt", event.getNextAttemptAt())
                            .log("Outbox event handler failed; retry scheduled");
                }
                return StepResult.FAILED;
            }
        } finally {
            MDC.put(TraceIdFilter.MDC_TRACE_ID, workerTraceId);
        }
    }

    static Duration computeBackoff(int attemptsAfterFailure) {
        int shift = Math.min(Math.max(0, attemptsAfterFailure - 1), 10);
        Duration backoff = INITIAL_BACKOFF.multipliedBy(1L << shift);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private static void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }
    @Component
    @Profile("worker")
    public static class OutboxRelayWorker {

        private final OutboxRelay outboxRelay;

        public OutboxRelayWorker(OutboxRelay outboxRelay) {
            this.outboxRelay = outboxRelay;
        }

        @Scheduled(fixedDelayString = "${thi360.outbox.poll-delay:PT2S}")
        public void runScheduledRelay() {
            outboxRelay.relayBatch();
        }
    }
}
