package vn.unicom.thi360.identity.domain;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "thi360.identity")
public record IdentityProperties(
        @DefaultValue({"admin", "administrator", "root", "system", "support", "platform", "thi360", "null", "undefined"})
        List<String> reservedUsernames
) {
}
