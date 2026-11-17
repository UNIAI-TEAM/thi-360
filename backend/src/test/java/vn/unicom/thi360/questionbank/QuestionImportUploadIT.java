package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SecurityProperties;
import vn.unicom.thi360.shared.storage.ObjectStorage;

class QuestionImportUploadIT extends AbstractIntegrationTest {
    private static final String URL = "/api/v1/admin/question-imports/uploads";
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectStorage storage;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void clearUploadRateLimitBucket() {
        redisTemplate.delete(RateLimitFilter.UPLOAD_KEY_PREFIX + "127.0.0.1");
    }

    @Test
    void uploadCsvStoresSourceAndSuggestions() throws Exception {
        byte[] bytes = "type,content,subject,grade,difficulty\nSHORT_ANSWER,Question,TOAN,G10,NB\n"
                .getBytes(StandardCharsets.UTF_8);
        var result = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "questions.csv", "text/csv", bytes))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileType").value("CSV"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.suggestedMapping.c0").value("type"))
                .andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText());
        var row = jdbc.queryForMap("select object_key, status, expires_at, created_at from questionbank.question_import_upload where id = ?", id);
        assertThat(row.get("status")).isEqualTo("UPLOADED");
        assertThat(storage.get((String) row.get("object_key"))).contains(bytes);
        assertThat(((java.sql.Timestamp) row.get("expires_at")).toInstant())
                .isEqualTo(((java.sql.Timestamp) row.get("created_at")).toInstant().plusSeconds(86400));
    }

    @Test
    void macroAndOversizedFilesAreRejected() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "test.xlsm", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[] {1}))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_MACRO_NOT_ALLOWED"));
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "test.csv", "text/csv", new byte[5 * 1024 * 1024 + 1]))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_TOO_LARGE"));
    }

    @Test
    void malformedCsvReturns422Not500() throws Exception {
        // Ô ngoặc kép không đóng: iterator của Commons CSV ném UncheckedIOException (R1-2)
        // — endpoint vẫn trả IMPORT_INVALID_FILE/422.
        byte[] bytes = "type,content\nSHORT_ANSWER,\"unterminated".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", bytes))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID_FILE"));
    }

    @Test
    void macroPartAndRowLimitAreRejected() throws Exception {
        byte[] macro;
        try (var out = new ByteArrayOutputStream(); var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/vbaProject.bin"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
            zip.finish();
            macro = out.toByteArray();
        }
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "macro.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, macro))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_MACRO_NOT_ALLOWED"));
        StringBuilder csv = new StringBuilder("type,content\n");
        for (int i = 0; i < 2001; i++) csv.append("SHORT_ANSWER,question\n");
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "large.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_ROW_LIMIT_EXCEEDED"));
    }

    @Test
    void zipBombRejected() throws Exception {
        byte[] workbook;
        try (var excel = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = excel.createSheet("Data");
            var header = sheet.createRow(0);
            var row = sheet.createRow(1);
            for (int i = 0; i < 20; i++) {
                header.createCell(i).setCellValue("column" + i);
                row.createCell(i).setCellValue("A".repeat(30000) + i);
            }
            excel.write(out);
            workbook = out.toByteArray();
        }
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "bomb.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, workbook))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID_FILE"));
    }

    @Test
    void viewerCannotUpload() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "test.csv", "text/csv", new byte[] {1}))
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUploadsDoNotConsumeSharedIpQuota() throws Exception {
        String client = "203.0.113.113";
        String key = RateLimitFilter.UPLOAD_KEY_PREFIX + client;
        redisTemplate.delete(key);
        try {
            int limit = securityProperties.rateLimit().uploadPerIpLimit();
            for (int i = 0; i <= limit; i++) {
                mockMvc.perform(multipart(URL)
                                .file(new MockMultipartFile("file", "test.csv", "text/csv", new byte[] {1}))
                                .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                                .header(ClientIpResolver.X_FORWARDED_FOR, client))
                        .andExpect(status().isUnauthorized());
            }
            assertThat(redisTemplate.hasKey(key)).isFalse();
            mockMvc.perform(multipart(URL)
                            .file(new MockMultipartFile("file", "test.csv", "text/csv",
                                    "type,content\nSHORT_ANSWER,q\n".getBytes(StandardCharsets.UTF_8)))
                            .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; })
                            .header(ClientIpResolver.X_FORWARDED_FOR, client)
                            .with(asUser(Permissions.QUESTION_MODIFY)))
                    .andExpect(status().isOk());
        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void uploadRateLimitReturns429AndIsolatesClients() throws Exception {
        // Chính sách upload theo IP của shared RateLimitFilter cũng áp cho endpoint này
        // (R1-8): vượt hạn mức -> 429 + Retry-After; client khác giữ nguyên quota.
        String clientA = "203.0.113.111";
        String clientB = "203.0.113.112";
        redisTemplate.delete(java.util.List.of(
                RateLimitFilter.UPLOAD_KEY_PREFIX + clientA,
                RateLimitFilter.UPLOAD_KEY_PREFIX + clientB));
        try {
            int limit = securityProperties.rateLimit().uploadPerIpLimit();
            byte[] csv = "type,content\nSHORT_ANSWER,q\n".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < limit; i++) {
                mockMvc.perform(multipart(URL)
                                .file(new MockMultipartFile("file", "rl-" + i + ".csv", "text/csv", csv))
                                .with(req -> {
                                    req.setRemoteAddr("127.0.0.1");
                                    return req;
                                })
                                .header(ClientIpResolver.X_FORWARDED_FOR, clientA)
                                .with(asUser(Permissions.QUESTION_MODIFY)))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(multipart(URL)
                            .file(new MockMultipartFile("file", "rl-blocked.csv", "text/csv", csv))
                            .with(req -> {
                                req.setRemoteAddr("127.0.0.1");
                                return req;
                            })
                            .header(ClientIpResolver.X_FORWARDED_FOR, clientA)
                            .with(asUser(Permissions.QUESTION_MODIFY)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
            mockMvc.perform(multipart(URL)
                            .file(new MockMultipartFile("file", "rl-b.csv", "text/csv", csv))
                            .with(req -> {
                                req.setRemoteAddr("127.0.0.1");
                                return req;
                            })
                            .header(ClientIpResolver.X_FORWARDED_FOR, clientB)
                            .with(asUser(Permissions.QUESTION_MODIFY)))
                    .andExpect(status().isOk());
        } finally {
            redisTemplate.delete(java.util.List.of(
                    RateLimitFilter.UPLOAD_KEY_PREFIX + clientA,
                    RateLimitFilter.UPLOAD_KEY_PREFIX + clientB));
        }
    }
}
