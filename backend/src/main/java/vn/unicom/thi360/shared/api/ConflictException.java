package vn.unicom.thi360.shared.api;

import java.util.List;

public class ConflictException extends RuntimeException {

    private final String code;
    private final List<FieldError> fieldErrors;

    public ConflictException(String code) {
        this(code, null, List.of());
    }

    public ConflictException(String code, String message) {
        this(code, message, List.of());
    }

    public ConflictException(String code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.code = code == null || code.isBlank() ? "CONFLICT" : code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
