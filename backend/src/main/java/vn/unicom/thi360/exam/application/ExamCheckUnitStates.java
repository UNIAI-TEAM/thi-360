package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.api.ExamCheckApiModels.SectionState;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Computes section states from current structure and the saved unit fingerprints. */
@Service
public class ExamCheckUnitStates {
    private final ExamCheckSnapshotReader snapshots;
    private final ExamCheckUnitStore store;
    private final ExamCheckUnitFingerprint fingerprints;
    private final SolverInputBuilder builder;
    private final SolverLimits limits;
    private final CatalogLookup catalog;
    private final ObjectMapper mapper;
    private final QuestionSourcePort source;
    private final ExamCheckTemplateFacts templateFacts;

    public ExamCheckUnitStates(ExamCheckSnapshotReader snapshots, ExamCheckUnitStore store,
            ExamCheckUnitFingerprint fingerprints, SolverInputBuilder builder, SolverLimits limits,
            CatalogLookup catalog, ObjectMapper mapper, QuestionSourcePort source, ExamCheckTemplateFacts templateFacts) {
        this.snapshots = snapshots;
        this.store = store;
        this.fingerprints = fingerprints;
        this.builder = builder;
        this.limits = limits;
        this.catalog = catalog;
        this.mapper = mapper;
        this.source = source;
        this.templateFacts = templateFacts;
    }

    @Transactional(readOnly = true)
    public List<SectionState> states(UUID examId) {
        var snapshot = snapshots.read(examId);
        var document = snapshot.structure();
        if (ExamStoredIdCollision.find(document).isPresent()) return allIncomplete(document);
        var combinations = ExamCheckUnitCombinations.forStructure(catalog, snapshot.exam().getKindItemId(), document);
        // Only the locked-count template facts feed sections; the exam-level publish ban marks no section.
        var rules = new ExamCheckRules(mapper, snapshot.exam(), document, combinations,
                ExamCheckUnitCombinations.topicParents(catalog, document),
                templateFacts.forSections(snapshot.exam()), templateFacts.kindCode(snapshot.exam()));
        Set<String> incomplete = new HashSet<>();
        Set<String> invalidParts = new HashSet<>();
        Set<vn.unicom.thi360.exam.domain.solver.ChosenOption> invalidOptions = new HashSet<>();
        Set<String> invalidPartSubjects = new HashSet<>();
        Map<String, Set<String>> missingCodes = new LinkedHashMap<>();
        for (ObjectNode issue : rules.unrepresentable()) {
            if ("COMBINATION_SUBJECT_MISSING".equals(issue.path("code").asText())) {
                String partId = issue.path("partId").asText();
                for (var code : issue.path("affectedCodes")) {
                    missingCodes.computeIfAbsent(partId, ignored -> new java.util.LinkedHashSet<>()).add(code.asText());
                }
                continue;
            }
            markIncomplete(incomplete, issue);
            if (!issue.hasNonNull("sectionId") && issue.hasNonNull("optionId")) {
                invalidOptions.add(new vn.unicom.thi360.exam.domain.solver.ChosenOption(
                        issue.path("partId").asText(), issue.path("optionId").asText()));
            }
            if (!issue.hasNonNull("sectionId") && !issue.hasNonNull("optionId") && issue.hasNonNull("partId")) {
                invalidParts.add(issue.path("partId").asText());
                if (issue.hasNonNull("subjectCode")) invalidPartSubjects.add(issue.path("subjectCode").asText());
            }
        }
        rules.templateIssues().forEach(issue -> markIncomplete(incomplete, issue));
        for (var part : document.parts()) {
            if (invalidParts.contains(part.id())) {
                invalidPartSubjects.addAll(ExamCheckDraftInputs.subjects(part));
                part.sections().forEach(section -> incomplete.add(section.id()));
                part.subjectPool().forEach(section -> incomplete.add(section.id()));
                for (var option : part.options()) {
                    option.sections().forEach(section -> incomplete.add(section.id()));
                    if (!option.blocks().isEmpty()) incomplete.add(option.id() + ":blocks");
                }
            }
        }
        for (var part : document.parts()) for (var option : part.options()) {
            if (invalidOptions.contains(new vn.unicom.thi360.exam.domain.solver.ChosenOption(part.id(), option.id()))) {
                option.sections().forEach(section -> incomplete.add(section.id()));
                if (!option.blocks().isEmpty()) incomplete.add(option.id() + ":blocks");
            }
        }
        Map<String, String> status = new LinkedHashMap<>();
        ExamCheckUnitPlanner.Plan plan = null;
        try {
            var solverDocument = ExamCheckDraftInputs.withoutParts(
                    DefaultExamChecker.maskInvalidOptions(ExamCheckDraftInputs.maskSections(document, incomplete),
                            invalidOptions), invalidParts);
            var input = missingCodes.isEmpty() ? builder.build(snapshot.exam().getKindItemId(), solverDocument)
                    : DefaultExamChecker.buildWithMissingCodes(snapshot, solverDocument, missingCodes, catalog, source, limits);
            plan = ExamCheckUnitPlanner.plan(input.parts(), limits.maxPaths(), limits.maxChoiceSubjects());
        } catch (IllegalArgumentException | ExamCheckUnitPlanner.TooManyPaths
                | ExamCheckUnitPlanner.TooManyChoiceSubjects ex) {
            // Incomplete drafts or a path limit cannot have a currently confirmed unit plan.
        }
        if (plan != null) {
            var oldUnits = store.load(examId);
            var config = ExamCheckFingerprint.Config.from(snapshot.exam());
            var inputs = snapshot.persistenceInput();
            var unitCatalog = new ExamCheckUnitCatalogSlice(catalog, mapper);
            Map<String, ExamSection> sections = ExamCheckUnitEngine.sections(document);
            for (var unit : plan.units()) {
                if (unit.key().sectionIds().stream().anyMatch(incomplete::contains)) {
                    incomplete.addAll(unit.key().sectionIds());
                    continue;
                }
                List<ExamSection> sourceSections = unit.key().sectionIds().stream().map(sections::get).toList();
                var identity = fingerprints.calculate(unit.key().subjectCode(), sourceSections, document.examQuotas(),
                        config, inputs.profileRevision(), unitCatalog, unit.sections());
                UUID subject = catalog.findActiveItem("SUBJECT", unit.key().subjectCode()).map(item -> item.id()).orElse(null);
                long revision = subject == null ? 0 : inputs.sourceRevisions().path(subject.toString()).asLong(0);
                var saved = oldUnits.get(identity.key());
                String current = saved == null || !identity.fingerprint().equals(saved.fingerprint())
                        || revision != saved.sourceRevision() || "UNCONFIRMED".equals(saved.outcome())
                        ? "NEEDS_CHECK" : "INVALID".equals(saved.outcome()) ? "INCOMPLETE" : "CHECKED";
                if (invalidPartSubjects.contains(unit.key().subjectCode())) current = "NEEDS_CHECK";
                var path = SolvePath.of(0, unit.sections());
                var structural = new PathResult(path, SolveStatus.VALID, null, null, null, List.of(), List.of(), null);
                for (ObjectNode issue : rules.path(structural)) markIncomplete(incomplete, issue);
                for (String sectionId : unit.key().sectionIds()) status.merge(sectionId, current, ExamCheckUnitStates::worst);
            }
        }
        List<SectionState> result = new ArrayList<>();
        for (var part : document.parts()) {
            add(result, part.id(), part.sections(), status, incomplete);
            add(result, part.id(), part.subjectPool(), status, incomplete);
            for (var option : part.options()) {
                add(result, part.id(), option.sections(), status, incomplete);
                if (!option.blocks().isEmpty()) {
                    String id = option.id() + ":blocks";
                    result.add(new SectionState(part.id(), id, option.code(), incomplete.contains(id)
                            ? "INCOMPLETE" : status.getOrDefault(id, "NEEDS_CHECK")));
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * ADV:CR-4:21: nothing of a draft with an ambiguous stored id is certifiable, so every section reads as incomplete
     * and no saved unit proof is consulted.
     */
    private static List<SectionState> allIncomplete(ExamStructureDocument document) {
        List<SectionState> result = new ArrayList<>();
        for (var part : document.parts()) {
            for (var section : part.sections()) {
                result.add(new SectionState(part.id(), section.id(), section.subjectCode(), "INCOMPLETE"));
            }
            for (var section : part.subjectPool()) {
                result.add(new SectionState(part.id(), section.id(), section.subjectCode(), "INCOMPLETE"));
            }
            for (var option : part.options()) {
                for (var section : option.sections()) {
                    result.add(new SectionState(part.id(), section.id(), section.subjectCode(), "INCOMPLETE"));
                }
                if (!option.blocks().isEmpty()) {
                    result.add(new SectionState(part.id(), option.id() + ":blocks", option.code(), "INCOMPLETE"));
                }
            }
        }
        return List.copyOf(result);
    }

    private static void add(List<SectionState> result, String partId, List<ExamSection> sections,
            Map<String, String> status, Set<String> incomplete) {
        for (var section : sections) result.add(new SectionState(partId, section.id(), section.subjectCode(),
                incomplete.contains(section.id()) ? "INCOMPLETE" : status.getOrDefault(section.id(), "NEEDS_CHECK")));
    }

    private static void markIncomplete(Set<String> incomplete, ObjectNode issue) {
        if ("ERROR".equals(issue.path("severity").asText()) && issue.hasNonNull("sectionId")) {
            incomplete.add(issue.path("sectionId").asText());
        }
    }

    private static String worst(String left, String right) {
        if ("INCOMPLETE".equals(left) || "INCOMPLETE".equals(right)) return "INCOMPLETE";
        if ("NEEDS_CHECK".equals(left) || "NEEDS_CHECK".equals(right)) return "NEEDS_CHECK";
        return "CHECKED";
    }
}
