package vn.unicom.thi360.identity.application;

/**
 * Ảnh đại diện bị từ chối (TM E20). {@code AvatarExceptionHandler} đổi thành 413 {@code AVATAR_TOO_LARGE} hoặc
 * 422 {@code AVATAR_INVALID_IMAGE} / {@code AVATAR_DIMENSIONS_EXCEEDED}: {@code GlobalExceptionHandler} không biểu
 * diễn được 413 theo mã của riêng endpoint này (plan B2).
 */
public class AvatarRejectedException extends RuntimeException {

    public enum Reason {
        TOO_LARGE("AVATAR_TOO_LARGE"),
        INVALID_IMAGE("AVATAR_INVALID_IMAGE"),
        DIMENSIONS_EXCEEDED("AVATAR_DIMENSIONS_EXCEEDED");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Reason reason;

    public AvatarRejectedException(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
