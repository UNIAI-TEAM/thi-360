package vn.unicom.thi360.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    public record SampleResult(String batchId, int processedRows) {
    }

    @Test
    void executeOnce_runsSupplierOnlyOnce_andReturnsSameResultOnSubsequentAndConcurrentCalls() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        String scope = "bulk-import";
        AtomicInteger executions = new AtomicInteger(0);

        SampleResult first = idempotencyService.executeOnce(key, scope, SampleResult.class, () -> {
            int count = executions.incrementAndGet();
            return new SampleResult("batch-1", count * 10);
        });

        SampleResult second = idempotencyService.executeOnce(key, scope, SampleResult.class, () -> {
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

    @Test
    void executeOnce_replaysMapOf_listOf_andNullResults() {
        String scope = "collections-scope";

        // Map.of (JDK immutable implementation class)
        String mapKey = "idem-map-" + UUID.randomUUID();
        AtomicInteger mapCalls = new AtomicInteger(0);
        Map<String, Integer> firstMap = idempotencyService.executeOnce(
                mapKey, scope, new TypeReference<>() {}, () -> {
                    mapCalls.incrementAndGet();
                    return Map.of("created", 4, "skipped", 1);
                });
        Map<String, Integer> replayedMap = idempotencyService.executeOnce(
                mapKey, scope, new TypeReference<>() {}, () -> {
                    mapCalls.incrementAndGet();
                    return Map.of("created", 99);
                });
        assertThat(mapCalls.get()).isEqualTo(1);
        assertThat(replayedMap).isEqualTo(firstMap).containsEntry("created", 4).containsEntry("skipped", 1);

        // List.of (JDK immutable implementation class)
        String listKey = "idem-list-" + UUID.randomUUID();
        AtomicInteger listCalls = new AtomicInteger(0);
        List<String> firstList = idempotencyService.executeOnce(
                listKey, scope, new TypeReference<>() {}, () -> {
                    listCalls.incrementAndGet();
                    return List.of("USER.VIEW", "USER.MODIFY");
                });
        List<String> replayedList = idempotencyService.executeOnce(
                listKey, scope, new TypeReference<>() {}, () -> {
                    listCalls.incrementAndGet();
                    return List.of("OTHER");
                });
        assertThat(listCalls.get()).isEqualTo(1);
        assertThat(replayedList).isEqualTo(firstList).containsExactly("USER.VIEW", "USER.MODIFY");

        // null result
        String nullKey = "idem-null-" + UUID.randomUUID();
        AtomicInteger nullCalls = new AtomicInteger(0);
        SampleResult firstNull = idempotencyService.executeOnce(
                nullKey, scope, SampleResult.class, () -> {
                    nullCalls.incrementAndGet();
                    return null;
                });
        SampleResult replayedNull = idempotencyService.executeOnce(
                nullKey, scope, SampleResult.class, () -> {
                    nullCalls.incrementAndGet();
                    return new SampleResult("should-not-run", 1);
                });
        assertThat(nullCalls.get()).isEqualTo(1);
        assertThat(firstNull).isNull();
        assertThat(replayedNull).isNull();
    }

    @Test
    void executeOnce_storesPlainJson_andReplaysWhenOriginalImplementationClassIsUnavailable() {
        String scope = "schema-evolution";
        String key = "idem-plain-" + UUID.randomUUID();

        record EphemeralWriterOnlyPayload(String batchId, int processedRows) {
        }

        idempotencyService.executeOnce(
                key,
                scope,
                EphemeralWriterOnlyPayload.class,
                () -> new EphemeralWriterOnlyPayload("batch-42", 7));

        IdempotencyKey saved = idempotencyKeyRepository
                .findById(new IdempotencyKeyId(scope, key))
                .orElseThrow();
        assertThat(saved.getResponse())
                .doesNotContain("EphemeralWriterOnlyPayload")
                .contains("\"batchId\"")
                .contains("\"batch-42\"");

        AtomicInteger replayCalls = new AtomicInteger(0);
        SampleResult replayedAsCurrentType = idempotencyService.executeOnce(
                key,
                scope,
                SampleResult.class,
                () -> {
                    replayCalls.incrementAndGet();
                    return new SampleResult("unexpected", 0);
                });

        assertThat(replayCalls.get()).isZero();
        assertThat(replayedAsCurrentType).isEqualTo(new SampleResult("batch-42", 7));
    }

    @Test
    void executeOnce_replaysDtoRecordThroughEachRemainingTypedSignature() {
        String scope = "typed-dto-replay";

        // 1. Class<T> signature with DTO record
        String classKey = "idem-dto-class-" + UUID.randomUUID();
        AtomicInteger classCalls = new AtomicInteger(0);
        SampleResult firstClassDto = idempotencyService.executeOnce(
                classKey,
                scope,
                SampleResult.class,
                () -> {
                    classCalls.incrementAndGet();
                    return new SampleResult("dto-class-1", 42);
                });
        SampleResult replayedClassDto = idempotencyService.executeOnce(
                classKey,
                scope,
                SampleResult.class,
                () -> {
                    classCalls.incrementAndGet();
                    return new SampleResult("should-not-run", 0);
                });
        assertThat(classCalls.get()).isEqualTo(1);
        assertThat(replayedClassDto)
                .isInstanceOf(SampleResult.class)
                .isEqualTo(firstClassDto);

        // 2. TypeReference<T> signature with DTO record
        String typeRefKey = "idem-dto-typeref-" + UUID.randomUUID();
        AtomicInteger typeRefCalls = new AtomicInteger(0);
        SampleResult firstTypeRefDto = idempotencyService.executeOnce(
                typeRefKey,
                scope,
                new TypeReference<SampleResult>() {},
                () -> {
                    typeRefCalls.incrementAndGet();
                    return new SampleResult("dto-typeref-1", 17);
                });
        SampleResult replayedTypeRefDto = idempotencyService.executeOnce(
                typeRefKey,
                scope,
                new TypeReference<SampleResult>() {},
                () -> {
                    typeRefCalls.incrementAndGet();
                    return new SampleResult("should-not-run", 0);
                });
        assertThat(typeRefCalls.get()).isEqualTo(1);
        assertThat(replayedTypeRefDto)
                .isInstanceOf(SampleResult.class)
                .isEqualTo(firstTypeRefDto);

        // 3. TypeReference<T> signature with generic DTO record containing nested DTO records
        record BatchEnvelope<T>(String requestId, List<T> items) {
        }
        String genericDtoKey = "idem-dto-generic-" + UUID.randomUUID();
        AtomicInteger genericDtoCalls = new AtomicInteger(0);
        BatchEnvelope<SampleResult> firstGenericDto = idempotencyService.executeOnce(
                genericDtoKey,
                scope,
                new TypeReference<BatchEnvelope<SampleResult>>() {},
                () -> {
                    genericDtoCalls.incrementAndGet();
                    return new BatchEnvelope<>("req-9", List.of(new SampleResult("nested-1", 3)));
                });
        BatchEnvelope<SampleResult> replayedGenericDto = idempotencyService.executeOnce(
                genericDtoKey,
                scope,
                new TypeReference<BatchEnvelope<SampleResult>>() {},
                () -> {
                    genericDtoCalls.incrementAndGet();
                    return new BatchEnvelope<>("should-not-run", List.of());
                });
        assertThat(genericDtoCalls.get()).isEqualTo(1);
        assertThat(replayedGenericDto)
                .isInstanceOf(BatchEnvelope.class)
                .isEqualTo(firstGenericDto);
        assertThat(replayedGenericDto.items())
                .hasSize(1)
                .allSatisfy(item -> assertThat(item).isInstanceOf(SampleResult.class));
    }
}
