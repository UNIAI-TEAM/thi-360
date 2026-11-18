package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.*;

/** Reuses valid subject units and solves only units whose inputs or source revision changed. */
@Service
public class ExamCheckUnitEngine {
    private final ExamCheckUnitStore store;
    private final ExamCheckUnitFingerprint fingerprints;
    private final CatalogLookup catalog;
    private final ObjectMapper mapper;
    private final SolverLimits limits;
    private final Clock clock;

    public ExamCheckUnitEngine(ExamCheckUnitStore store, ExamCheckUnitFingerprint fingerprints,
            CatalogLookup catalog, ObjectMapper mapper, SolverLimits limits, Clock clock) {
        this.store = store;
        this.fingerprints = fingerprints;
        this.catalog = catalog;
        this.mapper = mapper;
        this.limits = limits;
        this.clock = clock;
    }

    record Proof(SolveStatus status, SolveReason reason, String subjectCode, String groupId,
                 QuotaRef failingQuota, List<SubjectWitness> witnesses) { }
    record Computation(SolveOutcome outcome, List<ExamCheckUnitStore.Unit> changed) { }

    Computation solve(ExamCheckSnapshotReader.Snapshot snapshot, SolverInputBuilder.SolverInput input,
            ExamCheckRules rules, Instant deadline, Duration budget, ExamCheckScope scope) {
        ExamCheckUnitPlanner.Plan plan;
        try {
            plan = ExamCheckUnitPlanner.plan(input.parts(), limits.maxPaths(), limits.maxChoiceSubjects());
        } catch (ExamCheckUnitPlanner.TooManyPaths ex) {
            return new Computation(new SolveOutcome(SolveStatus.UNCONFIRMED, SolveReason.PATH_LIMIT_EXCEEDED,
                    List.of(), SolveStats.EMPTY), List.of());
        } catch (ExamCheckUnitPlanner.TooManyChoiceSubjects ex) {
            return new Computation(new SolveOutcome(SolveStatus.INVALID, SolveReason.CHOICE_SUBJECT_LIMIT_EXCEEDED,
                    List.of(), SolveStats.EMPTY), List.of());
        }
        var persisted = store.load(snapshot.exam().getId());
        var inputInfo = snapshot.persistenceInput();
        var config = ExamCheckFingerprint.Config.from(snapshot.exam());
        Map<String, ExamSection> sections = sections(snapshot.structure());
        Map<ExamCheckUnitPlanner.Key, Proof> proofs = new LinkedHashMap<>();
        List<ExamCheckUnitStore.Unit> changed = new ArrayList<>();
        Set<String> requested = scope.sections(snapshot.structure());
        int solved = 0, reused = 0;
        for (var unit : plan.units()) {
            if (scope.kind() != ExamCheckScope.Kind.EXAM
                    && unit.key().sectionIds().stream().noneMatch(requested::contains)) continue;
            List<ExamSection> sourceSections = unit.key().sectionIds().stream().map(sections::get).toList();
            var identity = fingerprints.calculate(unit.key().subjectCode(), sourceSections,
                    snapshot.structure().examQuotas(), config, inputInfo.profileRevision(), inputInfo.catalogRevision(),
                    unit.sections());
            UUID subjectId = catalog.findActiveItem("SUBJECT", unit.key().subjectCode()).map(item -> item.id()).orElse(null);
            long sourceRevision = subjectId == null ? 0 : inputInfo.sourceRevisions().path(subjectId.toString()).asLong(0);
            var old = persisted.get(identity.key());
            if (!clock.instant().isBefore(deadline)) {
                proofs.put(unit.key(), new Proof(SolveStatus.UNCONFIRMED, SolveReason.DEADLINE_EXCEEDED,
                        unit.key().subjectCode(), null, null, List.of()));
                continue;
            }
            if (old != null && "VALID".equals(old.outcome()) && identity.fingerprint().equals(old.fingerprint())
                    && sourceRevision == old.sourceRevision()) {
                proofs.put(unit.key(), mapper.convertValue(old.witness(), Proof.class));
                reused++;
                continue;
            }
            Instant started = clock.instant();
            Duration remaining = Duration.between(started, deadline);
            Proof proof;
            if (remaining.isNegative() || remaining.isZero()) {
                proof = new Proof(SolveStatus.UNCONFIRMED, SolveReason.DEADLINE_EXCEEDED,
                        unit.key().subjectCode(), null, null, List.of());
            } else {
                var bounded = new SolverLimits(limits.maxPaths(), limits.maxCandidatesPerBlock(), remaining,
                        limits.maxChoiceSubjects());
                SolveOutcome outcome = solveUnit(unit.sections(), input.candidates(), bounded);
                proof = proof(outcome, unit.key().subjectCode());
                solved++;
            }
            proofs.put(unit.key(), proof);
            var issues = mapper.createArrayNode();
            if (proof.status() != SolveStatus.VALID && proof.reason() != null) {
                var issue = new ExamCheckSolverIssues(new ExamCheckIssues(mapper), snapshot.structure(), budget.toSeconds())
                        .failure(proof.reason(), proof.subjectCode(), proof.groupId(), proof.failingQuota(), List.of());
                issues.add(issue);
            }
            // Structural failures belong to the section even if its source can be solved.
            var path = SolvePath.of(0, unit.sections());
            var pathResult = new PathResult(path, proof.status(), proof.reason(), proof.subjectCode(), proof.groupId(),
                    proof.witnesses(), List.of(), proof.failingQuota());
            boolean structuralError = false;
            for (ObjectNode issue : rules.path(pathResult)) {
                if (unit.key().sectionIds().contains(issue.path("sectionId").asText())) {
                    issues.add(issue);
                    structuralError |= "ERROR".equals(issue.path("severity").asText());
                }
            }
            String status = structuralError ? "INVALID" : proof.status().name();
            changed.add(new ExamCheckUnitStore.Unit(identity.key(), identity.sectionIds(), identity.fingerprint(),
                    sourceRevision, status, issues, mapper.valueToTree(proof), clock.instant(),
                    Math.max(0, Duration.between(started, clock.instant()).toMillis())));
        }
        List<PathResult> paths = new ArrayList<>();
        if (scope.kind() == ExamCheckScope.Kind.EXAM) {
            for (var path : plan.paths()) paths.add(assemble(path, proofs));
        } else {
            Map<ExamCheckUnitPlanner.Key, ExamCheckUnitPlanner.Unit> byKey = new LinkedHashMap<>();
            for (var unit : plan.units()) byKey.put(unit.key(), unit);
            for (var original : plan.paths()) for (var tuple : original.tuples()) {
                var selected = tuple.units().stream().filter(proofs::containsKey).toList();
                if (selected.isEmpty()) continue;
                List<SectionOnPath> pathSections = new ArrayList<>();
                for (var key : selected) pathSections.addAll(byKey.get(key).sections());
                var chosen = scope.kind() == ExamCheckScope.Kind.PART
                        ? original.path().chosen().stream().filter(item -> scope.targetId().equals(item.partId())).toList()
                        : original.path().chosen();
                List<SubjectPick> selectedPicks = new ArrayList<>();
                List<SubjectChoice> choices = new ArrayList<>();
                if (scope.kind() == ExamCheckScope.Kind.PART) {
                    for (var pick : tuple.picks()) {
                        boolean belongs = chosen.stream().anyMatch(item -> item.optionId().equals(pick.optionId()));
                        if (!belongs) continue;
                        selectedPicks.add(pick);
                        original.path().choices().stream().filter(item -> item.optionId().equals(pick.optionId()))
                                .findFirst().ifPresent(choices::add);
                        pathSections.removeAll(pick.resolvedSections());
                    }
                }
                List<List<SubjectPick>> picks = selectedPicks.stream().map(List::of).toList();
                var path = new SolvePath(paths.size(), chosen, pathSections, choices, picks);
                var selectedTuple = new ExamCheckUnitPlanner.Tuple(selectedPicks, selected);
                paths.add(assemble(new ExamCheckUnitPlanner.Path(path, List.of(selectedTuple)), proofs));
            }
        }
        SolveStatus status = paths.isEmpty() ? SolveStatus.INVALID : SolveStatus.VALID;
        for (var path : paths) status = worst(status, path.status());
        return new Computation(new SolveOutcome(status, null, paths,
                new SolveStats(plan.paths().stream().mapToLong(path -> path.tuples().size()).sum(), solved, reused)), changed);
    }

    List<ExamCheckUnitStore.Unit> invalidUnits(ExamCheckSnapshotReader.Snapshot snapshot,
            SolverInputBuilder.SolverInput input, ExamCheckScope scope, List<ObjectNode> issues) {
        var plan = ExamCheckUnitPlanner.plan(input.parts(), limits.maxPaths(), limits.maxChoiceSubjects());
        var requested = scope.sections(snapshot.structure());
        var sourceSections = sections(snapshot.structure());
        var inputInfo = snapshot.persistenceInput();
        var config = ExamCheckFingerprint.Config.from(snapshot.exam());
        List<ExamCheckUnitStore.Unit> changed = new ArrayList<>();
        for (var unit : plan.units()) {
            if (unit.key().sectionIds().stream().noneMatch(requested::contains)) continue;
            var relevant = issues.stream().filter(issue -> {
                if (issue.hasNonNull("sectionId")) {
                    return unit.key().sectionIds().contains(issue.path("sectionId").asText());
                }
                if (issue.hasNonNull("optionId")) {
                    return DefaultExamChecker.optionSharesSubjectOnPath(snapshot.structure(),
                            issue.path("optionId").asText(), Set.copyOf(unit.key().sectionIds()),
                            Set.of(unit.key().subjectCode()));
                }
                if (!issue.hasNonNull("partId")) return true;
                String partId = issue.path("partId").asText();
                return snapshot.structure().parts().stream().filter(part -> partId.equals(part.id()))
                        .anyMatch(part -> ExamCheckDraftInputs.subjects(part).contains(unit.key().subjectCode()));
            }).toList();
            if (relevant.isEmpty()) continue;
            var originals = unit.key().sectionIds().stream().map(sourceSections::get).toList();
            var identity = fingerprints.calculate(unit.key().subjectCode(), originals,
                    snapshot.structure().examQuotas(), config, inputInfo.profileRevision(),
                    inputInfo.catalogRevision(), unit.sections());
            UUID subject = catalog.findActiveItem("SUBJECT", unit.key().subjectCode())
                    .map(item -> item.id()).orElse(null);
            long revision = subject == null ? 0 : inputInfo.sourceRevisions().path(subject.toString()).asLong(0);
            var findings = mapper.createArrayNode();
            relevant.forEach(findings::add);
            changed.add(new ExamCheckUnitStore.Unit(identity.key(), identity.sectionIds(),
                    identity.fingerprint(), revision, "INVALID", findings, null, clock.instant(), 0));
        }
        return changed;
    }

    /** Public for an integration spy; no candidates or results are retained between calls. */
    public SolveOutcome solveUnit(List<SectionOnPath> sections, CandidateSource candidates, SolverLimits bounded) {
        return new PaperSolver(bounded, clock).solveAll(List.of(new SolvePart.Fixed("unit", sections)), candidates, SolveMode.check());
    }

    private static Proof proof(SolveOutcome outcome, String subjectCode) {
        if (outcome.results().isEmpty()) {
            return new Proof(outcome.status(), outcome.reason(), subjectCode, null, null, List.of());
        }
        PathResult result = outcome.results().getFirst();
        return new Proof(result.status(), result.reason(), result.subjectCode(), result.groupId(), result.failingQuota(), result.witness());
    }

    private static PathResult assemble(ExamCheckUnitPlanner.Path path, Map<ExamCheckUnitPlanner.Key, Proof> proofs) {
        List<SelectionResult> selections = new ArrayList<>();
        List<SubjectPick> failing = new ArrayList<>();
        Map<ExamCheckUnitPlanner.Key, SubjectWitness> witnesses = new LinkedHashMap<>();
        SolveStatus overall = SolveStatus.VALID;
        Proof failure = null;
        for (var tuple : path.tuples()) {
            SolveStatus status = SolveStatus.VALID;
            Proof tupleFailure = null;
            List<SubjectWitness> tupleWitness = new ArrayList<>();
            for (var key : tuple.units()) {
                Proof proof = proofs.get(key);
                SolveStatus updated = worst(status, proof.status());
                if (updated != status) tupleFailure = proof;
                status = updated;
                if (proof.status() == SolveStatus.VALID) tupleWitness.addAll(proof.witnesses());
            }
            if (status != SolveStatus.VALID) tupleWitness.clear();
            if (!path.path().choices().isEmpty()) selections.add(new SelectionResult(tuple.picks(), status,
                    tupleFailure == null ? null : tupleFailure.reason(), tupleFailure == null ? null : tupleFailure.subjectCode(),
                    tupleFailure == null ? null : tupleFailure.groupId(), tupleFailure == null ? null : tupleFailure.failingQuota(), tupleWitness));
            if (status == SolveStatus.INVALID) failing.addAll(tuple.picks());
            SolveStatus updated = worst(overall, status);
            if (updated != overall) failure = tupleFailure;
            overall = updated;
            if (status == SolveStatus.VALID) {
                for (var key : tuple.units()) {
                    Proof proof = proofs.get(key);
                    if (!proof.witnesses().isEmpty()) witnesses.putIfAbsent(key, proof.witnesses().getFirst());
                }
            }
        }
        return new PathResult(path.path(), overall, failure == null ? null : failure.reason(),
                failure == null ? null : failure.subjectCode(), failure == null ? null : failure.groupId(),
                overall == SolveStatus.VALID ? List.copyOf(witnesses.values()) : List.of(), failing,
                failure == null ? null : failure.failingQuota(), selections);
    }

    private static SolveStatus worst(SolveStatus a, SolveStatus b) {
        if (a == SolveStatus.INVALID || b == SolveStatus.INVALID) return SolveStatus.INVALID;
        if (a == SolveStatus.UNCONFIRMED || b == SolveStatus.UNCONFIRMED) return SolveStatus.UNCONFIRMED;
        return SolveStatus.VALID;
    }

    static Map<String, ExamSection> sections(ExamStructureDocument document) {
        Map<String, ExamSection> result = new LinkedHashMap<>();
        for (var part : document.parts()) {
            for (var section : part.sections()) result.put(section.id(), section);
            for (var section : part.subjectPool()) result.put(section.id(), section);
            for (var option : part.options()) {
                for (var section : option.sections()) result.put(section.id(), section);
                if (!option.blocks().isEmpty()) {
                    int count = option.blocks().stream().mapToInt(block -> Boolean.TRUE.equals(block.omitted()) ? 0
                            : block.questionCount() != null ? block.questionCount()
                            : block.setCount() == null || block.setSize() == null ? 0 : block.setCount() * block.setSize()).sum();
                    var synthetic = new ExamSection(option.id() + ":blocks", option.code(), count,
                            null, null, null, null, option.blocks());
                    result.put(synthetic.id(), synthetic);
                }
            }
        }
        return result;
    }
}
