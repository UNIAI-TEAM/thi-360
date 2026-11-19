package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructure;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.infrastructure.ExamStructureRepository;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionUsageKind;
import vn.unicom.thi360.shared.api.NotFoundException;

/** Short database transactions surrounding, but never enclosing, the solver. */
@Service
public class ExamPreviewStore {
    private final ExamRepository exams;
    private final ExamStructureRepository structures;
    private final QuestionSourcePort source;
    private final AuditRecorder audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ExamPreviewStore(ExamRepository exams, ExamStructureRepository structures,
            QuestionSourcePort source, AuditRecorder audit, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.exams = exams;
        this.structures = structures;
        this.source = source;
        this.audit = audit;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Snapshot(UUID id, String code, String timing, Integer durationMinutes,
                           String practiceScoring, BigDecimal maxScore, JsonNode parts, Exam exam,
                           JsonNode publishedCombinations) {}

    @Transactional(readOnly = true)
    public Snapshot load(UUID id) {
        Exam exam = exams.findById(id).orElseThrow(() -> new NotFoundException("Exam not found"));
        ExamStructure structure = structures.findById(id)
                .orElseThrow(() -> new NotFoundException("Exam structure not found"));
        ObjectNode parts = ((ObjectNode) structure.getParts()).deepCopy();
        if (structure.getExamQuotas() != null) parts.set("examQuotas", structure.getExamQuotas().deepCopy());
        return new Snapshot(exam.getId(), exam.getCode(), exam.getTiming(), exam.getDurationMinutes(),
                exam.getPracticeScoring(), exam.getMaxScore(), parts, exam, null);
    }

    /** Reconstruct the checker inputs from the immutable published configuration and structure. */
    @Transactional(readOnly = true)
    public Snapshot loadVersion(UUID id, int version, String pathId) {
        return loadVersionSnapshot(id, version, pathId, null);
    }

    @Transactional(readOnly = true)
    public Snapshot loadVersionForSection(UUID id, int version, String sectionId) {
        return loadVersionSnapshot(id, version, null, sectionId);
    }

    private Snapshot loadVersionSnapshot(UUID id, int version, String pathId, String sectionId) {
        var rows = jdbc.query("select config::text, parts::text, published_path_ids from exam.exam_version "
                        + "where exam_id = ? and version_no = ?",
                (rs, n) -> new VersionRow(rs.getString(1), rs.getString(2),
                        List.of((String[]) rs.getArray(3).getArray())), id, version);
        if (rows.isEmpty()) throw new NotFoundException(ExamErrorCodes.VERSION_NOT_FOUND, "Exam version not found");
        try {
            JsonNode config = mapper.readTree(rows.getFirst().config());
            JsonNode parts = mapper.readTree(rows.getFirst().parts());
            for (JsonNode part : parts.path("parts")) {
                if (part instanceof ObjectNode object) object.remove("paths");
            }
            if (parts instanceof ObjectNode object && config.has("examQuotas")) {
                object.set("examQuotas", config.path("examQuotas").deepCopy());
            }
            boolean published = sectionId == null ? rows.getFirst().paths().contains(pathId)
                    : publishedSection(parts, config, rows.getFirst().paths(), sectionId);
            if (!published) throw new NotFoundException("PATH_NOT_FOUND", "Exam version path not found");
            Integer duration = integer(config, "durationMinutes");
            BigDecimal max = decimal(config, "maxScore");
            String timing = config.path("timing").asText();
            String scoring = config.path("practiceScoring").asText();
            Exam exam = new Exam(id, config.path("code").asText(), config.path("name").asText(),
                    null, config.path("scope").asText(), timing, duration, max, scoring, Instant.EPOCH, null);
            String kindItemId = text(config, "kindItemId");
            exam.changeScope(kindItemId == null ? null : UUID.fromString(kindItemId),
                    config.path("scope").asText(), text(config, "partCode"), null,
                    integer(config, "structureYear"), integer(config, "matrixYear"),
                    config.path("yearsConfirmed").asBoolean(), text(config, "templateCode"),
                    timing, duration, max, null, Instant.EPOCH);
            exam.applyScoringProfile(decimal(config, "referenceMax"), scoring);
            int questionCount = 0;
            for (JsonNode part : parts.path("parts")) questionCount += part.path("questionCount").asInt();
            exam.applyStructureSummary(questionCount, false, false, 0);
            return new Snapshot(id, exam.getCode(), timing, duration, scoring, max, parts, exam,
                    config.path("combinations"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Invalid exam version snapshot", ex);
        }
    }

    private static boolean publishedSection(JsonNode parts, JsonNode config, List<String> paths, String sectionId) {
        if (paths.isEmpty()) return false;
        for (JsonNode part : parts.path("parts")) {
            for (JsonNode section : part.path("sections")) {
                if (sectionId.equals(section.path("id").asText())) return true;
            }
            for (JsonNode option : part.path("options")) {
                String optionId = option.path("id").asText();
                for (JsonNode section : option.path("sections")) {
                    if (sectionId.equals(section.path("id").asText()) && containsOption(paths, optionId)) return true;
                }
            }
            for (JsonNode section : part.path("subjectPool")) {
                if (!sectionId.equals(section.path("id").asText())) continue;
                String partId = part.path("id").asText();
                String subject = section.path("subjectCode").asText();
                for (JsonNode combination : config.path("combinations")) {
                    if (!partId.equals(combination.path("partId").asText())
                            || !containsOption(paths, partId + ":" + combination.path("code").asText())) continue;
                    for (JsonNode selected : combination.path("subjects")) {
                        if (subject.equals(selected.path("code").asText())) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsOption(List<String> paths, String optionId) {
        for (String path : paths) {
            for (String component : path.split("\\+", -1)) {
                if (component.equals(optionId)) return true;
            }
        }
        return false;
    }

    private record VersionRow(String config, String parts, List<String> paths) {}

    private static String text(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }

    private static Integer integer(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).intValue() : null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
    }

    @Transactional(readOnly = true)
    public Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> loadVersions(
            List<QuestionSourcePort.VersionKey> keys) {
        List<QuestionSourcePort.VersionKey> distinct = keys.stream().distinct().toList();
        Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions = new LinkedHashMap<>();
        for (QuestionSourcePort.Version version : source.loadVersions(distinct)) versions.put(version.key(), version);
        if (versions.size() != distinct.size()) throw new IllegalStateException("Selected version disappeared");
        return Map.copyOf(versions);
    }

    /** Record every selected root and the audit event atomically after the paper is built. */
    @Transactional
    public void recordPreview(UUID examId, String examCode, String pathId, long seed,
            List<QuestionSourcePort.VersionKey> keys) {
        List<QuestionSourcePort.VersionKey> distinct = keys.stream().distinct().toList();
        source.recordUsage(examId, QuestionUsageKind.PREVIEW, distinct);
        audit.record(AuditEvent.builder("EXAM", "EXAM.PREVIEW")
                .target("EXAM", examId.toString(), examCode)
                .after(Map.of("pathId", pathId, "seed", seed, "questions", distinct.size()))
                .build());
    }
}
