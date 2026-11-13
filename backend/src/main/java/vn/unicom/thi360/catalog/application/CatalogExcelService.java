package vn.unicom.thi360.catalog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.api.CatalogGroupRefResponse;
import vn.unicom.thi360.catalog.api.CatalogImportPreviewResponse;
import vn.unicom.thi360.catalog.api.CatalogImportResultResponse;
import vn.unicom.thi360.catalog.api.CatalogImportRowError;
import vn.unicom.thi360.catalog.api.CatalogItemRefResponse;
import vn.unicom.thi360.catalog.api.CombinationSubjectResponse;
import vn.unicom.thi360.catalog.api.CombinationVersionResponse;
import vn.unicom.thi360.catalog.domain.CatalogGroup;
import vn.unicom.thi360.catalog.domain.CatalogItem;
import vn.unicom.thi360.catalog.domain.CatalogItemRef;
import vn.unicom.thi360.catalog.domain.CatalogItemRefId;
import vn.unicom.thi360.catalog.domain.CatalogNormalizer;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRefRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.idempotency.IdempotencyService;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

@Service
public class CatalogExcelService {

    public static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    public static final int MAX_IMPORT_ROWS = 5_000;
    private static final long MAX_TREE_NODES = 50_000L;
    private static final Duration PREVIEW_TTL = Duration.ofHours(2);
    private static final String MODULE = "CATALOG";

    private static final double ZIP_MIN_INFLATE_RATIO = 0.01d;
    private static final long ZIP_MAX_ENTRY_SIZE = 10L * 1024L * 1024L;
    private static final long ZIP_MAX_TEXT_SIZE = 10L * 1024L * 1024L;

    private static final short PREVIEW_STATUS_PREVIEWED = 1;
    private static final short PREVIEW_STATUS_CONFIRMED = 2;

    private final CatalogGroupRepository catalogGroupRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final CatalogItemRefRepository catalogItemRefRepository;
    private final CatalogSupportDao catalogSupportDao;
    private final CatalogCacheService catalogCacheService;
    private final CatalogExcelSchemaHelper schemaHelper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final IdempotencyService idempotencyService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CatalogExcelService(
            CatalogGroupRepository catalogGroupRepository,
            CatalogItemRepository catalogItemRepository,
            CatalogItemRefRepository catalogItemRefRepository,
            CatalogSupportDao catalogSupportDao,
            CatalogCacheService catalogCacheService,
            CatalogExcelSchemaHelper schemaHelper,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            IdempotencyService idempotencyService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.catalogGroupRepository = catalogGroupRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.catalogItemRefRepository = catalogItemRefRepository;
        this.catalogSupportDao = catalogSupportDao;
        this.catalogCacheService = catalogCacheService;
        this.schemaHelper = schemaHelper;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.idempotencyService = idempotencyService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExportedWorkbook exportGroupItems(UUID groupId) {
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(schemaHelper.sheetName());
            CellStyle headerStyle = createHeaderStyle(workbook);

            if (group.getKind() == CatalogGroupKind.SIMPLE) {
                exportSimple(group, sheet, headerStyle);
            } else if (group.getKind() == CatalogGroupKind.COMBINATION) {
                exportCombination(group, sheet, headerStyle);
            } else {
                exportTree(group, sheet, headerStyle);
            }

            workbook.write(out);
            String filename = "catalog-" + group.getCodeNormalized() + ".xlsx";
            return new ExportedWorkbook(filename, out.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export catalog Excel for group " + groupId, ex);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Transactional
    public CatalogImportPreviewResponse previewImport(UUID groupId, MultipartFile file) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (group.getKind() == CatalogGroupKind.COMBINATION) {
            throw new ConflictException("IMPORT_NOT_SUPPORTED");
        }
        if (group.getStatus() != CatalogStatus.ACTIVE) {
            throw new ConflictException("CATALOG_GROUP_INACTIVE");
        }
        if (file == null || file.isEmpty()) {
            throw new ValidationException("IMPORT_EMPTY_FILE", null, List.of(
                    new FieldError("file", "IMPORT_EMPTY_FILE", schemaHelper.rowMessage("IMPORT_EMPTY_FILE", "Tệp Excel trống"))));
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ValidationException("IMPORT_FILE_TOO_LARGE", null, List.of(
                    new FieldError("file", "IMPORT_FILE_TOO_LARGE", schemaHelper.rowMessage("IMPORT_FILE_TOO_LARGE", "Tệp vượt quá 5 MB"))));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsm")) {
            throw new ValidationException("IMPORT_MACRO_NOT_ALLOWED", null, List.of(
                    new FieldError("file", "IMPORT_MACRO_NOT_ALLOWED", schemaHelper.rowMessage("IMPORT_MACRO_NOT_ALLOWED", "Không chấp nhận tệp chứa macro"))));
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("thi360-catalog-import-", ".xlsx");
            file.transferTo(tempFile);
            verifyZipSignature(tempFile);
            rejectMacroZipEntries(tempFile);
            configureZipSecureLimits();

            try (OPCPackage pkg = OPCPackage.open(tempFile.toFile(), PackageAccess.READ)) {
                rejectMacros(pkg);
                try (XSSFWorkbook workbook = new XSSFWorkbook(pkg)) {
                    rejectFormulas(workbook);
                    Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
                    if (sheet == null) {
                        throw new ValidationException("IMPORT_EMPTY_FILE", null, List.of(
                                new FieldError("file", "IMPORT_EMPTY_FILE", schemaHelper.rowMessage("IMPORT_EMPTY_FILE", "Tệp Excel trống"))));
                    }

                    StagedImportPayload staged = group.getKind() == CatalogGroupKind.TREE
                            ? parseAndValidateTree(group, sheet)
                            : parseAndValidateSimple(group, sheet);

                    if (staged.totalRows() == 0) {
                        throw new ValidationException("IMPORT_EMPTY_FILE", null, List.of(
                                new FieldError("file", "IMPORT_EMPTY_FILE", schemaHelper.rowMessage("IMPORT_EMPTY_FILE", "Tệp Excel không có dòng dữ liệu"))));
                    }

                    UUID previewId = Ids.newId();
                    Instant now = Instant.now(clock);
                    Instant expiresAt = now.plus(PREVIEW_TTL);
                    String payloadJson = serializeJson(staged);

                    jdbcTemplate.update(
                            """
                            INSERT INTO catalog.catalog_import_preview
                                (id, group_id, status, total_rows, valid_rows, error_rows, payload, created_by, created_at, expires_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                            """,
                            previewId,
                            groupId,
                            PREVIEW_STATUS_PREVIEWED,
                            staged.totalRows(),
                            staged.validRows(),
                            staged.errorRows(),
                            payloadJson,
                            actor.id(),
                            Timestamp.from(now),
                            Timestamp.from(expiresAt));

                    return new CatalogImportPreviewResponse(
                            previewId,
                            groupId,
                            group.getCode(),
                            group.getKind(),
                            staged.totalRows(),
                            staged.validRows(),
                            staged.errorRows(),
                            staged.errorRows() == 0 && staged.totalRows() > 0,
                            staged.errors());
                }
            }
        } catch (ValidationException | ConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("IMPORT_INVALID_FILE", null, List.of(
                    new FieldError("file", "IMPORT_INVALID_FILE", schemaHelper.rowMessage("IMPORT_INVALID_FILE", "Tệp Excel không hợp lệ"))));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Transactional
    public CatalogImportResultResponse confirmImport(UUID groupId, UUID previewId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.executeOnce(
                    idempotencyKey.trim(),
                    "CATALOG_IMPORT:" + groupId,
                    previewId.toString(),
                    CatalogImportResultResponse.class,
                    () -> doConfirmImport(groupId, previewId));
        }
        return doConfirmImport(groupId, previewId);
    }

    private CatalogImportResultResponse doConfirmImport(UUID groupId, UUID previewId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (group.getKind() == CatalogGroupKind.COMBINATION) {
            throw new ConflictException("IMPORT_NOT_SUPPORTED");
        }
        if (group.getStatus() != CatalogStatus.ACTIVE) {
            throw new ConflictException("CATALOG_GROUP_INACTIVE");
        }

        PreviewRecord preview = jdbcTemplate.query(
                        """
                        SELECT id, group_id, status, total_rows, error_rows, payload::text AS payload,
                               result_payload::text AS result_payload, expires_at
                        FROM catalog.catalog_import_preview
                        WHERE id = ? AND group_id = ?
                        FOR UPDATE
                        """,
                        rs -> {
                            if (!rs.next()) {
                                return null;
                            }
                            return new PreviewRecord(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("group_id", UUID.class),
                                    rs.getShort("status"),
                                    rs.getInt("total_rows"),
                                    rs.getInt("error_rows"),
                                    rs.getString("payload"),
                                    rs.getString("result_payload"),
                                    rs.getTimestamp("expires_at").toInstant());
                        },
                        previewId,
                        groupId);

        if (preview == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        if (preview.status() == PREVIEW_STATUS_CONFIRMED && preview.resultPayload() != null) {
            return deserializeJson(preview.resultPayload(), CatalogImportResultResponse.class);
        }
        Instant now = Instant.now(clock);
        if (preview.expiresAt().isBefore(now)) {
            throw new ConflictException("IMPORT_PREVIEW_EXPIRED");
        }

        StagedImportPayload staged = deserializeJson(preview.payload(), StagedImportPayload.class);
        if (preview.errorRows() > 0 || !staged.errors().isEmpty()) {
            throw new CatalogConflictException("IMPORT_HAS_ERRORS", toRowFieldErrors(staged.errors()));
        }

        CatalogImportResultResponse result = group.getKind() == CatalogGroupKind.TREE
                ? applyTreeImport(group, staged, previewId, actor.id(), now)
                : applySimpleImport(group, staged, previewId, actor.id(), now);

        jdbcTemplate.update(
                """
                UPDATE catalog.catalog_import_preview
                SET status = ?,
                    confirmed_at = ?,
                    result_payload = ?::jsonb
                WHERE id = ?
                """,
                PREVIEW_STATUS_CONFIRMED,
                Timestamp.from(now),
                serializeJson(result),
                previewId);

        catalogCacheService.evictGroupAndDependents(groupId, group.getCodeNormalized());
        return result;
    }

    private void exportSimple(CatalogGroup group, Sheet sheet, CellStyle headerStyle) {
        List<String> headers = schemaHelper.simpleHeaders();
        writeHeaderRow(sheet, headers, headerStyle);

        List<CatalogItem> items = jdbcTemplate.query(
                """
                SELECT id, code, name_vi, name_en, description, status
                FROM catalog.catalog_item
                WHERE group_id = ?
                ORDER BY display_order ASC, code_normalized ASC, id ASC
                """,
                (rs, rowNum) -> new CatalogItem(
                        rs.getObject("id", UUID.class),
                        group.getId(),
                        rs.getString("code"),
                        1,
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        rs.getString("description"),
                        rowNum,
                        CatalogStatus.fromCode(rs.getShort("status")),
                        false,
                        null,
                        (short) 1,
                        "/",
                        Instant.EPOCH,
                        null),
                group.getId());

        int r = 1;
        for (CatalogItem item : items) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(safe(item.getCode()));
            row.createCell(1).setCellValue(safe(item.getNameVi()));
            row.createCell(2).setCellValue(safe(item.getNameEn()));
            row.createCell(3).setCellValue(safe(item.getDescription()));
            row.createCell(4).setCellValue(schemaHelper.formatStatus(item.getStatus()));
        }
        setColumnWidths(sheet, headers.size());
    }

    private void exportCombination(CatalogGroup group, Sheet sheet, CellStyle headerStyle) {
        List<String> headers = schemaHelper.combinationExportHeaders();
        writeHeaderRow(sheet, headers, headerStyle);

        List<ExportComboRow> items = jdbcTemplate.query(
                """
                SELECT id, code, name_vi, name_en, description, status
                FROM catalog.catalog_item
                WHERE group_id = ?
                ORDER BY display_order ASC, code_normalized ASC, id ASC
                """,
                (rs, rowNum) -> new ExportComboRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        rs.getString("description"),
                        CatalogStatus.fromCode(rs.getShort("status"))),
                group.getId());

        Map<UUID, CombinationVersionResponse> combos = items.isEmpty()
                ? Map.of()
                : catalogSupportDao.loadCurrentCombinations(items.stream().map(ExportComboRow::id).toList());

        int r = 1;
        for (ExportComboRow item : items) {
            CombinationVersionResponse ver = combos.get(item.id());
            String examCode = ver == null ? "" : safe(ver.examCode());
            String subjectCodes = ver == null
                    ? ""
                    : ver.subjects().stream()
                            .map(CombinationSubjectResponse::code)
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("");
            String sourceNote = ver == null ? "" : safe(ver.sourceNote());
            String versionNo = ver == null ? "" : String.valueOf(ver.versionNo());

            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(safe(item.code()));
            row.createCell(1).setCellValue(safe(item.nameVi()));
            row.createCell(2).setCellValue(safe(item.nameEn()));
            row.createCell(3).setCellValue(examCode);
            row.createCell(4).setCellValue(subjectCodes);
            row.createCell(5).setCellValue(sourceNote);
            row.createCell(6).setCellValue(versionNo);
            row.createCell(7).setCellValue(schemaHelper.formatStatus(item.status()));
            row.createCell(8).setCellValue(safe(item.description()));
        }
        setColumnWidths(sheet, headers.size());
    }

    private void exportTree(CatalogGroup group, Sheet sheet, CellStyle headerStyle) {
        List<CatalogGroupRefResponse> refGroups = catalogSupportDao
                .loadGroupRefs(List.of(group.getId()))
                .getOrDefault(group.getId(), List.of());
        List<String> levels = group.getLevels() == null ? List.of() : group.getLevels();

        List<String> headers = new ArrayList<>();
        for (CatalogGroupRefResponse refGroup : refGroups) {
            headers.add(refGroup.nameVi());
        }
        for (String level : levels) {
            headers.add(schemaHelper.treeLevelCodeHeader(level));
            headers.add(schemaHelper.treeLevelNameViHeader(level));
            headers.add(schemaHelper.treeLevelNameEnHeader(level));
        }
        writeHeaderRow(sheet, headers, headerStyle);

        List<ExportTreeNode> allNodes = jdbcTemplate.query(
                """
                SELECT id, parent_id, depth, code, name_vi, name_en
                FROM catalog.catalog_item
                WHERE group_id = ?
                ORDER BY depth ASC, display_order ASC, code_normalized ASC, id ASC
                """,
                (rs, rowNum) -> new ExportTreeNode(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        rs.getShort("depth"),
                        rs.getString("code"),
                        rs.getString("name_vi"),
                        rs.getString("name_en")),
                group.getId());

        if (allNodes.isEmpty()) {
            setColumnWidths(sheet, headers.size());
            return;
        }

        Map<UUID, ExportTreeNode> nodeById = new LinkedHashMap<>();
        Map<UUID, List<ExportTreeNode>> childrenByParent = new HashMap<>();
        List<ExportTreeNode> roots = new ArrayList<>();
        for (ExportTreeNode n : allNodes) {
            nodeById.put(n.id(), n);
            if (n.parentId() == null) {
                roots.add(n);
            } else {
                childrenByParent.computeIfAbsent(n.parentId(), ignored -> new ArrayList<>()).add(n);
            }
        }

        List<UUID> rootIds = roots.stream().map(ExportTreeNode::id).toList();
        Map<UUID, List<CatalogItemRefResponse>> rootOwnRefs = catalogSupportDao.loadOwnRefs(rootIds);

        List<ExportTreeNode> leaves = new ArrayList<>();
        for (ExportTreeNode root : roots) {
            collectLeavesDfs(root, childrenByParent, leaves);
        }

        int r = 1;
        for (ExportTreeNode leaf : leaves) {
            List<ExportTreeNode> chain = new ArrayList<>(leaf.depth());
            ExportTreeNode cursor = leaf;
            while (cursor != null) {
                chain.add(cursor);
                cursor = cursor.parentId() == null ? null : nodeById.get(cursor.parentId());
            }
            Collections.reverse(chain);
            ExportTreeNode root = chain.getFirst();
            List<CatalogItemRefResponse> refs = rootOwnRefs.getOrDefault(root.id(), List.of());

            Map<UUID, List<String>> refCodesByGroup = new HashMap<>();
            for (CatalogItemRefResponse ref : refs) {
                refCodesByGroup
                        .computeIfAbsent(ref.refGroupId(), ignored -> new ArrayList<>())
                        .add(ref.code());
            }

            Row row = sheet.createRow(r++);
            int col = 0;
            for (CatalogGroupRefResponse refGroup : refGroups) {
                List<String> codes = refCodesByGroup.getOrDefault(refGroup.refGroupId(), List.of());
                row.createCell(col++).setCellValue(String.join("; ", codes));
            }
            for (int d = 0; d < levels.size(); d++) {
                if (d < chain.size()) {
                    ExportTreeNode node = chain.get(d);
                    row.createCell(col++).setCellValue(safe(node.code()));
                    row.createCell(col++).setCellValue(safe(node.nameVi()));
                    row.createCell(col++).setCellValue(safe(node.nameEn()));
                } else {
                    row.createCell(col++).setCellValue("");
                    row.createCell(col++).setCellValue("");
                    row.createCell(col++).setCellValue("");
                }
            }
        }
        setColumnWidths(sheet, headers.size());
    }

    private static void collectLeavesDfs(
            ExportTreeNode node,
            Map<UUID, List<ExportTreeNode>> childrenByParent,
            List<ExportTreeNode> outLeaves) {
        List<ExportTreeNode> children = childrenByParent.get(node.id());
        if (children == null || children.isEmpty()) {
            outLeaves.add(node);
            return;
        }
        for (ExportTreeNode child : children) {
            collectLeavesDfs(child, childrenByParent, outLeaves);
        }
    }

    private StagedImportPayload parseAndValidateSimple(CatalogGroup group, Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new ValidationException("IMPORT_INVALID_HEADER", null, List.of(
                    new FieldError("file", "IMPORT_INVALID_HEADER", schemaHelper.rowMessage("IMPORT_INVALID_HEADER", "Thiếu dòng tiêu đề"))));
        }

        int codeCol = -1;
        int nameViCol = -1;
        int nameEnCol = -1;
        int descCol = -1;
        int statusCol = -1;
        DataFormatter formatter = new DataFormatter();

        for (Cell cell : headerRow) {
            String norm = CatalogExcelSchemaHelper.normalizeHeader(formatter.formatCellValue(cell));
            switch (norm) {
                case "mã", "ma", "code" -> codeCol = cell.getColumnIndex();
                case "tên tiếng việt", "ten tieng viet", "namevi", "name_vi" -> nameViCol = cell.getColumnIndex();
                case "tên tiếng anh", "ten tieng anh", "nameen", "name_en" -> nameEnCol = cell.getColumnIndex();
                case "mô tả", "mo ta", "description" -> descCol = cell.getColumnIndex();
                case "trạng thái", "trang thai", "status" -> statusCol = cell.getColumnIndex();
                default -> {
                }
            }
        }

        if (codeCol < 0 || nameViCol < 0) {
            throw new ValidationException("IMPORT_INVALID_HEADER", null, List.of(
                    new FieldError("file", "IMPORT_INVALID_HEADER", schemaHelper.rowMessage("IMPORT_INVALID_HEADER", "Thiếu cột Mã hoặc Tên tiếng Việt"))));
        }

        Set<String> existingCodes = loadExistingCodesNormalized(group.getId());
        Set<String> seenInFile = new HashSet<>();

        List<StagedSimpleRow> simpleRows = new ArrayList<>();
        List<CatalogImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int validRows = 0;
        int errorRows = 0;

        Iterator<Row> rowIt = sheet.rowIterator();
        while (rowIt.hasNext()) {
            Row row = rowIt.next();
            int rowIdx = row.getRowNum();
            if (rowIdx == 0) {
                continue;
            }
            String codeRaw = readCellText(row, codeCol, formatter, null);
            String nameViRaw = readCellText(row, nameViCol, formatter, null);
            String nameEnRaw = nameEnCol >= 0 ? readCellText(row, nameEnCol, formatter, null) : null;
            String descRaw = descCol >= 0 ? readCellText(row, descCol, formatter, null) : null;
            String statusRaw = statusCol >= 0 ? readCellText(row, statusCol, formatter, null) : null;

            if (isAllBlank(codeRaw, nameViRaw, nameEnRaw, descRaw, statusRaw)) {
                continue;
            }
            totalRows++;
            if (totalRows > MAX_IMPORT_ROWS) {
                throw new ValidationException("IMPORT_ROW_LIMIT_EXCEEDED", null, List.of(
                        new FieldError("file", "IMPORT_ROW_LIMIT_EXCEEDED", schemaHelper.rowMessage("IMPORT_ROW_LIMIT_EXCEEDED", "Vượt quá 5.000 dòng"))));
            }

            int rowNo = rowIdx + 1;
            int errBefore = errors.size();

            String trimmedCode = CatalogNormalizer.trimToNull(codeRaw);
            String codeNorm = null;
            if (trimmedCode == null) {
                errors.add(new CatalogImportRowError(rowNo, "code", "REQUIRED", "Mã giá trị không được để trống"));
            } else if (!CatalogNormalizer.CODE_PATTERN.matcher(trimmedCode).matches()) {
                errors.add(new CatalogImportRowError(rowNo, "code", "CODE_INVALID", "Mã không đúng định dạng"));
            } else {
                codeNorm = CatalogNormalizer.normalizeCode(trimmedCode);
                if (!seenInFile.add(codeNorm)) {
                    errors.add(new CatalogImportRowError(rowNo, "code", "CATALOG_CODE_DUPLICATE_IN_FILE", "Mã bị trùng trong tệp"));
                } else if (existingCodes.contains(codeNorm)) {
                    errors.add(new CatalogImportRowError(
                            rowNo,
                            "code",
                            "CATALOG_CODE_TAKEN",
                            schemaHelper.rowMessage("CATALOG_CODE_TAKEN", "Mã giá trị đã tồn tại")));
                }
            }

            String trimmedNameVi = CatalogNormalizer.trimToNull(nameViRaw);
            if (trimmedNameVi == null) {
                errors.add(new CatalogImportRowError(rowNo, "nameVi", "REQUIRED", "Tên tiếng Việt không được để trống"));
            } else if (trimmedNameVi.length() > 255) {
                errors.add(new CatalogImportRowError(rowNo, "nameVi", "SIZE", "Tên tiếng Việt tối đa 255 ký tự"));
            }

            String trimmedNameEn = CatalogNormalizer.trimToNull(nameEnRaw);
            if (trimmedNameEn != null && trimmedNameEn.length() > 255) {
                errors.add(new CatalogImportRowError(rowNo, "nameEn", "SIZE", "Tên tiếng Anh tối đa 255 ký tự"));
            }

            String trimmedDesc = CatalogNormalizer.trimToNull(descRaw);
            if (trimmedDesc != null && trimmedDesc.length() > 1000) {
                errors.add(new CatalogImportRowError(rowNo, "description", "SIZE", "Mô tả tối đa 1000 ký tự"));
            }

            CatalogStatus parsedStatus = schemaHelper.parseStatus(statusRaw);
            if (parsedStatus == null) {
                errors.add(new CatalogImportRowError(rowNo, "status", "STATUS_INVALID", "Trạng thái không hợp lệ"));
            }

            if (errors.size() == errBefore) {
                validRows++;
                simpleRows.add(new StagedSimpleRow(
                        rowNo,
                        trimmedCode,
                        trimmedNameVi,
                        trimmedNameEn,
                        trimmedDesc,
                        parsedStatus.name()));
            } else {
                errorRows++;
            }
        }

        return new StagedImportPayload(totalRows, validRows, errorRows, errors, simpleRows, List.of());
    }

    private StagedImportPayload parseAndValidateTree(CatalogGroup group, Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new ValidationException("IMPORT_INVALID_HEADER", null, List.of(
                    new FieldError("file", "IMPORT_INVALID_HEADER", schemaHelper.rowMessage("IMPORT_INVALID_HEADER", "Thiếu dòng tiêu đề"))));
        }

        List<String> levels = group.getLevels() == null ? List.of() : group.getLevels();
        int levelCount = levels.size();
        List<CatalogGroupRefResponse> refGroups = catalogSupportDao
                .loadGroupRefs(List.of(group.getId()))
                .getOrDefault(group.getId(), List.of());

        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> headerIndexByNorm = new HashMap<>();
        for (Cell cell : headerRow) {
            String norm = CatalogExcelSchemaHelper.normalizeHeader(formatter.formatCellValue(cell));
            if (!norm.isEmpty()) {
                headerIndexByNorm.putIfAbsent(norm, cell.getColumnIndex());
            }
        }

        int[] refCols = new int[refGroups.size()];
        for (int i = 0; i < refGroups.size(); i++) {
            CatalogGroupRefResponse rg = refGroups.get(i);
            Integer idx = headerIndexByNorm.get(CatalogExcelSchemaHelper.normalizeHeader(rg.nameVi()));
            if (idx == null) {
                idx = headerIndexByNorm.get(CatalogExcelSchemaHelper.normalizeHeader(rg.code()));
            }
            refCols[i] = idx == null ? -1 : idx;
        }

        int[] codeCols = new int[levelCount];
        int[] nameViCols = new int[levelCount];
        int[] nameEnCols = new int[levelCount];
        for (int d = 0; d < levelCount; d++) {
            String levelName = levels.get(d);
            String normLevel = CatalogExcelSchemaHelper.normalizeHeader(levelName);
            Integer codeIdx = headerIndexByNorm.get(
                    CatalogExcelSchemaHelper.normalizeHeader(schemaHelper.treeLevelCodeHeader(levelName)));
            if (codeIdx == null) {
                codeIdx = headerIndexByNorm.get("mã " + normLevel);
            }

            Integer nameViIdx = headerIndexByNorm.get(
                    CatalogExcelSchemaHelper.normalizeHeader(schemaHelper.treeLevelNameViHeader(levelName)));
            if (nameViIdx == null) {
                nameViIdx = headerIndexByNorm.get(normLevel);
            }
            if (nameViIdx == null) {
                nameViIdx = headerIndexByNorm.get("tên " + normLevel);
            }

            Integer nameEnIdx = headerIndexByNorm.get(
                    CatalogExcelSchemaHelper.normalizeHeader(schemaHelper.treeLevelNameEnHeader(levelName)));
            if (nameEnIdx == null) {
                nameEnIdx = headerIndexByNorm.get("tên " + normLevel + " (en)");
            }

            if (codeIdx == null || nameViIdx == null) {
                throw new ValidationException("IMPORT_INVALID_HEADER", null, List.of(
                        new FieldError("file", "IMPORT_INVALID_HEADER", schemaHelper.rowMessage("IMPORT_INVALID_HEADER", "Thiếu cột của cấp " + levelName))));
            }
            codeCols[d] = codeIdx;
            nameViCols[d] = nameViIdx;
            nameEnCols[d] = nameEnIdx == null ? -1 : nameEnIdx;
        }

        Map<CellKey, String> mergedValues = buildMergedValueMap(sheet, formatter);
        Map<String, ExistingTreeNode> existingByCode = loadExistingTreeNodes(group.getId());
        Map<UUID, Map<String, UUID>> activeRefItemsByGroup = loadActiveRefItemsByGroup(refGroups);

        // Maintain staged nodes introduced across rows in this file
        Map<String, StagedNodeTracker> stagedByCode = new HashMap<>();
        List<StagedTreeNode> orderedStagedNodes = new ArrayList<>();
        Set<String> reusedExistingCodes = new HashSet<>();
        Map<String, Set<UUID>> rootRefsByCodeNorm = new LinkedHashMap<>();
        List<CatalogImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int validRows = 0;
        int errorRows = 0;

        LevelCell[] prevLevels = new LevelCell[levelCount];

        Iterator<Row> rowIt = sheet.rowIterator();
        while (rowIt.hasNext()) {
            Row row = rowIt.next();
            int rowIdx = row.getRowNum();
            if (rowIdx == 0) {
                continue;
            }

            LevelCell[] rawLevels = new LevelCell[levelCount];
            int firstExplicit = -1;
            int lastExplicit = -1;
            for (int d = 0; d < levelCount; d++) {
                String c = CatalogNormalizer.trimToNull(readCellText(row, codeCols[d], formatter, mergedValues));
                String nv = CatalogNormalizer.trimToNull(readCellText(row, nameViCols[d], formatter, mergedValues));
                String ne = nameEnCols[d] >= 0
                        ? CatalogNormalizer.trimToNull(readCellText(row, nameEnCols[d], formatter, mergedValues))
                        : null;
                rawLevels[d] = new LevelCell(c, nv, ne);
                if (c != null || nv != null || ne != null) {
                    if (firstExplicit < 0) {
                        firstExplicit = d;
                    }
                    lastExplicit = d;
                }
            }

            String[] rawRefCells = new String[refGroups.size()];
            boolean anyRefNonBlank = false;
            for (int i = 0; i < refGroups.size(); i++) {
                if (refCols[i] >= 0) {
                    rawRefCells[i] = CatalogNormalizer.trimToNull(readCellText(row, refCols[i], formatter, mergedValues));
                    if (rawRefCells[i] != null) {
                        anyRefNonBlank = true;
                    }
                }
            }

            if (firstExplicit < 0 && !anyRefNonBlank) {
                continue;
            }
            totalRows++;
            if (totalRows > MAX_IMPORT_ROWS) {
                throw new ValidationException("IMPORT_ROW_LIMIT_EXCEEDED", null, List.of(
                        new FieldError("file", "IMPORT_ROW_LIMIT_EXCEEDED", schemaHelper.rowMessage("IMPORT_ROW_LIMIT_EXCEEDED", "Vượt quá 5.000 dòng"))));
            }

            int rowNo = rowIdx + 1;
            int errBefore = errors.size();

            if (firstExplicit < 0) {
                errors.add(new CatalogImportRowError(rowNo, "level1Code", "REQUIRED", "Thiếu thông tin nút cây"));
                errorRows++;
                continue;
            }

            LevelCell[] resolved = new LevelCell[levelCount];
            for (int d = 0; d < firstExplicit; d++) {
                if (prevLevels[d] == null) {
                    errors.add(new CatalogImportRowError(
                            rowNo, "level" + (d + 1) + "Code", "PARENT_REQUIRED", "Thiếu thông tin cấp cha " + levels.get(d)));
                } else {
                    resolved[d] = prevLevels[d];
                }
            }
            for (int d = firstExplicit; d <= lastExplicit; d++) {
                LevelCell raw = rawLevels[d];
                if (raw.code() == null
                        && raw.nameVi() == null
                        && raw.nameEn() == null
                        && d < lastExplicit
                        && prevLevels[d] != null
                        && ancestorsMatchPrev(resolved, prevLevels, d)) {
                    resolved[d] = prevLevels[d];
                } else {
                    resolved[d] = raw;
                }
            }

            if (errors.size() > errBefore) {
                errorRows++;
                continue;
            }

            // Validate resolved levels 0..lastExplicit
            String parentCodeNorm = null;
            UUID parentExistingId = null;
            boolean parentInactiveInDb = false;

            List<StagedTreeNode> rowNewNodes = new ArrayList<>();
            List<String> rowReusedCodes = new ArrayList<>();
            for (int d = 0; d <= lastExplicit; d++) {
                int depth = d + 1;
                LevelCell cell = resolved[d];
                String colCode = "level" + depth + "Code";
                String colNameVi = "level" + depth + "NameVi";
                String colNameEn = "level" + depth + "NameEn";

                if (cell.code() == null) {
                    errors.add(new CatalogImportRowError(rowNo, colCode, "REQUIRED", "Thiếu mã " + levels.get(d)));
                    break;
                }
                if (!CatalogNormalizer.CODE_PATTERN.matcher(cell.code()).matches()) {
                    errors.add(new CatalogImportRowError(rowNo, colCode, "CODE_INVALID", "Mã không đúng định dạng"));
                    break;
                }
                if (cell.nameVi() == null) {
                    errors.add(new CatalogImportRowError(rowNo, colNameVi, "REQUIRED", "Thiếu tên " + levels.get(d)));
                    break;
                }
                if (cell.nameVi().length() > 255) {
                    errors.add(new CatalogImportRowError(rowNo, colNameVi, "SIZE", "Tên tiếng Việt tối đa 255 ký tự"));
                    break;
                }
                if (cell.nameEn() != null && cell.nameEn().length() > 255) {
                    errors.add(new CatalogImportRowError(rowNo, colNameEn, "SIZE", "Tên tiếng Anh tối đa 255 ký tự"));
                    break;
                }

                String codeNorm = CatalogNormalizer.normalizeCode(cell.code());
                ExistingTreeNode existing = existingByCode.get(codeNorm);
                if (existing != null) {
                    boolean sameParent = depth == 1
                            ? existing.parentId() == null
                            : Objects.equals(existing.parentId(), parentExistingId);
                    if (existing.depth() != depth || !sameParent) {
                        errors.add(new CatalogImportRowError(
                                rowNo,
                                colCode,
                                "CATALOG_CODE_TAKEN",
                                schemaHelper.rowMessage("CATALOG_CODE_TAKEN", "Mã nút đã tồn tại ở vị trí khác")));
                        break;
                    }
                    if (d < lastExplicit && existing.status() != CatalogStatus.ACTIVE) {
                        // Child might be new under an inactive existing parent
                        parentInactiveInDb = true;
                    }
                    rowReusedCodes.add(codeNorm);
                    parentCodeNorm = codeNorm;
                    parentExistingId = existing.id();
                } else {
                    if (parentInactiveInDb) {
                        errors.add(new CatalogImportRowError(
                                rowNo,
                                colCode,
                                "CATALOG_PARENT_INACTIVE",
                                schemaHelper.rowMessage("CATALOG_PARENT_INACTIVE", "Nút cha đang ngừng sử dụng")));
                        break;
                    }
                    StagedNodeTracker staged = stagedByCode.get(codeNorm);
                    if (staged != null) {
                        if (staged.depth() != depth || !Objects.equals(staged.parentCodeNorm(), parentCodeNorm)) {
                            errors.add(new CatalogImportRowError(
                                    rowNo,
                                    colCode,
                                    "CATALOG_CODE_TAKEN",
                                    schemaHelper.rowMessage("CATALOG_CODE_TAKEN", "Mã nút bị trùng ở vị trí khác trong tệp")));
                            break;
                        }
                    } else {
                        StagedTreeNode newNode = new StagedTreeNode(
                                cell.code(),
                                codeNorm,
                                cell.nameVi(),
                                cell.nameEn(),
                                (short) depth,
                                parentCodeNorm);
                        rowNewNodes.add(newNode);
                    }
                    parentCodeNorm = codeNorm;
                    parentExistingId = null;
                }
            }

            // Validate linked-catalog codes
            Set<UUID> rowRefIds = new LinkedHashSet<>();
            for (int i = 0; i < refGroups.size(); i++) {
                String rawRefCell = rawRefCells[i];
                if (rawRefCell == null) {
                    continue;
                }
                CatalogGroupRefResponse refGroup = refGroups.get(i);
                Map<String, UUID> activeItems = activeRefItemsByGroup.getOrDefault(refGroup.refGroupId(), Map.of());
                String[] tokens = rawRefCell.split(";");
                for (String token : tokens) {
                    String trimmedToken = CatalogNormalizer.trimToNull(token);
                    if (trimmedToken == null) {
                        continue;
                    }
                    UUID refItemId = activeItems.get(CatalogNormalizer.normalizeCode(trimmedToken));
                    if (refItemId == null) {
                        errors.add(new CatalogImportRowError(
                                rowNo,
                                refGroup.code(),
                                "CATALOG_REF_INVALID",
                                "Giá trị liên kết '" + trimmedToken + "' không tồn tại hoặc đã ngừng trong " + refGroup.nameVi()));
                    } else {
                        rowRefIds.add(refItemId);
                    }
                }
            }

            if (errors.size() == errBefore) {
                for (StagedTreeNode n : rowNewNodes) {
                    stagedByCode.put(n.codeNormalized(), new StagedNodeTracker(n.depth(), n.parentCodeNormalized()));
                    orderedStagedNodes.add(n);
                }
                reusedExistingCodes.addAll(rowReusedCodes);
                if (!rowRefIds.isEmpty()) {
                    String rootCodeNorm = CatalogNormalizer.normalizeCode(resolved[0].code());
                    rootRefsByCodeNorm.computeIfAbsent(rootCodeNorm, ignored -> new LinkedHashSet<>()).addAll(rowRefIds);
                }
                prevLevels = resolved;
                validRows++;
            } else {
                errorRows++;
            }
        }

        if (existingByCode.size() + orderedStagedNodes.size() > MAX_TREE_NODES) {
            throw new ConflictException("CATALOG_TREE_TOO_LARGE");
        }

        List<StagedRootRefs> rootRefsList = rootRefsByCodeNorm.entrySet().stream()
                .map(e -> new StagedRootRefs(e.getKey(), List.copyOf(e.getValue())))
                .toList();

        return new StagedImportPayload(
                totalRows,
                validRows,
                errorRows,
                errors,
                List.of(),
                orderedStagedNodes,
                rootRefsList,
                reusedExistingCodes.size());
    }

    private CatalogImportResultResponse applySimpleImport(
            CatalogGroup group,
            StagedImportPayload staged,
            UUID previewId,
            UUID actorId,
            Instant now) {
        Set<String> existingCodes = loadExistingCodesNormalized(group.getId());
        List<CatalogImportRowError> recheckErrors = new ArrayList<>();
        for (StagedSimpleRow row : staged.simpleRows()) {
            String norm = CatalogNormalizer.normalizeCode(row.code());
            if (existingCodes.contains(norm)) {
                recheckErrors.add(new CatalogImportRowError(
                        row.rowNo(),
                        "code",
                        "CATALOG_CODE_TAKEN",
                        schemaHelper.rowMessage("CATALOG_CODE_TAKEN", "Mã giá trị đã tồn tại")));
            }
        }
        if (!recheckErrors.isEmpty()) {
            throw new CatalogConflictException("IMPORT_HAS_ERRORS", toRowFieldErrors(recheckErrors));
        }

        int nextValue = Math.max(group.getLastValue(), catalogItemRepository.findMaxValueByGroupId(group.getId())) + 1;
        int nextOrder = catalogItemRepository.findMaxDisplayOrder(group.getId(), null) + 1;

        List<CatalogItem> toSave = new ArrayList<>(staged.simpleRows().size());
        for (StagedSimpleRow row : staged.simpleRows()) {
            UUID itemId = Ids.newId();
            CatalogItem item = new CatalogItem(
                    itemId,
                    group.getId(),
                    row.code(),
                    nextValue++,
                    row.nameVi(),
                    row.nameEn(),
                    row.description(),
                    nextOrder++,
                    CatalogStatus.valueOf(row.status()),
                    false,
                    null,
                    (short) 1,
                    "/" + itemId + "/",
                    now,
                    actorId);
            toSave.add(item);
        }
        catalogItemRepository.saveAll(toSave);
        catalogItemRepository.flush();
        catalogGroupRepository.advanceLastValue(group.getId(), nextValue - 1);

        for (CatalogItem item : toSave) {
            recordItemCreatedAudit(item);
        }
        return new CatalogImportResultResponse(
                previewId, group.getId(), group.getCode(), staged.totalRows(), toSave.size(), 0);
    }

    private CatalogImportResultResponse applyTreeImport(
            CatalogGroup group,
            StagedImportPayload staged,
            UUID previewId,
            UUID actorId,
            Instant now) {
        Map<String, ExistingTreeNode> existingByCode = loadExistingTreeNodes(group.getId());
        if (existingByCode.size() + staged.treeNodes().size() > MAX_TREE_NODES) {
            throw new ConflictException("CATALOG_TREE_TOO_LARGE");
        }

        int nextValue = Math.max(group.getLastValue(), catalogItemRepository.findMaxValueByGroupId(group.getId())) + 1;
        Map<UUID, Integer> nextOrderByParent = new HashMap<>();

        List<StagedTreeNode> sortedNodes = new ArrayList<>(staged.treeNodes());
        sortedNodes.sort((a, b) -> Short.compare(a.depth(), b.depth()));

        Map<String, ExistingTreeNode> resolvedNodes = new HashMap<>(existingByCode);
        List<CatalogItem> createdItems = new ArrayList<>();
        int reusedCount = staged.reusedNodeCount();

        for (StagedTreeNode node : sortedNodes) {
            ExistingTreeNode parent = node.parentCodeNormalized() == null
                    ? null
                    : resolvedNodes.get(node.parentCodeNormalized());
            UUID parentId = parent == null ? null : parent.id();

            ExistingTreeNode alreadyInDb = resolvedNodes.get(node.codeNormalized());
            if (alreadyInDb != null) {
                boolean sameParent = node.depth() == 1
                        ? alreadyInDb.parentId() == null
                        : Objects.equals(alreadyInDb.parentId(), parentId);
                if (alreadyInDb.depth() == node.depth() && sameParent) {
                    reusedCount++;
                    continue;
                }
                throw new CatalogConflictException("IMPORT_HAS_ERRORS", List.of(
                        new FieldError("code", "CATALOG_CODE_TAKEN", "Mã nút " + node.code() + " đã tồn tại ở vị trí khác")));
            }
            if (parent != null && parent.status() != CatalogStatus.ACTIVE) {
                throw new CatalogConflictException("IMPORT_HAS_ERRORS", List.of(
                        new FieldError("parentId", "CATALOG_PARENT_INACTIVE", "Nút cha đang ngừng sử dụng")));
            }

            UUID itemId = Ids.newId();
            String path = parent == null ? "/" + itemId + "/" : parent.path() + itemId + "/";
            int order = nextOrderByParent.computeIfAbsent(
                    parentId,
                    pid -> catalogItemRepository.findMaxDisplayOrder(group.getId(), pid) + 1);
            nextOrderByParent.put(parentId, order + 1);

            CatalogItem item = new CatalogItem(
                    itemId,
                    group.getId(),
                    node.code(),
                    nextValue++,
                    node.nameVi(),
                    node.nameEn(),
                    null,
                    order,
                    CatalogStatus.ACTIVE,
                    false,
                    parentId,
                    node.depth(),
                    path,
                    now,
                    actorId);
            createdItems.add(item);
            resolvedNodes.put(
                    node.codeNormalized(),
                    new ExistingTreeNode(itemId, parentId, node.depth(), path, CatalogStatus.ACTIVE));
        }

        if (!createdItems.isEmpty()) {
            catalogItemRepository.saveAll(createdItems);
            catalogItemRepository.flush();
            catalogGroupRepository.advanceLastValue(group.getId(), nextValue - 1);
            for (CatalogItem item : createdItems) {
                recordItemCreatedAudit(item);
            }
        }

        // Add-only refs on root nodes
        for (StagedRootRefs rootRefs : staged.rootRefs()) {
            ExistingTreeNode rootNode = resolvedNodes.get(rootRefs.rootCodeNormalized());
            if (rootNode == null || rootRefs.refItemIds().isEmpty()) {
                continue;
            }
            Set<UUID> existingOwnRefIds = new HashSet<>();
            for (CatalogItemRef existingRef : catalogItemRefRepository.findByIdItemId(rootNode.id())) {
                existingOwnRefIds.add(existingRef.getRefItemId());
            }
            List<CatalogItemRef> newRefs = new ArrayList<>();
            for (UUID refItemId : rootRefs.refItemIds()) {
                if (!existingOwnRefIds.contains(refItemId)) {
                    newRefs.add(new CatalogItemRef(rootNode.id(), refItemId, actorId, now));
                }
            }
            if (!newRefs.isEmpty()) {
                catalogItemRefRepository.saveAll(newRefs);
                catalogItemRefRepository.flush();
                auditRecorder.record(AuditEvent.builder(MODULE, "CATALOG_ITEM_REFS_UPDATED")
                        .target("CATALOG_ITEM", rootNode.id().toString(), rootRefs.rootCodeNormalized())
                        .after(Map.of("addedRefItemIds", newRefs.stream().map(r -> r.getRefItemId().toString()).toList()))
                        .build());
            }
        }

        return new CatalogImportResultResponse(
                previewId, group.getId(), group.getCode(), staged.totalRows(), createdItems.size(), reusedCount);
    }

    private Set<String> loadExistingCodesNormalized(UUID groupId) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT code_normalized FROM catalog.catalog_item WHERE group_id = ?",
                (rs, rowNum) -> rs.getString("code_normalized"),
                groupId));
    }

    private Map<String, ExistingTreeNode> loadExistingTreeNodes(UUID groupId) {
        Map<String, ExistingTreeNode> map = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, code_normalized, parent_id, depth, path, status FROM catalog.catalog_item WHERE group_id = ?",
                rs -> {
                    map.put(
                            rs.getString("code_normalized"),
                            new ExistingTreeNode(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("parent_id", UUID.class),
                                    rs.getShort("depth"),
                                    rs.getString("path"),
                                    CatalogStatus.fromCode(rs.getShort("status"))));
                },
                groupId);
        return map;
    }

    private Map<UUID, Map<String, UUID>> loadActiveRefItemsByGroup(List<CatalogGroupRefResponse> refGroups) {
        if (refGroups.isEmpty()) {
            return Map.of();
        }
        List<UUID> refGroupIds = refGroups.stream().map(CatalogGroupRefResponse::refGroupId).toList();
        String placeholders = String.join(",", Collections.nCopies(refGroupIds.size(), "?"));
        Map<UUID, Map<String, UUID>> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT i.id, i.group_id, i.code_normalized
                FROM catalog.catalog_item i
                JOIN catalog.catalog_group g ON g.id = i.group_id
                WHERE i.group_id IN (%s)
                  AND i.status = 1
                  AND g.status = 1
                """.formatted(placeholders),
                rs -> {
                    UUID gid = rs.getObject("group_id", UUID.class);
                    result.computeIfAbsent(gid, ignored -> new HashMap<>())
                            .put(rs.getString("code_normalized"), rs.getObject("id", UUID.class));
                },
                refGroupIds.toArray());
        return result;
    }

    private static void verifyZipSignature(Path tempFile) throws IOException {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(tempFile)) {
            int read = in.readNBytes(header, 0, 4);
            if (read < 4 || header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                throw new ValidationException("IMPORT_INVALID_FILE", null, List.of(
                        new FieldError("file", "IMPORT_INVALID_FILE", "Tệp tải lên không phải định dạng .xlsx hợp lệ")));
            }
        }
    }

    private static void configureZipSecureLimits() {
        ZipSecureFile.setMinInflateRatio(ZIP_MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(ZIP_MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxTextSize(ZIP_MAX_TEXT_SIZE);
    }

    private void rejectMacroZipEntries(Path tempFile) throws IOException {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(tempFile.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject")) {
                    throw new ValidationException("IMPORT_MACRO_NOT_ALLOWED", null, List.of(
                            new FieldError("file", "IMPORT_MACRO_NOT_ALLOWED", schemaHelper.rowMessage("IMPORT_MACRO_NOT_ALLOWED", "Không chấp nhận tệp chứa macro"))));
                }
            }
        }
    }

    private void rejectMacros(OPCPackage pkg) throws org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        for (PackagePart part : pkg.getParts()) {
            String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
            String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase(Locale.ROOT);
            if (partName.contains("vbaproject") || contentType.contains("macro") || contentType.contains("vbaproject")) {
                throw new ValidationException("IMPORT_MACRO_NOT_ALLOWED", null, List.of(
                        new FieldError("file", "IMPORT_MACRO_NOT_ALLOWED", schemaHelper.rowMessage("IMPORT_MACRO_NOT_ALLOWED", "Không chấp nhận tệp chứa macro"))));
            }
        }
    }

    private void rejectFormulas(XSSFWorkbook workbook) {
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        throw new ValidationException("IMPORT_FORMULA_NOT_ALLOWED", null, List.of(
                                new FieldError(
                                        "file",
                                        "IMPORT_FORMULA_NOT_ALLOWED",
                                        schemaHelper.rowMessage("IMPORT_FORMULA_NOT_ALLOWED", "Không chấp nhận công thức trong tệp Excel"))));
                    }
                }
            }
        }
    }

    private static Map<CellKey, String> buildMergedValueMap(Sheet sheet, DataFormatter formatter) {
        Map<CellKey, String> map = new HashMap<>();
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            Row firstRow = sheet.getRow(region.getFirstRow());
            if (firstRow == null) {
                continue;
            }
            Cell topLeft = firstRow.getCell(region.getFirstColumn());
            String val = topLeft == null ? "" : formatter.formatCellValue(topLeft);
            if (val == null || val.isBlank()) {
                continue;
            }
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    map.put(new CellKey(r, c), val);
                }
            }
        }
        return map;
    }

    private static String readCellText(
            Row row, int colIndex, DataFormatter formatter, Map<CellKey, String> mergedValues) {
        if (colIndex < 0) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        String direct = cell == null ? "" : formatter.formatCellValue(cell);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        if (mergedValues != null) {
            return mergedValues.get(new CellKey(row.getRowNum(), colIndex));
        }
        return null;
    }

    private static boolean ancestorsMatchPrev(LevelCell[] resolved, LevelCell[] prevLevels, int levelIndex) {
        for (int k = 0; k < levelIndex; k++) {
            if (resolved[k] == null || prevLevels[k] == null
                    || resolved[k].code() == null || prevLevels[k].code() == null) {
                return false;
            }
            String rc = CatalogNormalizer.normalizeCode(resolved[k].code());
            String pc = CatalogNormalizer.normalizeCode(prevLevels[k].code());
            if (!rc.equals(pc)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void writeHeaderRow(Sheet sheet, List<String> headers, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    private static void setColumnWidths(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.setColumnWidth(i, 24 * 256);
        }
    }

    private void recordItemCreatedAudit(CatalogItem item) {
        auditRecorder.record(AuditEvent.builder(MODULE, "CATALOG_ITEM_CREATED")
                .target("CATALOG_ITEM", item.getId().toString(), item.getCode())
                .after(Map.of(
                        "groupId", item.getGroupId().toString(),
                        "code", item.getCode(),
                        "value", item.getValue(),
                        "nameVi", item.getNameVi()))
                .build());
    }

    private static List<FieldError> toRowFieldErrors(List<CatalogImportRowError> errors) {
        return errors.stream()
                .map(e -> new FieldError(
                        "row[" + e.rowNo() + "]." + e.column(),
                        e.code(),
                        e.message()))
                .toList();
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize catalog import JSON", ex);
        }
    }

    private <T> T deserializeJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize catalog import JSON", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record ExportedWorkbook(String filename, byte[] bytes) {
    }

    private record ExportComboRow(
            UUID id, String code, String nameVi, String nameEn, String description, CatalogStatus status) {
    }

    private record ExportTreeNode(
            UUID id, UUID parentId, short depth, String code, String nameVi, String nameEn) {
    }

    private record CellKey(int row, int col) {
    }

    private record LevelCell(String code, String nameVi, String nameEn) {
    }

    private record ExistingTreeNode(
            UUID id, UUID parentId, short depth, String path, CatalogStatus status) {
    }

    private record StagedNodeTracker(short depth, String parentCodeNorm) {
    }

    private record PreviewRecord(
            UUID id,
            UUID groupId,
            short status,
            int totalRows,
            int errorRows,
            String payload,
            String resultPayload,
            Instant expiresAt) {
    }

    record StagedSimpleRow(
            int rowNo, String code, String nameVi, String nameEn, String description, String status) {
    }

    record StagedTreeNode(
            String code,
            String codeNormalized,
            String nameVi,
            String nameEn,
            short depth,
            String parentCodeNormalized) {
    }

    record StagedRootRefs(String rootCodeNormalized, List<UUID> refItemIds) {
    }

    record StagedImportPayload(
            int totalRows,
            int validRows,
            int errorRows,
            List<CatalogImportRowError> errors,
            List<StagedSimpleRow> simpleRows,
            List<StagedTreeNode> treeNodes,
            List<StagedRootRefs> rootRefs,
            int reusedNodeCount) {
        StagedImportPayload(
                int totalRows,
                int validRows,
                int errorRows,
                List<CatalogImportRowError> errors,
                List<StagedSimpleRow> simpleRows,
                List<StagedTreeNode> treeNodes) {
            this(totalRows, validRows, errorRows, errors, simpleRows, treeNodes, List.of(), 0);
        }
    }
}
