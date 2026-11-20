package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.StartAttemptRequest;
import vn.unicom.thi360.attempt.api.StartAttemptResponse;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledItem;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledPart;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.PlannedPart;
import vn.unicom.thi360.attempt.application.AttemptPathResolver.ResolvedStartPlan;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionUsageKind;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Orchestrates idempotent attempt start (Tx1), async background paper generation via
 * {@link GenerationExecutor}, atomic persistence + usage recording (Tx2), and failure abort +
 * entitlement release (LT §5.2, AC-1..AC-9, AC-28..AC-31, AC-34).
 */
@Service
public class AttemptStartService {

    private static final Logger log = LoggerFactory.getLogger(AttemptStartService.class);
    private static final List<AttemptState> OPEN_STATES =
            List.of(AttemptState.STARTING, AttemptState.IN_PROGRESS);
    private static final String OPERATION = "attempt.start";

    private final AttemptAccessGate accessGate;
    private final EntitlementPort entitlementPort;
    private final PublishedExamPort publishedExamPort;
    private final PaperSolverPort paperSolverPort;
    private final QuestionSourcePort questionSourcePort;
    private final AttemptPathResolver pathResolver;
    private final AttemptPaperAssembler paperAssembler;
    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final GenerationExecutor generationExecutor;
    private final IdempotentCommand idempotentCommand;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public record StartCommandResult(UUID attemptId, boolean resumed) {}

    private record GenerationJob(
            UUID attemptId,
            UUID userId,
            UUID examId,
            String examCode,
            int examVersionNo,
            UUID generationToken,
            long seed,
            AttemptTiming timing,
            Integer examDurationMinutes,
            BigDecimal totalMaxScore,
            List<String> partCodesToGenerate,
            List<PaperSolverPort.Selection> selections,
            List<PlannedPart> plannedParts,
            String pathDescription) {}

    public AttemptStartService(
            AttemptAccessGate accessGate,
            EntitlementPort entitlementPort,
            PublishedExamPort publishedExamPort,
            PaperSolverPort paperSolverPort,
            QuestionSourcePort questionSourcePort,
            AttemptPathResolver pathResolver,
            AttemptPaperAssembler paperAssembler,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            GenerationExecutor generationExecutor,
            IdempotentCommand idempotentCommand,
            OverdueAttemptCloser overdueAttemptCloser,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.accessGate = accessGate;
        this.entitlementPort = entitlementPort;
        this.publishedExamPort = publishedExamPort;
        this.paperSolverPort = paperSolverPort;
        this.questionSourcePort = questionSourcePort;
        this.pathResolver = pathResolver;
        this.paperAssembler = paperAssembler;
        this.attemptRepository = attemptRepository;
        this.attemptPartRepository = attemptPartRepository;
        this.attemptItemRepository = attemptItemRepository;
        this.attemptEventRepository = attemptEventRepository;
        this.generationExecutor = generationExecutor;
        this.idempotentCommand = idempotentCommand;
        this.overdueAttemptCloser = overdueAttemptCloser;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public ResponseEntity<StartAttemptResponse> startAttempt(
            String idempotencyKey, StartAttemptRequest request) {
        UUID userId = accessGate.requireLearner();
        boolean[] newlyExecuted = {false};
        StartCommandResult commandResult =
                idempotentCommand.execute(
                        idempotencyKey,
                        OPERATION,
                        request,
                        StartCommandResult.class,
                        () -> {
                            newlyExecuted[0] = true;
                            return executeStartOrResume(userId, request, idempotencyKey);
                        });

        if (newlyExecuted[0] && !commandResult.resumed()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(
                            new StartAttemptResponse(
                                    commandResult.attemptId(),
                                    AttemptState.STARTING,
                                    false,
                                    null,
                                    false));
        }

        Attempt current =
                attemptRepository
                        .findById(commandResult.attemptId())
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        AttemptState currentState = current.getState();
        AbortReason abortReason =
                currentState == AttemptState.ABORTED ? current.getAbortReason() : null;
        boolean retryable = currentState == AttemptState.ABORTED;

        StartAttemptResponse body =
                new StartAttemptResponse(
                        current.getId(), currentState, commandResult.resumed(), abortReason, retryable);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    private StartCommandResult executeStartOrResume(
            UUID userId, StartAttemptRequest request, String idempotencyKey) {
        EntitlementDescription desc = entitlementPort.describe(userId, request.entitlementId());

        // Serialize concurrent start attempts for the same (user, exam) on a single JDBC connection
        // to eliminate nested REQUIRES_NEW pool contention and roll back cleanly on reserve errors.
        jdbcTemplate.queryForObject(
                "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))) lock_acquired",
                Integer.class,
                userId.toString(),
                desc.examId().toString());

        Optional<Attempt> existingOpen =
                attemptRepository.findByUserIdAndExamIdAndStateIn(
                        userId, desc.examId(), OPEN_STATES);
        if (existingOpen.isPresent()) {
            Attempt open = existingOpen.get();
            if (open.getState() == AttemptState.IN_PROGRESS) {
                UUID openId = open.getId();
                entityManager.detach(open);
                overdueAttemptCloser.closeIfOverdue(openId, clock.instant());
                open = attemptRepository.findById(openId).orElse(null);
            }
            if (open != null && OPEN_STATES.contains(open.getState())) {
                return new StartCommandResult(open.getId(), true);
            }
        }

        ServingExam serving =
                publishedExamPort
                        .findServing(desc.examId())
                        .orElseThrow(() -> new NotFoundException("EXAM_NOT_FOUND", null));
        ResolvedStartPlan plan =
                pathResolver.resolveStartPlan(serving, desc.optionCodes(), request.preChoice());

        UUID attemptId = Ids.newId();
        UUID generationToken = Ids.newId();
        long seed = secureRandom.nextLong();

        EntitlementBenefits benefits =
                entitlementPort.reserve(userId, request.entitlementId(), attemptId);
        if (!Objects.equals(benefits.optionCodes(), desc.optionCodes())) {
            plan = pathResolver.resolveStartPlan(serving, benefits.optionCodes(), request.preChoice());
        }
        PublishedVersion version = plan.version();
        AttemptTiming timing = AttemptTiming.valueOf(version.timing());
        Instant now = clock.instant();
        Attempt attempt =
                Attempt.start(
                        attemptId,
                        userId,
                        serving.examId(),
                        version.versionNo(),
                        request.entitlementId(),
                        benefits.grantId(),
                        version.name(),
                        version.kindItemId(),
                        version.kindCode(),
                        version.scope(),
                        version.partCode(),
                        version.subjectItemId(),
                        timing,
                        NavigationMode.SEQUENTIAL,
                        plan.choices(),
                        seed,
                        generationToken,
                        benefits.resultView(),
                        version.practiceScoring(),
                        version.referenceMax(),
                        plan.totalMaxScore(),
                        idempotencyKey,
                        now);
        attemptRepository.saveAndFlush(attempt);

        GenerationJob job =
                new GenerationJob(
                        attemptId,
                        userId,
                        serving.examId(),
                        version.code(),
                        version.versionNo(),
                        generationToken,
                        seed,
                        timing,
                        plan.examDurationMinutes(),
                        plan.totalMaxScore(),
                        plan.partCodesToGenerate(),
                        plan.selections(),
                        plan.plannedParts(),
                        plan.pathDescription());

        scheduleGenerationAfterCommit(job);
        return new StartCommandResult(attemptId, false);
    }

    private void scheduleGenerationAfterCommit(GenerationJob job) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            submitGeneration(job);
                        }
                    });
        } else {
            submitGeneration(job);
        }
    }

    private void submitGeneration(GenerationJob job) {
        generationExecutor.submit(
                () -> runGenerationJob(job),
                () ->
                        abortGeneration(
                                job,
                                AbortReason.GENERATION_BUSY,
                                "Generation queue saturated or exceeded maxQueueWait"));
    }

    private void runGenerationJob(GenerationJob job) {
        try {
            GeneratedPaper paper =
                    paperSolverPort.generate(
                            job.examId(),
                            job.examVersionNo(),
                            job.partCodesToGenerate(),
                            job.selections(),
                            job.seed(),
                            Set.of());

            List<VersionKey> rootKeys = collectRootKeys(paper);
            Map<VersionKey, Version> versionMap = loadVersionsWithChildren(rootKeys);
            Map<String, GeneratedPart> generatedByPartCode = new LinkedHashMap<>();
            for (GeneratedPart gp : paper.parts()) {
                generatedByPartCode.put(gp.partCode(), gp);
            }

            Map<String, AssembledPart> assembledByPartCode = new LinkedHashMap<>();
            int nextDisplayNo = 1;
            for (PlannedPart pp : job.plannedParts()) {
                GeneratedPart gp = generatedByPartCode.get(pp.partCode());
                if (gp == null) {
                    if (job.partCodesToGenerate().contains(pp.partCode())) {
                        throw new IllegalStateException(
                                "Solver omitted requested part: " + pp.partCode());
                    }
                    continue;
                }
                Map<String, Boolean> shuffleMap = buildShuffleMap(gp);
                long partSeed = mixSeed(job.seed(), pp.ord());
                AssembledPart assembled =
                        paperAssembler.assemble(gp, versionMap, shuffleMap, partSeed, nextDisplayNo);
                nextDisplayNo = assembled.nextDisplayNo();
                assembledByPartCode.put(pp.partCode(), assembled);
            }

            persistGeneratedPaper(job, rootKeys, assembledByPartCode);
        } catch (PaperGenerationException ex) {
            AbortReason reason =
                    ex.reason() == PaperGenerationException.Reason.GENERATION_TIMEOUT
                            ? AbortReason.GENERATION_TIMEOUT
                            : AbortReason.GENERATION_FAILED;
            abortGeneration(job, reason, ex.getMessage());
        } catch (RuntimeException ex) {
            abortGeneration(job, AbortReason.GENERATION_FAILED, ex.getMessage());
        }
    }

    private void persistGeneratedPaper(
            GenerationJob job,
            List<VersionKey> rootKeys,
            Map<String, AssembledPart> assembledByPartCode) {
        requiresNewTx.executeWithoutResult(
                status -> {
                    Attempt attempt = attemptRepository.findByIdForUpdate(job.attemptId()).orElse(null);
                    if (attempt == null
                            || attempt.getState() != AttemptState.STARTING
                            || !job.generationToken().equals(attempt.getGenerationToken())) {
                        return;
                    }

                    if (!rootKeys.isEmpty()) {
                        questionSourcePort.recordUsage(job.examId(), QuestionUsageKind.ATTEMPT, rootKeys);
                    }

                    int attemptNo = attemptRepository.findNextAttemptNo(job.userId(), job.examId());
                    Instant now = clock.instant();
                    Instant attemptDeadline =
                            job.timing() == AttemptTiming.SHARED && job.examDurationMinutes() != null
                                    ? now.plus(Duration.ofMinutes(job.examDurationMinutes()))
                                    : null;

                    attempt.updateMaxScore(job.totalMaxScore());
                    attempt.open(attemptNo, now, attemptDeadline);
                    attemptRepository.save(attempt);

                    List<AttemptPart> partEntities = new ArrayList<>(job.plannedParts().size());
                    List<AttemptItem> rootItems = new ArrayList<>();
                    List<AttemptItem> childItems = new ArrayList<>();
                    UUID openedPartId = null;

                    for (PlannedPart pp : job.plannedParts()) {
                        UUID partId = Ids.newId();
                        long partSeed = mixSeed(job.seed(), pp.ord());
                        AttemptPart part =
                                AttemptPart.createPending(
                                        partId,
                                        job.attemptId(),
                                        pp.partCode(),
                                        pp.partName(),
                                        pp.ord(),
                                        pp.durationMinutes(),
                                        pp.questionCount(),
                                        pp.maxPoints(),
                                        partSeed);
                        if (pp.ord() == 0) {
                            Instant partDeadline =
                                    job.timing() == AttemptTiming.SHARED
                                            ? attemptDeadline
                                            : (pp.durationMinutes() != null
                                                    ? now.plus(Duration.ofMinutes(pp.durationMinutes()))
                                                    : null);
                            part.open(now, partDeadline);
                            openedPartId = partId;
                        }
                        partEntities.add(part);

                        AssembledPart assembled = assembledByPartCode.get(pp.partCode());
                        if (assembled != null && !assembled.items().isEmpty()) {
                            collectPartItems(
                                    job.attemptId(), partId, assembled.items(), rootItems, childItems);
                        }
                    }

                    attemptPartRepository.saveAll(partEntities);
                    attemptPartRepository.flush();
                    if (!rootItems.isEmpty()) {
                        attemptItemRepository.saveAll(rootItems);
                        attemptItemRepository.flush();
                    }
                    if (!childItems.isEmpty()) {
                        attemptItemRepository.saveAll(childItems);
                    }

                    List<AttemptEvent> events = new ArrayList<>(2);
                    events.add(
                            new AttemptEvent(
                                    Ids.newId(),
                                    job.attemptId(),
                                    AttemptEventType.STARTED,
                                    null,
                                    null,
                                    now,
                                    Map.of(
                                            "examCode", safe(job.examCode()),
                                            "versionNo", job.examVersionNo(),
                                            "attemptNo", attemptNo)));
                    if (openedPartId != null) {
                        events.add(
                                new AttemptEvent(
                                        Ids.newId(),
                                        job.attemptId(),
                                        AttemptEventType.PART_OPENED,
                                        null,
                                        openedPartId,
                                        now,
                                        Map.of("ord", 0)));
                    }
                    attemptEventRepository.saveAll(events);
                });
    }

    private void abortGeneration(GenerationJob job, AbortReason reason, String detail) {
        try {
            requiresNewTx.executeWithoutResult(
                    status -> {
                        Attempt attempt =
                                attemptRepository.findByIdForUpdate(job.attemptId()).orElse(null);
                        if (attempt == null
                                || attempt.getState() != AttemptState.STARTING
                                || !job.generationToken().equals(attempt.getGenerationToken())) {
                            return;
                        }
                        Instant now = clock.instant();
                        attempt.abort(reason, now);
                        attemptRepository.save(attempt);
                        attemptEventRepository.save(
                                new AttemptEvent(
                                        Ids.newId(),
                                        job.attemptId(),
                                        AttemptEventType.GENERATION_FAILED,
                                        null,
                                        null,
                                        now,
                                        Map.of(
                                                "examCode", safe(job.examCode()),
                                                "versionNo", job.examVersionNo(),
                                                "path", safe(job.pathDescription()),
                                                "reason", reason.name(),
                                                "detail", truncate(detail))));
                        entitlementPort.release(job.attemptId());
                        log.warn(
                                "attempt.generation_aborted examCode={} versionNo={} path={} attemptId={} reason={} detail={}",
                                job.examCode(),
                                job.examVersionNo(),
                                job.pathDescription(),
                                job.attemptId(),
                                reason,
                                truncate(detail));
                    });
        } catch (RuntimeException ex) {
            log.error(
                    "attempt.generation_abort_failed attemptId={} reason={}",
                    job.attemptId(),
                    reason,
                    ex);
        }
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

    private static List<VersionKey> collectRootKeys(GeneratedPaper paper) {
        Set<VersionKey> keys = new LinkedHashSet<>();
        for (GeneratedPart part : paper.parts()) {
            for (GeneratedSection section : part.sections()) {
                for (GeneratedGroup group : section.groups()) {
                    for (GeneratedQuestion q : group.questions()) {
                        keys.add(new VersionKey(q.questionId(), q.versionNo()));
                    }
                }
            }
        }
        return List.copyOf(keys);
    }

    private static Map<String, Boolean> buildShuffleMap(GeneratedPart part) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (GeneratedSection section : part.sections()) {
            for (GeneratedGroup group : section.groups()) {
                map.putIfAbsent(group.groupId(), group.shuffleUnits());
            }
        }
        return map;
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

    private static long mixSeed(long attemptSeed, int partOrd) {
        long z = attemptSeed ^ ((partOrd + 1L) * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
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
