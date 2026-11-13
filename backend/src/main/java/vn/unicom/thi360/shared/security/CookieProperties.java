package vn.unicom.thi360.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "thi360.cookie")
public record CookieProperties(
        @DefaultValue("") String domain,
        @DefaultValue("true") boolean secure
) {
}
