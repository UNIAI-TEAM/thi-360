package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
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
import vn.unicom.thi360.exam.application.ExamCheckScope;

@Transactional
class ExamCheckCompletionIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
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
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, '{\"parts\":[]}'::jsonb, 'hash')", examId);
        var start = jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null));
        var empty = mapper.createObjectNode();
        var input = new ExamCheckResultStore.Input(examId, 0, "hash", empty, 1L, 1L,
                "fingerprint", empty);
        jdbc.update("UPDATE exam.exam SET revision = revision + 1 WHERE id = ?", examId);
        var result = mapper.createObjectNode().put("status", "VALID").put("issueCount", 0);

        var completed = completion.complete(input, start.runId(), result, mapper.createObjectNode(),
                List.of(), ExamCheckScope.from(null, null, null), Instant.now(), 42,
                new ExamCheckCompletion.Actor(actorId, "tester", AuditActorType.USER), "trace-test");

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
        var start = jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null));
        assertThat(jobs.fail(start.runId(), "CHECK_RUN_ABANDONED")).isTrue();
        var empty = mapper.createObjectNode();
        var input = new ExamCheckResultStore.Input(examId, 0, "hash", empty, 1L, 1L,
                "fingerprint", empty);
        var result = mapper.createObjectNode().put("status", "VALID").put("issueCount", 0);

        var completed = completion.complete(input, start.runId(), result, empty,
                List.of(), ExamCheckScope.from(null, null, null), Instant.now(), 42,
                new ExamCheckCompletion.Actor(actorId, "tester", AuditActorType.USER), "trace-test");

        assertThat(completed.accepted()).isFalse();
        assertThat(jobs.get(examId, start.runId()).errorCode()).isEqualTo("CHECK_RUN_ABANDONED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id = ?",
                Integer.class, examId)).isZero();
    }

    @Test
    void workerFinishingAfterAbandonmentDeadlineCannotSave() {
        UUID examId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) "
                        + "VALUES (?, ?, ?, 'Late completion test', 'FULL', 'SHARED')",
                examId, examId.toString(), examId.toString());
        var start = jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null));
        jdbc.update("UPDATE exam.exam_check_job SET started_at=now()-interval '151 seconds' WHERE id=?", start.runId());
        var empty = mapper.createObjectNode();
        var input = new ExamCheckResultStore.Input(examId, 0, "hash", empty, 1L, 1L, "fingerprint", empty);
        var result = mapper.createObjectNode().put("status", "VALID").put("issueCount", 0);
        var completed = completion.complete(input, start.runId(), result, empty, List.of(),
                ExamCheckScope.from(null, null, null), Instant.now(), 151_000,
                new ExamCheckCompletion.Actor(actorId, "tester", AuditActorType.USER), "late-trace");
        assertThat(completed.accepted()).isFalse();
        assertThat(jobs.get(examId, start.runId()).errorCode()).isEqualTo("CHECK_RUN_ABANDONED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id=?", Integer.class, examId)).isZero();
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action'='EXAM.CHECK_FAILED' AND payload->>'targetId'=?", Integer.class, examId.toString())).isEqualTo(1);
    }
}
