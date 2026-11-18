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
        if (scope.kind() == ExamCheckScope.Kind.PART && requested.isEmpty()) {
            ExamPart part = snapshot.structure().parts().stream()
                    .filter(candidate -> scope.targetId().equals(candidate.id())).findFirst().orElseThrow();
            if (part.codeSource() == null && part.options().isEmpty()) {
                var issueFactory = new ExamCheckIssues(mapper);
                var empty = issueFactory.create("PART_EMPTY", "STRUCTURE", "ERROR");
                empty.put("partId", part.id());
                empty.put("partName", part.name());
                common.add(issueFactory.key(empty));
                return invalidResult(snapshot, scope, allInvalid, common, assembler, rules, deadline, budget);
            }
        }
        Map<String, List<ObjectNode>> invalidOptions = new LinkedHashMap<>();
        Map<String, Set<String>> missingCodes = new LinkedHashMap<>();
        boolean commonInvalid = false;
        for (ObjectNode issue : invalid) {
            if (scope.kind() == ExamCheckScope.Kind.EXAM
                    && "COMBINATION_SUBJECT_MISSING".equals(issue.path("code").asText())) {
                String partId = issue.path("partId").asText();
                for (var code : issue.path("affectedCodes")) {
                    missingCodes.computeIfAbsent(partId, ignored -> new java.util.LinkedHashSet<>()).add(code.asText());
                    invalidOptions.computeIfAbsent(partId + ":" + code.asText(), ignored -> new ArrayList<>()).add(issue);
                }
                continue;
            }
            String optionId = issue.path("optionId").asText(null);
            if (optionId == null) commonInvalid = true;
            else invalidOptions.computeIfAbsent(optionId, ignored -> new ArrayList<>()).add(issue);
        }
        if (commonInvalid || scope.kind() != ExamCheckScope.Kind.EXAM && !invalid.isEmpty()) {
            common.addAll(invalid);
            return invalidResult(snapshot, scope, allInvalid, common, assembler, rules, deadline, budget);
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
        Set<String> ignoredParts = new java.util.HashSet<>();
        Set<String> requestedSubjects = new java.util.HashSet<>();
        if (scope.kind() != ExamCheckScope.Kind.EXAM) {
            var sourceSections = ExamCheckUnitEngine.sections(snapshot.structure());
            for (String id : requested) {
                var section = sourceSections.get(id);
                if (section != null && section.subjectCode() != null) requestedSubjects.add(section.subjectCode());
            }
        }
        if (scope.kind() != ExamCheckScope.Kind.EXAM) {
            for (ObjectNode issue : allInvalid) {
                String sectionId = issue.path("sectionId").asText(null);
                String optionId = issue.path("optionId").asText(null);
                if (sectionId != null) {
                    maskedSections.add(sectionId);
                } else if (optionId != null) {
                    if (optionContains(snapshot.structure(), optionId, requested)
                            || optionSharesSubjectOnPath(snapshot.structure(), optionId, requested, requestedSubjects)) {
                        common.add(issue);
                        return invalidResult(snapshot, scope, allInvalid, common, assembler, rules, deadline, budget);
                    }
                    maskedOptions.add(optionId);
                } else {
                    String partId = issue.path("partId").asText(null);
                    var part = snapshot.structure().parts().stream().filter(p -> p.id().equals(partId)).findFirst().orElse(null);
                    Set<String> subjects = part == null ? Set.of() : ExamCheckDraftInputs.subjects(part);
                    boolean sameSubject = subjects.stream().anyMatch(requestedSubjects::contains)
                            || requestedSubjects.contains(issue.path("subjectCode").asText(null));
                    if (part == null || scope.kind() == ExamCheckScope.Kind.PART && partId.equals(scope.targetId()) || sameSubject) {
                        common.add(issue);
                        return invalidResult(snapshot, scope, allInvalid, common, assembler, rules, deadline, budget);
                    }
                    ignoredParts.add(partId);
                }
            }
        }
        ExamStructureDocument solverDocument = ExamCheckDraftInputs.withoutParts(
                maskInvalidOptions(ExamCheckDraftInputs.maskSections(snapshot.structure(), maskedSections), maskedOptions),
                ignoredParts);
        SolverInputBuilder.SolverInput input = missingCodes.isEmpty()
                ? builder.build(snapshot.exam().getKindItemId(), solverDocument)
                : buildWithMissingCodes(snapshot, solverDocument, missingCodes, catalog, source, limits);
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
                    var evaluation = assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                            new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                            deadline, budget, common, rules::path);
                    return new Evaluation(evaluation.result(), evaluation.witness(),
                            units.invalidUnits(snapshot, input, scope, related));
                }
            } catch (ExamCheckUnitPlanner.TooManyPaths | ExamCheckUnitPlanner.TooManyChoiceSubjects ignored) {
                // The unit engine reports the solver limit without attempting these units.
            }
        }
        var computed = units.solve(snapshot, input, rules, deadline, budget, scope);
        SolveOutcome outcome = computed.outcome();
        ExamCheckUnitEvidence.witnessFacts(source, snapshot.structure(), outcome.results(), combinations)
                .forEach((path, facts) -> witnessIssues.put(path, new ArrayList<>(rules.witnessIssues(facts))));
        for (var path : outcome.results()) {
            if (path.status() != SolveStatus.INVALID) continue;
            var topicIssues = ExamCheckUnitTopicEvidence.issues(path, snapshot.structure(), input.candidates(),
                    rules, clock, deadline, limits.maxCandidatesPerBlock());
            if (!topicIssues.isEmpty()) {
                witnessIssues.computeIfAbsent(path.path().pathIndex(), ignored -> new ArrayList<>()).addAll(topicIssues);
            }
        }
        var evaluation = assembler.assemble(snapshot.exam(), snapshot.structure(), input, outcome, deadline, budget, common, pathIssues);
        return new Evaluation(evaluation.result(), evaluation.witness(), markWitnessFailures(computed.changed(), witnessIssues));
    }

    static SolverInputBuilder.SolverInput buildWithMissingCodes(ExamCheckSnapshotReader.Snapshot snapshot,
            ExamStructureDocument document, Map<String, Set<String>> missingCodes, CatalogLookup catalog,
            QuestionSourcePort source, SolverLimits limits) {
        List<Set<String>> exclusions = document.parts().stream()
                .sorted(java.util.Comparator.comparing(ExamPart::order,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .filter(part -> part.codeSource() != null)
                .map(part -> missingCodes.getOrDefault(part.id(), Set.of())).toList();
        var filtered = new ExamCheckUnitCatalog(catalog, exclusions);
        var input = new SolverInputBuilder(filtered, source, limits).build(snapshot.exam().getKindItemId(), document);
        List<vn.unicom.thi360.exam.domain.solver.SolvePart> parts = new ArrayList<>();
        for (var part : input.parts()) {
            if (part instanceof vn.unicom.thi360.exam.domain.solver.SolvePart.Choice choice
                    && missingCodes.containsKey(choice.partId())) {
                var options = new ArrayList<>(choice.options());
                for (String code : missingCodes.get(choice.partId())) {
                    options.add(new vn.unicom.thi360.exam.domain.solver.SolveOption.Sections(
                            choice.partId() + ":" + code, List.of()));
                }
                parts.add(new vn.unicom.thi360.exam.domain.solver.SolvePart.Choice(
                        choice.partId(), choice.commonSections(), options, choice.choose()));
            } else {
                parts.add(part);
            }
        }
        return new SolverInputBuilder.SolverInput(parts, input.candidates());
    }

    private Evaluation invalidResult(ExamCheckSnapshotReader.Snapshot snapshot, ExamCheckScope scope,
            List<ObjectNode> allInvalid, List<ObjectNode> common, ExamCheckAssembler assembler,
            ExamCheckRules rules, Instant deadline, Duration budget) {
        var evaluation = assembler.assemble(snapshot.exam(), snapshot.structure(), null,
                new SolveOutcome(SolveStatus.INVALID, null, List.of(), SolveStats.EMPTY),
                deadline, budget, common, rules::path);
        if (scope.kind() == ExamCheckScope.Kind.EXAM) return evaluation;
        Set<String> sections = new java.util.HashSet<>();
        Set<String> options = new java.util.HashSet<>();
        Set<String> parts = new java.util.HashSet<>();
        for (var issue : allInvalid) {
            if (issue.hasNonNull("sectionId")) sections.add(issue.path("sectionId").asText());
            else if (issue.hasNonNull("optionId")) options.add(issue.path("optionId").asText());
            else if (issue.hasNonNull("partId")) parts.add(issue.path("partId").asText());
        }
        var document = ExamCheckDraftInputs.withoutParts(
                maskInvalidOptions(ExamCheckDraftInputs.maskSections(snapshot.structure(), sections), options), parts);
        try {
            var input = builder.build(snapshot.exam().getKindItemId(), document);
            return new Evaluation(evaluation.result(), evaluation.witness(),
                    units.invalidUnits(snapshot, input, scope, common));
        } catch (IllegalArgumentException | ExamCheckUnitPlanner.TooManyPaths
                | ExamCheckUnitPlanner.TooManyChoiceSubjects ex) {
            // A draft without a representable unit is reflected directly by structural section states.
            return evaluation;
        }
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

    static boolean optionSharesSubjectOnPath(ExamStructureDocument document, String optionId,
                                                      Set<String> requested, Set<String> subjects) {
        for (ExamPart part : document.parts()) for (ExamPartOption option : part.options()) {
            if (!optionId.equals(option.id())) continue;
            boolean sharesSubject = subjects.contains(option.code()) || option.sections().stream()
                    .anyMatch(section -> subjects.contains(section.subjectCode()));
            if (!sharesSubject) return false;
            if (part.participation() != null
                    && part.participation().mode() == vn.unicom.thi360.exam.domain.ParticipationMode.CHOICE
                    && Integer.valueOf(1).equals(part.participation().choose())) {
                for (ExamPartOption alternative : part.options()) {
                    if (alternative.id().equals(optionId)) continue;
                    if (requested.contains(alternative.id() + ":blocks")
                            || alternative.sections().stream().anyMatch(section -> requested.contains(section.id()))) {
                        return false;
                    }
                }
            }
            return true;
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
    static ExamStructureDocument maskInvalidOptions(ExamStructureDocument document, Set<String> invalid) {
        if (invalid.isEmpty()) return document;
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
