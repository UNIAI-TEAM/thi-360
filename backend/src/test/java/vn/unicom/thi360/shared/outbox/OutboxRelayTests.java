package vn.unicom.thi360.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.ids.Ids;

class OutboxRelayTests extends AbstractIntegrationTest {

    private static final String TEST_TOPIC = "test.parallel.topic";

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        outboxRelay.unregister(TEST_TOPIC);
    }

    @Test
    void processesEachRecordExactlyOnceWhenTwoWorkersRunInParallel() throws Exception {
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
                    .as("Event %s should be handled once", id)
                    .isEqualTo(1);
            OutboxEvent saved = outboxEventRepository.findById(id).orElseThrow();
            assertThat(saved.getProcessedAt()).isNotNull();
            assertThat(saved.getAttempts()).isEqualTo(0);
        }
    }
}
