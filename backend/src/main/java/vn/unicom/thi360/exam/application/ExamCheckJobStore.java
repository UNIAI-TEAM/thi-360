package vn.unicom.thi360.exam.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/** Short transactions for durable deep-check job lifecycle; never runs the solver. */
@Service
public class ExamCheckJobStore {
    private static final Duration ABANDONED_AFTER = Duration.ofSeconds(150);
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ExamCheckJobStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Start(UUID runId, boolean created) { }
    public record Job(UUID runId, String state, String resultJson, String errorCode) { }

    /** Locking the exam serializes claim, abandonment, and creation across API instances. */
    @Transactional
    public Start startOrGet(UUID examId, UUID actorId) {
        List<UUID> exam = jdbc.queryForList("SELECT id FROM exam.exam WHERE id = ? FOR UPDATE", UUID.class, examId);
        if (exam.isEmpty()) {
            throw new NotFoundException("Exam not found");
        }
        Instant now = clock.instant();
        abandon(examId, null, now);
        List<UUID> running = jdbc.queryForList(
                "SELECT id FROM exam.exam_check_job WHERE exam_id = ? AND state = 'RUNNING'",
                UUID.class, examId);
        if (!running.isEmpty()) {
            return new Start(running.getFirst(), false);
        }
        UUID id = Ids.newId();
        jdbc.update("INSERT INTO exam.exam_check_job (id, exam_id, state, started_by, started_at) "
                + "VALUES (?, ?, 'RUNNING', ?, ?)", id, examId, actorId, Timestamp.from(now));
        return new Start(id, true);
    }

    @Transactional
    public Job get(UUID examId, UUID runId) {
        abandon(examId, runId, clock.instant());
        List<Job> rows = jdbc.query("SELECT id, state, result::text, error_code FROM exam.exam_check_job "
                        + "WHERE exam_id = ? AND id = ?",
                (rs, row) -> new Job(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                examId, runId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Check run not found");
        }
        return rows.getFirst();
    }

    @Transactional
    public boolean complete(UUID runId, String resultJson) {
        return jdbc.update("UPDATE exam.exam_check_job SET state = 'DONE', finished_at = ?, result = ?::jsonb "
                        + "WHERE id = ? AND state = 'RUNNING'",
                Timestamp.from(clock.instant()), resultJson, runId) == 1;
    }

    @Transactional
    public boolean fail(UUID runId, String errorCode) {
        return jdbc.update("UPDATE exam.exam_check_job SET state = 'FAILED', finished_at = ?, error_code = ? "
                        + "WHERE id = ? AND state = 'RUNNING'",
                Timestamp.from(clock.instant()), errorCode, runId) == 1;
    }

    private void abandon(UUID examId, UUID runId, Instant now) {
        String sql = "UPDATE exam.exam_check_job SET state = 'FAILED', finished_at = ?, "
                + "error_code = 'CHECK_RUN_ABANDONED' WHERE exam_id = ? AND state = 'RUNNING' AND started_at <= ?";
        if (runId == null) {
            jdbc.update(sql, Timestamp.from(now), examId, Timestamp.from(now.minus(ABANDONED_AFTER)));
        } else {
            jdbc.update(sql + " AND id = ?", Timestamp.from(now), examId,
                    Timestamp.from(now.minus(ABANDONED_AFTER)), runId);
        }
    }
}
