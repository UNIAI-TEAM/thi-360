package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * {@code QuestionBody} đọc/ghi JSON qua {@code type} (plan B1): mỗi dạng serialize có trường
 * {@code type} đúng mã và deserialize về đúng record, kể cả câu con lồng trong Bộ câu hỏi.
 * Cấu trúc JSON phải khớp kiểu {@code QuestionBody} của web.
 */
class QuestionBodyJsonTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> allBodies() {
        return Stream.of(
            Arguments.of(QuestionType.SINGLE_CHOICE, QuestionBodyValidationTests.singleChoiceBody()),
            Arguments.of(QuestionType.MULTIPLE_CHOICE, QuestionBodyValidationTests.multipleChoiceBody()),
            Arguments.of(QuestionType.SHORT_ANSWER, QuestionBodyValidationTests.shortAnswerBody()),
            Arguments.of(QuestionType.CLOZE, QuestionBodyValidationTests.clozeBody()),
            Arguments.of(QuestionType.QUESTION_SET, QuestionBodyValidationTests.questionSetBody()),
            Arguments.of(QuestionType.TRUE_FALSE, QuestionBodyValidationTests.trueFalseBody()),
            Arguments.of(QuestionType.MATCHING, QuestionBodyValidationTests.matchingBody()),
            Arguments.of(QuestionType.ESSAY, QuestionBodyValidationTests.essayBody()));
    }

    @ParameterizedTest(name = "{0} quay vòng JSON")
    @MethodSource("allBodies")
    void roundTrip(QuestionType type, QuestionBody body) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        assertTrue(json.contains("\"type\":\"" + type.name() + "\""), json);
        QuestionBody parsed = MAPPER.readValue(json, QuestionBody.class);
        assertEquals(body.getClass(), parsed.getClass());
        assertEquals(type, parsed.type());
    }

    @Test
    void nestedChildrenDeserialize() throws Exception {
        String json = MAPPER.writeValueAsString(QuestionBodyValidationTests.questionSetBody());
        QuestionBody parsed = MAPPER.readValue(json, QuestionBody.class);
        QuestionBody.QuestionSetBody set = assertInstanceOf(QuestionBody.QuestionSetBody.class, parsed);
        assertEquals(2, set.children().size());
        assertInstanceOf(QuestionBody.SingleChoiceBody.class, set.children().get(0).body());
        assertInstanceOf(QuestionBody.ShortAnswerBody.class, set.children().get(1).body());
    }

    @Test
    void readsSpecShapedJson() throws Exception {
        // JSON theo hình dạng kiểu QuestionBody của web (spec 9.2) phải đọc được trực tiếp.
        String json = """
            {"type":"SHORT_ANSWER","answers":["4","bốn"],"inputHint":"nhập số"}
            """;
        QuestionBody parsed = MAPPER.readValue(json, QuestionBody.class);
        QuestionBody.ShortAnswerBody body = assertInstanceOf(QuestionBody.ShortAnswerBody.class, parsed);
        assertEquals(List.of("4", "bốn"), body.answers());
        assertEquals("nhập số", body.inputHint());
    }

    @Test
    void matchingBodyShape() throws Exception {
        String json = """
            {"type":"MATCHING",
             "left":[{"id":"l1","content":"a"}],
             "right":[{"id":"r1","content":"x"}],
             "pairs":[{"leftId":"l1","rightId":"r1","points":1}],
             "reuseRight":false,"shuffleLeft":true,"shuffleRight":true}
            """;
        QuestionBody parsed = MAPPER.readValue(json, QuestionBody.class);
        QuestionBody.MatchingBody body = assertInstanceOf(QuestionBody.MatchingBody.class, parsed);
        assertEquals("l1", body.pairs().get(0).leftId());
        var serialized = MAPPER.readTree(MAPPER.writeValueAsString(body));
        assertEquals(false, serialized.has("shuffleLeft"));
        assertEquals(false, serialized.has("shuffleRight"));
    }
}
