package vn.unicom.thi360.exam.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.exam.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class ExamCheckRetentionJob {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    ExamCheckRetentionJob(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${thi360.exam.retention-interval:PT1H}",
            initialDelayString = "${thi360.exam.retention-interval:PT1H}")
    void deleteExpiredJobs() {
        jdbc.update("DELETE FROM exam.exam_check_job WHERE state IN ('DONE', 'FAILED') AND finished_at < ?",
                Timestamp.from(clock.instant().minus(Duration.ofHours(24))));
    }
}
