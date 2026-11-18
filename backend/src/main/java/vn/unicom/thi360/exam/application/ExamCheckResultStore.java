package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.shared.ids.Ids;

/** Atomically saves a completed check only while its input exam revision still matches. */
@Service
public class ExamCheckResultStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExamCheckValidity validity;

    public ExamCheckResultStore(JdbcTemplate jdbc, ObjectMapper mapper, ExamCheckValidity validity) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.validity = validity;
    }

    public record Input(UUID examId, long examRevision, String structureHash, JsonNode configSnapshot,
                        Long profileRevision, long catalogRevision, String fingerprint,
                        JsonNode sourceRevisions) { }

    @Transactional
    public boolean save(Input input, String status, int issueCount, Instant checkedAt,
                        long durationMs, JsonNode result, JsonNode witness) {
        int updated = jdbc.update("UPDATE exam.exam SET check_status = ?, check_issue_count = ?, checked_at = ? "
                        + "WHERE id = ? AND revision = ?", status, issueCount, Timestamp.from(checkedAt),
                input.examId(), input.examRevision());
        if (updated == 0) {
            return false;
        }
        jdbc.update("INSERT INTO exam.exam_check_run (id, exam_id, exam_revision, structure_hash, config_snapshot, "
                        + "profile_revision, catalog_revision, input_fingerprint, source_revisions, result, witness, "
                        + "checked_at, duration_ms) VALUES (?, ?, ?, ?, "
                        + validity.storedConfig("?::jsonb", "?")
                        + ", ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?) ON CONFLICT (exam_id) DO UPDATE SET id = EXCLUDED.id, "
                        + "exam_revision = EXCLUDED.exam_revision, structure_hash = EXCLUDED.structure_hash, "
                        + "config_snapshot = EXCLUDED.config_snapshot, profile_revision = EXCLUDED.profile_revision, "
                        + "catalog_revision = EXCLUDED.catalog_revision, input_fingerprint = EXCLUDED.input_fingerprint, "
                        + "source_revisions = EXCLUDED.source_revisions, result = EXCLUDED.result, "
                        + "witness = EXCLUDED.witness, checked_at = EXCLUDED.checked_at, duration_ms = EXCLUDED.duration_ms",
                Ids.newId(), input.examId(), input.examRevision(), input.structureHash(), json(input.configSnapshot()),
                input.examId(),
                input.profileRevision(), input.catalogRevision(), input.fingerprint(), json(input.sourceRevisions()),
                json(result), json(witness), Timestamp.from(checkedAt), durationMs);
        return true;
    }

    private String json(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize check data", e);
        }
    }
}
