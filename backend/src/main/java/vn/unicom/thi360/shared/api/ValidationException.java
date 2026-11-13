package vn.unicom.thi360.shared.api;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final String code;
    private final List<FieldError> fieldErrors;

    public ValidationException(String message) {
        this("VALIDATION_FAILED", message, List.of());
    }

    public ValidationException(List<FieldError> fieldErrors) {
        this("VALIDATION_FAILED", null, fieldErrors);
    }

    public ValidationException(String message, List<FieldError> fieldErrors) {
        this("VALIDATION_FAILED", message, fieldErrors);
    }

    public ValidationException(String code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.code = code == null || code.isBlank() ? "VALIDATION_FAILED" : code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
