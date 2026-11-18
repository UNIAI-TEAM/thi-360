package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructure;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.infrastructure.ExamStructureRepository;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.shared.api.NotFoundException;

/** Reads check inputs in a read-only transaction before the solver begins. */
@Service
public class ExamCheckSnapshotReader {
    private final ExamRepository exams;
    private final ExamStructureRepository structures;
    private final ExamKindProfileRepository profiles;
    private final ExamBlockRefRepository references;
    private final QuestionSourcePort source;
    private final CatalogLookup catalog;
    private final ExamCheckFingerprint fingerprint;
    private final ObjectMapper mapper;

    public ExamCheckSnapshotReader(ExamRepository exams, ExamStructureRepository structures,
            ExamKindProfileRepository profiles, ExamBlockRefRepository references, QuestionSourcePort source,
            CatalogLookup catalog, ExamCheckFingerprint fingerprint, ObjectMapper mapper) {
        this.exams = exams;
        this.structures = structures;
        this.profiles = profiles;
        this.references = references;
        this.source = source;
        this.catalog = catalog;
        this.fingerprint = fingerprint;
        this.mapper = mapper;
    }

    public record Snapshot(Exam exam, ExamStructureDocument structure,
                           ExamCheckResultStore.Input persistenceInput) { }

    @Transactional(readOnly = true)
    public Snapshot read(UUID examId) {
        Exam exam = exams.findById(examId).orElseThrow(() -> new NotFoundException("Exam not found"));
        ExamStructure structure = structures.findById(examId)
                .orElseThrow(() -> new NotFoundException("Exam structure not found"));
        JsonNode parts = structure.getParts().deepCopy();
        var codec = new ExamStructureCodec(mapper);
        var document = new ExamStructureDocument(codec.read(parts).parts(),
                codec.readQuotas(structure.getExamQuotas()));
        Set<UUID> subjectIds = references.findByExamIdAndVersionNo(examId, null).stream()
                .map(ref -> ref.getSubjectItemId()).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Long> sourceRevisions = source.sourceRevisions(subjectIds);
        Long profileRevision = exam.getKindItemId() == null ? null
                : profiles.findById(exam.getKindItemId()).map(profile -> profile.getRevision()).orElse(null);
        long catalogRevision = catalog.revision();
        var config = ExamCheckFingerprint.Config.from(exam);
        String hash = fingerprint.calculate(codec.write(document), config, profileRevision, catalogRevision);
        ExamCheckResultStore.Input input = new ExamCheckResultStore.Input(examId, exam.getRevision(),
                structure.getStructureHash(), mapper.valueToTree(config), profileRevision, catalogRevision,
                hash, mapper.valueToTree(sourceRevisions));
        return new Snapshot(exam, document, input);
    }
}
