package vn.unicom.thi360.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.api.CatalogGroupRefResponse;
import vn.unicom.thi360.catalog.api.CatalogImportPreviewResponse;
import vn.unicom.thi360.catalog.domain.CatalogGroup;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRefRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogGroupType;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.idempotency.IdempotencyService;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

class CatalogExcelLocalizationTest {

    private CatalogGroupRepository catalogGroupRepository;
    private CatalogSupportDao catalogSupportDao;
    private JdbcTemplate jdbcTemplate;
    private CatalogExcelSchemaHelper schemaHelper;
    private CatalogExcelService excelService;

    @BeforeEach
    void setUp() {
        catalogGroupRepository = mock(CatalogGroupRepository.class);
        CatalogItemRepository catalogItemRepository = mock(CatalogItemRepository.class);
        CatalogItemRefRepository catalogItemRefRepository = mock(CatalogItemRefRepository.class);
        catalogSupportDao = mock(CatalogSupportDao.class);
        CatalogCacheService catalogCacheService = mock(CatalogCacheService.class);
        schemaHelper = CatalogExcelSchemaHelper.defaultInstance();
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.requireCurrentUser())
                .thenReturn(new CurrentUser(
                        UUID.randomUUID(), "admin", Realm.APP, Access.MANAGE, PermissionSet.empty()));
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);

        excelService = new CatalogExcelService(
                catalogGroupRepository,
                catalogItemRepository,
                catalogItemRefRepository,
                catalogSupportDao,
                catalogCacheService,
                schemaHelper,
                currentUserProvider,
                auditRecorder,
                idempotencyService,
                jdbcTemplate,
                objectMapper,
                clock);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void schemaHelper_localizesSheetHeadersStatusesAndMessages() {
        setRequestLocale("en");
        assertThat(schemaHelper.sheetName()).isEqualTo("Catalog");
        assertThat(schemaHelper.simpleHeaders())
                .containsExactly("Code*", "Vietnamese name*", "English name", "Description", "Status");
        assertThat(schemaHelper.combinationExportHeaders())
                .containsExactly("Code", "Vietnamese name", "English name", "Exam", "Subjects", "Source note", "Version", "Status", "Description");
        assertThat(schemaHelper.treeLevelCodeHeader("Chapter")).isEqualTo("Chapter code");
        assertThat(schemaHelper.treeLevelNameViHeader("Chapter")).isEqualTo("Chapter (Vietnamese)");
        assertThat(schemaHelper.treeLevelNameEnHeader("Chapter")).isEqualTo("Chapter (English)");
        assertThat(schemaHelper.formatStatus(CatalogStatus.ACTIVE)).isEqualTo("Active");
        assertThat(schemaHelper.formatStatus(CatalogStatus.INACTIVE)).isEqualTo("Inactive");

        setRequestLocale("fr");
        assertThat(schemaHelper.sheetName()).isEqualTo("DanhMuc");
        assertThat(schemaHelper.simpleHeaders())
                .containsExactly("Mã*", "Tên tiếng Việt*", "Tên tiếng Anh", "Mô tả", "Trạng thái");
        assertThat(schemaHelper.combinationExportHeaders())
                .containsExactly("Mã", "Tên tiếng Việt", "Tên tiếng Anh", "Kỳ thi", "Các môn học", "Nguồn quy ước", "Phiên bản", "Trạng thái", "Mô tả");
        assertThat(schemaHelper.treeLevelCodeHeader("Chương")).isEqualTo("Mã Chương");
        assertThat(schemaHelper.treeLevelNameViHeader("Chương")).isEqualTo("Chương (Tiếng Việt)");
        assertThat(schemaHelper.treeLevelNameEnHeader("Chương")).isEqualTo("Chương (Tiếng Anh)");
        assertThat(schemaHelper.formatStatus(CatalogStatus.ACTIVE)).isEqualTo("Hoạt động");
        assertThat(schemaHelper.formatStatus(CatalogStatus.INACTIVE)).isEqualTo("Ngừng sử dụng");
    }

    @Test
    void exportSimpleCombinationAndTree_localizeHeadersSheetAndRefGroupNames() throws IOException {
        UUID simpleId = UUID.randomUUID();
        CatalogGroup simpleGroup = new CatalogGroup(
                simpleId, "EXAM", "Kỳ thi", "Exam", null,
                CatalogStatus.ACTIVE, CatalogGroupType.SYSTEM, CatalogGroupKind.SIMPLE, null,
                Instant.now(), UUID.randomUUID());
        when(catalogGroupRepository.findById(simpleId)).thenReturn(Optional.of(simpleGroup));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(simpleId))).thenReturn(List.of());

        setRequestLocale("en");
        CatalogExcelService.ExportedWorkbook simpleEn = excelService.exportGroupItems(simpleId);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(simpleEn.bytes()))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Catalog");
            assertThat(rowCells(wb.getSheetAt(0).getRow(0)))
                    .containsExactly("Code*", "Vietnamese name*", "English name", "Description", "Status");
        }

        UUID combId = UUID.randomUUID();
        CatalogGroup combGroup = new CatalogGroup(
                combId, "SUBJECT_COMBINATION", "Mã tổ hợp", "Subject combination", null,
                CatalogStatus.ACTIVE, CatalogGroupType.SYSTEM, CatalogGroupKind.COMBINATION, null,
                Instant.now(), UUID.randomUUID());
        when(catalogGroupRepository.findById(combId)).thenReturn(Optional.of(combGroup));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(combId))).thenReturn(List.of());

        CatalogExcelService.ExportedWorkbook combEn = excelService.exportGroupItems(combId);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(combEn.bytes()))) {
            assertThat(rowCells(wb.getSheetAt(0).getRow(0)))
                    .containsExactly("Code", "Vietnamese name", "English name", "Exam", "Subjects", "Source note", "Version", "Status", "Description");
        }

        UUID treeId = UUID.randomUUID();
        UUID subjGroupId = UUID.randomUUID();
        CatalogGroup treeGroup = new CatalogGroup(
                treeId, "KNOWLEDGE", "Kiến thức", "Knowledge", null,
                CatalogStatus.ACTIVE, CatalogGroupType.SYSTEM, CatalogGroupKind.TREE,
                List.of("Chương", "Bài"), Instant.now(), UUID.randomUUID());
        when(catalogGroupRepository.findById(treeId)).thenReturn(Optional.of(treeGroup));
        when(catalogSupportDao.loadGroupRefs(List.of(treeId))).thenReturn(Map.of(
                treeId,
                List.of(new CatalogGroupRefResponse(
                        subjGroupId, "SUBJECT", "Môn học", "Subject", CatalogStatus.ACTIVE, 0, true, 0L))));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(treeId))).thenReturn(List.of());

        CatalogExcelService.ExportedWorkbook treeEn = excelService.exportGroupItems(treeId);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(treeEn.bytes()))) {
            assertThat(rowCells(wb.getSheetAt(0).getRow(0)))
                    .containsExactly(
                            "Subject",
                            "Chương code",
                            "Chương (Vietnamese)",
                            "Chương (English)",
                            "Bài code",
                            "Bài (Vietnamese)",
                            "Bài (English)");
        }

        setRequestLocale(null);
        CatalogExcelService.ExportedWorkbook treeVi = excelService.exportGroupItems(treeId);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(treeVi.bytes()))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("DanhMuc");
            assertThat(rowCells(wb.getSheetAt(0).getRow(0)))
                    .containsExactly(
                            "Môn học",
                            "Mã Chương",
                            "Chương (Tiếng Việt)",
                            "Chương (Tiếng Anh)",
                            "Mã Bài",
                            "Bài (Tiếng Việt)",
                            "Bài (Tiếng Anh)");
        }
    }

    @Test
    void simpleImport_acceptsEnglishAndVietnameseHeaders_andLocalizesRowAndHeaderErrors() throws IOException {
        UUID groupId = UUID.randomUUID();
        CatalogGroup group = new CatalogGroup(
                groupId, "QUESTION_TAG", "Nhãn câu hỏi", "Question tag", null,
                CatalogStatus.ACTIVE, CatalogGroupType.CUSTOM, CatalogGroupKind.SIMPLE, null,
                Instant.now(), UUID.randomUUID());
        when(catalogGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(groupId))).thenReturn(List.of());

        byte[] enValidXlsx = buildXlsx(List.of(
                List.of("Code*", "Vietnamese name*", "English name", "Description", "Status"),
                List.of("TAG_1", "Nhãn 1", "Tag 1", "Desc 1", "Active"),
                List.of("TAG_2", "Nhãn 2", "Tag 2", "", "Inactive")));

        setRequestLocale("en");
        CatalogImportPreviewResponse enPreview = excelService.previewImport(
                groupId,
                new MockMultipartFile("file", "en.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", enValidXlsx));
        assertThat(enPreview.totalRows()).isEqualTo(2);
        assertThat(enPreview.validRows()).isEqualTo(2);
        assertThat(enPreview.canConfirm()).isTrue();

        byte[] viValidXlsx = buildXlsx(List.of(
                List.of("Mã*", "Tên tiếng Việt*", "Tên tiếng Anh", "Mô tả", "Trạng thái"),
                List.of("TAG_3", "Nhãn 3", "Tag 3", "Mô tả 3", "Hoạt động")));
        CatalogImportPreviewResponse viPreview = excelService.previewImport(
                groupId,
                new MockMultipartFile("file", "vi.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", viValidXlsx));
        assertThat(viPreview.totalRows()).isEqualTo(1);
        assertThat(viPreview.validRows()).isEqualTo(1);
        assertThat(viPreview.canConfirm()).isTrue();

        // Invalid row in English locale produces English row errors
        byte[] badRowXlsx = buildXlsx(List.of(
                List.of("Code*", "Vietnamese name*", "Status"),
                List.of("", "", "BOGUS")));
        CatalogImportPreviewResponse badEn = excelService.previewImport(
                groupId,
                new MockMultipartFile("file", "bad.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", badRowXlsx));
        assertThat(badEn.canConfirm()).isFalse();
        assertThat(badEn.errors()).extracting("message")
                .containsExactlyInAnyOrder(
                        "Item code must not be blank",
                        "Vietnamese name must not be blank",
                        "Invalid status");

        setRequestLocale(null);
        CatalogImportPreviewResponse badVi = excelService.previewImport(
                groupId,
                new MockMultipartFile("file", "bad.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", badRowXlsx));
        assertThat(badVi.errors()).extracting("message")
                .containsExactlyInAnyOrder(
                        "Mã giá trị không được để trống",
                        "Tên tiếng Việt không được để trống",
                        "Trạng thái không hợp lệ");

        // Missing header localized
        byte[] wrongHeaderXlsx = buildXlsx(List.of(List.of("Foo", "Bar"), List.of("A", "B")));
        setRequestLocale("en");
        assertThatThrownBy(() -> excelService.previewImport(
                groupId,
                new MockMultipartFile("file", "bad-hdr.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", wrongHeaderXlsx)))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getCode()).isEqualTo("IMPORT_INVALID_HEADER");
                    assertThat(ve.getFieldErrors().getFirst().message())
                            .isEqualTo("Column structure in the Excel file does not match the catalog group template");
                });
    }

    @Test
    void treeImport_acceptsEnglishAndVietnameseHeaders_andLocalizesLevelHeaderErrors() throws IOException {
        UUID treeId = UUID.randomUUID();
        UUID subjGroupId = UUID.randomUUID();
        CatalogGroup treeGroup = new CatalogGroup(
                treeId, "KNOWLEDGE", "Kiến thức", "Knowledge", null,
                CatalogStatus.ACTIVE, CatalogGroupType.SYSTEM, CatalogGroupKind.TREE,
                List.of("Chương", "Bài"), Instant.now(), UUID.randomUUID());
        when(catalogGroupRepository.findById(treeId)).thenReturn(Optional.of(treeGroup));
        when(catalogSupportDao.loadGroupRefs(List.of(treeId))).thenReturn(Map.of(
                treeId,
                List.of(new CatalogGroupRefResponse(
                        subjGroupId, "SUBJECT", "Môn học", "Subject", CatalogStatus.ACTIVE, 0, true, 0L))));

        byte[] enTreeXlsx = buildXlsx(List.of(
                List.of(
                        "Subject",
                        "Chương code",
                        "Chương (Vietnamese)",
                        "Chương (English)",
                        "Bài code",
                        "Bài (Vietnamese)",
                        "Bài (English)"),
                List.of("UNKNOWN_SUBJ", "CH1", "Chương 1", "Chapter 1", "B1", "Bài 1", "Lesson 1")));

        setRequestLocale("en");
        CatalogImportPreviewResponse previewEn = excelService.previewImport(
                treeId,
                new MockMultipartFile("file", "tree-en.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", enTreeXlsx));
        assertThat(previewEn.totalRows()).isEqualTo(1);
        assertThat(previewEn.errorRows()).isEqualTo(1);
        assertThat(previewEn.errors()).hasSize(1);
        assertThat(previewEn.errors().getFirst().code()).isEqualTo("CATALOG_REF_INVALID");
        assertThat(previewEn.errors().getFirst().message())
                .isEqualTo("Reference item 'UNKNOWN_SUBJ' does not exist or is inactive in Subject");

        byte[] viTreeXlsx = buildXlsx(List.of(
                List.of(
                        "Môn học",
                        "Mã Chương",
                        "Chương (Tiếng Việt)",
                        "Chương (Tiếng Anh)",
                        "Mã Bài",
                        "Bài (Tiếng Việt)",
                        "Bài (Tiếng Anh)"),
                List.of("", "CH1", "Chương 1", "Chapter 1", "B1", "Bài 1", "Lesson 1")));
        CatalogImportPreviewResponse previewViHeaders = excelService.previewImport(
                treeId,
                new MockMultipartFile("file", "tree-vi.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", viTreeXlsx));
        assertThat(previewViHeaders.totalRows()).isEqualTo(1);
        assertThat(previewViHeaders.validRows()).isEqualTo(1);
        assertThat(previewViHeaders.canConfirm()).isTrue();
    }

    private static void setRequestLocale(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static List<String> rowCells(Row row) {
        DataFormatter fmt = new DataFormatter();
        return java.util.stream.IntStream.range(0, row.getLastCellNum())
                .mapToObj(i -> fmt.formatCellValue(row.getCell(i)))
                .toList();
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
}
