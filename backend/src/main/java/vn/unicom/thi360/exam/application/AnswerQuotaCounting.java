package vn.unicom.thi360.exam.application;

import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Which row of a subject's answer-type quota a counting unit belongs to (UNI-2754).
 *
 * <p>A subject whose quota has a QUESTION_SET ("Bộ câu hỏi") row counts every child unit of a QUESTION_SET
 * group in that row only, so the rows still add up to the subject count. A subject without the row counts
 * set children by their own answer type, as before. The group's own child-type filter is not affected.
 */
final class AnswerQuotaCounting {
    static final String QUESTION_SET = QuestionType.QUESTION_SET.name();

    private AnswerQuotaCounting() {
    }

    static boolean hasSetRow(ExamSection section) {
        return section != null && section.answerQuotas() != null && section.answerQuotas().items().stream()
                .anyMatch(item -> item.answerType() == QuestionType.QUESTION_SET);
    }

    static boolean hasSetRow(SectionOnPath section) {
        return section.subjectQuota() != null && section.subjectQuota().answerQuotas() != null
                && section.subjectQuota().answerQuotas().items().stream()
                        .anyMatch(item -> QUESTION_SET.equals(item.code()));
    }

    /** True when the units of {@code block} count in the QUESTION_SET row of {@code section}. */
    static boolean countsInSetRow(ExamSection section, QuestionBlock block) {
        return block != null && block.questionType() == QuestionType.QUESTION_SET && hasSetRow(section);
    }

    /** The quota row key a unit of {@code block} counts under: QUESTION_SET, or the unit's own answer type. */
    static String key(ExamSection section, QuestionBlock block, QuestionType unitType) {
        return countsInSetRow(section, block) ? QUESTION_SET : unitType.name();
    }

    /**
     * The row a picked unit of a solved path counts under: QUESTION_SET for a child of a set group (a unit of a
     * non-single group that is not a cloze blank) in a subject with the row, otherwise its own answer type.
     */
    static String key(SectionOnPath section, GroupQuota group, QuestionType unitType) {
        boolean child = group != null && group.kind() != GroupQuota.GroupKind.SINGLE
                && unitType != QuestionType.CLOZE;
        return child && hasSetRow(section) ? QUESTION_SET : unitType.name();
    }
}
