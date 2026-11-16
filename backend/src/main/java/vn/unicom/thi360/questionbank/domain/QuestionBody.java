package vn.unicom.thi360.questionbank.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Phần thân câu hỏi theo dạng (spec mục 4.3, hợp đồng dữ liệu spec 9.2). Jackson đọc/ghi theo
 * trường {@code type} (plan B1). Mỗi dạng là một record; các phần tử con (phương án, mệnh đề,
 * ô trống, mục ghép, câu con) có {@code id} do client sinh để giữ ổn định giữa các phiên bản.
 *
 * <p>Sơ đồ JSON của từng dạng phải khớp với kiểu {@code QuestionBody} của web
 * ({@code features/questions/types.ts} sau FE-3a); đổi một bên phải đổi cả hai.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = QuestionBody.SingleChoiceBody.class, name = "SINGLE_CHOICE"),
    @JsonSubTypes.Type(value = QuestionBody.MultipleChoiceBody.class, name = "MULTIPLE_CHOICE"),
    @JsonSubTypes.Type(value = QuestionBody.ShortAnswerBody.class, name = "SHORT_ANSWER"),
    @JsonSubTypes.Type(value = QuestionBody.ClozeBody.class, name = "CLOZE"),
    @JsonSubTypes.Type(value = QuestionBody.QuestionSetBody.class, name = "QUESTION_SET"),
    @JsonSubTypes.Type(value = QuestionBody.TrueFalseBody.class, name = "TRUE_FALSE"),
    @JsonSubTypes.Type(value = QuestionBody.MatchingBody.class, name = "MATCHING"),
    @JsonSubTypes.Type(value = QuestionBody.EssayBody.class, name = "ESSAY"),
})
public sealed interface QuestionBody {

    /** Mã dạng câu của phần thân này; phải trùng {@code type} của câu (spec 4.3). */
    QuestionType type();

    /** Trắc nghiệm một đáp án: 2-10 phương án không trống, đúng 1 phương án đúng khi Sẵn sàng. */
    record SingleChoiceBody(
        QuestionType type,
        List<ChoiceOption> options,
        Boolean shuffleOptions) implements QuestionBody {

        public SingleChoiceBody {
            type = QuestionType.SINGLE_CHOICE;
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** Trắc nghiệm nhiều đáp án: 2-10 phương án, ít nhất 2 phương án đúng (PBI-03 mục 10). */
    record MultipleChoiceBody(
        QuestionType type,
        List<ChoiceOption> options,
        Boolean shuffleOptions,
        MultipleChoiceRule scoringRule) implements QuestionBody {

        public MultipleChoiceBody {
            type = QuestionType.MULTIPLE_CHOICE;
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /**
     * Trả lời ngắn: {@code answers.get(0)} là đáp án chuẩn, sau là các đáp án được chấp nhận
     * (tối đa 20); {@code inputHint} là hướng dẫn nhập.
     */
    record ShortAnswerBody(
        QuestionType type,
        List<String> answers,
        String inputHint) implements QuestionBody {

        public ShortAnswerBody {
            type = QuestionType.SHORT_ANSWER;
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }

    /**
     * Điền khuyết: {@code passage} (HTML) chứa các dấu vị trí {@code [[id]]}; mỗi {@code Blank}
     * có id không trùng, đáp án và điểm riêng (mặc định 1, phải &gt; 0). Tối đa 30 vị trí; mỗi
     * dấu vị trí có đúng một ô và ngược lại.
     */
    record ClozeBody(
        QuestionType type,
        String passage,
        List<ClozeBlank> blanks) implements QuestionBody {

        public ClozeBody {
            type = QuestionType.CLOZE;
            blanks = blanks == null ? List.of() : List.copyOf(blanks);
        }
    }

    /**
     * Bộ câu hỏi (câu chùm): {@code passage} là ngữ liệu chung, {@code children} là các câu con
     * đầy đủ phần thân. Sẵn sàng khi có ít nhất 2 câu con đạt điều kiện của dạng mình; dạng câu
     * con theo spec D19 (không cho chùm hay Điền khuyết làm câu con).
     */
    record QuestionSetBody(
        QuestionType type,
        String passage,
        List<ChildQuestion> children) implements QuestionBody {

        public QuestionSetBody {
            type = QuestionType.QUESTION_SET;
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    /**
     * Đúng/Sai: 2-8 mệnh đề, mỗi mệnh đề có đáp án. {@code scoringMethod} là
     * {@code PER_STATEMENT} (mỗi mệnh đề có {@code points}, tổng bằng điểm câu) hoặc
     * {@code COUNT_TABLE} ({@code scoreTable} dài bằng số mệnh đề, không giảm, phần tử cuối
     * bằng điểm câu, mọi phần tử &ge; 0).
     */
    record TrueFalseBody(
        QuestionType type,
        List<TrueFalseStatement> statements,
        TrueFalseScoringMethod scoringMethod,
        List<BigDecimal> scoreTable) implements QuestionBody {

        public TrueFalseBody {
            type = QuestionType.TRUE_FALSE;
            statements = statements == null ? List.of() : List.copyOf(statements);
            scoreTable = scoreTable == null ? List.of() : List.copyOf(scoreTable);
        }
    }

    /**
     * Ghép đôi: {@code left} là mục cần ghép, {@code right} là lựa chọn (được nhiều hơn left);
     * mỗi {@code pair} trỏ một mục trái tới một lựa chọn phải kèm điểm; {@code reuseRight} cho
     * một lựa chọn dùng nhiều lần. Sẵn sàng: mọi mục trái có cặp, cặp trỏ tới lựa chọn có thật,
     * {@code reuseRight = false} thì mỗi lựa chọn dùng tối đa một lần, tổng điểm cặp = điểm câu.
     */
    record MatchingBody(
        QuestionType type,
        List<MatchingSide> left,
        List<MatchingSide> right,
        List<MatchingPair> pairs,
        Boolean reuseRight,
        Boolean shuffleLeft,
        Boolean shuffleRight) implements QuestionBody {

        public MatchingBody {
            type = QuestionType.MATCHING;
            left = left == null ? List.of() : List.copyOf(left);
            right = right == null ? List.of() : List.copyOf(right);
            pairs = pairs == null ? List.of() : List.copyOf(pairs);
        }
    }

    /**
     * Tự luận: chấm tay. Sẵn sàng bắt buộc {@code gradingGuide} (HTML, spec D20); {@code rubric}
     * tùy chọn, có thì tổng điểm tiêu chí phải bằng điểm câu; {@code wordLimit} để trống hoặc &gt; 0.
     */
    record EssayBody(
        QuestionType type,
        String gradingGuide,
        List<EssayCriterion> rubric,
        String sampleAnswer,
        Integer wordLimit) implements QuestionBody {

        public EssayBody {
            type = QuestionType.ESSAY;
            rubric = rubric == null ? List.of() : List.copyOf(rubric);
        }
    }

    /** Phương án của câu trắc nghiệm. */
    record ChoiceOption(String id, String content, Boolean correct) {}

    /** Một ô trống của câu Điền khuyết; {@code id} khớp dấu vị trí {@code [[id]]} trong ngữ liệu. */
    record ClozeBlank(String id, List<String> answers, BigDecimal points) {
        public ClozeBlank {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }

    /**
     * Câu con của bộ câu hỏi: phần thân theo dạng (D19), phân loại kế thừa của bộ khi để trống
     * (spec 9.2 — {@code null} là kế thừa). {@code points} mặc định 1 khi trống.
     *
     * <p>Câu con là dòng {@code question} riêng (spec 4.1): {@code id} là id bản ghi đó, do máy
     * chủ cấp khi lưu (câu con mới gửi lên có {@code id = null}); snapshot của câu con là một
     * dòng {@code question_version} riêng có cùng {@code version_no} với bộ. Phân loại riêng của
     * câu con nằm trên bản ghi của nó ({@code gradeItemId}/{@code levelItemId}/{@code
     * knowledgeItemIds} ở đây chỉ giữ giá trị ghi đè mà request gửi lên).
     */
    record ChildQuestion(
        QuestionType type,
        String content,
        QuestionBody body,
        String solution,
        BigDecimal points,
        UUID gradeItemId,
        UUID levelItemId,
        List<UUID> knowledgeItemIds,
        UUID id) {

        public ChildQuestion {
            knowledgeItemIds = knowledgeItemIds == null ? List.of() : List.copyOf(knowledgeItemIds);
        }
    }

    /** Mệnh đề Đúng/Sai; {@code points} chỉ dùng khi chấm {@code PER_STATEMENT}. */
    record TrueFalseStatement(String id, String content, Boolean answer, BigDecimal points) {}

    /** Một phần tử của hai bên Ghép đôi. */
    record MatchingSide(String id, String content) {}

    /** Một cặp ghép: mục trái {@code leftId} khớp lựa chọn {@code rightId}, điểm riêng. */
    record MatchingPair(String leftId, String rightId, BigDecimal points) {}

    /** Một tiêu chí của hướng dẫn chấm câu Tự luận. */
    record EssayCriterion(String id, String description, BigDecimal points) {}

    /** Quy tắc chấm câu Nhiều đáp án: đúng hết mới có điểm, hoặc chấm theo tỉ lệ. */
    enum MultipleChoiceRule {
        ALL_OR_NOTHING,
        PARTIAL,
    }

    /** Cách chấm câu Đúng/Sai nhiều mệnh đề (spec 4.3). */
    enum TrueFalseScoringMethod {
        /** Điểm cộng theo từng mệnh đề đúng; mỗi mệnh đề khai {@code points} riêng. */
        PER_STATEMENT,
        /** Điểm theo bảng: {@code scoreTable[i]} là điểm khi đúng {@code i + 1} mệnh đề. */
        COUNT_TABLE,
    }
}
