package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckSnapshotReader;

@Transactional
class ExamCheckSnapshotReaderIT extends AbstractIntegrationTest {
    private static final UUID EXAM_ID = UUID.fromString("00000000-0000-7000-8700-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-7000-8200-000000000201");
    @Autowired private ExamCheckSnapshotReader snapshots;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void capturesSourceRevisionsBeforeTheCheckRuns() {
        var before = snapshots.read(EXAM_ID);
        var captured = before.persistenceInput().sourceRevisions().path(SUBJECT_ID.toString());
        assertThat(captured.isIntegralNumber()).isTrue();
        long revision = captured.longValue();
        jdbc.update("INSERT INTO questionbank.question_source_revision (subject_item_id, revision) VALUES (?, 1) "
                + "ON CONFLICT (subject_item_id) DO UPDATE SET revision = questionbank.question_source_revision.revision + 1",
                SUBJECT_ID);
        var after = snapshots.read(EXAM_ID);
        assertThat(before.persistenceInput().sourceRevisions().path(SUBJECT_ID.toString()).longValue()).isEqualTo(revision);
        assertThat(after.persistenceInput().sourceRevisions().path(SUBJECT_ID.toString()).longValue()).isEqualTo(revision + 1);
    }
}
