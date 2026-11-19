package vn.unicom.thi360.identity.domain;

/** Mục đích của mã OTP email (TM 5.3): xác minh email hiện tại hoặc xác nhận email mới. */
public enum EmailCodePurpose {
    VERIFY,
    CHANGE
}
