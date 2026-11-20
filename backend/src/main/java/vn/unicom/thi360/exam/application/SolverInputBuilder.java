package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.AudioRequirement;
import vn.unicom.thi360.exam.domain.BlockMatrix;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.CellQuota;
import vn.unicom.thi360.exam.domain.solver.CompetencyQuota;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.DimensionQuota;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.MatrixQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.QuotaUnit;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SlotParentQuota;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectQuota;
import vn.unicom.thi360.exam.domain.solver.TopicCap;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.AnswerFormat;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Translates a working exam structure into the shared checker/preview solver input. */
@Component
public class SolverInputBuilder {
    private final CatalogLookup catalog;
    private final QuestionSourcePort source;
    private final SolverLimits limits;

    public SolverInputBuilder(CatalogLookup catalog, QuestionSourcePort source, SolverLimits limits) {
        this.catalog = catalog;
        this.source = source;
        this.limits = limits;
    }

    /**
     * {@code mapid}: compiled MAPID rules by section id, only for sections that have a {@code mapidMode}. The caps
     * and exclusions in it are data only until the solver and the source filter read them.
     */
    public record SolverInput(List<SolvePart> parts, SolverCandidates candidates,
            Map<String, CompiledMapidRules> mapid) {
        public SolverInput {
            parts = List.copyOf(parts);
            mapid = Map.copyOf(mapid);
        }

        public SolverInput(List<SolvePart> parts, SolverCandidates candidates) {
            this(parts, candidates, Map.of());
        }

        /** The same candidates and compiled rules over other parts. */
        public SolverInput withParts(List<SolvePart> other) {
            return new SolverInput(other, candidates, mapid);
        }
    }

    /** Per-solve source; selected candidates remain available for preview materialization. */
    public static final class SolverCandidates implements CandidateSource {
        private final QuestionSourcePort source;
        private final Map<GroupQuota, QuestionSourcePort.Filter> filters;
        private final java.util.Set<GroupQuota> setRowGroups;
        private final int fetchLimit;
        private final Map<GroupQuota, List<PathCandidate>> cache = new HashMap<>();
        private final Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> loaded = new LinkedHashMap<>();

        private SolverCandidates(QuestionSourcePort source, Map<GroupQuota, QuestionSourcePort.Filter> filters,
                java.util.Set<GroupQuota> setRowGroups, int fetchLimit) {
            this.source = source;
            this.filters = Map.copyOf(filters);
            this.setRowGroups = java.util.Set.copyOf(setRowGroups);
            this.fetchLimit = fetchLimit;
        }

        @Override
        public List<PathCandidate> candidatesFor(GroupQuota group) {
            return cache.computeIfAbsent(group, this::fetch);
        }

        private List<PathCandidate> fetch(GroupQuota group) {
            var filter = filters.get(group);
            if (filter == null || filter.subjectId() == null) return List.of();
            List<QuestionSourcePort.Candidate> candidates = source.findCandidates(filter, fetchLimit);
            List<PathCandidate> result = new ArrayList<>(candidates.size());
            boolean setRow = setRowGroups.contains(group);
            for (var candidate : candidates) {
                loaded.put(new QuestionSourcePort.VersionKey(candidate.questionId(), candidate.versionNo()), candidate);
                result.add(toPathCandidate(candidate, setRow));
            }
            return List.copyOf(result);
        }

        public Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> loadedCandidates() {
            return Map.copyOf(loaded);
        }

        /**
         * Single questions of the subject that match the source nodes of {@code group} (the group's own knowledge
         * scope and exclusions, no other condition): what the admin has when the group needs whole sets and
         * only scattered questions carry the code (UNI-2758). -1 when the pool is truncated and so unknown.
         */
        public long singlesFor(GroupQuota group) {
            var filter = filters.get(group);
            if (filter == null || filter.subjectId() == null) return -1;
            var singles = new QuestionSourcePort.Filter(filter.subjectId(), filter.knowledgeGroups(), List.of(), null,
                    null, null, List.of(), List.of(), null, null, List.of(), List.of(), List.of(),
                    filter.additionalKnowledgeGroups(), List.of(), null, filter.additionalEveryChildInScope(),
                    QuestionSourcePort.AudioRequirement.ANY, filter.excludedKnowledge(), null,
                    filter.additionalEveryChildSameNode());
            var found = source.findCandidates(singles, fetchLimit);
            if (found.size() >= fetchLimit) return -1;
            return found.stream().filter(candidate -> candidate.setId() == null).count();
        }

        public enum FilterDimension { ITEM_SHAPE, ANSWER_FORMAT, CHILD_CONDITION, AUDIO, POINTS }

        /** Source counts in selection units, with one declared filter removed at a time. */
        public record FilterCount(FilterDimension dimension, int required, long withoutFilter, long withFilter) { }

        public List<FilterCount> filterCounts(GroupQuota group, int required) {
            var filter = filters.get(group);
            if (filter == null || filter.subjectId() == null || required <= 0) return List.of();
            List<FilterDimension> dimensions = new ArrayList<>();
            if (filter.itemShape() != null) dimensions.add(FilterDimension.ITEM_SHAPE);
            if (!filter.answerFormats().isEmpty()) dimensions.add(FilterDimension.ANSWER_FORMAT);
            if (!filter.childConditions().isEmpty()) dimensions.add(FilterDimension.CHILD_CONDITION);
            if (filter.audioRequirement() != QuestionSourcePort.AudioRequirement.ANY) dimensions.add(FilterDimension.AUDIO);
            if (filter.requiredPointsPerQuestion() != null) dimensions.add(FilterDimension.POINTS);
            if (dimensions.isEmpty()) return List.of();
            var filtered = source.findCandidates(filter, fetchLimit);
            // If the source was truncated, it cannot establish the exact shortfall.
            if (filtered.size() >= fetchLimit) return List.of();
            long available = eligibleUnits(group, filtered, required, true);
            if (available >= required) return List.of();
            List<FilterCount> counts = new ArrayList<>();
            for (var dimension : dimensions) {
                var relaxed = relaxed(filter, dimension);
                long without = eligibleUnits(group, source.findCandidates(relaxed, fetchLimit), required,
                        dimension != FilterDimension.POINTS);
                if (without >= required) counts.add(new FilterCount(dimension, required, without, available));
            }
            return List.copyOf(counts);
        }

        /** The same filter with one declared dimension removed. */
        private static QuestionSourcePort.Filter relaxed(QuestionSourcePort.Filter filter, FilterDimension dimension) {
            return new QuestionSourcePort.Filter(filter.subjectId(), filter.knowledgeGroups(),
                    filter.answerTypes(), filter.questionType(), filter.taskTypeCode(), filter.childAnswerType(),
                    filter.tagCodes(), filter.competencyCodes(),
                    dimension == FilterDimension.POINTS ? null : filter.requiredPointsPerQuestion(),
                    dimension == FilterDimension.ITEM_SHAPE ? null : filter.itemShape(),
                    dimension == FilterDimension.ANSWER_FORMAT ? List.of() : filter.answerFormats(),
                    dimension == FilterDimension.CHILD_CONDITION ? List.of() : filter.childConditions(),
                    filter.additionalTagGroups(), filter.additionalKnowledgeGroups(),
                    filter.additionalCompetencyGroups(), filter.everyChildInScope(),
                    filter.additionalEveryChildInScope(),
                    dimension == FilterDimension.AUDIO ? QuestionSourcePort.AudioRequirement.ANY
                            : filter.audioRequirement(), filter.excludedKnowledge(), filter.everyChildSameNode(),
                    filter.additionalEveryChildSameNode());
        }

        /**
         * Eligible supply in the units the checker compares: candidate roots for SINGLE and SET
         * groups, counting units for FLEXIBLE_SET. A flexible pool only counts the largest subset
         * sum of whole set sizes up to {@code required}, so a shortage is attributed to a filter
         * only when the relaxed pool can actually compose the target.
         */
        private static long eligibleUnits(GroupQuota group, List<QuestionSourcePort.Candidate> candidates,
                int required, boolean checkPoints) {
            if (group.kind() != GroupQuota.GroupKind.FLEXIBLE_SET) {
                return candidates.stream().filter(candidate -> eligible(group, candidate, checkPoints)).count();
            }
            long supply = 0;
            for (var candidate : candidates) {
                if (eligible(group, candidate, checkPoints)) supply += candidate.units().size();
            }
            if (supply == 0) return 0;
            // The target can never be composed beyond the supply, so bound the state by it.
            int limit = (int) Math.min(required, supply);
            boolean[] reachable = new boolean[limit + 1];
            reachable[0] = true;
            for (var candidate : candidates) {
                if (!eligible(group, candidate, checkPoints)) continue;
                int size = candidate.units().size();
                for (int sum = limit; sum >= size; sum--) {
                    reachable[sum] |= reachable[sum - size];
                }
            }
            for (int sum = limit; sum > 0; sum--) {
                if (reachable[sum]) return sum;
            }
            return 0;
        }

        private static boolean eligible(GroupQuota group, QuestionSourcePort.Candidate candidate, boolean checkPoints) {
            var units = candidate.units();
            if (units.isEmpty()) return false;
            if (group.kind() == GroupQuota.GroupKind.SET && units.size() != group.setSize()) return false;
            if (group.kind() == GroupQuota.GroupKind.SINGLE && units.size() != 1) return false;
            if (group.expectedScoringRule() != null
                    && !group.expectedScoringRule().equals(candidate.scoringRule())) return false;
            for (var unit : units) {
                if (group.childAnswerType() != null && !group.childAnswerType().equals(unit.answerType().name())) return false;
                if (group.answerType() != null && !group.answerType().equals(unit.answerType().name())) return false;
                if (checkPoints && group.requiredPointsPerUnit() != null && (unit.points() == null
                        || group.requiredPointsPerUnit().compareTo(unit.points()) != 0)) return false;
            }
            return true;
        }
    }

    /** Build all paths without reading candidates. Invalid draft structures may throw IllegalArgumentException. */
    public SolverInput build(UUID examKindId, ExamStructureDocument document) {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        Map<UUID, CatalogItemView> knowledgeById = new HashMap<>();
        for (var item : knowledge) knowledgeById.put(item.id(), item);
        Map<UUID, List<UUID>> descendants = new HashMap<>();
        Map<String, UUID> subjects = new HashMap<>();
        Map<GroupQuota, QuestionSourcePort.Filter> filters = new LinkedHashMap<>();
        java.util.Set<GroupQuota> setRowGroups = new java.util.HashSet<>();
        List<SolvePart> parts = new ArrayList<>();
        Map<String, CompiledMapidRules> mapid = new LinkedHashMap<>();
        Map<String, List<vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota>> examQuotas = new HashMap<>();
        for (var quota : document.examQuotas()) {
            if (quota.enforcement() != QuotaEnforcement.REQUIRED || quota.subjectCodes().size() != 1) continue;
            String subject = quota.subjectCodes().getFirst();
            if (quota.excludeSubjectCodes().contains(subject)) continue;
            var converted = new vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota(quota.id(),
                    vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota.Dimension.valueOf(quota.dimension().name()),
                    quota.buckets().stream().map(b -> new vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota.Bucket(
                            b.codes(), b.count(), b.percent())).toList());
            examQuotas.computeIfAbsent(subject, ignored -> new ArrayList<>()).add(converted);
        }
        for (ExamPart part : document.parts().stream()
                .sorted(java.util.Comparator.comparing(ExamPart::order,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList()) {
            List<SectionOnPath> common = sections(part.sections(), false, filters, knowledge,
                    knowledgeById, descendants, subjects, examQuotas, setRowGroups, mapid);
            if (part.codeSource() != null) {
                Map<String, SectionOnPath> pool = new LinkedHashMap<>();
                for (SectionOnPath section : sections(part.subjectPool(), false, filters, knowledge,
                        knowledgeById, descendants, subjects, examQuotas, setRowGroups, mapid)) {
                    pool.put(section.subjectCode(), section);
                }
                List<SolveOption> codeOptions = new ArrayList<>();
                for (var combination : catalog.listCombinations(examKindId)) {
                    String branch = part.codeSource().branch();
                    if (branch != null && (combination.branch() == null || !branch.equals(combination.branch().name()))) continue;
                    List<SectionOnPath> ordered = new ArrayList<>();
                    for (String code : combination.subjectCodes()) {
                        SectionOnPath section = pool.get(code);
                        if (section == null) throw new IllegalArgumentException("Combination subject missing: " + code);
                        ordered.add(section);
                    }
                    codeOptions.add(new SolveOption.Sections(part.id() + ":" + combination.itemCode(), ordered));
                }
                parts.add(new SolvePart.Choice(part.id(), common, codeOptions, 1));
                continue;
            }
            if (part.participation() == null || part.participation().mode() != ParticipationMode.CHOICE) {
                parts.add(new SolvePart.Fixed(part.id(), common));
                continue;
            }
            List<SolveOption> options = new ArrayList<>();
            for (ExamPartOption option : part.options()) {
                boolean choice = option.kind() == OptionKind.SUBJECT_CHOICE;
                List<SectionOnPath> optionSections = new ArrayList<>(sections(option.sections(), choice,
                        filters, knowledge, knowledgeById, descendants, subjects, examQuotas, setRowGroups, mapid));
                if (!option.blocks().isEmpty()) {
                    if (choice || option.kind() != OptionKind.SUBJECT) {
                        throw new IllegalArgumentException("Option-level blocks require a subject option");
                    }
                    int count = option.blocks().stream().mapToInt(SolverInputBuilder::fixedBlockCount).sum();
                    ExamSection synthetic = new ExamSection(option.id() + ":blocks", option.code(), count,
                            null, null, null, null, option.blocks());
                    optionSections.addAll(sections(List.of(synthetic), false, filters, knowledge,
                            knowledgeById, descendants, subjects, examQuotas, setRowGroups, mapid));
                }
                if (choice) {
                    if (option.chooseSubjects() == null || option.countsByPick() == null) {
                        throw new IllegalArgumentException("Subject choice is incomplete");
                    }
                    options.add(new SubjectChoice(option.id(), optionSections, option.chooseSubjects(),
                            option.countsByPick()));
                } else {
                    options.add(new SolveOption.Sections(option.id(), optionSections));
                }
            }
            Integer choose = part.participation().choose();
            if (choose == null) throw new IllegalArgumentException("Choice count is missing");
            parts.add(new SolvePart.Choice(part.id(), common, options, choose));
        }
        int limit = limits.maxCandidatesPerBlock();
        return new SolverInput(parts, new SolverCandidates(source, filters, setRowGroups,
                limit == Integer.MAX_VALUE ? limit : limit + 1), mapid);
    }

    private List<SectionOnPath> sections(List<ExamSection> sections, boolean choice,
            Map<GroupQuota, QuestionSourcePort.Filter> filters, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants,
            Map<String, UUID> subjects,
            Map<String, List<vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota>> examQuotas,
            java.util.Set<GroupQuota> setRowGroups, Map<String, CompiledMapidRules> mapid) {
        List<SectionOnPath> result = new ArrayList<>();
        for (ExamSection declared : sections) {
            CompiledMapidRules compiled = MapidRuleCompiler.compile(declared);
            if (declared.mapidMode() != null) mapid.put(declared.id(), compiled);
            ExamSection section = compiled.effectiveSection();
            boolean matrix = section.mapidMode() == vn.unicom.thi360.exam.domain.MapidMode.MATRIX;
            UUID subject = subjects.computeIfAbsent(section.subjectCode(), code ->
                    catalog.findActiveItem("SUBJECT", code).map(CatalogItemView::id).orElse(null));
            List<GroupQuota> groups = new ArrayList<>();
            for (QuestionBlock block : section.blocks()) {
                if (Boolean.TRUE.equals(block.omitted())) continue;
                GroupQuota parent = group(block, knowledge, knowledgeById, descendants, matrix);
                int count = block.slots().isEmpty() ? 1 : block.setCount();
                SlotParentQuota shared = block.slots().isEmpty() ? null
                        : new SlotParentQuota(block.id(), parent.matrix(), parent.competencies(),
                                parent.mapidGroups(), parent.topicQuotas());
                for (int index = 1; index <= count; index++) {
                    final int slotIndex = index;
                    var slot = block.slots().stream().filter(s -> s.index() == slotIndex).findFirst().orElse(null);
                    GroupQuota group = shared == null ? parent : new GroupQuota(
                            block.id() + ":slot:" + index, GroupQuota.GroupKind.SET, null, null,
                            1, parent.setSize(), parent.childAnswerType(), null, List.of(), List.of(),
                            parent.requiredPointsPerUnit(), null, shared);
                    if (AnswerQuotaCounting.countsInSetRow(section, block)) setRowGroups.add(group);
                    KnowledgeScope scope = EffectiveKnowledgeScope.of(block.knowledgeScope(), section.knowledgeScope());
                    // SOURCE rules (BA 3, "chỉ lấy"): every rule of the subject and of the group must match, A|B|C inside
                    // one rule stays an OR. The first rule rides in knowledgeGroups, the others in additional groups.
                    List<List<UUID>> allSources = compiled.sourcesOf(block.id());
                    List<List<UUID>> subtrees = allSources.isEmpty()
                            ? expandScope(scope, knowledge, knowledgeById, descendants)
                            : expandNodes(allSources.getFirst(), knowledge, knowledgeById, descendants);
                    List<List<List<UUID>>> slotScopes = slot == null || slot.knowledgeScope() == null
                            || slot.knowledgeScope().nodeIds().isEmpty() ? List.of()
                            : List.of(expandScope(slot.knowledgeScope(), knowledge, knowledgeById, descendants));
                    List<QuestionType> answerTypes = block.questionType() == null && section.answerQuotas() != null
                            ? section.answerQuotas().items().stream().map(a -> a.answerType()).filter(a -> a != null).toList()
                            : List.of();
                    var shape = shape(block.itemShape());
                    var conditions = childConditions(block);
                    List<List<String>> slotTags = slot == null || slot.tagCodes().isEmpty()
                            ? List.of() : List.of(slot.tagCodes());
                    List<List<String>> slotCompetencies = slot == null || slot.knowledgeScope() == null
                            || slot.knowledgeScope().competencyCodes().isEmpty() ? List.of()
                            : List.of(slot.knowledgeScope().competencyCodes());
                    List<List<List<UUID>>> additionalKnowledge = new ArrayList<>(slotScopes);
                    List<Boolean> additionalEvery = new ArrayList<>(
                            slotScopes.isEmpty() ? List.of() : List.of(Boolean.TRUE.equals(slot.knowledgeScope().everyChildInScope())));
                    List<Boolean> additionalSameNode = new ArrayList<>(Collections.nCopies(slotScopes.size(), Boolean.FALSE));
                    for (int s = 1; s < allSources.size(); s++) {
                        additionalKnowledge.add(expandNodes(allSources.get(s), knowledge, knowledgeById, descendants));
                        additionalEvery.add(Boolean.TRUE);
                        additionalSameNode.add(Boolean.TRUE);
                    }
                    List<UUID> excluded = withDescendants(compiled.exclusions().getOrDefault(block.id(), List.of()),
                            knowledge, knowledgeById, descendants);
                    filters.put(group, new QuestionSourcePort.Filter(subject, subtrees,
                            answerTypes, block.questionType(), null, block.childAnswerType(), block.tagCodes(),
                            scope == null ? List.of() : scope.competencyCodes(), block.requiredPointsPerQuestion(),
                            shape, answerFormats(block), conditions,
                            slotTags, additionalKnowledge, slotCompetencies, scope == null ? null : scope.everyChildInScope(),
                            additionalEvery, audio(section.audioRequirement()), excluded,
                            // BA 5: a set counts for a SOURCE rule when all its children carry the SAME code of it.
                            allSources.isEmpty() ? null : Boolean.TRUE, additionalSameNode));
                    groups.add(group);
                }
            }
            result.add(new SectionOnPath(section.subjectCode(), section.id(), choice ? null : section.questionCount(),
                    subjectQuota(section, knowledge, knowledgeById, descendants,
                            examQuotas.getOrDefault(section.subjectCode(), List.of()), compiled.caps(),
                            matrix), groups,
                    section.choiceGroup() == null ? null : section.choiceGroup().name()));
        }
        return result;
    }

    static QuestionSourcePort.ItemShape shape(vn.unicom.thi360.exam.domain.ItemShape shape) {
        return shape == null ? null : new QuestionSourcePort.ItemShape(shape.statements(), shape.leftItems(),
                shape.rightItems(), shape.options());
    }

    /** Positional child conditions of a block as the source filter states them. */
    static List<QuestionSourcePort.ChildCondition> childConditions(QuestionBlock block) {
        return block.childConditions().stream().map(c -> {
            if (c.from() == null || c.to() == null) {
                throw new IllegalArgumentException("Child condition requires both from and to bounds");
            }
            return new QuestionSourcePort.ChildCondition(c.from(), c.to(), c.nodeIds(), c.answerType(),
                    shape(c.itemShape()));
        }).toList();
    }

    static List<AnswerFormat> answerFormats(QuestionBlock block) {
        return block.answerFormats().stream().map(AnswerFormat::valueOf).toList();
    }

    /** An unset requirement is {@code ANY}; every group of a section inherits the section's requirement. */
    static QuestionSourcePort.AudioRequirement audio(AudioRequirement requirement) {
        return requirement == null ? QuestionSourcePort.AudioRequirement.ANY
                : QuestionSourcePort.AudioRequirement.valueOf(requirement.name());
    }

    private static List<List<UUID>> expandScope(KnowledgeScope scope, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants) {
        if (scope == null) return List.of();
        return scope.nodeIds().stream().map(id -> descendants.computeIfAbsent(id, key -> {
            CatalogItemView root = knowledgeById.get(key);
            if (root == null) return List.of();
            return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                    .map(CatalogItemView::id).toList();
        })).toList();
    }

    /** One OR-group of a SOURCE rule: every node with its descendants (an unknown node is an empty subtree). */
    static List<List<UUID>> expandNodes(List<UUID> nodes, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants) {
        return expandScope(new KnowledgeScope(nodes, List.of(), null), knowledge, knowledgeById, descendants);
    }

    private static GroupQuota group(QuestionBlock block, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants,
            boolean mapid) {
        boolean set = block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE;
        List<CompetencyQuota> competencies = new ArrayList<>();
        if (block.matrix() != null) {
            for (var item : block.matrix().byCompetency()) {
                competencies.add(new CompetencyQuota(item.code(), integer(item.value()), unit(block.matrix())));
            }
        }
        // expectedScoring is NOT a source filter: SCORING_MISMATCH is a warning, it never blocks solve/preview.
        boolean flexibleSet = set && block.setCount() == null && block.setSize() == null;
        return new GroupQuota(block.id(), flexibleSet ? GroupQuota.GroupKind.FLEXIBLE_SET
                        : set ? GroupQuota.GroupKind.SET : GroupQuota.GroupKind.SINGLE,
                !set || flexibleSet ? block.questionCount() : null,
                set || block.questionType() == null ? null : block.questionType().name(),
                set ? block.setCount() : null, set ? block.setSize() : null,
                set && block.childAnswerType() != null ? block.childAnswerType().name() : null,
                matrix(block.matrix()), competencies, List.of(), block.requiredPointsPerQuestion(), null,
                null, topics(block.topicQuotas(), knowledge, knowledgeById, descendants, mapid));
    }

    private static SubjectQuota subjectQuota(ExamSection section, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants,
            List<vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota> examQuotas,
            List<CompiledMapidRules.Cap> caps, boolean mapid) {
        SubjectQuota.AnswerQuotas answers = null;
        if (section.answerQuotas() != null) {
            answers = new SubjectQuota.AnswerQuotas(section.answerQuotas().items().stream()
                    .map(a -> new DimensionQuota(a.answerType().name(), integer(a.value()))).toList(),
                    section.answerQuotas().unit() == null ? QuotaUnit.COUNT
                            : QuotaUnit.valueOf(section.answerQuotas().unit().name()));
        }
        return new SubjectQuota(answers, matrix(section.matrix()),
                topics(section.topicQuotas(), knowledge, knowledgeById, descendants, mapid), examQuotas,
                caps.stream().map(cap -> new TopicCap(cap.ruleId(),
                        cap.scope() == CompiledMapidRules.Cap.Scope.GROUP ? cap.groupId() : null,
                        withDescendants(cap.nodeIds(), knowledge, knowledgeById, descendants).stream()
                                .map(UUID::toString).toList(), cap.max())).toList(),
                mapid);
    }

    /** The nodes and every descendant of each, without duplicates; unknown nodes stay out, as for topic quotas. */
    private static List<UUID> withDescendants(List<UUID> nodes, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants) {
        return nodes.stream().flatMap(id -> descendants.computeIfAbsent(id, key -> {
            CatalogItemView root = knowledgeById.get(key);
            if (root == null) return List.of();
            return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                    .map(CatalogItemView::id).toList();
        }).stream()).distinct().toList();
    }

    private static List<vn.unicom.thi360.exam.domain.solver.TopicQuota> topics(
            List<vn.unicom.thi360.exam.domain.TopicQuota> quotas, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants,
            boolean mapid) {
        return quotas.stream().filter(q -> q.enforcement() != QuotaEnforcement.REFERENCE)
                .map(q -> new vn.unicom.thi360.exam.domain.solver.TopicQuota(q.id(),
                        q.nodeIds().stream().flatMap(id -> descendants.computeIfAbsent(id, key -> {
                            CatalogItemView root = knowledgeById.get(key);
                            if (root == null) return List.of();
                            return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                                    .map(CatalogItemView::id).toList();
                        }).stream()).map(UUID::toString).distinct().toList(),
                        q.count(), q.gradeCodes(), q.difficultyCodes(), q.groupIds(), mapid)).toList();
    }

    private static MatrixQuota matrix(BlockMatrix matrix) {
        if (matrix == null) return null;
        boolean gradeRequired = matrix.gradeEnforcement() == QuotaEnforcement.REQUIRED;
        boolean difficultyRequired = matrix.difficultyEnforcement() == QuotaEnforcement.REQUIRED;
        if (!gradeRequired && !difficultyRequired) return null;
        if (matrix.mode() == null) throw new IllegalArgumentException("Matrix mode is missing");
        if (matrix.mode().name().equals("COMBINED")) {
            if (!gradeRequired || !difficultyRequired) {
                throw new IllegalArgumentException("Combined matrix enforcement must match");
            }
            return new MatrixQuota.Combined(matrix.cells().stream()
                    .map(c -> new CellQuota(c.gradeCode(), c.difficultyCode(), integer(c.value()))).toList(), unit(matrix));
        }
        return new MatrixQuota.Separate(
                gradeRequired ? matrix.byGrade().stream().map(v -> new DimensionQuota(v.code(), integer(v.value()))).toList() : List.of(),
                difficultyRequired ? matrix.byDifficulty().stream().map(v -> new DimensionQuota(v.code(), integer(v.value()))).toList() : List.of(),
                unit(matrix));
    }

    private static QuotaUnit unit(BlockMatrix matrix) {
        return matrix.unit() == null ? QuotaUnit.COUNT : QuotaUnit.valueOf(matrix.unit().name());
    }

    private static int integer(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Quota value is missing");
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Quota value must be an integer", e);
        }
    }

    private static int fixedBlockCount(QuestionBlock block) {
        if (Boolean.TRUE.equals(block.omitted())) return 0;
        if (block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE) {
            if (block.setCount() == null && block.setSize() == null && block.questionCount() != null) {
                return block.questionCount();
            }
            if (block.setCount() == null || block.setSize() == null) throw new IllegalArgumentException("Set size is missing");
            return Math.multiplyExact(block.setCount(), block.setSize());
        }
        if (block.questionCount() == null) throw new IllegalArgumentException("Group count is missing");
        return block.questionCount();
    }

    /**
     * {@code setRow}: the group belongs to a subject whose answer quota has a QUESTION_SET row, so every child unit
     * counts toward that row. The unit keeps its real answer type for the group filters.
     */
    private static PathCandidate toPathCandidate(QuestionSourcePort.Candidate candidate, boolean setRow) {
        List<CountingUnit> units = new ArrayList<>(candidate.units().size());
        for (var item : candidate.units()) {
            String child = null;
            if (candidate.setId() != null) {
                if (item.questionId() != null && !item.questionId().equals(candidate.questionId())) child = item.questionId().toString();
                else if (item.reference() != null) child = candidate.questionId() + ":" + item.reference();
            }
            units.add(new CountingUnit(child, item.answerType().name(), item.gradeCode(), item.difficultyCode(),
                    item.mapidIds() == null ? List.of() : item.mapidIds().stream().map(UUID::toString).toList(),
                    item.competencyCodes(), item.points(), setRow ? AnswerQuotaCounting.QUESTION_SET : null));
        }
        return new PathCandidate(candidate.questionId().toString(), candidate.versionNo(),
                candidate.setId() == null ? null : candidate.setId().toString(),
                candidate.scoringRule(), units);
    }
}
