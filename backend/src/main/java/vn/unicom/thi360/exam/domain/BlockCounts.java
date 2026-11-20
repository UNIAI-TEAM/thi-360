package vn.unicom.thi360.exam.domain;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * The one rule for how many questions a group takes, shared by the checker, the structure summary, the check
 * presenter and the solver input (FU-BE15-R6-1). A set with a set shape takes {@code setCount x setSize} whatever
 * its {@code questionCount} says, because that is what the solver draws; a group that declares no count at all is a
 * "take the remainder" group and takes what the section count leaves.
 */
public final class BlockCounts {
    private BlockCounts() {
    }

    public static boolean isSet(QuestionBlock block) {
        return block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE;
    }

    /** A "take the remainder" group: not omitted, not a set, and without its own question count. */
    public static boolean isRemainder(QuestionBlock block) {
        return !Boolean.TRUE.equals(block.omitted()) && !isSet(block) && block.questionCount() == null;
    }

    /**
     * Questions the group is configured with, whether or not it is omitted: {@code setCount x setSize} for a set that
     * has a set shape, else its question count. Null when the configuration states none, or an incomplete or
     * unrepresentable set shape.
     */
    public static Integer configured(QuestionBlock block) {
        if (isSet(block) && (block.setCount() != null || block.setSize() != null)) {
            if (block.setCount() == null || block.setSize() == null) return null;
            long product = (long) block.setCount() * block.setSize();
            return product < Integer.MIN_VALUE || product > Integer.MAX_VALUE ? null : (int) product;
        }
        return block.questionCount();
    }

    /** Questions the group takes by its own declaration: zero when omitted; null for a remainder group. */
    public static Integer declared(QuestionBlock block) {
        return Boolean.TRUE.equals(block.omitted()) ? Integer.valueOf(0) : configured(block);
    }

    /**
     * Count of the single remainder group of a section: the section count minus what the other groups declare. Null
     * when the section has no count, does not have exactly one remainder group, another group's count is undetermined,
     * or the result is negative.
     */
    public static Integer remainder(ExamSection section) {
        if (section.questionCount() == null || section.blocks().stream().filter(BlockCounts::isRemainder).count() != 1) {
            return null;
        }
        long fixed = 0;
        for (QuestionBlock other : section.blocks()) {
            if (isRemainder(other)) continue;
            Integer count = declared(other);
            if (count == null) return null;
            fixed += count;
            if (fixed > Integer.MAX_VALUE) return null;
        }
        long left = section.questionCount() - fixed;
        return left < 0 || left > Integer.MAX_VALUE ? null : Integer.valueOf((int) left);
    }
}
