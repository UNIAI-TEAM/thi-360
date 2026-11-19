package vn.unicom.thi360.exam.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.application.ExamCoverCleanup;

/** Worker schedule of {@link ExamCoverCleanup}: once a day (plan B5). Off where {@code thi360.exam.scheduling.enabled} is false. */
@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.exam.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class ExamCoverCleanupJob {
    private final ExamCoverCleanup cleanup;

    ExamCoverCleanupJob(ExamCoverCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(fixedDelayString = "${thi360.exam.cover-cleanup.interval:PT24H}",
            initialDelayString = "${thi360.exam.cover-cleanup.interval:PT24H}")
    void sweep() {
        cleanup.run();
    }
}
