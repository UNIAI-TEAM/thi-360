package vn.unicom.thi360.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.CreateUserRequest;
import vn.unicom.thi360.identity.api.UserDetailResponse;
import vn.unicom.thi360.identity.domain.ImportBatch;
import vn.unicom.thi360.identity.domain.ImportBatchStatus;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.ImportRowRaw;
import vn.unicom.thi360.identity.domain.ImportRowStatus;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PasswordGenerator;
import vn.unicom.thi360.identity.infrastructure.ImportBatchRepository;
import vn.unicom.thi360.identity.infrastructure.ImportRowRepository;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.storage.EncryptedObjectStorage;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Component
@Profile("worker")
public class ImportBatchProcessorJob {

    public static final String JOB_NAME = "bulk-import";
    private static final String MODULE = "IDENTITY";
    private static final String TARGET_TYPE = "IMPORT_BATCH";

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    private final ImportBatchRepository importBatchRepository;
    private final ImportRowRepository importRowRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final UserAdminService userAdminService;
    private final PasswordGenerator passwordGenerator;
    private final ImportWorkbookCodec workbookCodec;
    private final ImportExcelSchemaHelper schemaHelper;
    private final EncryptedObjectStorage encryptedObjectStorage;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public ImportBatchProcessorJob(
            ImportBatchRepository importBatchRepository,
            ImportRowRepository importRowRepository,
            PlatformAdminRepository platformAdminRepository,
            UserAdminService userAdminService,
            PasswordGenerator passwordGenerator,
            ImportWorkbookCodec workbookCodec,
            ImportExcelSchemaHelper schemaHelper,
            EncryptedObjectStorage encryptedObjectStorage,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.importBatchRepository = importBatchRepository;
        this.importRowRepository = importRowRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.userAdminService = userAdminService;
        this.passwordGenerator = passwordGenerator;
        this.workbookCodec = workbookCodec;
        this.schemaHelper = schemaHelper;
        this.encryptedObjectStorage = encryptedObjectStorage;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public Optional<UUID> processNextConfirmedBatch() {
        UUID claimedBatchId = claimNextConfirmedBatch();
        if (claimedBatchId == null) {
            return Optional.empty();
        }
        processBatch(claimedBatchId);
        return Optional.of(claimedBatchId);
    }

    public ImportBatchStatus processBatch(UUID batchId) {
        String prevTraceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String prevJob = MDC.get("job");
        String prevJobId = MDC.get("jobId");

        if (prevTraceId == null || prevTraceId.isBlank()) {
            MDC.put(TraceIdFilter.MDC_TRACE_ID, Ids.newId().toString());
        }
        MDC.put("job", JOB_NAME);
        MDC.put("jobId", batchId.toString());

        try {
            return processBatchInternal(batchId);
        } finally {
            restoreMdc(TraceIdFilter.MDC_TRACE_ID, prevTraceId);
            restoreMdc("job", prevJob);
            restoreMdc("jobId", prevJobId);
        }
    }

    private UUID claimNextConfirmedBatch() {
        return requiresNewTx.execute(status -> {
            List<UUID> ids = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM identity.import_batch
                    WHERE status = ?
                    ORDER BY confirmed_at ASC NULLS LAST, created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """,
                    (rs, rowNum) -> rs.getObject("id", UUID.class),
                    ImportBatchStatus.CONFIRMED.code());
            if (ids.isEmpty()) {
                return null;
            }
            UUID batchId = ids.getFirst();
            Instant now = Instant.now(clock);
            jdbcTemplate.update(
                    """
                    UPDATE identity.import_batch
                    SET status = ?,
                        started_at = COALESCE(started_at, ?),
                        updated_at = ?,
                        version = version + 1
                    WHERE id = ?
                    """,
                    ImportBatchStatus.PROCESSING.code(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    batchId);
            return batchId;
        });
    }

    private ImportBatchStatus processBatchInternal(UUID batchId) {
        ImportBatch batch = requiresNewTx.execute(status -> {
            ImportBatch b = importBatchRepository.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            if (b.getStatus() == ImportBatchStatus.CONFIRMED
                    || b.getStatus() == ImportBatchStatus.COMPLETED_WITH_ERRORS) {
                b.markProcessing(Instant.now(clock));
                importBatchRepository.saveAndFlush(b);
            }
            return b;
        });
        if (batch == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        if (batch.getStatus() == ImportBatchStatus.COMPLETED) {
            return ImportBatchStatus.COMPLETED;
        }

        UUID actorId = batch.getConfirmedBy() != null ? batch.getConfirmedBy() : batch.getCreatedBy();
        String actorUsername = batch.getCreatedByUsername();
        boolean platformActor = actorId != null && platformAdminRepository.existsById(actorId);

        Map<String, String> existingGeneratedByUsername = loadPreviouslyGeneratedPasswords(batch);

        List<ImportRow> unfinishedRows = importRowRepository.findByBatchIdAndStatusInOrderByRowNoAsc(
                batchId, List.of(ImportRowStatus.VALID, ImportRowStatus.FAILED));

        jobLog.atInfo()
                .addKeyValue("batchId", batchId.toString())
                .addKeyValue("unfinishedCount", unfinishedRows.size())
                .addKeyValue("totalRows", batch.getTotalRows())
                .log("Starting bulk import batch processing");

        Map<Integer, String> generatedPasswordsByRowNo = new HashMap<>();
        for (ImportRow row : unfinishedRows) {
            String rawPassword = resolveRowPassword(row);
            try {
                ImportRowRaw raw = row.getRaw();
                List<UUID> classIds = row.getResolvedClassId() == null
                        ? null
                        : List.of(row.getResolvedClassId());
                CreateUserRequest createReq = new CreateUserRequest(
                        raw.username(),
                        rawPassword,
                        raw.fullName(),
                        raw.email(),
                        raw.phone(),
                        null,
                        classIds,
                        batch.isMustChangePassword());

                UserDetailResponse created = userAdminService.createStudentForImport(
                        createReq, actorId, actorUsername, platformActor);

                Instant now = Instant.now(clock);
                requiresNewTx.executeWithoutResult(tx -> {
                    ImportRow lockedRow = importRowRepository.findById(row.getId())
                            .orElseThrow();
                    lockedRow.markCreated(created.id(), now);
                    importRowRepository.saveAndFlush(lockedRow);
                });

                if (row.isPasswordGenerated()) {
                    generatedPasswordsByRowNo.put(row.getRowNo(), rawPassword);
                }
            } catch (RuntimeException ex) {
                FailureInfo failure = classifyFailure(ex);
                boolean retryable = ex instanceof ExternalServiceException;
                Instant now = Instant.now(clock);
                requiresNewTx.executeWithoutResult(tx -> {
                    ImportRow lockedRow = importRowRepository.findById(row.getId())
                            .orElseThrow();
                    lockedRow.markFailed(failure.code(), failure.reason(), !retryable, now);
                    importRowRepository.saveAndFlush(lockedRow);
                });
                jobLog.atWarn()
                        .addKeyValue("batchId", batchId.toString())
                        .addKeyValue("rowNo", row.getRowNo())
                        .addKeyValue("errorCode", failure.code())
                        .addKeyValue("retryable", retryable)
                        .log("Bulk import row failed");
            }
        }

        List<ImportRow> allRows = importRowRepository.findByBatchIdOrderByRowNoAsc(batchId);
        int createdCount = 0;
        int failedCount = 0;
        for (ImportRow row : allRows) {
            if (row.getStatus() == ImportRowStatus.CREATED) {
                createdCount++;
                if (row.isPasswordGenerated() && !generatedPasswordsByRowNo.containsKey(row.getRowNo())) {
                    String prevPwd = existingGeneratedByUsername.get(Normalizer.normalize(row.getRaw().username()));
                    if (prevPwd != null) {
                        generatedPasswordsByRowNo.put(row.getRowNo(), prevPwd);
                    }
                }
            } else if (row.getStatus() == ImportRowStatus.FAILED || row.getStatus() == ImportRowStatus.INVALID) {
                failedCount++;
            }
        }

        Locale locale = ImportExcelSchemaHelper.DEFAULT_LOCALE;
        byte[] resultBytes = workbookCodec.buildResultWorkbook(allRows, generatedPasswordsByRowNo, locale);
        String resultKey = ImportBatchService.resultObjectKey(batchId);
        encryptedObjectStorage.putEncrypted(resultKey, resultBytes);

        int finalCreatedCount = createdCount;
        int finalFailedCount = failedCount;
        ImportBatchStatus finalStatus = requiresNewTx.execute(tx -> {
            ImportBatch finalBatch = importBatchRepository.findByIdForUpdate(batchId)
                    .orElseThrow();
            String sourceKey = finalBatch.getFileObjectKey();
            Instant now = Instant.now(clock);
            finalBatch.markFinished(resultKey, finalCreatedCount, finalFailedCount, now);
            if (sourceKey != null) {
                finalBatch.markSourceDeleted(now);
            }
            importBatchRepository.saveAndFlush(finalBatch);

            if (sourceKey != null) {
                encryptedObjectStorage.delete(sourceKey);
            }

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", finalBatch.getStatus().name());
            after.put("createdRows", finalCreatedCount);
            after.put("failedRows", finalFailedCount);
            AuditActorType actorType = platformActor ? AuditActorType.PLATFORM : AuditActorType.USER;
            auditRecorder.record(AuditEvent.builder(MODULE,
                            finalBatch.getStatus() == ImportBatchStatus.COMPLETED
                                    ? "IMPORT_BATCH_COMPLETED"
                                    : "IMPORT_BATCH_COMPLETED_WITH_ERRORS")
                    .target(TARGET_TYPE, batchId.toString(),
                            finalBatch.getOriginalFilename() != null
                                    ? finalBatch.getOriginalFilename()
                                    : batchId.toString())
                    .after(after)
                    .actor(actorId, actorUsername, null, actorType)
                    .build());

            return finalBatch.getStatus();
        });

        jobLog.atInfo()
                .addKeyValue("batchId", batchId.toString())
                .addKeyValue("status", finalStatus != null ? finalStatus.name() : "UNKNOWN")
                .addKeyValue("createdRows", createdCount)
                .addKeyValue("failedRows", failedCount)
                .log("Completed bulk import batch processing");

        return finalStatus;
    }

    private Map<String, String> loadPreviouslyGeneratedPasswords(ImportBatch batch) {
        if (batch.getResultObjectKey() == null || batch.getResultDeletedAt() != null) {
            return Map.of();
        }
        Optional<byte[]> existing = encryptedObjectStorage.getDecrypted(batch.getResultObjectKey());
        if (existing.isEmpty()) {
            return Map.of();
        }
        return workbookCodec.extractGeneratedPasswordsByNormalizedUsername(existing.get());
    }

    private String resolveRowPassword(ImportRow row) {
        if (row.isPasswordGenerated() || row.getRaw().passwordEnc() == null) {
            return passwordGenerator.generate(row.getRaw().username());
        }
        return encryptedObjectStorage.decryptText(row.getRaw().passwordEnc());
    }

    private FailureInfo classifyFailure(RuntimeException ex) {
        Locale locale = ImportExcelSchemaHelper.DEFAULT_LOCALE;
        if (ex instanceof ExternalServiceException ese) {
            String msg = schemaHelper.message(
                    "IMPORT_WORKER.IDENTITY_PROVIDER_ERROR",
                    "Lỗi kết nối dịch vụ xác thực, có thể thử chạy lại",
                    locale);
            return new FailureInfo(ese.getCode(), msg);
        }
        if (ex instanceof ConflictException ce) {
            String msg = schemaHelper.message(ce.getCode(), ce.getCode(), locale);
            return new FailureInfo(ce.getCode(), msg);
        }
        if (ex instanceof ValidationException ve) {
            String msg = schemaHelper.message(ve.getCode(), ve.getCode(), locale);
            return new FailureInfo(ve.getCode(), msg);
        }
        if (ex instanceof NotFoundException) {
            String msg = schemaHelper.rowErrorMessage("CLASS_NOT_FOUND", "Mã lớp", locale);
            return new FailureInfo("CLASS_NOT_FOUND", msg);
        }
        String fallback = schemaHelper.message(
                "IMPORT_WORKER.UNEXPECTED_ERROR",
                "Lỗi kỹ thuật khi tạo tài khoản",
                locale);
        return new FailureInfo("INTERNAL_ERROR", fallback);
    }

    private static void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

    private record FailureInfo(String code, String reason) {
    }

    @Component
    @Profile("worker")
    @ConditionalOnProperty(
            name = "thi360.identity.import-batch.scheduling.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public static class ImportBatchProcessorWorker {

        private static final int MAX_BATCHES_PER_TICK = 10;
        private final ImportBatchProcessorJob processorJob;

        public ImportBatchProcessorWorker(ImportBatchProcessorJob processorJob) {
            this.processorJob = processorJob;
        }

        @Scheduled(
                fixedDelayString = "${thi360.identity.import-batch.poll-interval:PT5S}",
                initialDelayString = "${thi360.identity.import-batch.initial-delay:PT10S}")
        public void runScheduledImportBatches() {
            for (int i = 0; i < MAX_BATCHES_PER_TICK; i++) {
                if (processorJob.processNextConfirmedBatch().isEmpty()) {
                    break;
                }
            }
        }
    }
}
