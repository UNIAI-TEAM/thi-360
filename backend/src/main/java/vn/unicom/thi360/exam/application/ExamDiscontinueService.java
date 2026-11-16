package vn.unicom.thi360.exam.application;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.exam.api.ExamDetailResponse;
import vn.unicom.thi360.exam.api.ExamVersionLifecycleController.DiscontinueRequest;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

@Service
public class ExamDiscontinueService {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final ExamService exams;
    private final IdempotentCommand idempotency;
    private final CurrentUserProvider currentUser;
    private final AuditRecorder audit;

    public ExamDiscontinueService(JdbcTemplate jdbc, EntityManager entities, ExamService exams,
            IdempotentCommand idempotency, CurrentUserProvider currentUser, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.entities = entities;
        this.exams = exams;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ExamDetailResponse discontinue(UUID id, String key, DiscontinueRequest request) {
        return idempotency.execute(key, "exam.discontinue", id, request, ExamDetailResponse.class,
                () -> discontinueOnce(id, request));
    }

    private ExamDetailResponse discontinueOnce(UUID id, DiscontinueRequest request) {
        entities.flush();
        List<Row> rows = jdbc.query("select revision, status, name from exam.exam where id = ? for update",
                (rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3)), id);
        if (rows.isEmpty()) throw new NotFoundException("Exam not found");
        Row row = rows.getFirst();
        if (row.revision() != request.expectedRevision()) throw new VersionConflictException("EXAM_REVISION_CONFLICT", null);
        if (!"PUBLISHED".equals(row.status())) throw new ConflictException("EXAM_NOT_PUBLISHED");
        UUID actor = currentUser.requireCurrentUser().id();
        Instant now = Instant.now();
        jdbc.update("update exam.exam set status = 'DISCONTINUED', updated_at = ?, updated_by = ?, revision = revision + 1 where id = ?",
                java.sql.Timestamp.from(now), actor, id);
        jdbc.update("update exam.exam_version set status = 'DISCONTINUED', discontinued_at = ?, discontinued_by = ? "
                        + "where exam_id = ? and status = 'PUBLISHED'",
                java.sql.Timestamp.from(now), actor, id);
        audit.record(AuditEvent.success("EXAM", "EXAM.DISCONTINUE", "exam", id.toString(), row.name(),
                Map.of("status", "DISCONTINUED")));
        Exam managed = entities.find(Exam.class, id);
        entities.refresh(managed);
        return exams.getExamDetail(id);
    }

    private record Row(long revision, String status, String name) {}
}
