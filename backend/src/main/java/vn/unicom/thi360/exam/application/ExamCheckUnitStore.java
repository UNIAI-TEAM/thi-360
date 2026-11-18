package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists a solver unit only while the captured exam revision is still current. */
@Service
public class ExamCheckUnitStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ExamCheckUnitStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Unit(String key, List<String> sectionIds, String fingerprint, long sourceRevision,
                       String outcome, JsonNode issues, JsonNode witness, Instant checkedAt, long durationMs) { }

    @Transactional(readOnly = true)
    public Map<String, Unit> load(UUID examId) {
        Map<String, Unit> units = new LinkedHashMap<>();
        jdbc.query("SELECT unit_key, section_ids, input_fingerprint, source_revision, outcome, "
                        + "issues::text, witness::text, checked_at, duration_ms FROM exam.exam_check_unit WHERE exam_id = ?",
                rs -> {
                    Unit unit = new Unit(rs.getString(1), List.copyOf(Arrays.asList((String[]) rs.getArray(2).getArray())),
                            rs.getString(3), rs.getLong(4), rs.getString(5), parse(rs.getString(6)),
                            parse(rs.getString(7)), rs.getTimestamp(8).toInstant(), rs.getLong(9));
                    units.put(unit.key(), unit);
                }, examId);
        return units;
    }

    @Transactional
    public boolean save(UUID examId, long revision, List<Unit> units) {
        List<Long> revisions = jdbc.queryForList("SELECT revision FROM exam.exam WHERE id = ? FOR UPDATE", Long.class, examId);
        if (revisions.isEmpty() || revisions.getFirst() != revision) return false;
        for (Unit unit : units) {
            jdbc.update("INSERT INTO exam.exam_check_unit (exam_id, unit_key, section_ids, input_fingerprint, "
                            + "source_revision, outcome, issues, witness, checked_at, duration_ms) "
                            + "VALUES (?, ?, ARRAY(SELECT jsonb_array_elements_text(?::jsonb)), ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?) "
                            + "ON CONFLICT (exam_id, unit_key) DO UPDATE SET section_ids = EXCLUDED.section_ids, "
                            + "input_fingerprint = EXCLUDED.input_fingerprint, source_revision = EXCLUDED.source_revision, "
                            + "outcome = EXCLUDED.outcome, issues = EXCLUDED.issues, witness = EXCLUDED.witness, "
                            + "checked_at = EXCLUDED.checked_at, duration_ms = EXCLUDED.duration_ms",
                    examId, unit.key(), json(mapper.valueToTree(unit.sectionIds())), unit.fingerprint(),
                    unit.sourceRevision(), unit.outcome(), json(unit.issues()), json(unit.witness()),
                    Timestamp.from(unit.checkedAt()), unit.durationMs());
        }
        return true;
    }

    private JsonNode parse(String json) {
        if (json == null) return null;
        try { return mapper.readTree(json); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid saved check unit", e); }
    }

    private String json(JsonNode node) {
        if (node == null) return null;
        try { return mapper.writeValueAsString(node); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid check unit", e); }
    }
}
