package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
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

    public record SolverInput(List<SolvePart> parts, SolverCandidates candidates) {
        public SolverInput {
            parts = List.copyOf(parts);
        }
    }

    /** Per-solve source; selected candidates remain available for preview materialization. */
    public static final class SolverCandidates implements CandidateSource {
        private final QuestionSourcePort source;
        private final Map<GroupQuota, QuestionSourcePort.Filter> filters;
        private final int fetchLimit;
        private final Map<GroupQuota, List<PathCandidate>> cache = new HashMap<>();
        private final Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> loaded = new LinkedHashMap<>();

        private SolverCandidates(QuestionSourcePort source, Map<GroupQuota, QuestionSourcePort.Filter> filters,
                int fetchLimit) {
            this.source = source;
            this.filters = Map.copyOf(filters);
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
            for (var candidate : candidates) {
                loaded.put(new QuestionSourcePort.VersionKey(candidate.questionId(), candidate.versionNo()), candidate);
                result.add(toPathCandidate(candidate));
            }
            return List.copyOf(result);
        }

        public Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> loadedCandidates() {
            return Map.copyOf(loaded);
        }

        public enum FilterDimension { ITEM_SHAPE, ANSWER_FORMAT, CHILD_CONDITION }

        /** Source counts in selection units, with one declared filter removed at a time. */
        public record FilterCount(FilterDimension dimension, int required, long withoutFilter, long withFilter) { }

        public List<FilterCount> filterCounts(GroupQuota group, int required) {
            var filter = filters.get(group);
            if (filter == null || filter.subjectId() == null || required <= 0) return List.of();
            List<FilterDimension> dimensions = new ArrayList<>();
            if (filter.itemShape() != null) dimensions.add(FilterDimension.ITEM_SHAPE);
            if (!filter.answerFormats().isEmpty()) dimensions.add(FilterDimension.ANSWER_FORMAT);
            if (!filter.childConditions().isEmpty()) dimensions.add(FilterDimension.CHILD_CONDITION);
            if (dimensions.isEmpty()) return List.of();
            var filtered = source.findCandidates(filter, fetchLimit);
            long available = eligibleCount(group, filtered);
            // If the source was truncated, it cannot establish the exact shortfall.
            if (filtered.size() >= fetchLimit || available >= required) return List.of();
            List<FilterCount> counts = new ArrayList<>();
            for (var dimension : dimensions) {
                var relaxed = new QuestionSourcePort.Filter(filter.subjectId(), filter.knowledgeGroups(),
                        filter.answerTypes(), filter.questionType(), filter.taskTypeCode(), filter.childAnswerType(),
                        filter.tagCodes(), filter.competencyCodes(), filter.requiredPointsPerQuestion(),
                        dimension == FilterDimension.ITEM_SHAPE ? null : filter.itemShape(),
                        dimension == FilterDimension.ANSWER_FORMAT ? List.of() : filter.answerFormats(),
                        dimension == FilterDimension.CHILD_CONDITION ? List.of() : filter.childConditions(),
                        filter.additionalTagGroups(), filter.additionalKnowledgeGroups(), filter.additionalCompetencyGroups());
                long without = eligibleCount(group, source.findCandidates(relaxed, fetchLimit));
                if (without >= required) counts.add(new FilterCount(dimension, required, without, available));
            }
            return List.copyOf(counts);
        }

        private static long eligibleCount(GroupQuota group, List<QuestionSourcePort.Candidate> candidates) {
            return candidates.stream().filter(candidate -> {
                var units = candidate.units();
                if (units.isEmpty()) return false;
                if (group.kind() == GroupQuota.GroupKind.SET && units.size() != group.setSize()) return false;
                if (group.kind() == GroupQuota.GroupKind.SINGLE && units.size() != 1) return false;
                if (group.expectedScoringRule() != null
                        && !group.expectedScoringRule().equals(candidate.scoringRule())) return false;
                for (var unit : units) {
                    if (group.childAnswerType() != null && !group.childAnswerType().equals(unit.answerType().name())) return false;
                    if (group.answerType() != null && !group.answerType().equals(unit.answerType().name())) return false;
                    if (group.requiredPointsPerUnit() != null && (unit.points() == null
                            || group.requiredPointsPerUnit().compareTo(unit.points()) != 0)) return false;
                }
                return true;
            }).count();
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
        List<SolvePart> parts = new ArrayList<>();
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
                    knowledgeById, descendants, subjects, examQuotas);
            if (part.codeSource() != null) {
                Map<String, SectionOnPath> pool = new LinkedHashMap<>();
                for (SectionOnPath section : sections(part.subjectPool(), false, filters, knowledge,
                        knowledgeById, descendants, subjects, examQuotas)) {
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
                        filters, knowledge, knowledgeById, descendants, subjects, examQuotas));
                if (!option.blocks().isEmpty()) {
                    if (choice || option.kind() != OptionKind.SUBJECT) {
                        throw new IllegalArgumentException("Option-level blocks require a subject option");
                    }
                    int count = option.blocks().stream().mapToInt(SolverInputBuilder::fixedBlockCount).sum();
                    ExamSection synthetic = new ExamSection(option.id() + ":blocks", option.code(), count,
                            null, null, null, null, option.blocks());
                    optionSections.addAll(sections(List.of(synthetic), false, filters, knowledge,
                            knowledgeById, descendants, subjects, examQuotas));
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
        return new SolverInput(parts, new SolverCandidates(source, filters,
                limit == Integer.MAX_VALUE ? limit : limit + 1));
    }

    private List<SectionOnPath> sections(List<ExamSection> sections, boolean choice,
            Map<GroupQuota, QuestionSourcePort.Filter> filters, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants,
            Map<String, UUID> subjects,
            Map<String, List<vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota>> examQuotas) {
        List<SectionOnPath> result = new ArrayList<>();
        for (ExamSection section : sections) {
            UUID subject = subjects.computeIfAbsent(section.subjectCode(), code ->
                    catalog.findActiveItem("SUBJECT", code).map(CatalogItemView::id).orElse(null));
            List<GroupQuota> groups = new ArrayList<>();
            for (QuestionBlock block : section.blocks()) {
                if (Boolean.TRUE.equals(block.omitted())) continue;
                GroupQuota parent = group(block, knowledge, knowledgeById, descendants);
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
                    KnowledgeScope scope = block.knowledgeScope() != null ? block.knowledgeScope() : section.knowledgeScope();
                    List<List<UUID>> subtrees = expandScope(scope, knowledge, knowledgeById, descendants);
                    List<List<List<UUID>>> slotScopes = slot == null || slot.knowledgeScope() == null
                            || slot.knowledgeScope().nodeIds().isEmpty() ? List.of()
                            : List.of(expandScope(slot.knowledgeScope(), knowledge, knowledgeById, descendants));
                    List<QuestionType> answerTypes = block.questionType() == null && section.answerQuotas() != null
                            ? section.answerQuotas().items().stream().map(a -> a.answerType()).filter(a -> a != null).toList()
                            : List.of();
                    var shape = block.itemShape() == null ? null : new QuestionSourcePort.ItemShape(
                            block.itemShape().statements(), block.itemShape().leftItems(), block.itemShape().rightItems());
                    var conditions = block.childConditions().stream().map(c -> {
                        if (c.from() == null || c.to() == null) {
                            throw new IllegalArgumentException("Child condition requires both from and to bounds");
                        }
                        return new QuestionSourcePort.ChildCondition(c.from(), c.to(), c.nodeIds());
                    }).toList();
                    List<List<String>> slotTags = slot == null || slot.tagCodes().isEmpty()
                            ? List.of() : List.of(slot.tagCodes());
                    List<List<String>> slotCompetencies = slot == null || slot.knowledgeScope() == null
                            || slot.knowledgeScope().competencyCodes().isEmpty() ? List.of()
                            : List.of(slot.knowledgeScope().competencyCodes());
                    filters.put(group, new QuestionSourcePort.Filter(subject, subtrees,
                            answerTypes, block.questionType(), null, block.childAnswerType(), block.tagCodes(),
                            scope == null ? List.of() : scope.competencyCodes(), block.requiredPointsPerQuestion(),
                            shape, block.answerFormats().stream().map(AnswerFormat::valueOf).toList(), conditions,
                            slotTags, slotScopes, slotCompetencies));
                    groups.add(group);
                }
            }
            result.add(new SectionOnPath(section.subjectCode(), section.id(), choice ? null : section.questionCount(),
                    subjectQuota(section, knowledge, knowledgeById, descendants,
                            examQuotas.getOrDefault(section.subjectCode(), List.of())), groups,
                    section.choiceGroup() == null ? null : section.choiceGroup().name()));
        }
        return result;
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

    private static GroupQuota group(QuestionBlock block, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants) {
        boolean set = block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE;
        List<CompetencyQuota> competencies = new ArrayList<>();
        if (block.matrix() != null) {
            for (var item : block.matrix().byCompetency()) {
                competencies.add(new CompetencyQuota(item.code(), integer(item.value()), unit(block.matrix())));
            }
        }
        // expectedScoring is NOT a source filter: SCORING_MISMATCH is a warning, it never blocks solve/preview.
        return new GroupQuota(block.id(), set ? GroupQuota.GroupKind.SET : GroupQuota.GroupKind.SINGLE,
                set ? null : block.questionCount(), set || block.questionType() == null ? null : block.questionType().name(),
                set ? block.setCount() : null, set ? block.setSize() : null,
                set && block.childAnswerType() != null ? block.childAnswerType().name() : null,
                matrix(block.matrix()), competencies, List.of(), block.requiredPointsPerQuestion(), null,
                null, topics(block.topicQuotas(), knowledge, knowledgeById, descendants));
    }

    private static SubjectQuota subjectQuota(ExamSection section, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants,
            List<vn.unicom.thi360.exam.domain.solver.ExamDimensionQuota> examQuotas) {
        SubjectQuota.AnswerQuotas answers = null;
        if (section.answerQuotas() != null) {
            answers = new SubjectQuota.AnswerQuotas(section.answerQuotas().items().stream()
                    .map(a -> new DimensionQuota(a.answerType().name(), integer(a.value()))).toList(),
                    section.answerQuotas().unit() == null ? QuotaUnit.COUNT
                            : QuotaUnit.valueOf(section.answerQuotas().unit().name()));
        }
        return new SubjectQuota(answers, matrix(section.matrix()),
                topics(section.topicQuotas(), knowledge, knowledgeById, descendants), examQuotas);
    }

    private static List<vn.unicom.thi360.exam.domain.solver.TopicQuota> topics(
            List<vn.unicom.thi360.exam.domain.TopicQuota> quotas, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants) {
        return quotas.stream().filter(q -> q.enforcement() != QuotaEnforcement.REFERENCE)
                .map(q -> new vn.unicom.thi360.exam.domain.solver.TopicQuota(q.id(),
                        q.nodeIds().stream().flatMap(id -> descendants.computeIfAbsent(id, key -> {
                            CatalogItemView root = knowledgeById.get(key);
                            if (root == null) return List.of();
                            return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                                    .map(CatalogItemView::id).toList();
                        }).stream()).map(UUID::toString).distinct().toList(),
                        q.count(), q.gradeCodes(), q.difficultyCodes(), q.groupIds())).toList();
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
            if (block.setCount() == null || block.setSize() == null) throw new IllegalArgumentException("Set size is missing");
            return Math.multiplyExact(block.setCount(), block.setSize());
        }
        if (block.questionCount() == null) throw new IllegalArgumentException("Group count is missing");
        return block.questionCount();
    }

    private static PathCandidate toPathCandidate(QuestionSourcePort.Candidate candidate) {
        List<CountingUnit> units = new ArrayList<>(candidate.units().size());
        for (var item : candidate.units()) {
            String child = null;
            if (candidate.setId() != null) {
                if (item.questionId() != null && !item.questionId().equals(candidate.questionId())) child = item.questionId().toString();
                else if (item.reference() != null) child = candidate.questionId() + ":" + item.reference();
            }
            units.add(new CountingUnit(child, item.answerType().name(), item.gradeCode(), item.difficultyCode(),
                    item.mapidIds() == null ? List.of() : item.mapidIds().stream().map(UUID::toString).toList(),
                    item.competencyCodes(), item.points()));
        }
        return new PathCandidate(candidate.questionId().toString(), candidate.versionNo(),
                candidate.setId() == null ? null : candidate.setId().toString(),
                candidate.scoringRule(), units);
    }
}
