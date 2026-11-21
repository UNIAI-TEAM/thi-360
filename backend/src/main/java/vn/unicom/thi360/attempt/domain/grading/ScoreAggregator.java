package vn.unicom.thi360.attempt.domain.grading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Pure score and unit-statistics aggregator for an attempt (LT §9.1, BE §8, AC-6, AC-12..15, AC-21..25, AC-36).
 *
 * <p>Uses {@link ExactRational} internally and rounds exactly once at the final display step
 * with {@link java.math.RoundingMode#HALF_UP} to 2 decimal places.
 */
public final class ScoreAggregator {

    public record PartInput(
            UUID id,
            String partCode,
            String partName,
            int ord,
            PartState state,
            BigDecimal maxPoints,
            Integer questionCountRequired) {}

    public record ItemInput(
            UUID id,
            UUID partId,
            UUID parentItemId,
            UUID subjectItemId,
            String sectionId,
            String componentCode,
            QuestionType questionType,
            int unitCount,
            boolean inScope,
            BigDecimal pointsMax,
            BigDecimal earnedPoints,
            ItemOutcome outcome,
            Map<String, Object> gradingDetail) {

        /** Resolves exact rational earned score from {@code gradingDetail.exactFraction} if present, else decimal. */
        public ExactRational exactEarned() {
            if (outcome == ItemOutcome.PENDING_REVIEW) {
                return ExactRational.ZERO;
            }
            if (gradingDetail != null) {
                Object fraction = gradingDetail.get("exactFraction");
                if (fraction instanceof String s && !s.isBlank()) {
                    try {
                        return ExactRational.parse(s);
                    } catch (RuntimeException ignored) {
                        // Fall back to persisted numeric column
                    }
                }
            }
            return ExactRational.of(earnedPoints);
        }

        public List<ItemOutcome> clozeBlankOutcomes() {
            if (gradingDetail == null) {
                return List.of();
            }
            Object raw = gradingDetail.get("blankResults");
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }
            List<ItemOutcome> outcomes = new ArrayList<>(list.size());
            for (Object elem : list) {
                if (elem instanceof Map<?, ?> map) {
                    Object outObj = map.get("outcome");
                    if (outObj instanceof String s) {
                        try {
                            outcomes.add(ItemOutcome.valueOf(s));
                        } catch (IllegalArgumentException ignored) {
                            // Ignore unrecognized entry
                        }
                    }
                }
            }
            return outcomes;
        }
    }

    public record SectionInput(
            String sectionId,
            String subjectCode,
            Integer questionCount) {}

    public record Input(
            String examKindCode,
            String practiceScoring,
            BigDecimal referenceMax,
            List<PartInput> parts,
            List<ItemInput> items,
            List<SectionInput> sections) {}

    public record UnitStats(
            int correctCount,
            int partialCount,
            int wrongCount,
            int blankCount,
            int pendingReviewCount,
            int totalCount) {}

    public record PartBreakdown(
            UUID partId,
            String partCode,
            String partName,
            int ord,
            boolean skipped,
            boolean pendingReview,
            BigDecimal score,
            BigDecimal maxScore,
            UnitStats stats) {}

    public record SubjectBreakdown(
            String code,
            UUID subjectItemId,
            boolean pendingReview,
            BigDecimal score,
            BigDecimal maxScore,
            UnitStats stats) {}

    public record VactSubsectionBreakdown(
            String sectionId,
            int questionCount,
            int correctCount,
            int partialCount,
            int wrongCount,
            int blankCount,
            int totalCount) {}

    public record VactComponentBreakdown(
            String componentCode,
            int correctCount,
            int partialCount,
            int wrongCount,
            int blankCount,
            int totalCount,
            List<VactSubsectionBreakdown> subsections) {}

    public record AggregatedScore(
            boolean pendingReview,
            String practiceScoring,
            BigDecimal rawScore,
            BigDecimal displayScore,
            BigDecimal rawMaxScore,
            BigDecimal scaleMax,
            UnitStats statistics,
            List<PartBreakdown> partBreakdowns,
            List<SubjectBreakdown> subjectBreakdowns,
            List<VactComponentBreakdown> vactComponents) {

        public Map<String, Object> toResultJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("pendingReview", pendingReview);
            map.put("practiceScoring", practiceScoring);
            map.put("rawScore", rawScore);
            map.put("displayScore", displayScore);
            map.put("rawMaxScore", rawMaxScore);
            map.put("scaleMax", scaleMax);
            map.put("statistics", statsToMap(statistics));

            List<Map<String, Object>> partsList = new ArrayList<>(partBreakdowns.size());
            for (PartBreakdown pb : partBreakdowns) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("partId", pb.partId() != null ? pb.partId().toString() : null);
                pm.put("partCode", pb.partCode());
                pm.put("partName", pb.partName());
                pm.put("ord", pb.ord());
                pm.put("skipped", pb.skipped());
                pm.put("pendingReview", pb.pendingReview());
                pm.put("score", pb.score());
                pm.put("maxScore", pb.maxScore());
                pm.put("stats", statsToMap(pb.stats()));
                partsList.add(pm);
            }
            map.put("parts", partsList);

            List<Map<String, Object>> subjectsList = new ArrayList<>(subjectBreakdowns.size());
            for (SubjectBreakdown sb : subjectBreakdowns) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("code", sb.code());
                sm.put("subjectItemId", sb.subjectItemId() != null ? sb.subjectItemId().toString() : null);
                sm.put("pendingReview", sb.pendingReview());
                sm.put("score", sb.score());
                sm.put("maxScore", sb.maxScore());
                sm.put("stats", statsToMap(sb.stats()));
                subjectsList.add(sm);
            }
            map.put("subjects", subjectsList);

            if (vactComponents != null) {
                List<Map<String, Object>> vactList = new ArrayList<>(vactComponents.size());
                for (VactComponentBreakdown vc : vactComponents) {
                    Map<String, Object> vm = new LinkedHashMap<>();
                    vm.put("componentCode", vc.componentCode());
                    vm.put("correctCount", vc.correctCount());
                    vm.put("partialCount", vc.partialCount());
                    vm.put("wrongCount", vc.wrongCount());
                    vm.put("blankCount", vc.blankCount());
                    vm.put("totalCount", vc.totalCount());
                    if (vc.subsections() != null) {
                        List<Map<String, Object>> subList = new ArrayList<>(vc.subsections().size());
                        for (VactSubsectionBreakdown sub : vc.subsections()) {
                            Map<String, Object> subm = new LinkedHashMap<>();
                            subm.put("sectionId", sub.sectionId());
                            subm.put("questionCount", sub.questionCount());
                            subm.put("correctCount", sub.correctCount());
                            subm.put("partialCount", sub.partialCount());
                            subm.put("wrongCount", sub.wrongCount());
                            subm.put("blankCount", sub.blankCount());
                            subm.put("totalCount", sub.totalCount());
                            subList.add(subm);
                        }
                        vm.put("subsections", subList);
                    }
                    vactList.add(vm);
                }
                map.put("vactComponents", vactList);
            }
            return map;
        }

        private static Map<String, Object> statsToMap(UnitStats stats) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("correctCount", stats.correctCount());
            s.put("partialCount", stats.partialCount());
            s.put("wrongCount", stats.wrongCount());
            s.put("blankCount", stats.blankCount());
            s.put("pendingReviewCount", stats.pendingReviewCount());
            s.put("totalCount", stats.totalCount());
            return s;
        }
    }

    private static final class MutableAccumulator {
        ExactRational earned = ExactRational.ZERO;
        ExactRational max = ExactRational.ZERO;
        boolean pendingReview = false;
        int correctCount = 0;
        int partialCount = 0;
        int wrongCount = 0;
        int blankCount = 0;
        int pendingReviewCount = 0;

        void recordItem(ItemInput item) {
            max = max.add(ExactRational.of(item.pointsMax()));
            if (item.outcome() == ItemOutcome.PENDING_REVIEW) {
                pendingReview = true;
            } else {
                earned = earned.add(item.exactEarned());
            }
            recordUnits(item);
        }

        void recordUnits(ItemInput item) {
            if (item.questionType() == QuestionType.CLOZE) {
                List<ItemOutcome> blankOutcomes = item.clozeBlankOutcomes();
                if (!blankOutcomes.isEmpty()) {
                    for (ItemOutcome o : blankOutcomes) {
                        addOutcomeUnit(o, 1);
                    }
                    return;
                }
                addOutcomeUnit(item.outcome(), Math.max(1, item.unitCount()));
                return;
            }
            addOutcomeUnit(item.outcome(), 1);
        }

        void addOutcomeUnit(ItemOutcome outcome, int delta) {
            if (delta <= 0) {
                return;
            }
            ItemOutcome effective = outcome == null ? ItemOutcome.BLANK : outcome;
            switch (effective) {
                case CORRECT -> correctCount += delta;
                case PARTIAL -> partialCount += delta;
                case WRONG -> wrongCount += delta;
                case BLANK -> blankCount += delta;
                case PENDING_REVIEW -> pendingReviewCount += delta;
            }
        }

        void recordEmptySkippedPart(PartInput part) {
            if (part.maxPoints() != null) {
                max = max.add(ExactRational.of(part.maxPoints()));
            }
            if (part.questionCountRequired() != null && part.questionCountRequired() > 0) {
                blankCount += part.questionCountRequired();
            }
        }

        UnitStats toUnitStats() {
            int total = correctCount + partialCount + wrongCount + blankCount + pendingReviewCount;
            return new UnitStats(
                    correctCount, partialCount, wrongCount, blankCount, pendingReviewCount, total);
        }
    }

    private ScoreAggregator() {}

    public static AggregatedScore aggregate(Input input) {
        Objects.requireNonNull(input, "input must not be null");
        List<PartInput> parts = input.parts() == null ? List.of() : input.parts();
        List<ItemInput> allItems = input.items() == null ? List.of() : input.items();

        // AC-6: exclude parent QUESTION_SET items when their children exist so sets are never double-counted
        Set<UUID> parentIdsWithChildren = new HashSet<>();
        for (ItemInput item : allItems) {
            if (item.parentItemId() != null) {
                parentIdsWithChildren.add(item.parentItemId());
            }
        }

        List<ItemInput> leafInScopeItems = new ArrayList<>();
        Map<UUID, List<ItemInput>> itemsByPart = new HashMap<>();
        for (ItemInput item : allItems) {
            if (!item.inScope()) {
                continue;
            }
            if (item.questionType() == QuestionType.QUESTION_SET
                    && parentIdsWithChildren.contains(item.id())) {
                continue;
            }
            leafInScopeItems.add(item);
            itemsByPart.computeIfAbsent(item.partId(), ignored -> new ArrayList<>()).add(item);
        }

        MutableAccumulator totalAcc = new MutableAccumulator();
        List<PartBreakdown> partBreakdowns = new ArrayList<>(parts.size());
        Map<String, MutableAccumulator> subjectAccumulators = new LinkedHashMap<>();
        Map<String, UUID> subjectItemIdByCode = new HashMap<>();

        for (PartInput part : parts) {
            List<ItemInput> partItems = itemsByPart.getOrDefault(part.id(), List.of());
            MutableAccumulator partAcc = new MutableAccumulator();

            if (part.state() == PartState.SKIPPED && partItems.isEmpty()) {
                // AC-21: skipped before questions were generated -> 0 earned, add part maxPoints & required units to BLANK
                partAcc.recordEmptySkippedPart(part);
                totalAcc.recordEmptySkippedPart(part);
            } else {
                // AC-22: skipped with items, or normal closed/submitted part -> aggregate solely from its items
                for (ItemInput item : partItems) {
                    partAcc.recordItem(item);
                    totalAcc.recordItem(item);
                    recordSubjectItem(item, subjectAccumulators, subjectItemIdByCode);
                }
            }

            BigDecimal partScore = partAcc.pendingReview ? null : partAcc.earned.toBigDecimal(2);
            BigDecimal partMax = partAcc.max.toBigDecimal(2);
            partBreakdowns.add(new PartBreakdown(
                    part.id(),
                    part.partCode(),
                    part.partName(),
                    part.ord(),
                    part.state() == PartState.SKIPPED,
                    partAcc.pendingReview,
                    partScore,
                    partMax,
                    partAcc.toUnitStats()));
        }

        // Fallback for items without a matching PartInput (e.g. unit tests)
        if (parts.isEmpty() && !leafInScopeItems.isEmpty()) {
            for (ItemInput item : leafInScopeItems) {
                totalAcc.recordItem(item);
                recordSubjectItem(item, subjectAccumulators, subjectItemIdByCode);
            }
        }

        List<SubjectBreakdown> subjectBreakdowns = new ArrayList<>(subjectAccumulators.size());
        for (Map.Entry<String, MutableAccumulator> entry : subjectAccumulators.entrySet()) {
            MutableAccumulator acc = entry.getValue();
            subjectBreakdowns.add(new SubjectBreakdown(
                    entry.getKey(),
                    subjectItemIdByCode.get(entry.getKey()),
                    acc.pendingReview,
                    acc.pendingReview ? null : acc.earned.toBigDecimal(2),
                    acc.max.toBigDecimal(2),
                    acc.toUnitStats()));
        }

        String effectivePracticeScoring = input.practiceScoring() == null ? "RAW" : input.practiceScoring();
        BigDecimal rawMaxScore = totalAcc.max.toBigDecimal(2);
        ExactRational scaleMaxRational;
        if ("LINEAR".equals(effectivePracticeScoring)
                && input.referenceMax() != null
                && input.referenceMax().compareTo(BigDecimal.ZERO) > 0) {
            scaleMaxRational = ExactRational.of(input.referenceMax());
        } else {
            scaleMaxRational = totalAcc.max;
        }
        BigDecimal scaleMax = scaleMaxRational.toBigDecimal(2);

        BigDecimal rawScore = null;
        BigDecimal displayScore = null;
        if (!totalAcc.pendingReview) {
            rawScore = totalAcc.earned.toBigDecimal(2);
            if ("LINEAR".equals(effectivePracticeScoring)) {
                if (totalAcc.max.isZero()) {
                    displayScore = BigDecimal.ZERO.setScale(2);
                } else {
                    // One HALF_UP rounding step at display time after exact rational scaling
                    ExactRational scaled = totalAcc.earned
                            .divide(totalAcc.max)
                            .multiply(scaleMaxRational);
                    displayScore = scaled.toBigDecimal(2);
                }
            } else {
                displayScore = rawScore;
            }
        }

        List<VactComponentBreakdown> vactComponents =
                "VACT".equals(input.examKindCode())
                        ? computeVactComponents(leafInScopeItems, input.sections())
                        : null;

        return new AggregatedScore(
                totalAcc.pendingReview,
                effectivePracticeScoring,
                rawScore,
                displayScore,
                rawMaxScore,
                scaleMax,
                totalAcc.toUnitStats(),
                partBreakdowns,
                subjectBreakdowns,
                vactComponents);
    }

    private static void recordSubjectItem(
            ItemInput item,
            Map<String, MutableAccumulator> bySubject,
            Map<String, UUID> subjectItemIds) {
        String key = item.componentCode() != null
                ? item.componentCode()
                : (item.subjectItemId() != null ? item.subjectItemId().toString() : null);
        if (key == null) {
            return;
        }
        if (item.subjectItemId() != null) {
            subjectItemIds.putIfAbsent(key, item.subjectItemId());
        }
        bySubject.computeIfAbsent(key, ignored -> new MutableAccumulator()).recordItem(item);
    }

    private static List<VactComponentBreakdown> computeVactComponents(
            List<ItemInput> leafItems, List<SectionInput> sections) {
        Map<String, MutableAccumulator> byComponent = new LinkedHashMap<>();
        Map<String, MutableAccumulator> sciBySection = new LinkedHashMap<>();

        for (ItemInput item : leafItems) {
            String component = item.componentCode();
            if (component == null || component.isBlank()) {
                continue;
            }
            byComponent.computeIfAbsent(component, ignored -> new MutableAccumulator()).recordItem(item);
            if ("SCIENTIFIC_THINKING".equals(component) && item.sectionId() != null) {
                sciBySection.computeIfAbsent(item.sectionId(), ignored -> new MutableAccumulator())
                        .recordItem(item);
            }
        }

        List<SectionInput> sciSections = sections == null
                ? List.of()
                : sections.stream()
                        .filter(s -> "SCIENTIFIC_THINKING".equals(s.subjectCode()) && s.sectionId() != null)
                        .toList();
        boolean splitScientificThinking = sciSections.size() > 1;

        List<VactComponentBreakdown> result = new ArrayList<>(byComponent.size());
        for (Map.Entry<String, MutableAccumulator> entry : byComponent.entrySet()) {
            String component = entry.getKey();
            UnitStats stats = entry.getValue().toUnitStats();
            List<VactSubsectionBreakdown> subsections = null;

            if ("SCIENTIFIC_THINKING".equals(component) && splitScientificThinking) {
                subsections = new ArrayList<>(sciSections.size());
                for (SectionInput sec : sciSections) {
                    MutableAccumulator secAcc = sciBySection.getOrDefault(sec.sectionId(), new MutableAccumulator());
                    UnitStats subStats = secAcc.toUnitStats();
                    int qCount = sec.questionCount() != null ? sec.questionCount() : subStats.totalCount();
                    subsections.add(new VactSubsectionBreakdown(
                            sec.sectionId(),
                            qCount,
                            subStats.correctCount(),
                            subStats.partialCount(),
                            subStats.wrongCount(),
                            subStats.blankCount(),
                            subStats.totalCount()));
                }
            }

            result.add(new VactComponentBreakdown(
                    component,
                    stats.correctCount(),
                    stats.partialCount(),
                    stats.wrongCount(),
                    stats.blankCount(),
                    stats.totalCount(),
                    subsections));
        }
        return result;
    }
}
