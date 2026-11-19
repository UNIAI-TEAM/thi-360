package vn.unicom.thi360.identity.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.shared.storage.S3ObjectStorage;
import vn.unicom.thi360.shared.storage.S3Properties;

/**
 * Kho đối tượng của ảnh đại diện: cùng endpoint, khóa và region với {@code thi360.s3}, bucket riêng
 * ({@link AvatarStorageProperties#bucket()}). {@code defaultCandidate = false} để năm nơi đang tiêm
 * {@link ObjectStorage} theo kiểu (bucket riêng tư dùng chung) không bị nhập nhằng; chỉ {@code AvatarService} lấy
 * bean này theo tên.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "thi360.s3.mode", havingValue = "s3", matchIfMissing = true)
class AvatarStorageConfiguration {

    static final String BEAN_NAME = "avatarObjectStorage";

    @Bean(value = BEAN_NAME, defaultCandidate = false)
    ObjectStorage avatarObjectStorage(S3Properties s3, AvatarStorageProperties avatar) {
        return new S3ObjectStorage(new S3Properties(
                s3.mode(),
                s3.endpoint(),
                avatar.bucket(),
                s3.accessKey(),
                s3.secretKey(),
                s3.region(),
                s3.autoCreateBucket()));
    }
}
