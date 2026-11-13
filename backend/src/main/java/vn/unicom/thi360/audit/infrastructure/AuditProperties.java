package vn.unicom.thi360.audit.infrastructure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "thi360.audit")
public record AuditProperties(
        @Min(0) int archiveAfterQuarters
) {
}
