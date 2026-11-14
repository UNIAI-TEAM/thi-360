package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.application.ImportBatchProcessorJob;
import vn.unicom.thi360.identity.application.ImportBatchService;
import vn.unicom.thi360.identity.domain.ImportBatchStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.shared.storage.S3ObjectStorage;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportBatchMinioIT extends AbstractKeycloakIntegrationTest {

    private static final UUID SYSTEM_ADMIN_ROLE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final String MINIO_USER = "thi360it";
    private static final String MINIO_PASS = "thi360it-secret-key";
    private static final String MINIO_BUCKET = "thi360-it-private";

    private static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("cgr.dev/chainguard/minio:latest"))
                    .withCommand("server", "/data")
                    .withEnv("MINIO_ROOT_USER", MINIO_USER)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASS)
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(Duration.ofSeconds(60)));

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void registerMinioProperties(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        registry.add("thi360.s3.mode", () -> "s3");
        registry.add("thi360.s3.endpoint", () -> endpoint);
        registry.add("thi360.s3.bucket", () -> MINIO_BUCKET);
        registry.add("thi360.s3.access-key", () -> MINIO_USER);
        registry.add("thi360.s3.secret-key", () -> MINIO_PASS);
        registry.add("thi360.s3.auto-create-bucket", () -> "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectStorage objectStorage;

    @Autowired
    private ImportBatchProcessorJob importBatchProcessorJob;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID adminUserId;
    private UUID batchId;
    private UUID createdStudentId;
    private String idempotencyKey;

    @BeforeEach
    void clearRateLimitBefore() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + "127.0.0.1",
                RateLimitFilter.UPLOAD_KEY_PREFIX + "127.0.0.1"));
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete(List.of(
                RateLimitFilter.KEY_PREFIX + "127.0.0.1",
                RateLimitFilter.UPLOAD_KEY_PREFIX + "127.0.0.1"));
        if (batchId != null) {
            jdbcTemplate.update("DELETE FROM identity.import_row WHERE batch_id = ?", batchId);
            jdbcTemplate.update("DELETE FROM identity.import_batch WHERE id = ?", batchId);
        }
        if (idempotencyKey != null) {
            jdbcTemplate.update("DELETE FROM shared.idempotency_key WHERE key = ?", idempotencyKey);
        }
        if (createdStudentId != null) {
            try {
                keycloakClient.deleteUser(createdStudentId);
            } catch (Exception ignored) {
            }
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", createdStudentId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", createdStudentId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", createdStudentId);
        }
        if (adminUserId != null) {
            try {
                keycloakClient.deleteUser(adminUserId);
            } catch (Exception ignored) {
            }
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", adminUserId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", adminUserId);
        }
    }

    @Test
    void minioS3EncryptedLifecycle_andRealTomcatOver5MiBReturnsImportFileTooLarge() throws Exception {
        assertThat(objectStorage).isInstanceOf(S3ObjectStorage.class);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminUsername = "minio.adm." + suffix;
        String adminPassword = "Valid@1234";
        adminUserId = keycloakClient.createUser(new CreateUserCommand(
                adminUsername,
                adminUsername + "@thi360.vn",
                "MinIO",
                "Admin",
                adminPassword,
                true,
                false,
                true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String userCode = String.format("U%06d", seq == null ? 1L : seq);
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                adminUserId,
                userCode,
                adminUsername,
                adminUsername.toLowerCase(),
                "MinIO Admin",
                adminUsername + "@thi360.vn",
                (adminUsername + "@thi360.vn").toLowerCase(),
                Timestamp.from(now),
                adminUserId,
                Timestamp.from(now),
                adminUserId);
        jdbcTemplate.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?)",
                adminUserId,
                SYSTEM_ADMIN_ROLE_ID,
                adminUserId,
                Timestamp.from(now));

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginRes = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        objectMapper.writeValueAsString(Map.of("identifier", adminUsername, "password", adminPassword)),
                        loginHeaders),
                String.class);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> setCookies = loginRes.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        StringBuilder cookieHeader = new StringBuilder();
        String xsrfToken = null;
        for (String sc : setCookies) {
            String nameVal = sc.split(";", 2)[0];
            if (!cookieHeader.isEmpty()) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(nameVal);
            if (nameVal.startsWith(AuthCookieService.XSRF_COOKIE + "=")) {
                xsrfToken = nameVal.substring((AuthCookieService.XSRF_COOKIE + "=").length());
            }
        }
        assertThat(xsrfToken).isNotBlank();

        // 1. Over-5 MiB upload hits Tomcat MaxUploadSizeExceededException -> 400 IMPORT_FILE_TOO_LARGE
        byte[] over5MiB = new byte[5 * 1024 * 1024 + 1024];
        ResponseEntity<String> tooLargeRes = uploadMultipart(
                "/api/v1/admin/import-batches",
                "too-large.xlsx",
                over5MiB,
                cookieHeader.toString(),
                xsrfToken);
        assertThat(tooLargeRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(tooLargeRes.getBody()).get("code").asText())
                .isEqualTo("IMPORT_FILE_TOO_LARGE");

        // 2. Upload valid 1-row workbook -> stored encrypted in MinIO (bucket auto-created)
        String studentUsername = "minio.stu." + suffix;
        byte[] validXlsx = buildSingleRowXlsx(studentUsername, "MinIO Student");
        ResponseEntity<String> uploadRes = uploadMultipart(
                "/api/v1/admin/import-batches",
                "minio-valid.xlsx",
                validXlsx,
                cookieHeader.toString(),
                xsrfToken);
        assertThat(uploadRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        batchId = UUID.fromString(objectMapper.readTree(uploadRes.getBody()).get("id").asText());

        // Confirm via HTTP
        idempotencyKey = "minio-idem-" + suffix;
        HttpHeaders confirmHeaders = new HttpHeaders();
        confirmHeaders.add(HttpHeaders.COOKIE, cookieHeader.toString());
        confirmHeaders.add(AuthCookieService.XSRF_HEADER, xsrfToken);
        confirmHeaders.add(HttpHeaders.ORIGIN, "http://localhost:3001");
        confirmHeaders.add("Idempotency-Key", idempotencyKey);
        ResponseEntity<String> confirmRes = restTemplate.exchange(
                "/api/v1/admin/import-batches/" + batchId + "/confirm",
                HttpMethod.POST,
                new HttpEntity<>(confirmHeaders),
                String.class);
        assertThat(confirmRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Process via job
        ImportBatchStatus status = importBatchProcessorJob.processBatch(batchId);
        assertThat(status).isEqualTo(ImportBatchStatus.COMPLETED);
        createdStudentId = jdbcTemplate.queryForObject(
                "SELECT created_user_id FROM identity.import_row WHERE batch_id = ?",
                UUID.class,
                batchId);

        // Verify directly against real MinIO S3: source gone, result encrypted (not PK zip, not plaintext)
        String minioEndpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        try (S3Client rawS3 = S3Client.builder()
                .endpointOverride(URI.create(minioEndpoint))
                .forcePathStyle(true)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_USER, MINIO_PASS)))
                .build()) {
            assertThat(objectStorage.exists(ImportBatchService.sourceObjectKey(batchId))).isFalse();

            byte[] rawResultBytes = rawS3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(MINIO_BUCKET)
                            .key(ImportBatchService.resultObjectKey(batchId))
                            .build())
                    .asByteArray();
            assertThat(rawResultBytes[0] == 0x50 && rawResultBytes[1] == 0x4B).isFalse();
            assertThat(new String(rawResultBytes, StandardCharsets.UTF_8)).doesNotContain(studentUsername);
        }

        // Delete result via HTTP -> removed from MinIO
        HttpHeaders delHeaders = new HttpHeaders();
        delHeaders.add(HttpHeaders.COOKIE, cookieHeader.toString());
        delHeaders.add(AuthCookieService.XSRF_HEADER, xsrfToken);
        delHeaders.add(HttpHeaders.ORIGIN, "http://localhost:3001");
        ResponseEntity<Void> delRes = restTemplate.exchange(
                "/api/v1/admin/import-batches/" + batchId + "/result",
                HttpMethod.DELETE,
                new HttpEntity<>(delHeaders),
                Void.class);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(objectStorage.exists(ImportBatchService.resultObjectKey(batchId))).isFalse();
    }

    private ResponseEntity<String> uploadMultipart(
            String path, String filename, byte[] bytes, String cookieHeader, String xsrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        headers.add(AuthCookieService.XSRF_HEADER, xsrfToken);
        headers.add(HttpHeaders.ORIGIN, "http://localhost:3001");

        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static byte[] buildSingleRowXlsx(String username, String fullName) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên đăng nhập*");
            header.createCell(1).setCellValue("Mật khẩu");
            header.createCell(2).setCellValue("Họ và tên*");
            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue(username);
            r.createCell(2).setCellValue(fullName);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
