package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.PartChoiceResponse;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.PartChoiceSummary;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledItem;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledPart;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionUsageKind;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Handles after-start Part 3 choice confirmation, background generation, manual & worker retry,
 * stale-token discard, and ops failure alerting (LT §6.1, L4, L8, F4, F5, BS §5).
 */
@Service
public class AttemptChoiceService {

    private static final Logger log = LoggerFactory.getLogger(AttemptChoiceService.class);
    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");

    private static final String CHOICE_OPERATION = "attempt.part.choice";
    private static final String RETRY_OPERATION = "attempt.part.retry";
    private static final int OPS_ALERT_FAILURE_THRESHOLD = 3;
    private static final Set<String> NATURAL_SUBJECTS = Set.of("LY", "HOA", "SINH");
    private static final Set<String> SOCIAL_SUBJECTS = Set.of("SU", "DIA");

    private final AttemptAccessGate accessGate;
    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final PublishedExamPort publishedExamPort;
    private final EntitlementPort entitlementPort;
    private final PaperSolverPort paperSolverPort;
    private final QuestionSourcePort questionSourcePort;
    private final AttemptPaperAssembler paperAssembler;
    private final GenerationExecutor generationExecutor;
    private final IdempotentCommand idempotentCommand;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final AttemptSessionChecker attemptSessionChecker;
    private final AttemptLifecycleService attemptLifecycleService;
    private final TransactionTemplate readTx;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public record ChoiceCommandResult(boolean queuedNewGeneration, PartChoiceResponse snapshot) {}

    public record RetryCommandResult(boolean queuedNewGeneration, PartChoiceResponse snapshot) {}

    record PartGenerationJob(
            UUID attemptId,
            UUID partId,
            String partCode,
            UUID examId,
            String examCode,
            int examVersionNo,
            UUID generationToken,
            long partSeed,
            Map<String, Object> choice,
            UUID sessionId) {}

    public AttemptChoiceService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            PublishedExamPort publishedExamPort,
            EntitlementPort entitlementPort,
            PaperSolverPort paperSolverPort,
            QuestionSourcePort questionSourcePort,
            AttemptPaperAssembler paperAssembler,
            GenerationExecutor generationExecutor,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            AttemptLifecycleService attemptLifecycleService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.entitlementPort = Objects.requireNonNull(entitlementPort);
        this.paperSolverPort = Objects.requireNonNull(paperSolverPort);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.paperAssembler = Objects.requireNonNull(paperAssembler);
        this.generationExecutor = Objects.requireNonNull(generationExecutor);
        this.idempotentCommand = Objects.requireNonNull(idempotentCommand);
        this.overdueAttemptCloser = Objects.requireNonNull(overdueAttemptCloser);
        this.attemptSessionChecker = Objects.requireNonNull(attemptSessionChecker);
        this.attemptLifecycleService = Objects.requireNonNull(attemptLifecycleService);
        PlatformTransactionManager txMgr = Objects.requireNonNull(transactionManager);
        this.readTx = new TransactionTemplate(txMgr);
        this.readTx.setReadOnly(true);
        this.requiresNewTx = new TransactionTemplate(txMgr);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Confirms an after-start Part 3 choice idempotently and queues background generation. */
    public ResponseEntity<PartChoiceResponse> confirmChoice(
            UUID attemptId,
            UUID partId,
            String idempotencyKey,
            UUID sessionId,
            ConfirmPartChoiceRequest request) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null
                || partId == null
                || !attemptRepository.existsByIdAndUserId(attemptId, userId)
                || attemptPartRepository.findByIdAndAttemptId(partId, attemptId).isEmpty()) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        idempotentCommand.requireKey(idempotencyKey);

        Map<String, Object> digestPayload = buildChoiceDigestPayload(partId, request);
        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        boolean[] newlyExecuted = {false};
        ChoiceCommandResult commandResult;
        try {
            commandResult =
                    idempotentCommand.execute(
                            idempotencyKey,
                            CHOICE_OPERATION,
                            attemptId,
                            digestPayload,
                            ChoiceCommandResult.class,
                            () -> {
                                newlyExecuted[0] = true;
                                return executeConfirmChoice(
                                        attemptId, userId, partId, sessionId, request);
                            });
        } catch (ConflictException ex) {
            overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
            throw ex;
        }

        if (newlyExecuted[0]) {
            return commandResult.queuedNewGeneration()
                    ? ResponseEntity.status(HttpStatus.ACCEPTED).body(commandResult.snapshot())
                    : ResponseEntity.ok(commandResult.snapshot());
        }
        return ResponseEntity.ok(reloadCurrentState(attemptId, partId, sessionId));
    }

    /** Retries generation for a {@link PartState#GENERATION_FAILED} part idempotently. */
    public ResponseEntity<PartChoiceResponse> retryGeneration(
            UUID attemptId,
            UUID partId,
            String idempotencyKey,
            UUID sessionId,
            RetryPartRequest request) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null
                || partId == null
                || !attemptRepository.existsByIdAndUserId(attemptId, userId)
                || attemptPartRepository.findByIdAndAttemptId(partId, attemptId).isEmpty()) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        idempotentCommand.requireKey(idempotencyKey);

        Map<String, Object> digestPayload = buildRetryDigestPayload(partId, request);
        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        boolean[] newlyExecuted = {false};
        RetryCommandResult commandResult;
        try {
            commandResult =
                    idempotentCommand.execute(
                            idempotencyKey,
                            RETRY_OPERATION,
                            attemptId,
                            digestPayload,
                            RetryCommandResult.class,
                            () -> {
                                newlyExecuted[0] = true;
                                return executeRetry(attemptId, userId, partId, sessionId);
                            });
        } catch (ConflictException ex) {
            overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
            throw ex;
        }

        if (newlyExecuted[0]) {
            return commandResult.queuedNewGeneration()
                    ? ResponseEntity.status(HttpStatus.ACCEPTED).body(commandResult.snapshot())
                    : ResponseEntity.ok(commandResult.snapshot());
        }
        return ResponseEntity.ok(reloadCurrentState(attemptId, partId, sessionId));
    }

    /**
     * Transitions a {@link PartState#GENERATION_FAILED} part back to {@link PartState#GENERATING}
     * while holding {@code attempt -> attempt_part} pessimistic locks and schedules generation after
     * commit. Package-private so {@link PartGenerationRetryWorker} shares the exact path.
     */
    PartGenerationJob startRetryInLock(
            Attempt attempt, AttemptPart part, UUID sessionId, Instant lockNow) {
        return startRetryInLock(attempt, part, sessionId, lockNow, null);
    }

    /** Overload recording synchronous executor saturation on {@code saturatedOut} after commit. */
    PartGenerationJob startRetryInLock(
            Attempt attempt,
            AttemptPart part,
            UUID sessionId,
            Instant lockNow,
            AtomicBoolean saturatedOut) {
        PublishedVersion version =
                publishedExamPort
                        .loadVersion(attempt.getExamId(), attempt.getExamVersionNo())
                        .orElseThrow(() -> new NotFoundException("VERSION_NOT_FOUND", null));
        UUID generationToken = Ids.newId();
        part.startRetryGenerating(generationToken, lockNow);
        attemptPartRepository.saveAndFlush(part);

        PartGenerationJob job =
                new PartGenerationJob(
                        attempt.getId(),
                        part.getId(),
                        part.getPartCode(),
                        attempt.getExamId(),
                        version.code(),
                        attempt.getExamVersionNo(),
                        generationToken,
                        part.getSeed(),
                        part.getChoice(),
                        sessionId);
        schedulePartGenerationAfterCommit(job, saturatedOut);
        return job;
    }

    /**
     * Records a part generation failure while holding locks, emits the {@code GENERATION_FAILED}
     * event, and writes the single Ops {@code ERROR} alert on the 3rd consecutive failure.
     */
    void recordPartGenerationFailureInLock(
            Attempt attempt,
            AttemptPart part,
            String examCode,
            String reason,
            String detail,
            UUID sessionId,
            Instant now) {
        part.failGeneration(reason);
        attemptPartRepository.saveAndFlush(part);

        int tries = part.getGenerationTries();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attemptId", attempt.getId().toString());
        data.put("partId", part.getId().toString());
        data.put("partCode", part.getPartCode());
        data.put("examId", attempt.getExamId().toString());
        data.put("examCode", safe(examCode));
        data.put("versionNo", attempt.getExamVersionNo());
        data.put("reason", reason);
        data.put("generationTries", tries);
        data.put("detail", truncate(detail));

        attemptEventRepository.save(
                new AttemptEvent(
                        Ids.newId(),
                        attempt.getId(),
                        AttemptEventType.GENERATION_FAILED,
                        sessionId,
                        part.getId(),
                        now,
                        data));

        String safeDetail = truncate(detail);
        if (tries == OPS_ALERT_FAILURE_THRESHOLD) {
            jobLog.atError()
                    .addKeyValue("job", "attempt-part-generation")
                    .addKeyValue("attemptId", attempt.getId())
                    .addKeyValue("partId", part.getId())
                    .addKeyValue("partCode", part.getPartCode())
                    .addKeyValue("examId", attempt.getExamId())
                    .addKeyValue("examCode", safe(examCode))
                    .addKeyValue("versionNo", attempt.getExamVersionNo())
                    .addKeyValue("generationTries", tries)
                    .addKeyValue("reason", reason)
                    .addKeyValue("detail", safeDetail)
                    .log(
                            "attempt.part_generation_alert attemptId={} partId={} partCode={} examId={} examCode={} versionNo={} generationTries={} reason={} detail={}",
                            attempt.getId(),
                            part.getId(),
                            part.getPartCode(),
                            attempt.getExamId(),
                            safe(examCode),
                            attempt.getExamVersionNo(),
                            tries,
                            reason,
                            safeDetail);
        } else {
            jobLog.atWarn()
                    .addKeyValue("job", "attempt-part-generation")
                    .addKeyValue("attemptId", attempt.getId())
                    .addKeyValue("partId", part.getId())
                    .addKeyValue("partCode", part.getPartCode())
                    .addKeyValue("examId", attempt.getExamId())
                    .addKeyValue("examCode", safe(examCode))
                    .addKeyValue("versionNo", attempt.getExamVersionNo())
                    .addKeyValue("generationTries", tries)
                    .addKeyValue("reason", reason)
                    .addKeyValue("detail", safeDetail)
                    .log(
                            "attempt.part_generation_failed attemptId={} partId={} partCode={} examId={} examCode={} versionNo={} generationTries={} reason={} detail={}",
                            attempt.getId(),
                            part.getId(),
                            part.getPartCode(),
                            attempt.getExamId(),
                            safe(examCode),
                            attempt.getExamVersionNo(),
                            tries,
                            reason,
                            safeDetail);
        }
    }

    private ChoiceCommandResult executeConfirmChoice(
            UUID attemptId,
            UUID userId,
            UUID partId,
            UUID sessionId,
            ConfirmPartChoiceRequest request) {
        Attempt attempt =
                attemptRepository
                        .findByIdForUpdate(attemptId)
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        attemptSessionChecker.requireEditSession(attempt, sessionId);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
        Instant lockNow = clock.instant();
        AttemptPart part = findPart(parts, partId);
        if (part == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        if (part.getState() == PartState.SKIPPED
                && part.getCloseReason() == PartCloseReason.CHOICE_TIMEOUT) {
            throw new ConflictException("CHOICE_WINDOW_CLOSED");
        }
        if (part.getState() == PartState.AWAITING_CHOICE
                && part.getChoiceDeadlineAt() != null
                && !lockNow.isBefore(part.getChoiceDeadlineAt())) {
            throw new ConflictException("CHOICE_WINDOW_CLOSED");
        }

        attemptLifecycleService.advanceOverdueInLock(attempt, parts, lockNow);

        if (part.getState() == PartState.SKIPPED
                && part.getCloseReason() == PartCloseReason.CHOICE_TIMEOUT) {
            throw new ConflictException("CHOICE_WINDOW_CLOSED");
        }

        if (part.getChoiceConfirmedAt() != null) {
            if (attempt.getState() != AttemptState.IN_PROGRESS
                    || part.getState() == PartState.CLOSED
                    || part.getState() == PartState.SKIPPED) {
                throw new ConflictException("PART_NOT_AWAITING");
            }
            if (!matchesRecordedChoice(part.getChoice(), request)) {
                throw new ConflictException("CHOICE_LOCKED");
            }
            return new ChoiceCommandResult(false, toResponse(lockNow, attempt, part));
        }

        if (attempt.getState() != AttemptState.IN_PROGRESS
                || part.getState() != PartState.AWAITING_CHOICE) {
            throw new ConflictException("PART_NOT_AWAITING");
        }

        PublishedVersion version =
                publishedExamPort
                        .loadVersion(attempt.getExamId(), attempt.getExamVersionNo())
                        .orElseThrow(() -> new NotFoundException("VERSION_NOT_FOUND", null));
        if (!AttemptPathResolver.isAfterStartChoicePart(version, part.getPartCode())) {
            throw new ConflictException("PART_NOT_AWAITING");
        }

        Part versionPart =
                version.parts().stream()
                        .filter(p -> Objects.equals(part.getPartCode(), p.id()))
                        .findFirst()
                        .orElseThrow(() -> new ConflictException("PART_NOT_AWAITING"));

        EntitlementBenefits benefits = entitlementPort.benefitsOf(attempt.getId());
        Map<String, Object> choiceMap = validateAndBuildChoice(version, versionPart, benefits, part.getSeed(), request);

        UUID generationToken = Ids.newId();
        part.confirmChoiceAndStartGenerating(choiceMap, lockNow, generationToken, lockNow);
        attemptPartRepository.saveAndFlush(part);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("partCode", part.getPartCode());
        eventData.put("ord", part.getOrd());
        eventData.put("optionCode", choiceMap.get("optionCode"));
        eventData.put("choiceConfirmedAt", lockNow.toString());
        if (choiceMap.containsKey("subjectCodes")) {
            eventData.put("subjectCodes", choiceMap.get("subjectCodes"));
            eventData.put("subjectCounts", choiceMap.get("subjectCounts"));
        }
        eventData.put("seed", part.getSeed());
        attemptEventRepository.save(
                new AttemptEvent(
                        Ids.newId(),
                        attempt.getId(),
                        AttemptEventType.CHOICE_CONFIRMED,
                        sessionId,
                        part.getId(),
                        lockNow,
                        eventData));

        PartGenerationJob job =
                new PartGenerationJob(
                        attempt.getId(),
                        part.getId(),
                        part.getPartCode(),
                        attempt.getExamId(),
                        version.code(),
                        attempt.getExamVersionNo(),
                        generationToken,
                        part.getSeed(),
                        choiceMap,
                        sessionId);
        schedulePartGenerationAfterCommit(job, null);
        return new ChoiceCommandResult(true, toResponse(lockNow, attempt, part));
    }

    private RetryCommandResult executeRetry(
            UUID attemptId, UUID userId, UUID partId, UUID sessionId) {
        Attempt attempt =
                attemptRepository
                        .findByIdForUpdate(attemptId)
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        attemptSessionChecker.requireEditSession(attempt, sessionId);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
        Instant lockNow = clock.instant();
        AttemptPart part = findPart(parts, partId);
        if (part == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            throw new ConflictException("PART_NOT_AWAITING");
        }
        if (attempt.getTiming() == AttemptTiming.SHARED
                && attempt.getDeadlineAt() != null
                && !lockNow.isBefore(attempt.getDeadlineAt())) {
            throw new ConflictException("PART_NOT_AWAITING");
        }
        if (part.getState() == PartState.GENERATING) {
            throw new ConflictException("PART_GENERATING");
        }
        if (part.getState() != PartState.GENERATION_FAILED
                || part.getChoiceConfirmedAt() == null
                || part.getChoice() == null) {
            throw new ConflictException("PART_NOT_AWAITING");
        }

        startRetryInLock(attempt, part, sessionId, lockNow);
        return new RetryCommandResult(true, toResponse(lockNow, attempt, part));
    }

    private Map<String, Object> validateAndBuildChoice(
            PublishedVersion version,
            Part versionPart,
            EntitlementBenefits benefits,
            long partSeed,
            ConfirmPartChoiceRequest request) {
        String requestedCode = normalizeCode(request == null ? null : request.optionCode());
        if (requestedCode == null) {
            throw new BusinessRuleException("CHOICE_NOT_ALLOWED", null);
        }

        List<String> grantedNormalized = normalizeCodeList(benefits.optionCodes());
        if (benefits.optionCodes() != null
                && !benefits.optionCodes().isEmpty()
                && !grantedNormalized.contains(requestedCode)) {
            throw new BusinessRuleException("CHOICE_NOT_ALLOWED", null);
        }

        Option matchedOption = findOptionByCode(versionPart, requestedCode);
        if (matchedOption != null) {
            if (!isTokenOnPublishedPath(version.publishedPathIds(), matchedOption.id())) {
                throw new BusinessRuleException("CHOICE_NOT_ALLOWED", null);
            }
            if ("SUBJECT_CHOICE".equals(matchedOption.kind())) {
                List<String> canonicalSubjects =
                        validateAndCanonicalizeSubjects(
                                matchedOption, request == null ? null : request.subjectCodes());
                Map<String, Integer> subjectCounts =
                        computeSubjectCounts(matchedOption, canonicalSubjects, partSeed);
                Map<String, Object> choiceMap = new LinkedHashMap<>();
                choiceMap.put("optionCode", matchedOption.code());
                choiceMap.put("subjectCodes", canonicalSubjects);
                choiceMap.put("subjectCounts", subjectCounts);
                choiceMap.put("seed", partSeed);
                return choiceMap;
            }
            rejectUnexpectedSubjects(request == null ? null : request.subjectCodes());
            Map<String, Object> choiceMap = new LinkedHashMap<>();
            choiceMap.put("optionCode", matchedOption.code());
            choiceMap.put("seed", partSeed);
            return choiceMap;
        }

        Combination matchedCombination = findCombinationByCode(version, versionPart.id(), requestedCode);
        if (matchedCombination == null
                || !isTokenOnPublishedPath(
                        version.publishedPathIds(),
                        versionPart.id() + ":" + matchedCombination.code())) {
            throw new BusinessRuleException("CHOICE_NOT_ALLOWED", null);
        }
        rejectUnexpectedSubjects(request == null ? null : request.subjectCodes());
        Map<String, Object> choiceMap = new LinkedHashMap<>();
        choiceMap.put("optionCode", matchedCombination.code());
        choiceMap.put("seed", partSeed);
        return choiceMap;
    }

    private static List<String> validateAndCanonicalizeSubjects(
            Option option, List<String> rawSubjectCodes) {
        int expected = option.chooseSubjects() == null ? 0 : option.chooseSubjects();
        if (rawSubjectCodes == null || rawSubjectCodes.size() != expected || expected <= 0) {
            throw new BusinessRuleException("SUBJECTS_INVALID", null);
        }
        Set<String> allowedNorms = new LinkedHashSet<>();
        if (option.sections() != null) {
            for (Section section : option.sections()) {
                String norm = normalizeCode(section.subjectCode());
                if (norm != null) {
                    allowedNorms.add(norm);
                }
            }
        }
        Set<String> requestedNorms = new LinkedHashSet<>();
        for (String raw : rawSubjectCodes) {
            String norm = normalizeCode(raw);
            if (norm == null || !allowedNorms.contains(norm) || !requestedNorms.add(norm)) {
                throw new BusinessRuleException("SUBJECTS_INVALID", null);
            }
        }
        List<String> canonical = new ArrayList<>(expected);
        Set<String> seenCanonical = new LinkedHashSet<>();
        if (option.sections() != null) {
            for (Section section : option.sections()) {
                String norm = normalizeCode(section.subjectCode());
                if (norm != null && requestedNorms.contains(norm) && seenCanonical.add(norm)) {
                    canonical.add(section.subjectCode());
                }
            }
        }
        if (canonical.size() != expected) {
            throw new BusinessRuleException("SUBJECTS_INVALID", null);
        }
        return List.copyOf(canonical);
    }

    /**
     * Implements the HSA Science D25 / B10 split ({@code LY/HOA/SINH} vs {@code SU/DIA}, 3
     * subjects) matching {@code ChoiceGroupAllocator}: mixed natural+social gives the minority
     * group {@code low} and majority {@code high}; all-same-group picks the single {@code low}
     * subject via {@code new Random(partSeed).nextInt(selected.size())}.
     */
    private static Map<String, Integer> computeSubjectCounts(
            Option option, List<String> canonicalSubjects, long partSeed) {
        List<Integer> countsByPick = option.countsByPick();
        if (countsByPick == null || countsByPick.isEmpty()) {
            Map<String, Integer> fallback = new LinkedHashMap<>();
            if (option.sections() != null) {
                for (Section s : option.sections()) {
                    if (canonicalSubjects.contains(s.subjectCode()) && s.questionCount() != null) {
                        fallback.put(s.subjectCode(), s.questionCount());
                    }
                }
            }
            return Map.copyOf(fallback);
        }
        int high = countsByPick.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int low = countsByPick.stream().mapToInt(Integer::intValue).min().orElseThrow();

        long natural = 0;
        long social = 0;
        for (String subject : canonicalSubjects) {
            String norm = normalizeCode(subject);
            if (NATURAL_SUBJECTS.contains(norm)) {
                natural++;
            } else if (SOCIAL_SUBJECTS.contains(norm)) {
                social++;
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>(canonicalSubjects.size());
        if (natural > 0
                && social > 0
                && canonicalSubjects.size() == 3
                && natural + social == canonicalSubjects.size()) {
            boolean minorityIsNatural = natural == 1;
            for (String subject : canonicalSubjects) {
                boolean isNatural = NATURAL_SUBJECTS.contains(normalizeCode(subject));
                result.put(subject, (minorityIsNatural == isNatural) ? low : high);
            }
        } else {
            int lowIndex = new Random(partSeed).nextInt(canonicalSubjects.size());
            for (int i = 0; i < canonicalSubjects.size(); i++) {
                result.put(canonicalSubjects.get(i), i == lowIndex ? low : high);
            }
        }
        return Map.copyOf(result);
    }

    private void schedulePartGenerationAfterCommit(
            PartGenerationJob job, AtomicBoolean saturatedOut) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            submitPartGeneration(job, saturatedOut);
                        }
                    });
        } else {
            submitPartGeneration(job, saturatedOut);
        }
    }

    private void submitPartGeneration(PartGenerationJob job, AtomicBoolean saturatedOut) {
        generationExecutor.submit(
                () -> runPartGenerationJob(job),
                () -> {
                    if (saturatedOut != null) {
                        saturatedOut.set(true);
                    }
                    failPartGeneration(
                            job,
                            "GENERATION_BUSY",
                            "Generation queue saturated or exceeded maxQueueWait");
                });
    }

    private void runPartGenerationJob(PartGenerationJob job) {
        try {
            List<AttemptItem> existingItems =
                    attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(job.attemptId());
            Set<UUID> excludeQuestionIds = new LinkedHashSet<>();
            int startingDisplayNo = 1;
            for (AttemptItem item : existingItems) {
                excludeQuestionIds.add(item.getQuestionId());
                if (item.getDisplayNo() != null) {
                    int next = item.getDisplayNo() + item.getUnitCount();
                    if (next > startingDisplayNo) {
                        startingDisplayNo = next;
                    }
                }
            }

            String optionCode = asString(job.choice().get("optionCode"));
            List<String> subjectCodes = asStringList(job.choice().get("subjectCodes"));
            PaperSolverPort.Selection selection =
                    subjectCodes.isEmpty()
                            ? PaperSolverPort.Selection.option(job.partCode(), optionCode)
                            : PaperSolverPort.Selection.subjects(
                                    job.partCode(), optionCode, subjectCodes);

            GeneratedPaper paper =
                    paperSolverPort.generate(
                            job.examId(),
                            job.examVersionNo(),
                            List.of(job.partCode()),
                            List.of(selection),
                            job.partSeed(),
                            excludeQuestionIds);

            GeneratedPart generatedPart =
                    paper.parts().stream()
                            .filter(p -> Objects.equals(job.partCode(), p.partCode()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Solver omitted requested part: "
                                                            + job.partCode()));

            validateSubjectCounts(generatedPart, job.choice());

            List<VersionKey> rootKeys = collectRootKeys(generatedPart);
            Map<VersionKey, Version> versionMap = loadVersionsWithChildren(rootKeys);
            AssembledPart assembled =
                    paperAssembler.assemble(
                            generatedPart, versionMap, Map.of(), job.partSeed(), startingDisplayNo);

            persistGeneratedPart(job, paper, rootKeys, assembled);
        } catch (PaperGenerationException ex) {
            String reason =
                    ex.reason() == PaperGenerationException.Reason.GENERATION_TIMEOUT
                            ? "GENERATION_TIMEOUT"
                            : "GENERATION_FAILED";
            failPartGeneration(job, reason, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn(
                    "attempt.part_generation_unexpected_error attemptId={} partId={}",
                    job.attemptId(),
                    job.partId(),
                    ex);
            failPartGeneration(
                    job,
                    "GENERATION_FAILED",
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private void persistGeneratedPart(
            PartGenerationJob job,
            GeneratedPaper paper,
            List<VersionKey> rootKeys,
            AssembledPart assembled) {
        requiresNewTx.executeWithoutResult(
                status -> {
                    Attempt attempt =
                            attemptRepository.findByIdForUpdate(job.attemptId()).orElse(null);
                    if (attempt == null || attempt.getState() != AttemptState.IN_PROGRESS) {
                        return;
                    }
                    List<AttemptPart> parts =
                            attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(
                                    job.attemptId());
                    AttemptPart part = findPart(parts, job.partId());
                    if (part == null
                            || part.getState() != PartState.GENERATING
                            || !job.generationToken().equals(part.getGenerationToken())
                            || attemptItemRepository.existsByPartId(part.getId())) {
                        return;
                    }

                    Instant now = clock.instant();
                    if (attempt.getTiming() == AttemptTiming.SHARED
                            && attempt.getDeadlineAt() != null
                            && !now.isBefore(attempt.getDeadlineAt())) {
                        return;
                    }

                    if (!rootKeys.isEmpty()) {
                        questionSourcePort.recordUsage(
                                job.examId(), QuestionUsageKind.ATTEMPT, rootKeys);
                    }

                    List<AttemptItem> rootItems = new ArrayList<>();
                    List<AttemptItem> childItems = new ArrayList<>();
                    collectPartItems(
                            job.attemptId(),
                            part.getId(),
                            assembled.items(),
                            rootItems,
                            childItems);
                    if (!rootItems.isEmpty()) {
                        attemptItemRepository.saveAll(rootItems);
                        attemptItemRepository.flush();
                    }
                    if (!childItems.isEmpty()) {
                        attemptItemRepository.saveAll(childItems);
                    }

                    Instant partDeadline =
                            attempt.getTiming() == AttemptTiming.SHARED
                                    ? attempt.getDeadlineAt()
                                    : (part.getDurationMinutes() != null
                                            ? now.plus(Duration.ofMinutes(part.getDurationMinutes()))
                                            : null);
                    part.open(now, partDeadline);
                    attemptPartRepository.saveAndFlush(part);

                    Map<String, Object> openedData = new LinkedHashMap<>();
                    openedData.put("partCode", part.getPartCode());
                    openedData.put("ord", part.getOrd());
                    openedData.put("startedAt", now.toString());
                    if (paper.dropped() != null && !paper.dropped().isEmpty()) {
                        List<String> droppedIds =
                                paper.dropped().stream()
                                        .map(d -> d.question().questionId().toString())
                                        .toList();
                        openedData.put("droppedQuestionIds", droppedIds);
                    }
                    attemptEventRepository.save(
                            new AttemptEvent(
                                    Ids.newId(),
                                    attempt.getId(),
                                    AttemptEventType.PART_OPENED,
                                    job.sessionId(),
                                    part.getId(),
                                    now,
                                    openedData));
                });
    }

    private void failPartGeneration(PartGenerationJob job, String reason, String detail) {
        try {
            requiresNewTx.executeWithoutResult(
                    status -> {
                        Attempt attempt =
                                attemptRepository.findByIdForUpdate(job.attemptId()).orElse(null);
                        if (attempt == null || attempt.getState() != AttemptState.IN_PROGRESS) {
                            return;
                        }
                        List<AttemptPart> parts =
                                attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(
                                        job.attemptId());
                        AttemptPart part = findPart(parts, job.partId());
                        if (part == null
                                || part.getState() != PartState.GENERATING
                                || !job.generationToken().equals(part.getGenerationToken())) {
                            return;
                        }
                        recordPartGenerationFailureInLock(
                                attempt,
                                part,
                                job.examCode(),
                                reason,
                                detail,
                                job.sessionId(),
                                clock.instant());
                    });
        } catch (RuntimeException ex) {
            log.error(
                    "attempt.part_generation_fail_persist_error attemptId={} partId={} reason={}",
                    job.attemptId(),
                    job.partId(),
                    reason,
                    ex);
        }
    }

    private PartChoiceResponse reloadCurrentState(UUID attemptId, UUID partId, UUID sessionId) {
        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
        return readTx.execute(
                status -> {
                    Attempt attempt =
                            attemptRepository
                                    .findById(attemptId)
                                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
                    if (attempt.getState() == AttemptState.IN_PROGRESS) {
                        attemptSessionChecker.requireEditSession(attempt, sessionId);
                    }
                    AttemptPart part =
                            attemptPartRepository
                                    .findByIdAndAttemptId(partId, attemptId)
                                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
                    return toResponse(clock.instant(), attempt, part);
                });
    }

    private Map<VersionKey, Version> loadVersionsWithChildren(List<VersionKey> rootKeys) {
        if (rootKeys.isEmpty()) {
            return Map.of();
        }
        Map<VersionKey, Version> map = new LinkedHashMap<>();
        List<VersionKey> childKeys = new ArrayList<>();
        Set<VersionKey> seenChildKeys = new LinkedHashSet<>();

        for (Version root : questionSourcePort.loadVersions(rootKeys)) {
            map.put(root.key(), root);
            JsonNode children = root.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    String idText = child.path("id").asText(null);
                    if (idText != null && !idText.isBlank()) {
                        VersionKey childKey =
                                new VersionKey(UUID.fromString(idText), root.key().versionNo());
                        if (!map.containsKey(childKey) && seenChildKeys.add(childKey)) {
                            childKeys.add(childKey);
                        }
                    }
                }
            }
        }
        if (!childKeys.isEmpty()) {
            for (Version child : questionSourcePort.loadVersions(childKeys)) {
                map.putIfAbsent(child.key(), child);
            }
        }
        return map;
    }

    private static List<VersionKey> collectRootKeys(GeneratedPart part) {
        Set<VersionKey> keys = new LinkedHashSet<>();
        for (GeneratedSection section : part.sections()) {
            for (GeneratedGroup group : section.groups()) {
                for (GeneratedQuestion q : group.questions()) {
                    keys.add(new VersionKey(q.questionId(), q.versionNo()));
                }
            }
        }
        return List.copyOf(keys);
    }

    private static void collectPartItems(
            UUID attemptId,
            UUID partId,
            List<AssembledItem> assembled,
            List<AttemptItem> rootItems,
            List<AttemptItem> childItems) {
        UUID[] ids = new UUID[assembled.size()];
        for (int i = 0; i < assembled.size(); i++) {
            ids[i] = Ids.newId();
        }
        for (int i = 0; i < assembled.size(); i++) {
            AssembledItem src = assembled.get(i);
            UUID parentId = src.parentIndex() == null ? null : ids[src.parentIndex()];
            AttemptItem entity =
                    AttemptItem.create(
                            ids[i],
                            attemptId,
                            partId,
                            src.subjectItemId(),
                            src.sectionId(),
                            src.componentCode(),
                            src.blockId(),
                            parentId,
                            src.ord(),
                            src.displayNo(),
                            src.unitCount(),
                            src.questionId(),
                            src.questionVersionNo(),
                            src.questionType(),
                            src.pointsMax(),
                            src.optionOrder());
            if (parentId == null) {
                rootItems.add(entity);
            } else {
                childItems.add(entity);
            }
        }
    }

    private static void validateSubjectCounts(GeneratedPart generatedPart, Map<String, Object> choice) {
        Object rawCounts = choice.get("subjectCounts");
        if (!(rawCounts instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return;
        }
        Map<String, Integer> expected = new LinkedHashMap<>();
        rawMap.forEach(
                (k, v) -> {
                    if (k != null && v instanceof Number num) {
                        expected.put(normalizeCode(k.toString()), num.intValue());
                    }
                });
        if (generatedPart.sections().size() != expected.size()) {
            throw new IllegalStateException(
                    "Generated sections count mismatch: expected "
                            + expected.size()
                            + ", got "
                            + generatedPart.sections().size());
        }
        Set<String> seen = new LinkedHashSet<>();
        for (GeneratedSection section : generatedPart.sections()) {
            String normSubj = normalizeCode(section.subjectCode());
            Integer wantedCount = expected.get(normSubj);
            if (wantedCount == null || !seen.add(normSubj) || section.questionCount() != wantedCount) {
                throw new IllegalStateException(
                        "Generated section count mismatch for subject "
                                + section.subjectCode()
                                + ": expected "
                                + wantedCount
                                + ", got "
                                + section.questionCount());
            }
        }
    }

    private static boolean matchesRecordedChoice(
            Map<String, Object> recorded, ConfirmPartChoiceRequest request) {
        if (recorded == null || request == null) {
            return false;
        }
        String reqOption = normalizeCode(request.optionCode());
        String recordedOption = normalizeCode(asString(recorded.get("optionCode")));
        if (reqOption == null || !reqOption.equals(recordedOption)) {
            return false;
        }
        List<String> recordedSubjects = asStringList(recorded.get("subjectCodes"));
        List<String> rawReqSubjects = request.subjectCodes();
        if (recordedSubjects.isEmpty()) {
            return rawReqSubjects == null || rawReqSubjects.isEmpty();
        }
        if (rawReqSubjects == null || rawReqSubjects.size() != recordedSubjects.size()) {
            return false;
        }
        Set<String> recordedNorms = new LinkedHashSet<>();
        for (String s : recordedSubjects) {
            String norm = normalizeCode(s);
            if (norm != null) {
                recordedNorms.add(norm);
            }
        }
        Set<String> reqNorms = new LinkedHashSet<>();
        for (String s : rawReqSubjects) {
            String norm = normalizeCode(s);
            if (norm == null || !reqNorms.add(norm)) {
                return false;
            }
        }
        return recordedNorms.equals(reqNorms);
    }

    private static Map<String, Object> buildChoiceDigestPayload(
            UUID partId, ConfirmPartChoiceRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partId", partId.toString());
        String optionCode =
                request == null || request.optionCode() == null
                        ? null
                        : request.optionCode().trim();
        payload.put("optionCode", optionCode);
        List<String> subjectCodes = null;
        if (request != null && request.subjectCodes() != null) {
            List<String> trimmed = new ArrayList<>(request.subjectCodes().size());
            for (String code : request.subjectCodes()) {
                trimmed.add(code == null ? null : code.trim());
            }
            subjectCodes = trimmed;
        }
        payload.put("subjectCodes", subjectCodes);
        return payload;
    }

    private static Map<String, Object> buildRetryDigestPayload(
            UUID partId, RetryPartRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partId", partId.toString());
        String reason =
                request == null || request.reason() == null ? null : request.reason().trim();
        payload.put("reason", reason);
        return payload;
    }

    private static PartChoiceResponse toResponse(
            Instant serverNow, Attempt attempt, AttemptPart part) {
        return new PartChoiceResponse(
                serverNow,
                attempt.getId(),
                attempt.getState(),
                part.getId(),
                part.getPartCode(),
                part.getState(),
                toChoiceSummary(part.getChoice()),
                part.getChoiceConfirmedAt(),
                part.getChoiceDeadlineAt(),
                part.getStartedAt(),
                part.getDeadlineAt(),
                part.getGenerationTries(),
                part.getGenerationError());
    }

    private static PartChoiceSummary toChoiceSummary(Map<String, Object> rawChoice) {
        if (rawChoice == null || rawChoice.isEmpty()) {
            return null;
        }
        String optionCode = asString(rawChoice.get("optionCode"));
        List<String> subjectCodes = asStringList(rawChoice.get("subjectCodes"));
        List<String> mappedSubjects = subjectCodes.isEmpty() ? null : subjectCodes;
        Map<String, Integer> mappedCounts = null;
        if (rawChoice.get("subjectCounts") instanceof Map<?, ?> rawCounts && !rawCounts.isEmpty()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            rawCounts.forEach(
                    (k, v) -> {
                        if (k != null && v instanceof Number num) {
                            counts.put(k.toString(), num.intValue());
                        }
                    });
            if (!counts.isEmpty()) {
                mappedCounts = counts;
            }
        }
        return new PartChoiceSummary(optionCode, mappedSubjects, mappedCounts);
    }

    private static AttemptPart findPart(List<AttemptPart> parts, UUID partId) {
        for (AttemptPart part : parts) {
            if (part.getId().equals(partId)) {
                return part;
            }
        }
        return null;
    }

    private static Option findOptionByCode(Part part, String normalizedCode) {
        if (part.options() == null) {
            return null;
        }
        for (Option option : part.options()) {
            if (Objects.equals(normalizedCode, normalizeCode(option.code()))) {
                return option;
            }
        }
        return null;
    }

    private static Combination findCombinationByCode(
            PublishedVersion version, String partId, String normalizedCode) {
        if (version.combinations() == null) {
            return null;
        }
        for (Combination comb : version.combinations()) {
            if (Objects.equals(partId, comb.partId())
                    && Objects.equals(normalizedCode, normalizeCode(comb.code()))) {
                return comb;
            }
        }
        return null;
    }

    private static boolean isTokenOnPublishedPath(List<String> publishedPathIds, String token) {
        if (publishedPathIds == null || publishedPathIds.isEmpty() || token == null) {
            return false;
        }
        for (String path : publishedPathIds) {
            if (path != null && Arrays.asList(path.split("\\+", -1)).contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static void rejectUnexpectedSubjects(List<String> subjectCodes) {
        if (subjectCodes != null && !subjectCodes.isEmpty()) {
            throw new BusinessRuleException("SUBJECTS_INVALID", null);
        }
    }

    private static List<String> normalizeCodeList(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>(raw.size());
        for (String s : raw) {
            String norm = normalizeCode(s);
            if (norm != null) {
                list.add(norm);
            }
        }
        return list;
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String asString(Object val) {
        return val instanceof String s ? s : null;
    }

    private static List<String> asStringList(Object val) {
        if (!(val instanceof Collection<?> col)) {
            return List.of();
        }
        List<String> list = new ArrayList<>(col.size());
        for (Object item : col) {
            if (item instanceof String s) {
                list.add(s);
            }
        }
        return list;
    }

    private static String safe(String val) {
        return val == null ? "" : val;
    }

    private static String truncate(String val) {
        if (val == null) {
            return "";
        }
        return val.length() <= 240 ? val : val.substring(0, 240);
    }
}
