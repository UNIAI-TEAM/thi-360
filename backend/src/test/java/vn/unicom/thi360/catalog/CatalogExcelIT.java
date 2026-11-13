package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

class CatalogExcelIT extends AbstractIntegrationTest {

    private static final UUID EXAM_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000001");
    private static final UUID SUBJECT_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID SUBJECT_COMB_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000007");
    private static final UUID QUESTION_TAG_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000008");
    private static final UUID KNOWLEDGE_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000009");
    private static final UUID ITEM_HSA = UUID.fromString("00000000-0000-7000-8200-000000000101");
    private static final UUID ITEM_TOAN = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final UUID ITEM_VAN = UUID.fromString("00000000-0000-7000-8200-000000000202");
    private static final UUID ITEM_LY = UUID.fromString("00000000-0000-7000-8200-000000000204");
    private static final UUID ITEM_GRADE_10 = UUID.fromString("00000000-0000-7000-8200-000000000301");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exportSimpleCombinationAndTreeProduceExpectedSheets() throws Exception {
        // 0. Student cannot export (403)
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/items/export", EXAM_GROUP_ID)
                        .with(asStudent()))
                .andExpect(status().isForbidden());

        // 1. Export SIMPLE (EXAM)
        MvcResult simpleRes = mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/items/export", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("catalog-exam.xlsx")))
                .andReturn();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(simpleRes.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(0);
            assertThat(fmt.formatCellValue(header.getCell(0))).isEqualTo("Mã*");
            assertThat(fmt.formatCellValue(header.getCell(1))).isEqualTo("Tên tiếng Việt*");
            assertThat(fmt.formatCellValue(header.getCell(2))).isEqualTo("Tên tiếng Anh");
            assertThat(fmt.formatCellValue(header.getCell(3))).isEqualTo("Mô tả");
            assertThat(fmt.formatCellValue(header.getCell(4))).isEqualTo("Trạng thái");
            assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(2);
            assertThat(fmt.formatCellValue(sheet.getRow(1).getCell(0))).isEqualTo("HSA");
        }

        // 2. Export COMBINATION (SUBJECT_COMBINATION)
        UUID combGroupId = SUBJECT_COMB_GROUP_ID;
        String combCode = "XLS_" + shortCode();
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", combGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", combCode,
                                "nameVi", "Tổ hợp Excel",
                                "examItemId", ITEM_HSA,
                                "subjectItemIds", List.of(ITEM_TOAN, ITEM_VAN, ITEM_LY),
                                "sourceNote", "QD-XLS-1"))))
                .andExpect(status().isCreated());

        MvcResult combRes = mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/items/export", combGroupId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andReturn();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(combRes.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(0);
            assertThat(fmt.formatCellValue(header.getCell(0))).isEqualTo("Mã");
            assertThat(fmt.formatCellValue(header.getCell(3))).isEqualTo("Kỳ thi");
            assertThat(fmt.formatCellValue(header.getCell(4))).isEqualTo("Các môn học");
            assertThat(fmt.formatCellValue(header.getCell(5))).isEqualTo("Nguồn quy ước");
            assertThat(fmt.formatCellValue(header.getCell(6))).isEqualTo("Phiên bản");

            boolean foundComb = false;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null && combCode.equals(fmt.formatCellValue(row.getCell(0)))) {
                    foundComb = true;
                    assertThat(fmt.formatCellValue(row.getCell(3))).isEqualTo("HSA");
                    assertThat(fmt.formatCellValue(row.getCell(4))).isEqualTo("TOAN; VAN; LY");
                    assertThat(fmt.formatCellValue(row.getCell(5))).isEqualTo("QD-XLS-1");
                    assertThat(fmt.formatCellValue(row.getCell(6))).isEqualTo("1");
                }
            }
            assertThat(foundComb).isTrue();
        }

        // 3. Export TREE (KNOWLEDGE) with 2-level branch & 3-level branch -> leaf-only rows
        String chCode = "XCH_" + shortCode();
        String b1Code = "XB1_" + shortCode();
        String k1Code = "XK1_" + shortCode();
        String b2Code = "XB2_" + shortCode();
        UUID chId = createTreeItem(KNOWLEDGE_GROUP_ID, chCode, "Chương X", "Chapter X", null, List.of(ITEM_TOAN, ITEM_GRADE_10));
        UUID b1Id = createTreeItem(KNOWLEDGE_GROUP_ID, b1Code, "Bài 1", "Lesson 1", chId, null);
        createTreeItem(KNOWLEDGE_GROUP_ID, k1Code, "KT 1", "Topic 1", b1Id, null);
        createTreeItem(KNOWLEDGE_GROUP_ID, b2Code, "Bài 2", "Lesson 2", chId, null); // leaf at depth 2

        MvcResult treeRes = mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/items/export", KNOWLEDGE_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andReturn();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(treeRes.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(0);
            // KNOWLEDGE refs: SUBJECT (Môn học), GRADE (Khối lớp), then 3 levels: Chương, Bài, Kiến thức
            assertThat(fmt.formatCellValue(header.getCell(0))).isEqualTo("Môn học");
            assertThat(fmt.formatCellValue(header.getCell(1))).isEqualTo("Khối lớp");
            assertThat(fmt.formatCellValue(header.getCell(2))).isEqualTo("Mã Chương");
            assertThat(fmt.formatCellValue(header.getCell(3))).isEqualTo("Chương (Tiếng Việt)");
            assertThat(fmt.formatCellValue(header.getCell(4))).isEqualTo("Chương (Tiếng Anh)");
            assertThat(fmt.formatCellValue(header.getCell(5))).isEqualTo("Mã Bài");
            assertThat(fmt.formatCellValue(header.getCell(8))).isEqualTo("Mã Kiến thức");

            boolean foundDepth3Leaf = false;
            boolean foundDepth2Leaf = false;
            boolean foundNonLeafStandalone = false;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String c1 = fmt.formatCellValue(row.getCell(2));
                String c2 = fmt.formatCellValue(row.getCell(5));
                String c3 = fmt.formatCellValue(row.getCell(8));
                if (chCode.equals(c1) && b1Code.equals(c2) && k1Code.equals(c3)) {
                    foundDepth3Leaf = true;
                    assertThat(fmt.formatCellValue(row.getCell(0))).isEqualTo("TOAN");
                    assertThat(fmt.formatCellValue(row.getCell(1))).isEqualTo("G10");
                }
                if (chCode.equals(c1) && b2Code.equals(c2) && c3.isEmpty()) {
                    foundDepth2Leaf = true;
                }
                if (chCode.equals(c1) && c2.isEmpty() && c3.isEmpty()) {
                    foundNonLeafStandalone = true;
                }
            }
            assertThat(foundDepth3Leaf).isTrue();
            assertThat(foundDepth2Leaf).isTrue();
            assertThat(foundNonLeafStandalone).isFalse();
        }
    }

    @Test
    void simpleImportPreviewConfirmAndIdempotencyWorkEndToEnd() throws Exception {
        String c1 = "IMP1_" + shortCode();
        String c2 = "IMP2_" + shortCode();
        byte[] xlsx = buildXlsx(List.of(
                List.of("Mã*", "Tên tiếng Việt*", "Tên tiếng Anh", "Mô tả", "Trạng thái"),
                List.of(c1, "Nhãn A", "Tag A", "Mô tả A", "Hoạt động"),
                List.of(c2, "Nhãn B", "", "", "INACTIVE")
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tags.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx);

        // CATALOG.VIEW cannot import
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(file)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());

        MvcResult previewRes = mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(file)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.errorRows").value(0))
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andReturn();

        UUID previewId = UUID.fromString(
                objectMapper.readTree(previewRes.getResponse().getContentAsString()).get("previewId").asText());

        String idemKey = "idem-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        QUESTION_TAG_GROUP_ID, previewId)
                        .header("Idempotency-Key", idemKey)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.reusedCount").value(0));

        // Repeat confirm with same Idempotency-Key returns same result without duplicating
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        QUESTION_TAG_GROUP_ID, previewId)
                        .header("Idempotency-Key", idemKey)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2));

        // Repeat confirm without Idempotency-Key also returns stored result (status=2)
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        QUESTION_TAG_GROUP_ID, previewId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catalog.catalog_item WHERE group_id = ? AND code IN (?, ?)",
                Integer.class,
                QUESTION_TAG_GROUP_ID,
                c1,
                c2);
        assertThat(count).isEqualTo(2);

        // Non-existent previewId -> 404
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        QUESTION_TAG_GROUP_ID, UUID.randomUUID())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNotFound());
    }

    @Test
    void simpleImportValidationErrorsAndRecheckOnConfirm() throws Exception {
        String dupCode = "DUP_" + shortCode();
        String raceCode = "RACE_" + shortCode();

        // Preview with row errors: blank name, bad code, duplicate in file
        byte[] badXlsx = buildXlsx(List.of(
                List.of("Mã", "Tên tiếng Việt", "Trạng thái"),
                List.of(dupCode, "Valid", "ACTIVE"),
                List.of(dupCode, "Dup in file", "ACTIVE"),
                List.of("bad code!", "", "UNKNOWN")
        ));

        MvcResult badPreviewRes = mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "bad.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", badXlsx))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.errorRows").value(2))
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.errors[*].code", hasItem("CATALOG_CODE_DUPLICATE_IN_FILE")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("CODE_INVALID")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("REQUIRED")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("STATUS_INVALID")))
                .andReturn();

        UUID badPreviewId = UUID.fromString(
                objectMapper.readTree(badPreviewRes.getResponse().getContentAsString()).get("previewId").asText());

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        QUESTION_TAG_GROUP_ID, badPreviewId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));

        // Race-condition re-check: preview passes, then another item with same code is created before confirm
        byte[] raceXlsx = buildXlsx(List.of(
                List.of("Mã", "Tên tiếng Việt"),
                List.of(raceCode, "Race Tag")
        ));
        MvcResult racePreviewRes = mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "race.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", raceXlsx))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andReturn();

        UUID racePreviewId = UUID.fromString(
                objectMapper.readTree(racePreviewRes.getResponse().getContentAsString()).get("previewId").asText());

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", QUESTION_TAG_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", raceCode, "nameVi", "Inserted first"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        QUESTION_TAG_GROUP_ID, racePreviewId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("CATALOG_CODE_TAKEN"));

        // Expired preview returns 409 IMPORT_PREVIEW_EXPIRED
        jdbcTemplate.update(
                "UPDATE catalog.catalog_import_preview SET expires_at = now() - interval '1 hour' WHERE id = ?",
                racePreviewId);
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        QUESTION_TAG_GROUP_ID, racePreviewId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_PREVIEW_EXPIRED"));
    }

    @Test
    void treeImportHandlesMergedCellsInheritanceNodeReuseAndAddOnlyRefs() throws Exception {
        String existingCh = "TCH_" + shortCode();
        String existingB1 = "TB1_" + shortCode();
        String newK1 = "TK1_" + shortCode();
        String newK2 = "TK2_" + shortCode();
        String newB2 = "TB2_" + shortCode();
        String newK3 = "TK3_" + shortCode();

        // Pre-seed chapter + lesson 1 with TOAN ref; rename check ensures existing node name is NOT overwritten
        UUID chId = createTreeItem(KNOWLEDGE_GROUP_ID, existingCh, "Tên gốc Chương", null, null, List.of(ITEM_TOAN));
        createTreeItem(KNOWLEDGE_GROUP_ID, existingB1, "Bài 1 gốc", null, chId, null);

        byte[] treeXlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Tree");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Môn học");
            h.createCell(1).setCellValue("Khối lớp");
            h.createCell(2).setCellValue("Mã Chương");
            h.createCell(3).setCellValue("Chương (Tiếng Việt)");
            h.createCell(4).setCellValue("Mã Bài");
            h.createCell(5).setCellValue("Bài (Tiếng Việt)");
            h.createCell(6).setCellValue("Mã Kiến thức");
            h.createCell(7).setCellValue("Kiến thức (Tiếng Việt)");

            // Row 1 (Excel row 2): reuse existingCh + existingB1, add LY ref (add-only -> keeps TOAN), add newK1
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("LY");
            r1.createCell(1).setCellValue("G10");
            r1.createCell(2).setCellValue(existingCh);
            r1.createCell(3).setCellValue("Tên mới không ghi đè");
            r1.createCell(4).setCellValue(existingB1);
            r1.createCell(5).setCellValue("Bài 1 gốc");
            r1.createCell(6).setCellValue(newK1);
            r1.createCell(7).setCellValue("Kiến thức 1");

            // Row 2 (Excel row 3): merged cells for Chương & Bài inherited into blank cells, add newK2
            Row r2 = s.createRow(2);
            r2.createCell(6).setCellValue(newK2);
            r2.createCell(7).setCellValue("Kiến thức 2");
            s.addMergedRegion(new CellRangeAddress(1, 2, 2, 2));
            s.addMergedRegion(new CellRangeAddress(1, 2, 3, 3));

            // Row 3 (Excel row 4): blank Chương inherits existingCh from above, introduces newB2 + newK3
            Row r3 = s.createRow(3);
            r3.createCell(4).setCellValue(newB2);
            r3.createCell(5).setCellValue("Bài 2 mới");
            r3.createCell(6).setCellValue(newK3);
            r3.createCell(7).setCellValue("Kiến thức 3");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            treeXlsx = bos.toByteArray();
        }

        MvcResult previewRes = mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", KNOWLEDGE_GROUP_ID)
                        .file(new MockMultipartFile("file", "tree.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", treeXlsx))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.validRows").value(3))
                .andExpect(jsonPath("$.errorRows").value(0))
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andReturn();

        UUID previewId = UUID.fromString(
                objectMapper.readTree(previewRes.getResponse().getContentAsString()).get("previewId").asText());

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        KNOWLEDGE_GROUP_ID, previewId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(4)) // newK1, newK2, newB2, newK3
                .andExpect(jsonPath("$.reusedCount").value(2)); // existingCh, existingB1

        // Existing chapter kept original nameVi
        String chName = jdbcTemplate.queryForObject(
                "SELECT name_vi FROM catalog.catalog_item WHERE id = ?", String.class, chId);
        assertThat(chName).isEqualTo("Tên gốc Chương");

        // Add-only refs: TOAN preserved, LY and 10 added
        List<UUID> refIds = jdbcTemplate.queryForList(
                "SELECT ref_item_id FROM catalog.catalog_item_ref WHERE item_id = ?",
                UUID.class,
                chId);
        assertThat(refIds).containsExactlyInAnyOrder(ITEM_TOAN, ITEM_LY, ITEM_GRADE_10);

        // Re-importing the exact same file creates 0 and reuses all 6 nodes
        MvcResult secondPreview = mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", KNOWLEDGE_GROUP_ID)
                        .file(new MockMultipartFile("file", "tree.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", treeXlsx))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andReturn();
        UUID secondPreviewId = UUID.fromString(
                objectMapper.readTree(secondPreview.getResponse().getContentAsString()).get("previewId").asText());

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        KNOWLEDGE_GROUP_ID, secondPreviewId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.reusedCount").value(6));
    }

    @Test
    void treeImportReportsParentRequiredInactiveParentInvalidRefAndWrongParentCollision() throws Exception {
        String inactiveCh = "ICH_" + shortCode();
        UUID inactiveChId = createTreeItem(KNOWLEDGE_GROUP_ID, inactiveCh, "Chương ngừng", null, null, null);
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", inactiveChId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE", "reason", "Tạm dừng", "version", 0))))
                .andExpect(status().isOk());

        String activeCh1 = "ACH1_" + shortCode();
        String activeCh2 = "ACH2_" + shortCode();
        String takenB = "TAKENB_" + shortCode();
        UUID ach1Id = createTreeItem(KNOWLEDGE_GROUP_ID, activeCh1, "Chương 1", null, null, null);
        createTreeItem(KNOWLEDGE_GROUP_ID, takenB, "Bài thuộc C1", null, ach1Id, null);
        createTreeItem(KNOWLEDGE_GROUP_ID, activeCh2, "Chương 2", null, null, null);

        byte[] xlsx = buildXlsx(List.of(
                List.of("Môn học", "Mã Chương", "Chương (Tiếng Việt)", "Mã Bài", "Bài (Tiếng Việt)", "Mã Kiến thức", "Kiến thức (Tiếng Việt)"),
                // Row 2: missing ancestor on first data row -> PARENT_REQUIRED
                List.of("", "", "", "B_ORPHAN", "Bài mồ côi", "", ""),
                // Row 3: invalid ref + inactive parent -> CATALOG_REF_INVALID & CATALOG_PARENT_INACTIVE
                List.of("NO_SUCH_SUBJ", inactiveCh, "Chương ngừng", "B_UNDER_INACT", "Bài con", "", ""),
                // Row 4: takenB under activeCh2 (exists under activeCh1) -> CATALOG_CODE_TAKEN
                List.of("TOAN", activeCh2, "Chương 2", takenB, "Bài trùng", "", "")
        ));

        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", KNOWLEDGE_GROUP_ID)
                        .file(new MockMultipartFile("file", "tree-err.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorRows").value(3))
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.errors[*].code", hasItem("PARENT_REQUIRED")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("CATALOG_PARENT_INACTIVE")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("CATALOG_REF_INVALID")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("CATALOG_CODE_TAKEN")));
    }

    @Test
    void importSecurityLimitsRejectLargeMacrosFormulasRowLimitEmptyAndZipBomb() throws Exception {
        Set<String> beforeTempFiles = listCatalogImportTempFiles();

        // 1. COMBINATION group not supported
        byte[] okSimple = buildXlsx(List.of(
                List.of("Mã", "Tên tiếng Việt"),
                List.of("C1", "Tên 1")
        ));
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", SUBJECT_COMB_GROUP_ID)
                        .file(new MockMultipartFile("file", "ok.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", okSimple))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_SUPPORTED"));

        // 2. > 5 MB file
        byte[] oversized = new byte[5 * 1024 * 1024 + 128];
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "big.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", oversized))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_TOO_LARGE"));

        // 3. .xlsm macro filename AND embedded vbaProject.bin in .xlsx
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "macro.xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12", okSimple))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_MACRO_NOT_ALLOWED"));

        byte[] withVba = injectZipEntry(okSimple, "xl/vbaProject.bin", new byte[] {1, 2, 3, 4});
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "sneaky.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", withVba))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_MACRO_NOT_ALLOWED"));

        // 4. Formula cell -> IMPORT_FORMULA_NOT_ALLOWED
        byte[] formulaXlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Simple");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Mã");
            h.createCell(1).setCellValue("Tên tiếng Việt");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("F1");
            r.createCell(1).setCellFormula("1+1");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            formulaXlsx = bos.toByteArray();
        }
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "formula.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", formulaXlsx))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FORMULA_NOT_ALLOWED"));

        // 5. > 5,000 rows -> IMPORT_ROW_LIMIT_EXCEEDED
        byte[] tooManyRows;
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet s = wb.createSheet("Many");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Mã");
            h.createCell(1).setCellValue("Tên tiếng Việt");
            for (int i = 1; i <= 5001; i++) {
                Row r = s.createRow(i);
                r.createCell(0).setCellValue("R" + i);
                r.createCell(1).setCellValue("Row " + i);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            wb.dispose();
            tooManyRows = bos.toByteArray();
        }
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "5001.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", tooManyRows))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_ROW_LIMIT_EXCEEDED"));

        // 6. Empty data sheet -> IMPORT_EMPTY_FILE
        byte[] emptyRows = buildXlsx(List.of(List.of("Mã", "Tên tiếng Việt")));
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", emptyRows))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_EMPTY_FILE"));

        // 7. Zip bomb (high-compression giant XML entry > ZipSecureFile limits)
        byte[] zipBomb = injectZipEntry(okSimple, "xl/sharedStrings.xml", "A".repeat(12 * 1024 * 1024).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "bomb.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", zipBomb))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID_FILE"));

        // 8. Non-zip payload
        mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", QUESTION_TAG_GROUP_ID)
                        .file(new MockMultipartFile("file", "notzip.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "not a zip".getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID_FILE"));

        // Verify no leftover temp files
        assertThat(listCatalogImportTempFiles()).isEqualTo(beforeTempFiles);
    }

    private UUID createTreeItem(
            UUID groupId, String code, String nameVi, String nameEn, UUID parentId, List<UUID> refIds)
            throws Exception {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("code", code);
        payload.put("nameVi", nameVi);
        if (nameEn != null) {
            payload.put("nameEn", nameEn);
        }
        if (parentId != null) {
            payload.put("parentId", parentId);
        }
        if (refIds != null) {
            payload.put("refItemIds", refIds);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return UUID.fromString(node.get("id").asText());
    }

    private static byte[] buildXlsx(List<List<String>> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cols = rows.get(r);
                for (int c = 0; c < cols.size(); c++) {
                    row.createCell(c).setCellValue(cols.get(c));
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private static byte[] injectZipEntry(byte[] baseZip, String entryName, byte[] entryData) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(baseZip));
                ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(entry.getName()));
                zis.transferTo(zos);
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(entryData);
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private static Set<String> listCatalogImportTempFiles() throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (var stream = Files.list(tmp)) {
            return stream.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("thi360-catalog-import-"))
                    .collect(Collectors.toSet());
        }
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
