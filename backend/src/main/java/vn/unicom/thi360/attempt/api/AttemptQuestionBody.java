package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Schema(
        description = "Thân câu hỏi đã lọc theo allowlist cho học viên — tuyệt đối không chứa đáp án hay hướng dẫn chấm",
        oneOf = {
            AttemptQuestionBody.SingleChoice.class,
            AttemptQuestionBody.MultipleChoice.class,
            AttemptQuestionBody.ShortAnswer.class,
            AttemptQuestionBody.Cloze.class,
            AttemptQuestionBody.QuestionSet.class,
            AttemptQuestionBody.TrueFalse.class,
            AttemptQuestionBody.Matching.class,
            AttemptQuestionBody.Essay.class
        })
public sealed interface AttemptQuestionBody {

    QuestionType type();

    record SingleChoice(QuestionType type, List<OptionView> options) implements AttemptQuestionBody {
        public SingleChoice {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    record MultipleChoice(QuestionType type, List<OptionView> options, String scoringRule)
            implements AttemptQuestionBody {
        public MultipleChoice {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    record ShortAnswer(QuestionType type, String inputHint, String answerFormat)
            implements AttemptQuestionBody {}

    record Cloze(QuestionType type, String passage, List<ClozeBlankView> blanks)
            implements AttemptQuestionBody {
        public Cloze {
            blanks = blanks == null ? List.of() : List.copyOf(blanks);
        }
    }

    record QuestionSet(QuestionType type, String passage, Integer blankNumberStart)
            implements AttemptQuestionBody {}

    record TrueFalse(QuestionType type, List<StatementView> statements)
            implements AttemptQuestionBody {
        public TrueFalse {
            statements = statements == null ? List.of() : List.copyOf(statements);
        }
    }

    record Matching(
            QuestionType type, List<SideView> left, List<SideView> right, Boolean reuseRight)
            implements AttemptQuestionBody {
        public Matching {
            left = left == null ? List.of() : List.copyOf(left);
            right = right == null ? List.of() : List.copyOf(right);
        }
    }

    record Essay(QuestionType type, Integer wordLimit) implements AttemptQuestionBody {}

    record OptionView(String id, String content) {}

    record ClozeBlankView(String id) {}

    record StatementView(String id, String content) {}

    record SideView(String id, String content) {}
}
