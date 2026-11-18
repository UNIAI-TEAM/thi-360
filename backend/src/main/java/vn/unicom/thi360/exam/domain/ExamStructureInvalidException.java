package vn.unicom.thi360.exam.domain;

import java.util.List;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.FieldError;

/**
 * {@code EXAM_STRUCTURE_INVALID} with message keys instead of text: the domain has no locale, so each
 * {@link Detail} names a key in the {@code messages*} bundles and its arguments. The exam API advice resolves them in
 * the request locale. Without that advice the code alone is resolved by the shared handler and a field message
 * is its bundle key. {@link #reason()} is an English note for logs and tests, never sent to the client.
 */
public final class ExamStructureInvalidException extends BusinessRuleException {
    public static final String CODE = "EXAM_STRUCTURE_INVALID";

    private final String reason;
    private final List<Detail> details;

    public ExamStructureInvalidException(String reason, List<Detail> details) {
        super(CODE, null, details.stream().map(detail -> new FieldError(detail.field(), detail.code(),
                detail.messageKey())).toList());
        this.reason = reason;
        this.details = List.copyOf(details);
    }

    public String reason() {
        return reason;
    }

    public List<Detail> details() {
        return details;
    }

    /** One field error: {@code code} is the stable machine code, {@code messageKey} the bundle key of its text. */
    public record Detail(String field, String code, String messageKey, List<String> args) {
        public Detail {
            args = args == null ? List.of() : List.copyOf(args);
        }
    }
}
