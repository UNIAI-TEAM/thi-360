package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.AvatarTestImages;
import vn.unicom.thi360.identity.application.EmailCodeService;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.AuthFailureReason;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.KeycloakAuthException;
import vn.unicom.thi360.shared.mail.MailKind;
import vn.unicom.thi360.shared.mail.MailQueue;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.support.MailOutbox;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MobileProfileFixturesIT extends AbstractKeycloakIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/me-profile-200-avatar-pending-email.json",
                "api/me-profile-update-200.json",
                "api/me-profile-update-400-phone.json",
                "api/me-avatar-put-200.json",
                "api/me-avatar-put-413-avatar-too-large.json",
                "api/me-avatar-put-413-upload-too-large.json",
                "api/me-avatar-put-422-avatar-invalid-image.json",
                "api/me-avatar-put-422-avatar-dimensions-exceeded.json",
                "api/me-email-verification-429-rate-limited.json",
                "api/me-email-verify-422-email-code-invalid.json",
                "api/me-email-verify-422-email-code-expired.json",
                "api/me-email-change-400-wrong-old-password.json",
                "api/me-email-change-400-new-email.json",
                "api/me-email-change-409-email-taken.json",
                "api/me-email-change-429-rate-limited.json",
                "api/me-email-change-429-login-throttled.json",
                "api/me-email-change-confirm-422-email-code-invalid.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    private static final String PRIVATE_BUCKET = "thi360-mc-private";
    private static final String AVATAR_BUCKET = "thi360-mc-avatars";
    private static final String MINIO_USER = "thi360it";
    private static final String MINIO_PASS = "thi360it-secret-key";
    private static final String PASSWORD = "Valid@1234";
    private static final int FIVE_MB = 5 * 1024 * 1024;
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

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
        registry.add("thi360.s3.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("thi360.s3.bucket", () -> PRIVATE_BUCKET);
        registry.add("thi360.s3.access-key", () -> MINIO_USER);
        registry.add("thi360.s3.secret-key", () -> MINIO_PASS);
        registry.add("thi360.s3.auto-create-bucket", () -> "true");
        registry.add("thi360.avatar.bucket", () -> AVATAR_BUCKET);
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private KeycloakIdentityClient keycloakClient;
    @Autowired private EmailCodeService emailCodeService;
    @Autowired private MailQueue mailQueue;
    @Autowired private PlatformTransactionManager txManager;

    private final List<Account> createdAccounts = new ArrayList<>();
    private final List<String> touchedEmails = new ArrayList<>();
    private final List<String> usedIps = new ArrayList<>();
    private MailOutbox mailOutbox;
    private MobileFixtureCertifier certifier;

    private record Account(UUID id, String code, String username, String email) {}

    @BeforeEach
    void setUp() {
        mailOutbox = new MailOutbox(jdbc);
        certifier = new MobileFixtureCertifier(objectMapper);
    }

    @AfterEach
    void tearDown() {
        reset(keycloakClient);
        for (String email : touchedEmails) {
            jdbc.update("DELETE FROM shared.email_message WHERE lower(to_email) = lower(?)", email);
        }
        touchedEmails.clear();
        for (Account acc : createdAccounts) {
            jdbc.update("DELETE FROM identity.email_code WHERE user_id = ?", acc.id());
            jdbc.update("DELETE FROM identity.user_avatar WHERE user_id = ?", acc.id());
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", acc.id());
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", acc.id());
            try {
                keycloakClient.deleteUser(acc.id());
            } catch (Exception ignored) {
            }
        }
        createdAccounts.clear();
        for (String ip : usedIps) {
            redis.delete(RateLimitFilter.KEY_PREFIX + ip);
        }
        usedIps.clear();
    }

    @Test
    void fullNameLimitUnchanged() throws Exception {
        Account acc = createStudentAccount("mc.fn", true);

        String exact200 = "A".repeat(200);
        MvcResult okRes = mockMvc.perform(patch("/api/v1/me/profile")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", exact200))))
                .andReturn();
        assertThat(okRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(okRes.getResponse().getContentAsString()).path("fullName").asText())
                .isEqualTo(exact200);

        String over201 = "A".repeat(201);
        MvcResult badRes = mockMvc.perform(patch("/api/v1/me/profile")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", over201))))
                .andReturn();
        assertThat(badRes.getResponse().getStatus()).isEqualTo(400);
        JsonNode badJson = objectMapper.readTree(badRes.getResponse().getContentAsString());
        assertThat(badJson.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(badJson.path("fieldErrors").get(0).path("field").asText()).isEqualTo("fullName");
    }

    @Test
    void allProfileAvatarAndEmailContractFixturesMatchLiveResponses() throws Exception {
        Account acc = createStudentAccount("mc.prof", false);
        Account other = createStudentAccount("mc.other", true);

        MvcResult patchOk = mockMvc.perform(patch("/api/v1/me/profile")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Nguyễn Minh Anh",
                                "phone", "0912345678",
                                "dateOfBirth", "2008-05-15",
                                "gender", "FEMALE",
                                "province", "Hà Nội",
                                "school", "THPT Chu Văn An",
                                "gradeLevel", "GRADE_12",
                                "preferredLocale", "vi"))))
                .andReturn();
        certifier.certify("api/me-profile-update-200.json", 200, patchOk,
                Set.of("id", "code", "username", "email", "updatedAt", "createdAt"));

        MvcResult patchBadPhone = mockMvc.perform(patch("/api/v1/me/profile")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "abc-invalid-phone"))))
                .andReturn();
        certifier.certify("api/me-profile-update-400-phone.json", 400, patchBadPhone, Set.of("traceId"));

        byte[] validPng = AvatarTestImages.png(64, 64);
        MvcResult avatar200 = mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", validPng))
                        .with(asUser(acc.id(), acc.username())))
                .andReturn();
        certifier.certify("api/me-avatar-put-200.json", 200, avatar200,
                Set.of("username", "email", "avatarAssetId", "avatarUrl"));

        MvcResult avatarTooLarge = mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/avatar")
                        .file(new MockMultipartFile("file", "too-big.png", "image/png", AvatarTestImages.padded(validPng, FIVE_MB + 1)))
                        .with(asUser(acc.id(), acc.username())))
                .andReturn();
        certifier.certify("api/me-avatar-put-413-avatar-too-large.json", 413, avatarTooLarge, Set.of("traceId"));

        MvcResult avatarInvalid = mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/avatar")
                        .file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", AvatarTestImages.text()))
                        .with(asUser(acc.id(), acc.username())))
                .andReturn();
        certifier.certify("api/me-avatar-put-422-avatar-invalid-image.json", 422, avatarInvalid, Set.of("traceId"));

        MvcResult avatarDims = mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/avatar")
                        .file(new MockMultipartFile("file", "huge.png", "image/png", AvatarTestImages.pngHeaderOnly(4097, 4096)))
                        .with(asUser(acc.id(), acc.username())))
                .andReturn();
        certifier.certify("api/me-avatar-put-422-avatar-dimensions-exceeded.json", 422, avatarDims, Set.of("traceId"));

        String accessToken = mobileLoginAccessToken(acc.username(), PASSWORD);
        byte[] twentyThreeMiB = new byte[23 * 1024 * 1024];
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        uploadHeaders.setBearerAuth(accessToken);
        ByteArrayResource bigResource = new ByteArrayResource(twentyThreeMiB) {
            @Override
            public String getFilename() {
                return "oversized.png";
            }
        };
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", bigResource);
        ResponseEntity<String> uploadTooLarge = restTemplate.exchange(
                "/api/v1/me/avatar", HttpMethod.PUT, new HttpEntity<>(multipartBody, uploadHeaders), String.class);
        certifier.certify("api/me-avatar-put-413-upload-too-large.json", 413,
                uploadTooLarge.getStatusCode().value(), uploadTooLarge.getBody(), Set.of("traceId"));

        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            String otp = emailCodeService.issue(acc.id(), EmailCodePurpose.VERIFY, acc.email());
            mailQueue.enqueue(MailKind.EMAIL_VERIFY, acc.email(), "vi",
                    Map.of("fullName", "Nguyễn Minh Anh", "code", otp), acc.id());
        });
        String realVerifyCode = extractCode(acc.email(), MailKind.EMAIL_VERIFY);
        String wrongVerifyCode = "000000".equals(realVerifyCode) ? "111111" : "000000";

        MvcResult verifyRl = mockMvc.perform(post("/api/v1/me/email/verification")
                        .with(asUser(acc.id(), acc.username())))
                .andReturn();
        certifier.certify("api/me-email-verification-429-rate-limited.json", 429, verifyRl, Set.of("traceId", "retryAfter"));

        MvcResult verifyInvalid = mockMvc.perform(post("/api/v1/me/email/verify")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + wrongVerifyCode + "\"}"))
                .andReturn();
        certifier.certify("api/me-email-verify-422-email-code-invalid.json", 422, verifyInvalid, Set.of("traceId"));

        jdbc.update("UPDATE identity.email_code SET expires_at = now() - interval '1 minute' WHERE user_id = ? AND purpose = 'VERIFY'",
                acc.id());
        MvcResult verifyExpired = mockMvc.perform(post("/api/v1/me/email/verify")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + realVerifyCode + "\"}"))
                .andReturn();
        certifier.certify("api/me-email-verify-422-email-code-expired.json", 422, verifyExpired, Set.of("traceId"));

        String pendingEmail = "pending." + UUID.randomUUID().toString().substring(0, 8) + "@thi360.vn";
        touchedEmails.add(pendingEmail);

        MvcResult wrongPwd = mockMvc.perform(post("/api/v1/me/email/change")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newEmail", pendingEmail, "currentPassword", "Wrong@1234"))))
                .andReturn();
        certifier.certify("api/me-email-change-400-wrong-old-password.json", 400, wrongPwd, Set.of("traceId"));

        MvcResult badNewEmail = mockMvc.perform(post("/api/v1/me/email/change")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newEmail", "not-an-email", "currentPassword", PASSWORD))))
                .andReturn();
        certifier.certify("api/me-email-change-400-new-email.json", 400, badNewEmail, Set.of("traceId"));

        MvcResult emailTaken = mockMvc.perform(post("/api/v1/me/email/change")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newEmail", other.email(), "currentPassword", PASSWORD))))
                .andReturn();
        certifier.certify("api/me-email-change-409-email-taken.json", 409, emailTaken, Set.of("traceId"));

        MvcResult changeAccepted = mockMvc.perform(post("/api/v1/me/email/change")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newEmail", pendingEmail, "currentPassword", PASSWORD))))
                .andReturn();
        assertThat(changeAccepted.getResponse().getStatus()).isEqualTo(202);

        MvcResult profileWithAvatarAndPending = mockMvc.perform(get("/api/v1/me/profile")
                        .with(asUser(acc.id(), acc.username())))
                .andReturn();
        certifier.certify("api/me-profile-200-avatar-pending-email.json", 200, profileWithAvatarAndPending,
                Set.of("id", "code", "username", "email", "pendingEmail", "avatarAssetId", "avatarUrl", "updatedAt", "createdAt"));

        MvcResult changeRl = mockMvc.perform(post("/api/v1/me/email/change")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newEmail", pendingEmail, "currentPassword", PASSWORD))))
                .andReturn();
        certifier.certify("api/me-email-change-429-rate-limited.json", 429, changeRl, Set.of("traceId", "retryAfter"));

        String realChangeCode = extractCode(pendingEmail, MailKind.EMAIL_CHANGE);
        String wrongChangeCode = "000000".equals(realChangeCode) ? "111111" : "000000";
        MvcResult confirmInvalid = mockMvc.perform(post("/api/v1/me/email/change/confirm")
                        .with(asUser(acc.id(), acc.username()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + wrongChangeCode + "\"}"))
                .andReturn();
        certifier.certify("api/me-email-change-confirm-422-email-code-invalid.json", 422, confirmInvalid, Set.of("traceId"));

        Account lockAcc = createStudentAccount("mc.lock", true);
        String lockNewEmail = "locked." + UUID.randomUUID().toString().substring(0, 8) + "@thi360.vn";
        touchedEmails.add(lockNewEmail);
        doThrow(new KeycloakAuthException(AuthFailureReason.ACCOUNT_LOCKED))
                .when(keycloakClient)
                .passwordGrant(any(), eq(lockAcc.username()), any());
        try {
            MvcResult throttledRes = mockMvc.perform(post("/api/v1/me/email/change")
                            .with(asUser(lockAcc.id(), lockAcc.username()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("newEmail", lockNewEmail, "currentPassword", PASSWORD))))
                    .andReturn();
            certifier.certify("api/me-email-change-429-login-throttled.json", 429, throttledRes, Set.of("traceId"));
        } finally {
            reset(keycloakClient);
        }

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private Account createStudentAccount(String prefix, boolean emailVerified) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "." + suffix;
        String email = username + "@thi360.vn";
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, "Nguyễn", "Văn An", PASSWORD, true, false, emailVerified));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String userCode = String.format("U%06d", seq);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     email_verified_at, preferred_locale, source, status, must_change_password,
                     created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, 'Nguyễn Văn An', ?, ?, ?, 'vi', 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId, userCode, username, username.toLowerCase(), email, email.toLowerCase(),
                emailVerified ? now : null, now, userId, now, userId);
        Account acc = new Account(userId, userCode, username, email);
        createdAccounts.add(acc);
        touchedEmails.add(email);
        return acc;
    }

    private String mobileLoginAccessToken(String username, String password) throws Exception {
        String ip = "203.0.113." + ThreadLocalRandom.current().nextInt(10, 240);
        usedIps.add(ip);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(ClientIpResolver.X_FORWARDED_FOR, ip);
        ResponseEntity<String> loginRes = restTemplate.exchange(
                "/api/v1/auth/mobile/login",
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("identifier", username, "password", password)), headers),
                String.class);
        assertThat(loginRes.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(loginRes.getBody()).path("accessToken").asText();
    }

    private String extractCode(String email, MailKind kind) {
        MailOutbox.Mail mail = mailOutbox.require(email, kind);
        Matcher matcher = SIX_DIGITS.matcher(mail.text());
        assertThat(matcher.find()).as("6-digit OTP in mail to %s", email).isTrue();
        return matcher.group(1);
    }
}
