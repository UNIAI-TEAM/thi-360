package vn.unicom.thi360.identity.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.application.ImportExcelSchemaHelper.ImportColumn;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.ImportRowStatus;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;

@Component
public class ImportWorkbookCodec {

    public static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    public static final int MAX_DATA_ROWS = 2_000;

    private static final double ZIP_MIN_INFLATE_RATIO = 0.01d;
    private static final long ZIP_MAX_ENTRY_SIZE = 10L * 1024L * 1024L;
    private static final long ZIP_MAX_TEXT_SIZE = 10L * 1024L * 1024L;

    private static final String EXAMPLE_USERNAME = "nguyen.van.a";
    private static final String EXAMPLE_PASSWORD = "Thi360@1234";
    private static final String EXAMPLE_FULL_NAME = "Nguyễn Văn A";
    private static final String EXAMPLE_EMAIL = "nguyen.van.a@example.com";
    private static final String EXAMPLE_PHONE = "0901234567";
    private static final String EXAMPLE_CLASS_CODE = "LOP-A";

    private final ImportExcelSchemaHelper schemaHelper;

    public ImportWorkbookCodec(ImportExcelSchemaHelper schemaHelper) {
        this.schemaHelper = schemaHelper;
    }

    public byte[] buildTemplateWorkbook(Locale locale) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(schemaHelper.templateSheetName(locale));
            CellStyle headerStyle = createBoldStyle(workbook);

            List<String> headers = schemaHelper.templateHeaders(locale);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 24 * 256);
            }
            Cell noteHeaderCell = headerRow.createCell(headers.size());
            noteHeaderCell.setCellValue(schemaHelper.exampleNoteHeader(locale));
            noteHeaderCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(headers.size(), 52 * 256);

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue(EXAMPLE_USERNAME);
            Cell pwdCell = exampleRow.createCell(1);
            pwdCell.setCellValue(EXAMPLE_PASSWORD);
            exampleRow.createCell(2).setCellValue(EXAMPLE_FULL_NAME);
            exampleRow.createCell(3).setCellValue(EXAMPLE_EMAIL);
            exampleRow.createCell(4).setCellValue(EXAMPLE_PHONE);
            exampleRow.createCell(5).setCellValue(EXAMPLE_CLASS_CODE);
            exampleRow.createCell(6).setCellValue(schemaHelper.exampleNoteText(locale));

            CreationHelper factory = workbook.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = factory.createClientAnchor();
            anchor.setCol1(1);
            anchor.setCol2(4);
            anchor.setRow1(1);
            anchor.setRow2(4);
            Comment comment = drawing.createCellComment(anchor);
            comment.setString(factory.createRichTextString(schemaHelper.exampleNoteText(locale)));
            pwdCell.setCellComment(comment);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build import template workbook", ex);
        }
    }

    public List<ParsedImportRow> parseUploadedWorkbook(byte[] fileBytes, Locale locale) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw validationError("IMPORT_EMPTY_FILE", schemaHelper.message("IMPORT_EMPTY_FILE", "Tệp Excel trống", locale));
        }
        if (fileBytes.length > MAX_FILE_BYTES) {
            throw validationError(
                    "IMPORT_FILE_TOO_LARGE",
                    schemaHelper.message("IMPORT_FILE_TOO_LARGE", "Dung lượng tệp Excel vượt quá giới hạn cho phép (tối đa 5 MB)", locale));
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("thi360-user-import-", ".xlsx");
            Files.write(tempFile, fileBytes);
            verifyZipSignature(tempFile, locale);
            rejectMacroZipEntries(tempFile, locale);
            configureZipSecureLimits();

            try (OPCPackage pkg = OPCPackage.open(tempFile.toFile(), PackageAccess.READ)) {
                rejectMacros(pkg, locale);
                try (XSSFWorkbook workbook = new XSSFWorkbook(pkg)) {
                    rejectFormulas(workbook, locale);
                    Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
                    if (sheet == null) {
                        throw validationError(
                                "IMPORT_EMPTY_FILE",
                                schemaHelper.message("IMPORT_EMPTY_FILE", "Tệp Excel trống", locale));
                    }
                    return parseSheet(sheet, locale);
                }
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw validationError(
                    "IMPORT_INVALID_FILE",
                    schemaHelper.message("IMPORT_INVALID_FILE", "Tệp Excel không hợp lệ hoặc vượt quá giới hạn giải nén an toàn", locale));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temp file cleanup best effort
                }
            }
        }
    }

    public byte[] buildErrorsWorkbook(byte[] originalFileBytes, List<ImportRow> invalidRows, Locale locale) {
        configureZipSecureLimits();
        try (XSSFWorkbook sourceWb = new XSSFWorkbook(new ByteArrayInputStream(originalFileBytes));
                SXSSFWorkbook outWb = new SXSSFWorkbook(null, 200, false, true);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sourceSheet = sourceWb.getSheetAt(0);
            Sheet outSheet = outWb.createSheet(schemaHelper.templateSheetName(locale));
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            CellStyle headerStyle = createBoldStyle(outWb);

            Row sourceHeader = sourceSheet.getRow(0);
            List<Integer> copiedColIndices = new ArrayList<>();
            List<String> headers = new ArrayList<>();
            int sourceCols = sourceHeader == null ? 0 : sourceHeader.getLastCellNum();
            for (int c = 0; c < sourceCols; c++) {
                String val = readCellAsText(sourceHeader, c, formatter);
                if (schemaHelper.isErrorColumnHeader(val)) {
                    continue;
                }
                if (schemaHelper.matchColumn(val) != null) {
                    copiedColIndices.add(c);
                    headers.add(val.trim());
                }
            }
            if (copiedColIndices.isEmpty()) {
                headers = new ArrayList<>(schemaHelper.templateHeaders(locale));
                for (int i = 0; i < headers.size(); i++) {
                    copiedColIndices.add(i);
                }
            }

            Row outHeader = outSheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = outHeader.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                outSheet.setColumnWidth(i, 24 * 256);
            }
            int errorColIdx = headers.size();
            Cell errorHeaderCell = outHeader.createCell(errorColIdx);
            errorHeaderCell.setCellValue(schemaHelper.errorColumnHeader(locale));
            errorHeaderCell.setCellStyle(headerStyle);
            outSheet.setColumnWidth(errorColIdx, 48 * 256);

            int outRowIdx = 1;
            for (ImportRow invalidRow : invalidRows) {
                Row sourceRow = sourceSheet.getRow(invalidRow.getRowNo() - 1);
                Row outRow = outSheet.createRow(outRowIdx++);
                for (int i = 0; i < copiedColIndices.size(); i++) {
                    String cellVal = sourceRow == null
                            ? ""
                            : readCellAsText(sourceRow, copiedColIndices.get(i), formatter);
                    outRow.createCell(i).setCellValue(cellVal);
                }
                String combinedErrors = invalidRow.getErrors().stream()
                        .map(err -> err.column() + ": " + err.message())
                        .collect(Collectors.joining("; "));
                outRow.createCell(errorColIdx).setCellValue(combinedErrors);
            }

            outWb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build import error workbook", ex);
        }
    }

    public byte[] buildResultWorkbook(
            List<ImportRow> allRows, Map<Integer, String> generatedPasswordsByRowNo, Locale locale) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(null, 200, false, true);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(schemaHelper.resultSheetName(locale));
            CellStyle headerStyle = createBoldStyle(workbook);

            List<String> headers = schemaHelper.resultHeaders(locale);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 7 ? 42 * 256 : 24 * 256);
            }

            int r = 1;
            for (ImportRow row : allRows) {
                Row outRow = sheet.createRow(r++);
                outRow.createCell(0).setCellValue(safe(row.getRaw().username()));
                outRow.createCell(1).setCellValue(safe(row.getRaw().fullName()));
                outRow.createCell(2).setCellValue(safe(row.getRaw().email()));
                outRow.createCell(3).setCellValue(safe(row.getRaw().phone()));
                outRow.createCell(4).setCellValue(safe(row.getRaw().classCode()));

                String generatedPassword = "";
                if (row.isPasswordGenerated() && row.getStatus() == ImportRowStatus.CREATED) {
                    generatedPassword = generatedPasswordsByRowNo.getOrDefault(row.getRowNo(), "");
                }
                outRow.createCell(5).setCellValue(generatedPassword);
                outRow.createCell(6).setCellValue(schemaHelper.statusLabel(row.getStatus(), locale));
                outRow.createCell(7).setCellValue(row.getStatus() == ImportRowStatus.FAILED
                        ? safe(row.getFailureReason())
                        : "");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build import result workbook", ex);
        }
    }

    public Map<String, String> extractGeneratedPasswordsByNormalizedUsername(byte[] resultWorkbookBytes) {
        if (resultWorkbookBytes == null || resultWorkbookBytes.length == 0) {
            return Map.of();
        }
        configureZipSecureLimits();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(resultWorkbookBytes))) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return Map.of();
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, String> result = new HashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String username = Normalizer.normalize(readCellAsText(row, 0, formatter));
                String generatedPwd = readCellAsText(row, 5, formatter);
                if (username != null && generatedPwd != null && !generatedPwd.isBlank()) {
                    result.put(username, generatedPwd);
                }
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read existing result workbook", ex);
        }
    }

    private List<ParsedImportRow> parseSheet(Sheet sheet, Locale locale) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw validationError(
                    "IMPORT_INVALID_HEADER",
                    schemaHelper.message("IMPORT_INVALID_HEADER", "Thiếu dòng tiêu đề", locale));
        }

        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Map<ImportColumn, Integer> columnIndices = new EnumMap<>(ImportColumn.class);
        short lastCell = headerRow.getLastCellNum();
        for (int c = 0; c < lastCell; c++) {
            String headerText = readCellAsText(headerRow, c, formatter);
            ImportColumn col = schemaHelper.matchColumn(headerText);
            if (col != null && !columnIndices.containsKey(col)) {
                columnIndices.put(col, c);
            }
        }

        if (!columnIndices.containsKey(ImportColumn.USERNAME)
                || !columnIndices.containsKey(ImportColumn.FULL_NAME)) {
            throw validationError(
                    "IMPORT_INVALID_HEADER",
                    schemaHelper.message("IMPORT_INVALID_HEADER", "Cấu trúc cột trong tệp Excel không đúng mẫu", locale));
        }

        List<ParsedImportRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int i = 1; i <= lastRow; i++) {
            Row sheetRow = sheet.getRow(i);
            if (sheetRow == null) {
                continue;
            }
            String username = readMappedText(sheetRow, columnIndices.get(ImportColumn.USERNAME), formatter);
            String password = readMappedRawPassword(sheetRow, columnIndices.get(ImportColumn.PASSWORD), formatter);
            String fullName = readMappedText(sheetRow, columnIndices.get(ImportColumn.FULL_NAME), formatter);
            String email = readMappedText(sheetRow, columnIndices.get(ImportColumn.EMAIL), formatter);
            String phone = readMappedText(sheetRow, columnIndices.get(ImportColumn.PHONE), formatter);
            String classCode = readMappedText(sheetRow, columnIndices.get(ImportColumn.CLASS_CODE), formatter);

            if (isAllBlank(username, password, fullName, email, phone, classCode)) {
                continue;
            }
            if (i == 1 && isUntouchedExampleRow(username, password, fullName, email, phone, classCode)) {
                continue;
            }
            if (rows.size() >= MAX_DATA_ROWS) {
                throw validationError(
                        "IMPORT_ROW_LIMIT_EXCEEDED",
                        schemaHelper.message(
                                "IMPORT_BATCH_ROW_LIMIT_EXCEEDED",
                                "Số dòng dữ liệu vượt quá giới hạn tối đa 2.000 dòng",
                                locale));
            }
            rows.add(new ParsedImportRow(
                    i + 1,
                    trimToNull(username),
                    password == null || password.isBlank() ? null : password,
                    trimToNull(fullName),
                    trimToNull(email),
                    trimToNull(phone),
                    trimToNull(classCode)));
        }

        if (rows.isEmpty()) {
            throw validationError(
                    "IMPORT_EMPTY_FILE",
                    schemaHelper.message("IMPORT_EMPTY_FILE", "Tệp Excel trống hoặc không có dòng dữ liệu hợp lệ", locale));
        }
        return rows;
    }

    private static boolean isUntouchedExampleRow(
            String username, String password, String fullName, String email, String phone, String classCode) {
        return EXAMPLE_USERNAME.equals(trimToNull(username))
                && EXAMPLE_PASSWORD.equals(password)
                && EXAMPLE_FULL_NAME.equals(trimToNull(fullName))
                && EXAMPLE_EMAIL.equals(trimToNull(email))
                && EXAMPLE_PHONE.equals(trimToNull(phone))
                && EXAMPLE_CLASS_CODE.equals(trimToNull(classCode));
    }

    private void verifyZipSignature(Path tempFile, Locale locale) throws IOException {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(tempFile)) {
            int read = in.readNBytes(header, 0, 4);
            if (read < 4 || header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                throw validationError(
                        "IMPORT_INVALID_FILE",
                        schemaHelper.message("IMPORT_INVALID_FILE", "Tệp tải lên không phải định dạng .xlsx hợp lệ", locale));
            }
        }
    }

    private static void configureZipSecureLimits() {
        ZipSecureFile.setMinInflateRatio(ZIP_MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(ZIP_MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxTextSize(ZIP_MAX_TEXT_SIZE);
    }

    private void rejectMacroZipEntries(Path tempFile, Locale locale) throws IOException {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(tempFile.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject")) {
                    throw validationError(
                            "IMPORT_MACRO_NOT_ALLOWED",
                            schemaHelper.message("IMPORT_MACRO_NOT_ALLOWED", "Tệp Excel không được chứa macro", locale));
                }
            }
        }
    }

    private void rejectMacros(OPCPackage pkg, Locale locale)
            throws org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        for (PackagePart part : pkg.getParts()) {
            String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
            String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase(Locale.ROOT);
            if (partName.contains("vbaproject") || contentType.contains("macro") || contentType.contains("vbaproject")) {
                throw validationError(
                        "IMPORT_MACRO_NOT_ALLOWED",
                        schemaHelper.message("IMPORT_MACRO_NOT_ALLOWED", "Tệp Excel không được chứa macro", locale));
            }
        }
    }

    private void rejectFormulas(XSSFWorkbook workbook, Locale locale) {
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        throw validationError(
                                "IMPORT_FORMULA_NOT_ALLOWED",
                                schemaHelper.message("IMPORT_FORMULA_NOT_ALLOWED", "Tệp Excel không được chứa công thức", locale));
                    }
                }
            }
        }
    }

    private static String readMappedText(Row row, Integer colIndex, DataFormatter formatter) {
        if (colIndex == null || colIndex < 0) {
            return null;
        }
        return readCellAsText(row, colIndex, formatter);
    }

    private static String readMappedRawPassword(Row row, Integer colIndex, DataFormatter formatter) {
        if (colIndex == null || colIndex < 0) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        return formatter.formatCellValue(cell);
    }

    private static String readCellAsText(Row row, int colIndex, DataFormatter formatter) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }

    private static boolean isAllBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static CellStyle createBoldStyle(org.apache.poi.ss.usermodel.Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static ValidationException validationError(String code, String message) {
        return new ValidationException(code, message, List.of(new FieldError("file", code, message)));
    }

    public record ParsedImportRow(
            int rowNo,
            String username,
            String password,
            String fullName,
            String email,
            String phone,
            String classCode
    ) {
        @Override
        public String toString() {
            return "ParsedImportRow["
                    + "rowNo=" + rowNo
                    + ", username=" + username
                    + ", password=" + (password == null ? "null" : "[REDACTED]")
                    + ", fullName=" + fullName
                    + ", email=" + email
                    + ", phone=" + phone
                    + ", classCode=" + classCode
                    + ']';
        }
    }
}
