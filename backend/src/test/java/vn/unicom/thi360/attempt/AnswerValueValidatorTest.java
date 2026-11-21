package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AnswerValueValidator;
import vn.unicom.thi360.attempt.application.AnswerValueValidator.ValidationResult;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.questionbank.application.HtmlContentSanitizerAdapter;
import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Unit tests for {@link AnswerValueValidator}: type matching, snapshot ID validation, 64 KB
 * boundary, Essay text-only sanitization, and BS §6 word counting (AC-7, AC-8, BS §6).
 */
class AnswerValueValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnswerValueValidator validator =
            new AnswerValueValidator(
                    new HtmlContentSanitizerAdapter(
                            new QuestionHtmlSanitizer(QuestionAssetResolver.none())),
                    objectMapper);

    @Test
    void validatesAndClearsSingleChoice() throws Exception {
        JsonNode snap =
                objectMapper.readTree(
                        """
                        {"body":{"type":"SINGLE_CHOICE","options":[{"id":"o1"},{"id":"o2"}]}}
                        """);

        assertThat(validator.validate(QuestionType.SINGLE_CHOICE, snap, null).valid()).isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SINGLE_CHOICE,
                                        snap,
                                        new AnswerValue.SingleChoice(null))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SINGLE_CHOICE,
                                        snap,
                                        new AnswerValue.SingleChoice("o2"))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SINGLE_CHOICE,
                                        snap,
                                        new AnswerValue.SingleChoice("unknown"))
                                .valid())
                .isFalse();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SINGLE_CHOICE,
                                        snap,
                                        new AnswerValue.MultipleChoice(List.of("o1")))
                                .valid())
                .isFalse();
    }

    @Test
    void validatesMultipleChoiceIncludingEmptyDuplicatesAndUnknownIds() throws Exception {
        JsonNode snap =
                objectMapper.readTree(
                        """
                        {"body":{"type":"MULTIPLE_CHOICE","options":[{"id":"a"},{"id":"b"},{"id":"c"}]}}
                        """);

        assertThat(validator.validate(QuestionType.MULTIPLE_CHOICE, snap, null).valid()).isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MULTIPLE_CHOICE,
                                        snap,
                                        new AnswerValue.MultipleChoice(List.of()))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MULTIPLE_CHOICE,
                                        snap,
                                        new AnswerValue.MultipleChoice(List.of("a", "c")))
                                .valid())
                .isTrue();
        // Duplicates rejected
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MULTIPLE_CHOICE,
                                        snap,
                                        new AnswerValue.MultipleChoice(List.of("a", "a")))
                                .valid())
                .isFalse();
        // Unknown ID rejected
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MULTIPLE_CHOICE,
                                        snap,
                                        new AnswerValue.MultipleChoice(List.of("a", "z")))
                                .valid())
                .isFalse();
        // Blank element rejected; null element rejected at value object construction
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MULTIPLE_CHOICE,
                                        snap,
                                        new AnswerValue.MultipleChoice(List.of("a", "")))
                                .valid())
                .isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                new AnswerValue.MultipleChoice(
                                        java.util.Arrays.asList("a", null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesShortAnswerWithExact64KbBoundary() throws Exception {
        JsonNode snap = objectMapper.readTree("""
                {"body":{"type":"SHORT_ANSWER"}}
                """);

        assertThat(validator.validate(QuestionType.SHORT_ANSWER, snap, null).valid()).isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SHORT_ANSWER,
                                        snap,
                                        new AnswerValue.ShortAnswer(null))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SHORT_ANSWER,
                                        snap,
                                        new AnswerValue.ShortAnswer("42"))
                                .valid())
                .isTrue();

        String exact64Kb = "a".repeat(65_536);
        assertThat(exact64Kb.getBytes(StandardCharsets.UTF_8)).hasSize(65_536);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SHORT_ANSWER,
                                        snap,
                                        new AnswerValue.ShortAnswer(exact64Kb))
                                .valid())
                .isTrue();

        String over64Kb = "a".repeat(65_537);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SHORT_ANSWER,
                                        snap,
                                        new AnswerValue.ShortAnswer(over64Kb))
                                .valid())
                .isFalse();

        // Multi-byte UTF-8 boundary check: 3-byte char 'ệ' pushed over 65,536 bytes
        String multiByteOver = "ệ".repeat(21_846);
        assertThat(multiByteOver.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(65_536);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SHORT_ANSWER,
                                        snap,
                                        new AnswerValue.ShortAnswer(multiByteOver))
                                .valid())
                .isFalse();

        // NUL byte rejected (R1-11)
        assertThat(
                        validator
                                .validate(
                                        QuestionType.SHORT_ANSWER,
                                        snap,
                                        new AnswerValue.ShortAnswer("abc\u0000def"))
                                .valid())
                .isFalse();
    }

    @Test
    void validatesClozeTrueFalseMatchingAndAlwaysRejectsQuestionSet() throws Exception {
        JsonNode clozeSnap =
                objectMapper.readTree(
                        """
                        {"body":{"type":"CLOZE","blanks":[{"id":"b1"},{"id":"b2"}]}}
                        """);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.CLOZE,
                                        clozeSnap,
                                        new AnswerValue.Cloze(Map.of()))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.CLOZE,
                                        clozeSnap,
                                        new AnswerValue.Cloze(
                                                new java.util.LinkedHashMap<>() {
                                                    {
                                                        put("b1", "word");
                                                        put("b2", null);
                                                    }
                                                }))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.CLOZE,
                                        clozeSnap,
                                        new AnswerValue.Cloze(Map.of("b9", "x")))
                                .valid())
                .isFalse();
        // NUL byte in Cloze blank value rejected (R1-11)
        assertThat(
                        validator
                                .validate(
                                        QuestionType.CLOZE,
                                        clozeSnap,
                                        new AnswerValue.Cloze(Map.of("b1", "a\u0000b")))
                                .valid())
                .isFalse();

        // Cloze serialized JSON 64 KB boundary (R1-21)
        int clozeOverhead = objectMapper.writeValueAsBytes(new AnswerValue.Cloze(Map.of("b1", ""))).length;
        AnswerValue.Cloze exactCloze64Kb =
                new AnswerValue.Cloze(Map.of("b1", "x".repeat(AnswerValueValidator.MAX_ANSWER_BYTES - clozeOverhead)));
        AnswerValue.Cloze overCloze64Kb =
                new AnswerValue.Cloze(Map.of("b1", "x".repeat(AnswerValueValidator.MAX_ANSWER_BYTES - clozeOverhead + 1)));
        assertThat(objectMapper.writeValueAsBytes(exactCloze64Kb)).hasSize(AnswerValueValidator.MAX_ANSWER_BYTES);
        assertThat(objectMapper.writeValueAsBytes(overCloze64Kb)).hasSize(AnswerValueValidator.MAX_ANSWER_BYTES + 1);
        assertThat(validator.validate(QuestionType.CLOZE, clozeSnap, exactCloze64Kb).valid()).isTrue();
        assertThat(validator.validate(QuestionType.CLOZE, clozeSnap, overCloze64Kb).valid()).isFalse();

        JsonNode tfSnap =
                objectMapper.readTree(
                        """
                        {"body":{"type":"TRUE_FALSE","statements":[{"id":"s1"},{"id":"s2"}]}}
                        """);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.TRUE_FALSE,
                                        tfSnap,
                                        new AnswerValue.TrueFalse(Map.of()))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.TRUE_FALSE,
                                        tfSnap,
                                        new AnswerValue.TrueFalse(
                                                new java.util.LinkedHashMap<>() {
                                                    {
                                                        put("s1", true);
                                                        put("s2", null);
                                                    }
                                                }))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.TRUE_FALSE,
                                        tfSnap,
                                        new AnswerValue.TrueFalse(Map.of("s9", false)))
                                .valid())
                .isFalse();

        JsonNode matchSnap =
                objectMapper.readTree(
                        """
                        {"body":{"type":"MATCHING","left":[{"id":"l1"},{"id":"l2"}],"right":[{"id":"r1"},{"id":"r2"}]}}
                        """);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MATCHING,
                                        matchSnap,
                                        new AnswerValue.Matching(Map.of()))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MATCHING,
                                        matchSnap,
                                        new AnswerValue.Matching(
                                                new java.util.LinkedHashMap<>() {
                                                    {
                                                        put("l1", "r2");
                                                        put("l2", null);
                                                    }
                                                }))
                                .valid())
                .isTrue();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MATCHING,
                                        matchSnap,
                                        new AnswerValue.Matching(Map.of("l9", "r1")))
                                .valid())
                .isFalse();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MATCHING,
                                        matchSnap,
                                        new AnswerValue.Matching(Map.of("l1", "r9")))
                                .valid())
                .isFalse();
        // Duplicate rightId rejected when reuseRight is omitted or false, allowed when reuseRight=true (R1-12)
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MATCHING,
                                        matchSnap,
                                        new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r1")))
                                .valid())
                .isFalse();
        JsonNode matchNoReuseSnap =
                objectMapper.readTree(
                        """
                        {"body":{"type":"MATCHING","reuseRight":false,"left":[{"id":"l1"},{"id":"l2"}],"right":[{"id":"r1"},{"id":"r2"}]}}
                        """);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MATCHING,
                                        matchNoReuseSnap,
                                        new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r1")))
                                .valid())
                .isFalse();
        JsonNode matchReuseSnap =
                objectMapper.readTree(
                        """
                        {"body":{"type":"MATCHING","reuseRight":true,"left":[{"id":"l1"},{"id":"l2"}],"right":[{"id":"r1"},{"id":"r2"}]}}
                        """);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.MATCHING,
                                        matchReuseSnap,
                                        new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r1")))
                                .valid())
                .isTrue();

        // QUESTION_SET is NEVER answerable (even with null)
        JsonNode setSnap = objectMapper.readTree("""
                {"body":{"type":"QUESTION_SET"}}
                """);
        assertThat(validator.validate(QuestionType.QUESTION_SET, setSnap, null).valid()).isFalse();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.QUESTION_SET,
                                        setSnap,
                                        new AnswerValue.SingleChoice("a"))
                                .valid())
                .isFalse();
    }

    @Test
    void sanitizesEssayHtmlComputesWordCountPerBs6AndEnforces64Kb() throws Exception {
        JsonNode essaySnap = objectMapper.readTree("""
                {"body":{"type":"ESSAY"}}
                """);

        // Null html -> ("", 0)
        ValidationResult nullHtml =
                validator.validate(QuestionType.ESSAY, essaySnap, new AnswerValue.Essay(null, 99));
        assertThat(nullHtml.valid()).isTrue();
        assertThat(nullHtml.normalized()).isEqualTo(new AnswerValue.Essay("", 0));

        // Strips img, figure.image, script, on*, style, external link while keeping safe formatting
        String unsafeHtml =
                "<p onclick=\"alert(1)\" style=\"color:red\">Xin ch\u00e0o\u00A0Vi\u00ea\u0323t Nam "
                        + "<span class=\"math-inline\">x^2 + y^2</span> 2026 + = — "
                        + "<a href=\"https://evil.example\">li\u00ean k\u1ebft</a></p>"
                        + "<script>steal()</script>"
                        + "<img src=\"/api/v1/admin/question-assets/11111111-1111-1111-1111-111111111111\" onerror=\"x()\">"
                        + "<figure class=\"image\" data-width=\"medium\" data-zoom=\"true\"><img src=\"/x\"></figure>";
        ValidationResult cleaned =
                validator.validate(
                        QuestionType.ESSAY, essaySnap, new AnswerValue.Essay(unsafeHtml, 0));
        assertThat(cleaned.valid()).isTrue();
        AnswerValue.Essay normalized = (AnswerValue.Essay) cleaned.normalized();
        assertThat(normalized.html())
                .doesNotContain("<script")
                .doesNotContain("steal()")
                .doesNotContain("<img")
                .doesNotContain("<figure")
                .doesNotContain("onclick")
                .doesNotContain("onerror")
                .doesNotContain("style=")
                .doesNotContain("<a ");
        // Words: "Xin", "chào", "Việt" (NFD->NFC), "Nam", "2026", "liên", "kết" = 7 (math formula & standalone + = — excluded)
        assertThat(normalized.wordCount()).isEqualTo(7);

        // Exact 65,536 bytes passes; 65,537 bytes fails
        String exact64Kb = "x".repeat(65_536);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.ESSAY,
                                        essaySnap,
                                        new AnswerValue.Essay(exact64Kb, 1))
                                .valid())
                .isTrue();
        String over64Kb = "x".repeat(65_537);
        assertThat(
                        validator
                                .validate(
                                        QuestionType.ESSAY,
                                        essaySnap,
                                        new AnswerValue.Essay(over64Kb, 1))
                                .valid())
                .isFalse();

        // Post-sanitization Essay > 64 KB rejected (R1-5/R1-6/R1-7)
        assertThat(
                        validator
                                .validate(
                                        QuestionType.ESSAY,
                                        essaySnap,
                                        new AnswerValue.Essay("&".repeat(65_536), 1))
                                .valid())
                .isFalse();
        assertThat(
                        validator
                                .validate(
                                        QuestionType.ESSAY,
                                        essaySnap,
                                        new AnswerValue.Essay("<p>".repeat(21_845), 1))
                                .valid())
                .isFalse();

        // NUL byte rejected (R1-11)
        assertThat(
                        validator
                                .validate(
                                        QuestionType.ESSAY,
                                        essaySnap,
                                        new AnswerValue.Essay("<p>hi\u0000there</p>", 2))
                                .valid())
                .isFalse();

        // Inline math span replaced with space so adjacent words do not merge (R1-19)
        ValidationResult mathAdjacent =
                validator.validate(
                        QuestionType.ESSAY,
                        essaySnap,
                        new AnswerValue.Essay(
                                "<p>giá trị<span class=\"math-inline\">x</span>bằng 5</p>", 0));
        assertThat(mathAdjacent.valid()).isTrue();
        assertThat(((AnswerValue.Essay) mathAdjacent.normalized()).wordCount()).isEqualTo(4);
    }
}
