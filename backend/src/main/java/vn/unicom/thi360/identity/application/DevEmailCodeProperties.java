package vn.unicom.thi360.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mã OTP cố định cho dev (TM 5.3, plan A6): {@code THI360_DEV_EMAIL_CODE}. Khóa chỉ được nối trong khối
 * {@code on-profile: dev} của {@code application.yml}; {@link DevEmailCodeGuard} chặn khởi động khi biến này
 * xuất hiện ở profile khác.
 */
@ConfigurationProperties(prefix = "thi360.identity.email-code")
public record DevEmailCodeProperties(String devCode) {
}
