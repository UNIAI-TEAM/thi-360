package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import vn.unicom.thi360.exam.domain.BlockMatrix;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MatrixMode;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SelectionResult;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;

/** Presents the solver's proved paths and individual subject-choice outcomes. */
final class ExamCheckPathPresenter {
    private final ObjectMapper mapper;
    private final Exam exam;
    private final ExamStructureDocument document;
    private final ExamCheckIssues issues;
    private final ExamCheckSolverIssues solverIssues;
    private final ExamCheckSources sources;
    private final Map<String, SectionInfo> sections = new LinkedHashMap<>();
    private final Map<String, ExamPart> parts = new LinkedHashMap<>();
    private final Map<String, ExamPartOption> options = new LinkedHashMap<>();

    ExamCheckPathPresenter(ObjectMapper mapper, Exam exam, ExamStructureDocument document,
                           ExamCheckIssues issues, ExamCheckSolverIssues solverIssues,
                           ExamCheckSources sources) {
        this.mapper = mapper;
        this.exam = exam;
        this.document = document;
        this.issues = issues;
        this.solverIssues = solverIssues;
        this.sources = sources;
        for (ExamPart part : document.parts()) {
            parts.put(part.id(), part);
            for (ExamSection section : part.subjectPool()) sections.put(section.id(), new SectionInfo(part, null, section));
            for (ExamSection section : part.sections()) sections.put(section.id(), new SectionInfo(part, null, section));
            for (ExamPartOption option : part.options()) {
                options.put(option.id(), option);
                for (ExamSection section : option.sections()) sections.put(section.id(), new SectionInfo(part, option.id(), section));
                if (!option.blocks().isEmpty()) {
                    int count = option.blocks().stream().mapToInt(ExamCheckPathPresenter::fixedCount).sum();
                    ExamSection synthetic = new ExamSection(option.id() + ":blocks", option.code(), count,
                            null, null, null, null, option.blocks());
                    sections.put(synthetic.id(), new SectionInfo(part, option.id(), synthetic));
                }
            }
        }
    }

    ObjectNode render(PathResult result, List<ObjectNode> structuralIssues) {
        var path = result.path();
        ObjectNode out = mapper.createObjectNode();
        String pathId = path.chosen().isEmpty() ? "default"
                : String.join("+", path.chosen().stream().map(choice -> choice.optionId()).toList());
        out.put("pathId", pathId);
        ArrayNode choices = out.putArray("choices");
        for (var choice : path.chosen()) {
            ExamPartOption option = options.get(choice.optionId());
            ObjectNode item = choices.addObject();
            item.put("partId", choice.partId());
            item.put("optionId", choice.optionId());
            if (parts.get(choice.partId()) != null && parts.get(choice.partId()).codeSource() != null) {
                String prefix = choice.partId() + ":";
                String code = choice.optionId().startsWith(prefix) ? choice.optionId().substring(prefix.length()) : choice.optionId();
                item.put("kind", "COMBINATION");
                item.put("code", code);
                item.put("name", code);
            }
            if (option != null) {
                item.put("kind", option.kind() == null ? null : option.kind().name());
                item.put("code", option.code());
                item.put("name", option.name() == null ? option.code() : option.name());
            }
        }
        ArrayNode partIds = out.putArray("partIds");
        document.parts().forEach(part -> partIds.add(part.id()));

        LinkedHashMap<String, ObjectNode> allIssues = new LinkedHashMap<>();
        structuralIssues.forEach(issue -> add(allIssues, issue));
        ArrayNode selections = out.putArray("selections");
        Map<List<SubjectPick>, SelectionResult> evaluated = new LinkedHashMap<>();
        result.selections().forEach(selection -> evaluated.put(selection.picks(), selection));
        List<List<SubjectPick>> tuples = new ArrayList<>();
        if (!path.picks().isEmpty()) product(path.picks(), 0, new ArrayList<>(), tuples);
        for (List<SubjectPick> tuple : tuples) {
            SelectionResult selection = evaluated.get(tuple);
            ObjectNode value = selections.addObject();
            ArrayNode codes = value.putArray("subjectCodes");
            ArrayNode counts = value.putArray("counts");
            for (SubjectPick pick : tuple) {
                pick.subjectCodes().forEach(codes::add);
                pick.counts().forEach(counts::add);
            }
            SolveStatus status = selection == null ? SolveStatus.UNCONFIRMED : selection.status();
            ArrayNode selectionIssues = value.putArray("issues");
            if (selection != null && status != SolveStatus.VALID) {
                ObjectNode issue = solverIssues.failure(selection.reason(), selection.subjectCode(),
                        selection.groupId(), selection.failingQuota(), tuple);
                diagnoseShortage(issue, tuple.stream().flatMap(pick -> pick.resolvedSections().stream()).toList());
                selectionIssues.add(issue);
                add(allIssues, issue);
            } else if (selection == null) {
                ObjectNode issue = solverIssues.failure(vn.unicom.thi360.exam.domain.solver.SolveReason.DEADLINE_EXCEEDED,
                        null, null, null, tuple);
                selectionIssues.add(issue);
                add(allIssues, issue);
            }
            for (ObjectNode issue : structuralIssues) {
                if (affectsSelection(issue, tuple)) selectionIssues.add(issue);
            }
            boolean selectionError = false, selectionUnconfirmed = false;
            for (var issue : selectionIssues) {
                selectionError |= "ERROR".equals(issue.path("severity").asText());
                selectionUnconfirmed |= "UNCONFIRMED".equals(issue.path("severity").asText());
            }
            value.put("result", selectionError || status == SolveStatus.INVALID ? "INVALID"
                    : selectionUnconfirmed || status == SolveStatus.UNCONFIRMED ? "UNCONFIRMED" : "VALID");
        }
        if (result.status() != SolveStatus.VALID && result.reason() != null && tuples.isEmpty()) {
            ObjectNode issue = solverIssues.failure(result.reason(), result.subjectCode(), result.groupId(),
                    result.failingQuota(), List.of());
            diagnoseShortage(issue, path.sections());
            add(allIssues, issue);
        }

        Map<String, SectionOnPath> shown = new LinkedHashMap<>();
        path.sections().forEach(section -> shown.putIfAbsent(section.sectionId(), section));
        for (List<SubjectPick> tuple : tuples) {
            for (SubjectPick pick : tuple) {
                for (SectionOnPath section : pick.resolvedSections()) shown.putIfAbsent(section.sectionId(), section);
            }
        }
        // Fetch each source once before resolving warnings, so the compact projection is copied only once.
        for (SectionOnPath section : shown.values()) for (GroupQuota group : section.groups()) sources.pool(group);
        ArrayNode blocks = out.putArray("blocks");
        ArrayNode subjects = out.putArray("subjects");
        for (SectionOnPath section : shown.values()) {
            SectionInfo info = sections.get(section.sectionId());
            if (info == null) continue;
            int sectionCount = section.questionCount() == null ? 0 : section.questionCount();
            int fixed = section.groups().stream().mapToInt(ExamCheckPathPresenter::fixedCount).sum();
            Map<String, List<GroupQuota>> groupsByBlock = new LinkedHashMap<>();
            for (GroupQuota group : section.groups()) {
                String blockId = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
                groupsByBlock.computeIfAbsent(blockId, ignored -> new ArrayList<>()).add(group);
            }
            for (var entry : groupsByBlock.entrySet()) {
                QuestionBlock block = info.section.blocks().stream().filter(candidate -> Objects.equals(candidate.id(), entry.getKey()))
                        .findFirst().orElse(null);
                if (block == null) continue;
                ObjectNode combined = null;
                for (GroupQuota group : entry.getValue()) {
                    int required = group.kind() == GroupQuota.GroupKind.SET ? fixedCount(group)
                            : group.questionCount() == null ? Math.max(0, sectionCount - fixed) : group.questionCount();
                    ObjectNode row = block(info, block, group, required, allIssues);
                    if (combined == null) combined = row;
                    else {
                        combined.put("required", combined.path("required").asInt() + required);
                        if ("NOT_FOUND".equals(row.path("result").asText())) combined.put("result", "NOT_FOUND");
                        else if ("UNCONFIRMED".equals(row.path("result").asText())
                                && !"NOT_FOUND".equals(combined.path("result").asText())) combined.put("result", "UNCONFIRMED");
                        ArrayNode existing = (ArrayNode) combined.path("issues");
                        for (var issue : row.path("issues")) {
                            boolean present = false;
                            for (var value : existing) if (value.path("key").equals(issue.path("key"))) present = true;
                            if (!present) existing.add(issue);
                        }
                    }
                }
                if (combined != null) {
                    if (sources.complete(entry.getValue())) {
                        combined.put("available", sources.union(entry.getValue()).size());
                        if (block.setSize() != null) {
                            long sets = entry.getValue().stream().flatMap(group -> sources.pool(group).candidates().stream())
                                    .filter(candidate -> candidate.setId() != null && candidate.units().size() == block.setSize())
                                    .map(candidate -> candidate.questionId()).distinct().count();
                            combined.put("availableSets", sets);
                        }
                    } else {
                        combined.putNull("available");
                        combined.putNull("availableSets");
                    }
                    blocks.add(combined);
                }
            }
            subjects.add(subject(info, section, sectionCount));
        }
        List<SectionOnPath> counted = new ArrayList<>(path.sections());
        if (!tuples.isEmpty()) for (SubjectPick pick : tuples.getFirst()) counted.addAll(pick.resolvedSections());
        int totalQuestions = 0;
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (SectionOnPath section : counted) {
            SectionInfo info = sections.get(section.sectionId());
            if (info == null) continue;
            ExamPartOption option = info.optionId == null ? null : options.get(info.optionId);
            boolean subjectChoice = option != null && option.kind() == vn.unicom.thi360.exam.domain.OptionKind.SUBJECT_CHOICE;
            if (subjectChoice) totalQuestions += section.questionCount() == null ? 0 : section.questionCount();
            int fixed = section.groups().stream().mapToInt(ExamCheckPathPresenter::fixedCount).sum();
            for (QuestionBlock block : info.section.blocks()) {
                if (Boolean.TRUE.equals(block.omitted())) continue;
                int count = block.questionCount() == null
                        ? Math.max(0, (section.questionCount() == null ? 0 : section.questionCount()) - fixed)
                        : fixedCount(block);
                if (!subjectChoice) totalQuestions += count;
                if (block.requiredPointsPerQuestion() != null) {
                    totalPoints = totalPoints.add(block.requiredPointsPerQuestion().multiply(BigDecimal.valueOf(count)));
                }
            }
        }
        int minutes = document.parts().stream().mapToInt(part -> part.durationMinutes() == null ? 0 : part.durationMinutes()).sum();
        out.put("totalQuestions", totalQuestions);
        out.put("totalMinutes", "SHARED".equals(exam.getTiming()) && exam.getDurationMinutes() != null ? exam.getDurationMinutes() : minutes);
        out.put("totalPoints", totalPoints);
        ArrayNode pathIssues = out.putArray("issues");
        allIssues.values().forEach(pathIssues::add);
        boolean error = allIssues.values().stream().anyMatch(issue -> issue.path("severity").asText().equals("ERROR"));
        boolean uncertain = allIssues.values().stream().anyMatch(issue -> issue.path("severity").asText().equals("UNCONFIRMED"));
        out.put("result", error || result.status() == SolveStatus.INVALID ? "INVALID"
                : uncertain || result.status() == SolveStatus.UNCONFIRMED ? "UNCONFIRMED" : "VALID");
        return out;
    }

    /** Add only shortages directly demonstrated by a complete source projection. */
    private void diagnoseShortage(ObjectNode issue, List<SectionOnPath> sectionsOnPath) {
        if (!"CELL_SHORTAGE".equals(issue.path("code").asText())) return;
        String blockId = issue.path("blockId").asText(null);
        for (SectionOnPath section : sectionsOnPath) {
            SectionInfo info = sections.get(section.sectionId());
            if (info == null) continue;
            int fixed = section.groups().stream().mapToInt(ExamCheckPathPresenter::fixedCount).sum();
            for (GroupQuota group : section.groups()) {
                if (!Objects.equals(blockId, group.groupId())) continue;
                QuestionBlock block = info.section.blocks().stream()
                        .filter(candidate -> Objects.equals(blockId, candidate.id())).findFirst().orElse(null);
                if (block == null) return;
                ExamCheckSources.Pool pool = sources.pool(group);
                if (!pool.complete()) return;
                int target = group.kind() == GroupQuota.GroupKind.SET ? fixedCount(group)
                        : group.questionCount() == null
                        ? Math.max(0, (section.questionCount() == null ? 0 : section.questionCount()) - fixed)
                        : group.questionCount();
                ObjectNode requirements = requirement(block.matrix(), target);
                if (requirements != null) {
                    List<CountingUnit> units = sources.union(List.of(group));
                    for (var cell : requirements.path("cells")) {
                        String grade = cell.path("gradeCode").asText();
                        String difficulty = cell.path("difficultyCode").asText();
                        int required = cell.path("required").asInt();
                        long available = units.stream().filter(unit -> grade.equals(unit.gradeCode())
                                && difficulty.equals(unit.difficultyCode())).count();
                        if (available < required) {
                            issue.put("gradeCode", grade); issue.put("difficultyCode", difficulty);
                            issue.put("required", required); issue.put("available", available);
                            issues.key(issue);
                            return;
                        }
                    }
                }
                if (group.kind() == GroupQuota.GroupKind.SINGLE && pool.units() < target) {
                    issue.put("required", target); issue.put("available", pool.units());
                }
                return;
            }
        }
    }

    private ObjectNode block(SectionInfo info, QuestionBlock block, GroupQuota group, int required,
                             Map<String, ObjectNode> pathIssues) {
        ObjectNode out = mapper.createObjectNode();
        out.put("partId", info.part.id()); out.put("partName", info.part.name());
        if (info.part.order() == null) out.putNull("partOrder"); else out.put("partOrder", info.part.order());
        out.put("sectionId", info.section.id()); out.put("subjectCode", info.section.subjectCode());
        out.put("optionId", info.optionId); out.put("blockId", block.id()); out.put("blockName", block.name());
        out.put("questionType", block.questionType() == null ? null : block.questionType().name());
        out.put("required", required);
        if (block.setCount() == null) out.putNull("setCount"); else out.put("setCount", block.setCount());
        if (block.setSize() == null) out.putNull("setSize"); else out.put("setSize", block.setSize());
        ExamCheckSources.Pool pool = sources.pool(group);
        if (pool.complete()) out.put("available", pool.units()); else out.putNull("available");
        if (group.kind() == GroupQuota.GroupKind.SET && block.setSize() != null && pool.complete()) out.put("availableSets", pool.sets(block.setSize()));
        else out.putNull("availableSets");
        out.set("requirement", requirement(block.matrix(), required));
        ArrayNode blockIssues = out.putArray("issues");
        for (ObjectNode issue : pathIssues.values()) {
            if (Objects.equals(block.id(), issue.path("blockId").asText(null))) blockIssues.add(issue);
        }
        if (!pool.complete()) {
            ObjectNode issue = solverIssues.failure(vn.unicom.thi360.exam.domain.solver.SolveReason.DEADLINE_EXCEEDED,
                    info.section.subjectCode(), group.groupId(), null, List.of());
            addBlockIssue(pathIssues, blockIssues, issue);
        } else {
            boolean set = group.kind() == GroupQuota.GroupKind.SET;
            long available = set ? pool.sets(group.setSize()) : pool.units();
            int target = set ? group.setCount() : required;
            if (available < target) {
                ObjectNode issue = solverIssues.failure(vn.unicom.thi360.exam.domain.solver.SolveReason.INSUFFICIENT_SOURCE,
                        info.section.subjectCode(), group.groupId(), null, List.of());
                issue.put("required", target);
                issue.put("available", available);
                addBlockIssue(pathIssues, blockIssues, issue);
            }
        }
        if (block.expectedScoring() != null && pool.complete()) {
            ExamCheckSources.Mismatches mismatch = sources.mismatchedCodes(group, block.expectedScoring());
            if (!mismatch.complete()) {
                ObjectNode issue = solverIssues.failure(vn.unicom.thi360.exam.domain.solver.SolveReason.DEADLINE_EXCEEDED,
                        info.section.subjectCode(), block.id(), null, List.of());
                addBlockIssue(pathIssues, blockIssues, issue);
            } else if (!mismatch.codes().isEmpty()) {
                ObjectNode issue = context(issues.create("SCORING_MISMATCH", "POINTS", "WARNING"), info, block);
                issue.put("available", mismatch.codes().size());
                issue.set("questionCodes", mapper.valueToTree(mismatch.codes()));
                issues.key(issue); blockIssues.add(issue); add(pathIssues, issue);
            }
        }
        boolean invalid = false, uncertain = !pool.complete();
        for (var issue : blockIssues) {
            invalid |= issue.path("severity").asText().equals("ERROR");
            uncertain |= issue.path("severity").asText().equals("UNCONFIRMED");
        }
        out.put("result", invalid ? "NOT_FOUND" : uncertain ? "UNCONFIRMED" : "FOUND");
        return out;
    }

    private ObjectNode subject(SectionInfo info, SectionOnPath section, int count) {
        ObjectNode out = mapper.createObjectNode();
        out.put("sectionId", section.sectionId()); out.put("subjectCode", section.subjectCode());
        out.put("optionId", info.optionId); out.put("questionCount", count);
        ArrayNode answers = out.putArray("answerQuotas");
        ArrayNode grades = out.putArray("byGrade");
        ArrayNode difficulties = out.putArray("byDifficulty");
        List<CountingUnit> units = sources.union(section.groups());
        boolean complete = sources.complete(section.groups());
        var quota = info.section.answerQuotas();
        if (quota != null) {
            int[] values = ExamCheckQuantities.resolve(quota.items().stream().map(item -> item.value()).toList(), quota.unit(), count);
            if (values != null) for (int i = 0; i < values.length; i++) {
                String type = quota.items().get(i).answerType().name();
                long have = units.stream().filter(unit -> type.equals(unit.answerType())).count();
                ObjectNode entry = answers.addObject(); entry.put("answerType", type); entry.put("required", values[i]);
                if (complete) entry.put("achieved", Math.min(values[i], have)); else entry.putNull("achieved");
            }
        }
        ObjectNode matrix = requirement(info.section.matrix(), count);
        if (matrix != null) {
            if ("COMBINED".equals(matrix.path("mode").asText())) {
                Map<String, Integer> gradeTotals = new LinkedHashMap<>(), difficultyTotals = new LinkedHashMap<>();
                for (var cell : matrix.path("cells")) {
                    gradeTotals.merge(cell.path("gradeCode").asText(), cell.path("required").asInt(), Integer::sum);
                    difficultyTotals.merge(cell.path("difficultyCode").asText(), cell.path("required").asInt(), Integer::sum);
                }
                dimensions(grades, gradeTotals, units, true, complete); dimensions(difficulties, difficultyTotals, units, false, complete);
            } else {
                Map<String, Integer> gradeTotals = dimensionMap(matrix.path("byGrade"));
                Map<String, Integer> difficultyTotals = dimensionMap(matrix.path("byDifficulty"));
                dimensions(grades, gradeTotals, units, true, complete); dimensions(difficulties, difficultyTotals, units, false, complete);
            }
        }
        return out;
    }

    private void dimensions(ArrayNode out, Map<String, Integer> values, List<CountingUnit> units,
                            boolean grade, boolean complete) {
        for (var value : values.entrySet()) {
            long have = units.stream().filter(unit -> value.getKey().equals(grade ? unit.gradeCode() : unit.difficultyCode())).count();
            ObjectNode item = out.addObject(); item.put("code", value.getKey()); item.put("required", value.getValue());
            if (complete) item.put("achieved", Math.min(value.getValue(), have)); else item.putNull("achieved");
        }
    }

    private static Map<String, Integer> dimensionMap(com.fasterxml.jackson.databind.JsonNode nodes) {
        Map<String, Integer> values = new LinkedHashMap<>();
        nodes.forEach(node -> values.put(node.path("code").asText(), node.path("required").asInt()));
        return values;
    }

    private ObjectNode requirement(BlockMatrix matrix, int count) {
        if (matrix == null || matrix.mode() == null) return null;
        ObjectNode out = mapper.createObjectNode(); out.put("mode", matrix.mode().name());
        ArrayNode cells = out.putArray("cells"), grades = out.putArray("byGrade"),
                difficulties = out.putArray("byDifficulty"), competencies = out.putArray("byCompetency");
        if (matrix.mode() == MatrixMode.COMBINED) {
            int[] amounts = ExamCheckQuantities.resolve(matrix.cells().stream().map(cell -> cell.value()).toList(), matrix.unit(), count);
            if (amounts == null) return null;
            for (int i = 0; i < amounts.length; i++) {
                var cell = matrix.cells().get(i); ObjectNode value = cells.addObject();
                value.put("gradeCode", cell.gradeCode()); value.put("difficultyCode", cell.difficultyCode()); value.put("required", amounts[i]);
            }
        } else if (!dimension(grades, matrix.byGrade(), matrix, count) || !dimension(difficulties, matrix.byDifficulty(), matrix, count)) return null;
        if (!matrix.byCompetency().isEmpty() && !dimension(competencies, matrix.byCompetency(), matrix, count)) return null;
        return out;
    }

    private static boolean dimension(ArrayNode out, List<vn.unicom.thi360.exam.domain.MatrixDimensionValue> items,
                                     BlockMatrix matrix, int count) {
        int[] amounts = ExamCheckQuantities.resolve(items.stream().map(item -> item.value()).toList(), matrix.unit(), count);
        if (amounts == null) return false;
        for (int i = 0; i < amounts.length; i++) { ObjectNode value = out.addObject(); value.put("code", items.get(i).code()); value.put("required", amounts[i]); }
        return true;
    }

    private static ObjectNode context(ObjectNode issue, SectionInfo info, QuestionBlock block) {
        issue.put("partId", info.part.id()); issue.put("partName", info.part.name()); issue.put("sectionId", info.section.id());
        issue.put("subjectCode", info.section.subjectCode()); issue.put("optionId", info.optionId);
        issue.put("blockId", block.id()); issue.put("blockName", block.name()); return issue;
    }

    private static boolean affectsSelection(ObjectNode issue, List<SubjectPick> tuple) {
        if (issue.has("selection")) {
            for (var entry : issue.path("selection")) {
                boolean matched = false;
                for (SubjectPick pick : tuple) {
                    for (int i = 0; i < pick.sections().size(); i++) {
                        if (Objects.equals(entry.path("sectionId").asText(), pick.sections().get(i).sectionId())
                                && entry.path("count").asInt() == pick.counts().get(i)) matched = true;
                    }
                }
                if (!matched) return false;
            }
            return true;
        }
        String sectionId = issue.path("sectionId").asText(null);
        String optionId = issue.path("optionId").asText(null);
        if (sectionId == null && optionId == null) return false;
        for (SubjectPick pick : tuple) {
            if (optionId != null && !optionId.equals(pick.optionId())) continue;
            if (sectionId == null) return true;
            for (int i = 0; i < pick.sections().size(); i++) {
                if (sectionId.equals(pick.sections().get(i).sectionId())
                        && (issue.path("subjectCount").isNull()
                            || issue.path("subjectCount").asInt() == pick.counts().get(i))) return true;
            }
        }
        return false;
    }

    private static void addBlockIssue(Map<String, ObjectNode> issues, ArrayNode blockIssues, ObjectNode issue) {
        ObjectNode existing = issues.putIfAbsent(issue.path("key").asText(), issue);
        if (existing == null) {
            blockIssues.add(issue);
        } else {
            if (!issue.path("required").isNull()) existing.set("required", issue.path("required"));
            if (!issue.path("available").isNull()) existing.set("available", issue.path("available"));
        }
    }

    private static void add(Map<String, ObjectNode> issues, ObjectNode issue) { issues.putIfAbsent(issue.path("key").asText(), issue); }
    private static void product(List<List<SubjectPick>> choices, int index, List<SubjectPick> prefix, List<List<SubjectPick>> out) {
        if (index == choices.size()) { out.add(List.copyOf(prefix)); return; }
        for (SubjectPick pick : choices.get(index)) { prefix.add(pick); product(choices, index + 1, prefix, out); prefix.removeLast(); }
    }
    private static int fixedCount(QuestionBlock block) {
        if (block.questionCount() != null) return block.questionCount();
        return block.setCount() == null || block.setSize() == null ? 0 : block.setCount() * block.setSize();
    }
    private static int fixedCount(GroupQuota group) {
        if (group.kind() == GroupQuota.GroupKind.SET) return group.setCount() * group.setSize();
        return group.questionCount() == null ? 0 : group.questionCount();
    }
    private record SectionInfo(ExamPart part, String optionId, ExamSection section) { }
}
