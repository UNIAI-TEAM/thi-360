package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructure;
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

    public ExamPreviewStore(ExamRepository exams, ExamStructureRepository structures,
            QuestionSourcePort source, AuditRecorder audit) {
        this.exams = exams;
        this.structures = structures;
        this.source = source;
        this.audit = audit;
    }

    public record Snapshot(UUID id, String code, String timing, Integer durationMinutes,
                           String practiceScoring, BigDecimal maxScore, JsonNode parts) {}

    @Transactional(readOnly = true)
    public Snapshot load(UUID id) {
        Exam exam = exams.findById(id).orElseThrow(() -> new NotFoundException("Exam not found"));
        JsonNode parts = structures.findById(id).map(ExamStructure::getParts)
                .orElseThrow(() -> new NotFoundException("Exam structure not found"));
        return new Snapshot(exam.getId(), exam.getCode(), exam.getTiming(), exam.getDurationMinutes(),
                exam.getPracticeScoring(), exam.getMaxScore(), parts.deepCopy());
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
