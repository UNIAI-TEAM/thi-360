package vn.unicom.thi360.questionbank.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.api.QuestionImportHistoryEntry;
import vn.unicom.thi360.questionbank.domain.Question;
import vn.unicom.thi360.questionbank.domain.importing.ColumnMapping;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileException;
import vn.unicom.thi360.questionbank.domain.importing.ImportRowStatus;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImport;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportErrorLine;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUpload;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUploadRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/** Commits one import, including history, audit and idempotency result in the caller's transaction. */
@Service
public class QuestionImportCommitService {
    private static final Logger log = LoggerFactory.getLogger(QuestionImportCommitService.class);
    public enum DuplicatePolicy { SKIP, NEW_VERSION }
    public record Request(UUID uploadId, Map<String, String> mapping, DuplicatePolicy duplicatePolicy, boolean skipErrors) {}

    private final QuestionImportUploadRepository uploads;
    private final QuestionImportRepository imports;
    private final QuestionRepository questions;
    private final QuestionImportValidationService validation;
    private final QuestionImportUploadService files;
    private final QuestionImportLookup lookup;
    private final QuestionImportDraftMapper mapper;
    private final QuestionService questionService;
    private final QuestionImportHistoryService history;
    private final IdempotentCommand idempotent;
    private final CurrentUserProvider users;
    private final UserDirectory directory;
    private final AuditRecorder audit;
    private final ObjectStorage storage;
    private final Clock clock;
    private final EntityManager entityManager;

    public QuestionImportCommitService(QuestionImportUploadRepository uploads, QuestionImportRepository imports,
            QuestionRepository questions, QuestionImportValidationService validation, QuestionImportUploadService files,
            QuestionImportLookup lookup, QuestionImportDraftMapper mapper, QuestionService questionService,
            QuestionImportHistoryService history, IdempotentCommand idempotent, CurrentUserProvider users,
            UserDirectory directory, AuditRecorder audit, ObjectStorage storage, Clock clock, EntityManager entityManager) {
        this.uploads = uploads;
        this.imports = imports;
        this.questions = questions;
        this.validation = validation;
        this.files = files;
        this.lookup = lookup;
        this.mapper = mapper;
        this.questionService = questionService;
        this.history = history;
        this.idempotent = idempotent;
        this.users = users;
        this.directory = directory;
        this.audit = audit;
        this.storage = storage;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public QuestionImportHistoryEntry commit(UUID id, String key, Request request) {
        return idempotent.execute(key, "question.import.commit", id, request, QuestionImportHistoryEntry.class,
                () -> execute(id, request));
    }

    private QuestionImportHistoryEntry execute(UUID id, Request request) {
        long started = System.nanoTime();
        if (request == null || request.duplicatePolicy() == null
                || (request.uploadId() != null && !id.equals(request.uploadId()))) {
            throw new BusinessRuleException("IMPORT_MAPPING_INVALID");
        }
        QuestionImportUpload initial = validation.requireUpload(id);
        byte[] bytes = storage.get(initial.getObjectKey()).orElse(null);
        // The lock query must hydrate a fresh version after waiting for another commit.
        entityManager.detach(initial);
        QuestionImportUpload upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("IMPORT_SOURCE_GONE", null));
        if (bytes == null && upload.getStatus() == QuestionImportUpload.Status.UPLOADED) {
            throw new NotFoundException("IMPORT_SOURCE_GONE", null);
        }
        if (upload.getStatus() != QuestionImportUpload.Status.UPLOADED) {
            throw new ConflictException("IMPORT_ALREADY_COMMITTED");
        }
        if (!upload.getExpiresAt().isAfter(clock.instant())) {
            throw new NotFoundException("IMPORT_SOURCE_GONE", null);
        }
        upload.markCommitting();
        ColumnMapping mapping = new ColumnMapping(request.mapping());
        QuestionImportValidationService.Checked checked;
        vn.unicom.thi360.questionbank.domain.importing.ImportSheet sheet;
        try {
            sheet = files.read(upload.getFileName(), bytes);
            checked = validation.check(id, sheet, mapping);
        } catch (ImportFileException ex) {
            throw new BusinessRuleException(ex.code().name());
        }
        var validationResult = checked.validation();
        if (validationResult.summary().error() > 0 && !request.skipErrors()) {
            throw new BusinessRuleException("IMPORT_HAS_ERRORS");
        }
        List<UUID> targetIds = validationResult.rows().stream()
                .filter(row -> row.parentRowNumber() == null && row.duplicateOf() != null)
                .map(row -> row.duplicateOf().id()).distinct().sorted().toList();
        Map<UUID, Question> locked = new HashMap<>();
        if (!targetIds.isEmpty()) {
            questions.findAllByIdInForUpdate(targetIds).forEach(q -> locked.put(q.getId(), q));
        }
        var claimsAfterLock = lookup.existing(sheet, mapping);
        Map<String, UUID> before = new HashMap<>();
        checked.existing().forEach(ref -> before.put(ref.sourceCodeNormalized(), ref.id()));
        Map<String, UUID> after = new HashMap<>();
        claimsAfterLock.forEach(ref -> after.put(ref.sourceCodeNormalized(), ref.id()));
        if (!before.equals(after)) {
            throw new ConflictException("QUESTION_SOURCE_CODE_DUPLICATE");
        }
        int singles = 0, sets = 0, children = 0, skipped = 0, versions = 0, missingKnowledge = 0;
        for (var row : validationResult.rows()) {
            if (row.parentRowNumber() != null || row.status() == ImportRowStatus.ERROR || row.draft() == null) {
                continue;
            }
            if (row.status() == ImportRowStatus.DUPLICATE && request.duplicatePolicy() == DuplicatePolicy.SKIP) {
                skipped++;
                continue;
            }
            var mapped = mapper.map(row.draft(), checked.catalog());
            Question target = row.duplicateOf() == null ? null : locked.get(row.duplicateOf().id());
            if (row.duplicateOf() != null && target == null) {
                throw new ConflictException("QUESTION_SOURCE_CODE_DUPLICATE");
            }
            questionService.saveImported(target, mapped.request(), mapped.classification());
            if (target != null) {
                versions++;
            } else {
                if (row.isSetPassage()) {
                    sets++;
                    children += row.draft().children().size();
                } else {
                    singles++;
                }
                if (row.draft().knowledge().isEmpty()) {
                    missingKnowledge++;
                }
            }
        }
        List<QuestionImportErrorLine> errors = new ArrayList<>();
        for (var row : validationResult.rows()) {
            for (var issue : row.issues()) {
                if ("error".equals(issue.severity())) {
                    errors.add(new QuestionImportErrorLine(row.rowNumber(), row.sourceCode(),
                            new QuestionImportErrorLine.Issue(issue.code(), issue.severity(), issue.field(), issue.column(),
                                    issue.value(), issue.relatedRow(), issue.questionType(), issue.subjectCode())));
                }
            }
        }
        UUID actor = users.requireCurrentUser().id();
        Instant now = clock.instant();
        long duration = (System.nanoTime() - started) / 1_000_000;
        QuestionImport saved = imports.saveAndFlush(new QuestionImport(UUID.randomUUID(), upload.getFileName(),
                singles + sets, singles, sets, children, skipped, versions, validationResult.summary().error(),
                missingKnowledge, errors, duration, actor, now));
        upload.markCommitted(saved.getId(), now);
        uploads.saveAndFlush(upload);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("createdCount", singles + sets);
        counts.put("singleCount", singles);
        counts.put("setCount", sets);
        counts.put("setChildCount", children);
        counts.put("skippedDuplicateCount", skipped);
        counts.put("newVersionCount", versions);
        counts.put("errorRowCount", validationResult.summary().error());
        counts.put("missingKnowledgeCount", missingKnowledge);
        audit.record(AuditEvent.builder("QUESTION", "QUESTION.IMPORT")
                .target("QUESTION_IMPORT", saved.getId().toString(), null).after(counts).build());
        String objectKey = upload.getObjectKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    storage.delete(objectKey);
                } catch (RuntimeException ex) {
                    log.warn("Could not delete committed import source object {}", objectKey, ex);
                }
            }
        });
        return history.toEntry(saved, directory.findById(actor).orElse(null));
    }
}
