package vn.unicom.thi360.shared.api;

public class ExternalServiceException extends RuntimeException {

    private final String code;

    public ExternalServiceException(String code) {
        this(code, null, null);
    }

    public ExternalServiceException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null || code.isBlank() ? "EXTERNAL_SERVICE_ERROR" : code;
    }

    public String getCode() {
        return code;
    }
}
