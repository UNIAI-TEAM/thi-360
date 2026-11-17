package vn.unicom.thi360.exam.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/** Type-level checks for PUT structure; completeness and feasibility belong to BE-5b. */
public final class ExamStructureValidator {
    public static final int MAX_PATHS = 200;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private ExamStructureValidator() {
    }

    public static void validate(ExamStructureDocument document) {
        if (document == null) {
            invalid("structure", "structure is required");
        }
        Set<String> ids = new HashSet<>();
        if (document.parts().size() > 10) {
            throw new BusinessRuleException("EXAM_PATH_LIMIT_EXCEEDED");
        }
        for (ExamPart part : document.parts()) {
            if (part == null) {
                invalid("parts", "part must not be null");
            }
            validatePart(part, ids);
        }
        long paths = pathCount(document.parts());
        if (paths > MAX_PATHS) {
            throw new BusinessRuleException("EXAM_PATH_LIMIT_EXCEEDED");
        }
    }

    /** Returns the number of selectable paths, including SUBJECT_CHOICE combinations. */
    public static long countPaths(ExamStructureDocument document) {
        validate(document);
        return pathCount(document.parts());
    }

    private static void validatePart(ExamPart part, Set<String> ids) {
        if (part == null) {
            invalid("parts", "part must not be null");
        }
        id(part.id(), "parts.id", ids);
        nonNegative(part.durationMinutes(), "parts.durationMinutes");
        nonNegative(part.questionCount(), "parts.questionCount");
        nonNegative(part.questionCountOnPaper(), "parts.questionCountOnPaper");
        nonNegative(part.questionCountRequired(), "parts.questionCountRequired");
        if (part.participation() != null && part.participation().mode() == ParticipationMode.CHOICE) {
            if (part.participation().choose() == null || part.participation().choose() < 1) {
                invalid("parts.participation.choose", "choice count must be positive");
            }
        }
        if (part.maxPoints() != null && part.maxPoints().signum() <= 0) {
            invalid("parts.maxPoints", "max points must be positive");
        }
        int blockCount = 0;
        for (ExamSection section : part.sections()) {
            validateSection(section, ids);
            blockCount += section == null ? 0 : section.blocks().size();
        }
        int optionCount = 0;
        for (ExamPartOption option : part.options()) {
            validateOption(option, ids);
            optionCount++;
            if (option != null) {
                blockCount += option.blocks().size();
                for (ExamSection s : option.sections()) {
                    blockCount += s == null ? 0 : s.blocks().size();
                }
            }
        }
        if (blockCount > 30) {
            throw new BusinessRuleException("EXAM_PATH_LIMIT_EXCEEDED");
        }
        if (part.participation() != null && part.participation().mode() == ParticipationMode.CHOICE
                && optionCount > 20) {
            throw new BusinessRuleException("EXAM_PATH_LIMIT_EXCEEDED");
        }
    }

    private static void validateSection(ExamSection section, Set<String> ids) {
        if (section == null) {
            invalid("sections", "section must not be null");
        }
        id(section.id(), "sections.id", ids);
        if (section.subjectCode() == null || section.subjectCode().isBlank()) {
            invalid("sections.subjectCode", "subject code is required");
        }
        nonNegative(section.questionCount(), "sections.questionCount");
        validateScope(section.knowledgeScope(), "sections.knowledgeScope");
        validateMatrix(section.matrix(), "sections.matrix");
        if (section.answerQuotas() != null) {
            for (AnswerQuota quota : section.answerQuotas().items()) {
                if (quota == null || quota.answerType() == null || quota.value() == null
                        || quota.value().signum() < 0) {
                    invalid("sections.answerQuotas", "quota must have a non-negative value and type");
                }
                requireInteger(quota.value(), "sections.answerQuotas");
            }
        }
        for (QuestionBlock block : section.blocks()) {
            validateBlock(block, ids);
        }
    }

    private static void validateOption(ExamPartOption option, Set<String> ids) {
        if (option == null || option.kind() == null) {
            invalid("options", "option kind is required");
        }
        id(option.id(), "options.id", ids);
        if (option.code() == null || option.code().isBlank()) {
            invalid("options.code", "option code is required");
        }
        if (option.chooseSubjects() != null && option.chooseSubjects() < 1) {
            invalid("options.chooseSubjects", "chooseSubjects must be positive");
        }
        if (option.countsByPick() != null) {
            for (Integer count : option.countsByPick()) {
                if (count == null) {
                    invalid("options.countsByPick", "countsByPick elements must not be null");
                }
                nonNegative(count, "options.countsByPick");
            }
        }
        if (option.kind() == OptionKind.SUBJECT_CHOICE) {
            if (option.sections().size() > 8 || (option.chooseSubjects() != null && option.chooseSubjects() > 8)) {
                throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
            }
            if (option.chooseSubjects() != null && !option.sections().isEmpty()
                    && option.chooseSubjects() > option.sections().size()) {
                invalid("options.chooseSubjects", "chooseSubjects exceeds subject count");
            }
        }
        for (ExamSection section : option.sections()) {
            validateSection(section, ids);
        }
        for (QuestionBlock block : option.blocks()) {
            validateBlock(block, ids);
        }
    }

    private static void validateBlock(QuestionBlock block, Set<String> ids) {
        if (block == null) {
            invalid("blocks", "block must not be null");
        }
        id(block.id(), "blocks.id", ids);
        nonNegative(block.questionCount(), "blocks.questionCount");
        nonNegative(block.setCount(), "blocks.setCount");
        nonNegative(block.setSize(), "blocks.setSize");
        if ((block.setCount() == null) != (block.setSize() == null)) {
            invalid("blocks.setCount", "setCount and setSize must be supplied together");
        }
        if (block.requiredPointsPerQuestion() != null && block.requiredPointsPerQuestion().signum() <= 0) {
            invalid("blocks.requiredPointsPerQuestion", "required points must be positive");
        }
        validateScope(block.knowledgeScope(), "blocks.knowledgeScope");
        validateMatrix(block.matrix(), "blocks.matrix");
    }

    private static void validateScope(KnowledgeScope scope, String field) {
        if (scope == null) {
            return;
        }
        if (scope.includeDescendants() != null) {
            throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
        }
        if (scope.match() == null) {
            invalid(field + ".match", "knowledge match is required");
        }
        if (scope.nodeIds().stream().anyMatch(id -> id == null)) {
            invalid(field + ".nodeIds", "knowledge node id must not be null");
        }
    }

    private static void validateMatrix(BlockMatrix matrix, String field) {
        if (matrix == null) {
            return;
        }
        if (matrix.mode() == null || matrix.unit() == null) {
            invalid(field, "matrix mode and unit are required");
        }
        validateDimensionValues(matrix.cells(), field + ".cells", true, matrix.unit());
        validateDimensionValues(matrix.byGrade(), field + ".byGrade", false, matrix.unit());
        validateDimensionValues(matrix.byDifficulty(), field + ".byDifficulty", false, matrix.unit());
        validateDimensionValues(matrix.byCompetency(), field + ".byCompetency", false, matrix.unit());
    }

    private static void validateDimensionValues(List<?> values, String field, boolean cells,
            MatrixUnit unit) {
        for (Object value : values) {
            if (value == null) {
                invalid(field, "matrix value must not be null");
            }
            BigDecimal number = cells ? ((MatrixCell) value).value()
                    : ((MatrixDimensionValue) value).value();
            if (number == null || number.signum() < 0) {
                invalid(field, "matrix value must not be negative");
            }
            if (unit == MatrixUnit.PERCENT && number.compareTo(ONE_HUNDRED) > 0) {
                invalid(field, "percent matrix value must not exceed 100");
            }
            requireInteger(number, field);
        }
    }

    private static void requireInteger(BigDecimal value, String field) {
        if (value.stripTrailingZeros().scale() > 0) {
            invalid(field, "value must be an integer");
        }
    }

    private static long pathCount(List<ExamPart> parts) {
        long result = 1;
        for (ExamPart part : parts) {
            if (part.options().isEmpty() || part.participation() == null
                    || part.participation().mode() != ParticipationMode.CHOICE) {
                continue;
            }
            int choose = part.participation().choose() == null ? 1 : part.participation().choose();
            long ways = combinationsOfOptions(part.options(), choose);
            result = cappedMultiply(result, ways);
            if (result > MAX_PATHS) {
                return result;
            }
        }
        return result;
    }

    private static long combinationsOfOptions(List<ExamPartOption> options, int choose) {
        if (choose < 1 || choose > options.size()) {
            return 0;
        }
        return combinations(options, choose, 0, 1);
    }

    private static long combinations(List<ExamPartOption> options, int remaining, int index, long product) {
        if (remaining == 0) {
            return product;
        }
        long total = 0;
        for (int i = index; i <= options.size() - remaining; i++) {
            long optionWays = optionPaths(options.get(i));
            total += combinations(options, remaining - 1, i + 1, cappedMultiply(product, optionWays));
            if (total > MAX_PATHS) {
                return total;
            }
        }
        return total;
    }

    private static long optionPaths(ExamPartOption option) {
        if (option.kind() != OptionKind.SUBJECT_CHOICE || option.chooseSubjects() == null
                || option.sections().isEmpty()) {
            return 1;
        }
        return combinationsCount(option.sections().size(), option.chooseSubjects());
    }

    private static long combinationsCount(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        k = Math.min(k, n - k);
        long result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
            if (result > MAX_PATHS) {
                return result;
            }
        }
        return result;
    }

    private static long cappedMultiply(long left, long right) {
        if (left == 0 || right == 0) {
            return 0;
        }
        if (left > MAX_PATHS / right) {
            return MAX_PATHS + 1L;
        }
        return left * right;
    }

    private static void id(String value, String field, Set<String> ids) {
        if (value == null || value.isBlank() || !ids.add(value)) {
            invalid(field, "id must be non-blank and unique");
        }
    }

    private static void nonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            invalid(field, "value must not be negative");
        }
    }

    private static void invalid(String field, String message) {
        throw new BusinessRuleException("EXAM_STRUCTURE_INVALID", message);
    }
}
