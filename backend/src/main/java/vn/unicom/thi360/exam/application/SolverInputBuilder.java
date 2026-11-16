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
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectQuota;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
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
    }

    /** Build all paths without reading candidates. Invalid draft structures may throw IllegalArgumentException. */
    public SolverInput build(UUID examId, ExamStructureDocument document) {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        Map<UUID, CatalogItemView> knowledgeById = new HashMap<>();
        for (var item : knowledge) knowledgeById.put(item.id(), item);
        Map<UUID, List<UUID>> descendants = new HashMap<>();
        Map<String, UUID> subjects = new HashMap<>();
        Map<GroupQuota, QuestionSourcePort.Filter> filters = new LinkedHashMap<>();
        List<SolvePart> parts = new ArrayList<>();
        for (ExamPart part : document.parts().stream()
                .sorted(java.util.Comparator.comparing(ExamPart::order,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList()) {
            List<SectionOnPath> common = sections(part.sections(), false, filters, knowledge,
                    knowledgeById, descendants, subjects);
            if (part.participation() == null || part.participation().mode() != ParticipationMode.CHOICE) {
                parts.add(new SolvePart.Fixed(part.id(), common));
                continue;
            }
            List<SolveOption> options = new ArrayList<>();
            for (ExamPartOption option : part.options()) {
                boolean choice = option.kind() == OptionKind.SUBJECT_CHOICE;
                List<SectionOnPath> optionSections = new ArrayList<>(sections(option.sections(), choice,
                        filters, knowledge, knowledgeById, descendants, subjects));
                if (!option.blocks().isEmpty()) {
                    if (choice || option.kind() != OptionKind.SUBJECT) {
                        throw new IllegalArgumentException("Option-level blocks require a subject option");
                    }
                    int count = option.blocks().stream().mapToInt(SolverInputBuilder::fixedBlockCount).sum();
                    ExamSection synthetic = new ExamSection(option.id() + ":blocks", option.code(), count,
                            null, null, null, null, null, option.blocks());
                    optionSections.addAll(sections(List.of(synthetic), false, filters, knowledge,
                            knowledgeById, descendants, subjects));
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
            Map<String, UUID> subjects) {
        List<SectionOnPath> result = new ArrayList<>();
        for (ExamSection section : sections) {
            UUID subject = subjects.computeIfAbsent(section.subjectCode(), code ->
                    catalog.findActiveItem("SUBJECT", code).map(CatalogItemView::id).orElse(null));
            List<GroupQuota> groups = new ArrayList<>();
            for (QuestionBlock block : section.blocks()) {
                GroupQuota group = group(block);
                KnowledgeScope scope = block.knowledgeScope() != null ? block.knowledgeScope() : section.knowledgeScope();
                List<List<UUID>> subtrees = scope == null ? List.of() : scope.nodeIds().stream()
                        .map(id -> descendants.computeIfAbsent(id, key -> {
                            CatalogItemView root = knowledgeById.get(key);
                            if (root == null) return List.of();
                            return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                                    .map(CatalogItemView::id).toList();
                        })).toList();
                QuestionSourcePort.Match match = scope != null && scope.match() != null
                        && scope.match().name().equals("ALL") ? QuestionSourcePort.Match.ALL : QuestionSourcePort.Match.ANY;
                List<QuestionType> answerTypes = block.questionType() == null && section.answerQuotas() != null
                        ? section.answerQuotas().items().stream().map(a -> a.answerType()).filter(a -> a != null).toList()
                        : List.of();
                filters.put(group, new QuestionSourcePort.Filter(subject, subtrees, match, answerTypes,
                        block.questionType(), block.taskTypeCode(), block.childAnswerType(), block.tagCodes(),
                        scope == null ? List.of() : scope.competencyCodes(), block.requiredPointsPerQuestion()));
                groups.add(group);
            }
            result.add(new SectionOnPath(section.subjectCode(), section.id(), choice ? null : section.questionCount(),
                    subjectQuota(section), groups, section.choiceGroup() == null ? null : section.choiceGroup().name()));
        }
        return result;
    }

    private static GroupQuota group(QuestionBlock block) {
        boolean set = block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE;
        List<CompetencyQuota> competencies = new ArrayList<>();
        if (block.matrix() != null) {
            for (var item : block.matrix().byCompetency()) {
                competencies.add(new CompetencyQuota(item.code(), integer(item.value()), unit(block.matrix())));
            }
        }
        String scoring = null;
        if (block.expectedScoring() != null) {
            scoring = block.expectedScoring().trueFalseMethod() != null
                    ? block.expectedScoring().trueFalseMethod() : block.expectedScoring().multipleChoiceRule();
        }
        return new GroupQuota(block.id(), set ? GroupQuota.GroupKind.SET : GroupQuota.GroupKind.SINGLE,
                set ? null : block.questionCount(), set || block.questionType() == null ? null : block.questionType().name(),
                set ? block.setCount() : null, set ? block.setSize() : null,
                set && block.childAnswerType() != null ? block.childAnswerType().name() : null,
                matrix(block.matrix()), competencies, List.of(), block.requiredPointsPerQuestion(), scoring);
    }

    private static SubjectQuota subjectQuota(ExamSection section) {
        SubjectQuota.AnswerQuotas answers = null;
        if (section.answerQuotas() != null) {
            answers = new SubjectQuota.AnswerQuotas(section.answerQuotas().items().stream()
                    .map(a -> new DimensionQuota(a.answerType().name(), integer(a.value()))).toList(),
                    section.answerQuotas().unit() == null ? QuotaUnit.COUNT
                            : QuotaUnit.valueOf(section.answerQuotas().unit().name()));
        }
        return new SubjectQuota(answers, matrix(section.matrix()));
    }

    private static MatrixQuota matrix(BlockMatrix matrix) {
        if (matrix == null) return null;
        if (matrix.mode() == null) throw new IllegalArgumentException("Matrix mode is missing");
        if (matrix.mode().name().equals("COMBINED")) {
            return new MatrixQuota.Combined(matrix.cells().stream()
                    .map(c -> new CellQuota(c.gradeCode(), c.difficultyCode(), integer(c.value()))).toList(), unit(matrix));
        }
        return new MatrixQuota.Separate(matrix.byGrade().stream().map(v -> new DimensionQuota(v.code(), integer(v.value()))).toList(),
                matrix.byDifficulty().stream().map(v -> new DimensionQuota(v.code(), integer(v.value()))).toList(), unit(matrix));
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
                candidate.setId() == null ? null : candidate.setId().toString(), candidate.taskTypeCode(),
                candidate.scoringRule(), units);
    }
}
