package vn.unicom.thi360.shared.api;

public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException() {
        this("FORBIDDEN", null);
    }

    public ForbiddenException(String message) {
        this("FORBIDDEN", message);
    }

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "FORBIDDEN" : code;
    }

    public String getCode() {
        return code;
    }
}
