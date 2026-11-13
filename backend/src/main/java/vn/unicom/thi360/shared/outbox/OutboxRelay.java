package vn.unicom.thi360.shared.outbox;

import java.time.Clock;
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

@Service
public class OutboxRelay {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");
    public static final int DEFAULT_BATCH_SIZE = 200;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

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

        String prevJob = MDC.get("job");
        String prevJobId = MDC.get("jobId");
        MDC.put("job", "outbox");
        MDC.put("jobId", jobId);

        try {
            int processedCount = 0;
            for (int i = 0; i < safeBatchSize; i++) {
                Boolean handledOne = requiresNewTx.execute(status -> processNextAvailable(safeMaxAttempts));
                if (!Boolean.TRUE.equals(handledOne)) {
                    break;
                }
                processedCount++;
            }
            if (processedCount > 0) {
                jobLog.atInfo()
                        .addKeyValue("processed", processedCount)
                        .log("Outbox batch processed");
            }
            return processedCount;
        } finally {
            restoreMdc("job", prevJob);
            restoreMdc("jobId", prevJobId);
        }
    }

    private boolean processNextAvailable(int maxAttempts) {
        List<OutboxEvent> batch = repository.findNextPendingBatchForUpdate(1, maxAttempts);
        if (batch.isEmpty()) {
            return false;
        }
        OutboxEvent event = batch.getFirst();
        OutboxHandler handler = handlers.get(event.getTopic());
        if (handler == null) {
            String msg = "No OutboxHandler registered for topic: " + event.getTopic();
            event.recordFailure(msg);
            repository.save(event);
            jobLog.atWarn()
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("topic", event.getTopic())
                    .log(msg);
            return true;
        }

        try {
            handler.handle(event);
            event.markProcessed(Instant.now(clock));
            repository.save(event);
        } catch (Exception ex) {
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            event.recordFailure(errorMsg);
            repository.save(event);
            jobLog.atError()
                    .setCause(ex)
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("topic", event.getTopic())
                    .addKeyValue("attempts", event.getAttempts())
                    .log("Failed to handle outbox event");
        }
        return true;
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
