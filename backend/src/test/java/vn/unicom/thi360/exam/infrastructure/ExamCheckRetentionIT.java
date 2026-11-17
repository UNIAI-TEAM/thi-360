package vn.unicom.thi360.exam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import vn.unicom.thi360.AbstractIntegrationTest;

@Transactional
class ExamCheckRetentionIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deletesOnlyFinishedJobsOlderThanTwentyFourHours() {
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        UUID exam = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) "
                + "VALUES (?, ?, ?, 'Retention test', 'FULL', 'SHARED')",
                exam, exam.toString(), exam.toString());
        UUID expiredDone = insert(exam, actor, "DONE", now.minusSeconds(86401));
        UUID expiredFailed = insert(exam, actor, "FAILED", now.minusSeconds(86401));
        UUID boundary = insert(exam, actor, "DONE", now.minusSeconds(86400));
        UUID running = insert(exam, actor, "RUNNING", null);

        new ExamCheckRetentionJob(jdbc, Clock.fixed(now, ZoneOffset.UTC)).deleteExpiredJobs();

        assertThat(jdbc.queryForList("SELECT id FROM exam.exam_check_job WHERE exam_id=?", UUID.class, exam))
                .containsExactlyInAnyOrder(boundary, running)
                .doesNotContain(expiredDone, expiredFailed);
    }

    private UUID insert(UUID exam, UUID actor, String state, Instant finishedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam_check_job (id, exam_id, state, started_by, started_at, finished_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", id, exam, state, actor,
                Timestamp.from(Instant.parse("2026-11-14T00:00:00Z")),
                finishedAt == null ? null : Timestamp.from(finishedAt));
        return id;
    }
}
