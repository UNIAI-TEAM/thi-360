package vn.unicom.thi360.questionbank.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.questionbank.domain.importing.ColumnMapping;
import vn.unicom.thi360.questionbank.domain.importing.ExistingQuestionRef;
import vn.unicom.thi360.questionbank.domain.importing.ImportCatalog;
import vn.unicom.thi360.questionbank.domain.importing.ImportDictionary;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileException;
import vn.unicom.thi360.questionbank.domain.importing.ImportQuestionDraft;
import vn.unicom.thi360.questionbank.domain.importing.ImportRowResult;
import vn.unicom.thi360.questionbank.domain.importing.ImportRowStatus;
import vn.unicom.thi360.questionbank.domain.importing.ImportSheet;
import vn.unicom.thi360.questionbank.domain.importing.ImportSummary;
import vn.unicom.thi360.questionbank.domain.importing.QuestionImportParser;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUpload;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUploadRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/** Parses and validates an upload without modifying question or import tables. */
@Service
public class QuestionImportValidationService {
    private final QuestionImportUploadRepository uploads;
    private final ObjectStorage storage;
    private final QuestionImportUploadService files;
    private final QuestionImportLookup lookup;
    private final QuestionImportDraftMapper mapper;
    private final QuestionImportSafety safety;
    private final Clock clock;

    public QuestionImportValidationService(QuestionImportUploadRepository uploads, ObjectStorage storage,
            QuestionImportUploadService files, QuestionImportLookup lookup, QuestionImportDraftMapper mapper,
            QuestionImportSafety safety, Clock clock) {
        this.uploads = uploads;
        this.storage = storage;
        this.files = files;
        this.lookup = lookup;
        this.mapper = mapper;
        this.safety = safety;
        this.clock = clock;
    }

    public record Issue(String code, String severity, String field, String column, String value,
            Integer relatedRow, String questionType, String subjectCode) {}
    public record Row(int rowNumber, String sourceCode, ImportRowStatus status, List<Issue> issues,
            ImportQuestionDraft draft, ExistingQuestionRef duplicateOf, boolean isSetPassage,
            Integer parentRowNumber, boolean dropped) {}
    public record Validation(UUID uploadId, List<Row> rows, ImportSummary summary,
            Map<UUID, String> knowledgeNames) {}
    record Checked(Validation validation, ImportCatalog catalog, List<ExistingQuestionRef> existing) {}

    @Transactional(readOnly = true)
    public Validation validate(UUID id, Map<String, String> mapping) throws ImportFileException {
        QuestionImportUpload upload = uploads.findById(id)
                .filter(row -> row.getStatus() == QuestionImportUpload.Status.UPLOADED
                        && row.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new NotFoundException("IMPORT_SOURCE_GONE", null));
        byte[] bytes = storage.get(upload.getObjectKey()).orElseThrow(() -> new NotFoundException("IMPORT_SOURCE_GONE", null));
        return check(id, files.read(upload.getFileName(), bytes), new ColumnMapping(mapping)).validation();
    }

    QuestionImportUpload requireUpload(UUID id) {
        QuestionImportUpload upload = uploads.findById(id).orElseThrow(() -> new NotFoundException("IMPORT_SOURCE_GONE", null));
        if (upload.getStatus() == QuestionImportUpload.Status.COMMITTED) {
            throw new ConflictException("IMPORT_ALREADY_COMMITTED");
        }
        if (!upload.getExpiresAt().isAfter(clock.instant())) {
            throw new NotFoundException("IMPORT_SOURCE_GONE", null);
        }
        return upload;
    }

    Checked check(UUID uploadId, ImportSheet sheet, ColumnMapping mapping) throws ImportFileException {
        var snapshot = lookup.catalog();
        ImportCatalog catalog = snapshot.catalog();
        List<ExistingQuestionRef> existing = lookup.existing(sheet, mapping);
        var parsed = QuestionImportParser.parse(sheet, mapping, catalog, existing, ImportDictionary.standard());
        Map<Integer, List<Issue>> issues = new HashMap<>();
        for (ImportRowResult row : parsed.rows()) {
            List<Issue> list = new ArrayList<>();
            row.issues().forEach(issue -> list.add(new Issue(issue.code().name(),
                    issue.severity().name().toLowerCase(Locale.ROOT),
                    issue.field() == null ? null : issue.field().code(), issue.column(), issue.value(),
                    issue.relatedRow(), issue.questionType() == null ? null : issue.questionType().name(), issue.subjectCode())));
            if (row.draft() != null) {
                safety.htmlErrors(row.draft()).forEach(error -> list.add(new Issue(
                        "UNSAFE_CONTENT", "error", error.path(), null, error.code(), null,
                        row.draft().type() == null ? null : row.draft().type().name(), row.draft().subjectCode())));
            }
            if (!row.hasErrors() && !row.dropped() && row.draft() != null && row.draft().type() != null) {
                var mapped = mapper.map(row.draft(), catalog);
                safety.validate(mapped.request()).stream()
                        .filter(error -> !"QUESTION_CONTENT_UNSAFE".equals(error.code()))
                        .filter(error -> !row.isSetPassage() || !error.path().startsWith("body.children["))
                        .forEach(error -> list.add(new Issue("SAVE_VALIDATION", "error", error.path(), null,
                                error.code(), null, row.draft().type().name(), row.draft().subjectCode())));
            }
            issues.put(row.rowNumber(), list);
        }
        Map<Integer, Boolean> blockedSets = new HashMap<>();
        for (ImportRowResult row : parsed.rows()) {
            if ((row.isSetPassage() || row.parentRowNumber() != null)
                    && (row.dropped() || issues.get(row.rowNumber()).stream().anyMatch(i -> "error".equals(i.severity())))) {
                blockedSets.put(row.isSetPassage() ? row.rowNumber() : row.parentRowNumber(), true);
            }
        }
        List<Row> rows = new ArrayList<>();
        for (ImportRowResult row : parsed.rows()) {
            List<Issue> rowIssues = issues.get(row.rowNumber());
            Integer setRow = row.isSetPassage() ? Integer.valueOf(row.rowNumber()) : row.parentRowNumber();
            boolean blockedSet = setRow != null && blockedSets.containsKey(setRow);
            boolean hasError = rowIssues.stream().anyMatch(i -> "error".equals(i.severity()));
            if (row.isSetPassage() && blockedSet && !hasError) {
                rowIssues.add(new Issue("SET_CHILD_INVALID", "error", null, null, null, null,
                        row.draft() == null || row.draft().type() == null ? null : row.draft().type().name(), null));
            }
            boolean dropped = row.dropped() || blockedSet;
            ImportRowStatus status = hasError || dropped ? ImportRowStatus.ERROR : row.status();
            boolean unsafe = rowIssues.stream().anyMatch(i -> "UNSAFE_CONTENT".equals(i.code()));
            rows.add(new Row(row.rowNumber(), row.sourceCode(), status, List.copyOf(rowIssues),
                    unsafe || blockedSet ? null : row.draft(), row.duplicateOf(), row.isSetPassage(),
                    row.parentRowNumber(), dropped));
        }
        int errors = (int) rows.stream().filter(r -> r.status() == ImportRowStatus.ERROR).count();
        int duplicates = (int) rows.stream().filter(r -> r.status() == ImportRowStatus.DUPLICATE).count();
        int valid = rows.size() - errors - duplicates;
        int sets = (int) rows.stream().filter(r -> r.isSetPassage() && r.status() == ImportRowStatus.VALID).count();
        int children = (int) rows.stream().filter(r -> r.parentRowNumber() != null && r.status() == ImportRowStatus.VALID).count();
        return new Checked(new Validation(uploadId, List.copyOf(rows),
                new ImportSummary(rows.size(), valid, errors, duplicates, sets, children), snapshot.knowledgeNames()),
                catalog, existing);
    }
}
