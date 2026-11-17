package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.shared.security.AuthCookieService;

/**
 * AC-4 (UNI-2607): giới hạn multipart dùng chung 21/22 MB áp cho mọi endpoint upload —
 * file 20 MB qua được controller (200), file 23 MB bị tầng servlet chặn trước controller
 * → 413 {@code UPLOAD_TOO_LARGE} từ {@code GlobalExceptionHandler}.
 *
 * <p>Endpoint thử {@code /api/v1/admin/__test/upload} đến từ {@link TestBe1Endpoints}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestBe1Endpoints.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UploadLimitsIT extends AbstractKeycloakIntegrationTest {

    private static final UUID SYSTEM_ADMIN_ROLE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createdUserId;

    @AfterEach
    void cleanup() {
        if (createdUserId != null) {
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", createdUserId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", createdUserId);
            try {
                keycloakClient.deleteUser(createdUserId);
            } catch (Exception ignored) {
            }
            createdUserId = null;
        }
    }

    @Test
    void upload20MiB_reachesController200_andUpload23MiB_is413UploadTooLarge() throws Exception {
        String[] session = createAdminAndLogin();
        String cookieHeader = session[0];
        String xsrfToken = session[1];

        // 20 MB: multipart được phân tích, controller trả 200
        byte[] twentyMiB = new byte[20 * 1024 * 1024];
        ResponseEntity<String> okRes = uploadMultipart(
                "/api/v1/admin/__test/upload", "within-limit.bin", twentyMiB, cookieHeader, xsrfToken);
        assertThat(okRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode okJson = objectMapper.readTree(okRes.getBody());
        assertThat(okJson.get("received").asBoolean()).isTrue();
        assertThat(okJson.get("size").asLong()).isEqualTo(twentyMiB.length);

        // 23 MB: vượt giới hạn servlet 21 MB -> 413 UPLOAD_TOO_LARGE (shared handler)
        byte[] twentyThreeMiB = new byte[23 * 1024 * 1024];
        ResponseEntity<String> tooLargeRes = uploadMultipart(
                "/api/v1/admin/__test/upload", "too-big.bin", twentyThreeMiB, cookieHeader, xsrfToken);
        assertThat(tooLargeRes.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        JsonNode errJson = objectMapper.readTree(tooLargeRes.getBody());
        assertThat(errJson.get("code").asText()).isEqualTo("UPLOAD_TOO_LARGE");
    }

    private String[] createAdminAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "upload.lim." + suffix;
        String password = "Valid@1234";
        createdUserId = keycloakClient.createUser(new CreateUserCommand(
                username,
                username + "@thi360.vn",
                "Upload Limit",
                "Tester",
                password,
                true,
                false,
                true));
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String userCode = String.format("U%06d", seq);
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                createdUserId,
                userCode,
                username,
                username.toLowerCase(),
                "Upload Limit Tester",
                username + "@thi360.vn",
                (username + "@thi360.vn").toLowerCase(),
                Timestamp.from(now),
                createdUserId,
                Timestamp.from(now),
                createdUserId);
        jdbcTemplate.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?)",
                createdUserId, SYSTEM_ADMIN_ROLE_ID, createdUserId, Timestamp.from(now));

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginRes = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        objectMapper.writeValueAsString(
                                Map.of("identifier", username, "password", password)),
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
        return new String[] {cookieHeader.toString(), xsrfToken};
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
}
