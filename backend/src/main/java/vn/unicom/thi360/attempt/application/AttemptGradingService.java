package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.grading.ExactRational;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicies;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicy;
import vn.unicom.thi360.attempt.domain.grading.ItemGradeResult;
import vn.unicom.thi360.attempt.domain.grading.ItemGrader;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.AggregatedScore;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.ItemInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.PartInput;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.SectionInput;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Orchestrates idempotent auto-grading and score aggregation for finished attempts (LT §9.1).
 *
 * <p>Executes grading in {@code PROPAGATION_REQUIRES_NEW} so failures never taint the outbox relay
 * transaction (AC-20). Reuses locked re-aggregation for manual essay finalization/reopen in AT-5.
 */
@Service
public class AttemptGradingService {

    private final AttemptRepository attemptRepository;
    private final AttemptPartRepository attemptPartRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final QuestionSourcePort questionSourcePort;
    private final PublishedExamPort publishedExamPort;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public AttemptGradingService(
            AttemptRepository attemptRepository,
            AttemptPartRepository attemptPartRepository,
            AttemptItemRepository attemptItemRepository,
            AttemptEventRepository attemptEventRepository,
            QuestionSourcePort questionSourcePort,
            PublishedExamPort publishedExamPort,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptPartRepository = Objects.requireNonNull(attemptPartRepository);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.clock = Objects.requireNonNull(clock);
        TransactionTemplate tx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
    }

    /** Grades a finished attempt idempotently inside a dedicated {@code REQUIRES_NEW} transaction. */
    public void gradeFinishedAttempt(UUID attemptId) {
        requiresNewTx.executeWithoutResult(status -> {
            Attempt attempt = attemptRepository.findByIdForUpdate(attemptId).orElse(null);
            if (attempt == null) {
                return;
            }
            if (attempt.getState() == AttemptState.SCORED
                    || attempt.getState() == AttemptState.PENDING_REVIEW) {
                return;
            }
            if (attempt.getState() != AttemptState.GRADING
                    && attempt.getState() != AttemptState.GRADING_ERROR) {
                return;
            }
            gradeAndAggregateLocked(attempt, false);
        });
    }

    /** Re-grades an attempt from its stored immutable question versions while keeping FINAL essays (AC-16, AC-18). */
    public void regradeAttempt(UUID attemptId) {
        requiresNewTx.executeWithoutResult(status -> {
            Attempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                    .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Attempt not found: " + attemptId));
            gradeAndAggregateLocked(attempt, true);
        });
    }

    /** Marks the attempt {@code GRADING_ERROR} in a dedicated {@code REQUIRES_NEW} transaction when retries exhaust. */
    public void markGradingError(UUID attemptId, String errorMessage) {
        requiresNewTx.executeWithoutResult(status -> {
            Attempt attempt = attemptRepository.findByIdForUpdate(attemptId).orElse(null);
            if (attempt == null) {
                return;
            }
            if (attempt.getState() != AttemptState.GRADING
                    && attempt.getState() != AttemptState.GRADING_ERROR) {
                return;
            }
            Instant now = Instant.now(clock);
            attempt.markGradingError(errorMessage, now);
            attemptRepository.save(attempt);
            attemptEventRepository.save(new AttemptEvent(
                    Ids.newId(),
                    attemptId,
                    AttemptEventType.GRADING_FAILED,
                    null,
                    null,
                    now,
                    Map.of("error", safeError(errorMessage))));
        });
    }

    /**
     * Re-aggregates scores on an already {@code FOR UPDATE}-locked {@link Attempt} (used by AT-4 and AT-5).
     */
    public AggregatedScore reaggregateLockedAttempt(Attempt attempt, Instant now) {
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> items = loadItemsInPaperOrder(attempt.getId(), parts);
        List<SectionInput> sections = loadSectionMetadata(attempt);

        AggregatedScore aggregated = ScoreAggregator.aggregate(toScoreInput(attempt, parts, items, sections));
        attempt.applyGradingResult(
                aggregated.pendingReview(),
                aggregated.rawMaxScore(),
                aggregated.rawScore(),
                aggregated.displayScore(),
                aggregated.toResultJson(),
                now,
                now);
        attemptRepository.save(attempt);
        return aggregated;
    }

    private void gradeAndAggregateLocked(Attempt attempt, boolean regrade) {
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        List<AttemptItem> items = loadItemsInPaperOrder(attempt.getId(), parts);

        List<AttemptItem> inScopeItems = items.stream().filter(AttemptItem::isInScope).toList();
        Map<VersionKey, JsonNode> snapshots = loadSnapshots(inScopeItems);
        GradingPolicy policy = GradingPolicies.forKind(attempt.getExamKindCode());

        Map<UUID, List<ItemGradeResult>> childResultsByParentId = new LinkedHashMap<>();
        List<AttemptItem> setParents = new ArrayList<>();

        for (AttemptItem item : inScopeItems) {
            if (item.getQuestionType() == QuestionType.QUESTION_SET) {
                setParents.add(item);
                continue;
            }

            ItemGradeResult gradeResult;
            if (item.isFinalizedManualEssay()) {
                gradeResult = existingManualResult(item);
            } else {
                JsonNode snapshot = snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
                if (snapshot == null && item.getQuestionType() != QuestionType.ESSAY) {
                    throw new IllegalStateException(
                            "Missing issued question snapshot for "
                                    + item.getQuestionId()
                                    + " v"
                                    + item.getQuestionVersionNo());
                }
                gradeResult = ItemGrader.grade(
                        item.getQuestionType(),
                        snapshot,
                        item.getAnswer(),
                        item.getPointsMax(),
                        policy);
                item.recordGrading(gradeResult.earnedPoints(), gradeResult.outcome(), gradeResult.gradingDetail());
            }

            if (item.getParentItemId() != null) {
                childResultsByParentId
                        .computeIfAbsent(item.getParentItemId(), ignored -> new ArrayList<>())
                        .add(gradeResult);
            }
        }

        for (AttemptItem parent : setParents) {
            List<ItemGradeResult> children = childResultsByParentId.getOrDefault(parent.getId(), List.of());
            ItemGradeResult rolledUp = ItemGrader.rollUpQuestionSet(children, parent.getPointsMax());
            parent.recordGrading(rolledUp.earnedPoints(), rolledUp.outcome(), rolledUp.gradingDetail());
        }

        if (!items.isEmpty()) {
            attemptItemRepository.saveAll(items);
        }

        Instant now = Instant.now(clock);
        AggregatedScore aggregated = reaggregateLockedAttempt(attempt, now);

        attemptEventRepository.save(new AttemptEvent(
                Ids.newId(),
                attempt.getId(),
                regrade ? AttemptEventType.REGRADED : AttemptEventType.GRADED,
                null,
                null,
                now,
                Map.of(
                        "state", attempt.getState().name(),
                        "pendingReview", aggregated.pendingReview())));
    }

    private List<AttemptItem> loadItemsInPaperOrder(UUID attemptId, List<AttemptPart> parts) {
        List<AttemptItem> items = new ArrayList<>(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId));
        Map<UUID, Integer> partOrdById = new HashMap<>(parts.size());
        for (AttemptPart part : parts) {
            partOrdById.put(part.getId(), part.getOrd());
        }
        items.sort(Comparator
                .comparingInt((AttemptItem item) -> partOrdById.getOrDefault(item.getPartId(), Integer.MAX_VALUE))
                .thenComparingInt(AttemptItem::getOrd));
        return items;
    }

    private Map<VersionKey, JsonNode> loadSnapshots(List<AttemptItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Set<VersionKey> keysToLoad = new LinkedHashSet<>();
        for (AttemptItem item : items) {
            keysToLoad.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }
        List<Version> loaded = questionSourcePort.loadVersions(List.copyOf(keysToLoad));
        Map<VersionKey, JsonNode> map = new HashMap<>(loaded.size() * 2);
        for (Version version : loaded) {
            if (version == null || version.snapshot() == null) {
                continue;
            }
            map.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    String childIdText = child.path("id").asText(null);
                    if (childIdText != null && !childIdText.isBlank()) {
                        try {
                            map.putIfAbsent(
                                    new VersionKey(UUID.fromString(childIdText.trim()), version.key().versionNo()),
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

    private static ItemGradeResult existingManualResult(AttemptItem item) {
        BigDecimal earned = item.getEarnedPoints() != null ? item.getEarnedPoints() : BigDecimal.ZERO;
        ItemOutcome outcome = item.getOutcome() != null ? item.getOutcome() : ItemOutcome.PENDING_REVIEW;
        if (outcome == ItemOutcome.PENDING_REVIEW) {
            return ItemGradeResult.pendingReview(item.getPointsMax(), item.getGradingDetail());
        }
        ExactRational exact = ExactRational.of(earned);
        if (item.getGradingDetail() != null && item.getGradingDetail().get("exactFraction") instanceof String s) {
            try {
                exact = ExactRational.parse(s);
            } catch (RuntimeException ignored) {
                // Keep decimal value
            }
        }
        return ItemGradeResult.graded(exact, item.getPointsMax(), outcome, item.getGradingDetail());
    }

    private List<SectionInput> loadSectionMetadata(Attempt attempt) {
        if (!"VACT".equals(attempt.getExamKindCode())) {
            return List.of();
        }
        Optional<PublishedVersion> published =
                publishedExamPort.loadVersion(attempt.getExamId(), attempt.getExamVersionNo());
        if (published.isEmpty() || published.get().parts() == null) {
            return List.of();
        }
        List<SectionInput> sections = new ArrayList<>();
        for (PublishedExamPort.Part part : published.get().parts()) {
            if (part.sections() == null) {
                continue;
            }
            for (PublishedExamPort.Section sec : part.sections()) {
                sections.add(new SectionInput(sec.id(), sec.subjectCode(), sec.questionCount()));
            }
        }
        return sections;
    }

    private static ScoreAggregator.Input toScoreInput(
            Attempt attempt,
            List<AttemptPart> parts,
            List<AttemptItem> items,
            List<SectionInput> sections) {
        List<PartInput> partInputs = parts.stream()
                .map(p -> new PartInput(
                        p.getId(),
                        p.getPartCode(),
                        p.getPartName(),
                        p.getOrd(),
                        p.getState(),
                        p.getMaxPoints(),
                        p.getQuestionCountRequired()))
                .toList();
        List<ItemInput> itemInputs = items.stream()
                .map(i -> new ItemInput(
                        i.getId(),
                        i.getPartId(),
                        i.getParentItemId(),
                        i.getSubjectItemId(),
                        i.getSectionId(),
                        i.getComponentCode(),
                        i.getQuestionType(),
                        i.getUnitCount(),
                        i.isInScope(),
                        i.getPointsMax(),
                        i.getEarnedPoints(),
                        i.getOutcome(),
                        i.getGradingDetail()))
                .toList();
        return new ScoreAggregator.Input(
                attempt.getExamKindCode(),
                attempt.getPracticeScoring(),
                attempt.getReferenceMax(),
                partInputs,
                itemInputs,
                sections);
    }

    private static String safeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "GRADING_FAILED";
        }
        return errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
    }
}
