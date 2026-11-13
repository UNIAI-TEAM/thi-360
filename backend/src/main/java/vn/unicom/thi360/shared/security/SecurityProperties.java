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
            @DefaultValue("PT1M") Duration loginPerIpWindow
    ) {
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
