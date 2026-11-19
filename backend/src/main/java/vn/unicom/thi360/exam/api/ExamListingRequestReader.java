package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Reads the body of {@code PUT /exams/{id}/listing} by JSON token type (TM 6.2, AC-2). Jackson's scalar coercion would
 * turn {@code 99.9} into a whole number or {@code "5"} into 5 before any range check, silently changing a price or the
 * attempts a buyer receives. Here a wrong type is a field error {@code WRONG_TYPE} in a 422 {@code EXAM_LISTING_INVALID},
 * with every wrong field named; a number that does not fit is {@code OUT_OF_RANGE}. The shape of the request is still
 * {@link UpdateExamListingRequest}, which keeps the OpenAPI contract.
 */
@Component
public class ExamListingRequestReader {
    private static final String INVALID = ExamErrorCodes.LISTING_INVALID;
    private final AppMessages messages;

    public ExamListingRequestReader(AppMessages messages) {
        this.messages = messages;
    }

    public UpdateExamListingRequest read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ValidationException(List.of(new FieldError("body", "WRONG_TYPE", messages.get("EXAM_LISTING_FIELD_WRONG_TYPE", "object"))));
        }
        if (body.path("expectedRevision").isMissingNode() || body.path("expectedRevision").isNull()) {
            throw new ValidationException(List.of(new FieldError("expectedRevision", "REQUIRED", messages.get("EXAM_LISTING_FIELD_REQUIRED"))));
        }
        List<FieldError> errors = new ArrayList<>();
        String summary = text(body, "summary", errors);
        String objectives = text(body, "objectives", errors);
        String audience = text(body, "audience", errors);
        Long price = integer(body, "listPriceVnd", Long.MIN_VALUE, Long.MAX_VALUE, 0, Long.MAX_VALUE, errors);
        Long attempts = integer(body, "attemptsGranted", Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 100, errors);
        Boolean aiExplanation = bool(body, "benefitAiExplanation", errors);
        Boolean strengthWeakness = bool(body, "benefitStrengthWeakness", errors);
        Boolean practice = bool(body, "benefitPractice", errors);
        String benefitNote = text(body, "benefitNote", errors);
        String resultView = text(body, "resultView", errors);
        String description = text(body, "description", errors);
        UUID cover = uuid(body, "coverAssetId", errors);
        Long revision = integer(body, "expectedRevision", Long.MIN_VALUE, Long.MAX_VALUE, 0, Long.MAX_VALUE, errors);
        if (!errors.isEmpty()) {
            throw new BusinessRuleException(INVALID, messages.get(INVALID), errors);
        }
        return new UpdateExamListingRequest(summary, objectives, audience, price,
                attempts == null ? null : Math.toIntExact(attempts), aiExplanation, strengthWeakness, practice,
                benefitNote, resultView, description, cover, revision);
    }

    private String text(JsonNode body, String field, List<FieldError> errors) {
        JsonNode value = body.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) {
            wrongType(field, "string", errors);
            return null;
        }
        return value.asText();
    }

    private Boolean bool(JsonNode body, String field, List<FieldError> errors) {
        JsonNode value = body.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isBoolean()) {
            wrongType(field, "boolean", errors);
            return null;
        }
        return value.booleanValue();
    }

    /**
     * A JSON integer token only: {@code 5.0}, {@code 1e2} and {@code "5"} are wrong types. A value that does not fit
     * the Java type ({@code min}..{@code max}) is out of range, reported with the business range shown to the user.
     */
    private Long integer(JsonNode body, String field, long min, long max, long shownMin, long shownMax, List<FieldError> errors) {
        JsonNode value = body.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isIntegralNumber()) {
            wrongType(field, "integer", errors);
            return null;
        }
        if (!value.canConvertToLong() || value.longValue() < min || value.longValue() > max) {
            errors.add(new FieldError(field, "OUT_OF_RANGE", messages.get("EXAM_LISTING_FIELD_OUT_OF_RANGE", shownMin, shownMax)));
            return null;
        }
        return value.longValue();
    }

    private UUID uuid(JsonNode body, String field, List<FieldError> errors) {
        JsonNode value = body.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.isTextual()) {
            try {
                return UUID.fromString(value.asText());
            } catch (IllegalArgumentException ignored) {
                // falls through to the field error
            }
        }
        wrongType(field, "UUID", errors);
        return null;
    }

    private void wrongType(String field, String expected, List<FieldError> errors) {
        errors.add(new FieldError(field, "WRONG_TYPE", messages.get("EXAM_LISTING_FIELD_WRONG_TYPE", expected)));
    }
}
