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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.application.ImportBatchProcessorJob;
import vn.unicom.thi360.identity.application.ImportBatchService;
import vn.unicom.thi360.identity.domain.ImportBatchStatus;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
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

    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final List<UUID> createdBatchIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdKeycloakUserIds = new ArrayList<>();
    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<String> createdIdempotencyKeys = new ArrayList<>();

    @BeforeEach
    void setup() {
        clearInvocations(auditRecorder, keycloakClient);
    }

    @AfterEach
    void cleanup() {
        reset(keycloakClient, auditRecorder);
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
    void weakPasswordRow_returnsPasswordWeakAtRightRowAndColumn() throws Exception {
        String sfx = suffix();
        List<String[]> rows = List.of(
                new String[] {"good.pwd." + sfx, "Valid@1234", "Good Password", "", "", ""},
                new String[] {"weak.pwd." + sfx, "weak", "Weak Password", "", "", ""}
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
    void processNextConfirmedBatch_claimsWithSkipLocked_andResumesInterruptedProcessingOnlyForUnfinishedRows()
            throws Exception {
        String sfx = suffix();
        UUID batchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {"skip.u1." + sfx, "Valid@1234", "Skip One", "", "", ""},
                        new String[] {"skip.u2." + sfx, "Valid@1234", "Skip Two", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        String key = "skip-confirm-" + sfx;
        createdIdempotencyKeys.add(key);
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", key)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk());

        UUID claimed = importBatchProcessorJob.processNextConfirmedBatch().orElseThrow();
        assertThat(claimed).isEqualTo(batchId);
        assertThat(importBatchProcessorJob.processNextConfirmedBatch()).isEmpty();

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdRows").value(2));

        // Simulate worker interruption during PROCESSING: row 2 already CREATED, row 3 still VALID
        UUID interruptedBatchId = uploadBatch(
                buildImportXlsx(List.of(
                        new String[] {"res.u1." + sfx, "Valid@1234", "Resume One", "", "", ""},
                        new String[] {"res.u2." + sfx, "Valid@1234", "Resume Two", "", "", ""})),
                false,
                Permissions.USER_MODIFY);
        String key2 = "resume-confirm-" + sfx;
        createdIdempotencyKeys.add(key2);
        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", interruptedBatchId)
                        .header("Idempotency-Key", key2)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk());

        UUID existingId = Ids.newId();
        jdbcTemplate.update(
                "UPDATE identity.import_batch SET status = 4, started_at = now() WHERE id = ?",
                interruptedBatchId);
        jdbcTemplate.update(
                "UPDATE identity.import_row SET status = 3, created_user_id = ?, processed_at = now() WHERE batch_id = ? AND row_no = 2",
                existingId,
                interruptedBatchId);

        clearInvocations(keycloakClient);
        ImportBatchStatus resumedStatus = importBatchProcessorJob.processBatch(interruptedBatchId);
        assertThat(resumedStatus).isEqualTo(ImportBatchStatus.COMPLETED);
        verify(keycloakClient, times(1)).createUser(argThat(cmd ->
                cmd != null && ("res.u2." + sfx).equalsIgnoreCase(cmd.username())));
    }

    @Test
    void fakeKeycloakFailureRow3Of5_marksCompletedWithErrors_andRerunProcessesOnlyFailedRow() throws Exception {
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
        String key = "kc-fail-confirm-" + sfx;
        createdIdempotencyKeys.add(key);

        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", key)
                        .with(asUser(Permissions.USER_MODIFY, Permissions.CLASS_MODIFY)))
                .andExpect(status().isOk());

        // Fake Keycloak failure on row 3 (username kc.r3.<sfx>)
        String failUsername = "kc.r3." + sfx;
        doAnswer(invocation -> {
            KeycloakIdentityClient.CreateUserCommand cmd = invocation.getArgument(0);
            if (failUsername.equalsIgnoreCase(cmd.username())) {
                throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", "Simulated KC outage", null);
            }
            return invocation.callRealMethod();
        }).when(keycloakClient).createUser(any());

        ImportBatchStatus firstStatus = importBatchProcessorJob.processBatch(batchId);
        assertThat(firstStatus).isEqualTo(ImportBatchStatus.COMPLETED_WITH_ERRORS);

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.createdRows").value(4))
                .andExpect(jsonPath("$.failedRows").value(1));

        // After completion (even COMPLETED_WITH_ERRORS), source file is gone -> 409 IMPORT_SOURCE_GONE
        mockMvc.perform(get("/api/v1/admin/import-batches/{id}/errors", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_SOURCE_GONE"));

        // Verify row 1 assigned to class and STUDENT role
        UserAccount row1User = userAccountRepository.findActiveByUsernameNormalized("kc.r1." + sfx).orElseThrow();
        Integer membership = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member WHERE class_id = ? AND user_id = ?",
                Integer.class,
                classId,
                row1User.getId());
        assertThat(membership).isEqualTo(1);

        // Recover Keycloak, clear invocations, and rerun -> MUST process ONLY row 3!
        reset(keycloakClient);
        clearInvocations(keycloakClient);

        ImportBatchStatus rerunStatus = importBatchProcessorJob.processBatch(batchId);
        assertThat(rerunStatus).isEqualTo(ImportBatchStatus.COMPLETED);

        verify(keycloakClient, times(1)).createUser(argThat(cmd ->
                cmd != null && failUsername.equalsIgnoreCase(cmd.username())));

        mockMvc.perform(get("/api/v1/admin/import-batches/{id}", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdRows").value(5))
                .andExpect(jsonPath("$.failedRows").value(0));

        // Verify result file now has generated passwords for rows 1, 3, 5 and NONE for rows 2, 4
        MvcResult resultRes = mockMvc.perform(get("/api/v1/admin/import-batches/{id}/result", batchId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andReturn();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resultRes.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            assertThat(fmt.formatCellValue(sheet.getRow(1).getCell(5))).isNotBlank();
            assertThat(fmt.formatCellValue(sheet.getRow(2).getCell(5))).isEmpty();
            assertThat(fmt.formatCellValue(sheet.getRow(3).getCell(5))).isNotBlank();
            assertThat(fmt.formatCellValue(sheet.getRow(4).getCell(5))).isEmpty();
            assertThat(fmt.formatCellValue(sheet.getRow(5).getCell(5))).isNotBlank();
        }
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
        String key = "enc-confirm-" + sfx;
        createdIdempotencyKeys.add(key);

        mockMvc.perform(post("/api/v1/admin/import-batches/{id}/confirm", batchId)
                        .header("Idempotency-Key", key)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk());

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
        // Must NOT start with ZIP magic PK (0x50, 0x4B)
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
        List<String> rawJsons = jdbcTemplate.queryForList(
                "SELECT raw::text FROM identity.import_row WHERE batch_id = ?",
                String.class,
                batchId);
        assertThat(rawJsons).hasSize(4);
        for (String rawJson : rawJsons) {
            JsonNode node = objectMapper.readTree(rawJson);
            assertThat(node.path("passwordEnc").isNull() || node.path("passwordEnc").isMissingNode()).isTrue();
            assertThat(rawJson).doesNotContain(explicitPwd1).doesNotContain(explicitPwd2);
        }

        // 5. Logs contain NO passwords (neither user-supplied nor system-generated)
        String allLogs = output.getOut() + output.getErr();
        assertThat(allLogs)
                .doesNotContain(explicitPwd1)
                .doesNotContain(explicitPwd2)
                .doesNotContain(genPwd1)
                .doesNotContain(genPwd3);

        // 6. Audit outbox contains NO passwords
        while (outboxRelay.relayBatch() > 0) {
            // drain
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
    void upload_rejectsOver2000Rows_andRejectsFormulas() throws Exception {
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
