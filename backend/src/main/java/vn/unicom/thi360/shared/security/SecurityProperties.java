package vn.unicom.thi360.shared.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "thi360.security")
public record SecurityProperties(
        @DefaultValue("http://localhost:3001") List<String> adminOrigins,
        @DefaultValue({"127.0.0.1", "::1", "172.16.0.0/12"}) List<String> trustedProxies,
        @DefaultValue RateLimitProperties rateLimit,
        @DefaultValue LoginThrottleProperties loginThrottle,
        @DefaultValue SessionProperties session
) {

    public record RateLimitProperties(
            @DefaultValue("20") int loginPerIpLimit,
            @DefaultValue("PT1M") Duration loginPerIpWindow,
            @DefaultValue("10") int uploadPerIpLimit,
            @DefaultValue("PT1M") Duration uploadPerIpWindow,
            @DefaultValue("120") int publicPerIpLimit,
            @DefaultValue("PT1M") Duration publicPerIpWindow,
            List<String> exemptIps,
            // E19 (plan ID-1): đăng ký tối đa 30 lần mỗi giờ và 100 lần mỗi ngày cho mỗi IP (ngày = 24 giờ trượt).
            @DefaultValue("30") int registerPerIpHourLimit,
            @DefaultValue("PT1H") Duration registerPerIpHourWindow,
            @DefaultValue("100") int registerPerIpDayLimit,
            @DefaultValue("PT24H") Duration registerPerIpDayWindow
    ) {

        public RateLimitProperties {
            // Cửa sổ không dương làm Bucket4j từ chối lúc dùng, mà bộ lọc nuốt lỗi và cho qua: GET công khai
            // sẽ không bao giờ bị giới hạn. Báo lỗi ngay lúc nạp cấu hình để triển khai sai không khởi động.
            if (publicPerIpWindow == null || publicPerIpWindow.isZero() || publicPerIpWindow.isNegative()) {
                throw new IllegalArgumentException(
                        "thi360.security.rate-limit.public-per-ip-window must be a positive duration");
            }
            // Chỉ miễn chính sách của GET /api/v1/public/** (TM 10.2); mục sai định dạng thì không khởi động.
            exemptIps = exemptIps == null ? List.of() : List.copyOf(exemptIps);
            exemptIps.forEach(ExemptIpMatcher::requireIpOrCidr);
            // E19: mỗi cửa sổ phải dương, nếu không bộ lọc nuốt lỗi Bucket4j và cho đăng ký không giới hạn.
            requirePositive("register-per-ip-hour-limit", registerPerIpHourLimit);
            requirePositive("register-per-ip-day-limit", registerPerIpDayLimit);
            requirePositiveDuration("register-per-ip-hour-window", registerPerIpHourWindow);
            requirePositiveDuration("register-per-ip-day-window", registerPerIpDayWindow);
        }

        private static void requirePositive(String key, int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("thi360.security.rate-limit." + key + " must be positive");
            }
        }

        private static void requirePositiveDuration(String key, Duration value) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("thi360.security.rate-limit." + key + " must be a positive duration");
            }
        }
    }

    public record LoginThrottleProperties(
            @DefaultValue("5") int maxFailures,
            @DefaultValue("PT15M") Duration failureWindow,
            @DefaultValue("PT15M") Duration lockDuration
    ) {
    }

    public record SessionProperties(
            @DefaultValue("5") int maxPerUser,
            @DefaultValue("P30D") Duration ttl
    ) {
    }
}
