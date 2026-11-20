package vn.unicom.thi360.attempt.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded background generation pool and stale-STARTING thresholds (LT §5.2, plan B2). */
@ConfigurationProperties(prefix = "thi360.attempt.generation")
public record AttemptGenerationProperties(
        int threads,
        int queueCapacity,
        Duration maxQueueWait,
        Duration staleStartingAfter) {

    public AttemptGenerationProperties {
        threads = threads <= 0 ? 4 : threads;
        queueCapacity = queueCapacity < 0 ? 32 : queueCapacity;
        maxQueueWait = (maxQueueWait == null || maxQueueWait.isNegative())
                ? Duration.ofSeconds(60)
                : maxQueueWait;
        staleStartingAfter = (staleStartingAfter == null
                        || staleStartingAfter.isZero()
                        || staleStartingAfter.isNegative())
                ? Duration.ofMinutes(2)
                : staleStartingAfter;
    }
}
