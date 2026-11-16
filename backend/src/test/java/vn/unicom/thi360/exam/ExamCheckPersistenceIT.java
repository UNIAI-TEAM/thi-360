package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import vn.unicom.thi360.AbstractIntegrationTest;

@Transactional
class ExamCheckPersistenceIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void onlyOneRunningJobPerExamButCompletedJobsCanCoexist() {
        UUID examId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) "
                + "VALUES (?, ?, ?, 'Check migration test', 'FULL', 'SHARED')",
                examId, examId.toString(), examId.toString());
        String insert = "INSERT INTO exam.exam_check_job "
                + "(id, exam_id, state, started_by, started_at) "
                + "VALUES (?, ?, 'RUNNING', ?, now()) ON CONFLICT DO NOTHING";
        UUID first = UUID.randomUUID();
        assertThat(jdbc.update(insert, first, examId, actor)).isEqualTo(1);
        assertThat(jdbc.update(insert, UUID.randomUUID(), examId, actor)).isZero();
        jdbc.update("UPDATE exam.exam_check_job SET state='DONE', finished_at=now() WHERE id=?", first);
        assertThat(jdbc.update(insert, UUID.randomUUID(), examId, actor)).isEqualTo(1);
    }
}
