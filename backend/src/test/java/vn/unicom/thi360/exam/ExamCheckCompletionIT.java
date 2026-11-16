package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.exam.application.ExamCheckCompletion;
import vn.unicom.thi360.exam.application.ExamCheckJobStore;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;

@Transactional
class ExamCheckCompletionIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ExamCheckJobStore jobs;
    @Autowired private ExamCheckCompletion completion;

    @Test
    void staleDeepResultIsReturnedInJobButNotSavedAsCurrentCheck() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) "
                        + "VALUES (?, ?, ?, 'Deep completion test', 'FULL', 'SHARED')",
                examId, examId.toString(), examId.toString());
        var start = jobs.startOrGet(examId, actorId);
        var empty = mapper.createObjectNode();
        var input = new ExamCheckResultStore.Input(examId, 0, "hash", empty, 1L, 1L,
                "fingerprint", empty);
        jdbc.update("UPDATE exam.exam SET revision = revision + 1 WHERE id = ?", examId);
        var result = mapper.createObjectNode().put("status", "VALID").put("issueCount", 0);

        var completed = completion.complete(input, start.runId(), result, mapper.createObjectNode(),
                Instant.now(), 42, new ExamCheckCompletion.Actor(actorId, "tester", AuditActorType.USER), "trace-test");

        assertThat(completed.accepted()).isTrue();
        assertThat(completed.result().path("stale").asBoolean()).isTrue();
        assertThat(result.has("stale")).isFalse();
        var job = jobs.get(examId, start.runId());
        assertThat(job.state()).isEqualTo("DONE");
        assertThat(mapper.readTree(job.resultJson()).path("stale").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id = ?",
                Integer.class, examId)).isZero();
    }

    @Test
    void abandonedJobCannotOverwriteCurrentCheck() {
        UUID examId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) "
                        + "VALUES (?, ?, ?, 'Abandoned completion test', 'FULL', 'SHARED')",
                examId, examId.toString(), examId.toString());
        var start = jobs.startOrGet(examId, actorId);
        assertThat(jobs.fail(start.runId(), "CHECK_RUN_ABANDONED")).isTrue();
        var empty = mapper.createObjectNode();
        var input = new ExamCheckResultStore.Input(examId, 0, "hash", empty, 1L, 1L,
                "fingerprint", empty);
        var result = mapper.createObjectNode().put("status", "VALID").put("issueCount", 0);

        var completed = completion.complete(input, start.runId(), result, empty,
                Instant.now(), 42, new ExamCheckCompletion.Actor(actorId, "tester", AuditActorType.USER), "trace-test");

        assertThat(completed.accepted()).isFalse();
        assertThat(jobs.get(examId, start.runId()).errorCode()).isEqualTo("CHECK_RUN_ABANDONED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id = ?",
                Integer.class, examId)).isZero();
    }
}
