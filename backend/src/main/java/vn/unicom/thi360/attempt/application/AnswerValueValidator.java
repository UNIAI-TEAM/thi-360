package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Validates and normalizes student {@link AnswerValue} payloads against question type and serving
 * snapshot structure, enforcing the 64 KB size limit, Essay HTML text-only sanitization, and BS §6
 * word counting (LT §4, §7.1, BS §6, AC-2, AC-3, AC-7, AC-8).
 */
@Component
public class AnswerValueValidator {

    public static final int MAX_ANSWER_BYTES = 65_536;

    private final HtmlContentSanitizer htmlContentSanitizer;
    private final ObjectMapper objectMapper;

    public AnswerValueValidator(
            HtmlContentSanitizer htmlContentSanitizer, ObjectMapper objectMapper) {
        this.htmlContentSanitizer = Objects.requireNonNull(htmlContentSanitizer);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public record ValidationResult(boolean valid, AnswerValue normalized) {
        public static ValidationResult valid(AnswerValue normalized) {
            return new ValidationResult(true, normalized);
        }

        public static ValidationResult invalid() {
            return new ValidationResult(false, null);
        }
    }

    public ValidationResult validate(
            QuestionType questionType, JsonNode snapshot, AnswerValue answer) {
        if (questionType == null || questionType == QuestionType.QUESTION_SET) {
            return ValidationResult.invalid();
        }
        if (answer == null) {
            return ValidationResult.valid(null);
        }

        JsonNode body = snapshot == null ? null : snapshot.path("body");
        return switch (questionType) {
            case SINGLE_CHOICE -> validateSingleChoice(body, answer);
            case MULTIPLE_CHOICE -> validateMultipleChoice(body, answer);
            case SHORT_ANSWER -> validateShortAnswer(answer);
            case CLOZE -> validateCloze(body, answer);
            case TRUE_FALSE -> validateTrueFalse(body, answer);
            case MATCHING -> validateMatching(body, answer);
            case ESSAY -> validateEssay(answer);
            case QUESTION_SET -> ValidationResult.invalid();
        };
    }

    private ValidationResult validateSingleChoice(JsonNode body, AnswerValue answer) {
        if (!(answer instanceof AnswerValue.SingleChoice sc) || exceedsJsonByteLimit(sc)) {
            return ValidationResult.invalid();
        }
        if (sc.optionId() == null) {
            return ValidationResult.valid(sc);
        }
        Set<String> validOptionIds = collectIds(body, "options");
        return validOptionIds.contains(sc.optionId())
                ? ValidationResult.valid(sc)
                : ValidationResult.invalid();
    }

    private ValidationResult validateMultipleChoice(JsonNode body, AnswerValue answer) {
        if (!(answer instanceof AnswerValue.MultipleChoice mc) || exceedsJsonByteLimit(mc)) {
            return ValidationResult.invalid();
        }
        if (mc.optionIds().isEmpty()) {
            return ValidationResult.valid(mc);
        }
        Set<String> validOptionIds = collectIds(body, "options");
        Set<String> seen = new HashSet<>();
        for (String optionId : mc.optionIds()) {
            if (optionId == null || !validOptionIds.contains(optionId) || !seen.add(optionId)) {
                return ValidationResult.invalid();
            }
        }
        return ValidationResult.valid(mc);
    }

    private ValidationResult validateShortAnswer(AnswerValue answer) {
        if (!(answer instanceof AnswerValue.ShortAnswer sa)) {
            return ValidationResult.invalid();
        }
        String text = sa.text();
        if (text != null
                && (text.indexOf('\u0000') >= 0
                        || text.getBytes(StandardCharsets.UTF_8).length > MAX_ANSWER_BYTES)) {
            return ValidationResult.invalid();
        }
        return ValidationResult.valid(sa);
    }

    private ValidationResult validateCloze(JsonNode body, AnswerValue answer) {
        if (!(answer instanceof AnswerValue.Cloze cloze) || exceedsJsonByteLimit(cloze)) {
            return ValidationResult.invalid();
        }
        if (cloze.blanks().isEmpty()) {
            return ValidationResult.valid(cloze);
        }
        Set<String> validBlankIds = collectIds(body, "blanks");
        for (Map.Entry<String, String> entry : cloze.blanks().entrySet()) {
            String value = entry.getValue();
            if (entry.getKey() == null
                    || !validBlankIds.contains(entry.getKey())
                    || (value != null && value.indexOf('\u0000') >= 0)) {
                return ValidationResult.invalid();
            }
        }
        return ValidationResult.valid(cloze);
    }

    private ValidationResult validateTrueFalse(JsonNode body, AnswerValue answer) {
        if (!(answer instanceof AnswerValue.TrueFalse tf) || exceedsJsonByteLimit(tf)) {
            return ValidationResult.invalid();
        }
        if (tf.statements().isEmpty()) {
            return ValidationResult.valid(tf);
        }
        Set<String> validStatementIds = collectIds(body, "statements");
        for (Map.Entry<String, Boolean> entry : tf.statements().entrySet()) {
            if (entry.getKey() == null || !validStatementIds.contains(entry.getKey())) {
                return ValidationResult.invalid();
            }
        }
        return ValidationResult.valid(tf);
    }

    private ValidationResult validateMatching(JsonNode body, AnswerValue answer) {
        if (!(answer instanceof AnswerValue.Matching matching) || exceedsJsonByteLimit(matching)) {
            return ValidationResult.invalid();
        }
        if (matching.pairs().isEmpty()) {
            return ValidationResult.valid(matching);
        }
        boolean reuseRight = body != null && body.path("reuseRight").asBoolean(false);
        Set<String> validLeftIds = collectIds(body, "left");
        Set<String> validRightIds = collectIds(body, "right");
        Set<String> usedRight = new HashSet<>();
        for (Map.Entry<String, String> entry : matching.pairs().entrySet()) {
            String leftId = entry.getKey();
            String rightId = entry.getValue();
            if (leftId == null || !validLeftIds.contains(leftId)) {
                return ValidationResult.invalid();
            }
            if (rightId != null) {
                if (!validRightIds.contains(rightId)) {
                    return ValidationResult.invalid();
                }
                if (!reuseRight && !usedRight.add(rightId)) {
                    return ValidationResult.invalid();
                }
            }
        }
        return ValidationResult.valid(matching);
    }

    private ValidationResult validateEssay(AnswerValue answer) {
        if (!(answer instanceof AnswerValue.Essay essay)) {
            return ValidationResult.invalid();
        }
        String rawHtml = essay.html();
        if (rawHtml == null) {
            return ValidationResult.valid(new AnswerValue.Essay("", 0));
        }
        if (rawHtml.indexOf('\u0000') >= 0
                || rawHtml.getBytes(StandardCharsets.UTF_8).length > MAX_ANSWER_BYTES) {
            return ValidationResult.invalid();
        }
        HtmlContentSanitizer.Result sanitized =
                htmlContentSanitizer.sanitize(rawHtml, HtmlContentSanitizer.Options.textOnly());
        String cleanHtml = sanitized.html() == null ? "" : sanitized.html();
        if (cleanHtml.getBytes(StandardCharsets.UTF_8).length > MAX_ANSWER_BYTES) {
            return ValidationResult.invalid();
        }
        int wordCount = countWords(cleanHtml);
        return ValidationResult.valid(new AnswerValue.Essay(cleanHtml, wordCount));
    }

    /** Counts words per BS §6 after stripping math formula spans, normalizing NFC and Unicode whitespace. */
    static int countWords(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return 0;
        }
        Document doc = Jsoup.parseBodyFragment(sanitizedHtml);
        doc.select("span.math-inline, span.math-block")
                .forEach(el -> el.replaceWith(new TextNode(" ")));
        String plain = doc.body().text();
        if (plain.isEmpty()) {
            return 0;
        }
        String nfc = Normalizer.normalize(plain, Normalizer.Form.NFC);
        StringBuilder normalized = new StringBuilder(nfc.length());
        nfc.codePoints()
                .forEach(
                        cp -> {
                            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                                normalized.append(' ');
                            } else {
                                normalized.appendCodePoint(cp);
                            }
                        });
        String trimmed = normalized.toString().trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String token : trimmed.split(" +")) {
            if (token.codePoints().anyMatch(Character::isLetterOrDigit)) {
                count++;
            }
        }
        return count;
    }

    private boolean exceedsJsonByteLimit(AnswerValue answer) {
        try {
            return objectMapper.writeValueAsBytes(answer).length > MAX_ANSWER_BYTES;
        } catch (JsonProcessingException ex) {
            return true;
        }
    }

    private static Set<String> collectIds(JsonNode body, String arrayField) {
        if (body == null || body.isMissingNode()) {
            return Set.of();
        }
        JsonNode array = body.path(arrayField);
        if (!array.isArray()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode entry : array) {
            if (entry.hasNonNull("id")) {
                ids.add(entry.get("id").asText());
            }
        }
        return ids;
    }
}
