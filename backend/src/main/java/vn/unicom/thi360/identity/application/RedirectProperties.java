package vn.unicom.thi360.identity.application;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "thi360")
public record RedirectProperties(
        @NotBlank @DefaultValue("http://localhost:3000") String webUrl,
        @NotBlank @DefaultValue("http://localhost:3001") String adminUrl,
        @DefaultValue List<String> redirectAllowedOrigins
) {
}
