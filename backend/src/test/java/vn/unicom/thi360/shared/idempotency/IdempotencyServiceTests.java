package vn.unicom.thi360.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.AbstractIntegrationTest;

class IdempotencyServiceTests extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    public record SampleResult(String batchId, int processedRows) {
    }

    @Test
    void executeOnce_runsSupplierOnlyOnce_andReturnsSameResultOnSubsequentAndConcurrentCalls() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        String scope = "bulk-import";
        AtomicInteger executions = new AtomicInteger(0);

        SampleResult first = idempotencyService.executeOnce(key, scope, () -> {
            int count = executions.incrementAndGet();
            return new SampleResult("batch-1", count * 10);
        });

        SampleResult second = idempotencyService.executeOnce(key, scope, () -> {
            int count = executions.incrementAndGet();
            return new SampleResult("batch-2", count * 10);
        });

        assertThat(executions.get()).isEqualTo(1);
        assertThat(first).isEqualTo(new SampleResult("batch-1", 10));
        assertThat(second).isEqualTo(first);

        // Concurrent calls with a fresh key also execute exactly once
        String concurrentKey = "idem-concurrent-" + UUID.randomUUID();
        AtomicInteger concurrentExecutions = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<SampleResult> task = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return idempotencyService.executeOnce(concurrentKey, scope, SampleResult.class, () -> {
                    int c = concurrentExecutions.incrementAndGet();
                    return new SampleResult("batch-concurrent", c);
                });
            };
            Future<SampleResult> f1 = executor.submit(task);
            Future<SampleResult> f2 = executor.submit(task);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            SampleResult r1 = f1.get(10, TimeUnit.SECONDS);
            SampleResult r2 = f2.get(10, TimeUnit.SECONDS);

            assertThat(concurrentExecutions.get()).isEqualTo(1);
            assertThat(r1).isEqualTo(new SampleResult("batch-concurrent", 1));
            assertThat(r2).isEqualTo(r1);
        } finally {
            executor.shutdownNow();
        }
    }
}
