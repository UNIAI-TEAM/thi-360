package vn.unicom.thi360.identity.application;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.ImportBatchResponse;
import vn.unicom.thi360.identity.api.ImportRowResponse;
import vn.unicom.thi360.identity.application.ImportRowValidationService.ValidatedRow;
import vn.unicom.thi360.identity.application.ImportWorkbookCodec.ParsedImportRow;
import vn.unicom.thi360.identity.domain.ImportBatch;
import vn.unicom.thi360.identity.domain.ImportBatchStatus;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.ImportRowRaw;
import vn.unicom.thi360.identity.domain.ImportRowStatus;
import vn.unicom.thi360.identity.infrastructure.ImportBatchRepository;
import vn.unicom.thi360.identity.infrastructure.ImportRowRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.idempotency.IdempotencyService;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.storage.EncryptedObjectStorage;

@Service
public class ImportBatchService {

    public static final java.util.Set<String> ALLOWED_ROW_SORT_FIELDS = java.util.Set.of("rowNo", "status");

    private static final String MODULE = "IDENTITY";
    private static final String TARGET_TYPE = "IMPORT_BATCH";
    private static final String CONFIRM_IDEMPOTENCY_SCOPE = "POST:/api/v1/admin/import-batches/confirm";

    private final ImportBatchRepository importBatchRepository;
    private final ImportRowRepository importRowRepository;
    private final ImportWorkbookCodec workbookCodec;
    private final ImportRowValidationService validationService;
    private final ImportExcelSchemaHelper schemaHelper;
    private final EncryptedObjectStorage encryptedObjectStorage;
    private final IdempotencyService idempotencyService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public ImportBatchService(
            ImportBatchRepository importBatchRepository,
            ImportRowRepository importRowRepository,
            ImportWorkbookCodec workbookCodec,
            ImportRowValidationService validationService,
            ImportExcelSchemaHelper schemaHelper,
            EncryptedObjectStorage encryptedObjectStorage,
            IdempotencyService idempotencyService,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.importBatchRepository = importBatchRepository;
        this.importRowRepository = importRowRepository;
        this.workbookCodec = workbookCodec;
        this.validationService = validationService;
        this.schemaHelper = schemaHelper;
        this.encryptedObjectStorage = encryptedObjectStorage;
        this.idempotencyService = idempotencyService;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public byte[] buildTemplate(Locale locale) {
        return workbookCodec.buildTemplateWorkbook(locale);
    }

    public ImportBatchResponse uploadBatch(MultipartFile file, boolean mustChangePassword, Locale locale) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        if (file == null || file.isEmpty()) {
            String msg = schemaHelper.message("IMPORT_EMPTY_FILE", "Tệp Excel trống", locale);
            throw new ValidationException("IMPORT_EMPTY_FILE", msg, List.of(new FieldError("file", "IMPORT_EMPTY_FILE", msg)));
        }
        if (file.getSize() > ImportWorkbookCodec.MAX_FILE_BYTES) {
            String msg = schemaHelper.message(
                    "IMPORT_FILE_TOO_LARGE",
                    "Dung lượng tệp Excel vượt quá giới hạn cho phép (tối đa 5 MB)",
                    locale);
            throw new ValidationException("IMPORT_FILE_TOO_LARGE", msg, List.of(new FieldError("file", "IMPORT_FILE_TOO_LARGE", msg)));
        }
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        if (originalFilename != null && originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsm")) {
            String msg = schemaHelper.message("IMPORT_MACRO_NOT_ALLOWED", "Tệp Excel không được chứa macro", locale);
            throw new ValidationException("IMPORT_MACRO_NOT_ALLOWED", msg, List.of(new FieldError("file", "IMPORT_MACRO_NOT_ALLOWED", msg)));
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            String msg = schemaHelper.message("IMPORT_INVALID_FILE", "Không thể đọc tệp Excel", locale);
            throw new ValidationException("IMPORT_INVALID_FILE", msg, List.of(new FieldError("file", "IMPORT_INVALID_FILE", msg)));
        }

        List<ParsedImportRow> parsedRows = workbookCodec.parseUploadedWorkbook(fileBytes, locale);

        boolean hasAnyClassCode = parsedRows.stream()
                .anyMatch(r -> r.classCode() != null && !r.classCode().isBlank());
        if (hasAnyClassCode && !hasClassModifyPermission(actor)) {
            throw new ForbiddenException("IMPORT_CLASS_PERMISSION_REQUIRED", null);
        }

        List<ValidatedRow> validatedRows = validationService.validateParsedRows(parsedRows, locale);
        int totalRows = validatedRows.size();
        int invalidRows = (int) validatedRows.stream().filter(r -> !r.errors().isEmpty()).count();
        int validRows = totalRows - invalidRows;
        boolean hasGeneratedPasswords = parsedRows.stream()
                .anyMatch(r -> r.password() == null || r.password().isBlank());

        UUID batchId = Ids.newId();
        String sourceKey = sourceObjectKey(batchId);
        encryptedObjectStorage.putEncrypted(sourceKey, fileBytes);

        try {
            return transactionTemplate.execute(status -> {
                Instant now = Instant.now(clock);
                ImportBatch batch = new ImportBatch(
                        batchId,
                        sourceKey,
                        originalFilename,
                        mustChangePassword,
                        hasGeneratedPasswords,
                        actor.id(),
                        actor.username(),
                        now);
                batch.markValidated(totalRows, validRows, invalidRows, now);
                importBatchRepository.save(batch);

                List<ImportRow> rowEntities = new ArrayList<>(validatedRows.size());
                for (ValidatedRow vr : validatedRows) {
                    ParsedImportRow parsed = vr.parsed();
                    boolean passwordGenerated = parsed.password() == null || parsed.password().isBlank();
                    String encryptedPassword = passwordGenerated
                            ? null
                            : encryptedObjectStorage.encryptText(parsed.password());
                    ImportRowRaw raw = new ImportRowRaw(
                            parsed.username(),
                            encryptedPassword,
                            parsed.fullName(),
                            parsed.email(),
                            parsed.phone(),
                            parsed.classCode());
                    rowEntities.add(new ImportRow(
                            Ids.newId(),
                            batchId,
                            parsed.rowNo(),
                            raw,
                            vr.usernameNormalized(),
                            vr.emailNormalized(),
                            vr.classCodeNormalized(),
                            vr.resolvedClassId(),
                            passwordGenerated,
                            vr.errors()));
                }
                importRowRepository.saveAll(rowEntities);
                importBatchRepository.flush();
                importRowRepository.flush();

                Map<String, Object> after = new LinkedHashMap<>();
                after.put("totalRows", totalRows);
                after.put("validRows", validRows);
                after.put("invalidRows", invalidRows);
                after.put("mustChangePassword", mustChangePassword);
                recordAudit(actor, "IMPORT_BATCH_UPLOADED", batchId, originalFilename, after);

                return toBatchResponse(batch);
            });
        } catch (RuntimeException ex) {
            try {
                encryptedObjectStorage.delete(sourceKey);
            } catch (RuntimeException ignored) {
                // Best-effort storage cleanup on DB rollback
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ImportBatchResponse getBatch(UUID batchId) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return toBatchResponse(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportRowResponse> listBatchRows(
            UUID batchId, ImportRowStatus status, PageQuery pageQuery) {
        if (!importBatchRepository.existsById(batchId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        PageRequest pageable = PageRequest.of(
                pageQuery.page(),
                pageQuery.size(),
                pageQuery.sort() == null || pageQuery.sort().isUnsorted()
                        ? Sort.by(Sort.Direction.ASC, "rowNo")
                        : pageQuery.sort());
        Page<ImportRow> page = status == null
                ? importRowRepository.findByBatchId(batchId, pageable)
                : importRowRepository.findByBatchIdAndStatus(batchId, status, pageable);

        List<ImportRowResponse> items = page.getContent().stream()
                .map(ImportBatchService::toRowResponse)
                .toList();
        return PageResponse.from(new PageImpl<>(items, pageable, page.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public byte[] downloadErrorsWorkbook(UUID batchId, Locale locale) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (batch.getStatus().isTerminal()
                || batch.getFileObjectKey() == null
                || batch.getSourceDeletedAt() != null) {
            throw new ConflictException("IMPORT_SOURCE_GONE");
        }
        byte[] originalBytes = encryptedObjectStorage.getDecrypted(batch.getFileObjectKey())
                .orElseThrow(() -> new ConflictException("IMPORT_SOURCE_GONE"));
        List<ImportRow> invalidRows = importRowRepository.findByBatchIdAndStatusOrderByRowNoAsc(
                batchId, ImportRowStatus.INVALID);
        return workbookCodec.buildErrorsWorkbook(originalBytes, invalidRows, locale);
    }

    public ImportBatchResponse confirmBatch(UUID batchId, String idempotencyKey, Locale locale) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException(List.of(
                    new FieldError("Idempotency-Key", "REQUIRED", "Khóa Idempotency-Key là bắt buộc")));
        }
        String trimmedKey = idempotencyKey.trim();
        if (!importBatchRepository.existsById(batchId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        boolean hasClassCode = importRowRepository.existsByBatchIdAndClassCodeNormalizedIsNotNull(batchId);
        if (hasClassCode && !hasClassModifyPermission(actor)) {
            throw new ForbiddenException("IMPORT_CLASS_PERMISSION_REQUIRED", null);
        }

        try {
            return idempotencyService.executeOnce(
                    trimmedKey,
                    CONFIRM_IDEMPOTENCY_SCOPE,
                    batchId.toString(),
                    ImportBatchResponse.class,
                    () -> confirmBatchInsideIdempotency(batchId, trimmedKey, actor, locale));
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyKeyConstraintViolation(ex)) {
                throw new ConflictException(IdempotencyService.ERROR_IDEMPOTENCY_KEY_REUSED);
            }
            throw ex;
        }
    }

    private ImportBatchResponse confirmBatchInsideIdempotency(
            UUID batchId, String idempotencyKey, CurrentUser actor, Locale locale) {
        Integer invalidCount = requiresNewTx.execute(txStatus -> {
            ImportBatch batchInTx = importBatchRepository.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            if (batchInTx.getStatus() != ImportBatchStatus.VALIDATED) {
                throw new ConflictException("IMPORT_ALREADY_CONFIRMED");
            }
            List<ImportRow> rows = importRowRepository.findByBatchIdOrderByRowNoAsc(batchId);
            if (rows.isEmpty()) {
                throw new ConflictException("IMPORT_HAS_ERRORS");
            }
            int newlyInvalid = validationService.revalidatePersistedRows(rows, locale);
            int valid = rows.size() - newlyInvalid;
            batchInTx.updateValidationCounts(valid, newlyInvalid, Instant.now(clock));
            importRowRepository.saveAll(rows);
            importBatchRepository.saveAndFlush(batchInTx);
            return newlyInvalid;
        });

        if (invalidCount == null || invalidCount > 0) {
            throw new ConflictException("IMPORT_HAS_ERRORS");
        }

        ImportBatch refreshed = importBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (refreshed.getStatus() != ImportBatchStatus.VALIDATED || refreshed.getInvalidRows() > 0) {
            throw new ConflictException("IMPORT_HAS_ERRORS");
        }
        Instant now = Instant.now(clock);
        refreshed.markConfirmed(idempotencyKey, actor.id(), actor.username(), now);
        importBatchRepository.saveAndFlush(refreshed);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", refreshed.getStatus().name());
        after.put("totalRows", refreshed.getTotalRows());
        after.put("mustChangePassword", refreshed.isMustChangePassword());
        recordAudit(actor, "IMPORT_BATCH_CONFIRMED", batchId, refreshed.getOriginalFilename(), after);

        return toBatchResponse(refreshed);
    }

    @Transactional
    public byte[] downloadResultWorkbook(UUID batchId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (batch.getResultDeletedAt() != null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        if (batch.getResultObjectKey() == null) {
            if (!batch.getStatus().isTerminal()) {
                throw new ConflictException("IMPORT_RESULT_NOT_READY");
            }
            throw new NotFoundException("NOT_FOUND", null);
        }
        byte[] resultBytes = encryptedObjectStorage.getDecrypted(batch.getResultObjectKey())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", batch.getStatus().name());
        after.put("createdRows", batch.getCreatedRows());
        after.put("failedRows", batch.getFailedRows());
        recordAudit(actor, "IMPORT_RESULT_DOWNLOADED", batchId, batch.getOriginalFilename(), after);

        return resultBytes;
    }

    @Transactional
    public void deleteResultWorkbook(UUID batchId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ImportBatch batch = importBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (batch.getResultObjectKey() != null && batch.getResultDeletedAt() == null) {
            String keyToDelete = batch.getResultObjectKey();
            batch.markResultDeleted(actor.id(), Instant.now(clock));
            importBatchRepository.saveAndFlush(batch);
            encryptedObjectStorage.delete(keyToDelete);

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("resultDeletedAt", batch.getResultDeletedAt().toString());
            recordAudit(actor, "IMPORT_RESULT_DELETED", batchId, batch.getOriginalFilename(), after);
        }
    }

    public static String sourceObjectKey(UUID batchId) {
        return "import-batches/" + batchId + "/source.xlsx";
    }

    public static String resultObjectKey(UUID batchId) {
        return "import-batches/" + batchId + "/result.xlsx";
    }

    private void recordAudit(
            CurrentUser actor,
            String action,
            UUID batchId,
            String label,
            Map<String, Object> after) {
        AuditActorType actorType = actor.realm() == Realm.MASTER ? AuditActorType.PLATFORM : AuditActorType.USER;
        auditRecorder.record(AuditEvent.builder(MODULE, action)
                .target(TARGET_TYPE, batchId.toString(), label != null ? label : batchId.toString())
                .after(after)
                .actor(actor.id(), actor.username(), null, actorType)
                .build());
    }

    private static boolean hasClassModifyPermission(CurrentUser actor) {
        if (actor.realm() == Realm.MASTER || actor.permissions().isAllGranted()) {
            return true;
        }
        return actor.permissions().hasCode(Permissions.CLASS_MODIFY);
    }

    private static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return null;
        }
        String name = rawFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    static ImportBatchResponse toBatchResponse(ImportBatch batch) {
        boolean sourceAvailable = !batch.getStatus().isTerminal()
                && batch.getFileObjectKey() != null
                && batch.getSourceDeletedAt() == null;
        boolean resultAvailable = batch.getResultObjectKey() != null && batch.getResultDeletedAt() == null;
        boolean canConfirm = batch.getStatus() == ImportBatchStatus.VALIDATED
                && batch.getInvalidRows() == 0
                && batch.getTotalRows() > 0;
        return new ImportBatchResponse(
                batch.getId(),
                batch.getStatus(),
                batch.getOriginalFilename(),
                batch.isMustChangePassword(),
                batch.getTotalRows(),
                batch.getValidRows(),
                batch.getInvalidRows(),
                batch.getCreatedRows(),
                batch.getFailedRows(),
                batch.getInvalidRows() > 0,
                canConfirm,
                batch.isHasGeneratedPasswords(),
                sourceAvailable,
                resultAvailable,
                batch.getConfirmedAt(),
                batch.getStartedAt(),
                batch.getCompletedAt(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getVersion());
    }

    private static ImportRowResponse toRowResponse(ImportRow row) {
        ImportRowRaw raw = row.getRaw();
        return new ImportRowResponse(
                row.getId(),
                row.getRowNo(),
                raw.username(),
                raw.fullName(),
                raw.email(),
                raw.phone(),
                raw.classCode(),
                row.isPasswordGenerated(),
                row.getStatus(),
                row.getErrors(),
                row.getCreatedUserId(),
                row.getFailureCode(),
                row.getFailureReason(),
                row.getProcessedAt());
    }
    private static boolean isIdempotencyKeyConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String msg = cursor.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("uq_import_batch_idempotency_key")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
