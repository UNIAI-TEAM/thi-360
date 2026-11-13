package vn.unicom.thi360.shared.security;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "thi360.ctx")
public record CtxProperties(
        @NotBlank String secret,
        @DefaultValue("PT5M") Duration ttl
) {
}
