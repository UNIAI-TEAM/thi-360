package vn.unicom.thi360.shared.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
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

    public static final int MIN_SECRET_BYTES = 32;

    @AssertTrue(message = "thi360.ctx.secret must be at least 32 UTF-8 bytes")
    public boolean isSecretLengthValid() {
        return secret != null
                && !secret.isBlank()
                && secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
    }
}
