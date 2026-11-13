package vn.unicom.thi360.shared.api;

public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code) {
        this(code, null);
    }

    public ConflictException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "CONFLICT" : code;
    }

    public String getCode() {
        return code;
    }
}
