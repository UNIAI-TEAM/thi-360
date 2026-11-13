package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.shared.security.AuthCookieService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogMultipartTomcatIT extends AbstractKeycloakIntegrationTest {

    private static final UUID QUESTION_TAG_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000008");
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
        jdbcTemplate.update("DELETE FROM catalog.catalog_import_preview");
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
    void realTomcatMultipartAcceptsOver1MiBUnder5MiB_andMapsOver5MiBTo400ImportFileTooLarge() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "catalog.mp." + suffix;
        String password = "Valid@1234";
        createdUserId = keycloakClient.createUser(new CreateUserCommand(
                username,
                username + "@thi360.vn",
                "Catalog Multipart",
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
                "Catalog Multipart Tester",
                username + "@thi360.vn",
                (username + "@thi360.vn").toLowerCase(),
                Timestamp.from(now),
                createdUserId,
                Timestamp.from(now),
                createdUserId);
        jdbcTemplate.update(
                "INSERT INTO identity.user_role (user_id, role_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?)",
                createdUserId,
                SYSTEM_ADMIN_ROLE_ID,
                createdUserId,
                Timestamp.from(now));

        // Login over real HTTP to obtain session cookies and CSRF token
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginRes = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        objectMapper.writeValueAsString(Map.of("identifier", username, "password", password)),
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

        // 1. Valid XLSX > 1 MiB (Spring default limit) and < 5 MiB -> reaches preview and returns 200 OK
        byte[] over1MiBXlsx = buildLargeValidSimpleXlsx(2_200);
        assertThat(over1MiBXlsx.length)
                .isGreaterThan(1_048_576)
                .isLessThan(5 * 1024 * 1024);

        ResponseEntity<String> okRes = uploadMultipart(
                "/api/v1/admin/catalog/groups/" + QUESTION_TAG_GROUP_ID + "/items/import",
                "large-valid.xlsx",
                over1MiBXlsx,
                cookieHeader.toString(),
                xsrfToken);
        assertThat(okRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode okJson = objectMapper.readTree(okRes.getBody());
        assertThat(okJson.get("totalRows").asInt()).isEqualTo(2_200);
        assertThat(okJson.get("canConfirm").asBoolean()).isTrue();

        // 2. Over-limit upload (> 5 MiB) -> container MaxUploadSizeExceededException mapped to 400 IMPORT_FILE_TOO_LARGE
        byte[] over5MiB = new byte[5 * 1024 * 1024 + 1024];
        ResponseEntity<String> tooLargeRes = uploadMultipart(
                "/api/v1/admin/catalog/groups/" + QUESTION_TAG_GROUP_ID + "/items/import",
                "too-big.xlsx",
                over5MiB,
                cookieHeader.toString(),
                xsrfToken);
        assertThat(tooLargeRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode errJson = objectMapper.readTree(tooLargeRes.getBody());
        assertThat(errJson.get("code").asText()).isEqualTo("IMPORT_FILE_TOO_LARGE");
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

    private static byte[] buildLargeValidSimpleXlsx(int rowCount) throws IOException {
        Random rnd = new Random(42L);
        byte[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".getBytes(StandardCharsets.US_ASCII);
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            Sheet s = wb.createSheet("Large");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Mã*");
            h.createCell(1).setCellValue("Tên tiếng Việt*");
            h.createCell(2).setCellValue("Mô tả");
            byte[] buf = new byte[900];
            for (int i = 1; i <= rowCount; i++) {
                Row r = s.createRow(i);
                r.createCell(0).setCellValue("MP_TAG_" + i);
                r.createCell(1).setCellValue("Nhãn " + i);
                for (int j = 0; j < buf.length; j++) {
                    buf[j] = alphabet[rnd.nextInt(alphabet.length)];
                }
                r.createCell(2).setCellValue(new String(buf, StandardCharsets.US_ASCII));
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            wb.dispose();
            return bos.toByteArray();
        }
    }
}
