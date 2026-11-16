package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.unicom.thi360.exam.domain.BlockMatrix;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MatrixMode;
import vn.unicom.thi360.exam.domain.MatrixUnit;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Structural and exact-quota checks; source feasibility remains the solver's responsibility. */
final class ExamCheckRules {
    private final Exam exam;
    private final ExamStructureDocument document;
    private final ExamCheckIssues issues;
    private final Map<String, SectionContext> sections = new LinkedHashMap<>();
    private final Map<String, ExamPartOption> options = new LinkedHashMap<>();
    private final Map<String, ExamPart> parts = new LinkedHashMap<>();

    ExamCheckRules(ObjectMapper mapper, Exam exam, ExamStructureDocument document) {
        this.exam = exam;
        this.document = document;
        this.issues = new ExamCheckIssues(mapper);
        for (ExamPart part : document.parts()) {
            parts.put(part.id(), part);
            for (ExamSection section : part.sections()) sections.put(section.id(), new SectionContext(part, null, section));
            for (ExamPartOption option : part.options()) {
                options.put(option.id(), option);
                for (ExamSection section : option.sections()) sections.put(section.id(), new SectionContext(part, option.id(), section));
                if (!option.blocks().isEmpty()) {
                    int count = option.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
                    ExamSection synthetic = new ExamSection(option.id() + ":blocks", option.code(), count,
                            null, null, null, null, null, option.blocks());
                    sections.put(synthetic.id(), new SectionContext(part, option.id(), synthetic));
                }
            }
        }
    }

    List<ObjectNode> common() {
        List<ObjectNode> found = new ArrayList<>();
        if (document.parts().isEmpty() || exam.getName() == null || exam.getName().isBlank()
                || exam.getCode() == null || exam.getCode().isBlank()
                || ("PART".equals(exam.getScope()) && (exam.getPartCode() == null || exam.getPartCode().isBlank()))) {
            found.add(issue("INFO_MISSING", "INFO", null, null, null));
        }
        if (exam.getStructureYear() != null && exam.getMatrixYear() != null
                && !exam.getStructureYear().equals(exam.getMatrixYear()) && !exam.isYearsConfirmed()) {
            ObjectNode issue = issue("YEARS_UNCONFIRMED", "INFO", null, null, null);
            issue.put("required", exam.getStructureYear()); issue.put("available", exam.getMatrixYear()); found.add(issue);
        }
        for (ExamPart part : document.parts()) {
            if (isChoice(part) && part.options().isEmpty()) found.add(issue("OPTION_EMPTY", "STRUCTURE", part, null, null));
        }
        return found;
    }

    /** Detect inputs that the solver's strict value records cannot represent, including old jsonb. */
    List<ObjectNode> unrepresentable() {
        List<ObjectNode> found = new ArrayList<>();
        for (ExamPart part : document.parts()) {
            if (isChoice(part) && (part.participation().choose() == null || part.participation().choose() < 1
                    || part.participation().choose() > part.options().size())) {
                found.add(issue("OPTION_EMPTY", "STRUCTURE", part, null, null));
            }
            for (ExamPartOption option : part.options()) {
                if (!option.blocks().isEmpty() && option.kind() != OptionKind.SUBJECT) {
                    found.add(issue("OPTION_EMPTY", "STRUCTURE", part, option.id(), null));
                }
                if (!option.blocks().isEmpty() && option.kind() == OptionKind.SUBJECT
                        && option.blocks().stream().anyMatch(block -> !isSet(block) && block.questionCount() == null)) {
                    found.add(issue("REMAINDER_GROUP_INVALID", "STRUCTURE", part, option.id(), null));
                }
                if (option.kind() == OptionKind.SUBJECT_CHOICE) {
                    ObjectNode invalid = choiceIssue(part, option);
                    if (invalid != null) found.add(invalid);
                }
            }
        }
        for (SectionContext context : sections.values()) {
            ExamSection section = context.section;
            if (section.id() == null || section.subjectCode() == null) {
                found.add(issue("INFO_MISSING", "INFO", context.part, context.optionId, section));
            }
            long remainders = section.blocks().stream().filter(block -> block.questionCount() == null && !isSet(block)).count();
            if (remainders > 1) found.add(issue("REMAINDER_GROUP_INVALID", "STRUCTURE", context.part, context.optionId, section));
            if (section.answerQuotas() != null) {
                for (var value : section.answerQuotas().items()) {
                    if (value.answerType() == null) found.add(issue("INFO_MISSING", "INFO", context.part, context.optionId, section));
                    if (value.value() != null && value.value().signum() < 0) found.add(negative(context, null, value.value(), null));
                    if (notInteger(value.value())) found.add(nonInteger(context, null, value.value(), null));
                }
            }
            fractionalMatrix(found, context, null, section.matrix());
            for (QuestionBlock block : section.blocks()) {
                if (block.id() == null) found.add(issue("OPTION_EMPTY", "STRUCTURE", context.part, context.optionId, section));
                if (isSet(block) && (block.setCount() == null || block.setSize() == null)) {
                    ObjectNode invalid = sectionIssue("SET_SIZE_MISMATCH", "QUESTION_SET", context, null);
                    blockContext(invalid, block); found.add(issues.key(invalid));
                }
                fractionalMatrix(found, context, block, block.matrix());
            }
        }
        return found;
    }

    List<ObjectNode> path(PathResult result) {
        List<ObjectNode> found = new ArrayList<>();
        for (var selected : result.path().chosen()) {
            ExamPartOption option = options.get(selected.optionId());
            ExamPart part = parts.get(selected.partId());
            if (option == null || part == null) continue;
            if (Boolean.FALSE.equals(option.expertConfirmed())) found.add(issue("OPTION_UNCONFIRMED", "STRUCTURE", part, option.id(), null));
            if (option.kind() == OptionKind.SUBJECT_CHOICE) {
                ObjectNode invalid = choiceIssue(part, option);
                if (invalid != null) found.add(invalid);
            }
        }
        Map<String, Set<Integer>> possible = new HashMap<>();
        for (var picks : result.path().picks()) for (SubjectPick pick : picks) {
            for (int i = 0; i < pick.sections().size(); i++) {
                possible.computeIfAbsent(pick.sections().get(i).sectionId(), ignored -> new LinkedHashSet<>()).add(pick.counts().get(i));
            }
        }
        for (SectionOnPath section : result.path().sections()) {
            int count = section.questionCount() == null ? 0 : section.questionCount();
            section(found, section.sectionId(), count, Set.of(count));
        }
        for (var entry : possible.entrySet()) for (int count : entry.getValue()) section(found, entry.getKey(), count, entry.getValue());
        totals(found, result);
        return dedupe(found);
    }

    private void totals(List<ObjectNode> found, PathResult result) {
        List<SectionOnPath> counted = new ArrayList<>(result.path().sections());
        for (List<SubjectPick> choices : result.path().picks()) {
            if (!choices.isEmpty()) counted.addAll(choices.getFirst().resolvedSections());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> points = new LinkedHashMap<>();
        Set<String> missingPoints = new LinkedHashSet<>();
        int total = 0;
        for (SectionOnPath section : counted) {
            SectionContext context = sections.get(section.sectionId());
            if (context == null) continue;
            int count = section.questionCount() == null ? 0 : section.questionCount();
            counts.merge(context.part.id(), count, Integer::sum);
            ExamPartOption option = context.optionId == null ? null : options.get(context.optionId);
            boolean subjectChoice = option != null && option.kind() == OptionKind.SUBJECT_CHOICE;
            if (subjectChoice) total += count;
            int fixed = context.section.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
            for (QuestionBlock block : context.section.blocks()) {
                int groupCount = block.questionCount() == null ? Math.max(0, count - fixed) : fixedCount(block);
                if (!subjectChoice) total += groupCount;
                if (block.requiredPointsPerQuestion() == null) {
                    missingPoints.add(context.part.id());
                    if (exam.getMaxScore() != null) {
                        ObjectNode issue = sectionIssue("POINTS_TOTAL_MISMATCH", "POINTS", context, null);
                        blockContext(issue, block);
                        found.add(issues.key(issue));
                    }
                } else {
                    points.merge(context.part.id(), block.requiredPointsPerQuestion()
                            .multiply(BigDecimal.valueOf(groupCount)), BigDecimal::add);
                }
            }
        }
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (ExamPart part : document.parts()) {
            int count = counts.getOrDefault(part.id(), 0);
            if (part.questionCount() != null && part.questionCount() != count) {
                ObjectNode issue = issue("PART_TOTAL_MISMATCH", "STRUCTURE", part, null, null);
                issue.put("required", part.questionCount()); issue.put("available", count); found.add(issue);
            }
            BigDecimal actual = points.getOrDefault(part.id(), BigDecimal.ZERO);
            totalPoints = totalPoints.add(actual);
            if (part.maxPoints() != null && !missingPoints.contains(part.id()) && differs(actual, part.maxPoints())) {
                ObjectNode issue = issue("PART_POINTS_MISMATCH", "POINTS", part, null, null);
                issue.put("required", part.maxPoints()); issue.put("available", actual); found.add(issue);
            }
        }
        if (exam.getQuestionCount() != total) {
            ObjectNode issue = issue("PATH_TOTAL_MISMATCH", "STRUCTURE", null, null, null);
            issue.put("required", exam.getQuestionCount()); issue.put("available", total); found.add(issue);
        }
        BigDecimal target = "LINEAR".equals(exam.getPracticeScoring()) ? exam.getReferenceMax() : exam.getMaxScore();
        if (target != null && missingPoints.isEmpty() && differs(totalPoints, target)) {
            ObjectNode issue = issue("POINTS_TOTAL_MISMATCH", "POINTS", null, null, null);
            issue.put("required", target); issue.put("available", totalPoints); found.add(issue);
        }
    }

    private static boolean differs(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(new BigDecimal("0.000001")) > 0;
    }

    private void section(List<ObjectNode> found, String id, int count, Set<Integer> possible) {
        SectionContext context = sections.get(id);
        if (context == null) return;
        ExamSection section = context.section;
        boolean varies = possible.size() > 1;
        int min = possible.stream().mapToInt(Integer::intValue).min().orElse(count);
        int max = possible.stream().mapToInt(Integer::intValue).max().orElse(count);
        int fixed = section.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
        List<QuestionBlock> remaining = section.blocks().stream().filter(block -> block.questionCount() == null).toList();
        if (remaining.size() > 1 || remaining.stream().anyMatch(ExamCheckRules::isSet)
                || (!remaining.isEmpty() && fixed > min) || (varies && remaining.isEmpty())) {
            ObjectNode issue = sectionIssue("REMAINDER_GROUP_INVALID", "STRUCTURE", context, varies ? count : null);
            issue.put("required", min); issue.put("available", fixed); found.add(issues.key(issue));
        } else if (remaining.isEmpty() && fixed != count) {
            ObjectNode issue = sectionIssue("SECTION_TOTAL_MISMATCH", "STRUCTURE", context, varies ? count : null);
            issue.put("required", count); issue.put("available", fixed); found.add(issues.key(issue));
        }
        if (section.answerQuotas() == null) for (QuestionBlock block : section.blocks()) {
            if (block.questionType() == null && !isSet(block)) {
                ObjectNode issue = sectionIssue("GROUP_ANSWER_TYPE_MISSING", "STRUCTURE", context, varies ? count : null);
                blockContext(issue, block); found.add(issues.key(issue));
            }
        }
        boolean countVaries = varies && ((section.answerQuotas() != null && section.answerQuotas().unit() == MatrixUnit.COUNT)
                || (section.matrix() != null && section.matrix().unit() == MatrixUnit.COUNT));
        if (countVaries) {
            ObjectNode issue = sectionIssue("SUBJECT_QUOTA_COUNT_VARIES", "MATRIX", context, count);
            issue.put("required", min); issue.put("available", max); found.add(issues.key(issue));
        }
        if (section.answerQuotas() != null) {
            values(found, context, null, section.answerQuotas().items().stream().map(value -> value.value()).toList(),
                    section.answerQuotas().unit(), count, null, "ANSWER_QUOTA_TOTAL_MISMATCH", countVaries, varies ? count : null);
        }
        matrix(found, context, null, section.matrix(), count, "SUBJECT_MATRIX_TOTAL_MISMATCH", countVaries, varies ? count : null);
        for (QuestionBlock block : section.blocks()) {
            int groupCount = block.questionCount() == null ? Math.max(0, count - fixed) : fixedCount(block);
            if (isSet(block) && block.questionCount() != null
                    && (block.setCount() == null || block.setSize() == null || block.setCount() * block.setSize() != block.questionCount())) {
                ObjectNode issue = sectionIssue("SET_SIZE_MISMATCH", "QUESTION_SET", context, varies ? count : null);
                blockContext(issue, block); issue.put("required", block.questionCount());
                issue.put("available", (block.setCount() == null ? 0 : block.setCount()) * (block.setSize() == null ? 0 : block.setSize()));
                found.add(issues.key(issue));
            }
            matrix(found, context, block, block.matrix(), groupCount, "MATRIX_TOTAL_MISMATCH", false, varies ? count : null);
        }
    }

    private void matrix(List<ObjectNode> found, SectionContext context, QuestionBlock block, BlockMatrix matrix,
                        int count, String countCode, boolean countVaries, Integer subjectCount) {
        if (matrix == null) return;
        if (matrix.mode() == MatrixMode.COMBINED) {
            values(found, context, block, matrix.cells().stream().map(cell -> cell.value()).toList(), matrix.unit(), count,
                    null, countCode, countVaries, subjectCount);
        } else {
            values(found, context, block, matrix.byGrade().stream().map(value -> value.value()).toList(), matrix.unit(), count,
                    "GRADE", countCode, countVaries, subjectCount);
            values(found, context, block, matrix.byDifficulty().stream().map(value -> value.value()).toList(), matrix.unit(), count,
                    "DIFFICULTY", countCode, countVaries, subjectCount);
        }
        if (!matrix.byCompetency().isEmpty()) values(found, context, block,
                matrix.byCompetency().stream().map(value -> value.value()).toList(), matrix.unit(), count,
                "COMPETENCY", countCode, countVaries, subjectCount);
    }

    private void values(List<ObjectNode> found, SectionContext context, QuestionBlock block, List<BigDecimal> values,
                        MatrixUnit unit, int count, String dimension, String countCode, boolean countVaries, Integer subjectCount) {
        if (values.stream().anyMatch(ExamCheckRules::notInteger)) return; // handled before solver construction
        BigDecimal minimum = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        String code = minimum.signum() < 0 ? "MATRIX_NEGATIVE"
                : unit == MatrixUnit.PERCENT && sum.compareTo(BigDecimal.valueOf(100)) != 0 ? "PERCENT_TOTAL_INVALID"
                : unit != MatrixUnit.PERCENT && sum.compareTo(BigDecimal.valueOf(count)) != 0 && !countVaries ? countCode : null;
        if (code == null) return;
        ObjectNode issue = sectionIssue(code, "MATRIX", context, subjectCount);
        if (block != null) blockContext(issue, block);
        issue.put("dimension", dimension);
        issue.put("required", code.equals("MATRIX_NEGATIVE") ? 0 : unit == MatrixUnit.PERCENT ? 100 : count);
        issue.put("available", code.equals("MATRIX_NEGATIVE") ? minimum : sum);
        found.add(issues.key(issue));
    }

    private ObjectNode choiceIssue(ExamPart part, ExamPartOption option) {
        int choose = option.chooseSubjects() == null ? 0 : option.chooseSubjects();
        List<Integer> counts = option.countsByPick() == null ? List.of() : option.countsByPick();
        int common = part.sections().stream().mapToInt(section -> section.questionCount() == null ? 0 : section.questionCount()).sum();
        int target = (part.questionCount() == null ? 0 : part.questionCount()) - common;
        boolean invalid = choose < 1 || choose > option.sections().size() || option.sections().size() > 8
                || counts.size() != choose || counts.stream().anyMatch(value -> value == null || value <= 0)
                || counts.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum() != target
                || option.sections().stream().anyMatch(section -> section.choiceGroup() == null);
        if (!invalid) invalid = !allocationsMatch(option, choose, counts);
        if (!invalid) return null;
        ObjectNode issue = issue("SUBJECT_CHOICE_COUNTS_INVALID", "STRUCTURE", part, option.id(), null);
        issue.put("required", target); issue.put("available", counts.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        return issue;
    }

    private static boolean allocationsMatch(ExamPartOption option, int choose, List<Integer> counts) {
        int natural = (int) option.sections().stream().filter(section -> section.choiceGroup().name().equals("NATURAL")).count();
        int social = option.sections().size() - natural;
        int high = counts.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int low = counts.stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<Integer> expected = counts.stream().sorted().toList();
        for (int n = Math.max(0, choose - social); n <= Math.min(choose, natural); n++) {
            int s = choose - n;
            if (n > 0 && s > 0 && (n == s || choose != 3)) return false;
            int lows = n == 0 || s == 0 ? 1 : Math.min(n, s);
            List<Integer> actual = new ArrayList<>(choose);
            for (int i = 0; i < choose; i++) actual.add(i < lows ? low : high);
            if (!actual.stream().sorted().toList().equals(expected)) return false;
        }
        return true;
    }

    private void fractionalMatrix(List<ObjectNode> found, SectionContext context, QuestionBlock block, BlockMatrix matrix) {
        if (matrix == null) return;
        if (matrix.mode() == null) {
            ObjectNode issue = sectionIssue("MATRIX_INCOMPLETE", "MATRIX", context, null);
            if (block != null) blockContext(issue, block);
            found.add(issues.key(issue));
        }
        matrix.cells().forEach(value -> invalidValue(found, context, block, value.value(), null));
        matrix.byGrade().forEach(value -> invalidValue(found, context, block, value.value(), "GRADE"));
        matrix.byDifficulty().forEach(value -> invalidValue(found, context, block, value.value(), "DIFFICULTY"));
        matrix.byCompetency().forEach(value -> invalidValue(found, context, block, value.value(), "COMPETENCY"));
    }

    private void invalidValue(List<ObjectNode> found, SectionContext context, QuestionBlock block, BigDecimal value, String dimension) {
        if (notInteger(value)) found.add(nonInteger(context, block, value, dimension));
        else if (value.signum() < 0) found.add(negative(context, block, value, dimension));
    }

    private ObjectNode negative(SectionContext context, QuestionBlock block, BigDecimal value, String dimension) {
        ObjectNode issue = sectionIssue("MATRIX_NEGATIVE", "MATRIX", context, null);
        if (block != null) blockContext(issue, block);
        issue.put("dimension", dimension); issue.put("required", 0); issue.put("available", value);
        return issues.key(issue);
    }

    private ObjectNode nonInteger(SectionContext context, QuestionBlock block, BigDecimal value, String dimension) {
        ObjectNode issue = sectionIssue("MATRIX_NON_INTEGER", "MATRIX", context, null);
        if (block != null) blockContext(issue, block);
        issue.put("dimension", dimension); if (value != null) issue.put("available", value);
        return issues.key(issue);
    }

    private static boolean notInteger(BigDecimal value) {
        if (value == null) return true;
        try { value.intValueExact(); return false; } catch (ArithmeticException e) { return true; }
    }

    private ObjectNode sectionIssue(String code, String group, SectionContext context, Integer subjectCount) {
        ObjectNode issue = issue(code, group, context.part, context.optionId, context.section);
        if (subjectCount != null) issue.put("subjectCount", subjectCount);
        return issue;
    }

    private ObjectNode issue(String code, String group, ExamPart part, String optionId, ExamSection section) {
        ObjectNode issue = issues.create(code, group, "ERROR");
        if (part != null) { issue.put("partId", part.id()); issue.put("partName", part.name()); }
        issue.put("optionId", optionId);
        if (section != null) { issue.put("sectionId", section.id()); issue.put("subjectCode", section.subjectCode()); }
        return issues.key(issue);
    }

    private static void blockContext(ObjectNode issue, QuestionBlock block) { issue.put("blockId", block.id()); issue.put("blockName", block.name()); }
    private static boolean isChoice(ExamPart part) { return part.participation() != null && part.participation().mode() == ParticipationMode.CHOICE; }
    private static boolean isSet(QuestionBlock block) { return block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE; }
    private static int fixedCount(QuestionBlock block) {
        if (block.questionCount() != null) return block.questionCount();
        return block.setCount() == null || block.setSize() == null ? 0 : block.setCount() * block.setSize();
    }
    private static List<ObjectNode> dedupe(List<ObjectNode> found) {
        Map<String, ObjectNode> result = new LinkedHashMap<>();
        found.forEach(issue -> result.putIfAbsent(issue.path("key").asText(), issue));
        return List.copyOf(result.values());
    }
    private record SectionContext(ExamPart part, String optionId, ExamSection section) { }
}
