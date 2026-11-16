package vn.unicom.thi360.shared.api;

import java.util.List;

/**
 * Lỗi quy tắc nghiệp vụ: request hợp lệ về kiểu dữ liệu nhưng vi phạm luật của hệ thống.
 * Trả HTTP 422 với {@link ApiError} gồm mã lỗi và {@link FieldError} chi tiết từng trường.
 * Khác với lỗi kiểu dữ liệu của bean validation (400 {@code VALIDATION_FAILED}).
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final List<FieldError> fieldErrors;

    public BusinessRuleException(String code) {
        this(code, null, List.of());
    }

    public BusinessRuleException(String code, String message) {
        this(code, message, List.of());
    }

    public BusinessRuleException(String code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.code = code == null || code.isBlank() ? "BUSINESS_RULE_VIOLATED" : code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
