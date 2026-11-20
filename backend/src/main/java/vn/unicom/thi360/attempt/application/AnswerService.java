package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.api.AttemptPosition;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerResult;
import vn.unicom.thi360.attempt.api.FinalAnswerStatus;
import vn.unicom.thi360.attempt.api.FlagChange;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.api.SaveAnswersResponse;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Handles draft answer saving ({@code PUT /answers}), flags, position persistence, and implements
 * {@link FinalAnswerWriter} for atomic final-answer writes inside {@link AttemptLifecycleService}
 * (LT §6.3, §7.1, F8, F9, AC-1..AC-11).
 */
@Service
public class AnswerService implements FinalAnswerWriter {

    private static final String REASON_NOT_IN_SCOPE = "NOT_IN_SCOPE";
    private static final String REASON_INVALID_ANSWER = "INVALID_ANSWER";
    private static final String REASON_PART_CLOSED = "PART_CLOSED";
    private static final String REASON_TIME_UP = "TIME_UP";

    private final AttemptAccessGate accessGate;
    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final QuestionSourcePort questionSourcePort;
    private final AnswerValueValidator answerValueValidator;
    private final AttemptSaveRateLimiter attemptSaveRateLimiter;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final AttemptSessionChecker attemptSessionChecker;
    private final ObjectProvider<AttemptLifecycleService> lifecycleServiceProvider;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public AnswerService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            QuestionSourcePort questionSourcePort,
            AnswerValueValidator answerValueValidator,
            AttemptSaveRateLimiter attemptSaveRateLimiter,
            OverdueAttemptCloser overdueAttemptCloser,
            AttemptSessionChecker attemptSessionChecker,
            ObjectProvider<AttemptLifecycleService> lifecycleServiceProvider,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.answerValueValidator = Objects.requireNonNull(answerValueValidator);
        this.attemptSaveRateLimiter = Objects.requireNonNull(attemptSaveRateLimiter);
        this.overdueAttemptCloser = Objects.requireNonNull(overdueAttemptCloser);
        this.attemptSessionChecker = Objects.requireNonNull(attemptSessionChecker);
        this.lifecycleServiceProvider = Objects.requireNonNull(lifecycleServiceProvider);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
    }

    private record ChangeInput(UUID itemId, long seq, AnswerValue answer) {}

    /** Saves draft answers, flags, and viewing position for the active edit session (LT §7.1). */
    public SaveAnswersResponse saveAnswers(
            UUID attemptId, UUID sessionId, SaveAnswersRequest request) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null || !attemptRepository.existsByIdAndUserId(attemptId, userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        attemptSaveRateLimiter.checkLimit(attemptId);
        overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());

        SaveAnswersRequest normalized =
                request == null
                        ? new SaveAnswersRequest(List.of(), List.of(), null)
                        : request;

        try {
            return txTemplate.execute(
                    status -> executeSaveAnswers(attemptId, userId, sessionId, normalized));
        } catch (ConflictException ex) {
            overdueAttemptCloser.closeIfOverdue(attemptId, clock.instant());
            throw ex;
        }
    }

    private SaveAnswersResponse executeSaveAnswers(
            UUID attemptId, UUID userId, UUID sessionId, SaveAnswersRequest request) {
        Attempt attempt =
                attemptRepository
                        .findByIdForUpdate(attemptId)
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        attemptSessionChecker.requireEditSession(attempt, sessionId);

        List<AttemptPart> parts =
                attemptPartRepository.findByAttemptIdOrderByOrdAscForUpdate(attemptId);
        Instant lockNow = clock.instant();
        if (attempt.getState() == AttemptState.IN_PROGRESS) {
            lifecycleServiceProvider.getObject().advanceOverdueInLock(attempt, parts, lockNow);
        }

        Set<UUID> itemIdsToLock = new LinkedHashSet<>();
        for (AnswerChange change : request.changes()) {
            if (change.itemId() != null) {
                itemIdsToLock.add(change.itemId());
            }
        }
        for (FlagChange flag : request.flags()) {
            if (flag.itemId() != null) {
                itemIdsToLock.add(flag.itemId());
            }
        }

        Map<UUID, AttemptItem> itemsById = lockItemsById(attemptId, itemIdsToLock);
        Map<UUID, AttemptPart> partsById = indexPartsById(parts);
        Set<AttemptItem> dirtyItems = new LinkedHashSet<>();

        List<ChangeInput> changeInputs =
                request.changes().stream()
                        .map(c -> new ChangeInput(c.itemId(), c.seq(), c.answer()))
                        .toList();
        List<FinalAnswerResult> results =
                applyAnswerChanges(
                        attempt,
                        null,
                        sessionId,
                        partsById,
                        itemsById,
                        dirtyItems,
                        changeInputs,
                        lockNow);

        if (attempt.getState() == AttemptState.IN_PROGRESS) {
            for (FlagChange flag : request.flags()) {
                AttemptItem item = itemsById.get(flag.itemId());
                if (item == null || !item.isInScope()) {
                    continue;
                }
                AttemptPart part = partsById.get(item.getPartId());
                if (resolveClosedReason(attempt, part, lockNow) == null
                        && item.isFlagged() != flag.flagged()) {
                    item.setFlagged(flag.flagged());
                    dirtyItems.add(item);
                }
            }

            if (request.position() != null) {
                updatePositionIfValid(attempt, partsById, itemsById, request.position(), lockNow);
            }
        }

        if (!dirtyItems.isEmpty()) {
            attemptItemRepository.saveAllAndFlush(dirtyItems);
        }
        return new SaveAnswersResponse(results, lockNow);
    }

    @Override
    public List<FinalAnswerResult> writeFinalAnswers(
            Attempt attempt,
            AttemptPart targetPart,
            List<FinalAnswerChange> changes,
            Instant receivedAt) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        Objects.requireNonNull(attempt, "attempt must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");

        Set<UUID> itemIdsToLock = new LinkedHashSet<>();
        for (FinalAnswerChange change : changes) {
            if (change != null && change.itemId() != null) {
                itemIdsToLock.add(change.itemId());
            }
        }

        Map<UUID, AttemptPart> partsById =
                indexPartsById(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()));
        Map<UUID, AttemptItem> itemsById = lockItemsById(attempt.getId(), itemIdsToLock);
        Set<AttemptItem> dirtyItems = new LinkedHashSet<>();

        List<ChangeInput> changeInputs = new ArrayList<>(changes.size());
        for (FinalAnswerChange c : changes) {
            if (c != null) {
                changeInputs.add(new ChangeInput(c.itemId(), c.seq(), c.answer()));
            }
        }

        List<FinalAnswerResult> results =
                applyAnswerChanges(
                        attempt,
                        targetPart,
                        attempt.getEditSessionId(),
                        partsById,
                        itemsById,
                        dirtyItems,
                        changeInputs,
                        receivedAt);

        if (!dirtyItems.isEmpty()) {
            attemptItemRepository.saveAllAndFlush(dirtyItems);
        }
        return results;
    }

    private List<FinalAnswerResult> applyAnswerChanges(
            Attempt attempt,
            AttemptPart targetPart,
            UUID sessionId,
            Map<UUID, AttemptPart> partsById,
            Map<UUID, AttemptItem> itemsById,
            Set<AttemptItem> dirtyItems,
            List<ChangeInput> changes,
            Instant receivedAt) {
        if (changes.isEmpty()) {
            return List.of();
        }

        List<AttemptItem> itemsNeedingSnapshots = new ArrayList<>();
        for (ChangeInput change : changes) {
            AttemptItem item = itemsById.get(change.itemId());
            if (item == null || !item.isInScope()) {
                continue;
            }
            if (targetPart != null && !item.getPartId().equals(targetPart.getId())) {
                continue;
            }
            AttemptPart part = partsById.get(item.getPartId());
            if (resolveClosedReason(attempt, part, receivedAt) != null) {
                continue;
            }
            if (change.seq() > item.getAnswerSeq()
                    && change.answer() != null
                    && item.getQuestionType() != QuestionType.QUESTION_SET) {
                itemsNeedingSnapshots.add(item);
            }
        }

        Map<VersionKey, JsonNode> snapshots = loadSnapshotsForValidation(itemsNeedingSnapshots);
        List<FinalAnswerResult> results = new ArrayList<>(changes.size());

        for (ChangeInput change : changes) {
            UUID itemId = change.itemId();
            long seq = change.seq();
            AttemptItem item = itemsById.get(itemId);

            if (item == null || !item.isInScope()) {
                UUID partId = item == null ? null : item.getPartId();
                recordSaveRejected(
                        attempt.getId(), sessionId, partId, receivedAt, itemId, seq, REASON_NOT_IN_SCOPE);
                results.add(new FinalAnswerResult(itemId, seq, FinalAnswerStatus.REJECTED, REASON_NOT_IN_SCOPE));
                continue;
            }

            AttemptPart part = partsById.get(item.getPartId());
            if (targetPart != null && !item.getPartId().equals(targetPart.getId())) {
                String wrongPartReason =
                        part != null
                                        && part.getState() == PartState.CLOSED
                                        && part.getCloseReason() == PartCloseReason.TIME_UP
                                ? REASON_TIME_UP
                                : REASON_PART_CLOSED;
                recordSaveRejected(
                        attempt.getId(),
                        sessionId,
                        item.getPartId(),
                        receivedAt,
                        itemId,
                        seq,
                        wrongPartReason);
                results.add(new FinalAnswerResult(itemId, seq, FinalAnswerStatus.REJECTED, wrongPartReason));
                continue;
            }

            String closedReason = resolveClosedReason(attempt, part, receivedAt);
            if (closedReason != null) {
                recordSaveRejected(
                        attempt.getId(),
                        sessionId,
                        item.getPartId(),
                        receivedAt,
                        itemId,
                        seq,
                        closedReason);
                results.add(new FinalAnswerResult(itemId, seq, FinalAnswerStatus.REJECTED, closedReason));
                continue;
            }

            if (seq <= 0) {
                recordSaveRejected(
                        attempt.getId(),
                        sessionId,
                        item.getPartId(),
                        receivedAt,
                        itemId,
                        seq,
                        REASON_INVALID_ANSWER);
                results.add(
                        new FinalAnswerResult(
                                itemId, seq, FinalAnswerStatus.REJECTED, REASON_INVALID_ANSWER));
                continue;
            }

            if (seq < item.getAnswerSeq()) {
                results.add(new FinalAnswerResult(itemId, seq, FinalAnswerStatus.STALE, null));
                continue;
            }

            if (seq == item.getAnswerSeq()) {
                results.add(new FinalAnswerResult(itemId, seq, FinalAnswerStatus.SAVED, null));
                continue;
            }

            JsonNode snapshot =
                    snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
            AnswerValueValidator.ValidationResult validation =
                    answerValueValidator.validate(item.getQuestionType(), snapshot, change.answer());
            if (!validation.valid()) {
                recordSaveRejected(
                        attempt.getId(),
                        sessionId,
                        item.getPartId(),
                        receivedAt,
                        itemId,
                        seq,
                        REASON_INVALID_ANSWER);
                results.add(
                        new FinalAnswerResult(
                                itemId, seq, FinalAnswerStatus.REJECTED, REASON_INVALID_ANSWER));
                continue;
            }

            item.recordAnswer(validation.normalized(), seq, receivedAt);
            dirtyItems.add(item);
            results.add(new FinalAnswerResult(itemId, seq, FinalAnswerStatus.SAVED, null));
        }

        return results;
    }

    private void updatePositionIfValid(
            Attempt attempt,
            Map<UUID, AttemptPart> partsById,
            Map<UUID, AttemptItem> itemsById,
            AttemptPosition position,
            Instant now) {
        UUID partId = position.partId();
        UUID itemId = position.itemId();
        Map<String, Object> posMap = new LinkedHashMap<>();

        boolean partValid = partId == null || partsById.containsKey(partId);
        if (partId != null && partValid) {
            posMap.put("partId", partId.toString());
        }
        if (itemId != null) {
            AttemptItem posItem = itemsById.get(itemId);
            if (posItem == null) {
                posItem =
                        attemptItemRepository
                                .findById(itemId)
                                .filter(i -> i.getAttemptId().equals(attempt.getId()))
                                .orElse(null);
            }
            if (posItem != null
                    && posItem.isInScope()
                    && (partId == null || (partValid && posItem.getPartId().equals(partId)))) {
                posMap.put("itemId", itemId.toString());
            }
        }
        if (!posMap.isEmpty()) {
            attempt.updateLastPosition(posMap, now);
            attemptRepository.saveAndFlush(attempt);
        }
    }

    private Map<UUID, AttemptItem> lockItemsById(UUID attemptId, Set<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AttemptItem> map = new LinkedHashMap<>();
        for (AttemptItem item :
                attemptItemRepository.findByAttemptIdAndIdInOrderByIdAscForUpdate(
                        attemptId, itemIds)) {
            map.put(item.getId(), item);
        }
        return map;
    }

    private Map<VersionKey, JsonNode> loadSnapshotsForValidation(List<AttemptItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Set<VersionKey> keys = new LinkedHashSet<>();
        for (AttemptItem item : items) {
            keys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }
        Map<VersionKey, JsonNode> map = new LinkedHashMap<>();
        loadVersionsInto(keys, map);

        // Fallback for QUESTION_SET child items whose snapshot is embedded under parent's body.children
        Set<UUID> missingParentItemIds = new LinkedHashSet<>();
        for (AttemptItem item : items) {
            VersionKey key = new VersionKey(item.getQuestionId(), item.getQuestionVersionNo());
            if (!map.containsKey(key) && item.getParentItemId() != null) {
                missingParentItemIds.add(item.getParentItemId());
            }
        }
        if (!missingParentItemIds.isEmpty()) {
            Set<VersionKey> parentKeys = new LinkedHashSet<>();
            for (AttemptItem parent : attemptItemRepository.findAllById(missingParentItemIds)) {
                parentKeys.add(new VersionKey(parent.getQuestionId(), parent.getQuestionVersionNo()));
            }
            if (!parentKeys.isEmpty()) {
                loadVersionsInto(parentKeys, map);
            }
        }
        return map;
    }

    private void loadVersionsInto(Set<VersionKey> keys, Map<VersionKey, JsonNode> map) {
        for (Version version : questionSourcePort.loadVersions(List.copyOf(keys))) {
            map.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    String idText = child.path("id").asText(null);
                    if (idText != null && !idText.isBlank()) {
                        map.putIfAbsent(
                                new VersionKey(UUID.fromString(idText), version.key().versionNo()),
                                child);
                    }
                }
            }
        }
    }

    private void recordSaveRejected(
            UUID attemptId,
            UUID sessionId,
            UUID partId,
            Instant at,
            UUID itemId,
            long seq,
            String reason) {
        // AC-18 / LT §4: never write answer content on attempt_event, strictly metadata/itemId+seq+reason.
        Map<String, Object> data = new LinkedHashMap<>();
        if (itemId != null) {
            data.put("itemId", itemId.toString());
        }
        data.put("seq", seq);
        data.put("reason", reason);
        attemptEventRepository.save(
                new AttemptEvent(
                        Ids.newId(),
                        attemptId,
                        AttemptEventType.SAVE_REJECTED,
                        sessionId,
                        partId,
                        at,
                        data));
    }

    private static String resolveClosedReason(Attempt attempt, AttemptPart part, Instant now) {
        if (attempt.getState() != AttemptState.IN_PROGRESS) {
            return attempt.getEndReason() == EndReason.TIME_UP
                    ? REASON_TIME_UP
                    : REASON_PART_CLOSED;
        }
        if (part == null || part.getState() != PartState.OPEN) {
            return part != null
                            && part.getState() == PartState.CLOSED
                            && part.getCloseReason() == PartCloseReason.TIME_UP
                    ? REASON_TIME_UP
                    : REASON_PART_CLOSED;
        }
        Instant effectiveDeadline =
                attempt.getTiming() == AttemptTiming.PER_PART
                        ? part.getDeadlineAt()
                        : attempt.getDeadlineAt();
        if (effectiveDeadline != null && !now.isBefore(effectiveDeadline)) {
            return REASON_TIME_UP;
        }
        return null;
    }

    private static Map<UUID, AttemptPart> indexPartsById(List<AttemptPart> parts) {
        Map<UUID, AttemptPart> map = new LinkedHashMap<>();
        for (AttemptPart part : parts) {
            map.put(part.getId(), part);
        }
        return map;
    }
}
