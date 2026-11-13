package vn.unicom.thi360.shared.api;

public class RateLimitedException extends RuntimeException {

    private final String code;

    public RateLimitedException() {
        this("RATE_LIMITED", null);
    }

    public RateLimitedException(String code) {
        this(code, null);
    }

    public RateLimitedException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? "RATE_LIMITED" : code;
    }

    public String getCode() {
        return code;
    }
}
