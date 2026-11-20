package vn.unicom.thi360.attempt.application;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Bounded background executor for paper generation on both {@code api} and {@code worker} profiles
 * (LT L8, §5.2, plan B2). Drops queued jobs older than {@code maxQueueWait} and immediately invokes
 * {@code onBusy} when the queue rejects new submissions.
 */
@Component
public class GenerationExecutor {

    private final ThreadPoolExecutor executor;
    private final Duration maxQueueWait;
    private final Clock clock;

    public GenerationExecutor(AttemptGenerationProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxQueueWait = properties.maxQueueWait();
        BlockingQueue<Runnable> queue = properties.queueCapacity() == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(properties.queueCapacity());
        this.executor = new ThreadPoolExecutor(
                properties.threads(),
                properties.threads(),
                60L,
                TimeUnit.SECONDS,
                queue,
                Thread.ofPlatform().name("attempt-gen-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Submits a background generation task, invoking {@code onBusy} instead if the queue rejects the
     * submission or if the job waited strictly longer than {@code maxQueueWait} before starting.
     */
    public void submit(Runnable task, Runnable onBusy) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(onBusy, "onBusy must not be null");
        Instant enqueuedAt = clock.instant();
        try {
            executor.execute(() -> {
                Duration waited = Duration.between(enqueuedAt, clock.instant());
                if (waited.compareTo(maxQueueWait) > 0) {
                    onBusy.run();
                } else {
                    task.run();
                }
            });
        } catch (RejectedExecutionException ex) {
            onBusy.run();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
