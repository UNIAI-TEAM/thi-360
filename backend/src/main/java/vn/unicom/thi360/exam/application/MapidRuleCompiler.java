package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.exam.application.CompiledMapidRules.Cap;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.MapidComparator;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.QuotaBasis;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.TopicQuota;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Compiles the MAPID rules of a section into what the solver understands (UNI-1704 v2). The rules are the single
 * source of truth once the section has a {@code mapidMode}: the section's and blocks' own {@code nodeIds} and
 * {@code topicQuotas} are ignored, every other field stays. A section without a mode is returned untouched.
 *
 * <ul>
 *   <li>{@code ALL}: nodes and topic quotas cleared, rules ignored (they stay in the JSON).
 *   <li>{@code MATRIX}: SOURCE becomes the scope nodes of the section or block (whole-set matching), PRESENT and
 *       COUNT become required topic-quota minimums, EXACT adds a {@link Cap}, ONLY_IN_GROUP adds exclusions for
 *       every other block and, when it carries a number, behaves as COUNT on its group.
 * </ul>
 *
 * A percent is turned into units of its scope (section question count, or block units), rounded half up as the web
 * allocation code does ({@code countFromPercent}); a percent wins over a count.
 */
public final class MapidRuleCompiler {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private MapidRuleCompiler() {
    }

    /** @throws IllegalArgumentException when a rule is malformed or targets a block the section does not have */
    public static CompiledMapidRules compile(ExamSection section) {
        if (section.mapidMode() == null) {
            return new CompiledMapidRules(section, List.of(), Map.of());
        }
        Map<String, QuestionBlock> blocks = new LinkedHashMap<>();
        for (QuestionBlock block : section.blocks()) {
            blocks.put(block.id(), block);
        }
        Set<UUID> sectionSource = new LinkedHashSet<>();
        Map<String, Set<UUID>> blockSource = new LinkedHashMap<>();
        List<TopicQuota> sectionQuotas = new ArrayList<>();
        Map<String, List<TopicQuota>> blockQuotas = new LinkedHashMap<>();
        List<Cap> caps = new ArrayList<>();
        Map<String, Set<UUID>> exclusions = new LinkedHashMap<>();
        if (section.mapidMode() == MapidMode.MATRIX) {
            for (MapidRule rule : section.mapidRules()) {
                QuestionBlock target = rule.groupId() == null ? null : blocks.get(rule.groupId());
                if (rule.groupId() != null && target == null) {
                    throw new IllegalArgumentException("MAPID rule " + rule.id() + " targets an unknown group");
                }
                switch (rule.type()) {
                    case SOURCE -> (target == null ? sectionSource
                            : blockSource.computeIfAbsent(target.id(), key -> new LinkedHashSet<>()))
                            .addAll(rule.nodeIds());
                    case PRESENT -> addQuota(rule, 1, target, sectionQuotas, blockQuotas);
                    case COUNT -> count(rule, section, target, sectionQuotas, blockQuotas, caps);
                    case ONLY_IN_GROUP -> {
                        if (target == null) {
                            throw new IllegalArgumentException("MAPID rule " + rule.id() + " needs a group");
                        }
                        for (QuestionBlock other : section.blocks()) {
                            if (!other.id().equals(target.id())) {
                                exclusions.computeIfAbsent(other.id(), key -> new LinkedHashSet<>())
                                        .addAll(rule.nodeIds());
                            }
                        }
                        if (rule.count() != null || rule.percent() != null) {
                            count(rule, section, target, sectionQuotas, blockQuotas, caps);
                        }
                    }
                }
            }
        }
        boolean matrix = section.mapidMode() == MapidMode.MATRIX;
        List<QuestionBlock> effectiveBlocks = new ArrayList<>();
        for (QuestionBlock block : section.blocks()) {
            Set<UUID> source = blockSource.get(block.id());
            effectiveBlocks.add(withScopeAndQuotas(block,
                    scope(block.knowledgeScope(), matrix, source, isSet(block)),
                    blockQuotas.getOrDefault(block.id(), List.of())));
        }
        ExamSection effective = new ExamSection(section.id(), section.subjectCode(), section.questionCount(),
                scope(section.knowledgeScope(), matrix, sectionSource, true), section.answerQuotas(),
                section.matrix(), section.choiceGroup(), effectiveBlocks, sectionQuotas,
                section.audioRequirement(), section.mapidMode(), section.mapidRules());
        Map<String, List<UUID>> compiledExclusions = new LinkedHashMap<>();
        exclusions.forEach((blockId, nodes) -> compiledExclusions.put(blockId, List.copyOf(nodes)));
        return new CompiledMapidRules(effective, caps, compiledExclusions);
    }

    /**
     * Legacy nodes always go. In MATRIX the scope is rebuilt from the SOURCE rules ({@code source} may be null) and
     * {@code everyChildInScope} only follows them; in ALL every other field is kept, as it has no node to act on.
     */
    private static KnowledgeScope scope(KnowledgeScope legacy, boolean matrix, Set<UUID> source, boolean every) {
        List<String> competencies = legacy == null ? List.of() : legacy.competencyCodes();
        if (matrix && source != null && !source.isEmpty()) {
            return new KnowledgeScope(List.copyOf(source), competencies, every ? Boolean.TRUE : null);
        }
        if (legacy == null) {
            return null;
        }
        return new KnowledgeScope(List.of(), competencies, matrix ? null : legacy.everyChildInScope());
    }

    private static void count(MapidRule rule, ExamSection section, QuestionBlock target,
            List<TopicQuota> sectionQuotas, Map<String, List<TopicQuota>> blockQuotas, List<Cap> caps) {
        Integer number = rule.percent() != null
                ? fromPercent(rule.percent(), target == null ? units(section) : units(target)) : rule.count();
        if (number == null) {
            throw new IllegalArgumentException("MAPID rule " + rule.id() + " needs a count or a percent");
        }
        if (number > 0) {
            addQuota(rule, number, target, sectionQuotas, blockQuotas);
        }
        if (rule.comparator() == MapidComparator.EXACT) {
            caps.add(new Cap(rule.id(), target == null ? Cap.Scope.SUBJECT : Cap.Scope.GROUP,
                    target == null ? null : target.id(), rule.nodeIds(), number));
        }
    }

    private static void addQuota(MapidRule rule, int count, QuestionBlock target, List<TopicQuota> sectionQuotas,
            Map<String, List<TopicQuota>> blockQuotas) {
        TopicQuota quota = new TopicQuota(rule.id(), null, rule.nodeIds(), count, QuotaBasis.QUESTION,
                QuotaEnforcement.REQUIRED, List.of(), List.of(), List.of());
        (target == null ? sectionQuotas
                : blockQuotas.computeIfAbsent(target.id(), key -> new ArrayList<>())).add(quota);
    }

    /** Half up, never negative; zero when the scope has no units. */
    static int fromPercent(BigDecimal percent, int total) {
        if (total <= 0 || percent.signum() <= 0) {
            return 0;
        }
        return percent.multiply(BigDecimal.valueOf(total)).divide(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private static int units(ExamSection section) {
        return section.questionCount() == null ? 0 : section.questionCount();
    }

    private static int units(QuestionBlock block) {
        if (block.setCount() != null && block.setSize() != null) {
            return Math.multiplyExact(block.setCount(), block.setSize());
        }
        return block.questionCount() == null ? 0 : block.questionCount();
    }

    private static boolean isSet(QuestionBlock block) {
        return block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE;
    }

    private static QuestionBlock withScopeAndQuotas(QuestionBlock b, KnowledgeScope scope, List<TopicQuota> quotas) {
        return new QuestionBlock(b.id(), b.name(), b.questionType(), b.questionCount(), b.setCount(), b.setSize(),
                scope, b.matrix(), b.childAnswerType(), b.templateGroupCode(), b.answerTypeLocked(),
                b.requiredPointsPerQuestion(), b.tagCodes(), b.expectedScoring(), b.shuffleUnits(),
                b.shuffleEditable(), b.slots(), b.childConditions(), b.itemShape(), b.answerFormats(), b.omitted(),
                quotas, b.positionRange());
    }
}
