package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AttemptGenerationProperties;
import vn.unicom.thi360.attempt.application.GenerationExecutor;
import vn.unicom.thi360.shared.testapi.MutableClock;

/** Unit tests for {@link GenerationExecutor} queue bounds and 60s wait-time cap (AC-8, plan B2). */
class GenerationExecutorTest {

    @Test
    void zeroQueueCapacityRejectsSecondConcurrentJobImmediately() throws Exception {
        AttemptGenerationProperties props = new AttemptGenerationProperties(
                1, 0, Duration.ofSeconds(60), Duration.ofMinutes(2));
        GenerationExecutor executor = new GenerationExecutor(props, Clock.systemUTC());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger busyCalls = new AtomicInteger();
        AtomicInteger firstDone = new AtomicInteger();
        try {
            executor.submit(() -> {
                started.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                    firstDone.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, busyCalls::incrementAndGet);

            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> {}, busyCalls::incrementAndGet);

            assertThat(busyCalls.get()).isEqualTo(1);
            release.countDown();
        } finally {
            executor.shutdown();
        }
        assertThat(firstDone.get()).isEqualTo(1);
    }

    @Test
    void queuedJobAtExact60sRunsWhile60sPlus1sIsDropped() throws Exception {
        MutableClock clock = new MutableClock(
                Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC));
        AttemptGenerationProperties props = new AttemptGenerationProperties(
                1, 4, Duration.ofSeconds(60), Duration.ofMinutes(2));
        GenerationExecutor executor = new GenerationExecutor(props, clock);
        try {
            // Case 1: queued job waits exactly 60s -> still runs
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondDone = new CountDownLatch(1);
            AtomicInteger secondRan = new AtomicInteger();
            AtomicInteger secondBusy = new AtomicInteger();

            executor.submit(() -> {
                firstStarted.countDown();
                try {
                    assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, () -> {});
            assertThat(firstStarted.await(10, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> {
                secondRan.incrementAndGet();
                secondDone.countDown();
            }, () -> {
                secondBusy.incrementAndGet();
                secondDone.countDown();
            });

            clock.advance(Duration.ofSeconds(60));
            releaseFirst.countDown();
            assertThat(secondDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(secondRan.get()).isEqualTo(1);
            assertThat(secondBusy.get()).isZero();

            // Case 2: queued job waits 61s (> 60s) -> dropped and calls onBusy
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            CountDownLatch thirdDone = new CountDownLatch(1);
            AtomicInteger thirdRan = new AtomicInteger();
            AtomicInteger thirdBusy = new AtomicInteger();

            executor.submit(() -> {
                blockerStarted.countDown();
                try {
                    assertThat(releaseBlocker.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, () -> {});
            assertThat(blockerStarted.await(10, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> {
                thirdRan.incrementAndGet();
                thirdDone.countDown();
            }, () -> {
                thirdBusy.incrementAndGet();
                thirdDone.countDown();
            });

            clock.advance(Duration.ofSeconds(61));
            releaseBlocker.countDown();
            assertThat(thirdDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(thirdRan.get()).isZero();
            assertThat(thirdBusy.get()).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
