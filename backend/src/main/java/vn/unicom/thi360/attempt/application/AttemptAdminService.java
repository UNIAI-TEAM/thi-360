package vn.unicom.thi360.attempt.application;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.AdminAttemptActionResponse;
import vn.unicom.thi360.attempt.api.AdminAttemptDetailResponse;
import vn.unicom.thi360.attempt.api.AdminAttemptDetailResponse.AdminAttemptEventView;
import vn.unicom.thi360.attempt.api.AdminAttemptDetailResponse.AdminAttemptItemView;
import vn.unicom.thi360.attempt.api.AdminAttemptDetailResponse.AdminAttemptPartView;
import vn.unicom.thi360.attempt.api.AdminAttemptSummary;
import vn.unicom.thi360.attempt.api.AttemptChoiceSummary;
import vn.unicom.thi360.attempt.api.CompensateAttemptRequest;
import vn.unicom.thi360.attempt.api.CompensateAttemptResponse;
import vn.unicom.thi360.attempt.api.FaultAttemptRequest;
import vn.unicom.thi360.attempt.application.AttemptDateFilter.DateBound;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.EssayGrade;
import vn.unicom.thi360.attempt.domain.EssayGradeState;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.attempt.infrastructure.EssayGradeRepository;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.spi.CompensationResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.UserDetailSummary;
import vn.unicom.thi360.identity.spi.UserDirectory.UserSummary;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Operational attempt lookup, regrade of {@code GRADING_ERROR}, system-fault marking/closing,
 * and idempotent attempt compensation (LT §12, F18, F20, TM §8.5, AC-12..AC-16).
 */
@Service
public class AttemptAdminService {

    private static final String COMPENSATE_OPERATION = "attempt.compensate";
    private static final String SYSTEM_FAULT_FLAG = "SYSTEM_FAULT";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final EssayGradeRepository essayGradeRepository;
    private final AttemptGradingService attemptGradingService;
    private final AttemptLifecycleService attemptLifecycleService;
    private final EntitlementPort entitlementPort;
    private final UserDirectory userDirectory;
    private final IdempotentCommand idempotentCommand;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public AttemptAdminService(
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            EssayGradeRepository essayGradeRepository,
            AttemptGradingService attemptGradingService,
            AttemptLifecycleService attemptLifecycleService,
            EntitlementPort entitlementPort,
            UserDirectory userDirectory,
            IdempotentCommand idempotentCommand,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.essayGradeRepository = Objects.requireNonNull(essayGradeRepository);
        this.attemptGradingService = Objects.requireNonNull(attemptGradingService);
        this.attemptLifecycleService = Objects.requireNonNull(attemptLifecycleService);
        this.entitlementPort = Objects.requireNonNull(entitlementPort);
        this.userDirectory = Objects.requireNonNull(userDirectory);
        this.idempotentCommand = Objects.requireNonNull(idempotentCommand);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAttemptSummary> listAttempts(
            String user,
            String exam,
            AttemptState state,
            String from,
            String to,
            Integer page,
            Integer size) {
        DateBound fromBound = AttemptDateFilter.parseBound(from, true);
        DateBound toBound = AttemptDateFilter.parseBound(to, false);

        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        UUID userIdFilter = null;
        if (user != null && !user.isBlank()) {
            String trimmedUser = user.trim();
            UUID parsed = parseUuidOrNull(trimmedUser);
            if (parsed != null) {
                userIdFilter = parsed;
            } else {
                Optional<UserSummary> found = userDirectory.findActiveByUsername(trimmedUser);
                if (found.isEmpty()) {
                    return new PageResponse<>(List.of(), safePage, safeSize, 0L, 0);
                }
                userIdFilter = found.get().id();
            }
        }

        Specification<Attempt> spec = buildSpec(userIdFilter, exam, state, fromBound, toBound);
        Page<Attempt> attemptPage = attemptRepository.findAll(spec, pageable);
        List<Attempt> rows = attemptPage.getContent();
        if (rows.isEmpty()) {
            return PageResponse.from(attemptPage.map(a -> toSummary(a, null, List.of(), false)));
        }

        Set<UUID> userIds = new LinkedHashSet<>();
        List<UUID> attemptIds = new ArrayList<>(rows.size());
        for (Attempt a : rows) {
            userIds.add(a.getUserId());
            attemptIds.add(a.getId());
        }

        Map<UUID, UserSummary> usersById = userIds.isEmpty() ? Map.of() : userDirectory.findByIds(userIds);
        Set<UUID> compensatedIds = new HashSet<>(
                attemptEventRepository.findAttemptIdsWithType(attemptIds, AttemptEventType.COMPENSATED));
        Map<UUID, List<AttemptPart>> partsByAttemptId = new HashMap<>(rows.size() * 2);
        for (AttemptPart part : attemptPartRepository.findByAttemptIdInOrderByOrdAsc(attemptIds)) {
            partsByAttemptId.computeIfAbsent(part.getAttemptId(), ignored -> new ArrayList<>()).add(part);
        }

        return PageResponse.from(attemptPage, a -> toSummary(
                a,
                usersById.get(a.getUserId()),
                partsByAttemptId.getOrDefault(a.getId(), List.of()),
                compensatedIds.contains(a.getId())));
    }

    @Transactional
    public AdminAttemptDetailResponse getAttemptDetail(UUID attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        List<AttemptEvent> events = attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attemptId);
        List<AttemptItem> items = loadItemsInPaperOrder(attemptId, parts);
        UserDetailSummary userDetail = userDirectory.findDetailById(attempt.getUserId()).orElse(null);
        boolean compensated = attemptEventRepository.existsByAttemptIdAndType(
                attemptId, AttemptEventType.COMPENSATED);

        auditRecorder.record(AuditEvent.builder("attempt", "ATTEMPT_VIEWED_BY_ADMIN")
                .target("ATTEMPT", attemptId.toString(), attempt.getExamName())
                .after(Map.of("state", attempt.getState().name()))
                .build());

        Map<String, AttemptChoiceSummary> choices = AttemptChoiceViews.merge(attempt, parts);
        List<AdminAttemptPartView> partViews = parts.stream()
                .map(p -> new AdminAttemptPartView(
                        p.getId(),
                        p.getPartCode(),
                        p.getPartName(),
                        p.getOrd(),
                        p.getState(),
                        p.getDurationMinutes(),
                        p.getQuestionCountRequired(),
                        p.getMaxPoints(),
                        p.getChoice(),
                        p.getChoiceConfirmedAt(),
                        p.getChoiceDeadlineAt(),
                        p.getStartedAt(),
                        p.getDeadlineAt(),
                        p.getClosedAt(),
                        p.getCloseReason(),
                        p.getGenerationTries(),
                        p.getGenerationError()))
                .toList();

        List<AdminAttemptEventView> eventViews = events.stream()
                .map(e -> new AdminAttemptEventView(
                        e.getId(), e.getType(), e.getSessionId(), e.getPartId(), e.getAt(), e.getData()))
                .toList();

        List<AdminAttemptItemView> itemViews = items.stream()
                .map(i -> new AdminAttemptItemView(
                        i.getId(),
                        i.getPartId(),
                        i.getParentItemId(),
                        i.getOrd(),
                        i.getDisplayNo(),
                        i.getUnitCount(),
                        i.getQuestionId(),
                        i.getQuestionVersionNo(),
                        i.getQuestionType(),
                        i.getPointsMax(),
                        i.isInScope(),
                        i.getAnswer(),
                        i.getAnswerSeq(),
                        i.getAnswerReceivedAt(),
                        i.isFlagged(),
                        i.getEarnedPoints(),
                        i.getOutcome(),
                        i.getGradingDetail()))
                .toList();

        return new AdminAttemptDetailResponse(
                attempt.getId(),
                attempt.getUserId(),
                userDetail != null ? userDetail.username() : null,
                userDetail != null ? userDetail.fullName() : null,
                userDetail != null ? userDetail.email() : null,
                userDetail != null ? userDetail.code() : null,
                attempt.getExamId(),
                attempt.getExamVersionNo(),
                attempt.getExamName(),
                attempt.getExamKindCode(),
                attempt.getScope(),
                attempt.getPartCode(),
                attempt.getTiming(),
                attempt.getNavigationMode(),
                choices,
                attempt.getAttemptNo(),
                attempt.getState(),
                attempt.isRegrading(),
                attempt.getEndReason(),
                attempt.getAbortReason(),
                attempt.getStartedAt(),
                attempt.getDeadlineAt(),
                attempt.getFinishedAt(),
                attempt.getGradedAt(),
                attempt.getGradingError(),
                attempt.getPracticeScoring(),
                attempt.getReferenceMax(),
                attempt.getMaxScore(),
                attempt.getRawScore(),
                attempt.getDisplayScore(),
                attempt.getResultView(),
                attempt.getEntitlementId(),
                attempt.getGrantId(),
                attempt.getEditSessionId(),
                attempt.getEditSessionSeenAt(),
                attempt.getLastPosition(),
                attempt.getFaultFlag(),
                attempt.getFaultReason(),
                compensated,
                attempt.getCreatedAt(),
                attempt.getUpdatedAt(),
                partViews,
                eventViews,
                itemViews);
    }

    @Transactional
    public AdminAttemptActionResponse regrade(UUID attemptId) {
        Attempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (attempt.getState() != AttemptState.GRADING_ERROR) {
            throw new ConflictException("ATTEMPT_NOT_GRADING_ERROR", null);
        }

        restoreFinalizedEssayItems(attemptId);
        attemptGradingService.regradeLockedAttempt(attempt);

        auditRecorder.record(AuditEvent.builder("attempt", "ATTEMPT_REGRADED")
                .target("ATTEMPT", attemptId.toString(), attempt.getExamName())
                .after(buildAttemptActionAuditAfter(attempt))
                .build());

        boolean compensated = attemptEventRepository.existsByAttemptIdAndType(
                attemptId, AttemptEventType.COMPENSATED);
        return toActionResponse(attempt, compensated);
    }

    @Transactional
    public AdminAttemptActionResponse markFault(UUID attemptId, FaultAttemptRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String trimmedReason = request.reason().trim();

        Attempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (attempt.getState() == AttemptState.STARTING || attempt.getState() == AttemptState.ABORTED) {
            throw new ConflictException("ATTEMPT_FAULT_NOT_ALLOWED", null);
        }
        if (SYSTEM_FAULT_FLAG.equals(attempt.getFaultFlag())) {
            throw new ConflictException("ATTEMPT_ALREADY_FAULTED", null);
        }

        Instant now = clock.instant();
        if (attempt.getState() == AttemptState.IN_PROGRESS) {
            attemptLifecycleService.closeAttempt(attemptId, EndReason.FAULT_CLOSED, now);
        }
        attempt.markFault(SYSTEM_FAULT_FLAG, trimmedReason, now);
        attemptRepository.saveAndFlush(attempt);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("faultFlag", attempt.getFaultFlag());
        after.put("state", attempt.getState().name());
        if (attempt.getEndReason() != null) {
            after.put("endReason", attempt.getEndReason().name());
        }
        auditRecorder.record(AuditEvent.builder("attempt", "ATTEMPT_FAULT_MARKED")
                .target("ATTEMPT", attemptId.toString(), attempt.getExamName())
                .reason(trimmedReason)
                .after(after)
                .build());

        boolean compensated = attemptEventRepository.existsByAttemptIdAndType(
                attemptId, AttemptEventType.COMPENSATED);
        return toActionResponse(attempt, compensated);
    }

    public CompensateAttemptResponse compensate(
            UUID attemptId, String idempotencyKey, CompensateAttemptRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        CompensateAttemptRequest normalized = new CompensateAttemptRequest(request.reason().trim());
        return idempotentCommand.execute(
                idempotencyKey,
                COMPENSATE_OPERATION,
                attemptId,
                normalized,
                CompensateAttemptResponse.class,
                () -> executeCompensate(attemptId, normalized.reason()));
    }

    private CompensateAttemptResponse executeCompensate(UUID attemptId, String reason) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Attempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!SYSTEM_FAULT_FLAG.equals(attempt.getFaultFlag())) {
            throw new ConflictException("ATTEMPT_NOT_FAULTED", null);
        }

        CompensationResult result = entitlementPort.compensate(attemptId, actor.id(), reason);
        Instant now = clock.instant();
        attemptEventRepository.save(new AttemptEvent(
                Ids.newId(),
                attemptId,
                AttemptEventType.COMPENSATED,
                null,
                null,
                now,
                Map.of(
                        "entitlementId", result.entitlementId().toString(),
                        "grantId", result.grantId().toString(),
                        "adminId", actor.id().toString(),
                        "reason", reason)));

        auditRecorder.record(AuditEvent.builder("attempt", "ATTEMPT_COMPENSATED")
                .target("ATTEMPT", attemptId.toString(), attempt.getExamName())
                .reason(reason)
                .after(Map.of(
                        "entitlementId", result.entitlementId().toString(),
                        "grantId", result.grantId().toString()))
                .build());

        return new CompensateAttemptResponse(attemptId, result.entitlementId(), result.grantId(), true);
    }

    private void restoreFinalizedEssayItems(UUID attemptId) {
        List<EssayGrade> finalGrades =
                essayGradeRepository.findActiveByAttemptIdAndState(attemptId, EssayGradeState.FINAL);
        if (finalGrades.isEmpty()) {
            return;
        }
        Map<UUID, EssayGrade> gradeByItemId = new LinkedHashMap<>();
        for (EssayGrade grade : finalGrades) {
            gradeByItemId.put(grade.getAttemptItemId(), grade);
        }
        List<AttemptItem> items = attemptItemRepository.findByAttemptIdAndIdInOrderByIdAscForUpdate(
                attemptId, gradeByItemId.keySet());
        List<AttemptItem> updated = new ArrayList<>();
        for (AttemptItem item : items) {
            EssayGrade g = gradeByItemId.get(item.getId());
            if (g == null || g.getPoints() == null) {
                continue;
            }
            ItemOutcome expectedOutcome = EssayGradingService.resolveEssayOutcome(g.getPoints(), item.getPointsMax());
            Map<String, Object> expectedDetail = EssayGradingService.buildFinalizedGradingDetail(
                    item, g.getId(), g.getPoints(), g.getComment(), g.getCriteria());
            if (!item.isFinalizedManualEssay()
                    || item.getEarnedPoints() == null
                    || item.getEarnedPoints().compareTo(g.getPoints()) != 0
                    || item.getOutcome() != expectedOutcome) {
                item.recordGrading(g.getPoints(), expectedOutcome, expectedDetail);
                updated.add(item);
            }
        }
        if (!updated.isEmpty()) {
            attemptItemRepository.saveAllAndFlush(updated);
        }
    }

    private static Specification<Attempt> buildSpec(
            UUID userIdFilter,
            String exam,
            AttemptState state,
            DateBound fromBound,
            DateBound toBound) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userIdFilter != null) {
                predicates.add(cb.equal(root.get("userId"), userIdFilter));
            }
            if (exam != null && !exam.isBlank()) {
                String trimmed = exam.trim();
                UUID examUuid = parseUuidOrNull(trimmed);
                if (examUuid != null) {
                    predicates.add(cb.equal(root.get("examId"), examUuid));
                } else {
                    String pattern = "%" + AttemptDateFilter.escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
                    predicates.add(cb.like(cb.lower(root.get("examName")), pattern, '\\'));
                }
            }
            if (state != null) {
                predicates.add(cb.equal(root.get("state"), state));
            }
            Expression<Instant> effectiveTime = cb.coalesce(root.get("startedAt"), root.get("createdAt"));
            if (fromBound != null) {
                predicates.add(cb.greaterThanOrEqualTo(effectiveTime, fromBound.instant()));
            }
            if (toBound != null) {
                if (toBound.exclusive()) {
                    predicates.add(cb.lessThan(effectiveTime, toBound.instant()));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(effectiveTime, toBound.instant()));
                }
            }
            if (query != null
                    && query.getResultType() != Long.class
                    && query.getResultType() != long.class) {
                query.orderBy(
                        cb.desc(effectiveTime),
                        cb.desc(root.get("createdAt")),
                        cb.desc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<AttemptItem> loadItemsInPaperOrder(UUID attemptId, List<AttemptPart> parts) {
        List<AttemptItem> items = new ArrayList<>(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId));
        Map<UUID, Integer> partOrdById = new HashMap<>(parts.size());
        for (AttemptPart p : parts) {
            partOrdById.put(p.getId(), p.getOrd());
        }
        items.sort(Comparator
                .comparingInt((AttemptItem i) -> partOrdById.getOrDefault(i.getPartId(), Integer.MAX_VALUE))
                .thenComparingInt(AttemptItem::getOrd));
        return items;
    }

    private static AdminAttemptSummary toSummary(
            Attempt attempt, UserSummary user, List<AttemptPart> parts, boolean compensated) {
        return new AdminAttemptSummary(
                attempt.getId(),
                attempt.getUserId(),
                user != null ? user.username() : null,
                user != null ? user.fullName() : null,
                attempt.getExamId(),
                attempt.getExamVersionNo(),
                attempt.getExamName(),
                attempt.getExamKindCode(),
                attempt.getScope(),
                attempt.getPartCode(),
                AttemptChoiceViews.merge(attempt, parts),
                attempt.getAttemptNo(),
                attempt.getState(),
                attempt.isRegrading(),
                attempt.getEndReason(),
                attempt.getAbortReason(),
                attempt.getStartedAt(),
                attempt.getDeadlineAt(),
                attempt.getFinishedAt(),
                attempt.getGradedAt(),
                attempt.getRawScore(),
                attempt.getDisplayScore(),
                attempt.getMaxScore(),
                attempt.getFaultFlag(),
                attempt.getFaultReason(),
                compensated,
                attempt.getCreatedAt());
    }

    private static AdminAttemptActionResponse toActionResponse(Attempt attempt, boolean compensated) {
        return new AdminAttemptActionResponse(
                attempt.getId(),
                attempt.getState(),
                attempt.getRawScore(),
                attempt.getDisplayScore(),
                attempt.getMaxScore(),
                attempt.getGradedAt(),
                attempt.getFaultFlag(),
                attempt.getFaultReason(),
                compensated);
    }

    private static Map<String, Object> buildAttemptActionAuditAfter(Attempt attempt) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("state", attempt.getState().name());
        if (attempt.getRawScore() != null) {
            after.put("rawScore", attempt.getRawScore());
        }
        if (attempt.getDisplayScore() != null) {
            after.put("displayScore", attempt.getDisplayScore());
        }
        if (attempt.getMaxScore() != null) {
            after.put("maxScore", attempt.getMaxScore());
        }
        return after;
    }

    private static UUID parseUuidOrNull(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
