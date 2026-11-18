package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamPublishRequest;
import vn.unicom.thi360.exam.api.ExamPublishResponse;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionUsageKind;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

@Service
public class ExamPublishService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExamCheckValidity validity;
    private final QuestionSourcePort source;
    private final CatalogLookup catalog;
    private final IdempotentCommand idempotency;
    private final CurrentUserProvider currentUser;
    private final AuditRecorder audit;

    public ExamPublishService(JdbcTemplate jdbc, ObjectMapper mapper, ExamCheckValidity validity,
            QuestionSourcePort source, CatalogLookup catalog, IdempotentCommand idempotency, CurrentUserProvider currentUser,
            AuditRecorder audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.validity = validity;
        this.source = source;
        this.catalog = catalog;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ExamPublishResponse publish(UUID examId, String key, ExamPublishRequest request) {
        return idempotency.execute(key, "exam.publish", examId, request, ExamPublishResponse.class,
                () -> publishOnce(examId, request));
    }

    private ExamPublishResponse publishOnce(UUID examId, ExamPublishRequest request) {
        var rows = jdbc.query("SELECT revision, name, kind_item_id, timing, duration_minutes FROM exam.exam WHERE id = ? FOR UPDATE",
                (rs, n) -> new ExamRow(rs.getLong(1), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getObject(5, Integer.class)), examId);
        if (rows.isEmpty()) throw new NotFoundException("Exam not found");
        ExamRow exam = rows.getFirst();
        if (exam.revision() != request.expectedRevision()) throw new VersionConflictException(ExamErrorCodes.REVISION_CONFLICT, null);
        Set<String> selected = new LinkedHashSet<>(request.pathIds());
        if (selected.isEmpty()) throw new BusinessRuleException(ExamErrorCodes.NO_PATH_SELECTED, null);
        if (selected.contains(null) || selected.contains("")) throw new BusinessRuleException(ExamErrorCodes.PATH_NOT_VALID, null);
        var checks = jdbc.query("SELECT id, result::text, witness::text FROM exam.exam_check_run WHERE exam_id = ?",
                (rs, n) -> new CheckRow(rs.getObject(1, UUID.class), json(rs.getString(2)), json(rs.getString(3))), examId);
        if (checks.isEmpty()) throw checkRequired("NO_CHECK");
        CheckRow check = checks.getFirst();
        var initial = validity.assess(List.of(examId)).get(examId);
        if (initial == null || initial.invalidReason() != null) {
            throw checkRequired(initial == null ? "NO_CHECK" : initial.invalidReason());
        }
        Set<String> valid = new LinkedHashSet<>();
        Set<String> allPaths = new LinkedHashSet<>();
        for (JsonNode path : check.result().path("paths")) {
            String id = path.path("pathId").asText();
            allPaths.add(id);
            if ("VALID".equals(path.path("result").asText())) valid.add(id);
        }
        if (!valid.containsAll(selected)) throw new BusinessRuleException(ExamErrorCodes.PATH_NOT_VALID, null);
        List<QuestionSourcePort.VersionKey> picks = new ArrayList<>();
        Set<String> witnessed = new LinkedHashSet<>();
        for (JsonNode path : check.witness()) {
            String id = witnessPathId(path.path("path"));
            if (!selected.contains(id)) continue;
            witnessed.add(id);
            collectPicks(path.path("witness"), picks);
            for (JsonNode selection : path.path("selections")) collectPicks(selection.path("witness"), picks);
        }
        if (!witnessed.containsAll(selected)) throw new IllegalStateException("Missing published path witness");
        try {
            source.recordUsage(examId, QuestionUsageKind.PUBLISH_WITNESS, picks);
        } catch (IllegalStateException e) {
            if (!"Question no longer exists".equals(e.getMessage())) throw e;
            var stale = validity.assess(List.of(examId)).get(examId);
            if (stale != null && "SOURCE_CHANGED".equals(stale.invalidReason())) throw checkRequired("SOURCE_CHANGED");
            throw e;
        }
        String structure = jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?", String.class, examId);
        var snapshot = new ExamVersionSnapshot(mapper);
        JsonNode document = json(structure);
        long materializationRevision = catalog.revision();
        var allCombinations = snapshot.combinations(document, allPaths, exam.kindId(), catalog);
        if (catalog.revision() != materializationRevision) throw checkRequired("CONFIG_CHANGED");
        var assessment = validity.assess(List.of(examId)).get(examId);
        if (assessment == null || assessment.invalidReason() != null) {
            throw checkRequired(assessment == null ? "NO_CHECK" : assessment.invalidReason());
        }
        if (catalog.revision() != materializationRevision) throw checkRequired("CONFIG_CHANGED");
        UUID actor = currentUser.requireCurrentUser().id();
        int version = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0) + 1 FROM exam.exam_version WHERE exam_id = ?", Integer.class, examId);
        List<ExamPublishResponse.KeptPath> kept = new ArrayList<>();
        var versions = jdbc.query("SELECT version_no, serving_path_ids FROM exam.exam_version WHERE exam_id = ? AND state = 'SERVING' ORDER BY version_no",
                (rs, n) -> new VersionPaths(rs.getInt(1), List.of((String[]) rs.getArray(2).getArray())), examId);
        String quotas = snapshot.resolvedQuotas(document, selected).toString();
        var extra = snapshot.selectedCombinations(allCombinations, selected);
        extra.set("omittedGroups", snapshot.omittedGroups(document));
        extra.set("examQuotas", json(jdbc.queryForObject("SELECT exam_quotas::text FROM exam.exam_structure WHERE exam_id = ?", String.class, examId)));
        String extraConfig = extra.toString();
        String snapshotParts = snapshot.parts(document, check.result(), selected, exam.timing(), exam.durationMinutes()).toString();
        String configSql = "jsonb_build_object('code', e.code, 'name', e.name, 'description', e.description, 'instructions', e.instructions, 'kindItemId', e.kind_item_id, 'scope', e.scope, 'partCode', e.part_code, 'subjectItemId', e.subject_item_id, 'templateCode', e.template_code, 'structureYear', e.structure_year, 'matrixYear', e.matrix_year, 'yearsConfirmed', e.years_confirmed, 'timing', e.timing, 'durationMinutes', e.duration_minutes, 'maxScore', e.max_score, 'referenceMax', e.reference_max, 'practiceScoring', e.practice_scoring, 'coverAssetId', e.cover_asset_id, 'kindProfile', jsonb_build_object('scopes', p.scopes, 'templateBy', p.template_by, 'timing', p.timing, 'maxScore', p.max_score, 'referenceMax', p.reference_max, 'practiceScoring', p.practice_scoring, 'trueFalseScoring', p.tf_scoring, 'multipleChoiceRule', p.mc_rule), 'resolvedQuotas', ?::jsonb)";
        Set<Integer> matchingVersions = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT v.version_no FROM exam.exam_version v JOIN exam.exam e ON e.id = v.exam_id "
                + "JOIN exam.exam_structure s ON s.exam_id = e.id LEFT JOIN exam.exam_kind_profile p ON p.exam_item_id = e.kind_item_id "
                + "WHERE (v.config - 'resolvedQuotas' - 'combinations') = "
                + "((" + configSql + " || ?::jsonb) - 'resolvedQuotas' - 'combinations') "
                + "AND jsonb_set(v.parts, '{parts}', COALESCE((SELECT jsonb_agg(p.value - 'paths' ORDER BY p.ordinality) "
                + "FROM jsonb_array_elements(v.parts->'parts') WITH ORDINALITY p(value, ordinality)), '[]'::jsonb)) = s.parts "
                + "AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(?::jsonb->'combinations') c "
                + "WHERE EXISTS (SELECT 1 FROM unnest(v.serving_path_ids) path "
                + "WHERE ((c->>'partId') || ':' || (c->>'code')) = ANY(string_to_array(path, '+'))) "
                + "AND NOT (COALESCE(v.config->'combinations','[]'::jsonb) @> jsonb_build_array(c))) AND e.id = ?",
                Integer.class, quotas, extraConfig, extraConfig, examId));
        Set<String> alreadyServing = new LinkedHashSet<>();
        for (VersionPaths old : versions) {
            if (matchingVersions.contains(old.version())) alreadyServing.addAll(old.paths());
        }
        if (alreadyServing.containsAll(selected)) throw new ConflictException(ExamErrorCodes.NOTHING_TO_PUBLISH);
        for (VersionPaths old : versions) {
            List<String> remaining = old.paths().stream().filter(path -> !selected.contains(path)).toList();
            for (String path : remaining) kept.add(new ExamPublishResponse.KeptPath(path, old.version()));
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("UPDATE exam.exam_version SET serving_path_ids = ?, state = ? WHERE exam_id = ? AND version_no = ?");
                ps.setArray(1, connection.createArrayOf("text", remaining.toArray()));
                ps.setString(2, remaining.isEmpty() ? "RETIRED" : "SERVING");
                ps.setObject(3, examId);
                ps.setInt(4, old.version());
                return ps;
            });
        }
        String sql = "INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by, note) "
                + "SELECT ?, e.id, ?, " + configSql + " || ?::jsonb, ?::jsonb, ?, ?, ?, 'SERVING', 'PUBLISHED', ?, ?, ? FROM exam.exam e JOIN exam.exam_structure s ON s.exam_id = e.id LEFT JOIN exam.exam_kind_profile p ON p.exam_item_id = e.kind_item_id WHERE e.id = ?";
        Instant now = Instant.now();
        int inserted = jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            Array paths = connection.createArrayOf("text", selected.toArray());
            ps.setObject(1, Ids.newId());
            ps.setInt(2, version);
            ps.setString(3, quotas);
            ps.setString(4, extraConfig);
            ps.setString(5, snapshotParts);
            ps.setArray(6, paths);
            ps.setArray(7, paths);
            ps.setObject(8, check.id());
            ps.setObject(9, java.sql.Timestamp.from(now));
            ps.setObject(10, actor);
            ps.setString(11, request.note());
            ps.setObject(12, examId);
            return ps;
        });
        if (inserted != 1) throw new IllegalStateException("Exam structure missing");
        jdbc.update("INSERT INTO exam.exam_block_ref (id, exam_id, version_no, block_id, part_id, option_code, section_id, subject_item_id, question_type, answer_types, knowledge_item_ids, knowledge_mode, knowledge_inherited, tag_item_ids, competency_item_ids, required_points, combination_item_id) "
                + "SELECT gen_random_uuid(), exam_id, ?, block_id, part_id, option_code, section_id, subject_item_id, question_type, answer_types, knowledge_item_ids, knowledge_mode, knowledge_inherited, tag_item_ids, competency_item_ids, required_points, combination_item_id FROM exam.exam_block_ref WHERE exam_id = ? AND version_no IS NULL", version, examId);
        String allCombinationsJson = allCombinations.toString();
        Set<String> published = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT DISTINCT unnest(old.serving_path_ids) FROM exam.exam_version old "
                + "JOIN exam.exam_version current ON current.exam_id = old.exam_id AND current.version_no = ? "
                + "WHERE old.exam_id = ? AND (old.config - 'resolvedQuotas' - 'combinations') = "
                + "(current.config - 'resolvedQuotas' - 'combinations') "
                + "AND jsonb_set(old.parts, '{parts}', COALESCE((SELECT jsonb_agg(p.value - 'paths' ORDER BY p.ordinality) "
                + "FROM jsonb_array_elements(old.parts->'parts') WITH ORDINALITY p(value, ordinality)), '[]'::jsonb)) = "
                + "jsonb_set(current.parts, '{parts}', COALESCE((SELECT jsonb_agg(p.value - 'paths' ORDER BY p.ordinality) "
                + "FROM jsonb_array_elements(current.parts->'parts') WITH ORDINALITY p(value, ordinality)), '[]'::jsonb)) "
                + "AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(old.config->'combinations','[]'::jsonb)) c "
                + "WHERE EXISTS (SELECT 1 FROM unnest(old.serving_path_ids) path "
                + "WHERE ((c->>'partId') || ':' || (c->>'code')) = ANY(string_to_array(path, '+'))) "
                + "AND NOT (?::jsonb->'combinations' @> jsonb_build_array(c)))",
                String.class, version, examId, allCombinationsJson));
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED', serving_version = ?, has_unpublished_changes = ?, updated_at = ?, updated_by = ?, revision = revision + 1 WHERE id = ?",
                version, !published.containsAll(allPaths), java.sql.Timestamp.from(now), actor, examId);
        audit.record(AuditEvent.success("EXAM", "EXAM.PUBLISH", "exam", examId.toString(), exam.name(),
                java.util.Map.of("version", version, "pathIds", List.copyOf(selected))));
        return new ExamPublishResponse(examId, version, List.copyOf(selected), kept);
    }

    private static ConflictException checkRequired(String reason) {
        return new ConflictException(ExamErrorCodes.CHECK_REQUIRED, null, List.of(new FieldError("reason", reason, reason)));
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid check result", e); }
    }

    private static String witnessPathId(JsonNode path) {
        List<String> ids = new ArrayList<>();
        for (JsonNode choice : path.path("chosen")) ids.add(choice.path("optionId").asText());
        return ids.isEmpty() ? "default" : String.join("+", ids);
    }

    private static void collectPicks(JsonNode node, List<QuestionSourcePort.VersionKey> picks) {
        if (node.isArray()) {
            for (JsonNode child : node) collectPicks(child, picks);
        } else if (node.isObject()) {
            if (node.hasNonNull("questionId") && node.hasNonNull("versionNo")) {
                picks.add(new QuestionSourcePort.VersionKey(UUID.fromString(node.path("questionId").asText()), node.path("versionNo").asInt()));
            } else {
                node.elements().forEachRemaining(child -> collectPicks(child, picks));
            }
        }
    }

    private record ExamRow(long revision, String name, UUID kindId, String timing, Integer durationMinutes) { }
    private record CheckRow(UUID id, JsonNode result, JsonNode witness) { }
    private record VersionPaths(int version, List<String> paths) { }
}
