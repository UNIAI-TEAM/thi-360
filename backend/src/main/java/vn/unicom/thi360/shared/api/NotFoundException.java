package vn.unicom.thi360.shared.api;

public class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException(String message) {
        this("NOT_FOUND", message);
    }

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "NOT_FOUND" : code;
    }

    public String getCode() {
        return code;
    }
}
