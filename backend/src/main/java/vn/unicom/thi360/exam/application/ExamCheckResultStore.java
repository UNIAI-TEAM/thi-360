package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Atomically saves a whole-exam check only while its input exam revision and the template policy it was solved
 * against still match. Only a result whose checker evaluated the rules is stamped ({@link ExamCheckRulesStamp});
 * incomplete runs never receive a stamp. One-subject and one-part checks never reach this store.
 */
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

    /**
     * {@code templatePolicy} is the template policy ({@link ExamCheckValidity#templatePolicy}) read together with the
     * rest of the input, {@code {}} for the default policy. When it is given, the result is saved only while the
     * exam still has exactly that policy, and it is stored as the policy of the check; when it is null the policy is
     * read at save time.
     */
    public record Input(UUID examId, long examRevision, String structureHash, JsonNode configSnapshot,
                        Long profileRevision, long catalogRevision, String fingerprint,
                        JsonNode sourceRevisions, JsonNode templatePolicy) {
        public Input(UUID examId, long examRevision, String structureHash, JsonNode configSnapshot,
                     Long profileRevision, long catalogRevision, String fingerprint, JsonNode sourceRevisions) {
            this(examId, examRevision, structureHash, configSnapshot, profileRevision, catalogRevision, fingerprint,
                    sourceRevisions, null);
        }
    }

    /** Saves a result that was evaluated under the current rules (stamped). */
    @Transactional
    public boolean save(Input input, String status, int issueCount, Instant checkedAt,
                        long durationMs, JsonNode result, JsonNode witness) {
        return save(input, status, issueCount, checkedAt, durationMs, result, witness, true);
    }

    @Transactional
    public boolean save(Input input, String status, int issueCount, Instant checkedAt,
                        long durationMs, JsonNode result, JsonNode witness, boolean rulesEvaluated) {
        boolean captured = input.templatePolicy() != null;
        String policy = captured ? json(input.templatePolicy()) : null;
        List<Object> update = new ArrayList<>(List.of(status, issueCount, Timestamp.from(checkedAt),
                input.examId(), input.examRevision()));
        if (captured) update.add(policy);
        int updated = jdbc.update("UPDATE exam.exam e SET check_status = ?, check_issue_count = ?, checked_at = ? "
                        + "WHERE e.id = ? AND e.revision = ?"
                        + (captured ? " AND " + validity.currentPolicy("e") + " = ?::jsonb" : ""),
                update.toArray());
        if (updated == 0) {
            return false;
        }
        List<Object> insert = new ArrayList<>(java.util.Arrays.asList(Ids.newId(), input.examId(), input.examRevision(),
                input.structureHash(), json(input.configSnapshot())));
        String config;
        if (captured) {
            config = "?::jsonb || CASE WHEN ?::jsonb = '{}'::jsonb THEN '{}'::jsonb"
                    + " ELSE jsonb_build_object('templatePolicy', ?::jsonb) END";
            insert.add(policy);
            insert.add(policy);
        } else {
            config = validity.storedConfig("?::jsonb", "?");
            insert.add(input.examId());
        }
        insert.add(input.profileRevision());
        insert.add(input.catalogRevision());
        insert.add(input.fingerprint());
        insert.add(json(input.sourceRevisions()));
        insert.add(json(rulesEvaluated ? ExamCheckRulesStamp.stamped(result) : result));
        insert.add(json(witness));
        insert.add(Timestamp.from(checkedAt));
        insert.add(durationMs);
        jdbc.update("INSERT INTO exam.exam_check_run (id, exam_id, exam_revision, structure_hash, config_snapshot, "
                        + "profile_revision, catalog_revision, input_fingerprint, source_revisions, result, witness, "
                        + "checked_at, duration_ms) VALUES (?, ?, ?, ?, " + config
                        + ", ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?) ON CONFLICT (exam_id) DO UPDATE SET id = EXCLUDED.id, "
                        + "exam_revision = EXCLUDED.exam_revision, structure_hash = EXCLUDED.structure_hash, "
                        + "config_snapshot = EXCLUDED.config_snapshot, profile_revision = EXCLUDED.profile_revision, "
                        + "catalog_revision = EXCLUDED.catalog_revision, input_fingerprint = EXCLUDED.input_fingerprint, "
                        + "source_revisions = EXCLUDED.source_revisions, result = EXCLUDED.result, "
                        + "witness = EXCLUDED.witness, checked_at = EXCLUDED.checked_at, duration_ms = EXCLUDED.duration_ms",
                insert.toArray());
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
