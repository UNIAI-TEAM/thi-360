package vn.unicom.thi360.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình quên và đặt lại mật khẩu (TM 5.4, E16). Ngưỡng theo IP nằm ở
 * {@code thi360.security.rate-limit.forgot-per-ip-*} và {@code reset-per-ip-*} (bộ lọc), không ở đây.
 *
 * @param tokenTtl            hạn của mã đặt lại (E16: 1 giờ)
 * @param accountCooldown     khoảng tối thiểu giữa hai yêu cầu của một tài khoản (E16: 60 giây)
 * @param accountDailyLimit   số yêu cầu tối đa của một tài khoản trong {@code accountDailyWindow} (E16: 5)
 * @param accountDailyWindow  "ngày" của E16, tính cửa sổ trượt (quyết định tạm: 24 giờ trượt)
 * @param executorPoolSize    số luồng của bộ thực thi xử lý {@code forgot} sau khi đã trả phản hồi
 * @param executorQueueCapacity độ dài hàng đợi; đầy thì yêu cầu bị bỏ lặng lẽ, vẫn trả 202
 * @param writeReconcileGrace  thời gian chờ sau dấu ghi trước trước khi job đối soát coi lần ghi mật khẩu là chưa
 *                            xác định (lớn hơn thời gian chờ tối đa của lần gọi Keycloak)
 */
@ConfigurationProperties(prefix = "thi360.identity.password-reset")
public record PasswordResetProperties(
        @DefaultValue("PT1H") Duration tokenTtl,
        @DefaultValue("PT1M") Duration accountCooldown,
        @DefaultValue("5") int accountDailyLimit,
        @DefaultValue("PT24H") Duration accountDailyWindow,
        @DefaultValue("2") int executorPoolSize,
        @DefaultValue("100") int executorQueueCapacity,
        @DefaultValue("PT5M") Duration writeReconcileGrace) {

    public PasswordResetProperties {
        requirePositive("token-ttl", tokenTtl);
        requirePositive("account-cooldown", accountCooldown);
        requirePositive("account-daily-window", accountDailyWindow);
        requirePositive("write-reconcile-grace", writeReconcileGrace);
        if (accountDailyLimit <= 0) {
            throw new IllegalArgumentException(
                    "thi360.identity.password-reset.account-daily-limit must be positive");
        }
        if (executorPoolSize <= 0) {
            throw new IllegalArgumentException(
                    "thi360.identity.password-reset.executor-pool-size must be positive");
        }
        if (executorQueueCapacity <= 0) {
            throw new IllegalArgumentException(
                    "thi360.identity.password-reset.executor-queue-capacity must be positive");
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "thi360.identity.password-reset." + name + " must be a positive duration");
        }
    }
}
