package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.EssayCriterionInput;
import vn.unicom.thi360.attempt.api.EssayGradeView;
import vn.unicom.thi360.attempt.api.FinalizeEssayRequest;
import vn.unicom.thi360.attempt.api.FinalizeEssayResponse;
import vn.unicom.thi360.attempt.api.GradingItemDetailResponse;
import vn.unicom.thi360.attempt.api.GradingItemDetailResponse.RubricCriterionView;
import vn.unicom.thi360.attempt.api.GradingQueueCountsResponse;
import vn.unicom.thi360.attempt.api.GradingQueueItemView;
import vn.unicom.thi360.attempt.api.GradingQueueState;
import vn.unicom.thi360.attempt.api.ReopenEssayRequest;
import vn.unicom.thi360.attempt.api.SaveEssayDraftRequest;
import vn.unicom.thi360.attempt.application.AttemptDateFilter.DateBound;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EssayCriterionScore;
import vn.unicom.thi360.attempt.domain.EssayGrade;
import vn.unicom.thi360.attempt.domain.EssayGradeState;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.grading.ExactRational;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.attempt.infrastructure.EssayGradeRepository;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.UserSummary;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Anonymous manual essay grading queue, claim/takeover (30-minute idle threshold), draft saving,
 * finalization, and reopen workflows (LT §9.2, F17, BS §11, AC-1..AC-11, AC-22).
 */
@Service
public class EssayGradingService {

    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String BASE_ELIGIBLE_PREDICATES = """
            i.question_type = 'ESSAY'
            AND i.in_scope = true
            AND i.outcome IS NOT NULL
            AND i.outcome <> 'BLANK'
            AND a.state IN ('PENDING_REVIEW', 'SCORED')
            """;

    private static final String DONE_PREDICATE =
            "(i.outcome <> 'PENDING_REVIEW' AND g.id IS NOT NULL AND g.state = 'FINAL')";
    private static final String IN_PROGRESS_PREDICATE =
            "(i.outcome = 'PENDING_REVIEW' AND g.id IS NOT NULL AND g.state = 'DRAFT' AND g.last_activity_at >= :idleCutoff)";
    private static final String WAITING_PREDICATE =
            "(i.outcome = 'PENDING_REVIEW' AND (g.id IS NULL OR (g.state = 'DRAFT' AND g.last_activity_at < :idleCutoff)))";

    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final EssayGradeRepository essayGradeRepository;
    private final AttemptGradingService attemptGradingService;
    private final QuestionSourcePort questionSourcePort;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final Clock clock;

    private record QueueRow(
            UUID itemId,
            UUID attemptId,
            UUID examId,
            String examName,
            String examKindCode,
            String partCode,
            String partName,
            Integer displayNo,
            BigDecimal pointsMax,
            Instant submittedAt,
            boolean regrading,
            String outcome,
            BigDecimal earnedPoints,
            UUID graderId,
            String gradeState,
            Instant claimedAt,
            Instant lastActivityAt,
            Instant finalizedAt) {}

    public EssayGradingService(
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            EssayGradeRepository essayGradeRepository,
            AttemptGradingService attemptGradingService,
            QuestionSourcePort questionSourcePort,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            NamedParameterJdbcTemplate jdbc,
            EntityManager entityManager,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.essayGradeRepository = Objects.requireNonNull(essayGradeRepository);
        this.attemptGradingService = Objects.requireNonNull(attemptGradingService);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.userDirectory = Objects.requireNonNull(userDirectory);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.entityManager = Objects.requireNonNull(entityManager);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public PageResponse<GradingQueueItemView> listItems(
            GradingQueueState state,
            String exam,
            String kind,
            String from,
            String to,
            Integer page,
            Integer size) {
        entityManager.flush();
        DateBound fromBound = AttemptDateFilter.parseBound(from, true);
        DateBound toBound = AttemptDateFilter.parseBound(to, false);

        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Instant now = clock.instant();
        Instant idleCutoff = now.minus(IDLE_TIMEOUT);

        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildFilterWhereClause(state, exam, kind, fromBound, toBound, idleCutoff, params);

        String countSql = """
                SELECT COUNT(*)
                FROM attempt.attempt_item i
                JOIN attempt.attempt a ON a.id = i.attempt_id
                LEFT JOIN attempt.essay_grade g ON g.attempt_item_id = i.id AND g.superseded_by IS NULL
                WHERE
                """ + " " + whereClause;

        Long totalObj = jdbc.queryForObject(countSql, params, Long.class);
        long totalItems = totalObj == null ? 0L : totalObj;
        if (totalItems == 0L) {
            return new PageResponse<>(List.of(), safePage, safeSize, 0L, 0);
        }

        params.addValue("limit", safeSize);
        params.addValue("offset", (long) safePage * safeSize);

        String dataSql = """
                SELECT i.id AS item_id,
                       i.attempt_id,
                       a.exam_id,
                       a.exam_name,
                       a.exam_kind_code,
                       p.part_code,
                       p.part_name,
                       i.display_no,
                       i.points_max,
                       a.finished_at AS submitted_at,
                       a.regrading,
                       i.outcome,
                       i.earned_points,
                       g.grader_id,
                       g.state AS grade_state,
                       g.claimed_at,
                       g.last_activity_at,
                       g.finalized_at
                FROM attempt.attempt_item i
                JOIN attempt.attempt a ON a.id = i.attempt_id
                JOIN attempt.attempt_part p ON p.id = i.part_id
                LEFT JOIN attempt.essay_grade g ON g.attempt_item_id = i.id AND g.superseded_by IS NULL
                WHERE
                """ + " " + whereClause + """

                ORDER BY a.finished_at ASC NULLS LAST, p.ord ASC, i.ord ASC, i.id ASC
                LIMIT :limit OFFSET :offset
                """;

        List<QueueRow> rows = jdbc.query(dataSql, params, (rs, rowNum) -> mapQueueRow(rs));
        Set<UUID> graderIds = new LinkedHashSet<>();
        for (QueueRow row : rows) {
            if (row.graderId() != null) {
                graderIds.add(row.graderId());
            }
        }
        Map<UUID, UserSummary> gradersById = graderIds.isEmpty() ? Map.of() : userDirectory.findByIds(graderIds);

        List<GradingQueueItemView> views = new ArrayList<>(rows.size());
        for (QueueRow row : rows) {
            GradingQueueState itemState = resolveQueueState(
                    row.outcome(), row.gradeState(), row.lastActivityAt(), idleCutoff);
            boolean claimExpired = itemState == GradingQueueState.WAITING && row.graderId() != null;
            String graderName = resolveGraderName(row.graderId(), gradersById);
            views.add(new GradingQueueItemView(
                    row.itemId(),
                    row.attemptId(),
                    row.examId(),
                    row.examName(),
                    row.examKindCode(),
                    row.partCode(),
                    row.partName(),
                    row.displayNo(),
                    row.pointsMax(),
                    row.submittedAt(),
                    itemState,
                    row.regrading(),
                    row.graderId(),
                    graderName,
                    claimExpired,
                    row.claimedAt(),
                    row.lastActivityAt(),
                    row.finalizedAt(),
                    row.earnedPoints()));
        }
        int totalPages = (int) ((totalItems + safeSize - 1) / safeSize);
        return new PageResponse<>(views, safePage, safeSize, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public GradingQueueCountsResponse counts(String exam, String kind, String from, String to) {
        entityManager.flush();
        DateBound fromBound = AttemptDateFilter.parseBound(from, true);
        DateBound toBound = AttemptDateFilter.parseBound(to, false);

        Instant now = clock.instant();
        Instant idleCutoff = now.minus(IDLE_TIMEOUT);

        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildFilterWhereClause(null, exam, kind, fromBound, toBound, idleCutoff, params);
        params.addValue("idleCutoff", Timestamp.from(idleCutoff));

        String sql = """
                SELECT COALESCE(COUNT(*) FILTER (WHERE %s), 0) AS waiting_count,
                       COALESCE(COUNT(*) FILTER (WHERE %s), 0) AS in_progress_count,
                       COALESCE(COUNT(*) FILTER (WHERE %s), 0) AS done_count
                FROM attempt.attempt_item i
                JOIN attempt.attempt a ON a.id = i.attempt_id
                LEFT JOIN attempt.essay_grade g ON g.attempt_item_id = i.id AND g.superseded_by IS NULL
                WHERE %s
                """.formatted(WAITING_PREDICATE, IN_PROGRESS_PREDICATE, DONE_PREDICATE, whereClause);

        return jdbc.queryForObject(sql, params, (rs, rowNum) -> {
            long waiting = rs.getLong("waiting_count");
            long inProgress = rs.getLong("in_progress_count");
            long done = rs.getLong("done_count");
            return new GradingQueueCountsResponse(waiting, inProgress, done, waiting + inProgress + done);
        });
    }

    @Transactional(readOnly = true)
    public GradingItemDetailResponse getItemDetail(UUID itemId) {
        AttemptItem item = attemptItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("GRADING_ITEM_NOT_FOUND", null));
        Attempt attempt = attemptRepository.findById(item.getAttemptId())
                .orElseThrow(() -> new NotFoundException("GRADING_ITEM_NOT_FOUND", null));
        AttemptPart part = attemptPartRepository.findById(item.getPartId())
                .orElseThrow(() -> new NotFoundException("GRADING_ITEM_NOT_FOUND", null));
        requireEligibleItemForRead(attempt, item);

        AttemptItem parentItem = item.getParentItemId() != null
                ? attemptItemRepository.findById(item.getParentItemId()).orElse(null)
                : null;

        Map<VersionKey, JsonNode> snapshots = loadSnapshots(item, parentItem);
        JsonNode itemSnapshot = snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        JsonNode parentSnapshot = parentItem != null
                ? snapshots.get(new VersionKey(parentItem.getQuestionId(), parentItem.getQuestionVersionNo()))
                : null;

        String content = textOrNull(itemSnapshot, "content");
        String passage = parentSnapshot != null ? textOrNull(parentSnapshot.path("body"), "passage") : null;
        JsonNode body = itemSnapshot != null ? itemSnapshot.path("body") : null;
        String gradingGuide = textOrNull(body, "gradingGuide");
        String sampleAnswer = textOrNull(body, "sampleAnswer");
        Integer wordLimit = intOrNull(body, "wordLimit");
        List<RubricCriterionView> rubric = parseRubric(body);

        String answerHtml = (item.getAnswer() instanceof AnswerValue.Essay essay) ? essay.html() : null;
        int wordCount = (item.getAnswer() instanceof AnswerValue.Essay essay && essay.wordCount() != null)
                ? essay.wordCount()
                : AnswerValueValidator.countWords(answerHtml);

        Optional<EssayGrade> activeOpt = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(itemId);
        List<EssayGrade> historyEntities =
                essayGradeRepository.findByAttemptItemIdAndSupersededByIsNotNullOrderByClaimedAtAscIdAsc(itemId);

        Set<UUID> graderIds = new LinkedHashSet<>();
        activeOpt.ifPresent(g -> graderIds.add(g.getGraderId()));
        for (EssayGrade h : historyEntities) {
            graderIds.add(h.getGraderId());
        }
        Map<UUID, UserSummary> gradersById = graderIds.isEmpty() ? Map.of() : userDirectory.findByIds(graderIds);

        Instant now = clock.instant();
        Instant idleCutoff = now.minus(IDLE_TIMEOUT);

        EssayGrade active = activeOpt.orElse(null);
        GradingQueueState queueState = resolveQueueState(
                item.getOutcome().name(),
                active != null ? active.getState().name() : null,
                active != null ? active.getLastActivityAt() : null,
                idleCutoff);

        EssayGradeView activeView = active != null
                ? toGradeView(active, resolveGraderName(active.getGraderId(), gradersById), idleCutoff)
                : null;
        List<EssayGradeView> historyViews = historyEntities.stream()
                .map(h -> toGradeView(h, resolveGraderName(h.getGraderId(), gradersById), idleCutoff))
                .toList();

        return new GradingItemDetailResponse(
                item.getId(),
                attempt.getId(),
                attempt.getExamId(),
                attempt.getExamName(),
                attempt.getExamKindCode(),
                part.getPartCode(),
                part.getPartName(),
                item.getDisplayNo(),
                attempt.getFinishedAt(),
                attempt.isRegrading(),
                item.getQuestionId(),
                item.getQuestionVersionNo(),
                item.getPointsMax(),
                queueState,
                content,
                passage,
                answerHtml,
                wordCount,
                wordLimit,
                gradingGuide,
                rubric,
                sampleAnswer,
                activeView,
                historyViews);
    }

    @Transactional
    public EssayGradeView claim(UUID itemId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        LockedItem locked = lockAttemptAndItem(itemId);
        Attempt attempt = locked.attempt();
        AttemptItem item = locked.item();
        requireGradableItem(attempt, item);

        Instant now = clock.instant();
        Instant idleCutoff = now.minus(IDLE_TIMEOUT);
        Optional<EssayGrade> activeOpt = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(itemId);

        if (activeOpt.isPresent()) {
            EssayGrade active = activeOpt.get();
            if (active.getState() == EssayGradeState.FINAL || item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
                throw new ConflictException("ESSAY_ALREADY_FINALIZED", null);
            }
            if (active.getGraderId().equals(actor.id())) {
                active.touch(now);
                EssayGrade saved = essayGradeRepository.saveAndFlush(active);
                return toGradeView(saved, resolveActorName(actor), idleCutoff);
            }
            Instant staleDeadline = active.getLastActivityAt().plus(IDLE_TIMEOUT);
            if (!now.isAfter(staleDeadline)) {
                throw claimedException(active);
            }
            UUID newId = Ids.newId();
            active.markSuperseded(newId, now);
            essayGradeRepository.saveAndFlush(active);

            EssayGrade takeover = EssayGrade.claim(
                    newId, itemId, actor.id(), item.getQuestionVersionNo(), now);
            EssayGrade saved = essayGradeRepository.saveAndFlush(takeover);
            return toGradeView(saved, resolveActorName(actor), idleCutoff);
        }

        if (item.getOutcome() != ItemOutcome.PENDING_REVIEW) {
            throw new ConflictException("ESSAY_ALREADY_FINALIZED", null);
        }
        EssayGrade claimed = EssayGrade.claim(
                Ids.newId(), itemId, actor.id(), item.getQuestionVersionNo(), now);
        EssayGrade saved = essayGradeRepository.saveAndFlush(claimed);
        return toGradeView(saved, resolveActorName(actor), idleCutoff);
    }

    @Transactional
    public EssayGradeView saveDraft(UUID itemId, SaveEssayDraftRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        LockedItem locked = lockAttemptAndItem(itemId);
        Attempt attempt = locked.attempt();
        AttemptItem item = locked.item();
        requireGradableItem(attempt, item);

        Instant now = clock.instant();
        Instant idleCutoff = now.minus(IDLE_TIMEOUT);
        EssayGrade active = requireOwnedActiveDraft(itemId, actor.id());

        if (!Objects.equals(active.getVersion(), request.expectedVersion())) {
            throw new VersionConflictException("VERSION_CONFLICT", null);
        }
        if (request.points() != null && request.points().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("ESSAY_POINTS_OUT_OF_RANGE", null);
        }
        List<EssayCriterionScore> criteriaScores = toCriteriaScores(request.criteria());
        if (criteriaScores != null) {
            for (EssayCriterionScore c : criteriaScores) {
                if (c.points() != null && c.points().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
                }
            }
        }

        active.saveDraft(criteriaScores, request.points(), request.comment(), now);
        EssayGrade saved = essayGradeRepository.saveAndFlush(active);
        return toGradeView(saved, resolveActorName(actor), idleCutoff);
    }

    @Transactional
    public FinalizeEssayResponse finalizeGrade(UUID itemId, FinalizeEssayRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        LockedItem locked = lockAttemptAndItem(itemId);
        Attempt attempt = locked.attempt();
        AttemptItem item = locked.item();
        requireGradableItem(attempt, item);

        Instant now = clock.instant();
        Instant idleCutoff = now.minus(IDLE_TIMEOUT);
        EssayGrade active = requireOwnedActiveDraft(itemId, actor.id());

        if (request != null
                && request.expectedVersion() != null
                && !Objects.equals(active.getVersion(), request.expectedVersion())) {
            throw new VersionConflictException("VERSION_CONFLICT", null);
        }

        List<EssayCriterionScore> effectiveCriteria = (request != null && request.criteria() != null)
                ? toCriteriaScores(request.criteria())
                : active.getCriteria();
        BigDecimal effectivePoints = (request != null && request.points() != null)
                ? request.points()
                : active.getPoints();
        String effectiveComment = (request != null && request.comment() != null)
                ? request.comment()
                : active.getComment();

        validateFinalizeScores(item, effectivePoints, effectiveCriteria);

        active.finalizeGrade(effectiveCriteria, effectivePoints, effectiveComment, now);
        EssayGrade savedGrade = essayGradeRepository.saveAndFlush(active);

        ItemOutcome itemOutcome;
        if (effectivePoints.compareTo(item.getPointsMax()) == 0) {
            itemOutcome = ItemOutcome.CORRECT;
        } else if (effectivePoints.compareTo(BigDecimal.ZERO) == 0) {
            itemOutcome = ItemOutcome.WRONG;
        } else {
            itemOutcome = ItemOutcome.PARTIAL;
        }

        Map<String, Object> gradingDetail = buildFinalizedGradingDetail(
                item, savedGrade.getId(), effectivePoints, effectiveComment, effectiveCriteria);
        item.recordGrading(effectivePoints, itemOutcome, gradingDetail);
        attemptItemRepository.saveAndFlush(item);

        boolean wasRegrading = attempt.isRegrading();
        attemptGradingService.reaggregateLockedAttempt(attempt, now);

        auditRecorder.record(AuditEvent.builder("attempt", "ESSAY_GRADED")
                .target("ATTEMPT_ITEM", itemId.toString(), attempt.getExamName())
                .after(Map.of(
                        "attemptId", attempt.getId().toString(),
                        "essayGradeId", savedGrade.getId().toString(),
                        "points", effectivePoints,
                        "attemptState", attempt.getState().name()))
                .build());

        if (attempt.getState() == AttemptState.SCORED) {
            attemptEventRepository.save(new AttemptEvent(
                    Ids.newId(),
                    attempt.getId(),
                    wasRegrading ? AttemptEventType.REGRADED : AttemptEventType.GRADED,
                    null,
                    item.getPartId(),
                    now,
                    Map.of(
                            "state", attempt.getState().name(),
                            "pendingReview", false,
                            "essayGradeId", savedGrade.getId().toString())));
        }

        EssayGradeView gradeView = toGradeView(savedGrade, resolveActorName(actor), idleCutoff);
        return new FinalizeEssayResponse(
                gradeView,
                attempt.getId(),
                attempt.getState(),
                attempt.getRawScore(),
                attempt.getDisplayScore(),
                attempt.getMaxScore(),
                attempt.getGradedAt());
    }

    @Transactional
    public EssayGradeView reopen(UUID itemId, ReopenEssayRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String trimmedReason = request.reason().trim();
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        LockedItem locked = lockAttemptAndItem(itemId);
        Attempt attempt = locked.attempt();
        AttemptItem item = locked.item();
        requireGradableItem(attempt, item);

        EssayGrade active = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(itemId)
                .orElseThrow(() -> new ConflictException("ESSAY_NOT_FINALIZED", null));
        if (active.getState() != EssayGradeState.FINAL) {
            throw new ConflictException("ESSAY_NOT_FINALIZED", null);
        }

        Instant now = clock.instant();
        Instant idleCutoff = now.minus(IDLE_TIMEOUT);
        UUID newId = Ids.newId();
        UUID oldId = active.getId();
        BigDecimal oldPoints = active.getPoints();

        active.markSuperseded(newId, now);
        essayGradeRepository.saveAndFlush(active);

        EssayGrade reopenedDraft = EssayGrade.reopenFrom(
                newId, itemId, actor.id(), item.getQuestionVersionNo(), active, trimmedReason, now);
        EssayGrade savedDraft = essayGradeRepository.saveAndFlush(reopenedDraft);

        item.reopenManualEssay(detailKeepingWordCount(item));
        attemptItemRepository.saveAndFlush(item);

        attempt.reopenForRegrading(now);
        attemptGradingService.reaggregateLockedAttempt(attempt, now);

        Map<String, Object> beforeMap = new LinkedHashMap<>();
        beforeMap.put("essayGradeId", oldId.toString());
        beforeMap.put("points", oldPoints);
        beforeMap.put("state", EssayGradeState.FINAL.name());

        Map<String, Object> afterMap = new LinkedHashMap<>();
        afterMap.put("attemptId", attempt.getId().toString());
        afterMap.put("essayGradeId", savedDraft.getId().toString());
        afterMap.put("attemptState", attempt.getState().name());
        afterMap.put("regrading", attempt.isRegrading());

        auditRecorder.record(AuditEvent.builder("attempt", "ESSAY_REOPENED")
                .target("ATTEMPT_ITEM", itemId.toString(), attempt.getExamName())
                .before(beforeMap)
                .after(afterMap)
                .reason(trimmedReason)
                .build());

        return toGradeView(savedDraft, resolveActorName(actor), idleCutoff);
    }

    private record LockedItem(Attempt attempt, AttemptItem item) {}

    private LockedItem lockAttemptAndItem(UUID itemId) {
        UUID attemptId = attemptItemRepository.findAttemptIdById(itemId)
                .orElseThrow(() -> new NotFoundException("GRADING_ITEM_NOT_FOUND", null));
        Attempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new NotFoundException("GRADING_ITEM_NOT_FOUND", null));
        AttemptItem item = attemptItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new NotFoundException("GRADING_ITEM_NOT_FOUND", null));
        return new LockedItem(attempt, item);
    }

    private static void requireEligibleItemForRead(Attempt attempt, AttemptItem item) {
        if (!isEligibleEssayItem(attempt, item)) {
            throw new NotFoundException("GRADING_ITEM_NOT_FOUND", null);
        }
    }

    private static void requireGradableItem(Attempt attempt, AttemptItem item) {
        if (!isEligibleEssayItem(attempt, item)) {
            throw new ConflictException("ESSAY_NOT_GRADABLE", null);
        }
    }

    private static boolean isEligibleEssayItem(Attempt attempt, AttemptItem item) {
        return item.getQuestionType() == QuestionType.ESSAY
                && item.isInScope()
                && item.getOutcome() != null
                && item.getOutcome() != ItemOutcome.BLANK
                && (attempt.getState() == AttemptState.PENDING_REVIEW
                        || attempt.getState() == AttemptState.SCORED);
    }

    private EssayGrade requireOwnedActiveDraft(UUID itemId, UUID actorId) {
        EssayGrade active = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(itemId)
                .orElseThrow(() -> new ConflictException("GRADING_NOT_CLAIMED", null));
        if (active.getState() == EssayGradeState.FINAL) {
            throw new ConflictException("ESSAY_ALREADY_FINALIZED", null);
        }
        if (!active.getGraderId().equals(actorId)) {
            throw claimedException(active);
        }
        return active;
    }

    private GradingClaimedException claimedException(EssayGrade active) {
        String holderName = userDirectory.findById(active.getGraderId())
                .map(UserSummary::displayName)
                .orElse(null);
        return new GradingClaimedException(
                active.getGraderId(), holderName, active.getClaimedAt(), active.getLastActivityAt());
    }

    private void validateFinalizeScores(
            AttemptItem item, BigDecimal effectivePoints, List<EssayCriterionScore> effectiveCriteria) {
        if (effectivePoints == null
                || effectivePoints.compareTo(BigDecimal.ZERO) < 0
                || effectivePoints.compareTo(item.getPointsMax()) > 0) {
            throw new BusinessRuleException("ESSAY_POINTS_OUT_OF_RANGE", null);
        }

        AttemptItem parentItem = item.getParentItemId() != null
                ? attemptItemRepository.findById(item.getParentItemId()).orElse(null)
                : null;
        Map<VersionKey, JsonNode> snapshots = loadSnapshots(item, parentItem);
        JsonNode itemSnapshot = snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        List<RubricCriterionView> rubric = itemSnapshot != null
                ? parseRubric(itemSnapshot.path("body"))
                : List.of();

        if (!rubric.isEmpty()) {
            if (effectiveCriteria == null || effectiveCriteria.isEmpty()) {
                throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
            }
            Map<String, RubricCriterionView> rubricById = new LinkedHashMap<>();
            for (RubricCriterionView r : rubric) {
                if (r.id() != null) {
                    rubricById.put(r.id(), r);
                }
            }
            Set<String> seen = new HashSet<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (EssayCriterionScore c : effectiveCriteria) {
                if (c == null || c.id() == null || c.id().isBlank() || c.points() == null) {
                    throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
                }
                RubricCriterionView def = rubricById.get(c.id());
                if (def == null || !seen.add(c.id())) {
                    throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
                }
                if (c.points().compareTo(BigDecimal.ZERO) < 0
                        || (def.points() != null && c.points().compareTo(def.points()) > 0)) {
                    throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
                }
                sum = sum.add(c.points());
            }
            if (seen.size() != rubricById.size()) {
                throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
            }
            if (sum.compareTo(effectivePoints) != 0) {
                throw new BusinessRuleException("ESSAY_CRITERIA_SUM_MISMATCH", null);
            }
        } else if (effectiveCriteria != null && !effectiveCriteria.isEmpty()) {
            Set<String> seen = new HashSet<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (EssayCriterionScore c : effectiveCriteria) {
                if (c == null || c.id() == null || c.id().isBlank() || c.points() == null) {
                    throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
                }
                if (!seen.add(c.id()) || c.points().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessRuleException("ESSAY_CRITERIA_INVALID", null);
                }
                sum = sum.add(c.points());
            }
            if (sum.compareTo(effectivePoints) != 0) {
                throw new BusinessRuleException("ESSAY_CRITERIA_SUM_MISMATCH", null);
            }
        }
    }

    private Map<String, Object> buildFinalizedGradingDetail(
            AttemptItem item,
            UUID essayGradeId,
            BigDecimal points,
            String comment,
            List<EssayCriterionScore> criteria) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Integer wordCount = extractWordCount(item);
        if (wordCount != null) {
            detail.put("wordCount", wordCount);
        }
        detail.put("exactFraction", ExactRational.of(points).toFractionString());
        detail.put("essayGradeId", essayGradeId.toString());
        if (comment != null) {
            detail.put("comment", comment);
        }
        if (criteria != null && !criteria.isEmpty()) {
            List<Map<String, Object>> criteriaMaps = new ArrayList<>(criteria.size());
            for (EssayCriterionScore c : criteria) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.id());
                m.put("points", c.points());
                if (c.comment() != null) {
                    m.put("comment", c.comment());
                }
                criteriaMaps.add(m);
            }
            detail.put("criteria", criteriaMaps);
        }
        return detail;
    }

    private static Map<String, Object> detailKeepingWordCount(AttemptItem item) {
        Integer wordCount = extractWordCount(item);
        if (wordCount == null) {
            return Map.of();
        }
        return Map.of("wordCount", wordCount);
    }

    private static Integer extractWordCount(AttemptItem item) {
        if (item.getGradingDetail() != null && item.getGradingDetail().get("wordCount") instanceof Number n) {
            return n.intValue();
        }
        if (item.getAnswer() instanceof AnswerValue.Essay essay) {
            return essay.wordCount() != null
                    ? essay.wordCount()
                    : AnswerValueValidator.countWords(essay.html());
        }
        return null;
    }

    private Map<VersionKey, JsonNode> loadSnapshots(AttemptItem item, AttemptItem parentItem) {
        Set<VersionKey> keys = new LinkedHashSet<>();
        keys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        if (parentItem != null) {
            keys.add(new VersionKey(parentItem.getQuestionId(), parentItem.getQuestionVersionNo()));
        }
        Map<VersionKey, JsonNode> map = new LinkedHashMap<>();
        for (Version version : questionSourcePort.loadVersions(List.copyOf(keys))) {
            if (version == null || version.snapshot() == null) {
                continue;
            }
            map.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    String idText = child.path("id").asText(null);
                    if (idText != null && !idText.isBlank()) {
                        try {
                            map.putIfAbsent(
                                    new VersionKey(UUID.fromString(idText.trim()), version.key().versionNo()),
                                    child);
                        } catch (IllegalArgumentException ignored) {
                            // Ignore malformed child UUID
                        }
                    }
                }
            }
        }
        return map;
    }

    private static String buildFilterWhereClause(
            GradingQueueState state,
            String exam,
            String kind,
            DateBound fromBound,
            DateBound toBound,
            Instant idleCutoff,
            MapSqlParameterSource params) {
        StringBuilder sb = new StringBuilder(BASE_ELIGIBLE_PREDICATES);
        if (state != null) {
            switch (state) {
                case WAITING -> {
                    params.addValue("idleCutoff", Timestamp.from(idleCutoff));
                    sb.append(" AND ").append(WAITING_PREDICATE);
                }
                case IN_PROGRESS -> {
                    params.addValue("idleCutoff", Timestamp.from(idleCutoff));
                    sb.append(" AND ").append(IN_PROGRESS_PREDICATE);
                }
                case DONE -> sb.append(" AND ").append(DONE_PREDICATE);
            }
        }
        if (exam != null && !exam.isBlank()) {
            String trimmed = exam.trim();
            UUID examId = parseUuidOrNull(trimmed);
            if (examId != null) {
                params.addValue("examId", examId);
                sb.append(" AND a.exam_id = :examId");
            } else {
                params.addValue(
                        "examPattern",
                        "%" + AttemptDateFilter.escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%");
                sb.append(" AND LOWER(a.exam_name) LIKE :examPattern ESCAPE '\\'");
            }
        }
        if (kind != null && !kind.isBlank()) {
            params.addValue("kind", kind.trim());
            sb.append(" AND a.exam_kind_code = :kind");
        }
        if (fromBound != null) {
            params.addValue("fromAt", Timestamp.from(fromBound.instant()));
            sb.append(" AND a.finished_at >= :fromAt");
        }
        if (toBound != null) {
            params.addValue("toAt", Timestamp.from(toBound.instant()));
            sb.append(toBound.exclusive() ? " AND a.finished_at < :toAt" : " AND a.finished_at <= :toAt");
        }
        return sb.toString();
    }

    private static GradingQueueState resolveQueueState(
            String outcome, String gradeState, Instant lastActivityAt, Instant idleCutoff) {
        if (!"PENDING_REVIEW".equals(outcome) && "FINAL".equals(gradeState)) {
            return GradingQueueState.DONE;
        }
        if ("PENDING_REVIEW".equals(outcome)
                && "DRAFT".equals(gradeState)
                && lastActivityAt != null
                && !lastActivityAt.isBefore(idleCutoff)) {
            return GradingQueueState.IN_PROGRESS;
        }
        return GradingQueueState.WAITING;
    }

    private static QueueRow mapQueueRow(ResultSet rs) throws SQLException {
        return new QueueRow(
                rs.getObject("item_id", UUID.class),
                rs.getObject("attempt_id", UUID.class),
                rs.getObject("exam_id", UUID.class),
                rs.getString("exam_name"),
                rs.getString("exam_kind_code"),
                rs.getString("part_code"),
                rs.getString("part_name"),
                rs.getObject("display_no", Integer.class),
                rs.getBigDecimal("points_max"),
                toInstant(rs.getTimestamp("submitted_at")),
                rs.getBoolean("regrading"),
                rs.getString("outcome"),
                rs.getBigDecimal("earned_points"),
                rs.getObject("grader_id", UUID.class),
                rs.getString("grade_state"),
                toInstant(rs.getTimestamp("claimed_at")),
                toInstant(rs.getTimestamp("last_activity_at")),
                toInstant(rs.getTimestamp("finalized_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private EssayGradeView toGradeView(EssayGrade g, String graderName, Instant idleCutoff) {
        boolean claimExpired = g.getSupersededBy() == null
                && g.getState() == EssayGradeState.DRAFT
                && g.getLastActivityAt().isBefore(idleCutoff);
        return new EssayGradeView(
                g.getId(),
                g.getAttemptItemId(),
                g.getGraderId(),
                graderName,
                g.getCriteria(),
                g.getPoints(),
                g.getComment(),
                g.getGradingGuideVersion(),
                g.getState(),
                g.getClaimedAt(),
                g.getLastActivityAt(),
                g.getFinalizedAt(),
                g.getReopenedReason(),
                g.getSupersededBy(),
                g.getVersion(),
                claimExpired);
    }

    private String resolveActorName(CurrentUser actor) {
        return userDirectory.findById(actor.id())
                .map(UserSummary::displayName)
                .orElse(actor.username());
    }

    private static String resolveGraderName(UUID graderId, Map<UUID, UserSummary> gradersById) {
        if (graderId == null) {
            return null;
        }
        UserSummary summary = gradersById.get(graderId);
        return summary != null ? summary.displayName() : null;
    }

    private static List<EssayCriterionScore> toCriteriaScores(List<EssayCriterionInput> inputs) {
        if (inputs == null) {
            return null;
        }
        List<EssayCriterionScore> list = new ArrayList<>(inputs.size());
        for (EssayCriterionInput in : inputs) {
            if (in != null) {
                list.add(new EssayCriterionScore(in.id(), in.points(), in.comment()));
            }
        }
        return List.copyOf(list);
    }

    private static List<RubricCriterionView> parseRubric(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return List.of();
        }
        JsonNode rubricNode = body.path("rubric");
        if (!rubricNode.isArray() || rubricNode.isEmpty()) {
            return List.of();
        }
        List<RubricCriterionView> result = new ArrayList<>(rubricNode.size());
        for (JsonNode r : rubricNode) {
            BigDecimal pts = null;
            JsonNode ptsNode = r.path("points");
            if (ptsNode.isNumber()) {
                pts = ptsNode.decimalValue();
            } else if (ptsNode.isTextual()) {
                try {
                    pts = new BigDecimal(ptsNode.asText().trim());
                } catch (NumberFormatException ignored) {
                    pts = null;
                }
            }
            result.add(new RubricCriterionView(
                    textOrNull(r, "id"),
                    textOrNull(r, "description"),
                    pts));
        }
        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).isNumber() ? node.get(field).asInt() : null;
    }

    private static UUID parseUuidOrNull(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
