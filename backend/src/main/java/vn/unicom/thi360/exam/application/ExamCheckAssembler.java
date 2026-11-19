package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SolveOutcome;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;

/** Assembles the eight groups from deduplicated path and configuration issues. */
final class ExamCheckAssembler {
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SolverLimits limits;

    ExamCheckAssembler(ObjectMapper mapper, Clock clock, SolverLimits limits) {
        this.mapper = mapper;
        this.clock = clock;
        this.limits = limits;
    }

    ExamChecker.Evaluation assemble(Exam exam, ExamStructureDocument document,
                                    SolverInputBuilder.SolverInput input, SolveOutcome outcome,
                                    Instant deadline, Duration budget, List<ObjectNode> commonIssues,
                                    Function<PathResult, List<ObjectNode>> pathIssues) {
        ExamCheckIssues issueFactory = new ExamCheckIssues(mapper);
        ExamCheckSolverIssues solverIssues = new ExamCheckSolverIssues(issueFactory, document, budget.toSeconds());
        LinkedHashMap<String, ObjectNode> all = new LinkedHashMap<>();
        commonIssues.forEach(issue -> add(all, issue));
        if (outcome.reason() != null) add(all, solverIssues.failure(outcome.reason(), null, null, null, List.of()));
        if (outcome.results().isEmpty() && all.isEmpty()) {
            add(all, issueFactory.key(issueFactory.create("OPTION_EMPTY", "STRUCTURE", "ERROR")));
        }
        ObjectNode result = mapper.createObjectNode();
        ArrayNode paths = result.putArray("paths");
        ArrayNode witnesses = mapper.createArrayNode();
        if (input != null) {
            ExamCheckSources sources = new ExamCheckSources(input.candidates(), clock, deadline,
                    limits.maxCandidatesPerBlock());
            ExamCheckPathPresenter presenter = new ExamCheckPathPresenter(mapper, exam, document,
                    issueFactory, solverIssues, sources);
            for (PathResult path : outcome.results()) {
                var current = new java.util.ArrayList<>(commonIssues);
                current.addAll(pathIssues.apply(path));
                ObjectNode rendered = presenter.render(path, current);
                for (var node : rendered.path("issues")) {
                    ObjectNode issue = (ObjectNode) node;
                    if (issue.path("subjectCode").isMissingNode() || issue.path("subjectCode").isNull()) continue;
                    boolean onPath = false;
                    for (var subject : rendered.path("subjects")) {
                        if (subject.path("sectionId").asText().equals(issue.path("sectionId").asText())) {
                            onPath = true;
                            break;
                        }
                    }
                    if (!onPath) continue;
                    for (var choice : rendered.path("choices")) {
                        if (!issue.path("partId").asText().equals(choice.path("partId").asText())) continue;
                        boolean codeSourced = document.parts().stream().anyMatch(part ->
                                part.id().equals(choice.path("partId").asText()) && part.codeSource() != null);
                        if (codeSourced && choice.hasNonNull("code")) {
                            ArrayNode codes = (ArrayNode) issue.withArray("affectedCodes");
                            String code = choice.path("code").asText();
                            boolean present = false;
                            for (var value : codes) if (code.equals(value.asText())) present = true;
                            if (!present) codes.add(code);
                        }
                    }
                }
                paths.add(rendered);
                if ("VALID".equals(rendered.path("result").asText())) witnesses.add(mapper.valueToTree(path));
                rendered.path("issues").forEach(issue -> add(all, (ObjectNode) issue));
            }
        }
        ArrayNode groups = result.putArray("groups");
        for (String name : ExamCheckIssues.GROUPS) {
            ObjectNode group = groups.addObject();
            group.put("group", name);
            ArrayNode groupedIssues = group.putArray("issues");
            boolean error = false;
            for (ObjectNode issue : all.values()) {
                if (!name.equals(issue.path("group").asText())) continue;
                groupedIssues.add(issue);
                error |= "ERROR".equals(issue.path("severity").asText());
            }
            group.put("status", error ? "FAIL" : groupedIssues.isEmpty() ? "PASS" : "WARN");
        }
        boolean blocking = all.values().stream().anyMatch(issue -> !"WARNING".equals(issue.path("severity").asText()));
        boolean failedPath = false;
        for (var path : paths) failedPath |= !"VALID".equals(path.path("result").asText());
        result.put("status", !blocking && !failedPath && !paths.isEmpty() ? "VALID" : "INVALID");
        result.put("issueCount", all.size());
        result.put("partCount", document.parts().size());
        result.set("totalPoints", paths.isEmpty() ? mapper.valueToTree(BigDecimal.ZERO) : paths.get(0).path("totalPoints"));
        result.put("printedQuestions", document.parts().stream().mapToInt(ExamCheckAssembler::printedQuestions).sum());
        if (exam.getMaxScore() == null) result.putNull("maxScore"); else result.put("maxScore", exam.getMaxScore());
        return new ExamChecker.Evaluation(result, witnesses, List.of(), outcome.reason() == null);
    }

    private static int printedQuestions(ExamPart part) {
        if (part.questionCountOnPaper() != null) return part.questionCountOnPaper();
        return part.questionCount() == null ? 0 : part.questionCount();
    }

    private static void add(Map<String, ObjectNode> issues, ObjectNode issue) {
        ObjectNode existing = issues.putIfAbsent(issue.path("key").asText(), issue);
        if (existing != null && existing != issue && issue.path("affectedCodes").isArray()) {
            ArrayNode codes = existing.withArray("affectedCodes");
            for (var code : issue.path("affectedCodes")) {
                boolean present = false;
                for (var value : codes) if (value.equals(code)) present = true;
                if (!present) codes.add(code);
            }
        }
    }
}
