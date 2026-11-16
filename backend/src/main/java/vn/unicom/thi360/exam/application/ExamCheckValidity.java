package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Compare persisted check inputs in batches without hashing each list row. */
@Service
public class ExamCheckValidity {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CatalogLookup catalog;
    private final QuestionSourcePort source;

    public ExamCheckValidity(JdbcTemplate jdbc, ObjectMapper mapper, CatalogLookup catalog, QuestionSourcePort source) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.catalog = catalog;
        this.source = source;
    }

    public record Assessment(String status, String invalidReason) { }

    @Transactional(readOnly = true)
    public Map<UUID, String> statuses(Collection<UUID> examIds) {
        Map<UUID, String> statuses = new HashMap<>();
        assess(examIds).forEach((id, assessment) -> statuses.put(id, assessment.status()));
        return statuses;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Assessment> assess(Collection<UUID> examIds) {
        if (examIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(examIds.size(), "?"));
        String sql = "SELECT e.id, e.check_status, r.id AS run_id, r.result->>'status' AS checked_status, "
                + "s.structure_hash AS current_hash, r.structure_hash AS saved_hash, "
                + "r.profile_revision, p.revision AS current_profile_revision, "
                + "r.catalog_revision, r.source_revisions::text AS source_revisions, "
                + "r.config_snapshot = jsonb_build_object("
                + "'kindItemId', e.kind_item_id, 'scope', e.scope, 'partCode', e.part_code, "
                + "'subjectItemId', e.subject_item_id, 'templateCode', e.template_code, "
                + "'structureYear', e.structure_year, 'matrixYear', e.matrix_year, 'yearsConfirmed', e.years_confirmed, "
                + "'timing', e.timing, 'durationMinutes', e.duration_minutes, 'maxScore', e.max_score, "
                + "'referenceMax', e.reference_max, 'practiceScoring', e.practice_scoring) AS config_matches "
                + "FROM exam.exam e LEFT JOIN exam.exam_structure s ON s.exam_id = e.id "
                + "LEFT JOIN exam.exam_kind_profile p ON p.exam_item_id = e.kind_item_id "
                + "LEFT JOIN exam.exam_check_run r ON r.exam_id = e.id WHERE e.id IN (" + placeholders + ")";
        List<Row> rows = jdbc.query(sql, (rs, row) -> new Row(rs.getObject("id", UUID.class),
                rs.getString("check_status"), rs.getObject("run_id", UUID.class), rs.getString("checked_status"),
                rs.getString("current_hash"),
                rs.getString("saved_hash"), (Long) rs.getObject("profile_revision"),
                (Long) rs.getObject("current_profile_revision"), (Long) rs.getObject("catalog_revision"),
                revisions(rs.getString("source_revisions")), rs.getBoolean("config_matches")), examIds.toArray());
        Set<UUID> subjects = new HashSet<>();
        for (Row row : rows) subjects.addAll(row.revisions().keySet());
        Map<UUID, Long> currentSources = subjects.isEmpty() ? Map.of() : source.sourceRevisions(subjects);
        boolean hasChecks = rows.stream().anyMatch(row -> row.runId() != null);
        long catalogRevision = hasChecks ? catalog.revision() : 0;
        Map<UUID, Assessment> assessments = new HashMap<>();
        for (Row row : rows) {
            String reason = null;
            if (row.runId() != null) {
                if (row.currentHash() == null || !Objects.equals(row.currentHash(), row.savedHash())) {
                    reason = "STRUCTURE_CHANGED";
                } else if (!row.configMatches() || !Objects.equals(row.profileRevision(), row.currentProfileRevision())
                        || !Objects.equals(row.catalogRevision(), catalogRevision)) {
                    reason = "CONFIG_CHANGED";
                } else if (row.revisions().entrySet().stream().anyMatch(entry ->
                        !Objects.equals(entry.getValue(), currentSources.getOrDefault(entry.getKey(), 0L)))) {
                    reason = "SOURCE_CHANGED";
                }
            }
            String status;
            if (row.runId() == null) {
                status = "UNCHECKED".equals(row.status()) ? "UNCHECKED" : "NEEDS_RECHECK";
            } else if (reason != null) {
                status = "NEEDS_RECHECK";
            } else if ("VALID".equals(row.checkedStatus()) || "INVALID".equals(row.checkedStatus())) {
                status = row.checkedStatus();
            } else {
                throw new IllegalStateException("Invalid persisted check status");
            }
            assessments.put(row.examId(), new Assessment(status, reason));
        }
        return assessments;
    }

    private Map<UUID, Long> revisions(String json) {
        if (json == null) return Map.of();
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Invalid persisted source revisions");
            }
            Map<UUID, Long> result = new HashMap<>();
            root.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isIntegralNumber() || !entry.getValue().canConvertToLong()) {
                    throw new IllegalStateException("Invalid persisted source revision");
                }
                result.put(UUID.fromString(entry.getKey()), entry.getValue().longValue());
            });
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid persisted source revisions", e);
        }
    }

    private record Row(UUID examId, String status, UUID runId, String checkedStatus, String currentHash, String savedHash,
                       Long profileRevision, Long currentProfileRevision, Long catalogRevision,
                       Map<UUID, Long> revisions, boolean configMatches) { }
}
