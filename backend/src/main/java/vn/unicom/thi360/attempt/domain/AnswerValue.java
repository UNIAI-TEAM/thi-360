package vn.unicom.thi360.attempt.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Student answer payload stored in {@code attempt_item.answer} (LT §4, plan B1).
 *
 * <p>Uses {@code type} discriminator so Hibernate JSON serialization round-trips each variant
 * cleanly without external type metadata.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AnswerValue.SingleChoice.class, name = "SINGLE_CHOICE"),
    @JsonSubTypes.Type(value = AnswerValue.MultipleChoice.class, name = "MULTIPLE_CHOICE"),
    @JsonSubTypes.Type(value = AnswerValue.ShortAnswer.class, name = "SHORT_ANSWER"),
    @JsonSubTypes.Type(value = AnswerValue.Cloze.class, name = "CLOZE"),
    @JsonSubTypes.Type(value = AnswerValue.TrueFalse.class, name = "TRUE_FALSE"),
    @JsonSubTypes.Type(value = AnswerValue.Matching.class, name = "MATCHING"),
    @JsonSubTypes.Type(value = AnswerValue.Essay.class, name = "ESSAY"),
})
public sealed interface AnswerValue {

    record SingleChoice(String optionId) implements AnswerValue {
    }

    record MultipleChoice(List<String> optionIds) implements AnswerValue {
        public MultipleChoice {
            optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
        }
    }

    record ShortAnswer(String text) implements AnswerValue {
    }

    record Cloze(Map<String, String> blanks) implements AnswerValue {
        public Cloze {
            blanks = immutableMapAllowNullValues(blanks);
        }
    }

    record TrueFalse(Map<String, Boolean> statements) implements AnswerValue {
        public TrueFalse {
            statements = immutableMapAllowNullValues(statements);
        }
    }

    record Matching(Map<String, String> pairs) implements AnswerValue {
        public Matching {
            pairs = immutableMapAllowNullValues(pairs);
        }
    }

    record Essay(String html, Integer wordCount) implements AnswerValue {
    }

    private static <V> Map<String, V> immutableMapAllowNullValues(Map<String, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
