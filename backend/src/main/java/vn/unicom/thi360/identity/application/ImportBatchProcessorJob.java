package vn.unicom.thi360.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Value;
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
import vn.unicom.thi360.identity.domain.PlatformAdmin;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.infrastructure.ImportBatchRepository;
import vn.unicom.thi360.identity.infrastructure.ImportRowRepository;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
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
    private final UserAccountRepository userAccountRepository;
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
    private final Duration leaseDuration;
    private final Duration retryDelay;
    private final int maxAttempts;

    public ImportBatchProcessorJob(
            ImportBatchRepository importBatchRepository,
            ImportRowRepository importRowRepository,
            UserAccountRepository userAccountRepository,
            PlatformAdminRepository platformAdminRepository,
            UserAdminService userAdminService,
            PasswordGenerator passwordGenerator,
            ImportWorkbookCodec workbookCodec,
            ImportExcelSchemaHelper schemaHelper,
            EncryptedObjectStorage encryptedObjectStorage,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${thi360.identity.import-batch.lease-duration:PT5M}") Duration leaseDuration,
            @Value("${thi360.identity.import-batch.retry-delay:PT5S}") Duration retryDelay,
            @Value("${thi360.identity.import-batch.max-attempts:3}") int maxAttempts) {
        this.importBatchRepository = importBatchRepository;
        this.importRowRepository = importRowRepository;
        this.userAccountRepository = userAccountRepository;
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
        this.leaseDuration = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofMinutes(5)
                : leaseDuration;
        this.retryDelay = retryDelay == null || retryDelay.isNegative() ? Duration.ofSeconds(5) : retryDelay;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public Optional<UUID> processNextConfirmedBatch() {
        ClaimedBatch claimed = claimNextConfirmedBatch();
        if (claimed == null) {
            return Optional.empty();
        }
        processBatchWithToken(claimed.batchId(), claimed.leaseToken());
        return Optional.of(claimed.batchId());
    }

    public ImportBatchStatus processBatch(UUID batchId) {
        ClaimedBatchOrStatus claimed = requiresNewTx.execute(status -> {
            ImportBatch b = importBatchRepository.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            if (b.getStatus().isTerminal()) {
                return new ClaimedBatchOrStatus(b.getStatus(), null);
            }
            if (b.getStatus() != ImportBatchStatus.CONFIRMED
                    && b.getStatus() != ImportBatchStatus.PROCESSING) {
                throw new ConflictException("IMPORT_NOT_CONFIRMED");
            }
            Instant now = Instant.now(clock);
            if (b.getStatus() == ImportBatchStatus.PROCESSING
                    && b.getLeaseExpiresAt() != null
                    && b.getLeaseExpiresAt().isAfter(now)) {
                return new ClaimedBatchOrStatus(ImportBatchStatus.PROCESSING, null);
            }
            UUID leaseToken = Ids.newId();
            b.markProcessing(leaseToken, now.plus(leaseDuration), now);
            importBatchRepository.saveAndFlush(b);
            return new ClaimedBatchOrStatus(null, leaseToken);
        });
        if (claimed == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        if (claimed.terminalOrBusyStatus() != null) {
            return claimed.terminalOrBusyStatus();
        }
        return processBatchWithToken(batchId, claimed.leaseToken());
    }

    public int sweepPendingSourceDeletions() {
        String prevTraceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String prevJob = MDC.get("job");
        if (prevTraceId == null || prevTraceId.isBlank()) {
            MDC.put(TraceIdFilter.MDC_TRACE_ID, Ids.newId().toString());
        }
        MDC.put("job", JOB_NAME);
        try {
            List<PendingSourceDeletion> pending = jdbcTemplate.query(
                    """
                    SELECT id, file_object_key
                    FROM identity.import_batch
                    WHERE status IN (?, ?, ?)
                      AND file_object_key IS NOT NULL
                      AND source_deleted_at IS NULL
                    ORDER BY completed_at ASC NULLS LAST, updated_at ASC
                    LIMIT 50
                    """,
                    (rs, rowNum) -> new PendingSourceDeletion(
                            rs.getObject("id", UUID.class),
                            rs.getString("file_object_key")),
                    ImportBatchStatus.COMPLETED.code(),
                    ImportBatchStatus.COMPLETED_WITH_ERRORS.code(),
                    ImportBatchStatus.FAILED.code());
            int deletedCount = 0;
            for (PendingSourceDeletion item : pending) {
                if (tryDeleteSourceAndCheckpoint(item.batchId(), item.sourceKey())) {
                    deletedCount++;
                }
            }
            return deletedCount;
        } finally {
            restoreMdc(TraceIdFilter.MDC_TRACE_ID, prevTraceId);
            restoreMdc("job", prevJob);
        }
    }

    private ImportBatchStatus processBatchWithToken(UUID batchId, UUID leaseToken) {
        String prevTraceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String prevJob = MDC.get("job");
        String prevJobId = MDC.get("jobId");

        if (prevTraceId == null || prevTraceId.isBlank()) {
            MDC.put(TraceIdFilter.MDC_TRACE_ID, Ids.newId().toString());
        }
        MDC.put("job", JOB_NAME);
        MDC.put("jobId", batchId.toString());

        try {
            return processBatchInternal(batchId, leaseToken);
        } catch (StaleBatchLeaseException ex) {
            jobLog.atInfo()
                    .addKeyValue("batchId", batchId.toString())
                    .addKeyValue("leaseToken", leaseToken.toString())
                    .log("Stopping stale bulk import worker after lease loss");
            return importBatchRepository.findById(batchId)
                    .map(ImportBatch::getStatus)
                    .orElse(ImportBatchStatus.PROCESSING);
        } finally {
            restoreMdc(TraceIdFilter.MDC_TRACE_ID, prevTraceId);
            restoreMdc("job", prevJob);
            restoreMdc("jobId", prevJobId);
        }
    }

    private ClaimedBatch claimNextConfirmedBatch() {
        return requiresNewTx.execute(status -> {
            Instant now = Instant.now(clock);
            Timestamp nowTs = Timestamp.from(now);
            Timestamp leaseUntilTs = Timestamp.from(now.plus(leaseDuration));
            List<UUID> ids = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM identity.import_batch
                    WHERE status = ?
                       OR (
                           status = ?
                           AND (lease_expires_at IS NULL OR lease_expires_at <= ?)
                           AND (next_retry_at IS NULL OR next_retry_at <= ?)
                       )
                    ORDER BY
                        CASE WHEN status = ? THEN 0 ELSE 1 END,
                        next_retry_at ASC NULLS FIRST,
                        confirmed_at ASC NULLS LAST,
                        created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """,
                    (rs, rowNum) -> rs.getObject("id", UUID.class),
                    ImportBatchStatus.CONFIRMED.code(),
                    ImportBatchStatus.PROCESSING.code(),
                    nowTs,
                    nowTs,
                    ImportBatchStatus.CONFIRMED.code());
            if (ids.isEmpty()) {
                return null;
            }
            UUID batchId = ids.getFirst();
            UUID leaseToken = Ids.newId();
            int updated = jdbcTemplate.update(
                    """
                    UPDATE identity.import_batch
                    SET status = ?,
                        started_at = COALESCE(started_at, ?),
                        lease_token = ?,
                        lease_expires_at = ?,
                        next_retry_at = NULL,
                        updated_at = ?,
                        version = version + 1
                    WHERE id = ?
                    """,
                    ImportBatchStatus.PROCESSING.code(),
                    nowTs,
                    leaseToken,
                    leaseUntilTs,
                    nowTs,
                    batchId);
            if (updated != 1) {
                return null;
            }
            return new ClaimedBatch(batchId, leaseToken);
        });
    }

    private ImportBatchStatus processBatchInternal(UUID batchId, UUID leaseToken) {
        ImportBatch batch = requiresNewTx.execute(status ->
                importBatchRepository.findOwnedForUpdate(batchId, leaseToken)
                        .orElseThrow(() -> new StaleBatchLeaseException(batchId, leaseToken)));
        if (batch == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        if (batch.getStatus().isTerminal()) {
            return batch.getStatus();
        }

        UUID actorId = batch.getConfirmedBy() != null ? batch.getConfirmedBy() : batch.getCreatedBy();
        String actorUsername = resolveActorUsername(batch, actorId);
        boolean platformActor = actorId != null && platformAdminRepository.existsById(actorId);

        List<ImportRow> unfinishedRows = importRowRepository.findUnfinishedRowsForProcessing(batchId, maxAttempts);

        jobLog.atInfo()
                .addKeyValue("batchId", batchId.toString())
                .addKeyValue("unfinishedCount", unfinishedRows.size())
                .addKeyValue("totalRows", batch.getTotalRows())
                .log("Starting bulk import batch processing");

        for (ImportRow row : unfinishedRows) {
            extendBatchLease(batchId, leaseToken);
            String rawPassword = resolveAndCheckpointRowPassword(row, batchId, leaseToken);
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
                        createReq, row.getId(), batchId, leaseToken, actorId, actorUsername, platformActor);

                Instant now = Instant.now(clock);
                requiresNewTx.executeWithoutResult(tx -> {
                    importBatchRepository.findOwnedForUpdate(batchId, leaseToken)
                            .orElseThrow(() -> new StaleBatchLeaseException(batchId, leaseToken));
                    ImportRow lockedRow = importRowRepository.findByIdForUpdate(row.getId())
                            .orElseThrow();
                    if (lockedRow.getStatus() == ImportRowStatus.CREATED) {
                        return;
                    }
                    lockedRow.markCreated(created.id(), now);
                    importRowRepository.saveAndFlush(lockedRow);
                });
            } catch (StaleBatchLeaseException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                FailureInfo failure = classifyFailure(ex);
                boolean retryableError = ex instanceof ExternalServiceException;
                int nextAttempt = row.getAttemptCount() + 1;
                boolean willRetry = retryableError && nextAttempt < maxAttempts;
                Instant now = Instant.now(clock);
                requiresNewTx.executeWithoutResult(tx -> {
                    importBatchRepository.findOwnedForUpdate(batchId, leaseToken)
                            .orElseThrow(() -> new StaleBatchLeaseException(batchId, leaseToken));
                    ImportRow lockedRow = importRowRepository.findByIdForUpdate(row.getId())
                            .orElseThrow();
                    if (lockedRow.getStatus() == ImportRowStatus.CREATED) {
                        throw new StaleBatchLeaseException(batchId, leaseToken);
                    }
                    lockedRow.markFailed(failure.code(), failure.reason(), willRetry, now);
                    importRowRepository.saveAndFlush(lockedRow);
                });
                if (ex instanceof ExternalServiceException || "INTERNAL_ERROR".equals(failure.code())) {
                    jobLog.atWarn()
                            .setCause(ex)
                            .addKeyValue("batchId", batchId.toString())
                            .addKeyValue("rowNo", row.getRowNo())
                            .addKeyValue("errorCode", failure.code())
                            .addKeyValue("retryable", willRetry)
                            .addKeyValue("attemptCount", nextAttempt)
                            .log("Bulk import row failed");
                } else {
                    jobLog.atWarn()
                            .addKeyValue("batchId", batchId.toString())
                            .addKeyValue("rowNo", row.getRowNo())
                            .addKeyValue("errorCode", failure.code())
                            .addKeyValue("retryable", false)
                            .addKeyValue("attemptCount", nextAttempt)
                            .log("Bulk import row failed");
                }
            }
        }
        List<ImportRow> allRows = importRowRepository.findByBatchIdOrderByRowNoAsc(batchId);
        int createdCount = 0;
        int failedCount = 0;
        int pendingRetryCount = 0;
        for (ImportRow row : allRows) {
            if (row.getStatus() == ImportRowStatus.CREATED) {
                createdCount++;
            } else if (row.getStatus() == ImportRowStatus.FAILED) {
                failedCount++;
                if (row.isRetryable() && row.getAttemptCount() < maxAttempts) {
                    pendingRetryCount++;
                }
            } else if (row.getStatus() == ImportRowStatus.INVALID) {
                failedCount++;
            }
        }

        extendBatchLease(batchId, leaseToken);
        if (pendingRetryCount > 0 && batch.getRetryCount() < maxAttempts) {
            Instant now = Instant.now(clock);
            Instant nextRetryAt = now.plus(retryDelay);
            int finalCreatedCount = createdCount;
            int finalFailedCount = failedCount;
            requiresNewTx.executeWithoutResult(tx -> {
                ImportBatch b = importBatchRepository.findOwnedForUpdate(batchId, leaseToken)
                        .orElseThrow(() -> new StaleBatchLeaseException(batchId, leaseToken));
                b.scheduleRetry(nextRetryAt, finalCreatedCount, finalFailedCount, now);
                importBatchRepository.saveAndFlush(b);
            });
            jobLog.atInfo()
                    .addKeyValue("batchId", batchId.toString())
                    .addKeyValue("pendingRetryRows", pendingRetryCount)
                    .addKeyValue("nextRetryAt", nextRetryAt.toString())
                    .log("Scheduled bulk import batch retry");
            return ImportBatchStatus.PROCESSING;
        }

        Map<String, String> existingGeneratedByUsername = loadPreviouslyGeneratedPasswords(batch);
        Map<Integer, String> generatedPasswordsByRowNo = new HashMap<>();
        for (ImportRow row : allRows) {
            if (row.getStatus() == ImportRowStatus.CREATED && row.isPasswordGenerated()) {
                String pwd = null;
                if (row.getGeneratedPasswordEnc() != null && !row.getGeneratedPasswordEnc().isBlank()) {
                    pwd = encryptedObjectStorage.decryptText(row.getGeneratedPasswordEnc());
                } else if (row.getRaw() != null && row.getRaw().username() != null) {
                    pwd = existingGeneratedByUsername.get(Normalizer.normalize(row.getRaw().username()));
                }
                if (pwd != null) {
                    generatedPasswordsByRowNo.put(row.getRowNo(), pwd);
                }
            }
        }

        Locale locale = ImportExcelSchemaHelper.DEFAULT_LOCALE;
        byte[] resultBytes = workbookCodec.buildResultWorkbook(allRows, generatedPasswordsByRowNo, locale);
        String resultKey = ImportBatchService.resultObjectKey(batchId);
        extendBatchLease(batchId, leaseToken);
        try {
            encryptedObjectStorage.putEncrypted(resultKey, resultBytes);
        } catch (RuntimeException ex) {
            if (ex instanceof StaleBatchLeaseException stale) {
                throw stale;
            }
            Instant now = Instant.now(clock);
            int finalCreatedCount = createdCount;
            int finalFailedCount = failedCount;
            Integer currentResultRetry = requiresNewTx.execute(tx -> {
                ImportBatch b = importBatchRepository.findOwnedForUpdate(batchId, leaseToken)
                        .orElseThrow(() -> new StaleBatchLeaseException(batchId, leaseToken));
                int attemptBefore = b.getResultRetryCount();
                Instant nextRetryAt = now.plus(computeResultRetryDelay(attemptBefore));
                b.scheduleResultRetry(nextRetryAt, finalCreatedCount, finalFailedCount, now);
                importBatchRepository.saveAndFlush(b);
                return attemptBefore;
            });
            jobLog.atError()
                    .setCause(ex)
                    .addKeyValue("batchId", batchId.toString())
                    .addKeyValue("resultRetryCount", currentResultRetry)
                    .log("Failed to store encrypted bulk import result workbook; scheduled retry without wiping credentials");
            return ImportBatchStatus.PROCESSING;
        }

        int finalCreatedCount = createdCount;
        int finalFailedCount = failedCount;
        String[] sourceKeyToDelete = new String[1];
        ImportBatchStatus finalStatus = requiresNewTx.execute(tx -> {
            ImportBatch finalBatch = importBatchRepository.findOwnedForUpdate(batchId, leaseToken)
                    .orElseThrow(() -> new StaleBatchLeaseException(batchId, leaseToken));
            String sourceKey = finalBatch.getFileObjectKey();
            sourceKeyToDelete[0] = sourceKey;
            Instant now = Instant.now(clock);
            finalBatch.markFinished(resultKey, finalCreatedCount, finalFailedCount, now);
            importBatchRepository.saveAndFlush(finalBatch);
            wipeAllBatchRowSecrets(batchId);
            recordCompletionAudit(
                    finalBatch,
                    actorId,
                    actorUsername,
                    platformActor,
                    finalBatch.getStatus() == ImportBatchStatus.COMPLETED
                            ? "IMPORT_BATCH_COMPLETED"
                            : "IMPORT_BATCH_COMPLETED_WITH_ERRORS");
            return finalBatch.getStatus();
        });

        if (sourceKeyToDelete[0] != null) {
            tryDeleteSourceAndCheckpoint(batchId, sourceKeyToDelete[0]);
        }

        jobLog.atInfo()
                .addKeyValue("batchId", batchId.toString())
                .addKeyValue("status", finalStatus != null ? finalStatus.name() : "UNKNOWN")
                .addKeyValue("createdRows", createdCount)
                .addKeyValue("failedRows", failedCount)
                .log("Completed bulk import batch processing");

        return finalStatus;
    }

    private void extendBatchLease(UUID batchId, UUID leaseToken) {
        Instant now = Instant.now(clock);
        Timestamp nowTs = Timestamp.from(now);
        Timestamp leaseUntilTs = Timestamp.from(now.plus(leaseDuration));
        Integer updated = requiresNewTx.execute(tx -> jdbcTemplate.update(
                """
                UPDATE identity.import_batch
                SET lease_expires_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND status = ?
                  AND lease_token = ?
                """,
                leaseUntilTs,
                nowTs,
                batchId,
                ImportBatchStatus.PROCESSING.code(),
                leaseToken));
        if (updated == null || updated != 1) {
            throw new StaleBatchLeaseException(batchId, leaseToken);
        }
    }
    private String resolveActorUsername(ImportBatch batch, UUID actorId) {
        if (batch.getConfirmedBy() != null) {
            if (batch.getConfirmedByUsername() != null && !batch.getConfirmedByUsername().isBlank()) {
                return batch.getConfirmedByUsername();
            }
            if (actorId != null) {
                Optional<String> user = userAccountRepository.findById(actorId).map(UserAccount::getUsername);
                if (user.isPresent()) {
                    return user.get();
                }
                Optional<String> platform = platformAdminRepository.findById(actorId).map(PlatformAdmin::getUsername);
                if (platform.isPresent()) {
                    return platform.get();
                }
            }
            return null;
        }
        return batch.getCreatedByUsername();
    }

    private void wipeAllBatchRowSecrets(UUID batchId) {
        List<ImportRow> rows = importRowRepository.findByBatchIdOrderByRowNoAsc(batchId);
        for (ImportRow row : rows) {
            row.wipeSecrets();
        }
        importRowRepository.saveAllAndFlush(rows);
    }

    private void recordCompletionAudit(
            ImportBatch finalBatch,
            UUID actorId,
            String actorUsername,
            boolean platformActor,
            String action) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", finalBatch.getStatus().name());
        after.put("createdRows", finalBatch.getCreatedRows());
        after.put("failedRows", finalBatch.getFailedRows());
        AuditActorType actorType = platformActor ? AuditActorType.PLATFORM : AuditActorType.USER;
        auditRecorder.record(AuditEvent.builder(MODULE, action)
                .target(TARGET_TYPE, finalBatch.getId().toString(),
                        finalBatch.getOriginalFilename() != null
                                ? finalBatch.getOriginalFilename()
                                : finalBatch.getId().toString())
                .after(after)
                .actor(actorId, actorUsername, null, actorType)
                .build());
    }

    private boolean tryDeleteSourceAndCheckpoint(UUID batchId, String sourceKey) {
        try {
            encryptedObjectStorage.delete(sourceKey);
        } catch (RuntimeException ex) {
            jobLog.atWarn()
                    .setCause(ex)
                    .addKeyValue("batchId", batchId.toString())
                    .addKeyValue("sourceKey", sourceKey)
                    .log("Failed to delete bulk import source object after batch completion; keeping key for retry");
            return false;
        }
        Instant now = Instant.now(clock);
        Timestamp nowTs = Timestamp.from(now);
        requiresNewTx.executeWithoutResult(tx -> jdbcTemplate.update(
                """
                UPDATE identity.import_batch
                SET file_object_key = NULL,
                    source_deleted_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND file_object_key = ?
                  AND source_deleted_at IS NULL
                """,
                nowTs,
                nowTs,
                batchId,
                sourceKey));
        return true;
    }

    private Duration computeResultRetryDelay(int attemptBefore) {
        long multiplier = 1L << Math.min(Math.max(0, attemptBefore), 6);
        Duration candidate = retryDelay.multipliedBy(multiplier);
        Duration maxBackoff = Duration.ofMinutes(5);
        if (retryDelay.compareTo(maxBackoff) > 0) {
            return retryDelay;
        }
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
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

    private String resolveAndCheckpointRowPassword(ImportRow row, UUID batchId, UUID leaseToken) {
        if (row.isPasswordGenerated()) {
            if (row.getGeneratedPasswordEnc() != null && !row.getGeneratedPasswordEnc().isBlank()) {
                return encryptedObjectStorage.decryptText(row.getGeneratedPasswordEnc());
            }
            String generated = passwordGenerator.generate(row.getRaw().username());
            String enc = encryptedObjectStorage.encryptText(generated);
            requiresNewTx.executeWithoutResult(tx -> {
                importBatchRepository.findOwnedForUpdate(batchId, leaseToken)
                        .orElseThrow(() -> new StaleBatchLeaseException(batchId, leaseToken));
                int updated = importRowRepository.updateGeneratedPasswordEnc(row.getId(), enc);
                if (updated != 1) {
                    throw new IllegalStateException("Expected 1 updated import_row for generated password: " + row.getId());
                }
            });
            row.setGeneratedPasswordEnc(enc);
            return generated;
        }
        String enc = row.getRaw() == null ? null : row.getRaw().passwordEnc();
        if (enc == null || enc.isBlank()) {
            throw new IllegalStateException("Missing encrypted password on explicit-password import row: " + row.getId());
        }
        return encryptedObjectStorage.decryptText(enc);
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

    private record ClaimedBatch(UUID batchId, UUID leaseToken) {
    }

    private record ClaimedBatchOrStatus(ImportBatchStatus terminalOrBusyStatus, UUID leaseToken) {
    }

    private record PendingSourceDeletion(UUID batchId, String sourceKey) {
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
            processorJob.sweepPendingSourceDeletions();
        }
    }
}
