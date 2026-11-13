package vn.unicom.thi360.shared.api;

public class UnauthenticatedException extends RuntimeException {

    private final String code;

    public UnauthenticatedException() {
        this("UNAUTHENTICATED", null);
    }

    public UnauthenticatedException(String message) {
        this("UNAUTHENTICATED", message);
    }

    public UnauthenticatedException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "UNAUTHENTICATED" : code;
    }

    public String getCode() {
        return code;
    }
}
