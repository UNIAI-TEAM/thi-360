package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import vn.unicom.thi360.exam.api.ExamStructureResponse;
import vn.unicom.thi360.exam.api.ExamStructureSummaryResponse;
import vn.unicom.thi360.exam.api.ExamStructureSummaryResponse.Row;
import vn.unicom.thi360.exam.domain.BlockCounts;
import vn.unicom.thi360.exam.domain.ChildCondition;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ItemShape;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Summarizes the saved configuration without loading candidates or invoking the solver. */
public final class ExamCheckUnitSummary {
    private ExamCheckUnitSummary() { }

    public static ExamStructureSummaryResponse summarize(ExamStructureResponse structure) {
        return new ExamStructureSummaryResponse(structure.parts().stream()
                .map(part -> part(part, structure)).toList());
    }

    private static Row part(ExamPart part, ExamStructureResponse structure) {
        List<Row> children = new ArrayList<>();
        List<Row> common = part.sections().stream().map(section -> section(part.id(), section)).toList();
        children.addAll(common);
        List<Row> pool = part.subjectPool().stream().map(section -> section(part.id(), section)).toList();
        children.addAll(pool);
        List<Totals> alternatives = new ArrayList<>();
        if (part.codeSource() != null) {
            structure.combinations().stream().filter(c -> Objects.equals(c.partId(), part.id()))
                    .forEach(c -> {
                        List<Row> rows = new ArrayList<>();
                        for (String code : c.subjectCodes()) {
                            int index = -1;
                            for (int i = 0; i < part.subjectPool().size(); i++) {
                                if (Objects.equals(code, part.subjectPool().get(i).subjectCode())) { index = i; break; }
                            }
                            if (index < 0) { alternatives.add(Totals.UNKNOWN); return; }
                            rows.add(pool.get(index));
                        }
                        alternatives.add(sum(rows));
                    });
        } else {
            for (ExamPartOption option : part.options()) {
                List<Row> rows = new ArrayList<>(option.sections().stream()
                        .map(section -> section(part.id(), section)).toList());
                if (!option.blocks().isEmpty()) {
                    rows.add(section(part.id(), new ExamSection(option.id() + ":blocks", option.code(), null,
                            null, null, null, null, option.blocks())));
                }
                children.addAll(rows);
                alternatives.add(option.kind() == OptionKind.SUBJECT_CHOICE
                        ? subjectChoice(option, rows) : sum(rows));
            }
        }
        Totals totals = sum(common);
        if (part.codeSource() != null || part.participation() != null
                && part.participation().mode() == ParticipationMode.CHOICE) {
            Integer choose = part.codeSource() != null ? 1 : part.participation().choose();
            // Alternative totals are only meaningful when every possible selection has the same value.
            Totals selected = choices(alternatives, choose);
            totals = totals.add(selected);
        }
        return row(part.id(), null, null, part.name(), part.questionCount(), totals, null, null, children);
    }

    private static Row section(String partId, ExamSection section) {
        Integer remainder = remainderCount(section);
        List<Row> blocks = section.blocks().stream()
                .map(block -> block(partId, section.id(), block, isRemainder(block) ? remainder : null)).toList();
        return row(partId, section.id(), null, section.subjectCode(), section.questionCount(),
                sum(blocks), null, null, blocks);
    }

    private static boolean isSet(QuestionBlock block) {
        return BlockCounts.isSet(block);
    }

    private static boolean isRemainder(QuestionBlock block) {
        return BlockCounts.isRemainder(block);
    }

    private static Integer fixedCount(QuestionBlock block) {
        return BlockCounts.declared(block);
    }

    private static Integer remainderCount(ExamSection section) {
        return BlockCounts.remainder(section);
    }

    private static Row block(String partId, String sectionId, QuestionBlock block, Integer remainder) {
        boolean omitted = Boolean.TRUE.equals(block.omitted());
        boolean set = isSet(block);
        Integer count = remainder != null ? remainder : fixedCount(block);
        QuestionType answer = set && block.questionType() == QuestionType.QUESTION_SET
                ? block.childAnswerType() : block.questionType();
        Integer commands = answer == null && !omitted ? null : count;
        if (!omitted && (answer == QuestionType.TRUE_FALSE || answer == QuestionType.MATCHING)) {
            Integer perQuestion = block.itemShape() == null ? null : answer == QuestionType.TRUE_FALSE
                    ? block.itemShape().statements() : block.itemShape().leftItems();
            commands = multiply(count, perQuestion);
        }
        if (!omitted && commands == null && block.questionType() == QuestionType.QUESTION_SET) {
            // Chùm trộn dạng (H39, spec 5.2 rule 5): the answer type lives on the typed child
            // conditions, so the command count is the per-position sum over one set.
            commands = multiply(block.setCount(), typedSetCommands(block));
        }
        BigDecimal points = omitted ? BigDecimal.ZERO : count == null || block.requiredPointsPerQuestion() == null
                ? null : block.requiredPointsPerQuestion().multiply(BigDecimal.valueOf(count));
        Integer none = 0;
        Integer essays = answer == null && !omitted ? null : answer == QuestionType.ESSAY ? count : none;
        Integer objective = answer == null && !omitted ? null : answer == QuestionType.ESSAY ? none : count;
        Totals totals = new Totals(count, commands, points, essays, objective);
        return row(partId, sectionId, block.id(), block.name(), count, totals,
                set ? block.setCount() : null, set ? count : null, List.of());
    }

    private static Row row(String part, String section, String block, String name, Integer target,
            Totals totals, Integer setCount, Integer childCount, List<Row> children) {
        Long difference = target == null || totals.count() == null ? null
                : (long) totals.count() - target;
        Integer differenceInt = difference == null || difference < Integer.MIN_VALUE
                || difference > Integer.MAX_VALUE ? null : difference.intValue();
        return new Row(part, section, block, name, target, totals.count(), differenceInt, setCount, childCount,
                totals.commands(), totals.points(), totals.essays(), totals.objective(), children);
    }

    private static Integer multiply(Integer left, Integer right) {
        if (left == null || right == null) return null;
        long result = (long) left * right;
        return result < Integer.MIN_VALUE || result > Integer.MAX_VALUE ? null : (int) result;
    }

    /**
     * Commands of one set under typed child conditions (H39): every covered position contributes the
     * commands of its condition's answer type ({@code TRUE_FALSE} and {@code MATCHING} count their
     * items, every other type counts one). Null when a position is uncovered, a range is invalid or a
     * needed item shape is missing, since the declared commands are then undetermined.
     */
    private static Integer typedSetCommands(QuestionBlock block) {
        if (block.setSize() == null) {
            return null;
        }
        long sum = 0;
        for (ChildCondition condition : block.childConditions()) {
            if (condition.answerType() == null || condition.from() == null || condition.to() == null
                    || condition.from() < 1 || condition.to() > block.setSize()
                    || condition.from() > condition.to()) {
                continue;
            }
            Integer perChild = perChildCommands(condition.answerType(), condition.itemShape());
            if (perChild == null) {
                return null;
            }
            // Long arithmetic keeps an unrepresentable command count undetermined instead of throwing.
            sum += (long) (condition.to() - condition.from() + 1) * perChild;
            if (sum > Integer.MAX_VALUE) return null;
        }
        return sum == 0 ? null : (int) sum;
    }

    private static Integer perChildCommands(QuestionType answerType, ItemShape shape) {
        if (answerType == QuestionType.TRUE_FALSE) {
            return shape == null ? null : shape.statements();
        }
        if (answerType == QuestionType.MATCHING) {
            return shape == null ? null : shape.leftItems();
        }
        return 1;
    }

    private static Totals sum(List<Row> rows) {
        Totals result = Totals.ZERO;
        for (Row row : rows) result = result.add(new Totals(row.allocated(), row.commandCount(), row.maxPoints(),
                row.essayCount(), row.objectiveCount()));
        return result;
    }

    private static Totals choices(List<Totals> alternatives, Integer choose) {
        if (choose == null || choose < 1 || choose > alternatives.size()) return Totals.UNKNOWN;
        if (choose == alternatives.size()) {
            Totals result = Totals.ZERO;
            for (Totals value : alternatives) result = result.add(value);
            return result;
        }
        Totals same = alternatives.getFirst();
        for (Totals value : alternatives) same = same.agree(value);
        Totals result = Totals.ZERO;
        for (int i = 0; i < choose; i++) result = result.add(same);
        return result;
    }

    private static Totals subjectChoice(ExamPartOption option, List<Row> rows) {
        if (option.chooseSubjects() == null || option.countsByPick() == null
                || option.countsByPick().size() != option.chooseSubjects()
                || option.countsByPick().stream().anyMatch(Objects::isNull)) return Totals.UNKNOWN;
        long maximum = option.countsByPick().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (rows.stream().anyMatch(row -> !Objects.equals(row.target(), (int) maximum))) return Totals.UNKNOWN;
        Totals selected = choices(rows.stream().map(row -> new Totals(row.allocated(), row.commandCount(),
                row.maxPoints(), row.essayCount(), row.objectiveCount())).toList(), option.chooseSubjects());
        long dropped = maximum * option.chooseSubjects()
                - option.countsByPick().stream().mapToLong(Integer::longValue).sum();
        if (dropped == 0) return selected;
        if (selected.count() == null) return Totals.UNKNOWN;
        long count = selected.count() - dropped;
        Integer countInt = count < Integer.MIN_VALUE || count > Integer.MAX_VALUE
                ? null : (int) count;
        // A dropped single may carry multiple commands or different points: those values
        // cannot be inferred from the configured counts alone.
        return new Totals(countInt, null, null, null, null);
    }

    private record Totals(Integer count, Integer commands, BigDecimal points, Integer essays, Integer objective) {
        private static final Totals ZERO = new Totals(0, 0, BigDecimal.ZERO, 0, 0);
        private static final Totals UNKNOWN = new Totals(null, null, null, null, null);
        Totals add(Totals other) {
            return new Totals(addInt(count, other.count), addInt(commands, other.commands),
                    points == null || other.points == null ? null : points.add(other.points),
                    addInt(essays, other.essays), addInt(objective, other.objective));
        }
        Totals agree(Totals other) {
            return new Totals(equal(count, other.count), equal(commands, other.commands),
                    points != null && other.points != null && points.compareTo(other.points) == 0 ? points : null,
                    equal(essays, other.essays), equal(objective, other.objective));
        }
        private static Integer addInt(Integer a, Integer b) {
            if (a == null || b == null) return null;
            long sum = (long) a + b;
            return sum < Integer.MIN_VALUE || sum > Integer.MAX_VALUE ? null : (int) sum;
        }
        private static Integer equal(Integer a, Integer b) { return Objects.equals(a, b) ? a : null; }
    }
}
