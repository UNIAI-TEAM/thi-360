package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckJobStore;
import vn.unicom.thi360.exam.application.ExamCheckScope;

class ExamCheckJobStoreIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExamCheckJobStore jobs;

    @Test
    void concurrentClaimsShareRunAndAbandonedRunCanBeReplaced() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) "
                        + "VALUES (?, ?, ?, 'Job lifecycle test', 'FULL', 'SHARED')",
                examId, examId.toString(), examId.toString());
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> { ready.countDown(); go.await(); return jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null)); });
                var second = executor.submit(() -> { ready.countDown(); go.await(); return jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null)); });
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                ExamCheckJobStore.Start a = first.get(20, TimeUnit.SECONDS);
                ExamCheckJobStore.Start b = second.get(20, TimeUnit.SECONDS);
                assertThat(a.runId()).isEqualTo(b.runId());
                assertThat(a.created()).isNotEqualTo(b.created());

                jdbc.update("UPDATE exam.exam_check_job SET started_at = now() - interval '151 seconds' WHERE id = ?",
                        a.runId());
                assertThat(jobs.get(examId, a.runId()).state()).isEqualTo("FAILED");
                assertThat(jobs.get(examId, a.runId()).errorCode()).isEqualTo("CHECK_RUN_ABANDONED");
                assertThat(jobs.complete(a.runId(), "{\"ok\":true}")).isFalse();
                ExamCheckJobStore.Start next = jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null));
                assertThat(next.created()).isTrue();
                assertThat(next.runId()).isNotEqualTo(a.runId());
                assertThat(jobs.complete(next.runId(), "{\"ok\":true}")).isTrue();
                ExamCheckJobStore.Start orphan = jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null));
                jdbc.update("UPDATE exam.exam_check_job SET started_at = now() - interval '151 seconds' WHERE id = ?",
                        orphan.runId());
                ExamCheckJobStore.Start replacement = jobs.startOrGet(examId, actorId, ExamCheckScope.from(null, null, null));
                assertThat(replacement.created()).isTrue();
                assertThat(replacement.runId()).isNotEqualTo(orphan.runId());
                assertThat(jobs.get(examId, orphan.runId()).errorCode()).isEqualTo("CHECK_RUN_ABANDONED");
                assertThat(jobs.get(examId, next.runId()).state()).isEqualTo("DONE");
                assertThat(jobs.get(examId, next.runId()).resultJson()).contains("\"ok\": true");
            }
        } finally {
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", examId);
        }
    }
}
