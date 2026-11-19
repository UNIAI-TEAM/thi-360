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
            List<String> exemptIps
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
