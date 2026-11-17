package vn.unicom.thi360.questionbank.domain.importing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Đọc XLSX bằng Apache POI theo cùng cách an toàn của
 * {@code catalog/application/CatalogExcelService} và
 * {@code identity/application/ImportWorkbookCodec}: chữ ký zip, chặn phần macro
 * ({@code vbaProject}, content-type macro), giới hạn {@code ZipSecureFile} (tỉ lệ nén, cỡ
 * mục, cỡ chữ) chống zip bomb, và từ chối ô công thức. Đọc trang tính đầu tiên; dòng 1 là
 * tên cột; ô đọc thành chuỗi qua {@link DataFormatter}.
 */
public final class XlsxImportSheetReader implements ImportSheetReader {

    private static final double ZIP_MIN_INFLATE_RATIO = 0.01d;
    private static final long ZIP_MAX_ENTRY_SIZE = 10L * 1024L * 1024L;
    private static final long ZIP_MAX_TEXT_SIZE = 10L * 1024L * 1024L;
    private static final int MAX_COLUMNS = 256;

    @Override
    public ImportSheet read(String fileName, byte[] fileBytes, ImportLimits limits) throws ImportFileException {
        ImportFiles.checkCommon(fileName, fileBytes, limits);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("thi360-question-import-", ".xlsx");
            Files.write(tempFile, fileBytes);
            verifyZipSignature(tempFile);
            rejectMacroZipEntries(tempFile);
            configureZipSecureLimits();

            try (OPCPackage pkg = OPCPackage.open(tempFile.toFile(), PackageAccess.READ)) {
                rejectMacros(pkg);
                try (XSSFWorkbook workbook = new XSSFWorkbook(pkg)) {
                    rejectFormulas(workbook);
                    Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
                    if (sheet == null) {
                        throw error(ImportIssueCode.IMPORT_EMPTY_FILE, "Tệp Excel trống");
                    }
                    return parseSheet(sheet, limits);
                }
            }
        } catch (ImportFileException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ImportFileException(
                ImportIssueCode.IMPORT_INVALID_FILE,
                "Tệp Excel không hợp lệ hoặc vượt quá giới hạn giải nén an toàn",
                ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // dọn tệp tạm theo khả năng
                }
            }
        }
    }

    private ImportSheet parseSheet(Sheet sheet, ImportLimits limits) throws ImportFileException {
        // Chỉ duyệt các dòng vật lý: tệp thưa (ô XFD1 / A1048576) không được bung thành
        // ma trận đầy; giữ số dòng gốc của trang tính (R1-1).
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<List<String>> table = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        int headerCols = -1;
        int acceptedRows = 0;
        for (Row row : sheet) {
            int last = row.getLastCellNum();
            if (headerCols < 0) {
                if (last <= 0) {
                    throw error(ImportIssueCode.IMPORT_INVALID_HEADER, "Thiếu dòng tiêu đề");
                }
                if (last > MAX_COLUMNS) {
                    throw error(ImportIssueCode.IMPORT_INVALID_HEADER,
                        "Số cột vượt quá giới hạn tối đa " + MAX_COLUMNS);
                }
                headerCols = last;
                List<String> header = new ArrayList<>(headerCols);
                for (int c = 0; c < headerCols; c++) {
                    header.add(readCellAsText(row, c, formatter));
                }
                if (header.stream().allMatch(String::isBlank)) {
                    throw error(ImportIssueCode.IMPORT_INVALID_HEADER, "Thiếu dòng tiêu đề");
                }
                table.add(header);
                rowNumbers.add(row.getRowNum() + 1);
                continue;
            }
            List<String> values = null;
            boolean blank = true;
            for (int c = 0; c < Math.min(last, headerCols); c++) {
                String text = readCellAsText(row, c, formatter);
                if (values == null) {
                    values = new ArrayList<>(headerCols);
                }
                values.add(text);
                if (!text.isBlank()) {
                    blank = false;
                }
            }
            if (blank) {
                continue;
            }
            if (acceptedRows >= limits.maxRows()) {
                throw error(ImportIssueCode.IMPORT_ROW_LIMIT_EXCEEDED,
                    "Số dòng dữ liệu vượt quá giới hạn tối đa " + limits.maxRows() + " dòng");
            }
            acceptedRows++;
            for (int c = values == null ? 0 : values.size(); c < headerCols; c++) {
                values.add("");
            }
            table.add(values);
            rowNumbers.add(row.getRowNum() + 1);
        }
        if (headerCols < 0) {
            throw error(ImportIssueCode.IMPORT_EMPTY_FILE, "Tệp Excel trống");
        }
        return ImportSheets.fromTable(
            ImportFileType.XLSX, sheet.getSheetName(), table,
            rowNumbers.stream().mapToInt(Integer::intValue).toArray(), limits);
    }

    private static String readCellAsText(Row row, int colIndex, DataFormatter formatter) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }

    private static void verifyZipSignature(Path tempFile) throws IOException, ImportFileException {
        byte[] signature = new byte[4];
        try (InputStream in = Files.newInputStream(tempFile)) {
            int read = in.readNBytes(signature, 0, 4);
            if (read < 4 || signature[0] != 0x50 || signature[1] != 0x4B
                || signature[2] != 0x03 || signature[3] != 0x04) {
                throw error(ImportIssueCode.IMPORT_INVALID_FILE, "Tệp tải lên không phải định dạng .xlsx hợp lệ");
            }
        }
    }

    private static void configureZipSecureLimits() {
        ZipSecureFile.setMinInflateRatio(ZIP_MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(ZIP_MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxTextSize(ZIP_MAX_TEXT_SIZE);
    }

    private static void rejectMacroZipEntries(Path tempFile) throws IOException, ImportFileException {
        try (ZipFile zip = new ZipFile(tempFile.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject")) {
                    throw error(ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED, "Tệp Excel không được chứa macro");
                }
            }
        }
    }

    private static void rejectMacros(OPCPackage pkg)
        throws ImportFileException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        for (PackagePart part : pkg.getParts()) {
            String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
            String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase(Locale.ROOT);
            if (partName.contains("vbaproject") || contentType.contains("macro") || contentType.contains("vbaproject")) {
                throw error(ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED, "Tệp Excel không được chứa macro");
            }
        }
    }

    private static void rejectFormulas(XSSFWorkbook workbook) throws ImportFileException {
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        throw error(ImportIssueCode.IMPORT_FORMULA_NOT_ALLOWED, "Tệp Excel không được chứa công thức");
                    }
                }
            }
        }
    }

    private static ImportFileException error(ImportIssueCode code, String message) {
        return new ImportFileException(code, message);
    }
}
