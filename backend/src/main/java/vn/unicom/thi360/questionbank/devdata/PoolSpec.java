package vn.unicom.thi360.questionbank.devdata;

import java.math.BigDecimal;
import java.util.List;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * One family of sample questions that the published exam set needs on top of the sample bank (DS). A family
 * produces {@code count} root questions; a root is a single question, a question set of {@code setSize} children or
 * a cloze. The codes are {@code DS-Q-<family>-<nnn>}: stable keys, so a rerun creates nothing twice.
 *
 * <p>The counts are the shortfall of the 2B sample bank against three disjoint attempts of every published exam
 * (DS AC-12), measured on the templates of the migrations. They are plain data: when a template changes and an
 * attempt runs short, raise the count of the family the solver names, nothing else changes.
 */
final class PoolSpec {
    final String family;
    final String subject;
    final QuestionType type;
    final int count;
    final BigDecimal points;
    List<String> tags = List.of();
    String competency;
    int setSize;
    QuestionType childType = QuestionType.SINGLE_CHOICE;
    /** Per child position when the set mixes types; overrides {@code childType} and {@code setSize}. */
    List<QuestionType> mixedChildren = List.of();
    int options = 4;
    int statements = 4;
    int matchingSide = 4;
    int blanks = 5;
    boolean audio;
    boolean partialMultiple;
    /** Whether the very hard level is spread too (only the exam kinds with a four-level matrix need it). */
    boolean veryHard;

    private PoolSpec(String family, String subject, QuestionType type, int count, String points) {
        this.family = family;
        this.subject = subject;
        this.type = type;
        this.count = count;
        this.points = new BigDecimal(points);
    }

    static PoolSpec of(String family, String subject, QuestionType type, int count, String points) {
        return new PoolSpec(family, subject, type, count, points);
    }

    static PoolSpec set(String family, String subject, int count, int setSize, String childPoints) {
        PoolSpec spec = new PoolSpec(family, subject, QuestionType.QUESTION_SET, count, childPoints);
        spec.setSize = setSize;
        return spec;
    }

    PoolSpec tags(String... codes) {
        this.tags = List.of(codes);
        return this;
    }

    PoolSpec competency(String code) {
        this.competency = code;
        return this;
    }

    PoolSpec audio() {
        this.audio = true;
        return this;
    }

    PoolSpec options(int count) {
        this.options = count;
        return this;
    }

    PoolSpec partial() {
        this.partialMultiple = true;
        return this;
    }

    PoolSpec veryHard() {
        this.veryHard = true;
        return this;
    }

    PoolSpec mixed(QuestionType... types) {
        this.mixedChildren = List.of(types);
        this.setSize = types.length;
        return this;
    }

}
