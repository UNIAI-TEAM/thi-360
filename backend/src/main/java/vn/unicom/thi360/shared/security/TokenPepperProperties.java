package vn.unicom.thi360.shared.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Server-side pepper for the HMAC of one-time codes (email OTP, password-reset codes; plan B6).
 * Read from {@code THI360_TOKEN_PEPPER}. Only the {@code dev}, {@code test} and {@code e2e}
 * profiles carry a public {@code dev-only-...} value; every other profile fails to start
 * without the variable.
 */
@Validated
@ConfigurationProperties(prefix = "thi360.token")
public record TokenPepperProperties(
        @NotBlank(message = "thi360.token.pepper (THI360_TOKEN_PEPPER) must not be blank") String pepper
) {
}
