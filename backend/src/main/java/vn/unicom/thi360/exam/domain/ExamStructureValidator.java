package vn.unicom.thi360.exam.domain;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/** Type-level checks for PUT structure; completeness and feasibility belong to BE-5b. */
public final class ExamStructureValidator {
    public static final int MAX_PATHS = 200;
    public static final int DEFAULT_MAX_TOPIC_QUOTAS = 8;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    /** Not legal as a question-set child (QuestionBodyValidator, QUESTION_CHILD_TYPE_NOT_ALLOWED). */
    private static final Set<QuestionType> NOT_CHILD_TYPES = EnumSet.of(QuestionType.QUESTION_SET, QuestionType.CLOZE);

    private ExamStructureValidator() {
    }

    public static void validate(ExamStructureDocument document) {
        validate(document, DEFAULT_MAX_TOPIC_QUOTAS);
    }

    public static void validate(ExamStructureDocument document, int maxTopicQuotas) {
        if (document == null) {
            invalid("structure", "structure is required");
        }
        if (maxTopicQuotas < 1) {
            throw new IllegalArgumentException("maxTopicQuotas must be positive");
        }
        Set<String> ids = new HashSet<>();
        if (document.parts().size() > 10) {
            throw new BusinessRuleException("EXAM_PATH_LIMIT_EXCEEDED");
        }
        Set<String> fixedSubjects = new HashSet<>();
        for (ExamPart part : document.parts()) {
            if (part == null) {
                invalid("parts", "part must not be null");
            }
            for (ExamSection section : part.sections()) {
                if (section != null) {
                    fixedSubjects.add(section.subjectCode());
                }
            }
        }
        for (ExamPart part : document.parts()) {
            validatePart(part, ids, maxTopicQuotas, fixedSubjects);
        }
        validateExamQuotas(document.examQuotas());
        for (int i = 0; i < document.parts().size(); i++) {
            validateDisplaySegments(document.parts().get(i), i);
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

    private static void validatePart(ExamPart part, Set<String> ids, int maxTopicQuotas,
            Set<String> fixedSubjects) {
        id(part.id(), "parts.id", ids);
        nonNegative(part.durationMinutes(), "parts.durationMinutes");
        nonNegative(part.questionCount(), "parts.questionCount");
        nonNegative(part.questionCountOnPaper(), "parts.questionCountOnPaper");
        nonNegative(part.questionCountRequired(), "parts.questionCountRequired");
        if (part.codeSource() != null
                && (!"GLOBAL_LIST".equals(part.codeSource().source())
                || part.codeSource().branch() != null
                && !Set.of("SCIENCE", "ENGLISH").contains(part.codeSource().branch()))) {
            invalid("parts.codeSource", "unsupported code source or branch");
        }
        if (part.codeSource() != null && !part.options().isEmpty()) {
            invalid("parts.options", "a code-source part must not have options");
        }
        if (part.codeSource() == null && !part.subjectPool().isEmpty()) {
            invalid("parts.subjectPool", "subjectPool requires codeSource");
        }
        for (ExamSection section : part.subjectPool()) {
            validateSection(section, ids, maxTopicQuotas);
            if (!fixedSubjects.add(section.subjectCode())) {
                invalid("parts.subjectPool", "subjectPool subject is duplicated");
            }
        }
        if (part.participation() != null && part.participation().mode() == ParticipationMode.CHOICE) {
            if (part.participation().choose() == null || part.participation().choose() < 1) {
                invalid("parts.participation.choose", "choice count must be positive");
            }
        }
        if (part.maxPoints() != null && part.maxPoints().signum() <= 0) {
            invalid("parts.maxPoints", "max points must be positive");
        }
        int blockCount = 0;
        for (ExamSection section : part.subjectPool()) {
            blockCount += section.blocks().size();
        }
        for (ExamSection section : part.sections()) {
            validateSection(section, ids, maxTopicQuotas);
            blockCount += section == null ? 0 : section.blocks().size();
        }
        int optionCount = 0;
        for (ExamPartOption option : part.options()) {
            validateOption(option, ids, maxTopicQuotas);
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

    private static void validateSection(ExamSection section, Set<String> ids, int maxTopicQuotas) {
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
        validateTopicQuotas(section.topicQuotas(), "sections.topicQuotas", maxTopicQuotas);
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
            validateBlock(block, ids, maxTopicQuotas);
        }
    }

    private static void validateOption(ExamPartOption option, Set<String> ids, int maxTopicQuotas) {
        if (option == null || option.kind() == null) {
            invalid("options.kind", "option kind is invalid or unsupported");
        }
        id(option.id(), "options.id", ids);
        if (!option.blocks().isEmpty()) {
            // The checker, the solver and the preview name the holder of the direct blocks "<option>:blocks", so no
            // stored id of the same structure may take that name (it would be two sections of one path with one id).
            id(option.id() + ":blocks", "options.blocks", ids);
        }
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
            validateSection(section, ids, maxTopicQuotas);
        }
        for (QuestionBlock block : option.blocks()) {
            validateBlock(block, ids, maxTopicQuotas);
        }
    }

    private static void validateBlock(QuestionBlock block, Set<String> ids, int maxTopicQuotas) {
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
        validateTopicQuotas(block.topicQuotas(), "blocks.topicQuotas", maxTopicQuotas);
        if (block.itemShape() != null) {
            nonNegative(block.itemShape().statements(), "blocks.itemShape.statements");
            nonNegative(block.itemShape().leftItems(), "blocks.itemShape.leftItems");
            nonNegative(block.itemShape().rightItems(), "blocks.itemShape.rightItems");
            nonNegative(block.itemShape().options(), "blocks.itemShape.options");
        }
        for (ChildCondition condition : block.childConditions()) {
            if (condition == null || condition.from() != null && condition.from() < 1
                    || condition.to() != null && condition.to() < 1
                    || condition.from() != null && condition.to() != null && condition.from() > condition.to()) {
                invalid("blocks.childConditions", "child positions must form a positive range");
            }
            if (condition.itemShape() != null) {
                nonNegative(condition.itemShape().statements(), "blocks.childConditions.itemShape.statements");
                nonNegative(condition.itemShape().leftItems(), "blocks.childConditions.itemShape.leftItems");
                nonNegative(condition.itemShape().rightItems(), "blocks.childConditions.itemShape.rightItems");
                nonNegative(condition.itemShape().options(), "blocks.childConditions.itemShape.options");
            }
        }
        validateTypedChildConditions(block);
        if (!block.slots().isEmpty()) {
            if (block.setCount() == null || (block.questionType() != QuestionType.QUESTION_SET
                    && block.questionType() != QuestionType.CLOZE)) {
                invalid("blocks.slots.index", "slots require a question set");
            }
            for (BlockSlot slot : block.slots()) {
                if (slot == null || slot.index() == null || slot.index() < 1
                        || slot.index() > block.setCount()) {
                    invalid("blocks.slots.index", "slot index must be between 1 and setCount");
                }
                if (slot.knowledgeScope() != null) {
                    validateScope(slot.knowledgeScope(), "blocks.slots.knowledgeScope");
                }
            }
        }
        if (Boolean.TRUE.equals(block.omitted()) && block.questionType() != QuestionType.ESSAY) {
            invalid("blocks.omitted", "only ESSAY blocks may be omitted");
        }
    }

    private static void validateScope(KnowledgeScope scope, String field) {
        if (scope == null) {
            return;
        }
        if (scope.hasLegacyFields()) {
            throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
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
        if (matrix.mode() == MatrixMode.COMBINED
                && effectiveEnforcement(matrix.gradeEnforcement())
                        != effectiveEnforcement(matrix.difficultyEnforcement())) {
            invalid(field, "combined matrix grade and difficulty enforcement must match");
        }
        validateDimensionValues(matrix.cells(), field + ".cells", true, matrix.unit());
        validateDimensionValues(matrix.byGrade(), field + ".byGrade", false, matrix.unit());
        validateDimensionValues(matrix.byDifficulty(), field + ".byDifficulty", false, matrix.unit());
        validateDimensionValues(matrix.byCompetency(), field + ".byCompetency", false, matrix.unit());
    }

    private static QuotaEnforcement effectiveEnforcement(QuotaEnforcement enforcement) {
        return enforcement == null ? QuotaEnforcement.REQUIRED : enforcement;
    }

    private static void validateTopicQuotas(List<TopicQuota> quotas, String field, int max) {
        if (quotas.size() > max) {
            reject("topic quota limit exceeded", "topicQuotas", "MAX_TOPIC_QUOTAS", "MAX_TOPIC_QUOTAS");
        }
        Set<String> ids = new HashSet<>();
        for (TopicQuota quota : quotas) {
            if (quota == null || quota.id() == null || !ids.add(quota.id())
                    || quota.count() == null || quota.count() < 0
                    || quota.basis() != null && !basisAllowed(quota.basis(), quota.enforcement())) {
                invalid(field, "topic quota is invalid");
            }
            if (quota.percent() != null
                    && (quota.percent().signum() < 0 || quota.percent().compareTo(ONE_HUNDRED) > 0)) {
                reject("topic quota percent must be between 0 and 100", field, "PERCENT_OUT_OF_RANGE",
                        "PERCENT_OUT_OF_RANGE");
            }
        }
    }

    /**
     * Chỉ tiêu theo câu ({@code QUESTION}) luôn hợp lệ; theo lệnh ({@code PROMPT}) chỉ ở mức đối chiếu
     * (spec H44, D19). Thiếu {@code enforcement} tính là {@code REQUIRED}.
     */
    private static boolean basisAllowed(QuotaBasis basis, QuotaEnforcement enforcement) {
        return basis == QuotaBasis.QUESTION
                || basis == QuotaBasis.PROMPT && enforcement == QuotaEnforcement.REFERENCE;
    }

    private static void validateExamQuotas(List<ExamQuota> quotas) {
        for (ExamQuota quota : quotas) {
            if (quota == null || quota.dimension() == null || quota.enforcement() == null
                    || !basisAllowed(quota.basis(), quota.enforcement())) {
                invalid("examQuotas", "exam quota is invalid");
            }
            if (quota.enforcement() == QuotaEnforcement.REQUIRED
                    && quota.subjectCodes().size() != 1) {
                throw new BusinessRuleException("EXAM_QUOTA_SCOPE_INVALID");
            }
            for (ExamQuota.Bucket bucket : quota.buckets()) {
                if (bucket == null || bucket.codes().isEmpty()
                        || bucket.count() != null && bucket.count() < 0
                        || bucket.percent() != null
                        && (bucket.percent().signum() < 0 || bucket.percent().compareTo(ONE_HUNDRED) > 0)) {
                    invalid("examQuotas", "exam quota bucket is invalid");
                }
            }
        }
    }

    /**
     * Phân đoạn hiển thị (spec H40): nối nhau phủ kín câu 1 đến {@code questionCount}, không hở, không chồng;
     * tổng điểm bằng {@code maxPoints} của phần. Lỗi chỉ tới phân đoạn.
     */
    private static void validateDisplaySegments(ExamPart part, int partIndex) {
        List<DisplaySegment> segments = part.displaySegments();
        if (segments.isEmpty()) {
            return;
        }
        String base = "parts[" + partIndex + "].displaySegments";
        Set<String> codes = new HashSet<>();
        int next = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < segments.size(); i++) {
            DisplaySegment segment = segments.get(i);
            String field = base + "[" + i + "]";
            if (segment == null || segment.code() == null || segment.code().isBlank()
                    || segment.name() == null || segment.name().isBlank()
                    || !codes.add(segment.code()) || segment.fromPosition() == null
                    || segment.toPosition() == null || segment.fromPosition() < 1
                    || segment.fromPosition() > segment.toPosition()
                    || segment.commandCount() == null || segment.commandCount() < 0
                    || segment.maxPoints() == null || segment.maxPoints().signum() < 0) {
                reject("display segment is incomplete or duplicated", field, "SEGMENT_INVALID", "SEGMENT_INVALID");
            }
            if (segment.fromPosition() > next) {
                reject("display segments leave a gap", field, "SEGMENT_GAP", "SEGMENT_GAP",
                        String.valueOf(segment.fromPosition()));
            }
            if (segment.fromPosition() < next) {
                reject("display segments overlap", field, "SEGMENT_OVERLAP", "SEGMENT_OVERLAP",
                        String.valueOf(segment.fromPosition()));
            }
            next = segment.toPosition() + 1;
            total = total.add(segment.maxPoints());
        }
        String last = base + "[" + (segments.size() - 1) + "]";
        if (part.questionCount() == null) {
            reject("display segments need the part questionCount", last, "SEGMENT_COVERAGE",
                    "SEGMENT_COVERAGE_TOTAL_MISSING");
        }
        if (next - 1 != part.questionCount()) {
            reject("display segments do not cover the part", last, "SEGMENT_COVERAGE", "SEGMENT_COVERAGE",
                    String.valueOf(next - 1), String.valueOf(part.questionCount()));
        }
        if (part.maxPoints() == null) {
            reject("display segments need the part maxPoints", last, "SEGMENT_POINTS",
                    "SEGMENT_POINTS_TOTAL_MISSING");
        }
        if (total.compareTo(part.maxPoints()) != 0) {
            reject("display segment points do not sum to the part points", last, "SEGMENT_POINTS", "SEGMENT_POINTS",
                    total.toPlainString(), part.maxPoints().toPlainString());
        }
    }

    /**
     * Chùm trộn dạng (spec H39): các điều kiện có {@code answerType} phải phủ kín vị trí 1 đến
     * {@code setSize}, không chồng nhau. Điều kiện chỉ có {@code nodeIds} không tham gia.
     */
    private static void validateTypedChildConditions(QuestionBlock block) {
        List<ChildCondition> typed = block.childConditions().stream()
                .filter(condition -> condition.answerType() != null)
                .sorted(java.util.Comparator.comparing(condition -> condition.from() == null ? 0 : condition.from()))
                .toList();
        if (typed.isEmpty()) {
            return;
        }
        if (block.questionType() != QuestionType.QUESTION_SET || block.setSize() == null) {
            invalid("blocks.childConditions", "typed child conditions require a question set with setSize");
        }
        if (block.childAnswerType() != null) {
            invalid("blocks.childAnswerType", "a mixed set must leave childAnswerType blank");
        }
        for (ChildCondition condition : typed) {
            if (NOT_CHILD_TYPES.contains(condition.answerType())) {
                invalid("blocks.childConditions.answerType", "answerType is not a legal question set child type");
            }
        }
        int next = 1;
        for (ChildCondition condition : typed) {
            if (condition.from() == null || condition.to() == null || condition.from() != next) {
                invalid("blocks.childConditions", "typed child conditions must cover positions 1 to setSize "
                        + "without gaps or overlaps");
            }
            next = condition.to() + 1;
        }
        if (next - 1 != block.setSize()) {
            invalid("blocks.childConditions", "typed child conditions must cover positions 1 to setSize "
                    + "without gaps or overlaps");
        }
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

    /** Generic field rejection; the English {@code reason} is for logs and tests, the client gets a bundle message. */
    private static void invalid(String field, String reason) {
        reject(reason, field, "STRUCTURE_FIELD_INVALID", "STRUCTURE_FIELD_INVALID", field);
    }

    /** Throws the 422; {@code messageKey} and {@code args} are resolved in the request locale at the API boundary. */
    private static void reject(String reason, String field, String code, String messageKey, String... args) {
        throw new ExamStructureInvalidException(reason,
                List.of(new ExamStructureInvalidException.Detail(field, code, messageKey, List.of(args))));
    }
}
