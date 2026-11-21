package vn.unicom.thi360.attempt.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.SegmentResultView;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.UnitStatsView;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.domain.grading.ExactRational;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Segment;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Builds per-{@code displaySegment} results from a frozen {@link PublishedVersion} snapshot and
 * graded {@link AttemptItem}s (BS §17.3, ADD-HV H40).
 *
 * <p>Attributes split {@code QUESTION_SET} clusters and straddling {@code CLOZE} blanks strictly by
 * 1-based part-local position, using {@link ExactRational} for lossless summation before a single
 * HALF_UP rounding to 2 decimals. Returns {@code null} when no part in the attempt defines
 * {@code displaySegments}.
 */
@Component
public class AttemptSegmentResultBuilder {

    private record PositionedItem(AttemptItem item, int startPos, int endPos) {}

    private static final class SegmentAccumulator {
        ExactRational earned = ExactRational.ZERO;
        ExactRational itemsMax = ExactRational.ZERO;
        boolean pendingReview = false;
        int correctCount = 0;
        int partialCount = 0;
        int wrongCount = 0;
        int blankCount = 0;
        int pendingReviewCount = 0;

        void recordWholeItem(AttemptItem item, int unitSpan) {
            itemsMax = itemsMax.add(ExactRational.of(item.getPointsMax()));
            if (item.getOutcome() == ItemOutcome.PENDING_REVIEW) {
                pendingReview = true;
            } else {
                earned = earned.add(exactEarned(item));
            }
            if (item.getQuestionType() == QuestionType.CLOZE) {
                List<Map<?, ?>> blankEntries = clozeBlankEntries(item);
                if (!blankEntries.isEmpty()) {
                    for (Map<?, ?> entry : blankEntries) {
                        addOutcome(parseOutcomeOrDefault(entry.get("outcome"), item.getOutcome()), 1);
                    }
                    return;
                }
                addOutcome(item.getOutcome(), unitSpan);
                return;
            }
            addOutcome(item.getOutcome(), 1);
        }

        void recordClozeBlank(AttemptItem item, int unitSpan, int blankIndex, List<Map<?, ?>> blankEntries) {
            if (blankIndex < blankEntries.size()) {
                Map<?, ?> entry = blankEntries.get(blankIndex);
                ItemOutcome blankOutcome = parseOutcomeOrDefault(entry.get("outcome"), item.getOutcome());
                BigDecimal rawBlankMax = toBigDecimal(entry.get("pointsMax"));
                ExactRational blankMax = rawBlankMax != null
                        ? ExactRational.of(rawBlankMax)
                        : ExactRational.of(item.getPointsMax()).divide(ExactRational.of(unitSpan, 1));
                itemsMax = itemsMax.add(blankMax);
                if (blankOutcome == ItemOutcome.PENDING_REVIEW) {
                    pendingReview = true;
                    pendingReviewCount++;
                } else {
                    earned = earned.add(exactBlankEarned(entry));
                    addOutcome(blankOutcome, 1);
                }
                return;
            }

            ExactRational unitDivisor = ExactRational.of(unitSpan, 1);
            itemsMax = itemsMax.add(ExactRational.of(item.getPointsMax()).divide(unitDivisor));
            if (item.getOutcome() == ItemOutcome.PENDING_REVIEW) {
                pendingReview = true;
                pendingReviewCount++;
            } else {
                earned = earned.add(exactEarned(item).divide(unitDivisor));
                addOutcome(item.getOutcome(), 1);
            }
        }

        void addOutcome(ItemOutcome outcome, int delta) {
            if (delta <= 0) {
                return;
            }
            ItemOutcome effective = outcome == null ? ItemOutcome.BLANK : outcome;
            switch (effective) {
                case CORRECT -> correctCount += delta;
                case PARTIAL -> partialCount += delta;
                case WRONG -> wrongCount += delta;
                case BLANK -> blankCount += delta;
                case PENDING_REVIEW -> {
                    pendingReview = true;
                    pendingReviewCount += delta;
                }
            }
        }

        UnitStatsView toUnitStats() {
            int total = correctCount + partialCount + wrongCount + blankCount + pendingReviewCount;
            return new UnitStatsView(
                    correctCount, partialCount, wrongCount, blankCount, pendingReviewCount, total);
        }
    }

    /**
     * Computes segment breakdown views from {@code publishedVersion}, returning {@code null} when
     * no part in the attempt carries non-empty {@code displaySegments}.
     */
    public List<SegmentResultView> build(
            Optional<PublishedVersion> publishedVersion,
            List<AttemptPart> parts,
            List<AttemptItem> allItems) {
        if (publishedVersion == null
                || publishedVersion.isEmpty()
                || publishedVersion.get().parts() == null
                || parts == null
                || parts.isEmpty()) {
            return null;
        }

        Map<String, PublishedExamPort.Part> publishedPartsById = new HashMap<>();
        for (PublishedExamPort.Part pubPart : publishedVersion.get().parts()) {
            if (pubPart != null && pubPart.id() != null) {
                publishedPartsById.put(pubPart.id(), pubPart);
            }
        }

        Map<UUID, List<AttemptItem>> leafItemsByPart = groupInScopeLeafItemsByPart(allItems);
        List<AttemptPart> orderedParts = parts.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AttemptPart::getOrd))
                .toList();

        List<SegmentResultView> result = new ArrayList<>();
        for (AttemptPart attemptPart : orderedParts) {
            PublishedExamPort.Part pubPart = publishedPartsById.get(attemptPart.getPartCode());
            if (pubPart == null || pubPart.displaySegments() == null || pubPart.displaySegments().isEmpty()) {
                continue;
            }
            List<Segment> sortedSegments = pubPart.displaySegments().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .comparing(Segment::fromPosition, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Segment::toPosition, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (sortedSegments.isEmpty()) {
                continue;
            }

            List<AttemptItem> partItems = leafItemsByPart.getOrDefault(attemptPart.getId(), List.of());
            if (attemptPart.getState() == PartState.SKIPPED && partItems.isEmpty()) {
                for (Segment seg : sortedSegments) {
                    result.add(buildEmptySkippedSegment(attemptPart.getPartCode(), seg));
                }
                continue;
            }

            List<PositionedItem> positioned = positionPartItems(partItems);
            for (Segment seg : sortedSegments) {
                result.add(buildSegmentResult(attemptPart.getPartCode(), seg, positioned));
            }
        }

        return result.isEmpty() ? null : List.copyOf(result);
    }

    private static Map<UUID, List<AttemptItem>> groupInScopeLeafItemsByPart(List<AttemptItem> allItems) {
        if (allItems == null || allItems.isEmpty()) {
            return Map.of();
        }
        Set<UUID> outOfScopeParentIds = new HashSet<>();
        for (AttemptItem item : allItems) {
            if (item != null && !item.isInScope() && item.getId() != null) {
                outOfScopeParentIds.add(item.getId());
            }
        }

        Map<UUID, List<AttemptItem>> byPart = new HashMap<>();
        for (AttemptItem item : allItems) {
            if (item == null || !item.isInScope()) {
                continue;
            }
            if (item.getParentItemId() != null && outOfScopeParentIds.contains(item.getParentItemId())) {
                continue;
            }
            if (item.getQuestionType() == QuestionType.QUESTION_SET) {
                continue;
            }
            byPart.computeIfAbsent(item.getPartId(), ignored -> new ArrayList<>()).add(item);
        }
        for (List<AttemptItem> partItems : byPart.values()) {
            partItems.sort(Comparator.comparingInt(AttemptItem::getOrd));
        }
        return byPart;
    }

    private static List<PositionedItem> positionPartItems(List<AttemptItem> partItems) {
        List<PositionedItem> positioned = new ArrayList<>(partItems.size());
        int pos = 1;
        for (AttemptItem item : partItems) {
            int span = item.getQuestionType() == QuestionType.CLOZE
                    ? Math.max(1, item.getUnitCount())
                    : 1;
            positioned.add(new PositionedItem(item, pos, pos + span - 1));
            pos += span;
        }
        return positioned;
    }

    private static SegmentResultView buildEmptySkippedSegment(String partCode, Segment seg) {
        int fromPos = seg.fromPosition() != null ? seg.fromPosition() : 0;
        int toPos = seg.toPosition() != null ? seg.toPosition() : 0;
        int blankCount = (fromPos >= 1 && toPos >= fromPos) ? (toPos - fromPos + 1) : 0;
        BigDecimal maxPoints = seg.maxPoints() != null
                ? seg.maxPoints().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new SegmentResultView(
                seg.code(),
                seg.name(),
                partCode,
                fromPos,
                toPos,
                false,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                maxPoints,
                new UnitStatsView(0, 0, 0, blankCount, 0, blankCount));
    }

    private static SegmentResultView buildSegmentResult(
            String partCode, Segment seg, List<PositionedItem> positioned) {
        int fromPos = seg.fromPosition() != null ? seg.fromPosition() : 0;
        int toPos = seg.toPosition() != null ? seg.toPosition() : 0;
        SegmentAccumulator acc = new SegmentAccumulator();

        if (fromPos <= toPos) {
            for (PositionedItem pi : positioned) {
                if (pi.endPos() < fromPos || pi.startPos() > toPos) {
                    continue;
                }
                AttemptItem item = pi.item();
                int unitSpan = pi.endPos() - pi.startPos() + 1;
                if (pi.startPos() >= fromPos && pi.endPos() <= toPos) {
                    acc.recordWholeItem(item, unitSpan);
                } else {
                    List<Map<?, ?>> blankEntries = clozeBlankEntries(item);
                    for (int k = 0; k < unitSpan; k++) {
                        int blankPos = pi.startPos() + k;
                        if (blankPos >= fromPos && blankPos <= toPos) {
                            acc.recordClozeBlank(item, unitSpan, k, blankEntries);
                        }
                    }
                }
            }
        }

        BigDecimal score = acc.pendingReview ? null : acc.earned.toBigDecimal(2);
        BigDecimal maxPoints = seg.maxPoints() != null
                ? seg.maxPoints().setScale(2, RoundingMode.HALF_UP)
                : acc.itemsMax.toBigDecimal(2);

        return new SegmentResultView(
                seg.code(),
                seg.name(),
                partCode,
                fromPos,
                toPos,
                acc.pendingReview,
                score,
                maxPoints,
                acc.toUnitStats());
    }

    private static ExactRational exactEarned(AttemptItem item) {
        if (item.getOutcome() == ItemOutcome.PENDING_REVIEW) {
            return ExactRational.ZERO;
        }
        Map<String, Object> detail = item.getGradingDetail();
        if (detail != null && detail.get("exactFraction") instanceof String s && !s.isBlank()) {
            try {
                return ExactRational.parse(s);
            } catch (RuntimeException ignored) {
                // Fall back to persisted numeric column
            }
        }
        return ExactRational.of(item.getEarnedPoints());
    }

    private static ExactRational exactBlankEarned(Map<?, ?> entry) {
        Object fraction = entry.get("exactFraction");
        if (fraction instanceof String s && !s.isBlank()) {
            try {
                return ExactRational.parse(s);
            } catch (RuntimeException ignored) {
                // Fall back to numeric pointsEarned
            }
        }
        BigDecimal bd = toBigDecimal(entry.get("pointsEarned"));
        return bd != null ? ExactRational.of(bd) : ExactRational.ZERO;
    }

    private static List<Map<?, ?>> clozeBlankEntries(AttemptItem item) {
        Map<String, Object> detail = item.getGradingDetail();
        if (detail == null) {
            return List.of();
        }
        Object raw = detail.get("blankResults");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<?, ?>> entries = new ArrayList<>(list.size());
        for (Object elem : list) {
            if (elem instanceof Map<?, ?> map) {
                entries.add(map);
            }
        }
        return entries;
    }

    private static ItemOutcome parseOutcomeOrDefault(Object obj, ItemOutcome fallback) {
        if (obj instanceof ItemOutcome io) {
            return io;
        }
        if (obj instanceof String s) {
            try {
                return ItemOutcome.valueOf(s);
            } catch (IllegalArgumentException ignored) {
                // Fall back below
            }
        }
        return fallback != null ? fallback : ItemOutcome.BLANK;
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BigDecimal bd) {
            return bd;
        }
        if (obj instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
