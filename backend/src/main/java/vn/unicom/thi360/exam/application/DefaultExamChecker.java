package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SolveOutcome;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SolveStats;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Runs exact configuration checks and the shared solver without holding a database transaction. */
@Service
public class DefaultExamChecker implements ExamChecker {
    private final SolverInputBuilder builder;
    private final SolverLimits limits;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final CatalogLookup catalog;
    private final QuestionSourcePort source;
    private final ExamCheckUnitEngine units;

    public DefaultExamChecker(SolverInputBuilder builder, SolverLimits limits,
            Clock clock, ObjectMapper mapper, CatalogLookup catalog, QuestionSourcePort source,
            ExamCheckUnitEngine units) {
        this.builder = builder;
        this.limits = limits;
        this.clock = clock;
        this.mapper = mapper;
        this.catalog = catalog;
        this.source = source;
        this.units = units;
    }

    @Override
    public Evaluation check(ExamCheckSnapshotReader.Snapshot snapshot, Instant deadline, Duration budget, ExamCheckScope scope) {
        var combinations = ExamCheckUnitCombinations.forStructure(catalog, snapshot.exam().getKindItemId(), snapshot.structure());
        var rules = new ExamCheckRules(mapper, snapshot.exam(), snapshot.structure(), combinations,
                ExamCheckUnitCombinations.topicParents(catalog, snapshot.structure()));
        Set<String> requested = scope.sections(snapshot.structure());
        List<ObjectNode> common = new ArrayList<>(rules.common().stream()
                .filter(issue -> inScope(issue, scope, requested)).toList());
        List<ObjectNode> allInvalid = rules.unrepresentable();
        List<ObjectNode> invalid = allInvalid.stream()
                .filter(issue -> inScope(issue, scope, requested)).toList();
        var assembler = new ExamCheckAssembler(mapper, clock, limits);
        Map<String, List<ObjectNode>> invalidOptions = new LinkedHashMap<>();
        boolean commonInvalid = false;
        for (ObjectNode issue : invalid) {
            String optionId = issue.path("optionId").asText(null);
            if (optionId == null) commonInvalid = true;
            else invalidOptions.computeIfAbsent(optionId, ignored -> new ArrayList<>()).add(issue);
        }
        if (commonInvalid || scope.kind() != ExamCheckScope.Kind.EXAM && !invalid.isEmpty()) {
            common.addAll(invalid);
            return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                    new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                    deadline, budget, common, rules::path);
        }
        Map<Long, List<ObjectNode>> witnessIssues = new LinkedHashMap<>();
        Function<PathResult, List<ObjectNode>> pathIssues = path -> {
            List<ObjectNode> found = new ArrayList<>(rules.path(path));
            if (scope.kind() != ExamCheckScope.Kind.EXAM) {
                Set<String> solvedSections = path.path().sections().stream().map(section -> section.sectionId())
                        .collect(java.util.stream.Collectors.toSet());
                for (var picks : path.path().picks()) for (var pick : picks)
                    for (var section : pick.sections()) solvedSections.add(section.sectionId());
                found.removeIf(issue -> !inScope(issue, scope, solvedSections));
            }
            found.addAll(witnessIssues.getOrDefault(path.path().pathIndex(), List.of()));
            path.path().chosen().forEach(choice ->
                    found.addAll(invalidOptions.getOrDefault(choice.optionId(), List.of())));
            return found;
        };
        if (!clock.instant().isBefore(deadline)) {
            common.addAll(invalid);
            return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                    new SolveOutcome(SolveStatus.UNCONFIRMED, SolveReason.DEADLINE_EXCEEDED, List.of(), SolveStats.EMPTY),
                    deadline, budget, common, rules::path);
        }
        Set<String> maskedSections = new java.util.HashSet<>();
        Set<String> maskedOptions = new java.util.HashSet<>(invalidOptions.keySet());
        if (scope.kind() != ExamCheckScope.Kind.EXAM) {
            for (ObjectNode issue : allInvalid) {
                String sectionId = issue.path("sectionId").asText(null);
                String optionId = issue.path("optionId").asText(null);
                if (sectionId != null) {
                    maskedSections.add(sectionId);
                } else if (optionId != null) {
                    if (optionContains(snapshot.structure(), optionId, requested)) {
                        common.add(issue);
                        return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                                new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                                deadline, budget, common, rules::path);
                    }
                    maskedOptions.add(optionId);
                } else {
                    // A malformed part prevents its paths from being enumerated reliably.
                    common.add(issue);
                    return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                            new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                            deadline, budget, common, rules::path);
                }
            }
        }
        ExamStructureDocument solverDocument = maskInvalidOptions(
                ExamCheckDraftInputs.maskSections(snapshot.structure(), maskedSections), maskedOptions);
        SolverInputBuilder.SolverInput input = builder.build(snapshot.exam().getKindItemId(), solverDocument);
        if (!maskedSections.isEmpty()) {
            try {
                var plan = ExamCheckUnitPlanner.plan(input.parts(), limits.maxPaths(), limits.maxChoiceSubjects());
                Set<String> selected = new java.util.HashSet<>();
                for (var unit : plan.units()) {
                    if (unit.key().sectionIds().stream().anyMatch(requested::contains)) {
                        selected.addAll(unit.key().sectionIds());
                    }
                }
                var related = allInvalid.stream().filter(issue ->
                        selected.contains(issue.path("sectionId").asText(null))).toList();
                if (!related.isEmpty()) {
                    common.addAll(related);
                    return assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                            new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                            deadline, budget, common, rules::path);
                }
            } catch (ExamCheckUnitPlanner.TooManyPaths | ExamCheckUnitPlanner.TooManyChoiceSubjects ignored) {
                // The unit engine reports the solver limit without attempting these units.
            }
        }
        var computed = units.solve(snapshot, input, rules, deadline, budget, scope);
        SolveOutcome outcome = computed.outcome();
        ExamCheckUnitEvidence.witnessFacts(source, snapshot.structure(), outcome.results(), combinations)
                .forEach((path, facts) -> witnessIssues.put(path, rules.witnessIssues(facts)));
        var evaluation = assembler.assemble(snapshot.exam(), snapshot.structure(), input, outcome, deadline, budget, common, pathIssues);
        return new Evaluation(evaluation.result(), evaluation.witness(), markWitnessFailures(computed.changed(), witnessIssues));
    }

    private static boolean inScope(ObjectNode issue, ExamCheckScope scope, Set<String> sections) {
        if (scope.kind() == ExamCheckScope.Kind.EXAM) return true;
        if (issue.hasNonNull("sectionId")) return sections.contains(issue.path("sectionId").asText());
        return scope.kind() == ExamCheckScope.Kind.PART
                && scope.targetId().equals(issue.path("partId").asText());
    }

    private static boolean optionContains(ExamStructureDocument document, String optionId, Set<String> requested) {
        for (ExamPart part : document.parts()) for (ExamPartOption option : part.options()) {
            if (!optionId.equals(option.id())) continue;
            if (requested.contains(option.id() + ":blocks")) return true;
            return option.sections().stream().anyMatch(section -> requested.contains(section.id()));
        }
        return false;
    }

    private List<ExamCheckUnitStore.Unit> markWitnessFailures(List<ExamCheckUnitStore.Unit> changed,
            Map<Long, List<ObjectNode>> byPath) {
        List<ExamCheckUnitStore.Unit> result = new ArrayList<>(changed.size());
        for (var unit : changed) {
            var issues = unit.issues().deepCopy();
            boolean invalid = false;
            for (var pathIssues : byPath.values()) for (var issue : pathIssues) {
                if (unit.sectionIds().contains(issue.path("sectionId").asText())
                        && "ERROR".equals(issue.path("severity").asText())) {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) issues).add(issue);
                    invalid = true;
                }
            }
            result.add(invalid ? new ExamCheckUnitStore.Unit(unit.key(), unit.sectionIds(), unit.fingerprint(),
                    unit.sourceRevision(), "INVALID", issues, unit.witness(), unit.checkedAt(), unit.durationMs()) : unit);
        }
        return result;
    }

    /** Keep each invalid option's path identifiable while solving unrelated valid options normally. */
    private static ExamStructureDocument maskInvalidOptions(ExamStructureDocument document, Set<String> invalid) {
        List<ExamPart> parts = new ArrayList<>(document.parts().size());
        for (ExamPart part : document.parts()) {
            List<ExamPartOption> options = new ArrayList<>(part.options().size());
            for (ExamPartOption option : part.options()) {
                options.add(invalid.contains(option.id())
                        ? new ExamPartOption(option.id(), OptionKind.SUBJECT, option.code(), option.name(),
                                null, null, List.of(), List.of(), option.expertConfirmed())
                        : option);
            }
            parts.add(new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                    part.questionCountOnPaper(), part.questionCountRequired(), part.participation(),
                    part.maxPoints(), part.sections(), options, part.subjectPool(), part.codeSource()));
        }
        return new ExamStructureDocument(parts, document.examQuotas());
    }
}
