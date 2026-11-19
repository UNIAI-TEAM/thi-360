package vn.unicom.thi360.identity.application;

/** Mã OTP sai nhưng còn được nhập lại: 422 {@code EMAIL_CODE_INVALID} kèm {@code remainingAttempts}. */
public class EmailCodeInvalidException extends RuntimeException {

    private final int remainingAttempts;

    public EmailCodeInvalidException(int remainingAttempts) {
        super("Email code is invalid");
        this.remainingAttempts = remainingAttempts;
    }

    public int getRemainingAttempts() {
        return remainingAttempts;
    }
}
