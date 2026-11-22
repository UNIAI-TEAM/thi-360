package vn.unicom.thi360.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Bucket ảnh đại diện (TM E20), riêng với bucket riêng tư {@code thi360.s3.bucket}. */
@ConfigurationProperties(prefix = "thi360.avatar")
public record AvatarStorageProperties(@DefaultValue("thi360-avatars") String bucket) {
}
