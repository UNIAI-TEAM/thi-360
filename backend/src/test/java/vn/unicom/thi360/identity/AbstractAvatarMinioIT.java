package vn.unicom.thi360.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.PasswordResetTestSupport.Account;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.TokenPepperProperties;

/**
 * Nền chung của {@code MeProfileIT} và {@code AvatarMinioIT}: MinIO thật (theo mẫu {@code ImportBatchMinioIT}) với
 * hai bucket tách biệt, bucket riêng tư dùng chung và bucket ảnh đại diện; tài khoản dựng và dọn theo
 * {@link PasswordResetTestSupport}. Đặt {@code @DirtiesContext} để thuộc tính MinIO không rò sang lớp test khác.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractAvatarMinioIT extends AbstractKeycloakIntegrationTest {

    static final String PRIVATE_BUCKET = "thi360-it-private";
    static final String AVATAR_BUCKET = "thi360-it-avatars";
    private static final String MINIO_USER = "thi360it";
    private static final String MINIO_PASS = "thi360it-secret-key";

    static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("cgr.dev/chainguard/minio:latest"))
                    .withCommand("server", "/data")
                    .withEnv("MINIO_ROOT_USER", MINIO_USER)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASS)
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000)
                            .withStartupTimeout(Duration.ofSeconds(60)));

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void registerMinioProperties(DynamicPropertyRegistry registry) {
        registry.add("thi360.s3.mode", () -> "s3");
        registry.add("thi360.s3.endpoint", AbstractAvatarMinioIT::endpoint);
        registry.add("thi360.s3.bucket", () -> PRIVATE_BUCKET);
        registry.add("thi360.s3.access-key", () -> MINIO_USER);
        registry.add("thi360.s3.secret-key", () -> MINIO_PASS);
        registry.add("thi360.s3.auto-create-bucket", () -> "true");
        registry.add("thi360.avatar.bucket", () -> AVATAR_BUCKET);
    }

    static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected StringRedisTemplate redis;
    @Autowired
    protected KeycloakIdentityClient keycloakClient;
    @Autowired
    protected UserAccountRepository userAccountRepository;
    @Autowired
    protected TokenPepperProperties pepper;

    protected PasswordResetTestSupport support;
    private S3Client s3;

    @BeforeEach
    void setUpSupport() {
        support = new PasswordResetTestSupport(keycloakClient, userAccountRepository, jdbc, redis, pepper.pepper());
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .forcePathStyle(true)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(MINIO_USER, MINIO_PASS)))
                .build();
    }

    @AfterEach
    void cleanupSupport() {
        support.cleanup();
        s3.close();
    }

    /** Phiên của chính {@code account} (người dùng mô phỏng, realm APP). */
    protected RequestPostProcessor sessionOf(Account account) {
        return asUser(account.id(), account.username());
    }

    protected MockHttpServletRequestBuilder putAvatar(byte[] content, String fileName, String contentType) {
        return multipart(HttpMethod.PUT, "/api/v1/me/avatar")
                .file(new MockMultipartFile("file", fileName, contentType, content));
    }

    protected boolean objectExists(String bucket, String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    /** Khóa mọi đối tượng hiện có trong bucket (rỗng khi bucket chưa tồn tại). */
    protected List<String> objectKeys(String bucket) {
        try {
            return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build())
                    .contents().stream().map(S3Object::key).toList();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return List.of();
            }
            throw ex;
        }
    }

    protected static String avatarKey(String assetId) {
        UUID.fromString(assetId);
        return assetId + ".png";
    }
}
