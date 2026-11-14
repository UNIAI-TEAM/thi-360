package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.application.ImportBatchProcessorJob;
import vn.unicom.thi360.identity.application.ImportBatchService;
import vn.unicom.thi360.identity.application.ImportWorkbookCodec;
import vn.unicom.thi360.identity.domain.ImportBatchStatus;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.ImportRowStatus;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.ImportRowRepository;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SecurityProperties;
import vn.unicom.thi360.shared.storage.EncryptedObjectStorage;
import vn.unicom.thi360.shared.storage.InMemoryObjectStorage;
import vn.unicom.thi360.shared.storage.ObjectStorage;
@ExtendWith(OutputCaptureExtension.class)
class ImportBatchAdminIT extends AbstractKeycloakIntegrationTest {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordPolicy passwordPolicy;

    @Autowired
    private ImportBatchProcessorJob importBatchProcessorJob;

    @Autowired
    private ObjectStorage objectStorage;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    @MockitoSpyBean
    private ImportRowRepository importRowRepository;

    @MockitoSpyBean
    private EncryptedObjectStorage encryptedObjectStorage;

    @MockitoSpyBean
    private ImportWorkbookCodec workbookCodec;

    private final List<UUID> createdBatchIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdKeycloakUserIds = new ArrayList<>();
    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<String> createdIdempotencyKeys = new ArrayList<>();

    @BeforeEach
    void setup() {
        clearInvocations(auditRecorder, keycloakClient, importRowRepository, encryptedObjectStorage, workbookCodec);
        clearRateLimitKeys();
    }

    @AfterEach
    void cleanup() {
        reset(keycloakClient, auditRecorder, importRowRepository, encryptedObjectStorage, workbookCodec);
        clearRateLimitKeys();
        if (objectStorage instanceof InMemoryObjectStorage mem) {
            mem.clear();
        }
        for (UUID batchId : createdBatchIds) {
            List<UUID> rowUserIds = jdbcTemplate.queryForList(
                    "SELECT created_user_id FROM identity.import_row WHERE batch_id = ? AND created_user_id IS NOT NULL",
                    UUID.class,
                    batchId);
            createdUserIds.addAll(rowUserIds);
            createdKeycloakUserIds.addAll(rowUserIds);
            jdbcTemplate.update("DELETE FROM identity.import_row WHERE batch_id = ?", batchId);
            jdbcTemplate.update("DELETE FROM identity.import_batch WHERE id = ?", batchId);
        }
        createdBatchIds.clear();

        for (String key : createdIdempotencyKeys) {
            jdbcTemplate.update("DELETE FROM shared.idempotency_key WHERE key = ?", key);
        }
        createdIdempotencyKeys.clear();

        for (UUID kcId : createdKeycloakUserIds) {
            try {
                keycloakClient.deleteUser(kcId);
            } catch (Exception ignored) {
                // Ignore if already deleted
            }
        }
        createdKeycloakUserIds.clear();

        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM classroom.class_member_history WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_permission WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        createdUserIds.clear();

        for (UUID classId : createdClassIds) {
            jdbcTemplate.update("DELETE FROM classroom.class_member_history WHERE class_id = ? OR related_class_id = ?", classId, classId);
            jdbcTemplate.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbcTemplate.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        createdClassIds.clear();
    }

    @Test
    void everyEndpoint_returns403WhenMissingUserModifyPermission() throws Exception {
        UUID anyId = Ids.newId();
        MockMultipartFile validFile = multipartXlsx("valid.xlsx", buildImportXlsx(List.<String[]>of(
                new String[] {"imp.403." + suffix(), "Valid@1234", "Forbidden User", "", "", ""})));

        mockMvc.perform(get("/api/v1/admin/import-batches/template").with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(multipart("/api/v1/admin/import-batches").file(validFile).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/rows", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/errors", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", anyId)
                        .header("Idempotency-Key", "key-403-" + suffix())
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/result", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/admin/import-batches/{id}/result", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void classCodeInFile_requiresClassModifyAtUploadAndConfirm_else403ImportClassPermissionRequired() throws Exception {
        String sfx = suffix();
        String activeCode = "IMPCLS-" + sfx;
        createTestClass(activeCode, "Import Active Class", true);

        byte[] xlsxWithClass = buildImportXlsx(List.<String[]>of(
                new String[] {"imp.cls." + sfx, "Valid@1234", "Student With Class", "", "", activeCode}));

        // 1. Upload with USER.MODIFY only (missing CLASS.MODIFY) -> 403 IMPORT_CLASS_PERMISSION_REQUIRED
        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("with-class.xlsx", xlsxWithClass))
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPORT_CLASS_PERMISSION_REQUIRED"));

        // 2. Upload with USER.MODIFY + CLASS.MODIFY -> 201
        UUID batchId = uploadBatch(xlsxWithClass, false, Permissions.USER_MODIFY, Permissions.CLASS_MODIFY);

        // 3. Confirm without CLASS.MODIFY (e.g. permission revoked) -> 403 IMPORT_CLASS_PERMISSION_REQUIRED
        String key = "confirm-no-cls-" + sfx;
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", key)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPORT_CLASS_PERMISSION_REQUIRED"));

        // 4. Confirm with USER.MODIFY + CLASS.MODIFY -> 200 CONFIRMED
        createdIdempotencyKeys.add(key);
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", key)
                        .with(asUser(Permissions.USER_MODIFY, Permissions.CLASS_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void templateDownload_containsHeadersExampleRowAndPasswordNote() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/admin/import-batches/template")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(res.getResponse().getContentType()).startsWith(XLSX_MEDIA_TYPE.toString());
        byte[] bytes = res.getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(0);
            assertThat(fmt.formatCellValue(header.getCell(0))).isEqualTo("Tên đăng nhập*");
            assertThat(fmt.formatCellValue(header.getCell(1))).isEqualTo("Mật khẩu");
            assertThat(fmt.formatCellValue(header.getCell(2))).isEqualTo("Họ và tên*");
            assertThat(fmt.formatCellValue(header.getCell(3))).isEqualTo("Email");
            assertThat(fmt.formatCellValue(header.getCell(4))).isEqualTo("Số điện thoại");
            assertThat(fmt.formatCellValue(header.getCell(5))).isEqualTo("Mã lớp");

            Row example = sheet.getRow(1);
            assertThat(example).isNotNull();
            assertThat(fmt.formatCellValue(example.getCell(6))).contains("để trống thì hệ thống sinh");
        }
    }

    @Test
    void validationErrors_duplicatesCaseInsensitive_wrongClassCreatesNoClass_andRowColumnMessages() throws Exception {
        String sfx = suffix();
        String existingUsername = "existing.u." + sfx;
        String existingEmail = "existing.e." + sfx + "@thi360.vn";
        insertActiveUser(existingUsername, existingEmail);

        String activeClassCode = "CLS-ACT-" + sfx;
        String inactiveClassCode = "CLS-INACT-" + sfx;
        String wrongClassCode = "CLS-NOPE-" + sfx;
        createTestClass(activeClassCode, "Active Class", true);
        createTestClass(inactiveClassCode, "Inactive Class", false);

        List<String[]> rows = List.of(
                // Row 2: valid
                new String[] {"Ok.User." + sfx, "Valid@1234", "Nguyễn Văn Hợp", "ok." + sfx + "@thi360.vn", "0901112223", activeClassCode},
                // Row 3: duplicate username in file (case-insensitive) + duplicate email in file (case-insensitive)
                new String[] {"OK.USER." + sfx.toUpperCase(), "Valid@1234", "Trùng Trong File", "OK." + sfx.toUpperCase() + "@THI360.VN", "", ""},
                // Row 4: username taken (case-insensitive) + email taken (case-insensitive)
                new String[] {existingUsername.toUpperCase(), "Valid@1234", "Trùng Hệ Thống", existingEmail.toUpperCase(), "", ""},
                // Row 5: required username + required fullName + invalid email
                new String[] {"", "Valid@1234", "", "bad-email", "", ""},
                // Row 6: invalid username + wrong class code
                new String[] {"ab", "Valid@1234", "Tên Sai", "", "", wrongClassCode},
                // Row 7: inactive class
                new String[] {"inact.cls." + sfx, "Valid@1234", "Lớp Ngừng", "", "", inactiveClassCode}
        );

        UUID batchId = uploadBatch(buildImportXlsx(rows), false, Permissions.USER_MODIFY, Permissions.CLASS_MODIFY);

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.totalRows").value(6))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(5))
                .andExpect(jsonPath("$.hasErrors").value(true))
                .andExpect(jsonPath("$.canConfirm").value(false));

        MvcResult rowsRes = mockMvc.perform(get("/api/v1/admin/import-batches/{id}/rows", batchId)
                        .param("status", "INVALID")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(rowsRes.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(5);

        // Row 3: USERNAME_DUPLICATE_IN_FILE + EMAIL_DUPLICATE_IN_FILE
        assertRowHasError(items.get(0), 3, "Tên đăng nhập", "USERNAME_DUPLICATE_IN_FILE");
        assertRowHasError(items.get(0), 3, "Email", "EMAIL_DUPLICATE_IN_FILE");
        // Row 4: USERNAME_TAKEN + EMAIL_TAKEN
        assertRowHasError(items.get(1), 4, "Tên đăng nhập", "USERNAME_TAKEN");
        assertRowHasError(items.get(1), 4, "Email", "EMAIL_TAKEN");
        // Row 5: REQUIRED (username, fullName) + EMAIL_INVALID
        assertRowHasError(items.get(2), 5, "Tên đăng nhập", "REQUIRED");
        assertRowHasError(items.get(2), 5, "Họ và tên", "REQUIRED");
        assertRowHasError(items.get(2), 5, "Email", "EMAIL_INVALID");
        // Row 6: USERNAME_INVALID + CLASS_NOT_FOUND
        assertRowHasError(items.get(3), 6, "Tên đăng nhập", "USERNAME_INVALID");
        assertRowHasError(items.get(3), 6, "Mã lớp", "CLASS_NOT_FOUND");
        // Row 7: CLASS_INACTIVE
        assertRowHasError(items.get(4), 7, "Mã lớp", "CLASS_INACTIVE");

        // Wrong class code must NOT create any class
        Integer wrongClassCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_room WHERE code_normalized = ?",
                Integer.class,
                wrongClassCode.toLowerCase());
        assertThat(wrongClassCount).isZero();

        // Confirm with errors -> 409 IMPORT_HAS_ERRORS
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", "err-confirm-" + sfx)
                        .with(asUser(Permissions.USER_MODIFY, Permissions.CLASS_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));
    }

    @Test
    void weakPasswordRow_returnsPasswordWeakAtRightRowAndColumn_andRemainsInvalidAfterRepeatedConfirms()
            throws Exception {
        String sfx = suffix();
        String weakUsername = "weak.pwd." + sfx;
        List<String[]> rows = List.of(
                new String[] {"good.pwd." + sfx, "Valid@1234", "Good Password", "", "", ""},
                new String[] {weakUsername, "weak", "Weak Password", "", "", ""}
        );
        UUID batchId = uploadBatch(buildImportXlsx(rows), false, Permissions.USER_MODIFY);

        MvcResult rowsRes = mockMvc.perform(get("/api/v1/admin/import-batches/{id}/rows", batchId)
                        .param("status", "INVALID")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(rowsRes.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(1);
        assertRowHasError(items.get(0), 3, "Mật khẩu", "PASSWORD_WEAK");

        // R1-4: Revalidation at confirm MUST preserve PASSWORD_WEAK and reject repeated confirms with 409
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                            .header("Idempotency-Key", "weak-confirm-" + attempt + "-" + sfx)
                            .with(asUser(Permissions.USER_MODIFY)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));

            mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                            .with(asUser(Permissions.USER_MODIFY)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VALIDATED"))
                    .andExpect(jsonPath("$.invalidRows").value(1))
                    .andExpect(jsonPath("$.canConfirm").value(false))
                    .andExpect(jsonPath("$.sourceAvailable").value(true));
        }

        MvcResult rowsAfter = mockMvc.perform(get("/api/v1/admin/import-batches/{id}/rows", batchId)
                        .param("status", "INVALID")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode itemsAfter = objectMapper.readTree(rowsAfter.getResponse().getContentAsString()).get("items");
        assertThat(itemsAfter).hasSize(1);
        assertRowHasError(itemsAfter.get(0), 3, "Mật khẩu", "PASSWORD_WEAK");

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/errors", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk());
        assertThat(userAccountRepository.findActiveByUsernameNormalized(weakUsername)).isEmpty();
    }

    @Test
    void errorFile_containsOnlyBadRowsAndErrorColumn_reuploadFixedFileHasZeroErrors() throws Exception {
        String sfx = suffix();
        List<String[]> rows = List.of(
                new String[] {"fix.ok1." + sfx, "Valid@1234", "Valid One", "", "", ""},
                new String[] {"ab", "KeepMe@123", "Bad Row Two", "", "", ""},
                new String[] {"fix.ok3." + sfx, "Valid@1234", "Valid Three", "", "", ""},
                new String[] {"fix.bad4." + sfx, "weak", "Bad Row Four", "bad-email", "", ""}
        );
        UUID batchId = uploadBatch(buildImportXlsx(rows), false, Permissions.USER_MODIFY);

        MvcResult errorRes = mockMvc.perform(get("/api/v1/admin/import-batches/{id}/errors", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] errorBytes = errorRes.getResponse().getContentAsByteArray();

        // Parse error workbook, assert only 2 bad rows + "Lỗi" column + original password preserved, then fix them
        byte[] fixedBytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(errorBytes));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            assertThat(sheet.getLastRowNum()).isEqualTo(2); // header + 2 bad rows
            Row header = sheet.getRow(0);
            assertThat(fmt.formatCellValue(header.getCell(6))).isEqualTo("Lỗi");

            Row bad1 = sheet.getRow(1);
            assertThat(fmt.formatCellValue(bad1.getCell(0))).isEqualTo("ab");
            assertThat(fmt.formatCellValue(bad1.getCell(1))).isEqualTo("KeepMe@123");
            assertThat(fmt.formatCellValue(bad1.getCell(6))).isNotBlank();

            Row bad2 = sheet.getRow(2);
            assertThat(fmt.formatCellValue(bad2.getCell(0))).isEqualTo("fix.bad4." + sfx);
            assertThat(fmt.formatCellValue(bad2.getCell(6))).contains("Mật khẩu").contains("Email");

            // Fix both rows in-place (keeping "Lỗi" column intact)
            bad1.getCell(0).setCellValue("fixed.row1." + sfx);
            bad2.getCell(1).setCellValue("Fixed@1234");
            bad2.getCell(3).setCellValue("fixed." + sfx + "@thi360.vn");

            wb.write(out);
            fixedBytes = out.toByteArray();
        }

        UUID fixedBatchId = uploadBatch(fixedBytes, false, Permissions.USER_MODIFY);
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", fixedBatchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andExpect(jsonPath("$.canConfirm").value(true));
    }

    @Test
    void confirmTwiceSameKey_runsOnce_andRevalidatesAtConfirm() throws Exception {
        String sfx = suffix();
        String u1 = "idem.u1." + sfx;
        String u2 = "idem.u2." + sfx;
        UUID batchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {u1, "Valid@1234", "Idem User 1", "", "", ""},
                        new String[] {u2, "Valid@1234", "Idem User 2", "", "", ""})),
                false,
                Permissions.USER_MODIFY);

        // Simulate u2 getting taken between upload and confirm -> confirm revalidates and returns 409 IMPORT_HAS_ERRORS
        insertActiveUser(u2, null);
        String keyFail = "idem-fail-" + sfx;
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", keyFail)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invalidRows").value(1));

        // Clean batch: confirm twice with same key -> identical response & single confirmation audit
        UUID cleanBatchId = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {"idem.clean." + sfx, "Valid@1234", "Idem Clean", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        String idemKey = "idem-ok-" + sfx;
        createdIdempotencyKeys.add(idemKey);
        clearInvocations(auditRecorder);

        MvcResult first = mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", cleanBatchId)
                        .header("Idempotency-Key", idemKey)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", cleanBatchId)
                        .header("Idempotency-Key", idemKey)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        verify(auditRecorder, times(1)).record(argThat(ev ->
                ev != null && "IMPORT_BATCH_CONFIRMED".equals(ev.action())));

        // Different key on already-confirmed batch -> 409
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", cleanBatchId)
                        .header("Idempotency-Key", "idem-other-" + sfx)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_ALREADY_CONFIRMED"));
    }

    @Test
    void processNextConfirmedBatch_claimsWithSkipLocked_neverStealsLiveLease_andRecoversStaleProcessingViaDispatcher()
            throws Exception {
        String sfx = suffix();
        UUID batchA = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {"skip.a1." + sfx, "Valid@1234", "Skip A1", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        UUID batchB = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {"skip.b1." + sfx, "Valid@1234", "Skip B1", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchA, "skip-a-" + sfx);
        confirmBatch(batchB, "skip-b-" + sfx);

        // R1-15: Hold FOR UPDATE lock on batchA in tx1 while dispatcher runs -> SKIP LOCKED skips A and processes B
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = executor.submit(() -> {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.executeWithoutResult(tx -> {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM identity.import_batch WHERE id = ? FOR UPDATE",
                        UUID.class,
                        batchA);
                lockAcquired.countDown();
                try {
                    releaseLock.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        });
        try {
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
            UUID claimedWhileALocked = importBatchProcessorJob.processNextConfirmedBatch().orElseThrow();
            assertThat(claimedWhileALocked).isEqualTo(batchB);
            assertThat(importBatchProcessorJob.processNextConfirmedBatch()).isEmpty();
        } finally {
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
        }

        // Release A -> dispatcher now claims and completes A
        UUID claimedA = importBatchProcessorJob.processNextConfirmedBatch().orElseThrow();
        assertThat(claimedA).isEqualTo(batchA);
        assertThat(importBatchProcessorJob.processNextConfirmedBatch()).isEmpty();

        // R1-1 + R1-15: Crash during PROCESSING after row 1 is genuinely CREATED; active lease protects against steal,
        // expired lease recovers via production dispatcher without re-creating row 1, and row 1 result password logs in.
        String resU1 = "res.u1." + sfx;
        String resU2 = "res.u2." + sfx;
        UUID interruptedBatchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {resU1, "", "Resume One", "", "", ""},
                        new String[] {resU2, "Valid@1234", "Resume Two", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(interruptedBatchId, "resume-confirm-" + sfx);

        doAnswer(invocation -> {
            KeycloakIdentityClient.CreateUserCommand cmd = invocation.getArgument(0);
            if (resU2.equalsIgnoreCase(cmd.username())) {
                throw new SimulatedWorkerCrashError("Simulated worker crash after row 1");
            }
            return invocation.callRealMethod();
        }).when(keycloakClient).createUser(any());

        try {
            dispatcherWorker().runScheduledImportBatches();
        } catch (SimulatedWorkerCrashError expected) {
            // Simulated abrupt worker death during PROCESSING
        }
        reset(keycloakClient);

        // Live worker lease (> now) MUST NOT be stolen by another dispatcher
        assertThat(importBatchProcessorJob.processNextConfirmedBatch()).isEmpty();

        // Expire lease -> production dispatcher resumes stale PROCESSING batch and processes ONLY row 2
        jdbcTemplate.update(
                "UPDATE identity.import_batch SET lease_expires_at = now() - interval '1 minute' WHERE id = ?",
                interruptedBatchId);
        clearInvocations(keycloakClient);

        dispatcherWorker().runScheduledImportBatches();

        verify(keycloakClient, times(1)).createUser(argThat(cmd ->
                cmd != null && resU2.equalsIgnoreCase(cmd.username())));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", interruptedBatchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdRows").value(2));

        String row1GenPwd = readResultCell(interruptedBatchId, 1, 5);
        assertThat(row1GenPwd).isNotBlank();
        assertLoginSucceeds(resU1, row1GenPwd);
    }

    @Test
    void fakeKeycloakFailureRow3Of5_autoRetriesViaDispatcherBeforeTerminal_andExhaustedExplicitRowWipesSecrets()
            throws Exception {
        String sfx = suffix();
        String clsCode = "CLS-KC-" + sfx;
        UUID classId = createTestClass(clsCode, "KC Fail Class", true);

        List<String[]> rows = List.of(
                new String[] {"kc.r1." + sfx, "", "Row One", "", "", clsCode},
                new String[] {"kc.r2." + sfx, "UserPwd@22", "Row Two", "", "", ""},
                new String[] {"kc.r3." + sfx, "", "Row Three", "", "", clsCode},
                new String[] {"kc.r4." + sfx, "UserPwd@44", "Row Four", "", "", ""},
                new String[] {"kc.r5." + sfx, "", "Row Five", "", "", ""}
        );
        UUID batchId = uploadBatch(buildImportXlsx(rows), false, Permissions.USER_MODIFY, Permissions.CLASS_MODIFY);
        confirmBatch(batchId, "kc-fail-confirm-" + sfx, Permissions.USER_MODIFY, Permissions.CLASS_MODIFY);

        // Fake Keycloak failure on row 3 (username kc.r3.<sfx>)
        String failUsername = "kc.r3." + sfx;
        doAnswer(invocation -> {
            KeycloakIdentityClient.CreateUserCommand cmd = invocation.getArgument(0);
            if (failUsername.equalsIgnoreCase(cmd.username())) {
                throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", "Simulated KC outage", null);
            }
            return invocation.callRealMethod();
        }).when(keycloakClient).createUser(any());

        dispatcherWorker().runScheduledImportBatches();

        // Retryable row failure keeps batch in PROCESSING with next_retry_at scheduled (not terminal yet)
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.createdRows").value(4))
                .andExpect(jsonPath("$.failedRows").value(1));

        // Before next_retry_at, dispatcher does not prematurely retry
        assertThat(importBatchProcessorJob.processNextConfirmedBatch()).isEmpty();

        // Verify row 1 assigned to class and STUDENT role
        UserAccount row1User = userAccountRepository.findActiveByUsernameNormalized("kc.r1." + sfx).orElseThrow();
        Integer membership = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member WHERE class_id = ? AND user_id = ?",
                Integer.class,
                classId,
                row1User.getId());
        assertThat(membership).isEqualTo(1);

        // Recover Keycloak, advance retry window, and run dispatcher -> MUST process ONLY row 3!
        reset(keycloakClient);
        clearInvocations(keycloakClient);
        jdbcTemplate.update(
                "UPDATE identity.import_batch SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                batchId);

        dispatcherWorker().runScheduledImportBatches();

        verify(keycloakClient, times(1)).createUser(argThat(cmd ->
                cmd != null && failUsername.equalsIgnoreCase(cmd.username())));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdRows").value(5))
                .andExpect(jsonPath("$.failedRows").value(0));

        assertThat(readResultCell(batchId, 1, 5)).isNotBlank();
        assertThat(readResultCell(batchId, 2, 5)).isEmpty();
        assertThat(readResultCell(batchId, 3, 5)).isNotBlank();
        assertThat(readResultCell(batchId, 4, 5)).isEmpty();
        assertThat(readResultCell(batchId, 5, 5)).isNotBlank();

        // R1-5 + R1-17 Part A: Pending retry of an explicit-password row preserves user's intended password
        String expRetryOk = "exp.retry.ok." + sfx;
        String expRetryTarget = "exp.retry.tgt." + sfx;
        String intendedPwd = "IntendedPwd@99";
        UUID explicitRetryBatchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {expRetryOk, "Valid@1234", "Explicit OK", "", "", ""},
                        new String[] {expRetryTarget, intendedPwd, "Explicit Retry", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(explicitRetryBatchId, "exp-retry-" + sfx);

        doAnswer(invocation -> {
            KeycloakIdentityClient.CreateUserCommand cmd = invocation.getArgument(0);
            if (expRetryTarget.equalsIgnoreCase(cmd.username())) {
                throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", "Transient KC fail", null);
            }
            return invocation.callRealMethod();
        }).when(keycloakClient).createUser(any());

        dispatcherWorker().runScheduledImportBatches();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM identity.import_batch WHERE id = ?",
                Short.class,
                explicitRetryBatchId)).isEqualTo(ImportBatchStatus.PROCESSING.code());
        String pendingRaw = jdbcTemplate.queryForObject(
                "SELECT raw::text FROM identity.import_row WHERE batch_id = ? AND row_no = 3",
                String.class,
                explicitRetryBatchId);
        assertThat(pendingRaw).doesNotContain(intendedPwd);
        assertThat(objectMapper.readTree(pendingRaw).path("passwordEnc").asText()).isNotBlank();

        reset(keycloakClient);
        jdbcTemplate.update(
                "UPDATE identity.import_batch SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                explicitRetryBatchId);
        dispatcherWorker().runScheduledImportBatches();
        assertLoginSucceeds(expRetryTarget, intendedPwd);

        // R1-5 + R1-17 Part B: Exhausted retryable failure on explicit-password row -> terminal COMPLETED_WITH_ERRORS,
        // source gone, and EVERY terminal row has zero password material in raw or generated_password_enc.
        String termPwd1 = "TermPass@111";
        String termPwd2 = "TermFail@222";
        String termFailUser = "exp.term.fail." + sfx;
        UUID exhaustedBatchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {"exp.term.ok." + sfx, termPwd1, "Terminal OK", "", "", ""},
                        new String[] {termFailUser, termPwd2, "Terminal Fail", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(exhaustedBatchId, "exp-term-" + sfx);

        doAnswer(invocation -> {
            KeycloakIdentityClient.CreateUserCommand cmd = invocation.getArgument(0);
            if (termFailUser.equalsIgnoreCase(cmd.username())) {
                throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", "Persistent KC outage", null);
            }
            return invocation.callRealMethod();
        }).when(keycloakClient).createUser(any());

        for (int attempt = 1; attempt <= 3; attempt++) {
            if (attempt > 1) {
                jdbcTemplate.update(
                        "UPDATE identity.import_batch SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                        exhaustedBatchId);
            }
            dispatcherWorker().runScheduledImportBatches();
        }
        reset(keycloakClient);

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", exhaustedBatchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.createdRows").value(1))
                .andExpect(jsonPath("$.failedRows").value(1));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/errors", exhaustedBatchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_SOURCE_GONE"));

        List<Map<String, Object>> terminalRows = jdbcTemplate.queryForList(
                "SELECT raw::text AS raw_text, generated_password_enc FROM identity.import_row WHERE batch_id = ?",
                exhaustedBatchId);
        assertThat(terminalRows).hasSize(2);
        for (Map<String, Object> r : terminalRows) {
            String rawJson = (String) r.get("raw_text");
            JsonNode node = objectMapper.readTree(rawJson);
            assertThat(node.path("passwordEnc").isNull() || node.path("passwordEnc").isMissingNode()).isTrue();
            assertThat(rawJson).doesNotContain(termPwd1).doesNotContain(termPwd2);
            assertThat(r.get("generated_password_enc")).isNull();
        }
        assertThat(readResultCell(exhaustedBatchId, 1, 5)).isEmpty();
        assertThat(readResultCell(exhaustedBatchId, 2, 5)).isEmpty();
    }

    @Test
    void crashAfterAccountActivationBeforeRowCheckpoint_recoversExactAccountWithoutDuplicate() throws Exception {
        String sfx = suffix();
        String u1 = "r1.2.u1." + sfx;
        String u2 = "r1.2.u2." + sfx;
        UUID batchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {u1, "", "Crash Activate 1", "", "", ""},
                        new String[] {u2, "Valid@1234", "Crash Activate 2", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchId, "r1-2-" + sfx);

        AtomicBoolean crashedOnce = new AtomicBoolean(false);
        doAnswer(invocation -> {
            ImportRow row = invocation.getArgument(0);
            if (row != null && row.getRowNo() == 2 && row.getStatus() == ImportRowStatus.CREATED
                    && crashedOnce.compareAndSet(false, true)) {
                throw new SimulatedWorkerCrashError("Simulated crash after account activation before row checkpoint");
            }
            return invocation.callRealMethod();
        }).when(importRowRepository).saveAndFlush(any());

        try {
            dispatcherWorker().runScheduledImportBatches();
        } catch (SimulatedWorkerCrashError expected) {
            // Simulated abrupt worker death after UserAdminService committed ACTIVE account
        }
        reset(importRowRepository);

        // User account 1 is already ACTIVE in DB & Keycloak, while import_row 2 is still VALID (uncheckpointed)
        UserAccount activeUser1 = userAccountRepository.findActiveByUsernameNormalized(u1).orElseThrow();
        assertThat(activeUser1.getStatus()).isEqualTo(UserStatus.ACTIVE);
        ImportRow uncheckpointedRow1 = importRowRepository.findByBatchIdOrderByRowNoAsc(batchId).getFirst();
        assertThat(uncheckpointedRow1.getStatus()).isEqualTo(ImportRowStatus.VALID);
        assertThat(uncheckpointedRow1.getCreatedUserId()).isNull();
        assertThat(uncheckpointedRow1.getProvisionalUserId()).isNotNull();

        // Expire lease and recover through production dispatcher
        jdbcTemplate.update(
                "UPDATE identity.import_batch SET lease_expires_at = now() - interval '1 minute' WHERE id = ?",
                batchId);
        clearInvocations(keycloakClient);

        dispatcherWorker().runScheduledImportBatches();

        // Must NOT call Keycloak createUser again for u1; only u2 is created
        verify(keycloakClient, times(1)).createUser(any());
        verify(keycloakClient, times(1)).createUser(argThat(cmd -> cmd != null && u2.equalsIgnoreCase(cmd.username())));

        Integer u1Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity.user_account WHERE username_normalized = ? AND deleted_at IS NULL",
                Integer.class,
                u1.toLowerCase());
        assertThat(u1Count).isEqualTo(1);

        List<ImportRow> finalRows = importRowRepository.findByBatchIdOrderByRowNoAsc(batchId);
        assertThat(finalRows.get(0).getStatus()).isEqualTo(ImportRowStatus.CREATED);
        assertThat(finalRows.get(0).getCreatedUserId()).isEqualTo(activeUser1.getId());
        assertThat(finalRows.get(1).getStatus()).isEqualTo(ImportRowStatus.CREATED);

        String recoveredPwd = readResultCell(batchId, 1, 5);
        assertThat(recoveredPwd).isNotBlank();
        assertLoginSucceeds(u1, recoveredPwd);
    }

    @Test
    void crashAfterGeneratedRowCreatedAndResultUploadFailure_preservesExactGeneratedPassword() throws Exception {
        String sfx = suffix();
        // Part A: crash after row 1 CREATED before row 2 & before result upload
        String u1 = "r1.3.a1." + sfx;
        String u2 = "r1.3.a2." + sfx;
        UUID batchA = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {u1, "", "Gen Crash 1", "", "", ""},
                        new String[] {u2, "", "Gen Crash 2", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchA, "r1-3-a-" + sfx);

        doAnswer(invocation -> {
            KeycloakIdentityClient.CreateUserCommand cmd = invocation.getArgument(0);
            if (u2.equalsIgnoreCase(cmd.username())) {
                throw new SimulatedWorkerCrashError("Simulated crash before row 2");
            }
            return invocation.callRealMethod();
        }).when(keycloakClient).createUser(any());

        try {
            dispatcherWorker().runScheduledImportBatches();
        } catch (SimulatedWorkerCrashError expected) {
            // Simulated crash
        }
        reset(keycloakClient);

        // Row 1 is CREATED with raw.passwordEnc == null, generated_password_enc != null
        String rawRow1 = jdbcTemplate.queryForObject(
                "SELECT raw::text FROM identity.import_row WHERE batch_id = ? AND row_no = 2",
                String.class,
                batchA);
        assertThat(objectMapper.readTree(rawRow1).path("passwordEnc").isNull()).isTrue();
        String stagedEnc = jdbcTemplate.queryForObject(
                "SELECT generated_password_enc FROM identity.import_row WHERE batch_id = ? AND row_no = 2",
                String.class,
                batchA);
        assertThat(stagedEnc).isNotBlank();

        jdbcTemplate.update(
                "UPDATE identity.import_batch SET lease_expires_at = now() - interval '1 minute' WHERE id = ?",
                batchA);
        dispatcherWorker().runScheduledImportBatches();

        // After completion, generated_password_enc is wiped and exact password authenticates
        Integer remainingStaged = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity.import_row WHERE batch_id = ? AND generated_password_enc IS NOT NULL",
                Integer.class,
                batchA);
        assertThat(remainingStaged).isZero();
        String pwdA1 = readResultCell(batchA, 1, 5);
        String pwdA2 = readResultCell(batchA, 2, 5);
        assertLoginSucceeds(u1, pwdA1);
        assertLoginSucceeds(u2, pwdA2);

        // Part B: Result upload fails -> retries cleanly without recreating user and keeps exact password
        String u3 = "r1.3.b1." + sfx;
        UUID batchB = uploadBatch(
                buildImportXlsx(List.<String[]>of(new String[] {u3, "", "Upload Fail Gen", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchB, "r1-3-b-" + sfx);

        AtomicBoolean failResultPut = new AtomicBoolean(true);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key != null && key.endsWith("/result.xlsx") && failResultPut.compareAndSet(true, false)) {
                throw new ExternalServiceException("INTERNAL_ERROR", "Simulated S3 upload outage", null);
            }
            return invocation.callRealMethod();
        }).when(encryptedObjectStorage).putEncrypted(any(), any());

        dispatcherWorker().runScheduledImportBatches();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM identity.import_batch WHERE id = ?",
                Short.class,
                batchB)).isEqualTo(ImportBatchStatus.PROCESSING.code());

        reset(encryptedObjectStorage);
        clearInvocations(keycloakClient);
        jdbcTemplate.update(
                "UPDATE identity.import_batch SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                batchB);
        dispatcherWorker().runScheduledImportBatches();

        verify(keycloakClient, times(0)).createUser(any());
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchB)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        String pwdB = readResultCell(batchB, 1, 5);
        assertLoginSucceeds(u3, pwdB);
    }

    @Test
    void importDoesNotDeleteForeignProvisioningAccount_andFailsRowWithUsernameTaken() throws Exception {
        String sfx = suffix();
        String sharedUsername = "r1.6.shared." + sfx;
        String interactiveFullName = "Interactive Original Owner";
        String interactivePwd = "Interactive@1234";
        UUID batchId = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {sharedUsername, "Import@1234", "Import Overwrite Attempt", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchId, "r1-6-" + sfx);

        CountDownLatch kcUserCreated = new CountDownLatch(1);
        CountDownLatch releaseSetPassword = new CountDownLatch(1);
        doAnswer(invocation -> {
            kcUserCreated.countDown();
            assertThat(releaseSetPassword.await(15, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(keycloakClient).setPassword(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MvcResult> interactiveFuture = executor.submit(() -> mockMvc.perform(post("/api/v1/admin/users")
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "fullName": "%s"
                                }
                                """.formatted(sharedUsername, interactivePwd, interactiveFullName)))
                .andReturn());

        try {
            assertThat(kcUserCreated.await(15, TimeUnit.SECONDS)).isTrue();

            // Interactive create is paused in PROVISIONING with a real Keycloak user.
            // Run the confirmed import for the exact same username.
            dispatcherWorker().runScheduledImportBatches();
            // Release paused interactive create
            releaseSetPassword.countDown();
            MvcResult interactiveRes = interactiveFuture.get(15, TimeUnit.SECONDS);
            assertThat(interactiveRes.getResponse().getStatus()).isEqualTo(201);

            UUID interactiveId = UUID.fromString(
                    objectMapper.readTree(interactiveRes.getResponse().getContentAsString()).get("id").asText());
            createdUserIds.add(interactiveId);
            createdKeycloakUserIds.add(interactiveId);

            // Original interactive account survived untouched and authenticates with its password
            UserAccount persisted = userAccountRepository.findById(interactiveId).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(persisted.getFullName()).isEqualTo(interactiveFullName);
            assertLoginSucceeds(sharedUsername, interactivePwd);

            // Import batch failed that row with stable USERNAME_TAKEN conflict
            mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                            .with(asUser(Permissions.USER_MODIFY)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                    .andExpect(jsonPath("$.createdRows").value(0))
                    .andExpect(jsonPath("$.failedRows").value(1));

            mockMvc.perform(get("/api/v1/admin/import-batches/{id}/rows", batchId)
                            .with(asUser(Permissions.USER_MODIFY)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.items[0].failureCode").value("USERNAME_TAKEN"));
        } finally {
            releaseSetPassword.countDown();
            reset(keycloakClient);
            executor.shutdownNow();
        }
    }

    @Test
    void uploadAndConfirmByDifferentAdmins_auditsConfirmerIdAndUsername() throws Exception {
        String sfx = suffix();
        String uploaderUsername = "r1.7.uploader." + sfx;
        String confirmerUsername = "r1.7.confirmer." + sfx;
        UUID uploaderId = insertActiveUser(uploaderUsername, null);
        UUID confirmerId = insertActiveUser(confirmerUsername, null);
        String studentUsername = "r1.7.stu." + sfx;

        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("diff-admin.xlsx", buildImportXlsx(List.<String[]>of(
                                new String[] {studentUsername, "Valid@1234", "Actor Check Student", "", "", ""}))))
                        .with(asUser(uploaderId, uploaderUsername, Permissions.USER_MODIFY)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID batchId = UUID.fromString(objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("id").asText());
        createdBatchIds.add(batchId);

        String key = "r1-7-" + sfx;
        createdIdempotencyKeys.add(key);
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", key)
                        .with(asUser(confirmerId, confirmerUsername, Permissions.USER_MODIFY)))
                .andExpect(status().isOk());

        dispatcherWorker().runScheduledImportBatches();

        while (outboxRelay.relayBatch() > 0) {
            // drain to audit.audit_event
        }

        UUID createdStudentId = jdbcTemplate.queryForObject(
                "SELECT created_user_id FROM identity.import_row WHERE batch_id = ?",
                UUID.class,
                batchId);

        Map<String, Object> userCreatedAudit = jdbcTemplate.queryForMap(
                "SELECT actor_id, actor_username FROM audit.audit_event WHERE action = 'USER_CREATED' AND target_id = ?",
                createdStudentId.toString());
        assertThat(userCreatedAudit.get("actor_id")).isEqualTo(confirmerId);
        assertThat(userCreatedAudit.get("actor_username")).isEqualTo(confirmerUsername);

        Map<String, Object> batchCompletedAudit = jdbcTemplate.queryForMap(
                "SELECT actor_id, actor_username FROM audit.audit_event WHERE action = 'IMPORT_BATCH_COMPLETED' AND target_id = ?",
                batchId.toString());
        assertThat(batchCompletedAudit.get("actor_id")).isEqualTo(confirmerId);
        assertThat(batchCompletedAudit.get("actor_username")).isEqualTo(confirmerUsername);
    }

    @Test
    void confirmTwoBatchesWithSameIdempotencyKey_returns409SequentiallyAndConcurrently() throws Exception {
        String sfx = suffix();
        UUID batchA = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {"r1.9.a." + sfx, "Valid@1234", "Batch A", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        UUID batchB = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {"r1.9.b." + sfx, "Valid@1234", "Batch B", "", "", ""})),
                false,
                Permissions.USER_MODIFY);

        String seqKey = "r1-9-seq-" + sfx;
        createdIdempotencyKeys.add(seqKey);
        MvcResult confirmA = mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchA)
                        .header("Idempotency-Key", seqKey)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchB)
                        .header("Idempotency-Key", seqKey)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // Batch B stays VALIDATED; replay of Batch A stays identical
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchB)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));

        MvcResult replayA = mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchA)
                        .header("Idempotency-Key", seqKey)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(replayA.getResponse().getContentAsString()).isEqualTo(confirmA.getResponse().getContentAsString());

        // Concurrent cross-batch reuse race
        UUID batchC = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {"r1.9.c." + sfx, "Valid@1234", "Batch C", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        UUID batchD = uploadBatch(
                buildImportXlsx(List.<String[]>of(
                        new String[] {"r1.9.d." + sfx, "Valid@1234", "Batch D", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        String concKey = "r1-9-conc-" + sfx;
        createdIdempotencyKeys.add(concKey);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> callC = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchC)
                                .header("Idempotency-Key", concKey)
                                .with(asUser(Permissions.USER_MODIFY)))
                        .andReturn();
            };
            Callable<MvcResult> callD = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchD)
                                .header("Idempotency-Key", concKey)
                                .with(asUser(Permissions.USER_MODIFY)))
                        .andReturn();
            };
            Future<MvcResult> futC = executor.submit(callC);
            Future<MvcResult> futD = executor.submit(callD);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult resC = futC.get(15, TimeUnit.SECONDS);
            MvcResult resD = futD.get(15, TimeUnit.SECONDS);
            int statusC = resC.getResponse().getStatus();
            int statusD = resD.getResponse().getStatus();
            assertThat(List.of(statusC, statusD)).containsExactlyInAnyOrder(200, 409);
            MvcResult conflictRes = statusC == 409 ? resC : resD;
            assertThat(objectMapper.readTree(conflictRes.getResponse().getContentAsString()).get("code").asText())
                    .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void uploadRateLimit_returns429BeforeWorkbookParsing_andIsolatesClients() throws Exception {
        String sfx = suffix();
        String clientA = "203.0.113.91";
        String clientB = "203.0.113.92";
        int limit = securityProperties.rateLimit().uploadPerIpLimit();
        byte[] xlsx = buildImportXlsx(List.<String[]>of(
                new String[] {"rl.u." + sfx, "Valid@1234", "Rate Limit User", "", "", ""}));

        for (int i = 1; i <= limit; i++) {
            MvcResult res = mockMvc.perform(multipart("/api/v1/admin/import-batches")
                            .file(multipartXlsx("rl-" + i + ".xlsx", xlsx))
                            .with(req -> {
                                req.setRemoteAddr("127.0.0.1");
                                return req;
                            })
                            .header(ClientIpResolver.X_FORWARDED_FOR, clientA)
                            .with(asUser(Permissions.USER_MODIFY)))
                    .andExpect(status().isCreated())
                    .andReturn();
            createdBatchIds.add(UUID.fromString(
                    objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText()));
        }

        int batchesBeforeLimit = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity.import_batch", Integer.class);
        clearInvocations(workbookCodec);

        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("rl-blocked.xlsx", xlsx))
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, clientA)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        verify(workbookCodec, times(0)).parseUploadedWorkbook(any(), any());
        int batchesAfterLimit = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity.import_batch", Integer.class);
        assertThat(batchesAfterLimit).isEqualTo(batchesBeforeLimit);

        // Independent client B is allowed
        MvcResult clientBRes = mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("rl-client-b.xlsx", xlsx))
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        })
                        .header(ClientIpResolver.X_FORWARDED_FOR, clientB)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isCreated())
                .andReturn();
        createdBatchIds.add(UUID.fromString(
                objectMapper.readTree(clientBRes.getResponse().getContentAsString()).get("id").asText()));
    }

    @Test
    void blankPasswordRows_encryptedResult_noSecretsInLogsOrRaw_sourceDeletedAndResultDeletable(CapturedOutput output)
            throws Exception {
        String sfx = suffix();
        String explicitPwd1 = "Secret@1234";
        String explicitPwd2 = "Custom@5678";

        List<String[]> rows = List.of(
                new String[] {"gen.u1." + sfx, "", "Generated One", "g1." + sfx + "@thi360.vn", "", ""},
                new String[] {"exp.u2." + sfx, explicitPwd1, "Explicit Two", "e2." + sfx + "@thi360.vn", "", ""},
                new String[] {"gen.u3." + sfx, "", "Generated Three", "", "", ""},
                new String[] {"exp.u4." + sfx, explicitPwd2, "Explicit Four", "", "", ""}
        );
        UUID batchId = uploadBatch(buildImportXlsx(rows), false, Permissions.USER_MODIFY);
        confirmBatch(batchId, "enc-confirm-" + sfx);

        ImportBatchStatus statusAfter = importBatchProcessorJob.processBatch(batchId);
        assertThat(statusAfter).isEqualTo(ImportBatchStatus.COMPLETED);

        // 1. Source object deleted from ObjectStorage & GET /errors returns 409 IMPORT_SOURCE_GONE
        String sourceKey = ImportBatchService.sourceObjectKey(batchId);
        assertThat(objectStorage.exists(sourceKey)).isFalse();
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/errors", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_SOURCE_GONE"));

        // 2. Stored result object is encrypted (not readable raw as xlsx or plaintext)
        String resultKey = ImportBatchService.resultObjectKey(batchId);
        byte[] rawStoredBytes = ((InMemoryObjectStorage) objectStorage).rawBytes(resultKey).orElseThrow();
        assertThat(rawStoredBytes.length).isGreaterThan(20);
        assertThat(rawStoredBytes[0] == 0x50 && rawStoredBytes[1] == 0x4B).isFalse();
        String rawAsText = new String(rawStoredBytes, StandardCharsets.UTF_8);
        assertThat(rawAsText).doesNotContain("gen.u1." + sfx).doesNotContain(explicitPwd1).doesNotContain(explicitPwd2);

        // 3. Download result -> decrypts properly, records IMPORT_RESULT_DOWNLOADED audit,
        //    only blank-password rows have generated policy-compliant passwords
        clearInvocations(auditRecorder);
        MvcResult dlRes = mockMvc.perform(get("/api/v1/admin/import-batches/{id}/result", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();

        verify(auditRecorder).record(argThat(ev ->
                ev != null && "IMPORT_RESULT_DOWNLOADED".equals(ev.action())));

        String genPwd1;
        String genPwd3;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(dlRes.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            genPwd1 = fmt.formatCellValue(sheet.getRow(1).getCell(5));
            String expOut2 = fmt.formatCellValue(sheet.getRow(2).getCell(5));
            genPwd3 = fmt.formatCellValue(sheet.getRow(3).getCell(5));
            String expOut4 = fmt.formatCellValue(sheet.getRow(4).getCell(5));

            assertThat(genPwd1).isNotBlank();
            assertThat(passwordPolicy.isValid(genPwd1, "gen.u1." + sfx)).isTrue();
            assertThat(expOut2).isEmpty();
            assertThat(genPwd3).isNotBlank();
            assertThat(passwordPolicy.isValid(genPwd3, "gen.u3." + sfx)).isTrue();
            assertThat(expOut4).isEmpty();
        }

        // 4. import_row.raw in DB has NO password on any row after completion
        List<Map<String, Object>> dbRows = jdbcTemplate.queryForList(
                "SELECT raw::text AS raw_text, generated_password_enc FROM identity.import_row WHERE batch_id = ?",
                batchId);
        assertThat(dbRows).hasSize(4);
        for (Map<String, Object> dbRow : dbRows) {
            String rawJson = (String) dbRow.get("raw_text");
            JsonNode node = objectMapper.readTree(rawJson);
            assertThat(node.path("passwordEnc").isNull() || node.path("passwordEnc").isMissingNode()).isTrue();
            assertThat(rawJson).doesNotContain(explicitPwd1).doesNotContain(explicitPwd2);
            assertThat(dbRow.get("generated_password_enc")).isNull();
        }

        // 5. Logs contain NO passwords (neither user-supplied nor system-generated)
        String allLogs = output.getOut() + output.getErr();
        assertThat(allLogs)
                .doesNotContain(explicitPwd1)
                .doesNotContain(explicitPwd2)
                .doesNotContain(genPwd1)
                .doesNotContain(genPwd3);

        // 6. R1-14: Persisted audit outbox AND relayed audit_event payloads contain expected actions and NO secrets
        List<String> createdUserIdsForBatch = jdbcTemplate.queryForList(
                "SELECT created_user_id::text FROM identity.import_row WHERE batch_id = ? AND created_user_id IS NOT NULL",
                String.class,
                batchId);
        List<String> targetIds = new ArrayList<>();
        targetIds.add(batchId.toString());
        targetIds.addAll(createdUserIdsForBatch);

        List<String> outboxPayloads = jdbcTemplate.queryForList(
                "SELECT payload::text FROM shared.outbox_event WHERE topic = 'audit' AND payload->>'targetId' = ANY (?)",
                String.class,
                (Object) targetIds.toArray(String[]::new));
        assertThat(outboxPayloads).isNotEmpty();
        assertThat(String.join("\n", outboxPayloads))
                .contains("IMPORT_BATCH_UPLOADED")
                .contains("IMPORT_BATCH_CONFIRMED")
                .contains("USER_CREATED")
                .contains("IMPORT_BATCH_COMPLETED")
                .contains("IMPORT_RESULT_DOWNLOADED")
                .doesNotContain(explicitPwd1)
                .doesNotContain(explicitPwd2)
                .doesNotContain(genPwd1)
                .doesNotContain(genPwd3)
                .doesNotContain("passwordEnc");

        while (outboxRelay.relayBatch() > 0) {
            // drain
        }

        List<Map<String, Object>> persistedAudits = jdbcTemplate.queryForList(
                "SELECT action, before::text AS before_txt, after::text AS after_txt, reason FROM audit.audit_event WHERE target_id = ANY (?)",
                (Object) targetIds.toArray(String[]::new));
        assertThat(persistedAudits).isNotEmpty();
        for (Map<String, Object> ev : persistedAudits) {
            String combined = ev.get("before_txt") + "|" + ev.get("after_txt") + "|" + ev.get("reason");
            assertThat(combined)
                    .doesNotContain(explicitPwd1)
                    .doesNotContain(explicitPwd2)
                    .doesNotContain(genPwd1)
                    .doesNotContain(genPwd3)
                    .doesNotContain("passwordEnc");
        }

        // 7. DELETE /{id}/result deletes object, records audit, and subsequent GET -> 404
        clearInvocations(auditRecorder);
        mockMvc.perform(delete("/api/v1/admin/import-batches/{id}/result", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isNoContent());

        assertThat(objectStorage.exists(resultKey)).isFalse();
        verify(auditRecorder).record(argThat(ev ->
                ev != null && "IMPORT_RESULT_DELETED".equals(ev.action())));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/result", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isNotFound());
    }


    @Test
    void staleWorkerResumingAfterLeaseTakeover_touchesNothingAndLeavesWinnerOutcomeIntact() throws Exception {
        String sfx = suffix();
        // Part A: Worker A pauses immediately after Keycloak setPassword returns (before Tx2 promotion);
        // lease expires, Worker B recovers and completes, then A resumes.
        String u1 = "r2.1.a." + sfx;
        UUID batchA = uploadBatch(
                buildImportXlsx(List.<String[]>of(new String[] {u1, "", "Lease Fence A", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchA, "r2-1-a-" + sfx);

        CountDownLatch aPausedAfterSetPwd = new CountDownLatch(1);
        CountDownLatch releaseWorkerA = new CountDownLatch(1);
        AtomicBoolean firstSetPwdPaused = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Object res = invocation.callRealMethod();
            if (firstSetPwdPaused.compareAndSet(false, true)) {
                aPausedAfterSetPwd.countDown();
                assertThat(releaseWorkerA.await(20, TimeUnit.SECONDS)).isTrue();
            }
            return res;
        }).when(keycloakClient).setPassword(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> workerAFuture = executor.submit(() -> dispatcherWorker().runScheduledImportBatches());
        try {
            assertThat(aPausedAfterSetPwd.await(15, TimeUnit.SECONDS)).isTrue();

            // Expire A's lease -> Worker B claims with a new lease_token and completes batchA
            jdbcTemplate.update(
                    "UPDATE identity.import_batch SET lease_expires_at = now() - interval '1 minute' WHERE id = ?",
                    batchA);
            dispatcherWorker().runScheduledImportBatches();

            Map<String, Object> batchBeforeResume = jdbcTemplate.queryForMap(
                    "SELECT status, created_rows, failed_rows, result_object_key, source_deleted_at FROM identity.import_batch WHERE id = ?",
                    batchA);
            assertThat(((Number) batchBeforeResume.get("status")).shortValue()).isEqualTo(ImportBatchStatus.COMPLETED.code());
            assertThat(((Number) batchBeforeResume.get("created_rows")).intValue()).isEqualTo(1);
            assertThat(((Number) batchBeforeResume.get("failed_rows")).intValue()).isZero();
            assertThat(batchBeforeResume.get("result_object_key")).isNotNull();

            UUID bCreatedUserId = jdbcTemplate.queryForObject(
                    "SELECT created_user_id FROM identity.import_row WHERE batch_id = ?",
                    UUID.class,
                    batchA);
            assertThat(bCreatedUserId).isNotNull();
            String bPwd = readResultCell(batchA, 1, 5);
            assertThat(bPwd).isNotBlank();
            byte[] bResultBytes = ((InMemoryObjectStorage) objectStorage)
                    .rawBytes(ImportBatchService.resultObjectKey(batchA))
                    .orElseThrow();

            // Release stale worker A: must stop cleanly without overwriting row, result, batch, or ACTIVE user
            releaseWorkerA.countDown();
            workerAFuture.get(15, TimeUnit.SECONDS);
            reset(keycloakClient);

            Map<String, Object> batchAfterResume = jdbcTemplate.queryForMap(
                    "SELECT status, created_rows, failed_rows, result_object_key, source_deleted_at FROM identity.import_batch WHERE id = ?",
                    batchA);
            assertThat(((Number) batchAfterResume.get("status")).shortValue()).isEqualTo(ImportBatchStatus.COMPLETED.code());
            assertThat(((Number) batchAfterResume.get("created_rows")).intValue()).isEqualTo(1);
            assertThat(((Number) batchAfterResume.get("failed_rows")).intValue()).isZero();

            Map<String, Object> rowAfterResume = jdbcTemplate.queryForMap(
                    "SELECT status, created_user_id, failure_code, generated_password_enc FROM identity.import_row WHERE batch_id = ?",
                    batchA);
            assertThat(((Number) rowAfterResume.get("status")).shortValue()).isEqualTo(ImportRowStatus.CREATED.code());
            assertThat(rowAfterResume.get("created_user_id")).isEqualTo(bCreatedUserId);
            assertThat(rowAfterResume.get("failure_code")).isNull();
            assertThat(rowAfterResume.get("generated_password_enc")).isNull();

            List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
                    "SELECT id, status FROM identity.user_account WHERE username_normalized = ? AND deleted_at IS NULL",
                    u1.toLowerCase());
            assertThat(accounts).hasSize(1);
            assertThat(accounts.getFirst().get("id")).isEqualTo(bCreatedUserId);
            assertThat(((Number) accounts.getFirst().get("status")).shortValue()).isEqualTo(UserStatus.ACTIVE.code());

            byte[] afterResultBytes = ((InMemoryObjectStorage) objectStorage)
                    .rawBytes(ImportBatchService.resultObjectKey(batchA))
                    .orElseThrow();
            assertThat(afterResultBytes).isEqualTo(bResultBytes);
            assertThat(readResultCell(batchA, 1, 5)).isEqualTo(bPwd);
            assertLoginSucceeds(u1, bPwd);
        } finally {
            releaseWorkerA.countDown();
            reset(keycloakClient);
            executor.shutdownNow();
        }

        // Part B: Worker A pauses right before result upload (inside buildResultWorkbook); B completes, A resumes ->
        // A never overwrites result.xlsx in storage or finalizes batch.
        String u2 = "r2.1.b." + sfx;
        UUID batchB = uploadBatch(
                buildImportXlsx(List.<String[]>of(new String[] {u2, "", "Lease Fence B", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchB, "r2-1-b-" + sfx);

        CountDownLatch aPausedBeforeResultWrite = new CountDownLatch(1);
        CountDownLatch releaseWorkerAB = new CountDownLatch(1);
        AtomicBoolean pausedResultBuildOnce = new AtomicBoolean(false);
        AtomicInteger resultPutCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            Object res = invocation.callRealMethod();
            if (pausedResultBuildOnce.compareAndSet(false, true)) {
                aPausedBeforeResultWrite.countDown();
                assertThat(releaseWorkerAB.await(20, TimeUnit.SECONDS)).isTrue();
            }
            return res;
        }).when(workbookCodec).buildResultWorkbook(any(), any(), any());
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key != null && key.endsWith("/result.xlsx")) {
                resultPutCount.incrementAndGet();
            }
            return invocation.callRealMethod();
        }).when(encryptedObjectStorage).putEncrypted(any(), any());

        ExecutorService executorB = Executors.newSingleThreadExecutor();
        Future<?> workerBFuture = executorB.submit(() -> dispatcherWorker().runScheduledImportBatches());
        try {
            assertThat(aPausedBeforeResultWrite.await(15, TimeUnit.SECONDS)).isTrue();

            jdbcTemplate.update(
                    "UPDATE identity.import_batch SET lease_expires_at = now() - interval '1 minute' WHERE id = ?",
                    batchB);
            dispatcherWorker().runScheduledImportBatches();
            assertThat(resultPutCount.get()).isEqualTo(1);
            byte[] winnerResultBytes = ((InMemoryObjectStorage) objectStorage)
                    .rawBytes(ImportBatchService.resultObjectKey(batchB))
                    .orElseThrow();

            releaseWorkerAB.countDown();
            workerBFuture.get(15, TimeUnit.SECONDS);

            // Stale worker A must NOT have written result.xlsx after B won
            assertThat(resultPutCount.get()).isEqualTo(1);
            assertThat(((InMemoryObjectStorage) objectStorage)
                    .rawBytes(ImportBatchService.resultObjectKey(batchB))
                    .orElseThrow()).isEqualTo(winnerResultBytes);
            String b2Pwd = readResultCell(batchB, 1, 5);
            assertLoginSucceeds(u2, b2Pwd);
        } finally {
            releaseWorkerAB.countDown();
            reset(workbookCodec, encryptedObjectStorage);
            executorB.shutdownNow();
        }
    }

    @Test
    void persistentResultStorageFailureBeyondMaxAttempts_keepsStagedGeneratedPasswords_andRestoresExactResult()
            throws Exception {
        String sfx = suffix();
        String genUser = "r2.2.gen." + sfx;
        String expUser = "r2.2.exp." + sfx;
        String explicitPwd = "ExplicitPass@22";
        UUID batchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {genUser, "", "Result Exhaustion Gen", "", "", ""},
                        new String[] {expUser, explicitPwd, "Result Exhaustion Exp", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchId, "r2-2-" + sfx);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key != null && key.endsWith("/result.xlsx")) {
                throw new ExternalServiceException("INTERNAL_ERROR", "Persistent result storage outage", null);
            }
            return invocation.callRealMethod();
        }).when(encryptedObjectStorage).putEncrypted(any(), any());

        // Exceed maxAttempts (3) with 5 consecutive result-write failures
        for (int attempt = 1; attempt <= 5; attempt++) {
            if (attempt > 1) {
                jdbcTemplate.update(
                        "UPDATE identity.import_batch SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                        batchId);
            }
            dispatcherWorker().runScheduledImportBatches();
        }
        reset(encryptedObjectStorage);

        Map<String, Object> stalledBatch = jdbcTemplate.queryForMap(
                "SELECT status, created_rows, failed_rows, result_object_key, result_retry_count FROM identity.import_batch WHERE id = ?",
                batchId);
        assertThat(((Number) stalledBatch.get("status")).shortValue()).isEqualTo(ImportBatchStatus.PROCESSING.code());
        assertThat(((Number) stalledBatch.get("created_rows")).intValue()).isEqualTo(2);
        assertThat(((Number) stalledBatch.get("failed_rows")).intValue()).isZero();
        assertThat(stalledBatch.get("result_object_key")).isNull();
        assertThat(((Number) stalledBatch.get("result_retry_count")).intValue()).isEqualTo(5);

        // Staged generated password MUST still be preserved; explicit row secret MUST already be wiped
        String stagedGenEnc = jdbcTemplate.queryForObject(
                "SELECT generated_password_enc FROM identity.import_row WHERE batch_id = ? AND row_no = 2",
                String.class,
                batchId);
        assertThat(stagedGenEnc).isNotBlank();
        String expRawJson = jdbcTemplate.queryForObject(
                "SELECT raw::text FROM identity.import_row WHERE batch_id = ? AND row_no = 3",
                String.class,
                batchId);
        JsonNode expRawNode = objectMapper.readTree(expRawJson);
        assertThat(expRawNode.path("passwordEnc").isNull() || expRawNode.path("passwordEnc").isMissingNode()).isTrue();
        assertThat(expRawJson).doesNotContain(explicitPwd);

        // Restore storage and recover via production dispatcher -> zero extra Keycloak creations
        clearInvocations(keycloakClient);
        jdbcTemplate.update(
                "UPDATE identity.import_batch SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                batchId);
        dispatcherWorker().runScheduledImportBatches();

        verify(keycloakClient, times(0)).createUser(any());
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdRows").value(2))
                .andExpect(jsonPath("$.failedRows").value(0))
                .andExpect(jsonPath("$.resultAvailable").value(true))
                .andExpect(jsonPath("$.sourceAvailable").value(false));

        Integer remainingStaged = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity.import_row WHERE batch_id = ? AND generated_password_enc IS NOT NULL",
                Integer.class,
                batchId);
        assertThat(remainingStaged).isZero();

        String deliveredGenPwd = readResultCell(batchId, 1, 5);
        assertThat(deliveredGenPwd).isNotBlank();
        assertThat(readResultCell(batchId, 2, 5)).isEmpty();
        assertLoginSucceeds(genUser, deliveredGenPwd);
        assertLoginSucceeds(expUser, explicitPwd);
    }

    @Test
    void sourceDeletionFailure_retainsSourceKeyUntilCleanupSweepSucceeds() throws Exception {
        String sfx = suffix();
        String u1 = "r2.3.u." + sfx;
        UUID batchId = uploadBatch(
                buildImportXlsx(List.<String[]>of(new String[] {u1, "Valid@1234", "Source Delete Retry", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        confirmBatch(batchId, "r2-3-" + sfx);
        String sourceKey = ImportBatchService.sourceObjectKey(batchId);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (sourceKey.equals(key)) {
                throw new ExternalServiceException("INTERNAL_ERROR", "Transient source delete outage", null);
            }
            return invocation.callRealMethod();
        }).when(encryptedObjectStorage).delete(any());

        dispatcherWorker().runScheduledImportBatches();

        // Batch completes and API marks source unavailable, while DB retains file_object_key & null source_deleted_at
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourceAvailable").value(false));
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/errors", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_SOURCE_GONE"));

        assertThat(objectStorage.exists(sourceKey)).isTrue();
        Map<String, Object> pendingDb = jdbcTemplate.queryForMap(
                "SELECT file_object_key, source_deleted_at FROM identity.import_batch WHERE id = ?",
                batchId);
        assertThat(pendingDb.get("file_object_key")).isEqualTo(sourceKey);
        assertThat(pendingDb.get("source_deleted_at")).isNull();

        // Restore storage and run worker -> sweep deletes orphan source and records source_deleted_at
        reset(encryptedObjectStorage);
        dispatcherWorker().runScheduledImportBatches();

        assertThat(objectStorage.exists(sourceKey)).isFalse();
        Map<String, Object> cleanedDb = jdbcTemplate.queryForMap(
                "SELECT file_object_key, source_deleted_at FROM identity.import_batch WHERE id = ?",
                batchId);
        assertThat(cleanedDb.get("file_object_key")).isNull();
        assertThat(cleanedDb.get("source_deleted_at")).isNotNull();
        assertThat(importBatchProcessorJob.sweepPendingSourceDeletions()).isZero();
    }
    @Test
    void upload_rejectsOver2000Rows_formulas_macros_zipBomb_andNonZip() throws Exception {
        int batchesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity.import_batch", Integer.class);

        byte[] over2000 = buildLargeImportXlsx(2_001);
        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("too-many-rows.xlsx", over2000))
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_ROW_LIMIT_EXCEEDED"));

        byte[] withFormula;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Users");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Tên đăng nhập*");
            h.createCell(2).setCellValue("Họ và tên*");
            Row r = s.createRow(1);
            r.createCell(0).setCellFormula("CONCAT(\"a\",\"b\")");
            r.createCell(2).setCellValue("Formula User");
            wb.write(out);
            withFormula = out.toByteArray();
        }
        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("formula.xlsx", withFormula))
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FORMULA_NOT_ALLOWED"));

        // R1-16: .xlsm macro filename
        byte[] validXlsx = buildImportXlsx(List.<String[]>of(
                new String[] {"macro.u." + suffix(), "Valid@1234", "Macro User", "", "", ""}));
        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(new MockMultipartFile(
                                "file",
                                "macro.xlsm",
                                "application/vnd.ms-excel.sheet.macroEnabled.12",
                                validXlsx))
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_MACRO_NOT_ALLOWED"));

        // R1-16: .xlsx disguise with embedded xl/vbaProject.bin
        byte[] disguisedMacro = injectZipEntry(validXlsx, "xl/vbaProject.bin", "fake-vba-bytes".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("disguised-macro.xlsx", disguisedMacro))
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_MACRO_NOT_ALLOWED"));

        // R1-16: Zip bomb (valid OOXML with xl/sharedStrings.xml expanded beyond ZipSecureFile 10 MiB entry cap)
        byte[] zipBomb = buildValidOoxmlSharedStringsBomb(validXlsx);
        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("bomb.xlsx", zipBomb))
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID_FILE"));

        // R1-16: Non-zip payload
        mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("notzip.xlsx", "not-a-zip".getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID_FILE"));

        int batchesAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity.import_batch", Integer.class);
        assertThat(batchesAfter).isEqualTo(batchesBefore);
    }

    private ImportBatchProcessorJob.ImportBatchProcessorWorker dispatcherWorker() {
        return new ImportBatchProcessorJob.ImportBatchProcessorWorker(importBatchProcessorJob);
    }

    private void confirmBatch(UUID batchId, String idempotencyKey, String... permissions) throws Exception {
        createdIdempotencyKeys.add(idempotencyKey);
        String[] effectivePermissions = permissions.length == 0
                ? new String[] {Permissions.USER_MODIFY}
                : permissions;
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", idempotencyKey)
                        .with(asUser(effectivePermissions)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private String readResultCell(UUID batchId, int dataRowIndex1Based, int cellIndex) throws Exception {
        MvcResult resultRes = mockMvc.perform(get("/api/v1/admin/import-batches/{id}/result", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resultRes.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            return fmt.formatCellValue(sheet.getRow(dataRowIndex1Based).getCell(cellIndex));
        }
    }

    private void assertLoginSucceeds(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", password))))
                .andExpect(status().isOk());
    }

    private void clearRateLimitKeys() {
        redisTemplate.delete(List.of(
                RateLimitFilter.UPLOAD_KEY_PREFIX + "127.0.0.1",
                RateLimitFilter.UPLOAD_KEY_PREFIX + "203.0.113.91",
                RateLimitFilter.UPLOAD_KEY_PREFIX + "203.0.113.92",
                RateLimitFilter.KEY_PREFIX + "127.0.0.1",
                RateLimitFilter.KEY_PREFIX + "203.0.113.99"));
    }

    private static byte[] injectZipEntry(byte[] baseZipBytes, String entryName, byte[] entryContent) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(baseZipBytes));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry entry;
            boolean replaced = false;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals(entryName)) {
                    zout.write(entryContent);
                    replaced = true;
                } else {
                    zin.transferTo(zout);
                }
                zout.closeEntry();
            }
            if (!replaced) {
                zout.putNextEntry(new ZipEntry(entryName));
                zout.write(entryContent);
                zout.closeEntry();
            }
            zout.finish();
            return out.toByteArray();
        }
    }

    private static byte[] buildValidOoxmlSharedStringsBomb(byte[] baseXlsx) throws IOException {
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random rng = new Random(42L);
        StringBuilder commentPayload = new StringBuilder(11 * 1024 * 1024 + 512 * 1024);
        commentPayload.append("<!-- ");
        for (int i = 0; i < 250 * 1024; i++) {
            commentPayload.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        commentPayload.append("A".repeat(11 * 1024 * 1024 + 300 * 1024));
        commentPayload.append(" -->");

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(baseXlsx));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putNextEntry(new ZipEntry(entry.getName()));
                if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    String originalXml = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                    int closeIdx = originalXml.lastIndexOf("</sst>");
                    assertThat(closeIdx).isPositive();
                    String expandedXml = originalXml.substring(0, closeIdx)
                            + commentPayload
                            + originalXml.substring(closeIdx);
                    zout.write(expandedXml.getBytes(StandardCharsets.UTF_8));
                } else {
                    zin.transferTo(zout);
                }
                zout.closeEntry();
            }
            zout.finish();
            return out.toByteArray();
        }
    }

    private static final class SimulatedWorkerCrashError extends Error {
        SimulatedWorkerCrashError(String message) {
            super(message);
        }
    }

    private UUID uploadBatch(byte[] xlsxBytes, boolean mustChangePassword, String... permissions) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/v1/admin/import-batches")
                        .file(multipartXlsx("import.xlsx", xlsxBytes))
                        .param("mustChangePassword", Boolean.toString(mustChangePassword))
                        .with(asUser(permissions)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdBatchIds.add(id);
        return id;
    }

    private static void assertRowHasError(JsonNode rowNode, int expectedRowNo, String expectedColumn, String expectedCode) {
        assertThat(rowNode.get("rowNo").asInt()).isEqualTo(expectedRowNo);
        JsonNode errors = rowNode.get("errors");
        boolean matched = false;
        for (JsonNode err : errors) {
            if (err.get("rowNo").asInt() == expectedRowNo
                    && expectedColumn.equals(err.get("column").asText())
                    && expectedCode.equals(err.get("code").asText())
                    && !err.get("message").asText().isBlank()) {
                matched = true;
                break;
            }
        }
        assertThat(matched)
                .as("Expected row %d column '%s' error code '%s' in %s", expectedRowNo, expectedColumn, expectedCode, errors)
                .isTrue();
    }

    private UUID insertActiveUser(String username, String email) {
        UUID id = Ids.newId();
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String code = String.format("U%06d", seq == null ? 1L : seq);
        UserAccount account = new UserAccount(
                id,
                code,
                username,
                "Existing " + username,
                email,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                Instant.now(),
                id);
        userAccountRepository.saveAndFlush(account);
        createdUserIds.add(id);
        return id;
    }

    private UUID createTestClass(String code, String name, boolean active) {
        UUID id = Ids.newId();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO classroom.class_room
                    (id, code, code_normalized, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """,
                id,
                code,
                code.trim().toLowerCase(),
                name,
                active ? (short) 1 : (short) 2,
                Timestamp.from(now),
                Timestamp.from(now));
        createdClassIds.add(id);
        return id;
    }

    private static MockMultipartFile multipartXlsx(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, XLSX_MEDIA_TYPE.toString(), bytes);
    }

    private static byte[] buildImportXlsx(List<String[]> dataRows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("DanhSachTaiKhoan");
            Row header = sheet.createRow(0);
            String[] headers = {"Tên đăng nhập*", "Mật khẩu", "Họ và tên*", "Email", "Số điện thoại", "Mã lớp"};
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }
            for (int i = 0; i < dataRows.size(); i++) {
                String[] vals = dataRows.get(i);
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < vals.length; c++) {
                    if (vals[c] != null && !vals[c].isEmpty()) {
                        row.createCell(c).setCellValue(vals[c]);
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] buildLargeImportXlsx(int rowCount) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Large");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên đăng nhập*");
            header.createCell(1).setCellValue("Mật khẩu");
            header.createCell(2).setCellValue("Họ và tên*");
            for (int i = 1; i <= rowCount; i++) {
                Row r = sheet.createRow(i);
                r.createCell(0).setCellValue("bulk.u." + i);
                r.createCell(1).setCellValue("Valid@1234");
                r.createCell(2).setCellValue("User " + i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
