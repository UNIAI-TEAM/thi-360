package vn.unicom.thi360.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "thi360.s3")
public record S3Properties(
        @DefaultValue("s3") String mode,
        String endpoint,
        String bucket,
        String accessKey,
        String secretKey,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("false") boolean autoCreateBucket
) {
}
