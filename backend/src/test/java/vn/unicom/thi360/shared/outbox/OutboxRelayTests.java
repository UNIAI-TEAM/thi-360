package vn.unicom.thi360.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayTests extends AbstractIntegrationTest {

    private static final Logger handlerLog = LoggerFactory.getLogger(OutboxRelayTests.class);
    private static final String TEST_TOPIC = "test.parallel.topic";
    private static final String TRACE_TOPIC = "test.trace.topic";
    private static final String STRING_TOPIC = "test.string.topic";
    private static final String FAILING_TOPIC = "test.failing.topic";
    private static final String DEDUP_TOPIC = "test.dedup.topic";

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        MDC.clear();
        outboxEventRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        outboxRelay.unregister(TEST_TOPIC);
        outboxRelay.unregister(TRACE_TOPIC);
        outboxRelay.unregister(STRING_TOPIC);
        outboxRelay.unregister(FAILING_TOPIC);
        outboxRelay.unregister(DEDUP_TOPIC);
    }

    @Test
    void claimsAndProcessesEachPendingRowOnceAcrossConcurrentWorkers() throws Exception {
        int eventCount = 10;
        Set<UUID> publishedIds = ConcurrentHashMap.newKeySet();
        Map<UUID, AtomicInteger> handleCounts = new ConcurrentHashMap<>();

        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < eventCount; i++) {
                UUID id = Ids.newId();
                publishedIds.add(id);
                handleCounts.put(id, new AtomicInteger(0));
                outboxPublisher.publish(id, TEST_TOPIC, Map.of("seq", i));
            }
        });

        outboxRelay.register(TEST_TOPIC, event -> {
            AtomicInteger counter = handleCounts.get(event.getId());
            if (counter != null) {
                counter.incrementAndGet();
            }
            Thread.sleep(15);
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> workerTask = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return outboxRelay.relayBatch(eventCount);
            };

            Future<Integer> worker1 = executor.submit(workerTask);
            Future<Integer> worker2 = executor.submit(workerTask);

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            int count1 = worker1.get(30, TimeUnit.SECONDS);
            int count2 = worker2.get(30, TimeUnit.SECONDS);

            assertThat(count1 + count2).isEqualTo(eventCount);
        } finally {
            executor.shutdownNow();
        }

        for (UUID id : publishedIds) {
            assertThat(handleCounts.get(id).get())
                    .as("Row %s should be claimed once by concurrent workers", id)
                    .isEqualTo(1);
            OutboxEvent saved = outboxEventRepository.findById(id).orElseThrow();
            assertThat(saved.getProcessedAt()).isNotNull();
            assertThat(saved.getFailedAt()).isNull();
            assertThat(saved.getAttempts()).isEqualTo(0);
        }
    }

    @Test
    void propagatesOriginTraceIdToHandlerAndJobLogs_generatesWorkerTraceWhenAbsent_andCleansMdc(
            CapturedOutput output) {
        UUID tracedEventId = Ids.newId();
        UUID untracedEventId = Ids.newId();

        MDC.put(TraceIdFilter.MDC_TRACE_ID, "origin-trace-777");
        try {
            transactionTemplate.executeWithoutResult(
                    status -> outboxPublisher.publish(tracedEventId, TRACE_TOPIC, Map.of("kind", "traced")));
        } finally {
            MDC.remove(TraceIdFilter.MDC_TRACE_ID);
        }

        transactionTemplate.executeWithoutResult(
                status -> outboxPublisher.publish(untracedEventId, TRACE_TOPIC, Map.of("kind", "untraced")));

        assertThat(outboxEventRepository.findById(tracedEventId).orElseThrow().getTraceId())
                .isEqualTo("origin-trace-777");
        assertThat(outboxEventRepository.findById(untracedEventId).orElseThrow().getTraceId())
                .isNull();

        Map<UUID, String> observedMdcTraceByEvent = new ConcurrentHashMap<>();
        outboxRelay.register(TRACE_TOPIC, event -> {
            String mdcTrace = MDC.get(TraceIdFilter.MDC_TRACE_ID);
            observedMdcTraceByEvent.put(event.getId(), mdcTrace);
            handlerLog.info("Handled outbox event {}", event.getId());
        });

        int processed = outboxRelay.relayBatch(10);
        assertThat(processed).isEqualTo(2);

        assertThat(observedMdcTraceByEvent.get(tracedEventId)).isEqualTo("origin-trace-777");
        String workerTrace = observedMdcTraceByEvent.get(untracedEventId);
        assertThat(workerTrace).isNotBlank().isNotEqualTo("origin-trace-777");

        // MDC must be clean after relayBatch completes
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get("job")).isNull();
        assertThat(MDC.get("jobId")).isNull();

        // Verify both handler logs and thi360.job logs carry the respective trace IDs
        assertThat(output.getOut())
                .contains("trace=origin-trace-777")
                .contains("Handled outbox event " + tracedEventId)
                .contains("trace=" + workerTrace)
                .contains("Handled outbox event " + untracedEventId);
        assertThat(output.getOut().lines()
                        .anyMatch(l -> l.contains("thi360.job")
                                && l.contains("trace=origin-trace-777")
                                && l.contains(tracedEventId.toString())))
                .isTrue();
        assertThat(output.getOut().lines()
                        .anyMatch(l -> l.contains("thi360.job")
                                && l.contains("trace=" + workerTrace)
                                && l.contains(untracedEventId.toString())))
                .isTrue();
    }

    @Test
    void publishesPlainStringPayloadAsValidJsonb_andReadsBackSameString() throws Exception {
        UUID id = Ids.newId();
        String rawMessage = "plain text outbox payload";

        transactionTemplate.executeWithoutResult(
                status -> outboxPublisher.publish(id, STRING_TOPIC, rawMessage));

        OutboxEvent stored = outboxEventRepository.findById(id).orElseThrow();
        String deserialized = objectMapper.readValue(stored.getPayload(), String.class);
        assertThat(deserialized).isEqualTo(rawMessage);
    }

    @Test
    void failingHandlerRetriesWithExponentialBackoff_oncePerPoll_untilMaxAttemptsMarksTerminalFailure(
            CapturedOutput output) {
        UUID id = Ids.newId();
        AtomicInteger invocations = new AtomicInteger(0);

        transactionTemplate.executeWithoutResult(
                status -> outboxPublisher.publish(id, FAILING_TOPIC, Map.of("step", 1)));

        outboxRelay.register(FAILING_TOPIC, event -> {
            invocations.incrementAndGet();
            throw new IllegalStateException("downstream unavailable");
        });

        // Poll 1: even with batchSize=10 and maxAttempts=3, failing event is attempted only once
        int processedPoll1 = outboxRelay.relayBatch(10, 3);
        assertThat(processedPoll1).isZero();
        assertThat(invocations.get()).isEqualTo(1);

        OutboxEvent afterAttempt1 = outboxEventRepository.findById(id).orElseThrow();
        assertThat(afterAttempt1.getAttempts()).isEqualTo(1);
        assertThat(afterAttempt1.getLastError()).isEqualTo("downstream unavailable");
        assertThat(afterAttempt1.getProcessedAt()).isNull();
        assertThat(afterAttempt1.getFailedAt()).isNull();
        assertThat(afterAttempt1.getNextAttemptAt())
                .isAfterOrEqualTo(afterAttempt1.getCreatedAt().plus(OutboxRelay.INITIAL_BACKOFF));

        // Immediate poll before next_attempt_at does not re-attempt
        outboxRelay.relayBatch(10, 3);
        assertThat(invocations.get()).isEqualTo(1);

        // Simulate backoff elapsed for attempt 2
        rewindNextAttemptAt(id, Instant.now().minusSeconds(1));
        outboxRelay.relayBatch(10, 3);
        assertThat(invocations.get()).isEqualTo(2);

        OutboxEvent afterAttempt2 = outboxEventRepository.findById(id).orElseThrow();
        assertThat(afterAttempt2.getAttempts()).isEqualTo(2);
        assertThat(afterAttempt2.getFailedAt()).isNull();
        assertThat(afterAttempt2.getProcessedAt()).isNull();
        assertThat(afterAttempt2.getNextAttemptAt()).isAfter(afterAttempt1.getNextAttemptAt());

        // Simulate backoff elapsed for attempt 3 (reaches maxAttempts = 3 -> terminal failure)
        rewindNextAttemptAt(id, Instant.now().minusSeconds(1));
        outboxRelay.relayBatch(10, 3);
        assertThat(invocations.get()).isEqualTo(3);

        OutboxEvent afterAttempt3 = outboxEventRepository.findById(id).orElseThrow();
        assertThat(afterAttempt3.getAttempts()).isEqualTo(3);
        assertThat(afterAttempt3.getProcessedAt()).isNull();
        assertThat(afterAttempt3.getFailedAt()).isNotNull();
        assertThat(afterAttempt3.getLastError()).isEqualTo("downstream unavailable");

        assertThat(output.getOut().lines()
                        .anyMatch(l -> l.contains("ERROR")
                                && l.contains("thi360.job")
                                && l.contains(FAILING_TOPIC)
                                && l.contains(id.toString())))
                .isTrue();

        // Terminal event is never picked up again even if next_attempt_at is in the past
        rewindNextAttemptAt(id, Instant.now().minusSeconds(10));
        outboxRelay.relayBatch(10, 3);
        assertThat(invocations.get()).isEqualTo(3);
    }

    @Test
    void unknownTopicMarksEventFailedImmediately_andLogsOperatorError(CapturedOutput output) {
        UUID id = Ids.newId();
        String unknownTopic = "unregistered.topic.name";

        transactionTemplate.executeWithoutResult(
                status -> outboxPublisher.publish(id, unknownTopic, Map.of("k", "v")));

        int processed = outboxRelay.relayBatch(10, 3);
        assertThat(processed).isZero();

        OutboxEvent saved = outboxEventRepository.findById(id).orElseThrow();
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getFailedAt()).isNotNull();
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).contains("No OutboxHandler registered for topic: " + unknownTopic);

        assertThat(output.getOut().lines()
                        .anyMatch(l -> l.contains("ERROR")
                                && l.contains("thi360.job")
                                && l.contains(unknownTopic)
                                && l.contains(id.toString())))
                .isTrue();
    }

    @Test
    void atLeastOnceDelivery_handlerDeduplicatingByEventIdAppliesExternalEffectOnceWhenFirstAttemptFailsAfterSideEffect() {
        UUID id = Ids.newId();
        transactionTemplate.executeWithoutResult(
                status -> outboxPublisher.publish(id, DEDUP_TOPIC, Map.of("action", "send-welcome")));

        AtomicInteger handlerInvocations = new AtomicInteger(0);
        Set<UUID> deduplicatedEventIds = ConcurrentHashMap.newKeySet();
        List<UUID> appliedExternalSideEffects = new ArrayList<>();

        outboxRelay.register(DEDUP_TOPIC, event -> {
            int call = handlerInvocations.incrementAndGet();
            if (deduplicatedEventIds.add(event.getId())) {
                appliedExternalSideEffects.add(event.getId());
            }
            if (call == 1) {
                throw new RuntimeException("Simulated commit/network failure after external side effect");
            }
        });

        // First poll fails after side effect
        assertThat(outboxRelay.relayBatch(10, 3)).isZero();
        assertThat(handlerInvocations.get()).isEqualTo(1);
        assertThat(appliedExternalSideEffects).containsExactly(id);

        // Retry after backoff succeeds; handler runs a second time but deduplicates by event ID
        rewindNextAttemptAt(id, Instant.now().minusSeconds(1));
        assertThat(outboxRelay.relayBatch(10, 3)).isEqualTo(1);
        assertThat(handlerInvocations.get()).isEqualTo(2);
        assertThat(appliedExternalSideEffects).containsExactly(id);

        OutboxEvent saved = outboxEventRepository.findById(id).orElseThrow();
        assertThat(saved.getProcessedAt()).isNotNull();
        assertThat(saved.getFailedAt()).isNull();
        assertThat(saved.getAttempts()).isEqualTo(1);
    }

    @Test
    void slowFailingHandlerSchedulesBackoffFromFailureCompletion_staysIneligibleInSamePollAndUntilDeadline_andUsesCompletionTimestamps() {
        Instant t0 = Instant.parse("2026-11-13T10:00:00Z");
        MutableClock mutableClock = new MutableClock(t0);
        OutboxPublisher timedPublisher = new OutboxPublisher(outboxEventRepository, objectMapper, mutableClock);
        OutboxRelay timedRelay = new OutboxRelay(outboxEventRepository, transactionManager, mutableClock);

        UUID slowFailId = Ids.newId();
        String slowFailTopic = "test.slow.fail.topic";
        AtomicInteger failInvocations = new AtomicInteger(0);
        Duration handlerDuration = Duration.ofSeconds(10); // > INITIAL_BACKOFF (2s)

        transactionTemplate.executeWithoutResult(
                status -> timedPublisher.publish(slowFailId, slowFailTopic, Map.of("case", "slow-fail")));

        timedRelay.register(slowFailTopic, event -> {
            failInvocations.incrementAndGet();
            mutableClock.advance(handlerDuration);
            throw new IllegalStateException("slow downstream timeout");
        });

        // Single poll with batchSize=10 must invoke the slow failing handler only once
        int processedPoll1 = timedRelay.relayBatch(10, 2);
        assertThat(processedPoll1).isZero();
        assertThat(failInvocations.get()).isEqualTo(1);

        Instant failure1At = t0.plus(handlerDuration);
        Instant expectedNextAttempt = failure1At.plus(OutboxRelay.INITIAL_BACKOFF);
        OutboxEvent afterAttempt1 = outboxEventRepository.findById(slowFailId).orElseThrow();
        assertThat(afterAttempt1.getAttempts()).isEqualTo(1);
        assertThat(afterAttempt1.getFailedAt()).isNull();
        assertThat(afterAttempt1.getNextAttemptAt()).isEqualTo(expectedNextAttempt);

        // Still ineligible after t0 + INITIAL_BACKOFF (10:00:02) and just before failure1At + INITIAL_BACKOFF (10:00:12)
        mutableClock.setInstant(t0.plus(OutboxRelay.INITIAL_BACKOFF).plusSeconds(1));
        timedRelay.relayBatch(10, 2);
        assertThat(failInvocations.get()).isEqualTo(1);

        mutableClock.setInstant(expectedNextAttempt.minusMillis(1));
        timedRelay.relayBatch(10, 2);
        assertThat(failInvocations.get()).isEqualTo(1);

        // Eligible at failure completion + backoff -> fails again and records terminal failedAt at completion time
        mutableClock.setInstant(expectedNextAttempt);
        timedRelay.relayBatch(10, 2);
        assertThat(failInvocations.get()).isEqualTo(2);

        Instant failure2At = expectedNextAttempt.plus(handlerDuration);
        OutboxEvent afterAttempt2 = outboxEventRepository.findById(slowFailId).orElseThrow();
        assertThat(afterAttempt2.getAttempts()).isEqualTo(2);
        assertThat(afterAttempt2.getFailedAt()).isEqualTo(failure2At);
        assertThat(afterAttempt2.getProcessedAt()).isNull();

        // Successful slow handler also records processedAt at completion time
        Instant t1 = failure2At.plusSeconds(10);
        mutableClock.setInstant(t1);
        UUID slowSuccessId = Ids.newId();
        String slowSuccessTopic = "test.slow.success.topic";
        Duration successDuration = Duration.ofSeconds(5);

        transactionTemplate.executeWithoutResult(
                status -> timedPublisher.publish(slowSuccessId, slowSuccessTopic, Map.of("case", "slow-ok")));

        timedRelay.register(slowSuccessTopic, event -> mutableClock.advance(successDuration));
        assertThat(timedRelay.relayBatch(10, 2)).isEqualTo(1);

        OutboxEvent processedEvent = outboxEventRepository.findById(slowSuccessId).orElseThrow();
        assertThat(processedEvent.getProcessedAt()).isEqualTo(t1.plus(successDuration));
        assertThat(processedEvent.getFailedAt()).isNull();
    }

    private static final class MutableClock extends Clock {

        private Instant current;
        private final ZoneId zone;

        private MutableClock(Instant initial) {
            this(initial, ZoneOffset.UTC);
        }

        private MutableClock(Instant initial, ZoneId zone) {
            this.current = initial;
            this.zone = zone;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        void setInstant(Instant instant) {
            this.current = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private void rewindNextAttemptAt(UUID id, Instant nextAttemptAt) {
        jdbcTemplate.update(
                "UPDATE shared.outbox_event SET next_attempt_at = ? WHERE id = ?",
                Timestamp.from(nextAttemptAt),
                id);
    }
}
