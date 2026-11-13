package vn.unicom.thi360.shared.api;

public class VersionConflictException extends RuntimeException {

    private final String code;

    public VersionConflictException() {
        this("VERSION_CONFLICT", null);
    }

    public VersionConflictException(String message) {
        this("VERSION_CONFLICT", message);
    }

    public VersionConflictException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "VERSION_CONFLICT" : code;
    }

    public String getCode() {
        return code;
    }
}
